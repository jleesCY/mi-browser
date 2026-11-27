// Containers' storage: each container's tabs browse in its own WebView profile ("container-<id>"), kept on the phone
// like the normal one, so its cookies, sign-ins, site storage, cache and service workers stay apart from the normal
// tabs' and every other container's. A deleted container's profile is deleted (or, while WebView still has it loaded,
// emptied, and deleted at the next launch).
package com.jlees.mibrowser.containers

import android.webkit.ServiceWorkerClient
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.incognito.incognitoSupported

/** Containers need WebView's profiles, as incognito does (an older WebView offers neither). */
val containersSupported get() = incognitoSupported

/** @param serviceWorkers filters containers' service workers like the normal profile's (ad blocking) */
class ContainerProfiles(private val serviceWorkers: ServiceWorkerClient) {
    /** Profiles WebView has loaded in this process: it can't delete those until the process ends. */
    private val loaded = HashSet<String>()

    /** Puts [wv] in container [id]'s profile. Must run before anything else touches the WebView. */
    fun attach(wv: WebView, id: String) {
        if (!containersSupported) return
        val name = containerProfile(id)
        val p = ProfileStore.getInstance().getOrCreateProfile(name)
        WebViewCompat.setProfile(wv, name)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            runCatching { p.serviceWorkerController.setServiceWorkerClient(serviceWorkers) }
        }
        loaded += name
    }

    /**
     * Deletes the data of every container no longer in [containers] (deleted in Settings): its profile goes, or one
     * loaded in this process is emptied through its own APIs now and goes at the next launch (by this same call at
     * startup).
     */
    fun release(containers: List<Container>) {
        if (!containersSupported) return
        val store = ProfileStore.getInstance()
        orphanedProfiles(store.allProfileNames, containers).forEach { name ->
            if (name in loaded) wipe(name) else runCatching { store.deleteProfile(name) }
        }
    }

    /** Clear Browsing Data: every container's cookies and site storage go too (the HTTP cache goes with its WebViews). */
    fun clearAll() {
        if (!containersSupported) return
        ProfileStore.getInstance().allProfileNames.filter { it.startsWith(CONTAINER_PROFILE_PREFIX) }.forEach(::wipe)
    }

    private fun wipe(name: String) {
        ProfileStore.getInstance().getProfile(name)?.run {
            cookieManager.removeAllCookies(null); cookieManager.flush()
            webStorage.deleteAllData()
            geolocationPermissions.clearAll()
        }
    }
}
