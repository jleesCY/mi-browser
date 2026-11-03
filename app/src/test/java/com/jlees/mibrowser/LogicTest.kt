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
        assertEquals("https://www.google.com/s2/favicons?domain=github.com&sz=128", faviconUrl("github.com"))
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

    @Test fun hex() {
        assertEquals(0xFF007AFF, hexColor("#007AFF"))
        assertEquals(0xFF007AFF, hexColor("bad"))
    }
}
