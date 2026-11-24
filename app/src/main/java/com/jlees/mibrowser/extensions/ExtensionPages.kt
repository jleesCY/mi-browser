// An extension's own pages: its background (a hidden WebView, so Manifest V2 pages and V3 service workers both run with
// everything a browser page has), and its popup and options page (shown in a sheet). Each is served from the
// extension's folder at https://<id>.ext.mi.invalid/, and starts with the full extension API (assets/js/ext-page.js).
package com.jlees.mibrowser.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import com.jlees.mibrowser.BuildConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

/**
 * An extension's style sheet as a browser serves it: its own files named as moz-extension://__MSG_@@extension_id__/…
 * point at the extension's address at mi; the placeholder elsewhere is its id.
 */
fun extensionCss(css: String, id: String): String =
    css.replace("moz-extension://__MSG_@@extension_id__", extOrigin(id)).replace("__MSG_@@extension_id__", id)

/** One of an extension's pages to show: its popup, options or side panel. */
data class OpenPage(val ext: String, val path: String, val kind: ExtensionEngine.Kind)

internal class ExtensionPages(private val e: ExtensionEngine) {
    private class Background(val view: WebView, val ctx: ExtensionEngine.Ctx, val reason: String)
    private val backgrounds = HashMap<String, Background>()
    private val open = HashMap<WebView, ExtensionEngine.Ctx>()


    // ---------------------------------------------------------------- backgrounds

    /** Starts extension [id]'s background, if it has one. [reason]: "install", "update" or "startup" (for its first events). */
    fun startBackground(id: String, reason: String) {
        val info = e.infos[id] ?: return
        if (!info.manifest.hasBackground || !extensionsSupported || backgrounds.containsKey(id)) return
        e.menus.started(id, reason)
        val url = info.manifest.backgroundPage?.let { "${extOrigin(id)}/${it.trimStart('/')}" } ?: "${extOrigin(id)}/_mi_background.html"
        val ctx = e.Ctx(id, ExtensionEngine.Kind.BACKGROUND, null, true, url, null)
        e.contexts += ctx
        val wv = pageView(e.app, info, ctx)
        backgrounds[id] = Background(wv, ctx, reason)
        wv.loadUrl(url)
    }

    fun stopBackground(id: String) {
        stopOffscreen(id)
        val b = backgrounds.remove(id) ?: return
        e.drop(b.ctx)
        b.view.destroy()
    }

    // ---------------------------------------------------------------- offscreen documents (chrome.offscreen)

    private val offscreen = HashMap<String, Background>()

    fun hasOffscreen(id: String) = offscreen.containsKey(id)

    /** A hidden page of extension [id] at [path], like its background: for DOM work a service worker can't do. */
    fun startOffscreen(id: String, path: String) {
        val info = e.infos[id] ?: return
        if (!extensionsSupported || offscreen.containsKey(id)) return
        val url = "${extOrigin(id)}/${path.trimStart('/')}"
        val ctx = e.Ctx(id, ExtensionEngine.Kind.OFFSCREEN, null, true, url, null)
        e.contexts += ctx
        val wv = pageView(e.app, info, ctx) { stopOffscreen(id) }
        offscreen[id] = Background(wv, ctx, "offscreen")
        wv.loadUrl(url)
    }

    fun stopOffscreen(id: String) {
        val b = offscreen.remove(id) ?: return
        e.drop(b.ctx)
        b.view.destroy()
    }

    private val restarted = HashMap<String, Long>()

    /** [view]'s renderer died: the page is dropped; a background is started again (at most once a minute, so it can't loop). */
    private fun rendererGone(id: String, view: WebView, close: (() -> Unit)?) {
        when {
            backgrounds[id]?.view === view -> {
                stopBackground(id)
                val now = System.currentTimeMillis()
                if (now - (restarted[id] ?: 0) > 60_000) { restarted[id] = now; e.main.postDelayed({ if (e.store[id]?.enabled == true) startBackground(id, "startup") }, 2_000) }
            }
            offscreen[id]?.view === view -> stopOffscreen(id)
            else -> close?.invoke() // a popup or options page: its sheet closes
        }
    }

