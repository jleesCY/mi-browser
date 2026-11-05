// Incognito storage. Every incognito tab (with its pop-ups) has its own WebView profile, so cookies, storage, cache,
// service workers and permissions never cross between it and normal tabs or other incognito tabs. A profile ends as
// soon as nothing uses it; leftovers (after a crash) are deleted at startup.
package com.jlees.mibrowser.incognito

import android.os.Process
import android.view.View
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.data.INCOGNITO_PROFILE_PREFIX

/**
 * @param tabProfiles the profiles of the open tabs (a profile in use is never ended)
 * @param onEnded called when a profile ends, so session state kept for it elsewhere (sign-ins, permissions,
 *   pop-up allowances, certificate exceptions) is forgotten too
 * @param serviceWorkers filters the service workers of incognito profiles like the normal one's
 */
class IncognitoProfiles(
    private val tabProfiles: () -> Collection<String>,
    private val onEnded: (profile: String) -> Unit,
    private val serviceWorkers: ServiceWorkerClient,
) {
    /** WebView (by tab id) → its profile. A pop-up's WebView can outlive its tab entry for a moment. */
    private val viewProfile = HashMap<String, String>()
    /** Profiles loaded in this process (WebView can't delete them until it ends) and those already wiped. */
    private val loaded = HashSet<String>()
    private val wiped = HashSet<String>()

    /** The profile of tab [id]'s WebView, if it's incognito. */
    fun profileOfView(id: String): String? = viewProfile[id]

    /**
     * Puts [wv] (tab [id]) in [profile]. Must run before anything else touches the WebView, and never falls back to
     * the normal profile. Autofill services (password managers) are kept out: nothing typed is saved or filled in.
     */
    fun attach(id: String, wv: WebView, profile: String) {
        check(incognitoSupported) { "incognito needs WebView profiles" }
        val p = ProfileStore.getInstance().getOrCreateProfile(profile)
        WebViewCompat.setProfile(wv, profile)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            runCatching { p.serviceWorkerController.setServiceWorkerClient(serviceWorkers) }
        }
        loaded += profile
        viewProfile[id] = profile
        wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    /**
     * Tab [id]'s WebView is about to be destroyed. If it was its profile's last WebView, the profile's HTTP cache is
     * cleared now (that needs a live WebView). Returns whether the WebView was incognito.
     */
    fun detach(id: String, wv: WebView?): Boolean {
        val p = viewProfile.remove(id) ?: return false
        if (wv != null && p !in viewProfile.values) wv.clearCache(true)
        return true
    }

    /** [wv]'s cookie store: its profile's, so an incognito tab never touches the normal cookie jar. */
    fun cookies(wv: WebView): CookieManager =
        if (incognitoSupported) WebViewCompat.getProfile(wv).cookieManager else CookieManager.getInstance()

    /**
     * Ends every incognito profile no tab or WebView uses (all of them when [all]). A profile loaded in this process
     * can't be deleted until the process ends, so it is wiped through its own APIs instead (cookies, all site storage
     * incl. service workers and IndexedDB, location permissions; its HTTP cache went with its last WebView) and its
     * folder is deleted at the next launch, by this same call at startup.
     */
    fun release(all: Boolean = false) {
        if (!incognitoSupported) return
        val inUse = if (all) emptySet() else tabProfiles().toSet() + viewProfile.values
        val store = ProfileStore.getInstance()
        store.allProfileNames.filter { it.startsWith(INCOGNITO_PROFILE_PREFIX) && it !in inUse && it !in wiped }.forEach { name ->
            if (name in loaded) {
                store.getProfile(name)?.run {
                    cookieManager.removeAllCookies(null); cookieManager.flush()
                    webStorage.deleteAllData()
                    geolocationPermissions.clearAll()
                }
                wiped += name
            } else runCatching { store.deleteProfile(name) }
            onEnded(name)
        }
    }

    /**
     * Leaving the app after using incognito: end the process, so the next launch starts fresh and deletes the wiped
     * profile folders (WebView keeps a profile loaded for the life of the process). [flush] saves normal data first.
     */
    fun endProcessIfUsed(flush: () -> Unit) {
        if (loaded.isEmpty()) return
        flush()
        Process.killProcess(Process.myPid())
    }
}
