package com.jlees.mibrowser

import com.jlees.mibrowser.tabs.MAX_TAB_HISTORY

import com.jlees.mibrowser.library.addHistory
import com.jlees.mibrowser.library.isNewVisit
import com.jlees.mibrowser.library.RESTORED_VISIT
import com.jlees.mibrowser.tabs.repairSaved
import com.jlees.mibrowser.library.pruneHistory
import com.jlees.mibrowser.tabs.applyNavUpdate
import com.jlees.mibrowser.tabs.capped
import com.jlees.mibrowser.tabs.pushNavigation
import com.jlees.mibrowser.update.Updates

import com.jlees.mibrowser.adblock.stripTracking
import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.*
import com.jlees.mibrowser.search.SEARCH_ENGINES
import com.jlees.mibrowser.search.allBangs
import com.jlees.mibrowser.search.isValidBangKey
import com.jlees.mibrowser.search.searchEngine
import com.jlees.mibrowser.search.searchQueryFromUrl
import com.jlees.mibrowser.search.typedBangLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun inputParsing() {
        assertEquals(null, inputToUrl("  ", false, SEARCH_ENGINES[0]))
        assertEquals("https://example.com", inputToUrl("example.com", false, SEARCH_ENGINES[0]))
        assertEquals("http://192.168.1.1:8080/x", inputToUrl("192.168.1.1:8080/x", false, SEARCH_ENGINES[0]))
        assertEquals("http://localhost:3000", inputToUrl("localhost:3000", false, SEARCH_ENGINES[0]))
        assertEquals("https://a.com", inputToUrl("http://a.com", true, SEARCH_ENGINES[0]))
        // HTTPS Only has no exception for local addresses (its warning page lets one through)
        assertEquals("https://localhost:3000", inputToUrl("localhost:3000", true, SEARCH_ENGINES[0]))
        assertEquals("https://192.168.1.1:8080/x", inputToUrl("http://192.168.1.1:8080/x", true, SEARCH_ENGINES[0]))
        assertEquals("http://a.com", inputToUrl("http://a.com", false, SEARCH_ENGINES[0]))
        assertEquals("https://www.google.com/search?q=hello%20world", inputToUrl("hello world", false, SEARCH_ENGINES[0]))
        assertEquals("https://search.yahoo.com/search?p=example.com", inputToUrl("!se example.com", false, SEARCH_ENGINES[5]))
    }

    @Test fun searchAndTitles() {
        assertEquals("react native", searchQueryFromUrl("https://www.google.com/search?q=react+native"))
        assertEquals("cats", searchQueryFromUrl("https://duckduckgo.com/?q=cats"))
        assertEquals(null, searchQueryFromUrl("https://www.google.com/sorry/index?continue=x&q=Eg4token"))
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
        val newer = com.jlees.mibrowser.update.Updates::isNewer
        assertEquals(true, newer("v1.0.1", "1.0.0"))
        assertEquals(true, newer("v1.10.0", "1.9.9"))
        assertEquals(true, newer("2.0", "1.9.9"))
        assertEquals(false, newer("v0.10.3", "1.0.0"))
        assertEquals(false, newer("v1.0.0", "1.0.0"))
        assertEquals(false, newer("v1.0.0-beta", "1.0.0"))
    }

    @Test fun typedAddressesForDevelopmentAndLocalNetworks() {
        fun go(t: String, httpsOnly: Boolean = false) = inputToUrl(t, httpsOnly, SEARCH_ENGINES[0])!!
        val search = "https://www.google.com/search?q="
        // local development: http, never upgraded, ports and paths kept
        assertEquals("http://localhost:3000", go("localhost:3000"))
        assertEquals("http://localhost", go("localhost"))
        assertEquals("https://localhost:3000/api?x=1", go("localhost:3000/api?x=1", httpsOnly = true)) // no local exception
        assertEquals("https://localhost:3000", go("http://localhost:3000", httpsOnly = true))
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
        assertEquals(true, isNotSecure("http://localhost:3000", none))               // no special rule for local addresses
        assertEquals(true, isNotSecure("http://127.0.0.1:8000", none))
        assertEquals(true, isNotSecure("https://self-signed.example/", setOf("self-signed.example"))) // allowed past a cert error
        assertEquals(false, isNotSecure(null, none))
        assertEquals(false, isNotSecure("about:blank", none))
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

    @Test fun reloadsAndRestoresAreNotNewVisits() {
        val url = "https://example.com/a"
        assertEquals(true, isNewVisit(null, url))                       // a new tab's first page
        assertEquals(false, isNewVisit(url, url))                       // reload
        assertEquals(false, isNewVisit("$url/", url))                   // same page, trailing slash
        assertEquals(true, isNewVisit(url, "https://example.com/b"))    // went somewhere else
        assertEquals(false, isNewVisit(RESTORED_VISIT, url + "?fresh=1")) // restored at startup, even if the address changed
        assertEquals(RESTORED_VISIT, repairSaved(com.jlees.mibrowser.data.newTab().copy(historyStack = listOf(url), currentIndex = 0), false).lastVisit)
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

    @Test fun adBlockFilterEngine() {
        val e = com.jlees.mibrowser.adblock.FilterEngine()
        listOf(
            "doubleclick.net",                      // hosts list entry
            "||ads.example.com^",                   // host rule
            "||tracker.io^\$third-party",
            "/banner/ads/*",                         // path pattern
            "||cdn.site.com/ad.js\$script,domain=news.com|~sport.news.com",
            "@@||ads.example.com/allowed^",          // exception
            "@@||safe.org^\$document",              // site allow-listed
            "||evil.com^\$popup",                  // unsupported option: skipped
            "news.com##.sponsor", "~sport.news.com,news.com##.promo", "##.ad-banner", "sport.news.com#@#.ad-banner",
        ).forEach { if (it == "doubleclick.net") e.addHost(it) else e.addRule(it) }
        e.finish()
        val js = com.jlees.mibrowser.adblock.FilterEngine.SCRIPT
        fun b(url: String, page: String?, third: Boolean = true, type: Int = js) = e.blocks(url, java.net.URI(url).host, page, type, third)
        assertEquals(true, b("https://stats.g.doubleclick.net/x", "a.com"))
        assertEquals(true, b("https://ads.example.com/a.gif", "a.com"))
        assertEquals(false, b("https://ads.example.com/allowed/x", "a.com"))          // exception wins
        assertEquals(true, b("https://tracker.io/p", "a.com"))
        assertEquals(false, b("https://tracker.io/p", "tracker.io", third = false))      // first-party: not blocked
        assertEquals(true, b("https://x.com/img/banner/ads/1.png", "a.com"))
        assertEquals(false, b("https://x.com/img/banners/ads/1.png", "a.com"))          // pattern needs the exact path
        assertEquals(true, b("https://cdn.site.com/ad.js", "www.news.com"))
        assertEquals(false, b("https://cdn.site.com/ad.js", "sport.news.com"))          // ~domain
        assertEquals(false, b("https://cdn.site.com/ad.js", "other.com"))               // domain= only
        assertEquals(false, b("https://cdn.site.com/ad.js", "www.news.com", type = com.jlees.mibrowser.adblock.FilterEngine.IMAGE)) // \$script only
        assertEquals(false, b("https://ads.example.com/a.gif", "safe.org"))             // \$document allow
        assertEquals(false, b("https://evil.com/x", "a.com"))
        val css = StringBuilder(); val except = HashSet<String>()
        e.specificCss("www.news.com", css, except)
        assertEquals(true, css.contains(".sponsor{") && css.contains(".promo{"))
        css.clear(); e.specificCss("sport.news.com", css, except)
        assertEquals(false, css.contains(".promo{"))                                     // ~sport.news.com
        assertEquals(true, ".ad-banner" in except)                                       // generic exception for that site
        assertEquals(listOf(".ad-banner"), e.genericSelectors)
        assertEquals("example.co.uk", com.jlees.mibrowser.adblock.AdBlock.registrable("ads.example.co.uk"))
        assertEquals("example.com", com.jlees.mibrowser.adblock.AdBlock.registrable("a.b.example.com"))
        assertEquals("nos.nl", com.jlees.mibrowser.adblock.AdBlock.registrable("www.nos.nl"))           // not "www.nos.nl"
        assertEquals("nos.nl", com.jlees.mibrowser.adblock.AdBlock.registrable("assets.nos.nl"))        // same site: first-party
        assertEquals("alice.github.io", com.jlees.mibrowser.adblock.AdBlock.registrable("alice.github.io")) // shared host: own site
        val one = com.jlees.mibrowser.adblock.FilterEngine().apply { addRule("/x/*"); finish() }        // keyword "x": 1 char
        assertEquals(true, one.blocks("https://a.com/x/1.js", "a.com", "b.com", js, true))
        assertEquals(com.jlees.mibrowser.adblock.FilterEngine.IMAGE, com.jlees.mibrowser.adblock.FilterEngine.typeOf("https://a.com/x.png?y=1", null))
    }

    @Test fun hex() {
        assertEquals(0xFF007AFF, hexColor("#007AFF"))
        assertEquals(0xFF007AFF, hexColor("bad"))
    }

    @Test
    fun searchShortcutsAndCustomEngines() {
        val g = SEARCH_ENGINES[0]
        assertEquals("https://en.wikipedia.org/w/index.php?search=mars", inputToUrl("!w mars", false, g))
        assertEquals("https://www.youtube.com/results?search_query=lofi%20beats", inputToUrl("!yt lofi beats", false, g))
        assertEquals("https://www.youtube.com/results?search_query=cats", inputToUrl("!YT cats", false, g)) // case-insensitive
        assertEquals("https://www.google.com/search?q=lofi%20!yt", inputToUrl("lofi !yt", false, g)) // only at the start
        assertEquals("https://en.wikipedia.org/w/index.php?search=!yt%20x", inputToUrl("!w !yt x", false, g)) // one shortcut
        assertEquals("https://www.google.com/search?q=!nope%20x", inputToUrl("!nope x", false, g)) // unknown: a plain search
        assertEquals("https://www.google.com/search?q=hi!", inputToUrl("hi!", false, g))
        assertEquals("https://www.google.com/search?q=example.com", inputToUrl("!se example.com", false, g)) // !se always searches
        assertEquals("https://www.startpage.com/sp/search?query=x&cat=web",
            inputToUrl("!se x", false, searchEngine(SEARCH_ENGINES.size, "Startpage", "https://www.startpage.com/sp/search?query=%s&cat=web")))
        val custom = searchEngine(SEARCH_ENGINES.size, "Startpage", "https://www.startpage.com/sp/search?query=%s&cat=web")
        assertEquals("Startpage", custom.name)
        assertEquals("https://www.startpage.com/sp/search?query=a%20b&cat=web", inputToUrl("a b", false, custom))
        assertEquals(SEARCH_ENGINES[0], searchEngine(SEARCH_ENGINES.size, "Bad", "not a url")) // invalid custom: falls back
        assertEquals(SEARCH_ENGINES[0], searchEngine(99, "", ""))
        val mine = allBangs(engine = g, custom = mapOf("ebay" to "https://www.ebay.com/sch/i.html?_nkw=%s", "w" to "https://de.wikipedia.org/w/index.php?search=%s"))
        assertEquals("https://www.ebay.com/sch/i.html?_nkw=lamp", inputToUrl("!ebay lamp", false, g, mine))
        assertEquals("https://de.wikipedia.org/w/index.php?search=mars", inputToUrl("!w mars", false, g, mine)) // yours win
        assertEquals(2, typedBangLength("!w ", mine))    // highlighted once a space follows
        assertEquals(0, typedBangLength("!w", mine))
        assertEquals(5, typedBangLength("!ebay mars", mine))
        assertEquals(0, typedBangLength("!nope x", mine))
        assertEquals(false, isValidBangKey("Has Space"))
    }

    @Test
    fun trackingParametersAreStripped() {
        assertEquals("https://a.com/p?id=7&x=%20y#top", stripTracking("https://a.com/p?utm_source=nl&id=7&fbclid=AbC&x=%20y&UTM_Medium=e#top"))
        assertEquals("https://a.com/p", stripTracking("https://a.com/p?gclid=1&utm_campaign=z"))       // nothing left: no "?"
        assertEquals("https://a.com/p?q=utm_source", stripTracking("https://a.com/p?q=utm_source"))     // values aren't touched
        assertEquals("https://a.com/search?q=mars", stripTracking("https://a.com/search?q=mars"))
        assertEquals("mibrowser://x?utm_source=y", stripTracking("mibrowser://x?utm_source=y"))         // web links only
    }

    @Test fun siteIconsKeepTheMostRecentlyUsed() {
        val icons = listOf("old.png" to 1L, "newest.png" to 30L, "mid.png" to 20L, "older.png" to 5L)
        assertEquals(listOf("older.png", "old.png"), com.jlees.mibrowser.data.leastRecentlyUsed(icons, keep = 2))
        assertEquals(emptyList<String>(), com.jlees.mibrowser.data.leastRecentlyUsed(icons, keep = 500)) // under the cap: none go
    }

    @Test fun snapshotsAreSkippedOnlyWhenThePageIsUnchanged() {
        val key = com.jlees.mibrowser.tabs.snapshotKey("https://a.com", 0, 400, 1080, 2000, 9000, 100)
        val scrolled = com.jlees.mibrowser.tabs.snapshotKey("https://a.com", 0, 900, 1080, 2000, 9000, 100)
        assertFalse(com.jlees.mibrowser.tabs.needsSnapshot(key, key, hasSnapshot = true))      // same page, same place: skip
        assertTrue(com.jlees.mibrowser.tabs.needsSnapshot(scrolled, key, hasSnapshot = true))  // scrolled since: take one
        assertTrue(com.jlees.mibrowser.tabs.needsSnapshot(key, key, hasSnapshot = false))      // none kept (e.g. cleared): take one
        assertTrue(com.jlees.mibrowser.tabs.needsSnapshot(key, null, hasSnapshot = true))      // never taken this session
    }

    @Test fun searchShortcutsAreFoundFromResultsPages() {
        // the search in a results page's address becomes %s; the page's other parameters stay
        assertEquals("https://www.ebay.com/sch/i.html?_from=R40&_nkw=%s&_sacat=0",
            com.jlees.mibrowser.search.searchTemplateFrom("https://www.ebay.com/sch/i.html?_from=R40&_nkw=headphones&_sacat=0"))
        assertEquals("https://duckduckgo.com/?q=%s", com.jlees.mibrowser.search.searchTemplateFrom("https://duckduckgo.com/?q=cats+and+dogs"))
        assertEquals(null, com.jlees.mibrowser.search.searchTemplateFrom("https://example.com/about"))   // not a results page
        assertEquals(null, com.jlees.mibrowser.search.searchTemplateFrom("https://example.com/?q="))     // an empty search
        assertEquals("ebay", com.jlees.mibrowser.search.suggestedShortcutKey("https://www.ebay.com/sch/i.html"))
    }

    @Test fun searchShortcutsAreFoundFromWhatSitesDeclare() {
        val html = """<head><link rel="search" type="application/opensearchdescription+xml" href="/opensearch.xml" title="Wiki"></head>"""
        assertEquals("https://en.wikipedia.org/opensearch.xml", com.jlees.mibrowser.search.openSearchLink(html, "https://en.wikipedia.org/wiki/Mars"))
        val xml = """<OpenSearchDescription><Url type="application/x-suggestions+json" template="https://x/s?q={searchTerms}"/>""" +
            """<Url type="text/html" method="get" template="https://en.wikipedia.org/w/index.php?title=Special:Search&amp;search={searchTerms}&amp;page={startPage?}"/></OpenSearchDescription>"""
        assertEquals("https://en.wikipedia.org/w/index.php?title=Special:Search&search=%s", com.jlees.mibrowser.search.templateFromOpenSearch(xml))
        assertEquals(null, com.jlees.mibrowser.search.openSearchLink("<head></head>", "https://example.com"))
    }

    @Test fun readAtMostStopsAtTheCap() {
        val data = ByteArray(20_000) { it.toByte() }
        val (all, moreAll) = com.jlees.mibrowser.logic.readAtMost(data.inputStream(), 50_000)
        assertEquals(20_000, all.size); assertFalse(moreAll)            // under the cap: everything, nothing more
        val (part, more) = com.jlees.mibrowser.logic.readAtMost(data.inputStream(), 10_000)
        assertEquals(10_000, part.size); assertTrue(more)               // over: cut at the cap, and says there was more
        assertFalse(com.jlees.mibrowser.logic.readAtMost(data.inputStream(), 20_000).second) // exactly the cap: nothing more
    }
}
