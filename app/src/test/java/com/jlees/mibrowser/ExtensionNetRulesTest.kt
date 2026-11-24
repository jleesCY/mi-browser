package com.jlees.mibrowser

import com.jlees.mibrowser.extensions.DnrAction
import com.jlees.mibrowser.extensions.DnrCondition
import com.jlees.mibrowser.extensions.DnrHeader
import com.jlees.mibrowser.extensions.DnrIndex
import com.jlees.mibrowser.extensions.DnrOutcome
import com.jlees.mibrowser.extensions.DnrParam
import com.jlees.mibrowser.extensions.DnrQueryTransform
import com.jlees.mibrowser.extensions.DnrRedirect
import com.jlees.mibrowser.extensions.DnrRule
import com.jlees.mibrowser.extensions.DnrTransform
import com.jlees.mibrowser.extensions.NetRequest
import com.jlees.mibrowser.extensions.applyHeaderOps
import com.jlees.mibrowser.extensions.dnrDecide
import com.jlees.mibrowser.extensions.isThirdParty
import com.jlees.mibrowser.extensions.redirectTarget
import com.jlees.mibrowser.extensions.requestType
import com.jlees.mibrowser.extensions.siteOf
import com.jlees.mibrowser.extensions.urlFilterRegex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionNetRulesTest {
    private fun req(url: String, type: String = "script", initiator: String? = "https://news.example.com/a", method: String = "GET") = NetRequest(url, method, type, initiator, 1)
    private fun rule(id: Int, type: String, c: DnrCondition, priority: Int = 1, redirect: DnrRedirect? = null, headers: List<DnrHeader>? = null) =
        DnrRule(id, priority, DnrAction(type, redirect, requestHeaders = headers), c)
    private val origin = { id: String -> "https://$id.ext.mi.invalid" }

    @Test fun types() {
        assertEquals("main_frame", requestType(true, "document", null, "https://a.com/"))
        assertEquals("sub_frame", requestType(false, "iframe", null, "https://a.com/"))
        assertEquals("xmlhttprequest", requestType(false, "empty", null, "https://a.com/api"))
        assertEquals("stylesheet", requestType(false, null, "text/css,*/*", "https://a.com/x"))
        assertEquals("image", requestType(false, null, null, "https://a.com/x.PNG?v=1"))
        assertEquals("script", requestType(false, null, "*/*", "https://a.com/app.js"))
        assertEquals("other", requestType(false, null, "*/*", "https://a.com/data"))
    }

    @Test fun sites() {
        assertEquals("example.com", siteOf("a.b.example.com"))
        assertEquals("bbc.co.uk", siteOf("www.bbc.co.uk"))
        assertTrue(isThirdParty("https://ads.tracker.net/p.js", "https://news.example.com/"))
        assertFalse(isThirdParty("https://cdn.example.com/p.js", "https://news.example.com/"))
        assertFalse(isThirdParty("https://a.com/", null)) // no page: not third-party
    }

    @Test fun urlFilters() {
        val f = urlFilterRegex("||ads.example.com^", false)
        assertTrue(f.containsMatchIn("https://ads.example.com/banner.js"))
        assertTrue(f.containsMatchIn("https://x.ads.example.com/"))
        assertFalse(f.containsMatchIn("https://notads.example.com/"))
        assertFalse(f.containsMatchIn("https://ads.example.community/"))
        assertTrue(urlFilterRegex("|https://a.com/x", false).containsMatchIn("https://a.com/x/y"))
        assertFalse(urlFilterRegex("|https://a.com/x", false).containsMatchIn("http://b.com/?u=https://a.com/x"))
        assertTrue(urlFilterRegex("/ad-*.gif|", false).containsMatchIn("https://a.com/ad-123.gif"))
        assertFalse(urlFilterRegex("/ad-*.gif|", false).containsMatchIn("https://a.com/ad-123.gif?x"))
        assertTrue(urlFilterRegex("BANNER", false).containsMatchIn("https://a.com/banner"))
        assertFalse(urlFilterRegex("BANNER", true).containsMatchIn("https://a.com/banner"))
    }

    @Test fun decisions() {
        val block = rule(1, "block", DnrCondition(urlFilter = "||tracker.net^", domainType = "thirdParty"))
        val allow = rule(2, "allow", DnrCondition(urlFilter = "||tracker.net/ok^"), priority = 2)
        val index = DnrIndex(listOf("e" to block, "e" to allow))
        val r1 = req("https://tracker.net/p.js")
        assertEquals(DnrOutcome.Block("e"), dnrDecide(index.matches(r1), r1, origin))
        // a higher-priority allow wins
        val r2 = req("https://tracker.net/ok/p.js")
        assertEquals(DnrOutcome.Allow("e"), dnrDecide(index.matches(r2), r2, origin))
        // first-party: the block rule's domainType doesn't match
        val r3 = req("https://tracker.net/p.js", initiator = "https://www.tracker.net/")
        assertEquals(DnrOutcome.None, dnrDecide(index.matches(r3), r3, origin))
        // rules without resourceTypes skip the main frame
        val r4 = req("https://tracker.net/", type = "main_frame")
        assertEquals(DnrOutcome.None, dnrDecide(index.matches(r4), r4, origin))
        // same priority: allow beats block
        val tie = DnrIndex(listOf("a" to rule(1, "block", DnrCondition(urlFilter = "x.com")), "b" to rule(1, "allow", DnrCondition(urlFilter = "x.com"))))
        val r5 = req("https://x.com/a")
        assertEquals(DnrOutcome.Allow("b"), dnrDecide(tie.matches(r5), r5, origin))
        // conditions: methods, request domains, initiators, excluded types
        val c = rule(1, "block", DnrCondition(requestDomains = listOf("cdn.com"), initiatorDomains = listOf("shop.com"), requestMethods = listOf("post"), excludedResourceTypes = listOf("image")))
        val ci = DnrIndex(listOf("e" to c))
        assertEquals(1, ci.matches(NetRequest("https://img.cdn.com/x", "POST", "xmlhttprequest", "https://www.shop.com/", 1)).size)
        assertEquals(0, ci.matches(NetRequest("https://img.cdn.com/x", "GET", "xmlhttprequest", "https://www.shop.com/", 1)).size)
        assertEquals(0, ci.matches(NetRequest("https://img.cdn.com/x", "POST", "image", "https://www.shop.com/", 1)).size)
        assertEquals(0, ci.matches(NetRequest("https://img.cdn.com/x", "POST", "xmlhttprequest", "https://other.com/", 1)).size)
    }

    @Test fun redirectsAndHeaders() {
        val up = rule(1, "upgradeScheme", DnrCondition(urlFilter = "||a.com^"))
        val r = req("http://a.com/x")
        assertEquals(DnrOutcome.Redirect("e", "https://a.com/x"), dnrDecide(DnrIndex(listOf("e" to up)).matches(r), r, origin))
        // to a file of the extension
        assertEquals("https://e.ext.mi.invalid/res/jquery.js", redirectTarget(rule(1, "redirect", DnrCondition(), redirect = DnrRedirect(extensionPath = "/res/jquery.js")), "https://cdn.com/jquery.js", origin("e")))
        // regex substitution
        assertEquals("https://old.reddit.com/r/x", redirectTarget(rule(1, "redirect", DnrCondition(regexFilter = "^https://www\\.reddit\\.com/(.*)"), redirect = DnrRedirect(regexSubstitution = "https://old.reddit.com/\\1")), "https://www.reddit.com/r/x", ""))
        // transform: drop tracking parameters, keep the rest
        val clean = rule(1, "redirect", DnrCondition(), redirect = DnrRedirect(transform = DnrTransform(queryTransform = DnrQueryTransform(removeParams = listOf("utm_source"), addOrReplaceParams = listOf(DnrParam("ref", "mi"))))))
        assertEquals("https://a.com/p?id=1&ref=mi", redirectTarget(clean, "https://a.com/p?utm_source=x&id=1", ""))
        // header operations
        val h = applyHeaderOps(listOf("User-Agent" to "x", "Cookie" to "a=1"), listOf(DnrHeader("user-agent", "set", "y"), DnrHeader("cookie", "append", "b=2"), DnrHeader("Referer", "remove")))
        assertEquals(listOf("Cookie" to "a=1; b=2", "user-agent" to "y"), h)
    }

    @Test fun sitePoliciesAllowExtensionsFiles() {
        val p = "default-src 'self'; script-src 'nonce-x' 'strict-dynamic'; style-src 'self' 'unsafe-inline' data:; font-src 'self' data:; object-src 'none'; frame-src 'none'"
        val out = com.jlees.mibrowser.extensions.allowExtensionsIn(p)
        // what loads styles, fonts, frames (and the default) gets mi's extension address; 'none' gives way to it
        assertEquals("default-src 'self' https://*.ext.mi.invalid; script-src 'nonce-x' 'strict-dynamic'; style-src 'self' 'unsafe-inline' data: https://*.ext.mi.invalid; " +
            "font-src 'self' data: https://*.ext.mi.invalid; object-src 'none'; frame-src https://*.ext.mi.invalid", out)
        // once is enough
        assertEquals(out, com.jlees.mibrowser.extensions.allowExtensionsIn(out))
        // several policies, each changed
        assertEquals("img-src 'self' https://*.ext.mi.invalid, img-src https: https://*.ext.mi.invalid", com.jlees.mibrowser.extensions.allowExtensionsIn("img-src 'self', img-src https:"))
    }

    @Test fun extensionSockets() {
        // reached (host permissions, cookies) at the matching http(s) address
        assertEquals("https://capi.grammarly.com/freews", com.jlees.mibrowser.extensions.httpOfSocket("wss://capi.grammarly.com/freews"))
        assertEquals("http://localhost:8080/x", com.jlees.mibrowser.extensions.httpOfSocket("ws://localhost:8080/x"))
        assertEquals(null, com.jlees.mibrowser.extensions.httpOfSocket("https://example.com/"))
        // a page may only close with 1000 or its own 3000–4999 codes
        assertEquals(1000, com.jlees.mibrowser.extensions.socketCloseCode(null))
        assertEquals(4001, com.jlees.mibrowser.extensions.socketCloseCode(4001))
        assertEquals(1000, com.jlees.mibrowser.extensions.socketCloseCode(1006))
    }
}
