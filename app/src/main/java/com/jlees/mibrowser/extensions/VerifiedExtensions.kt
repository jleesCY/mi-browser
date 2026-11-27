// Extensions tested and verified in mi: each one checked by hand on a phone, every main feature, before it's listed
// here. They're recommended (Get Extensions shows them first, marked "Tested in mi"); any other extension can still
// be added, rated by what it uses (ExtensionRules). The list grows with mi's updates, as extensions are tested and, where
// one needs it, given its own rules. Matched by its Firefox Add-ons slug, so a copy
// from somewhere else isn't taken for the verified one.
package com.jlees.mibrowser.extensions

/**
 * An extension verified in mi: its name, where it comes from, and how Get Extensions
 * shows it ([summary]: its store's one-line description; [iconUrl]: its store icon).
 * Pages and extensions run in the phone's Android System WebView, which each phone updates on its own, so each entry
 * also says which WebView versions (Chromium major versions) it suits: [testedWebView] is the one it was tested on;
 * below [worksFrom] it won't work (it needs something that version lacks); below [problemsBelow] it may have problems.
 * Work them out when verifying, from what it uses (its manifest version, the web features its code needs).
 * [limitations]: what testing found it can't do, or does differently, in mi, one point each (no full stop): shown as
 * Known Limitations, above the other warnings, before it's added and on its page.
 */
data class VerifiedExtension(
    val name: String, val amoSlug: String,
    val summary: String = "", val iconUrl: String? = null,
    val testedWebView: Int, val worksFrom: Int, val problemsBelow: Int = worksFrom,
    val limitations: List<String> = emptyList(),
) {
    val source: InstallSource get() = InstallSource.Amo(amoSlug)
}

/** How a verified extension suits this phone's WebView. */
enum class WebViewFit { OK, MAY_HAVE_PROBLEMS, WONT_WORK, UNKNOWN }

/** [v] on WebView major version [major] (null: unknown). */
fun webViewFit(v: VerifiedExtension, major: Int?): WebViewFit = when {
    major == null -> WebViewFit.UNKNOWN
    major < v.worksFrom -> WebViewFit.WONT_WORK
    major < v.problemsBelow -> WebViewFit.MAY_HAVE_PROBLEMS
    else -> WebViewFit.OK
}

/** The warning to show for [v] on WebView [major], or null when it suits this phone. */
fun webViewWarning(v: VerifiedExtension, major: Int?): String? = when (webViewFit(v, major)) {
    WebViewFit.WONT_WORK -> "${v.name} won't work with this phone's Android System WebView (version $major): it needs version ${v.worksFrom} or newer. Update Android System WebView from the Play Store."
    WebViewFit.MAY_HAVE_PROBLEMS -> "${v.name} may have problems with this phone's Android System WebView (version $major); it was verified on version ${v.testedWebView}. Updating Android System WebView from the Play Store should fix that."
    else -> null
}

/** This phone's WebView version ("133.0.6943.137"), or null if it can't be read. */
fun webViewVersion(ctx: android.content.Context): String? = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName

/** This phone's WebView major version (Chromium's), or null if it can't be read. */
fun webViewMajor(ctx: android.content.Context): Int? = webViewVersion(ctx)?.substringBefore('.')?.toIntOrNull()

/**
 * Add an entry only after testing it in mi on a phone: install, its button and popup, its options page, what it does
 * on pages, and incognito if it allows that. Say in a comment above it what was checked, in which mi version, and put
 * anything found that it can't do in mi in [VerifiedExtension.limitations].
 */
