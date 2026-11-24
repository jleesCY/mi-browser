package com.jlees.mibrowser

import com.jlees.mibrowser.extensions.MenuItem
import com.jlees.mibrowser.extensions.MenuPlace
import com.jlees.mibrowser.extensions.menuItemsFor
import com.jlees.mibrowser.extensions.CodeFlags
import com.jlees.mibrowser.extensions.Fit
import com.jlees.mibrowser.extensions.compatibility
import com.jlees.mibrowser.extensions.extensionId
import com.jlees.mibrowser.extensions.localize
import com.jlees.mibrowser.extensions.looksLikePasswordManager
import com.jlees.mibrowser.extensions.matchesPattern
import com.jlees.mibrowser.extensions.parseManifest
import com.jlees.mibrowser.extensions.runsOn
import com.jlees.mibrowser.extensions.scanApis
import com.jlees.mibrowser.extensions.scanFlags
import com.jlees.mibrowser.extensions.webAccessible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRulesTest {
    private fun manifest(s: String) = parseManifest(Json.parseToJsonElement(s).jsonObject)

    @Test fun matchPatterns() {
        assertTrue(matchesPattern("<all_urls>", "https://a.com/x"))
        assertFalse(matchesPattern("<all_urls>", "about:blank"))
        assertTrue(matchesPattern("*://*.youtube.com/*", "https://www.youtube.com/watch?v=1"))
        assertTrue(matchesPattern("*://*.youtube.com/*", "https://youtube.com/")) // *. covers the domain itself
        assertFalse(matchesPattern("*://*.youtube.com/*", "https://notyoutube.com/"))
        assertFalse(matchesPattern("*://*.youtube.com/*", "ftp://www.youtube.com/"))
        assertTrue(matchesPattern("https://example.com/a/*", "https://example.com/a/b/c"))
        assertFalse(matchesPattern("https://example.com/a/*", "https://example.com/b"))
        assertFalse(matchesPattern("https://example.com/*", "http://example.com/")) // scheme given: only that one
        assertTrue(matchesPattern("*://example.com/*", "https://user@example.com:8443/x")) // port and user ignored
        assertTrue(matchesPattern("*://*/*", "http://10.0.2.2:8811/"))
    }

    @Test fun contentScriptsMatchesAndGlobs() {
        val m = manifest("""{"name":"x","version":"1","content_scripts":[{"matches":["*://*.example.com/*"],"exclude_matches":["*://admin.example.com/*"],
            "exclude_globs":["*logout*"],"js":["a.js"],"run_at":"document_start","all_frames":true}]}""")
        val cs = m.contentScripts.single()
        assertEquals("document_start", cs.runAt); assertTrue(cs.allFrames)
        assertTrue(runsOn(cs, "https://www.example.com/page"))
        assertFalse(runsOn(cs, "https://admin.example.com/"))
        assertFalse(runsOn(cs, "https://www.example.com/logout"))
    }

    @Test fun manifestParts() {
        val mv3 = manifest("""{"manifest_version":3,"name":"__MSG_n__","version":"2.1","action":{"default_popup":"p.html","default_icon":{"16":"i16.png","48":"i48.png"}},
            "background":{"service_worker":"sw.js","type":"module"},"host_permissions":["https://*/*"],"permissions":["storage","tabs"],
            "web_accessible_resources":[{"resources":["img/*","f.css"],"matches":["<all_urls>"]}],"options_ui":{"page":"o.html"},
            "browser_specific_settings":{"gecko":{"id":"x@y.org"}}}""")
        assertEquals(3, mv3.version); assertEquals("p.html", mv3.popup); assertEquals("sw.js", mv3.serviceWorker); assertTrue(mv3.backgroundModule)
        assertEquals(listOf("storage", "tabs"), mv3.permissions); assertEquals(listOf("https://*/*"), mv3.hostPermissions)
        assertEquals("o.html", mv3.optionsPage); assertEquals("x@y.org", mv3.geckoId); assertEquals("i48.png", mv3.icon(32))
        assertTrue(webAccessible("/img/a.png", mv3.webAccessible)); assertFalse(webAccessible("secret.js", mv3.webAccessible))
        // MV2: host patterns sit among the permissions
        val mv2 = manifest("""{"name":"x","version":"1","permissions":["storage","<all_urls>","*://*.a.com/*"],"background":{"scripts":["b1.js","b2.js"]},"browser_action":{}}""")
        assertEquals(listOf("storage"), mv2.permissions); assertEquals(listOf("<all_urls>", "*://*.a.com/*"), mv2.hostPermissions)
        assertEquals(listOf("b1.js", "b2.js"), mv2.backgroundScripts); assertTrue(mv2.hasAction)
        assertEquals("My Ext", localize("__MSG_appName__", mapOf("appname" to "My Ext")))
        assertEquals("__MSG_missing__", localize("__MSG_missing__", emptyMap()))
    }

    @Test fun apiScan() {
        val apis = scanApis("chrome.storage.local.get(k); browser.runtime.sendMessage(m); if (chrome.tabs) x; myChrome.foo();")
        assertEquals(setOf("storage.local", "runtime.sendMessage", "tabs"), apis)
        assertTrue(scanFlags("const f = chrome[name];", "").dynamicApi)
        assertTrue(scanFlags("", "eval(code)").contentEval)
        assertFalse(scanFlags("chrome.runtime.id", "x.evaluate()").let { it.dynamicApi || it.contentEval })
    }

    @Test fun ratings() {
        val simple = manifest("""{"name":"Dark","version":"1","permissions":["storage"],"content_scripts":[{"matches":["<all_urls>"],"js":["c.js"]}]}""")
        // everything it uses is fully supported: Works
        val works = compatibility(simple, setOf("storage.local", "runtime.sendMessage", "runtime.onMessage", "tabs", "i18n.getMessage"), CodeFlags())
        assertEquals(Fit.WORKS, works.fit); assertTrue(works.reasons.isEmpty())
        // anything partial, or unchecked, makes it a Maybe, with a reason
        val partial = compatibility(simple, setOf("storage.local", "contextMenus.create"), CodeFlags())
        assertEquals(Fit.MAYBE, partial.fit); assertTrue(partial.reasons.single().contains("long-press menu"))
        assertEquals(Fit.MAYBE, compatibility(simple, setOf("storage.local"), CodeFlags(dynamicApi = true)).fit)
        assertEquals(Fit.WORKS, compatibility(simple, setOf("tabs.captureVisibleTab", "sidePanel.open", "offscreen.createDocument"), CodeFlags()).fit)
        assertEquals(Fit.MAYBE, compatibility(simple, setOf("tabs.move"), CodeFlags()).fit)
        // signing in: a site's own sign-in works (a Maybe for the permission), Chrome's Google account sign-in doesn't
        val signIn = manifest("""{"name":"x","version":"1","permissions":["identity"],"background":{"scripts":["b.js"]}}""")
        assertEquals(Fit.MAYBE, compatibility(signIn, setOf("identity.launchWebAuthFlow"), CodeFlags()).fit)
        assertEquals(Fit.NO, compatibility(signIn, setOf("identity.getAuthToken"), CodeFlags()).fit)
        assertEquals(Fit.MAYBE, compatibility(simple, setOf("tabs.somethingNew"), CodeFlags()).fit) // an API mi doesn't know: not guaranteed
        assertEquals(Fit.WORKS, compatibility(simple, setOf("runtime.OnInstalledReason", "runtime.PlatformOs"), CodeFlags()).fit) // constants, not calls (Keepa)
        // a developer-tools panel is an extra (Cookie-Editor, Adblock Plus): not a reason to call it Not recommended
        assertEquals(Fit.MAYBE, compatibility(manifest("""{"name":"x","version":"1","devtools_page":"d.html","content_scripts":[{"matches":["<all_urls>"],"js":["c.js"]}]}"""), emptySet(), CodeFlags()).fit)
        // something mi can't do: Not recommended, whatever else is fine
        val native = manifest("""{"name":"x","version":"1","permissions":["nativeMessaging"],"content_scripts":[{"matches":["<all_urls>"],"js":["c.js"]}]}""")
        assertEquals(Fit.NO, compatibility(native, emptySet(), CodeFlags()).fit)
        // blocking web requests works, except for requests that send data: a Maybe
        val blocking = manifest("""{"name":"x","version":"1","permissions":["webRequest","webRequestBlocking"],"background":{"scripts":["b.js"]}}""")
        assertEquals(Fit.MAYBE, compatibility(blocking, emptySet(), CodeFlags()).fit)
        assertEquals(Fit.NO, compatibility(manifest("""{"name":"x","version":"1","theme":{}}"""), emptySet(), CodeFlags()).fit)
        // something mi can't do, but only for an extra (an optional permission, or code without the permission it needs): a Maybe
        val optional = manifest("""{"name":"x","version":"1","permissions":["storage"],"optional_permissions":["identity"],"content_scripts":[{"matches":["<all_urls>"],"js":["c.js"]}]}""")
        val extra = compatibility(optional, setOf("identity.getAuthToken"), CodeFlags())
        assertEquals(Fit.MAYBE, extra.fit); assertTrue(extra.reasons.any { it.startsWith("Some of its extras won't work") })
        assertEquals(listOf("identity"), optional.optionalPermissions); assertEquals(listOf("storage"), optional.permissions)
        assertEquals(Fit.MAYBE, compatibility(simple, setOf("runtime.connectNative"), CodeFlags()).fit) // no nativeMessaging permission: it can't be needed
        assertEquals(Fit.NO, compatibility(simple, emptySet(), CodeFlags(), passwordManager = true).fit)
        assertTrue(looksLikePasswordManager("Bitwarden", "A secure and free password manager"))
        assertFalse(looksLikePasswordManager("Dark Reader", "Dark mode for every website"))
    }

    @Test fun storePages() {
        assertEquals(com.jlees.mibrowser.extensions.InstallSource.Amo("darkreader"), com.jlees.mibrowser.extensions.storeSource("https://addons.mozilla.org/en-US/firefox/addon/darkreader/"))
        assertEquals(com.jlees.mibrowser.extensions.InstallSource.Amo("sponsorblock"), com.jlees.mibrowser.extensions.storeSource("https://addons.mozilla.org/android/addon/sponsorblock/?src=search"))
        // the Chrome Web Store isn't a store mi adds from (it doesn't make extensions for phones)
        assertEquals(null, com.jlees.mibrowser.extensions.storeSource("https://chromewebstore.google.com/detail/dark-reader/eimadpbcbfnmbkopoojfekhnkhdbieeh"))
        assertEquals(null, com.jlees.mibrowser.extensions.storeSource("https://addons.mozilla.org/en-US/firefox/"))
        assertEquals(null, com.jlees.mibrowser.extensions.storeSource("https://example.com/addon/x"))
        assertEquals("Dark Reader", com.jlees.mibrowser.extensions.storeTitleName("Dark Reader – Get this Extension for 🦊 Firefox (en-US)"))
        assertEquals("", com.jlees.mibrowser.extensions.storeTitleName("Client Challenge")) // a bot check, not the listing
    }

    @Test fun ids() {
        val gecko = extensionId("addon@darkreader.org", "Dark Reader")
        assertTrue(gecko.matches(Regex("[a-z0-9-]+"))); assertTrue(gecko.startsWith("addon-darkreader-org-"))
    }

    @Test fun menuItems() {
        val items = listOf(
            MenuItem("link", "Open", contexts = listOf("link")),
            MenuItem("sel", "Translate %s", contexts = listOf("selection")),
            MenuItem("all", "Everywhere", contexts = listOf("all")),
            MenuItem("parent", "Tools", contexts = listOf("all")),
            MenuItem("child", "Child", contexts = listOf("link"), parentId = "parent"),
            MenuItem("hidden", "Hidden", contexts = listOf("all"), visible = false),
            MenuItem("onlyEx", "Example only", contexts = listOf("link"), targetUrlPatterns = listOf("https://example.com/*")),
        )
        val link = menuItemsFor(items, setOf(MenuPlace.LINK), "https://a.com/", "https://b.com/x").map { it.id }
        // a parent with children is only their heading; hidden items and other contexts don't show
        assertEquals(listOf("link", "all", "child"), link)
        assertEquals(listOf("link", "all", "child", "onlyEx"), menuItemsFor(items, setOf(MenuPlace.LINK), null, "https://example.com/y").map { it.id })
        assertEquals(listOf("sel", "all"), menuItemsFor(items, setOf(MenuPlace.SELECTION), null, null).map { it.id })
        // a hidden parent hides its children
        val hiddenParent = items.map { if (it.id == "parent") it.copy(visible = false) else it }
        assertEquals(listOf("link", "all"), menuItemsFor(hiddenParent, setOf(MenuPlace.LINK), null, "https://b.com/").map { it.id })
    }

    @Test fun signInReturnAddress() {
        // Firefox's: the SHA-1 of its add-on id
        val ff = com.jlees.mibrowser.extensions.identityRedirect("abc", "addon@example.com")
        assertTrue(ff.matches(Regex("https://[0-9a-f]{40}\\.extensions\\.allizom\\.org/")))
    }

    @Test fun nativeAddresses() {
        val ff = "darkreader-addon-1a2b3c"; val cr = "eimadpbcbfnmbkopoojfekhnkhdbieeh"
        // as Firefox gives them
        assertEquals("moz-extension://$ff/", com.jlees.mibrowser.extensions.nativeBase(ff))
        val page = "https://$ff.ext.mi.invalid/src/popup.html?x=1"
        assertEquals("moz-extension://$ff/src/popup.html?x=1", com.jlees.mibrowser.extensions.toNative(page))
        assertEquals("https://example.com/", com.jlees.mibrowser.extensions.toNative("https://example.com/")) // a web page stays
        // and back: what mi loads
        assertEquals(page, com.jlees.mibrowser.extensions.fromNative("moz-extension://$ff/src/popup.html?x=1"))
        assertEquals(null, com.jlees.mibrowser.extensions.fromNative("chrome-extension://$cr/")) // mi gives only Firefox's
        assertEquals(null, com.jlees.mibrowser.extensions.fromNative("https://example.com/"))
    }

    @Test fun styleSheetsNameTheExtensionsOwnFiles() {
        val css = "@font-face{src:url(moz-extension://__MSG_@@extension_id__/fonts/a.woff2)} .x{background:url(moz-extension://__MSG_@@extension_id__/i.png)} /* __MSG_@@extension_id__ */"
        assertEquals("@font-face{src:url(https://abc.ext.mi.invalid/fonts/a.woff2)} .x{background:url(https://abc.ext.mi.invalid/i.png)} /* abc */",
            com.jlees.mibrowser.extensions.extensionCss(css, "abc"))
    }

    @Test fun extensionCodeSeesItsApisOnWindow() {
        // its own world's window.chrome / window.browser are the extension APIs (as in a browser); nothing else changes
        assertEquals("a=chrome.runtime.getURL(\"\");b=browser.storage;typeof chrome",
            com.jlees.mibrowser.extensions.isolatedWorld("a=window.chrome.runtime.getURL(\"\");b=window.browser.storage;typeof window.chrome"))
        assertEquals("x.window.chrome;window.chromeX;window.top===window;mywindow.chrome",
            com.jlees.mibrowser.extensions.isolatedWorld("x.window.chrome;window.chromeX;window.top===window;mywindow.chrome"))
        // import() goes through mi (it maps extensions' native addresses); import.meta and methods named import don't
        assertEquals("__miImport(u).then(f);x.import(y);import.meta.url;await __miImport(\"./a.js\")",
            com.jlees.mibrowser.extensions.isolatedWorld("import(u).then(f);x.import(y);import.meta.url;await import (\"./a.js\")"))
    }

    @Test fun onlyExtensionsMadeForPhonesAreAdded() {
        fun json(s: String) = kotlinx.serialization.json.Json.parseToJsonElement(s) as kotlinx.serialization.json.JsonObject
        fun madeForPhones(l: kotlinx.serialization.json.JsonObject?, m: kotlinx.serialization.json.JsonObject?) = com.jlees.mibrowser.extensions.madeForPhones(l, m)
        // Firefox Add-ons' "compatibility": a desktop-only one lists only Firefox; Dark Reader lists Android too
        assertFalse(madeForPhones(json("""{"firefox":{"min":"58.0","max":"*"}}"""), null))
        assertTrue(madeForPhones(json("""{"firefox":{"min":"78.0"},"android":{"min":"113.0"}}"""), null))
        // the listing decides, whatever the manifest says
        assertFalse(madeForPhones(json("""{"firefox":{}}"""), json("""{"browser_specific_settings":{"gecko_android":{}}}""")))
        // not listed (a file from a page): only if its manifest declares Android; no word either way isn't enough
        assertTrue(madeForPhones(null, json("""{"browser_specific_settings":{"gecko":{"id":"a@b"},"gecko_android":{}}}""")))
        assertFalse(madeForPhones(null, json("""{"browser_specific_settings":{"gecko":{"id":"a@b"}}}""")))
        assertFalse(madeForPhones(null, null))
    }

    @Test fun moduleBackgroundsLoadAsModules() {
        fun m(s: String) = com.jlees.mibrowser.extensions.parseManifest(kotlinx.serialization.json.Json.parseToJsonElement(s) as kotlinx.serialization.json.JsonObject)
        // Firefox's Manifest V3 background: scripts as modules (uBlock Origin Lite's), which a classic script tag can't run
        val ff = com.jlees.mibrowser.extensions.backgroundHtml(m("""{"manifest_version":3,"name":"x","version":"1","background":{"scripts":["/js/background.js"],"type":"module"}}"""))
        assertTrue(ff.contains("""<script type="module" src="/js/background.js">"""))
        val classic = com.jlees.mibrowser.extensions.backgroundHtml(m("""{"manifest_version":2,"name":"x","version":"1","background":{"scripts":["a.js","b.js"]}}"""))
        assertTrue(classic.contains("""<script src="/a.js"></script><script src="/b.js"></script>"""))
    }
}
