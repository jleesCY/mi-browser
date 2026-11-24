// Getting extensions: searching Firefox Add-ons, fetching a package from Firefox Add-ons or the Chrome Web Store (or a
// file a page links to), reading it into a staging folder and rating it, then installing it once confirmed. Also turning
// extensions on and off, and removing them. ExtensionFlow holds what the screens show while this happens.
package com.jlees.mibrowser.extensions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.Notices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A package fetched and read, waiting for "Add" in the install dialog. */
class PendingInstall(val info: ExtensionInfo, val source: String?, val iconUrl: String?, val replacing: InstalledExtension?)

/** What the store says about an extension, shown while its package downloads. */
data class StoreListing(val name: String, val summary: String = "", val iconUrl: String? = null, val version: String? = null)

/** A store page for an extension that isn't installed: the "Add to mi." bar offers it. */
data class StoreOffer(val name: String, val source: InstallSource, val pageUrl: String)

/** One result from searching Firefox Add-ons. */
data class AmoAddon(
    val slug: String, val name: String, val summary: String, val iconUrl: String?, val users: Long, val rating: Double,
)


/** What the extension screens show; set by the installer and the engine. */
object ExtensionFlow {
    /** The running engine (WebViewHost creates it). */
    var engine: ExtensionEngine? = null
    /** An extension's popup or options page, open in its sheet. */
    var page by mutableStateOf<OpenPage?>(null)
    /** The name of an extension being fetched (the install dialog shows it's on its way). */
    var fetching by mutableStateOf<String?>(null)
    /** The store's listing for the extension being fetched: the install dialog shows it while the package downloads. */
    var listing by mutableStateOf<StoreListing?>(null)
    /** Bumped when a fetch is cancelled: a download from before stops, and its result is dropped. */
    @Volatile var run = 0
    /** How far the package has downloaded (0..1), or null while that isn't known; 1 while it's being checked. */
    var progress by mutableStateOf<Float?>(null)
    /** Downloaded and now being read and rated. */
    var checking by mutableStateOf(false)
    var pending by mutableStateOf<PendingInstall?>(null)
    var offer by mutableStateOf<StoreOffer?>(null)
    /** Library › Extensions should open at this: an extension's id, or "get" (Get Extensions). */
    var libraryTarget by mutableStateOf<String?>(null)
    /** The installed extension whose panel is open (ExtensionPanel). */
    var opened by mutableStateOf<String?>(null)
    /** A sign-in page an extension opened (identity.launchWebAuthFlow), shown in its sheet. */
    var auth by mutableStateOf<AuthFlow?>(null)
    /** Store pages the offer was turned down for (this run): not offered again. */
    val dismissed = HashSet<String>()
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

object ExtensionInstaller {
    private const val AMO = "https://addons.mozilla.org/api/v5/addons"
    private fun lang() = Locale.getDefault().toLanguageTag()

