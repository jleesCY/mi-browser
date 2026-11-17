// Clearing what sites leave behind: all of it (Clear Browsing Data, Wipe All Data) or one site's (Site settings).
package com.jlees.mibrowser.web

import android.app.Activity
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.WebViewCompat
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.adblock.AdBlock
import com.jlees.mibrowser.data.clearBrowsingTraces
import com.jlees.mibrowser.incognito.IncognitoProfiles

class BrowsingData(
    private val activity: Activity, private val vm: BrowserViewModel, private val incognito: IncognitoProfiles,
    /** What was granted this session goes too: sign-ins, camera/mic/location. */
    private val forgetGrants: () -> Unit,
) {
    /** The HTTP cache is cleared through a WebView: with none open (e.g. at startup), the next one created does it. */
    private var cacheClearPending = false

    /** Cookies, site storage (localStorage, IndexedDB…), HTTP cache and saved form/auth data, in every normal tab. */
    fun clearAll(views: Collection<WebView>) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        views.forEach { it.clearCache(true); it.clearFormData() }
        if (views.isEmpty()) cacheClearPending = true
        @Suppress("DEPRECATION") WebViewDatabase.getInstance(activity).apply { clearHttpAuthUsernamePassword(); clearFormData() }
        forgetGrants()
        vm.clearBrowsingTraces() // tab snapshots, site icons, mi's own HTTP cache
    }

    /** A new (normal) WebView: clears the cache if a clear came while none was open. */
    fun created(wv: WebView) { if (cacheClearPending) { wv.clearCache(true); cacheClearPending = false } }

    /**
     * Clears [url]'s site, as seen from [wv] (null: no live WebView; [incognitoTab]: its profile's storage): its cookies
     * (WebView can't delete one site's cookies directly, so each is expired, for the host and its registrable domain)
     * and its storage (local storage, IndexedDB, caches, service workers), then reloads. Cookies set on other paths
     * than / can survive.
     */
    fun clearSite(wv: WebView?, incognitoTab: Boolean, url: String) {
        val u = Uri.parse(url)
        val host = u.host ?: return
        val origin = "${u.scheme}://$host"
        val cm = wv?.let { incognito.cookies(it) } ?: CookieManager.getInstance()
        val domains = listOf(host, AdBlock.registrable(host)).distinct()
        cm.getCookie(origin)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            cm.setCookie(origin, "$name=; Max-Age=0; Path=/")
            domains.forEach { d -> cm.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$d") }
        }
        cm.flush()
        val storage = if (wv != null && incognitoTab) WebViewCompat.getProfile(wv).webStorage else WebStorage.getInstance()
        storage.deleteOrigin(origin)
        wv?.reload()
    }
}