val VERIFIED_EXTENSIONS: List<VerifiedExtension> = listOf(
    // Tested in mi 2.0. Firefox Add-ons build. Checked: theming (dynamic mode) on
    // plain and strict-policy sites (Wikipedia, Hacker News, Craigslist, PyPI), its popup (on/off for a site and for
    // all, brightness, all options), settings kept after mi restarts; it leaves sites already dark as they are. Needs
    // quirks/DarkReader.kt. Range: mi's engine needs WebView 87 (document-start scripts, message listeners); Dark
    // Reader's current Chrome builds ask for 106.
    VerifiedExtension("Dark Reader", amoSlug = "darkreader", testedWebView = 133, worksFrom = 87, problemsBelow = 106,
        summary = "Dark mode for every website. Take care of your eyes, use dark theme for night and daily browsing.",
        // (without ?modified=…, so it stays the current icon)
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/855/855413-64.png"),
    // Tested in mi 2.0. Firefox Add-ons build. Checked: blocking scripts and fetches, hiding page elements (generic and
    // site-specific), its popup (counts, turning it off for a site, its icon going grey), its blocked-page screen and
    // Proceed, the dashboard (settings, filter lists). It filters once its background has started (seconds after mi
    // does); mi's own ad blocking covers the moments before. Range: it needs structuredClone (98); its in-page scripts
    // need Array.toSorted (110).
    VerifiedExtension("uBlock Origin", amoSlug = "ublock-origin", testedWebView = 133, worksFrom = 98, problemsBelow = 110,
        summary = "Finally, an efficient wide-spectrum content blocker. Easy on CPU and memory.",
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png",
        limitations = listOf(
            "It starts filtering a little while after mi opens; mi's own ad blocking covers that time",
            "On a heavy page, a request it hasn't answered within 1.5 seconds loads anyway (uBlock Origin Lite is steadier in mi)",
        )),
    // Tested in mi 2.0. Firefox Add-ons build (Manifest V3: its background is a module; its rules matched by mi itself,
    // so pages don't wait on it). Checked: blocking scripts and fetches, hiding page elements (on pages loaded once its
    // first start has registered its scripts), its popup's filtering mode down to none and back up to optimal, applied
    // at once. Range: its code needs Promise.withResolvers (119); Firefox for Android 128 is its minimum.
    VerifiedExtension("uBlock Origin Lite", amoSlug = "ublock-origin-lite", testedWebView = 158, worksFrom = 119, problemsBelow = 122,
        summary = "An efficient content blocker. Blocks ads, trackers, miners, and more immediately upon installation.",
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/2887/2887803-64.png",
        limitations = listOf("Right after it's added, it hides page elements only on pages loaded once it has finished setting up")),
    // Tested in mi 2.0. Firefox Add-ons build. Checked: its setup page (accepting closes it and restarts Keepa, which then
    // saves its settings through cookies on keepa.com/extension), the price-history chart on an Amazon product page.
    // Range: its code needs ||=, ??= and String.replaceAll (85).
    VerifiedExtension("Keepa", amoSlug = "keepa", testedWebView = 158, worksFrom = 85,
        summary = "Price history charts and price drop alerts for Amazon.",
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/329/329119-64.png"),
    // Tested in mi 2.0. Firefox Add-ons build. Checked: a sponsor segment skipped on m.youtube.com (YouTube requires
    // Trusted Types, which mi lifts for the site once SponsorBlock's innerHTML is refused), its popup off YouTube.
    // Range: its background needs Array.at (92); its popup needs structuredClone (98).
    VerifiedExtension("SponsorBlock", amoSlug = "sponsorblock", testedWebView = 158, worksFrom = 92, problemsBelow = 98,
        summary = "Easily skip YouTube video sponsors, using a shared database of reported segments.",
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/2590/2590937-64.png",
        limitations = listOf("On YouTube, mi turns off one of the site's script protections (Trusted Types) so SponsorBlock can add its buttons")),
    // Tested in mi 2.0. Firefox Add-ons build. Checked: its welcome page, trackers seen and blocked on a news site
    // (its badge counts them), its popup listing them. Range: its code needs Object.hasOwn (93).
    VerifiedExtension("Privacy Badger", amoSlug = "privacy-badger17", testedWebView = 158, worksFrom = 93,
        summary = "Stop companies from spying on you. Made by the digital rights nonprofit EFF.",
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/506/506646-64.png"),
    // Tested in mi 2.0. Firefox Add-ons build. Checked: tracking parameters mi doesn't strip itself (otm_source, cmpid)
    // removed from a link before it loads, its popup. Range: nothing newer than mi's engine needs (87).
    VerifiedExtension("ClearURLs", amoSlug = "clearurls", testedWebView = 158, worksFrom = 87,
        summary = "Automatically removes tracking parameters from links.",
        iconUrl = "https://addons.mozilla.org/user-media/addon_icons/839/839767-64.png"),
)

/** The verified entry for an install source (a store page), or null. */
fun verifiedFor(source: InstallSource?, list: List<VerifiedExtension> = VERIFIED_EXTENSIONS): VerifiedExtension? = when (source) {
    is InstallSource.Amo -> list.firstOrNull { it.amoSlug == source.slug }
    else -> null
}

/** Whether this installed extension is a verified one (by the store page it came from). */
val InstalledExtension.verified: Boolean get() = verifiedFor(storeSource(source)) != null
