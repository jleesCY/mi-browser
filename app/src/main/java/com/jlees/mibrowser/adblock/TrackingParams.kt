// Link cleaning: tracking parameters removed from links you open, copy or share (Settings → Ad Blocking).
package com.jlees.mibrowser.adblock

import com.jlees.mibrowser.BrowserViewModel

/** Query parameters that only exist to track the click (ad, email and social campaign IDs). */
private val TRACKING_PARAMS = setOf(
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_name", "utm_reader", "utm_social", "utm_brand",
    "fbclid", "gclid", "dclid", "gbraid", "wbraid", "gclsrc", "msclkid", "yclid", "twclid", "ttclid", "igshid", "igsh", "li_fat_id",
    "mc_cid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "s_cid", "srsltid",
)

/** [url] without tracking parameters ([TRACKING_PARAMS]); everything else (order, encoding, fragment) is kept. */
fun stripTracking(url: String): String {
    val hash = url.indexOf('#').let { if (it < 0) url.length else it } // a '?' inside the fragment isn't a query
    val q = url.indexOf('?').takeIf { it in 0 until hash } ?: return url
    if (!(url.startsWith("https://") || url.startsWith("http://"))) return url
    val kept = url.substring(q + 1, hash).split('&').filter { p -> p.isNotEmpty() && p.substringBefore('=').lowercase() !in TRACKING_PARAMS }
    val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
    return url.substring(0, q) + query + url.substring(hash)
}


/** [url] without tracking parameters when that setting is on (links opened, copied or shared). */
fun BrowserViewModel.cleanLink(url: String) = if (settings.stripTrackingParams) stripTracking(url) else url
