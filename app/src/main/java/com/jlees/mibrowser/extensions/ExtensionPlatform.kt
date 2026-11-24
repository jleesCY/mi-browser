// The extension APIs that reach into the browser and the phone: cookies, signing in (identity), side panels, idle
// state, DNS, management of itself, tab extras (screenshots, back/forward, duplicate, language), browsing data,
// downloads (through mi's downloader) and offscreen documents. Each answers like Chrome; where WebView can't give
// everything (cookies' attributes, clearing data "since" a time) the rating says so before installing.
package com.jlees.mibrowser.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.downloads.DownloadItem
import com.jlees.mibrowser.downloads.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.security.MessageDigest

/** A sign-in page an extension opened (identity.launchWebAuthFlow), shown in a sheet until it reaches [redirect]. */
class AuthFlow(val ext: String, val url: String, val redirect: String, val done: (String?) -> Unit)

/** The address an extension's sign-in comes back to: Chrome's for a Chrome Web Store id, else Firefox's for its add-on id. */
fun identityRedirect(id: String, geckoId: String?): String =
    if (id.matches(Regex("[a-p]{32}"))) "https://$id.chromiumapp.org/"
    else "https://" + MessageDigest.getInstance("SHA-1").digest((geckoId ?: id).toByteArray()).joinToString("") { "%02x".format(it) } + ".extensions.allizom.org/"

internal class ExtensionPlatform(private val e: ExtensionEngine) {
    private val vm get() = e.vm