    /** [url]'s body; [progress] hears the share received so far (0..1) when the size is known. */
    private fun get(url: String, progress: ((Float) -> Unit)? = null): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, application/x-xpinstall, application/x-chrome-extension, */*")
        }
        // the Chrome Web Store redirects across hosts, which HttpURLConnection doesn't follow by itself
        var c = conn
        repeat(5) {
            val code = c.responseCode
            if (code in 300..399) { val next = URL(URL(c.url.toString()), c.getHeaderField("Location")).toString(); c.disconnect(); c = URL(next).openConnection() as HttpURLConnection }
            else {
                if (code >= 400) error("HTTP $code")
                val total = c.contentLengthLong
                return c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream(if (total in 1..Int.MAX_VALUE) total.toInt() else 64 * 1024)
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); read += n
                        if (total > 0) progress?.invoke((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                    out.toByteArray()
                }
            }
        }
        error("too many redirects")
    }

    /** Searches Firefox Add-ons ([query] blank: its recommended extensions), most used first. */
    suspend fun search(query: String): List<AmoAddon> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val url = if (q.isEmpty()) "$AMO/search/?promoted=recommended&type=extension&app=firefox&sort=users&page_size=30&lang=${lang()}"
            else "$AMO/search/?q=${URLEncoder.encode(q, "UTF-8")}&type=extension&app=firefox&sort=relevance&page_size=30&lang=${lang()}"
        val results = lenientJson.parseToJsonElement(String(get(url))).jsonObject["results"] as? JsonArray ?: return@withContext emptyList()
        results.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            AmoAddon(
                slug = (o["slug"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                name = text(o["name"]) ?: return@mapNotNull null,
                summary = text(o["summary"]).orEmpty(),
                iconUrl = (o["icon_url"] as? JsonPrimitive)?.contentOrNull,
                users = (o["average_daily_users"] as? JsonPrimitive)?.longOrNull ?: 0,
                rating = ((o["ratings"] as? JsonObject)?.get("average") as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            )
        }
    }

    /** A localized AMO field: a string (when asked with lang), or a map of languages. */
    private fun text(e: kotlinx.serialization.json.JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonObject -> (e[lang()] ?: e["en-US"] ?: e.values.firstOrNull()).let { (it as? JsonPrimitive)?.contentOrNull }
        else -> null
    }

    /** The package and its icon for [source]: its bytes, the page to remember it by, and an icon address. */
    internal fun download(source: InstallSource, onListing: (StoreListing) -> Unit = {}, progress: (Float) -> Unit = {}): Triple<ByteArray, String?, String?> = when (source) {
        is InstallSource.Amo -> {
            val o = lenientJson.parseToJsonElement(String(get("$AMO/addon/${URLEncoder.encode(source.slug, "UTF-8")}/?lang=${lang()}"))).jsonObject
            val file = ((o["current_version"] as? JsonObject)?.get("file") as? JsonObject)?.get("url").let { (it as? JsonPrimitive)?.contentOrNull } ?: error("no file")
            // what the store says, shown while the package downloads
            text(o["name"])?.let { name ->
                onListing(StoreListing(name, text(o["summary"]).orEmpty(), (o["icon_url"] as? JsonPrimitive)?.contentOrNull,
                    ((o["current_version"] as? JsonObject)?.get("version") as? JsonPrimitive)?.contentOrNull))
            }
            Triple(get(file, progress), "https://addons.mozilla.org/firefox/addon/${source.slug}/", (o["icon_url"] as? JsonPrimitive)?.contentOrNull)
        }
        is InstallSource.Chrome -> Triple(
            get("https://clients2.google.com/service/update2/crx?response=redirect&prodversion=131.0.0.0&acceptformat=crx2,crx3&x=id%3D${source.id}%26installsource%3Dondemand%26uc", progress),
            "https://chromewebstore.google.com/detail/${source.id}", null)
        is InstallSource.File -> Triple(get(source.url, progress), null, null)
    }

    /**
     * Where a fetched extension waits for Add: in mi's own files, not its cache, which Android may empty when the phone
     * is short of space (a waiting extension would vanish under the dialog). Cleared at each start.
     */
    internal fun staging(app: android.content.Context) = File(app.noBackupFilesDir, "ext-staging")
}

/** Fetches and reads the extension at [source], then asks to install it (ExtensionFlow.pending). [name]: what to call it meanwhile. */
fun BrowserViewModel.fetchExtension(source: InstallSource, name: String, listing: StoreListing? = null) {
    if (ExtensionFlow.fetching != null) return
    ExtensionFlow.fetching = name
    ExtensionFlow.listing = listing ?: StoreListing(name)
    val run = ++ExtensionFlow.run
    ExtensionFlow.progress = null; ExtensionFlow.checking = false
    ExtensionFlow.offer = null
    viewModelScope.launch {
        val app = getApplication<android.app.Application>()
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                var last = -1
                val (bytes, page, icon) = ExtensionInstaller.download(source, onListing = { l -> main.post { if (ExtensionFlow.run == run) ExtensionFlow.listing = l } }) { f ->
                    if (ExtensionFlow.run != run) throw kotlinx.coroutines.CancellationException("cancelled") // Cancel was tapped
                    val pct = (f * 100).toInt()
                    if (pct != last) { last = pct; main.post { ExtensionFlow.progress = f } } // once per percent
                }
                main.post { ExtensionFlow.checking = true }
                val dir = File(ExtensionInstaller.staging(app), System.nanoTime().toString())
                ExtensionPackage.unpack(bytes, dir)
                val info = ExtensionPackage.inspect(dir, chromeId = (source as? InstallSource.Chrome)?.id)
                Triple(info, page, icon)
            }
        }
        if (ExtensionFlow.run != run) { result.getOrNull()?.first?.dir?.deleteRecursively(); return@launch } // cancelled meanwhile
        ExtensionFlow.fetching = null
        result.onSuccess { (info, page, icon) ->
            val installed = extensions[info.id]
            // checking for an update and finding the same version: nothing to do
            if (installed != null && installed.version == info.manifest.versionName) { info.dir.deleteRecursively(); Notices.show("${installed.name} is up to date") }
            else ExtensionFlow.pending = PendingInstall(info, page, icon, installed)
        }
            .onFailure { ExtensionFlow.listing = null; Notices.show("Couldn't get $name") }
    }
}

