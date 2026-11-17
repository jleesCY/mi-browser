// A tab's browser-side events: progress, title and icon, new windows, JS dialogs, files, fullscreen, permissions.
package com.jlees.mibrowser.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.*
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconHost

/** Tab [id]'s WebChromeClient; the jobs behind it are [webHost]'s (PopupPolicy, DialogLimit, SitePermissions…). */
class TabChromeClient(private val webHost: WebViewHost, private val id: String) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        if (id == webHost.vm.tabs.activeId) webHost.vm.ui.tryEmit(UiEvent.LoadProgress(newProgress / 100f))
    }

    /**
     * Site icons come from the page itself and are kept on the device (see Favicons). The icon is paired with the
     * page through WebView's history entry, not view.url: around a navigation the previous page's icon can arrive
     * after the address has already changed.
     */
    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        if (webHost.isIncognito(id)) return
        val item = view.copyBackForwardList().currentItem ?: return
        val host = faviconHost(item.url) ?: return
        item.favicon?.let { Favicons.save(host, it) }
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        webHost.nav(id, view, view.url, webHost.vm.tabs.list.firstOrNull { it.id == id }?.loading ?: false)
    }

    /**
     * window.open / target=_blank: a real window, shown as a new tab linked to this one. The page keeps
     * window.opener and postMessage to it (sign-in and payment pop-ups such as PayPal depend on that).
     * Pop-ups without a tap are blocked unless the user allowed the site.
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val site = displayHost(view.url)
        if (!isUserGesture && !webHost.popups.allowed(webHost.profileOf(id), site)) {
            if (id == webHost.vm.tabs.activeId) webHost.popups.blocked(webHost.profileOf(id), site, view.url)
            return false
        }
        return webHost.openPopup(id, resultMsg)
    }

    /** The page called window.close() (only windows opened by script may): back to the opener. */
    override fun onCloseWindow(window: WebView) { webHost.vm.closeWindow(id) }

    // JS dialogs use WebView's own dialogs (return false) unless the page is spamming them (see dialogAllowed)
    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        if (webHost.dialogLimit.allow(id)) return false
        result.confirm(); return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        if (webHost.dialogLimit.allow(id)) return false
        result.cancel(); return true
    }

    override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
        if (webHost.dialogLimit.allow(id)) return false
        result.cancel(); return true
    }

    override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams) =
        webHost.fileChooser.show(callback, params)

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = webHost.showCustomView(view, callback)
    override fun onHideCustomView() = webHost.hideCustomView()

    override fun onPermissionRequest(request: PermissionRequest) = webHost.permissions.media(webHost.scope(id), request)
    override fun onPermissionRequestCanceled(request: PermissionRequest) { webHost.vm.alert = null }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        webHost.permissions.location(webHost.scope(id), origin, callback)
    override fun onGeolocationPermissionsHidePrompt() { webHost.vm.alert = null }
}