    /** The background's scripts have run: it's told why it started, then gets what waited for it. */
    fun backgroundReady(ctx: ExtensionEngine.Ctx) {
        if (ctx.ready) return
        ctx.ready = true
        val b = backgrounds[ctx.ext]
        val reason = b?.reason ?: "startup"
        if (reason == "startup") e.emit(listOf(ctx), "runtime.onStartup", kotlinx.serialization.json.JsonArray(emptyList()))
        else e.emit(listOf(ctx), "runtime.onInstalled", buildJsonObject { put("reason", reason); if (reason == "update") put("previousVersion", "") })
        ctx.flush()
    }

    // ---------------------------------------------------------------- popup and options

    /** A WebView showing [p] (in its sheet); [close] when the page closes itself (window.close()). */
    fun openPage(context: Context, p: OpenPage, close: () -> Unit): WebView? {
        val info = e.infos[p.ext] ?: return null
        val url = "${extOrigin(p.ext)}/${p.path.trimStart('/')}"
        val ctx = e.Ctx(p.ext, p.kind, null, true, url, null)
        e.contexts += ctx
        val wv = pageView(context, info, ctx, close)
        open[wv] = ctx
        wv.loadUrl(url)
        return wv
    }

    fun closePage(wv: WebView) { open.remove(wv)?.let { e.drop(it) }; wv.destroy() }

    /** mi is closing: every page of every extension goes (backgrounds, offscreen documents, popups still open). */
    fun stopAll() {
        backgrounds.keys.toList().forEach(::stopBackground)
        offscreen.keys.toList().forEach(::stopOffscreen)
        open.keys.toList().forEach(::closePage)
    }

    // ---------------------------------------------------------------- the page itself