/** "Add" in the install dialog: the staged extension becomes installed and starts. */
fun BrowserViewModel.confirmExtension() {
    val p = ExtensionFlow.pending ?: return
    ExtensionFlow.pending = null
    ExtensionFlow.listing = null
    val engine = ExtensionFlow.engine ?: return
    if (!File(p.info.dir, "manifest.json").exists()) { Notices.show("Couldn't add ${p.info.name}. Try again"); return }
    val dest = engine.dirOf(p.info.id)
    engine.pages.stopBackground(p.info.id)
    dest.deleteRecursively(); dest.parentFile?.mkdirs()
    if (!p.info.dir.renameTo(dest)) { p.info.dir.copyRecursively(dest, overwrite = true); p.info.dir.deleteRecursively() }
    extensions.add(InstalledExtension(
        id = p.info.id, name = p.info.name, version = p.info.manifest.versionName, description = p.info.description,
        enabled = p.replacing?.enabled ?: true, incognito = p.replacing?.incognito ?: false,
        fit = p.info.compatibility.fit, reasons = p.info.compatibility.reasons, serious = p.info.compatibility.serious, source = p.source ?: p.replacing?.source,
        installedAt = System.currentTimeMillis(),
    ))
    engine.refresh(p.info.id, reason = if (p.replacing != null) "update" else "install")
    Notices.show(if (p.replacing != null) "Updated ${p.info.name}" else "Added ${p.info.name}", done = true)
}

/** "Cancel" in the install dialog: the staged files go. */
fun BrowserViewModel.cancelExtension() {
    // still downloading: it stops, and what comes of it is dropped
    if (ExtensionFlow.fetching != null) { ExtensionFlow.run++; ExtensionFlow.fetching = null }
    ExtensionFlow.listing = null
    ExtensionFlow.pending?.info?.dir?.deleteRecursively()
    ExtensionFlow.pending = null
}

fun BrowserViewModel.setExtensionEnabled(id: String, on: Boolean) {
    extensions.update(id) { copy(enabled = on) }
    ExtensionFlow.engine?.refresh(id)
}

/** Its content scripts stop (or start again) on [site] and its subdomains, from each page's next load. */
fun BrowserViewModel.setExtensionOffOn(id: String, site: String, off: Boolean) {
    val host = site.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').removePrefix("www.")
    if (host.isEmpty()) return
    extensions.update(id) { copy(offSites = if (off) (offSites + host).distinct() else offSites - host) }
    ExtensionFlow.engine?.refresh(id)
}

/** Clear Data: what the extension stored goes, and it starts again as if just installed. */
fun BrowserViewModel.clearExtensionData(id: String) {
    val name = extensions[id]?.name ?: return
    extensions.clearAll(id)
    ExtensionFlow.engine?.refresh(id, reason = "install")
    Notices.show("Cleared $name's data")
}

/** Check for Update: fetched again from where it came from; the install dialog offers a newer version. */
fun BrowserViewModel.checkExtensionUpdate(id: String) {
    val e = extensions[id] ?: return
    val source = storeSource(e.source) ?: return Notices.show("${e.name} wasn't added from a store")
    fetchExtension(source, e.name)
}

fun BrowserViewModel.setExtensionButton(id: String, show: Boolean) = extensions.update(id) { copy(showButton = show) }

fun BrowserViewModel.setExtensionIncognito(id: String, on: Boolean) {
    extensions.update(id) { copy(incognito = on) }
    ExtensionFlow.engine?.refresh(id)
}

/** Removes extension [id], with Undo: its files wait in the cache (cleared with it) and its data in memory until then. */
fun BrowserViewModel.removeExtension(id: String) {
    val e = extensions[id] ?: return
    val engine = ExtensionFlow.engine ?: return
    val data = extensions.savedData(id)
    val trash = java.io.File(getApplication<android.app.Application>().cacheDir, "ext-trash/$id").also { it.deleteRecursively(); it.parentFile?.mkdirs() }
    extensions.remove(id)
    engine.refresh(id)
    engine.dirOf(id).renameTo(trash)
    Notices.current = com.jlees.mibrowser.ui.common.Undo("Removed ${e.name}") {
        if (!trash.exists()) return@Undo
        engine.dirOf(id).deleteRecursively(); trash.renameTo(engine.dirOf(id))
        extensions.restore(e, data)
        engine.refresh(id)
    }
}
