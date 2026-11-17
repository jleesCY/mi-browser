// The menu for a held link or image in a page (Open in New Tab, Copy, Download, Add to Favorites…).
package com.jlees.mibrowser.web

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ContextMenuData
import com.jlees.mibrowser.ui.common.Haptics

/** Asks WebView what was held (its own hit test) and opens mi's menu for a link or image; anything else is the page's. */
class LinkMenu(private val vm: BrowserViewModel, private val haptics: Haptics) {
    fun onLongPress(wv: WebView): Boolean {
        val hit = wv.hitTestResult
        val type = hit.type
        if (type != WebView.HitTestResult.SRC_ANCHOR_TYPE && type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE && type != WebView.HitTestResult.IMAGE_TYPE) return false
        haptics.longPress()
        // the link's address and text come back in a message (requestFocusNodeHref)
        val msg = Handler(Looper.getMainLooper()) { m ->
            val d = m.data
            val link = when (type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> hit.extra
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> d.getString("url")
                else -> null
            }
            val img = if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) null else (d.getString("src") ?: hit.extra)
            val text = if (link != null) (d.getString("title") ?: "").trim().take(50) else "Image"
            vm.contextMenu = ContextMenuData(link, img, text)
            true
        }.obtainMessage()
        wv.requestFocusNodeHref(msg)
        return true
    }
}
