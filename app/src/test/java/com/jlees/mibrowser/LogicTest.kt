package com.jlees.mibrowser

import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicTest {
    @Test fun inputParsing() {
        assertEquals(null, inputToUrl("  ", false, false, 0))
        assertEquals("https://example.com", inputToUrl("example.com", false, false, 0))
        assertEquals("http://192.168.1.1:8080/x", inputToUrl("192.168.1.1:8080/x", false, false, 0))
        assertEquals("http://localhost:3000", inputToUrl("localhost:3000", false, false, 0))
        assertEquals("https://a.com", inputToUrl("http://a.com", false, true, 0))
        assertEquals("http://a.com", inputToUrl("http://a.com", false, false, 0))
        assertEquals("https://www.google.com/search?q=hello%20world", inputToUrl("hello world", false, false, 0))
        assertEquals("https://search.yahoo.com/search?p=example.com", inputToUrl("example.com", true, false, 5))
    }

    @Test fun searchAndTitles() {
        assertEquals("react native", searchQueryFromUrl("https://www.google.com/search?q=react+native"))
        assertEquals("Google Search: test", historyTitle("https://google.com/search?q=test", null))
        assertEquals("Example", historyTitle("https://example.com", "Example"))
        assertEquals("example.com", historyTitle("https://example.com", "https://x"))
        assertEquals("www.example.com", displayHost("https://www.example.com/path"))
    }

    @Test fun deepLinks() {
        assertEquals("https://a.com", parseDeepLink("mibrowser://https://a.com"))
        assertEquals("https://b.com", parseDeepLink("mi://open?url=https%3A%2F%2Fb.com"))
        assertEquals("http://c.com", parseDeepLink("http://c.com"))
        assertEquals(null, parseDeepLink("mibrowser://open?url=javascript:alert(1)"))
    }

    @Test fun tabHistory() {
        var t = TabItem("1", url = "https://a.com", historyStack = listOf("https://a.com"), currentIndex = 0)
        // new navigation pushes
        var (s, i) = applyNavUpdate(t, "https://b.com", false)
        assertEquals(listOf("https://a.com", "https://b.com"), s); assertEquals(1, i)
        t = t.copy(url = "https://b.com", historyStack = s, currentIndex = i)
        // back detected
        val (s2, i2) = applyNavUpdate(t, "https://a.com/", false)
        assertEquals(s, s2); assertEquals(0, i2)
        // new nav from the back position truncates forward
        t = t.copy(url = "https://a.com", currentIndex = 0)
        val (s3, i3) = applyNavUpdate(t, "https://c.com", false)
        assertEquals(listOf("https://a.com", "https://c.com"), s3); assertEquals(1, i3)
        // redirect with forward history keeps stack size
        val (s4, i4) = applyNavUpdate(t, "https://d.com", true)
        assertEquals(listOf("https://d.com", "https://b.com"), s4); assertEquals(0, i4)
        // push via address bar
        val (s5, i5) = pushNavigation(t, "https://e.com")
        assertEquals(listOf("https://a.com", "https://e.com"), s5); assertEquals(1, i5)
    }

    @Test fun historyRules() {
        val (h, r) = addHistory(emptyList(), emptyList(), "https://www.google.com/search?q=cats", "x", 1000)
        assertEquals(1, h.size); assertEquals("Google Search: cats", h[0].title); assertEquals("cats", r[0].title)
        val (h2, r2) = addHistory(h, r, "https://www.bing.com/search?q=cats", null, 2000)
        assertEquals(2, h2.size); assertEquals(1, r2.size)
        val (h3, _) = addHistory(h2, r2, "https://www.google.com/search?q=cats/", null, 3000)
        assertEquals(2, h3.size)
        assertEquals(1, pruneHistory(listOf(HistoryItem("a", "", 0), HistoryItem("b", "", 9_000)), 5_000, { it.timestamp }, 10_000).size)
    }

    @Test fun updateVersions() {
        val newer = com.jlees.mibrowser.data.Updates::isNewer
        assertEquals(true, newer("v1.0.1", "1.0.0"))
        assertEquals(true, newer("v1.10.0", "1.9.9"))
        assertEquals(true, newer("2.0", "1.9.9"))
        assertEquals(false, newer("v0.10.3", "1.0.0"))
        assertEquals(false, newer("v1.0.0", "1.0.0"))
        assertEquals(false, newer("v1.0.0-beta", "1.0.0"))
    }

    @Test fun typedAddressesForDevelopmentAndLocalNetworks() {
        fun go(t: String, httpsOnly: Boolean = false) = inputToUrl(t, false, httpsOnly, 0)!!
        val search = "https://www.google.com/search?q="
        // local development: http, never upgraded, ports and paths kept
        assertEquals("http://localhost:3000", go("localhost:3000"))
        assertEquals("http://localhost", go("localhost"))
        assertEquals("http://localhost:3000/api?x=1", go("localhost:3000/api?x=1", httpsOnly = true))
        assertEquals("http://localhost:3000", go("http://localhost:3000", httpsOnly = true))
        assertEquals("http://app.localhost:8080", go("app.localhost:8080"))
        assertEquals("http://10.0.2.2:3000", go("10.0.2.2:3000"))
        assertEquals("http://192.168.1.5:8080/", go("192.168.1.5:8080/"))
        assertEquals("http://[::1]:8080", go("[::1]:8080"))
        assertEquals("http://myapp.local", go("myapp.local"))
        assertEquals("http://dev.test:5173", go("dev.test:5173"))
        assertEquals("http://router.home.arpa", go("router.home.arpa"))
        assertEquals("http://nas:5000", go("nas:5000"))
        // public sites: https, ports allowed, explicit http upgraded only in HTTPS-Only mode
        assertEquals("https://example.com", go("example.com"))
        assertEquals("https://example.com:8443/x", go("example.com:8443/x"))
        assertEquals("https://münchen.de", go("münchen.de"))
        assertEquals("http://example.com", go("http://example.com"))
        assertEquals("https://example.com", go("http://example.com", httpsOnly = true))
        // searches
        assertEquals(search + "nas", go("nas"))
        assertEquals(search + "hello%20world", go("hello world"))
        assertEquals(search + "1.5", go("1.5"))
        assertEquals(search + "999.1.1.1", go("999.1.1.1"))
        assertEquals(search + "user%40example.com", go("user@example.com"))
        assertEquals(search + "example.com%3A99999", go("example.com:99999"))
    }

    @Test fun localHostsAreRecognized() {
        listOf("localhost", "a.localhost", "127.0.0.1", "10.0.2.2", "[::1]", "::1", "nas", "pi.local", "x.test", "box.lan", "r.home.arpa")
            .forEach { assertEquals(it, true, isLocalHost(it)) }
        listOf("example.com", "localhost.com", "test.example.org", "", null).forEach { assertEquals(it.toString(), false, isLocalHost(it)) }
    }

    @Test fun incognitoModeOnlySwitchesOnAFreshTab() {
        val fresh = com.jlees.mibrowser.data.newTab()
        assertEquals(true, fresh.isFresh)
        assertEquals(false, com.jlees.mibrowser.data.newTab("https://example.com").isFresh)               // has history
        assertEquals(false, fresh.copy(historyStack = listOf("https://a.com"), currentIndex = 0).isFresh)  // went home after browsing
        assertEquals(false, fresh.copy(openerId = "x").isFresh)                                             // a pop-up keeps its opener's mode
        val inc = fresh.copy(profile = com.jlees.mibrowser.data.newIncognitoProfile())
        assertEquals(true, inc.incognito)
        assertEquals(true, inc.profile!!.startsWith(com.jlees.mibrowser.data.INCOGNITO_PROFILE_PREFIX))
        // incognito state is never serialized: a restored tab is always normal
        val json = com.jlees.mibrowser.data.json.encodeToString(com.jlees.mibrowser.data.TabItem.serializer(), inc)
        assertEquals(false, json.contains("incognito"))
    }

    @Test fun faviconHostIsTheSiteItself() {
        assertEquals("en.wikipedia.org", faviconHost("https://en.wikipedia.org/wiki/Mars"))
        assertEquals("github.com", faviconHost("github.com"))               // bare host (history site groups)
        assertEquals("xn--pple-43d.com", faviconHost("https://\u0430pple.com/")) // punycode, like the address bar
        assertEquals(null, faviconHost("about:blank"))
        assertEquals(null, faviconHost(null))
    }

    @Test fun connectionSecurityMarker() {
        val none = emptySet<String>()
        assertEquals(true, isNotSecure("http://example.com/login", none))           // plain http over the network
        assertEquals(true, isNotSecure("http://192.168.1.20:8080/", none))           // LAN http crosses Wi-Fi unencrypted
        assertEquals(false, isNotSecure("https://example.com", none))
        assertEquals(false, isNotSecure("http://localhost:3000", none))              // never leaves the device
        assertEquals(false, isNotSecure("http://127.0.0.1:8000", none))
        assertEquals(false, isNotSecure("http://[::1]:8080/", none))
        assertEquals(false, isNotSecure("http://app.localhost/", none))
        assertEquals(true, isNotSecure("https://self-signed.example/", setOf("self-signed.example"))) // allowed past a cert error
        assertEquals(false, isNotSecure(null, none))
        assertEquals(false, isNotSecure("about:blank", none))
        assertEquals(false, isLoopback("127.example.com"))
    }

    @Test fun typedHttpsUpgradeIsDetected() {
        assertEquals(true, isTypedHttpsUpgrade("example.com", "https://example.com"))
        assertEquals(false, isTypedHttpsUpgrade("https://example.com", "https://example.com"))
        assertEquals(false, isTypedHttpsUpgrade("localhost:3000", "http://localhost:3000"))
        assertEquals(false, isTypedHttpsUpgrade("cats", "https://www.google.com/search?q=cats"))
    }

    @Test fun historyTitleUpgradesFromHostPlaceholder() {
        val url = "https://en.wikipedia.org/wiki/Mars"
        val (h1, _) = addHistory(emptyList(), emptyList(), url, "en.wikipedia.org") // loaded before the title arrived
        val (h2, _) = addHistory(h1, emptyList(), url, "Mars - Wikipedia")
        assertEquals(listOf("Mars - Wikipedia"), h2.map { it.title })
        val (h3, _) = addHistory(h2, emptyList(), url, null) // a later call without a title keeps the real one
        assertEquals("Mars - Wikipedia", h3[0].title)
    }

    @Test fun tabHistoryIsCapped() {
        val (stack, idx) = capped(List(MAX_TAB_HISTORY + 5) { "https://e.com/$it" }, MAX_TAB_HISTORY + 4)
        assertEquals(MAX_TAB_HISTORY, stack.size)
        assertEquals("https://e.com/5", stack.first())
        assertEquals(MAX_TAB_HISTORY - 1, idx)
    }

    @Test fun homographHostsShowAsPunycode() {
        assertEquals("xn--pple-43d.com", displayHost("https://\u0430pple.com/login")) // Cyrillic а
        assertEquals("apple.com", displayHost("https://apple.com/"))
    }

    @Test fun blocklistSuffixMatch() {
        val list = setOf("doubleclick.net", "ads.example.com")
        val blocked = { h: String -> com.jlees.mibrowser.data.Blocklist.isBlocked(h, list) }
        assertEquals(true, blocked("doubleclick.net"))
        assertEquals(true, blocked("stats.g.doubleclick.net"))
        assertEquals(true, blocked("a.ads.example.com"))
        assertEquals(false, blocked("example.com"))
        assertEquals(false, blocked("notdoubleclick.net"))
    }

    @Test fun hex() {
        assertEquals(0xFF007AFF, hexColor("#007AFF"))
        assertEquals(0xFF007AFF, hexColor("bad"))
    }
}