    @SuppressLint("SetJavaScriptEnabled")
    private fun pageView(context: Context, info: ExtensionInfo, ctx: ExtensionEngine.Ctx, close: (() -> Unit)? = null): WebView {
        val origin = extOrigin(info.id)
        val wv = WebView(context)
        with(wv.settings) {
            javaScriptEnabled = true; domStorageEnabled = true
            @Suppress("DEPRECATION") databaseEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false) // links that open a window open here, and leave for a tab below
        }
        e.userAgents.apply(wv.settings, desktop = false)
        WebViewCompat.addWebMessageListener(wv, "miExt", setOf(origin)) { _, message, _, _, proxy ->
            e.onMessage(ExtensionEngine.Source.Page(ctx), message.data, proxy)
        }
        WebViewCompat.addDocumentStartJavaScript(wv, startScript(info, ctx.kind), setOf(origin))
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                if (extIdOf(request.url.toString()) == info.id) serve(info.id, request.url.path ?: "/", all = true) else null
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (extIdOf(url) == info.id) return false
                // its native address (moz-extension://…): the page at mi's
                fromNative(url)?.takeIf { extIdOf(it) == info.id }?.let { view.loadUrl(it); return true }
                // a link out of a popup or options page: a new tab, as a browser's popup would open it
                if (url.startsWith("http://") || url.startsWith("https://")) { e.vm.addNewTab(url); close?.invoke() }
                return true
            }
            // the page renderer died (Android reclaimed its memory, or it crashed): this page goes, instead of WebView
            // taking mi down with it; a background starts again shortly
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                e.main.post { rendererGone(info.id, view, close) }
                return true
            }
        }
        wv.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onCloseWindow(window: WebView?) { close?.invoke() }
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                if (BuildConfig.DEBUG) android.util.Log.d("miExt", "[${info.name}] ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                return true
            }
        }
        return wv
    }

    /** The document-start script for one of [info]'s pages: the extension API, configured for this extension. */
    private fun startScript(info: ExtensionInfo, kind: ExtensionEngine.Kind): String {
        val cfg = configOf(info, kind.name.lowercase(), incognito = false)
        return "(function(makeApi, CFG, TOKEN){\n(${e.scripts.extAddress})()([CFG.id]);\n${e.scripts.extPage}\n})(${e.scripts.extApi}(), $cfg, ${JsonPrimitive(e.tokens[info.id] ?: "")});"
    }

    /** What the extension API needs to know about the extension, as ext-api.js's `cfg`. */
    fun configOf(info: ExtensionInfo, kind: String, incognito: Boolean): JsonObject = buildJsonObject {
        put("id", info.id); put("name", info.name); put("kind", kind)
        put("manifest", info.manifest.raw)
        put("permissions", kotlinx.serialization.json.JsonArray(info.manifest.permissions.map(::JsonPrimitive)))
        put("messages", JsonObject(info.messages.mapValues { JsonPrimitive(it.value) }))
        // runtime.getURL's: the extension's address as its browser gives it (ext-address.js loads it from mi's)
        put("baseUrl", nativeBase(info.id))
        put("uiLanguage", Locale.getDefault().toLanguageTag())
        put("appVersion", BuildConfig.VERSION_NAME)
        put("incognito", incognito)
        put("incognitoAllowed", e.store[info.id]?.incognito == true)
        put("identityRedirect", identityRedirect(info.id, info.manifest.geckoId))
    }

    // ---------------------------------------------------------------- the extension's files

    /**
     * A file of extension [id] at [path], or the page mi makes to run its background scripts. [all]: for its own pages;
     * otherwise (a web page asking) only the files it lets pages load (web_accessible_resources).
     */
    fun serve(id: String, path: String, all: Boolean): WebResourceResponse? {
        val info = e.infos[id] ?: return notFound()
        if (all && path == "/_mi_background.html") return html(backgroundHtml(info.manifest))
        if (!all && !webAccessible(path, info.manifest.webAccessible)) return notFound()
        val dir = info.dir.canonicalFile
        val f = File(dir, path.trimStart('/')).canonicalFile
        if (!f.path.startsWith(dir.path) || !f.isFile) return notFound()
        val ext = f.extension.lowercase()
        val mime = when (ext) {
            "js", "mjs" -> "text/javascript"; "json" -> "application/json"; "css" -> "text/css"; "html", "htm" -> "text/html"
            "svg" -> "image/svg+xml"; "wasm" -> "application/wasm"; "woff2" -> "font/woff2"; "woff" -> "font/woff"; "ttf" -> "font/ttf"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        // style sheets name the extension's own files as moz-extension://__MSG_@@extension_id__/… (Firefox, Chrome fill the
        // id in as they serve them): here, its address at mi
        val body = if (ext == "css") ByteArrayInputStream(extensionCss(f.readText(), id).toByteArray()) else f.inputStream()
        return WebResourceResponse(mime, if (mime.startsWith("text/") || mime == "application/json") "utf-8" else null, 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-cache"), body)
    }

    private fun html(s: String) = WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(s.toByteArray()))
    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/**
 * The page a background runs in: its scripts in order, or its service worker; modules when the manifest says "type":
 * "module" (Firefox's Manifest V3 backgrounds use scripts as modules, as uBlock Origin Lite's does).
 */
internal fun backgroundHtml(m: Manifest): String = buildString {
    append("<!doctype html><html><head><meta charset=\"utf-8\"></head><body>")
    val type = if (m.backgroundModule) "type=\"module\" " else ""
    m.backgroundScripts.forEach { append("<script ${type}src=\"/${it.trimStart('/')}\"></script>") }
    m.serviceWorker?.let { append("<script ${type}src=\"/${it.trimStart('/')}\"></script>") }
    append("</body></html>")
}
