package com.jlees.mibrowser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.webkit.JavaScriptReplyProxy
import com.jlees.mibrowser.extensions.ExtensionEngine
import com.jlees.mibrowser.extensions.InstalledExtension
import com.jlees.mibrowser.extensions.blockedPart
import com.jlees.mibrowser.extensions.isStrictSite
import com.jlees.mibrowser.extensions.originOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.jsonObject

/** The extension engine's message routing with fake frames: what pages and backgrounds are told, and when. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtensionEngineTest {
    /** A frame's end of the miExt channel: records what mi sends it. */
    private class FakeFrame : JavaScriptReplyProxy() {
        val sent = mutableListOf<String>()
        override fun postMessage(message: String) { sent += message }
        override fun postMessage(arrayBuffer: ByteArray) {}
        override fun executeJavaScript(script: String, callback: androidx.webkit.WebViewOutcomeReceiver<String, androidx.webkit.JavaScriptExecutionException>?) {}
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val vm = BrowserViewModel(app).apply { extensions.add(InstalledExtension("e", "E", "1")) }
    private val engine = ExtensionEngine(app, vm).apply { tokens["e"] = "tok" }
    private val background = FakeFrame()

    init {
        engine.contexts += engine.Ctx("e", ExtensionEngine.Kind.BACKGROUND, null, true, "https://e.ext.mi.invalid/", null).also { it.proxy = background; it.ready = true }
    }

    private fun fromTab(frame: FakeFrame, msg: String) { engine.onMessage(ExtensionEngine.Source.Tab("t1", null), msg, frame); shadowOf(Looper.getMainLooper()).idle() }
    private fun hello(frame: FakeFrame, url: String) = fromTab(frame, """{"t":"hello","u":"$url","top":true,"x":[["e","tok"]]}""")
    private fun call(frame: FakeFrame, name: String, args: String) = fromTab(frame, """{"t":"call","x":"e","k":"tok","c":1,"n":"$name","a":$args}""")

    @Test fun aPortClosesWhenItsPageIsReplaced() {
        val page = FakeFrame()
        hello(page, "https://a.com/")
        call(page, "port.connect", """["p1","vapi"]""")
        assertTrue(background.sent.any { "runtime.onConnect" in it && "p1" in it })
        // the tab moves on to another page: the background hears the old page's port close (it used to stay open forever)
        hello(FakeFrame(), "https://b.com/")
        assertTrue(background.sent.any { "port.disconnect" in it && "p1" in it })
    }

    @Test fun anExtensionPageKeepsWorkingBesideItsOwnFrames() {
        // uBlock Origin's dashboard (an extension page in a tab) holds its panes in a frame of its own: the frame saying
        // hello used to replace the page, whose calls then went unanswered (a blank dashboard)
        val ext = ExtensionEngine.Source.Tab("t2", null, origin = "https://e.ext.mi.invalid")
        fun from(frame: FakeFrame, msg: String) { engine.onMessage(ext, msg, frame); shadowOf(Looper.getMainLooper()).idle() }
        val page = FakeFrame(); val pane = FakeFrame()
        from(page, """{"t":"hello","x":"e","k":"tok","u":"https://e.ext.mi.invalid/dashboard.html","top":true}""")
        from(pane, """{"t":"hello","x":"e","k":"tok","u":"https://e.ext.mi.invalid/settings.html","top":false}""")
        from(page, """{"t":"call","x":"e","k":"tok","c":7,"n":"port.connect","a":["p3","x"]}""")
        assertTrue(page.sent.any { "\"t\":\"res\"" in it && "\"c\":7" in it })
    }

    @Test fun aCookieSetOnAPathIsToldWithThatPath() {
        val m = com.jlees.mibrowser.extensions.parseManifest(kotlinx.serialization.json.Json.parseToJsonElement(
            """{"name":"E","version":"1","manifest_version":3,"permissions":["cookies"],"host_permissions":["*://*.keepa.com/*"]}""").jsonObject)
        engine.infos["e"] = com.jlees.mibrowser.extensions.ExtensionInfo("e", File("."), m, emptyMap(), com.jlees.mibrowser.extensions.compatibility(m, emptySet(), com.jlees.mibrowser.extensions.CodeFlags()))
        // Keepa keeps its settings in cookies on keepa.com/extension, saved when cookies.onChanged tells it one with that
        // path: read back from the site's root, the cookie wasn't found, so it was never told (and never saved its settings)
        val bg = engine.contexts.first { it.kind == ExtensionEngine.Kind.BACKGROUND }
        engine.onMessage(ExtensionEngine.Source.Page(bg), """{"t":"call","x":"e","k":"tok","c":1,"n":"cookies.set","a":[{"url":"https://keepa.com","path":"/extension","name":"s_boxType","value":"0","secure":true}]}""", background)
        shadowOf(Looper.getMainLooper()).idle()
        val told = background.sent.firstOrNull { "cookies.onChanged" in it }
        assertTrue(told != null && "\"path\":\"/extension\"" in told && "s_boxType" in told)
    }

    @Test fun tabsAlreadyOpenAreNotToldAsNew() {
        fun settle() { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); shadowOf(Looper.getMainLooper()).idle() }
        vm.tabs.open(com.jlees.mibrowser.data.TabItem("a"))
        engine.observeTabs(); settle()
        // every tab open at start used to be sent as created, reaching uBlock Origin while it was starting (and breaking it)
        assertFalse(background.sent.any { "tabs.onCreated" in it })
        vm.tabs.open(com.jlees.mibrowser.data.TabItem("b")); settle()
        assertEquals(1, background.sent.count { "tabs.onCreated" in it })
    }

    @Test fun callsFromAFrameMiDoesntKnowYetGoUnanswered() {
        // why ext-content.js says hello before running any content script (see below)
        val page = FakeFrame()
        call(page, "port.connect", """["p2","x"]""")
        assertTrue(page.sent.isEmpty())
        assertFalse(background.sent.any { "p2" in it })
    }

    @Test fun contentScriptsRunOnlyAfterTheFrameSaysHello() {
        val js = File("src/main/assets/js/ext-content.js").readText()
        val hello = js.indexOf("post({ t: 'hello'")
        assertTrue(hello >= 0)
        assertTrue("hello must come before the scripts run", hello < js.indexOf("ext.scripts.forEach"))
    }

    @Test fun trustedTypesRefusalsFromExtensionCodeAreReported() {
        // YouTube requires Trusted Types; a browser exempts extensions, mi's page-world scripts aren't, so a refusal of
        // extension code (no address of its own, or mi's address for the extension) is told to mi like a blocked file,
        // while the site's own scripts (an https: address) never lift its policy
        val js = File("src/main/assets/js/ext-content.js").readText()
        val handler = js.substringAfter("addEventListener('securitypolicyviolation'").substringBefore("}, true);")
        assertTrue("require-trusted-types-for" in handler)
        assertTrue("!/^https?:/.test(ev.sourceFile || '')" in handler)
        assertTrue("|| types)" in handler)
    }

    @Test fun anExtensionsOwnPageShowsItsName() {
        // its address at mi (…ext.mi.invalid) means nothing to the person: the Pill and tab cards show the extension
        val m = com.jlees.mibrowser.extensions.parseManifest(kotlinx.serialization.json.Json.parseToJsonElement("""{"name":"Badger","version":"1"}""").jsonObject)
        engine.infos["e"] = com.jlees.mibrowser.extensions.ExtensionInfo("e", File("."), m, emptyMap(), com.jlees.mibrowser.extensions.compatibility(m, emptySet(), com.jlees.mibrowser.extensions.CodeFlags()))
        com.jlees.mibrowser.extensions.ExtensionFlow.engine = engine
        try {
            assertEquals("Badger", com.jlees.mibrowser.extensions.shownHost("https://e.ext.mi.invalid/welcome.html"))
            assertEquals("example.com", com.jlees.mibrowser.extensions.shownHost("https://example.com/"))
        } finally { com.jlees.mibrowser.extensions.ExtensionFlow.engine = null }
    }

    @Test fun addressesWithoutASiteAreNeverStrict() {
        assertEquals("https://a.com", originOf("https://a.com/x?y"))
        assertNull(originOf("about:blank"))
        assertNull(originOf("https://ads.example/a b|c")) // java.net.URI can't read it (this crashed mi)
        assertFalse(isStrictSite("about:blank", setOf("https://a.com")))
        assertFalse(isStrictSite("https://ads.example/a b|c", setOf("https://a.com")))
        assertTrue(isStrictSite("https://a.com/page", setOf("https://a.com")))
    }

    @Test fun aBlockedPartFailsLikeABlockedRequest() {
        // WebView checks available() before sending headers: throwing there makes fetch() reject and onerror fire
        val r = blockedPart()
        assertTrue(runCatching { r.data.available() }.exceptionOrNull() is IOException)
    }
}
