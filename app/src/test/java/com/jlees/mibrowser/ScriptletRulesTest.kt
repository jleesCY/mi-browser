package com.jlees.mibrowser

import com.jlees.mibrowser.adblock.Scriptlet
import com.jlees.mibrowser.adblock.ScriptletIndex
import com.jlees.mibrowser.adblock.canonicalScriptlet
import com.jlees.mibrowser.adblock.parseScriptlet
import com.jlees.mibrowser.adblock.preprocessFilterList
import com.jlees.mibrowser.adblock.scriptletArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Scriptlet rules (uBlock Origin's "##+js(…)"): reading them, and which run on a site. */
class ScriptletRulesTest {
    @Test fun arguments() {
        // commas split, "\," stays, spaces trim, matching quotes go
        assertEquals(listOf("set", "a.b", "undefined"), scriptletArgs("set, a.b, undefined"))
        assertEquals(listOf("rpnt", "script", "a,b", ""), scriptletArgs("rpnt, script, a\\,b, "))
        assertEquals(listOf("trusted-rpfr", "\"adPlacements\"", "\"no_ads\"", "player?"), scriptletArgs("trusted-rpfr, '\"adPlacements\"', '\"no_ads\"', player?"))
        // short names become full ones; trailing empty arguments go
        assertEquals(Scriptlet("abort-on-property-read", listOf("adsbygoogle")), parseScriptlet("aopr, adsbygoogle, "))
        assertEquals("set-constant", canonicalScriptlet("set"))
        assertEquals("addEventListener-defuser", canonicalScriptlet("aeld"))
        assertEquals("no-window-open-if", canonicalScriptlet("nowoif.js"))
        assertEquals(null, parseScriptlet(""))
    }

    @Test fun sites() {
        val i = ScriptletIndex()
        listOf(
            "example.com##+js(aopr, a)",
            "news.example.com,other.org##+js(set, b, true)",
            "google.*##+js(nowoif)",
            "##+js(nobab)",
            "*,~quiet.net##+js(nofab)",
            "~skip.example.com,example.com##+js(aeld, click)",
            "example.com#@#+js(set, b, true)",
            "news.example.com#@#+js(aopr, a)",
            "off.example.com#@#+js()",
            "example.com##.ad", // not a scriptlet rule
        ).forEach(i::add)
        fun names(h: String) = i.forHost(h).map { it.name + it.args }
        // a site and its subdomains; rules for everywhere too
        assertEquals(listOf("abort-on-property-read[a]", "addEventListener-defuser[click]", "bab-defuser[]", "fuckadblock-defuser[]"), names("www.example.com"))
        // an exception turns one off there (and under it); "~site" leaves it out there
        assertEquals(listOf("addEventListener-defuser[click]", "bab-defuser[]", "fuckadblock-defuser[]"), names("news.example.com"))
        assertEquals(listOf("abort-on-property-read[a]", "bab-defuser[]", "fuckadblock-defuser[]"), names("skip.example.com"))
        // "+js()": none at all
        assertEquals(emptyList<String>(), names("off.example.com"))
        // "name.*": the site under any top-level domain
        assertTrue("no-window-open-if[]" in names("www.google.co.uk"))
        assertTrue("no-window-open-if[]" in names("google.de"))
        assertTrue("no-window-open-if[]" !in names("notgoogle.com"))
        assertEquals(listOf("set-constant[b, true]", "bab-defuser[]", "fuckadblock-defuser[]"), names("other.org"))
        assertEquals(listOf("bab-defuser[]"), names("quiet.net"))
    }

    @Test fun listSectionsForOtherBrowsersAreLeftOut() {
        val list = """
            a##+js(aopr, x)
            !#if env_firefox
            b##+js(aopr, firefox-only)
            !#else
            c##+js(aopr, not-firefox)
            !#endif
            !#if env_chromium && env_mobile
            d##+js(aopr, chromium-phone)
            !#endif
            !#if !env_mobile
            e##+js(aopr, desktop)
            !#endif
            !#include filters-2024.txt
        """.trimIndent().lineSequence()
        assertEquals(listOf("a##+js(aopr, x)", "c##+js(aopr, not-firefox)", "d##+js(aopr, chromium-phone)"), preprocessFilterList(list).toList())
    }

    @Test fun youtubeGetsItsAdScriptletsFromTheBundledList() {
        val bundled = File("src/main/assets/filters/scriptlets.txt").takeIf { it.exists() } ?: File("app/src/main/assets/filters/scriptlets.txt")
        val i = ScriptletIndex().apply { bundled.forEachLine(action = ::add) }
        assertTrue(i.size > 5000)
        val yt = i.forHost("m.youtube.com")
        // the ad data is removed from YouTube's player responses, as uBlock does
        assertTrue(yt.any { it.name == "set-constant" && it.args.firstOrNull() == "ytInitialPlayerResponse.adPlacements" })
        assertTrue(yt.any { it.name == "json-prune" && it.args.firstOrNull()?.contains("playerResponse.adPlacements") == true })
    }
}
