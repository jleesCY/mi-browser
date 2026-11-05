// Switching a tab between normal and incognito.
package com.jlees.mibrowser.incognito

import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.newIncognitoProfile
import com.jlees.mibrowser.tabs.canToggleIncognito

/** Incognito needs WebView profiles (WebView 110+); without them the toggle isn't offered (never a shared profile). */
val incognitoSupported: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }

/** Search pill toggle. Only a fresh tab can switch; a tab with history keeps its mode. */
fun BrowserViewModel.toggleIncognito() {
    val t = tabs.current ?: return
    if (!canToggleIncognito(t, incognitoSupported)) return
    tabs.update(t.id) { copy(profile = if (incognito) null else newIncognitoProfile()) }
}

/** The "Close incognito tabs" notification: every incognito tab (and its pop-ups) is closed and erased. */
fun BrowserViewModel.closeIncognitoTabs() = closeTabs(tabs.list.filter { it.incognito }.map { it.id }.toSet())