    fun handle(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val ns = name.substringBefore('.')
        when (ns) {
            "cookies" -> cookies(c, name, a, done)
            "identity" -> identity(c, name, a, done)
            "sidePanel" -> sidePanel(c, name, a, done)
            "idle" -> done(true, JsonPrimitive(if (e.app.getSystemService(PowerManager::class.java).isInteractive) "active" else "locked"), null)
            "dns" -> dns(a, done)
            "management" -> management(c, name, a, done)
            "tabsx" -> tabsExtra(c, name, a, done)
            "browsingData" -> browsingData(name, a, done)
            "downloads" -> downloads(c, name, a, done)
            "offscreen" -> offscreen(c, name, a, done)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---------------------------------------------------------------- cookies

    /** The cookie store for [storeId]: the normal one, or an incognito tab's (only for extensions allowed there). */
    private fun cookieStore(c: ExtensionEngine.Ctx, storeId: String?): CookieManager? {
        val private = storeId == "1" || storeId == "firefox-private" || (storeId == null && c.scope != null)
        if (!private) return CookieManager.getInstance()
        if (e.store[c.ext]?.incognito != true) return null
        val tab = vm.tabs.list.firstOrNull { it.incognito } ?: return null
        return e.requests.cookieStoreOf(tab.id)
    }

    private fun storeIdOf(c: ExtensionEngine.Ctx, storeId: String?) = if (storeId == "1" || storeId == "firefox-private" || (storeId == null && c.scope != null)) "1" else "0"

    /** Cookies for [url], as chrome.cookies.Cookie (WebView gives names and values only: the rest is what can be told from the address). */
    private fun cookiesFor(cm: CookieManager, url: String, storeId: String): List<JsonObject> {
        val host = hostOf(url) ?: return emptyList()
        return cm.getCookie(url).orEmpty().split(';').mapNotNull { part ->
            val p = part.trim(); if (p.isEmpty()) return@mapNotNull null
            buildJsonObject {
                put("name", p.substringBefore('=')); put("value", p.substringAfter('=', ""))
                put("domain", host); put("hostOnly", true); put("path", "/"); put("secure", url.startsWith("https"))
                put("httpOnly", false); put("sameSite", "unspecified"); put("session", true); put("storeId", storeId)
            }
        }
    }

    private fun mayReach(ext: String, url: String) = e.infos[ext]?.manifest?.hostPermissions?.any { matchesPattern(it, url) } == true

    private fun cookies(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val o = a.getOrNull(0).obj()
        val storeId = storeIdOf(c, o["storeId"].str())
        if (name == "cookies.getAllCookieStores") return done(true, buildJsonArray {
            add(buildJsonObject { put("id", "0"); put("tabIds", JsonArray(vm.tabs.list.filter { !it.incognito }.map { JsonPrimitive(e.tabInt(it.id)) })); put("incognito", false) })
            if (e.store[c.ext]?.incognito == true && vm.tabs.list.any { it.incognito })
                add(buildJsonObject { put("id", "1"); put("tabIds", JsonArray(vm.tabs.list.filter { it.incognito }.map { JsonPrimitive(e.tabInt(it.id)) })); put("incognito", true) })
        }, null)
        val cm = cookieStore(c, o["storeId"].str()) ?: return done(false, null, "No such cookie store")
        // a site the extension may reach (its host permissions), as Chrome requires
        val url = o["url"].str() ?: o["domain"].str()?.let { "https://${it.trimStart('.')}/" }
        if (url == null && name != "cookies.getAll") return done(false, null, "No url")
        if (url != null && !mayReach(c.ext, url)) return done(false, null, "No host permissions for cookies at url: \"$url\".")
        when (name) {
            "cookies.get" -> done(true, cookiesFor(cm, url!!, storeId).firstOrNull { it["name"].str() == o["name"].str() }, null)
            "cookies.getAll" -> {
                if (url == null) return done(true, JsonArray(emptyList()), null) // WebView can't list every site's cookies
                val urls = if (o["url"] == null) listOf(url, url.replaceFirst("https://", "http://")) else listOf(url)
                val all = urls.flatMap { cookiesFor(cm, it, storeId) }.distinctBy { it["name"].str() }
                done(true, JsonArray(all.filter { ck -> o["name"].str()?.let { it == ck["name"].str() } ?: true }), null)
            }
            "cookies.set" -> {
                val n = o["name"].str().orEmpty(); val v = o["value"].str().orEmpty()
                val cookie = buildString {
                    append(n).append('=').append(v)
                    o["domain"].str()?.let { append("; Domain=").append(it) }
                    append("; Path=").append(o["path"].str() ?: "/")
                    (o["expirationDate"] as? JsonPrimitive)?.doubleOrNull?.let { append("; Expires=").append(java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }.format(java.util.Date((it * 1000).toLong()))) }
                    if (o["secure"].bool() == true) append("; Secure")
                    if (o["httpOnly"].bool() == true) append("; HttpOnly")
                    o["sameSite"].str()?.let { s -> when (s) { "lax" -> append("; SameSite=Lax"); "strict" -> append("; SameSite=Strict"); "no_restriction" -> append("; SameSite=None") } }
                }
                cm.setCookie(url, cookie) { ok ->
                    cm.flush()
                    val result = cookiesFor(cm, url!!, storeId).firstOrNull { it["name"].str() == n }
                    if (ok && result != null) changed(c.ext, result, removed = false)
                    done(true, result, null)
                }
            }
            "cookies.remove" -> {
                val n = o["name"].str().orEmpty()
                val before = cookiesFor(cm, url!!, storeId).firstOrNull { it["name"].str() == n }
                val host = hostOf(url).orEmpty()
                // the cookie may have been set for the host or a parent domain: each is expired
                val domains = listOf<String?>(null) + host.split('.').indices.map { host.split('.').drop(it).joinToString(".") }.filter { it.contains('.') }
                domains.forEach { d -> cm.setCookie(url, "$n=; Max-Age=0; Path=/" + (d?.let { "; Domain=$it" } ?: "")) }
                cm.flush()
                if (before != null) changed(c.ext, before, removed = true)
                done(true, buildJsonObject { put("url", url); put("name", n); put("storeId", storeId) }, null)
            }
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---- cookies sites set themselves (signing in on a site): told to extensions as cookies.onChanged, as in a browser.
    // WebView doesn't report cookie changes, so a page's cookies are noted as it starts and compared as it finishes
    // (or its address changes): what was added, changed or removed is told to extensions that may see that site's cookies.

    /** Per tab: the site whose cookies were noted, and them (name → value). */
    private val noted = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Map<String, String>>>()

    private fun cookieWatchers(url: String, scope: String?) = e.store.list.filter { x ->
        x.enabled && e.runsIn(x.id, scope) && (e.infos[x.id]?.manifest?.permissions?.contains("cookies") == true || e.granted(x.id, "cookies")) && mayReach(x.id, url)
    }

    private fun cookieMap(tabId: String, url: String): Map<String, String>? {
        val cm = e.requests.cookieStoreOf(tabId) ?: return null
        return cm.getCookie(url).orEmpty().split(';').mapNotNull { p -> p.trim().takeIf { it.isNotEmpty() }?.let { it.substringBefore('=') to it.substringAfter('=', "") } }.toMap()
    }

    /** Tab [tabId] is on [url] (a page started, finished, or its address changed): its site's cookie changes are told. */
    fun pageCookies(tabId: String, url: String?, scope: String?) {
        val site = url?.takeIf { it.startsWith("http") }?.let { u -> hostOf(u)?.let { "https://$it/".takeIf { u.startsWith("https") } ?: "http://$it/" } } ?: return
        if (cookieWatchers(site, scope).isEmpty()) return
        val now = cookieMap(tabId, site) ?: return
        val before = noted.put(tabId, site to now)
        if (before == null || before.first != site) return // a new site: noted, compared from now on
        val storeId = if (scope != null) "1" else "0"
        val old = before.second
        val changes = (now.keys + old.keys).mapNotNull { name ->
            val was = old[name]; val is_ = now[name]
            if (was == is_) null else name to (is_ == null)
        }
        if (changes.isEmpty()) return
        val watchers = cookieWatchers(site, scope)
        for ((name, removed) in changes) {
            val cookie = buildJsonObject {
                put("name", name); put("value", if (removed) old[name].orEmpty() else now[name].orEmpty())
                put("domain", hostOf(site).orEmpty()); put("hostOnly", false); put("path", "/"); put("secure", site.startsWith("https"))
                put("httpOnly", false); put("sameSite", "unspecified"); put("session", false); put("storeId", storeId)
            }
            watchers.forEach { changed(it.id, cookie, removed) }
        }
    }

    fun forgetTab(tabId: String) { noted.remove(tabId) }

    private fun changed(ext: String, cookie: JsonObject, removed: Boolean) =
        e.emitToPages(ext, "cookies.onChanged", JsonArray(listOf(buildJsonObject { put("removed", removed); put("cookie", cookie); put("cause", if (removed) "explicit" else "explicit") })))

    // ---------------------------------------------------------------- identity (signing in)

    private fun identity(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val redirect = identityRedirect(c.ext, e.infos[c.ext]?.manifest?.geckoId)
        when (name) {
            "identity.launchWebAuthFlow" -> {
                val o = a.getOrNull(0).obj()
                val url = o["url"].str() ?: return done(false, null, "No url")
                if (!url.startsWith("https://") && !url.startsWith("http://")) return done(false, null, "Invalid url")
                if (ExtensionFlow.auth != null) return done(false, null, "Another sign-in is already open.")
                var answered = false
                val flow = AuthFlow(c.ext, url, redirect) { back ->
                    if (answered) return@AuthFlow
                    answered = true
                    if (ExtensionFlow.auth?.ext == c.ext) ExtensionFlow.auth = null
                    if (back != null) done(true, JsonPrimitive(back), null)
                    else done(false, null, if (o["interactive"].bool() == true) "The user did not approve access." else "User interaction required.")
                }
                // a sign-in page in a sheet, done when it reaches the extension's return address; or, not interactive, out of sight
                if (o["interactive"].bool() == true) ExtensionFlow.auth = flow else silentAuth(e.app, flow)
            }
            "identity.getAuthToken" -> done(false, null, "Signing in with a Google account isn't supported in mi.")
            "identity.getProfileUserInfo" -> done(true, buildJsonObject { put("email", ""); put("id", "") }, null)
            "identity.getAccounts" -> done(true, JsonArray(emptyList()), null)
            "identity.removeCachedAuthToken", "identity.clearAllCachedAuthTokens" -> done(true, null, null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---------------------------------------------------------------- side panels (shown as mi's sheet)

    private fun sidePanel(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val o = a.getOrNull(0).obj()
        when (name) {
            "sidePanel.setOptions" -> { o["path"].str()?.let { p -> e.updateAction(c.ext) { copy(panel = p) } }; o["enabled"].bool()?.let { on -> e.updateAction(c.ext) { copy(panelEnabled = on) } }; done(true, null, null) }
            "sidePanel.getOptions" -> done(true, buildJsonObject { e.panelOf(c.ext)?.let { put("path", it) }; put("enabled", e.actions[c.ext]?.panelEnabled != false) }, null)
            "sidePanel.setPanelBehavior" -> { o["openPanelOnActionClick"].bool()?.let { b -> e.updateAction(c.ext) { copy(panelOnClick = b) } }; done(true, null, null) }
            "sidePanel.getPanelBehavior" -> done(true, buildJsonObject { put("openPanelOnActionClick", e.actions[c.ext]?.panelOnClick == true) }, null)
            "sidePanel.open", "sidePanel.toggle" -> {
                val path = e.panelOf(c.ext) ?: return done(false, null, "No side panel")
                if (name == "sidePanel.toggle" && ExtensionFlow.page?.let { it.ext == c.ext && it.path == path } == true) ExtensionFlow.page = null
                else ExtensionFlow.page = OpenPage(c.ext, path, ExtensionEngine.Kind.POPUP)
                done(true, null, null)
            }
            "sidePanel.close" -> { if (ExtensionFlow.page?.ext == c.ext) ExtensionFlow.page = null; done(true, null, null) }
            "sidePanel.isOpen" -> done(true, JsonPrimitive(ExtensionFlow.page?.ext == c.ext), null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---------------------------------------------------------------- dns, management

    private fun dns(a: JsonArray, done: Done) {
        val host = a.getOrNull(0).str() ?: return done(false, null, "No hostname")
        vm.viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { InetAddress.getAllByName(host).map { it.hostAddress.orEmpty() } } }
            r.onSuccess { done(true, buildJsonObject { put("addresses", JsonArray(it.map(::JsonPrimitive))); put("canonicalName", host); put("isTRR", false) }, null) }
                .onFailure { done(false, null, "NS_ERROR_UNKNOWN_HOST") }
        }
    }

    private fun selfInfo(ext: String): JsonObject? {
        val info = e.infos[ext] ?: return null
        val installed = e.store[ext]
        return buildJsonObject {
            put("id", ext); put("name", info.name); put("shortName", info.name); put("description", info.description)
            put("version", info.manifest.versionName); put("enabled", installed?.enabled != false); put("type", "extension")
            put("installType", if (installed?.source != null) "normal" else "sideload"); put("mayDisable", true); put("offlineEnabled", true)
            put("permissions", JsonArray(info.manifest.permissions.map(::JsonPrimitive))); put("hostPermissions", JsonArray(info.manifest.hostPermissions.map(::JsonPrimitive)))
            info.manifest.optionsPage?.let { put("optionsUrl", nativeBase(ext) + it.trimStart('/')) }
            put("icons", JsonArray(info.manifest.icons.map { (s, p) -> buildJsonObject { put("size", s); put("url", nativeBase(ext) + p.trimStart('/')) } }))
        }
    }

    private fun management(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        when (name) {
            "management.getSelf" -> done(true, selfInfo(c.ext), null)
            // other extensions aren't shown to an extension: it sees itself only
            "management.getAll" -> done(true, JsonArray(listOfNotNull(selfInfo(c.ext))), null)
            "management.get" -> if (a.getOrNull(0).str() == c.ext) done(true, selfInfo(c.ext), null) else done(false, null, "Failed to find extension with id ${a.getOrNull(0).str()}.")
            "management.uninstallSelf" -> {
                val name0 = e.infos[c.ext]?.name ?: "This extension"
                vm.showAlert("Remove $name0?", "$name0 asked to remove itself. Its settings and saved data go with it.", listOf(
                    com.jlees.mibrowser.AlertButton("Keep", com.jlees.mibrowser.ButtonStyle.CANCEL) { done(false, null, "User cancelled uninstall.") },
                    com.jlees.mibrowser.AlertButton("Remove", com.jlees.mibrowser.ButtonStyle.DESTRUCTIVE) { done(true, null, null); vm.removeExtension(c.ext) },
                ), onCancel = { done(false, null, "User cancelled uninstall.") })
            }
            "management.setEnabled" -> {
                if (a.getOrNull(0).str() != c.ext) return done(false, null, "Extensions can only change themselves in mi.")
                if (a.getOrNull(1).bool() == false) vm.setExtensionEnabled(c.ext, false)
                done(true, null, null)
            }
            "management.getPermissionWarningsById", "management.getPermissionWarningsByManifest" -> done(true, JsonArray(emptyList()), null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---------------------------------------------------------------- tabs: screenshots, history, duplicate, language

    private fun tabsExtra(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        fun tab(n: Int?) = (n?.let(e::tabOf) ?: vm.tabs.activeId)?.let { id -> vm.tabs.list.firstOrNull { it.id == id } }
        when (name) {
            "tabsx.captureVisibleTab" -> {
                val t = vm.tabs.current ?: return done(false, null, "No tab")
                if (t.url?.let { mayReach(c.ext, it) } != true && e.infos[c.ext]?.manifest?.permissions?.contains("activeTab") != true) return done(false, null, "Missing host permission for the tab")
                val wv = e.tabView(t.id) ?: return done(false, null, "No tab")
                val opts = a.getOrNull(1).obj()
                if (wv.width <= 0 || wv.height <= 0) return done(false, null, "The tab isn't showing")
                val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply { translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat()) }.also { wv.draw(it) }
                val png = opts["format"].str() == "png"
                vm.viewModelScope.launch {
                    val data = withContext(Dispatchers.Default) {
                        ByteArrayOutputStream().use { out -> bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, opts["quality"].int() ?: 92, out); out.toByteArray() }.also { bmp.recycle() }
                    }
                    done(true, JsonPrimitive("data:image/${if (png) "png" else "jpeg"};base64," + Base64.encodeToString(data, Base64.NO_WRAP)), null)
                }
            }
            "tabsx.goBack" -> { tab(a.getOrNull(0).int())?.let { e.tabView(it.id)?.takeIf { w -> w.canGoBack() }?.goBack() }; done(true, null, null) }
            "tabsx.goForward" -> { tab(a.getOrNull(0).int())?.let { e.tabView(it.id)?.takeIf { w -> w.canGoForward() }?.goForward() }; done(true, null, null) }
            "tabsx.duplicate" -> {
                val t = tab(a.getOrNull(0).int()) ?: return done(false, null, "No tab")
                vm.addNewTab(t.url, incognito = t.incognito)
                done(true, vm.tabs.current?.let { e.tabJson(it) }, null)
            }
            "tabsx.detectLanguage" -> {
                val wv = tab(a.getOrNull(0).int())?.let { e.tabView(it.id) } ?: return done(true, JsonPrimitive("und"), null)
                wv.evaluateJavascript("(document.documentElement.lang||navigator.language||'und')") { r ->
                    done(true, JsonPrimitive(r?.trim('"')?.substringBefore('-')?.lowercase()?.ifEmpty { "und" } ?: "und"), null)
                }
            }
            "tabsx.getZoom" -> done(true, JsonPrimitive(vm.settings.textZoom / 100.0), null)
            "tabsx.highlight" -> { tab((a.getOrNull(0).obj()["tabs"] as? JsonArray)?.firstOrNull().int() ?: a.getOrNull(0).obj()["tabs"].int())?.let { vm.setActiveTab(it.id) }; done(true, null, null) }
            "tabsx.move", "tabsx.discard" -> done(true, tab(a.getOrNull(0).int())?.let { e.tabJson(it) }, null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---------------------------------------------------------------- browsing data

    private fun browsingData(name: String, a: JsonArray, done: Done) {
        val what: Set<String> = when (name) {
            "browsingData.remove" -> a.getOrNull(1).obj().filter { it.value.bool() == true }.keys
            "browsingData.removeCookies" -> setOf("cookies")
            "browsingData.removeCache" -> setOf("cache")
            "browsingData.removeHistory" -> setOf("history")
            "browsingData.removeLocalStorage" -> setOf("localStorage")
            "browsingData.removeIndexedDB" -> setOf("indexedDB")
            "browsingData.removeServiceWorkers" -> setOf("serviceWorkers")
            "browsingData.removeDownloads" -> setOf("downloads")
            "browsingData.removeCacheStorage" -> setOf("cacheStorage")
            "browsingData.settings" -> return done(true, buildJsonObject {
                put("options", buildJsonObject { put("since", 0) })
                put("dataToRemove", buildJsonObject { listOf("cache", "cookies", "history", "localStorage", "indexedDB", "downloads").forEach { put(it, false) } })
                put("dataRemovable", buildJsonObject { listOf("cache", "cookies", "history", "localStorage", "indexedDB", "downloads").forEach { put(it, true) } })
            }, null)
            else -> emptySet()
        }
        // as asked, all of it (WebView can't remove data from only a certain time on)
        if ("cookies" in what) { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
        if ("cache" in what || "cacheStorage" in what) vm.tabs.list.firstNotNullOfOrNull { e.tabView(it.id) }?.clearCache(true)
        if ("localStorage" in what || "indexedDB" in what || "serviceWorkers" in what || "webSQL" in what || "fileSystems" in what) WebStorage.getInstance().deleteAllData()
        if ("history" in what) vm.history.clear()
        if ("downloads" in what) vm.downloads.items.filterNot { it.active }.forEach { vm.downloads.remove(it.id) }
        done(true, null, null)
    }

    // ---------------------------------------------------------------- downloads (mi's downloader)

    private val downloadIds = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val nextDownload = java.util.concurrent.atomic.AtomicInteger(1)
    private fun downloadInt(id: String) = downloadIds.getOrPut(id) { nextDownload.getAndIncrement() }
    private fun downloadOf(n: Int?) = downloadIds.entries.firstOrNull { it.value == n }?.key

    private fun downloadJson(d: DownloadItem) = buildJsonObject {
        put("id", downloadInt(d.id)); put("url", d.url); put("finalUrl", d.url); put("filename", d.fileName); put("mime", d.mime)
        put("startTime", java.time.Instant.ofEpochMilli(d.startedAt).toString())
        put("state", when (d.state) { DownloadState.DONE -> "complete"; DownloadState.FAILED -> "interrupted"; else -> "in_progress" })
        put("paused", d.state == DownloadState.PAUSED); put("canResume", d.state == DownloadState.PAUSED || d.state == DownloadState.FAILED)
        put("bytesReceived", d.done); put("totalBytes", d.total); put("fileSize", d.total); put("exists", d.state == DownloadState.DONE)
        put("incognito", d.incognito); put("danger", "safe")
        d.error?.let { put("error", "NETWORK_FAILED") }
    }

    private fun downloads(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val o = a.getOrNull(0).obj()
        when (name) {
            "downloads.download" -> {
                val url = o["url"].str() ?: return done(false, null, "No url")
                if (url.startsWith("blob:") || url.startsWith("data:")) return done(false, null, "Downloading blob: and data: addresses from an extension isn't supported in mi.")
                val id = vm.downloader.enqueue(
                    url, cookies = CookieManager.getInstance().getCookie(url), userAgent = android.webkit.WebSettings.getDefaultUserAgent(e.app),
                    contentDisposition = o["filename"].str()?.let { "attachment; filename=\"${it.substringAfterLast('/').replace("\"", "")}\"" },
                    mime = null, length = -1, incognito = c.scope != null, folder = vm.settings.downloadFolder,
                )
                vm.downloads.items.firstOrNull { it.id == id }?.let { e.emitToPages(c.ext, "downloads.onCreated", JsonArray(listOf(downloadJson(it)))) }
                done(true, JsonPrimitive(downloadInt(id)), null)
            }
            "downloads.search" -> {
                val q = o["query"]?.let { (it as? JsonArray)?.mapNotNull { x -> x.str() } }.orEmpty()
                val list = vm.downloads.items.filter { d ->
                    (o["id"].int()?.let { downloadOf(it) == d.id } ?: true) &&
                        (o["url"].str()?.let { it == d.url } ?: true) &&
                        (o["state"].str()?.let { it == downloadJson(d)["state"].str() } ?: true) &&
                        q.all { d.fileName.contains(it, true) || d.url.contains(it, true) }
                }.let { l -> o["limit"].int()?.takeIf { it > 0 }?.let { l.take(it) } ?: l }
                done(true, JsonArray(list.map(::downloadJson)), null)
            }
            "downloads.pause" -> { downloadOf(a.getOrNull(0).int())?.let { vm.downloader.pause(it) }; done(true, null, null) }
            "downloads.resume" -> { downloadOf(a.getOrNull(0).int())?.let { vm.downloader.resume(it) }; done(true, null, null) }
            "downloads.cancel" -> { downloadOf(a.getOrNull(0).int())?.let { vm.downloader.cancel(it) }; done(true, null, null) }
            "downloads.erase" -> {
                val ids = vm.downloads.items.filter { d -> o["id"].int()?.let { downloadOf(it) == d.id } ?: (o["state"].str()?.let { it == downloadJson(d)["state"].str() } ?: false) }
                ids.forEach { vm.downloads.remove(it.id) }
                done(true, JsonArray(ids.map { JsonPrimitive(downloadInt(it.id)) }), null)
            }
            "downloads.open", "downloads.show", "downloads.showDefaultFolder" -> { vm.openView(com.jlees.mibrowser.Sheet.DOWNLOADS); done(true, null, null) }
            "downloads.getFileIcon" -> done(true, null, null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    /** Downloads changing: told to extensions with the downloads permission (downloads.onChanged), as they finish or fail. */
    fun observeDownloads() = vm.viewModelScope.launch {
        var before = emptyMap<String, DownloadState>()
        snapshotFlow { vm.downloads.items.associate { it.id to it.state } }.collect { now ->
            val listeners = e.store.list.filter { x -> x.enabled && e.infos[x.id]?.manifest?.permissions?.contains("downloads") == true }
            if (listeners.isNotEmpty()) for ((id, state) in now) {
                val old = before[id] ?: continue
                if (old == state) continue
                val delta = buildJsonObject {
                    put("id", downloadInt(id))
                    put("state", buildJsonObject {
                        put("previous", if (old == DownloadState.DONE) "complete" else if (old == DownloadState.FAILED) "interrupted" else "in_progress")
                        put("current", if (state == DownloadState.DONE) "complete" else if (state == DownloadState.FAILED) "interrupted" else "in_progress")
                    })
                }
                listeners.forEach { e.emitToPages(it.id, "downloads.onChanged", JsonArray(listOf(delta))) }
            }
            if (listeners.isNotEmpty()) for (id in before.keys - now.keys) listeners.forEach { e.emitToPages(it.id, "downloads.onErased", JsonArray(listOf(JsonPrimitive(downloadInt(id))))) }
            before = now
        }
    }

    // ---------------------------------------------------------------- offscreen documents

    private fun offscreen(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        when (name) {
            "offscreen.createDocument" -> {
                val url = a.getOrNull(0).obj()["url"].str() ?: return done(false, null, "No url")
                if (e.pages.hasOffscreen(c.ext)) return done(false, null, "Only a single offscreen document may be created.")
                e.pages.startOffscreen(c.ext, url.substringAfter(extOrigin(c.ext)).trimStart('/'))
                done(true, null, null)
            }
            "offscreen.closeDocument" -> { e.pages.stopOffscreen(c.ext); done(true, null, null) }
            "offscreen.hasDocument" -> done(true, JsonPrimitive(e.pages.hasOffscreen(c.ext)), null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    // ---------------------------------------------------------------- idle (screen on and off)

    private var lastIdle = "active"
    /** Tells extensions with the idle permission when the screen locks and unlocks (idle.onStateChanged). */
    fun watchIdle() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val state = if (intent.action == Intent.ACTION_SCREEN_OFF) "locked" else "active"
                if (state == lastIdle) return
                lastIdle = state
                e.store.list.filter { x -> x.enabled && e.infos[x.id]?.manifest?.permissions?.contains("idle") == true }
                    .forEach { e.emitToPages(it.id, "idle.onStateChanged", JsonArray(listOf(JsonPrimitive(state)))) }
            }
        }
        e.app.registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) })
    }
}
