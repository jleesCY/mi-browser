// The extension engine's decisions, as pure functions: reading a manifest, matching pages against an extension's
// patterns, finding which browser APIs its code uses, and how well it will work in mi (the rating shown before
// installing). Nothing here touches Android, so it's all unit-tested (ExtensionRulesTest).
package com.jlees.mibrowser.extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// ---------------------------------------------------------------- manifest

/** One `content_scripts` entry: which pages, what to run, and when. */
data class ContentScript(
    val matches: List<String>,
    val excludeMatches: List<String> = emptyList(),
    val includeGlobs: List<String> = emptyList(),
    val excludeGlobs: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    /** document_start, document_end or document_idle (the default). */
    val runAt: String = "document_idle",
    val allFrames: Boolean = false,
    /** MV3 "MAIN": runs as the page's own script, without extension APIs. */
    val mainWorld: Boolean = false,
)

/** What mi needs from an extension's manifest.json (the rest is kept as raw JSON for `runtime.getManifest`). */
data class Manifest(
    val version: Int,
    val name: String,
    val versionName: String,
    val description: String,
    val defaultLocale: String?,
    /** Firefox's `browser_specific_settings.gecko.id`, if it has one. */
    val geckoId: String?,
    val icons: Map<Int, String>,
    /** Permissions it needs to run. */
    val permissions: List<String>,
    /** Permissions it asks for later, for extras (optional_permissions). */
    val optionalPermissions: List<String>,
    val hostPermissions: List<String>,
    val contentScripts: List<ContentScript>,
    /** MV2 background scripts, in order. */
    val backgroundScripts: List<String>,
    /** MV2 background page. */
    val backgroundPage: String?,
    /** MV3 service worker. */
    val serviceWorker: String?,
    val backgroundModule: Boolean,
    /** The toolbar button's popup (action / browser_action / page_action). */
    val popup: String?,
    val hasAction: Boolean,
    val actionTitle: String?,
    val actionIcons: Map<Int, String>,
    val optionsPage: String?,
    /** Files pages may load (MV2: patterns; MV3: each entry's resources, any site). */
    val webAccessible: List<String>,
    val urlOverrides: List<String>,
    val hasDevtools: Boolean,
    val isTheme: Boolean,
    val raw: JsonObject,
) {
    val hasBackground get() = backgroundScripts.isNotEmpty() || backgroundPage != null || serviceWorker != null
    /** A side panel (Chrome's side_panel, Firefox's sidebar_action): mi shows it as a sheet from the extension's button. */
    val hasPanel get() = raw["side_panel"] is JsonObject || raw["sidebar_action"] is JsonObject
    /** It gets a button in the tab menu. */
    val hasButton get() = hasAction || popup != null || hasPanel
    /** The best icon for [size]dp-ish use: the smallest at least that big, else the biggest. */
    fun icon(size: Int): String? = (actionIcons.ifEmpty { icons }).let { m -> m.entries.filter { it.key >= size }.minByOrNull { it.key }?.value ?: m.maxByOrNull { it.key }?.value }
}

// Reading JSON from manifests and from extensions' calls (used across the package)
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
/** A list of text: an array's, or a single value as a list of one; nothing else is an empty list. */
internal fun JsonElement?.strings(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.str() }
    is JsonPrimitive -> listOfNotNull(contentOrNull)
    else -> emptyList()
}
/** An object, or an empty one. */
internal fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonElement?.objOrNull(): JsonObject? = this as? JsonObject
private fun iconMap(e: JsonElement?): Map<Int, String> = when (e) {
    is JsonObject -> e.mapNotNull { (k, v) -> k.toIntOrNull()?.let { s -> v.str()?.let { s to it } } }.toMap()
    is JsonPrimitive -> e.contentOrNull?.let { mapOf(16 to it) } ?: emptyMap()
    else -> emptyMap()
}

/** Reads a manifest.json; throws when it isn't one (no name or version). */
fun parseManifest(raw: JsonObject): Manifest {
    val mv = raw["manifest_version"].int() ?: 2
    val bg = raw["background"].objOrNull()
    val action = (raw["action"] ?: raw["browser_action"] ?: raw["page_action"]).objOrNull()
    val perms = raw["permissions"].strings()
    val optional = raw["optional_permissions"].strings()
    // MV2 puts host patterns among its permissions; MV3 has host_permissions
    val hosts = raw["host_permissions"].strings() + (perms + optional).filter { it.contains("://") || it == "<all_urls>" }
    val war = when (val w = raw["web_accessible_resources"]) {
        is JsonArray -> w.flatMap { e -> if (e is JsonObject) e["resources"].strings() else listOfNotNull(e.str()) }
        else -> emptyList()
    }
    return Manifest(
        version = mv,
        name = raw["name"].str() ?: error("no name"),
        versionName = raw["version"].str() ?: error("no version"),
        description = raw["description"].str().orEmpty(),
        defaultLocale = raw["default_locale"].str(),
        geckoId = (raw["browser_specific_settings"] ?: raw["applications"]).objOrNull()?.get("gecko").objOrNull()?.get("id").str(),
        icons = iconMap(raw["icons"]),
        permissions = perms.filterNot { it.contains("://") || it == "<all_urls>" },
        optionalPermissions = optional.filterNot { it.contains("://") || it == "<all_urls>" },
        hostPermissions = hosts,
        contentScripts = (raw["content_scripts"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.objOrNull() ?: return@mapNotNull null
            ContentScript(
                matches = o["matches"].strings(), excludeMatches = o["exclude_matches"].strings(),
                includeGlobs = o["include_globs"].strings(), excludeGlobs = o["exclude_globs"].strings(),
                js = o["js"].strings(), css = o["css"].strings(),
                runAt = o["run_at"].str() ?: "document_idle",
                allFrames = o["all_frames"].bool() ?: false,
                mainWorld = o["world"].str() == "MAIN",
            )
        },
        backgroundScripts = bg?.get("scripts").strings(),
        backgroundPage = bg?.get("page").str(),
        serviceWorker = bg?.get("service_worker").str(),
        backgroundModule = bg?.get("type").str() == "module",
        popup = action?.get("default_popup").str()?.takeIf { it.isNotBlank() },
        hasAction = action != null,
        actionTitle = action?.get("default_title").str(),
        actionIcons = iconMap(action?.get("default_icon")),
        optionsPage = raw["options_ui"].objOrNull()?.get("page").str() ?: raw["options_page"].str(),
        webAccessible = war,
        urlOverrides = raw["chrome_url_overrides"].objOrNull()?.keys?.toList().orEmpty(),
        hasDevtools = raw["devtools_page"] != null,
        isTheme = raw["theme"] != null,
        raw = raw,
    )
}

/** "__MSG_appName__" → its message from [messages] (keys compared case-insensitively, as Chrome does). */
fun localize(text: String, messages: Map<String, String>): String =
    Regex("__MSG_([A-Za-z0-9_@]+)__").replace(text) { m -> messages[m.groupValues[1].lowercase()] ?: m.value }

// ---------------------------------------------------------------- match patterns

/**
 * A Chrome/Firefox match pattern (like `*` + `://` + `*.example.com/path` + `*`, or "<all_urls>") against [url]. The scheme is http(s),
 * file or "*" (http and https); the host "*" (any), "*.example.com" (it and its subdomains) or exact; the path a glob.
 */
fun matchesPattern(pattern: String, url: String): Boolean {
    if (pattern == "<all_urls>") return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")
    val m = Regex("""^(\*|https?|file|wss?|ftp)://([^/]*)(/.*)?$""").find(pattern) ?: return false
    val (scheme, host, path) = m.destructured
    val u = Regex("""^([a-z]+)://([^/?#]*)([^#]*)""").find(url) ?: return false
    val (uScheme, uAuthority, uRest) = u.destructured
    if (scheme == "*") { if (uScheme != "http" && uScheme != "https") return false } else if (scheme != uScheme) return false
    val uHost = uAuthority.substringAfter('@').substringBefore(':').lowercase()
    val h = host.substringBefore(':').lowercase()
    val hostOk = when {
        h == "*" -> true
        h.startsWith("*.") -> uHost == h.removePrefix("*.") || uHost.endsWith(h.removePrefix("*"))
        else -> uHost == h
    }
    if (!hostOk) return false
    return globMatches(path.ifEmpty { "/" }, uRest.ifEmpty { "/" })
}

/** A glob with "*" (anything) and "?" (one character) against [text], the whole of it. */
fun globMatches(glob: String, text: String): Boolean {
    val re = buildString {
        append('^')
        glob.forEach { c -> when (c) { '*' -> append(".*"); '?' -> append('.'); else -> append(Regex.escape(c.toString())) } }
        append('$')
    }
    return Regex(re, RegexOption.DOT_MATCHES_ALL).matches(text)
}

/** Whether content script [cs] runs on [url] (its matches and globs, as Chrome applies them). */
fun runsOn(cs: ContentScript, url: String): Boolean =
    cs.matches.any { matchesPattern(it, url) } &&
        cs.excludeMatches.none { matchesPattern(it, url) } &&
        (cs.includeGlobs.isEmpty() || cs.includeGlobs.any { globMatches(it, url) }) &&
        cs.excludeGlobs.none { globMatches(it, url) }

/**
 * An extension's code for its own world in a page (content scripts, executeScript), where a browser gives it a window
 * of its own whose "chrome" and "browser" are the extension APIs. mi runs it in the page with chrome and browser as
 * its function's arguments, but `window` stays the page's (a stand-in there would fail checks like
 * `event.source === window`), so "window.chrome" and "window.browser" are read as those arguments instead.
 */
fun isolatedWorld(code: String): String = DYNAMIC_IMPORT.replace(WINDOW_API.replace(code) { it.groupValues[1] }, "__miImport(")
private val WINDOW_API = Regex("""(?<![\w$.])window\.(chrome|browser)\b""")
// ponytail: a plain regex, so a method *defined* as `import(x) {…}` in a content script would be renamed too; parse
// the code if an extension ever does that
/** `import(…)`: WebView can't load an extension's native address, so mi's ext-content.js loads it (`import.meta`, a method called import, are left alone). */
private val DYNAMIC_IMPORT = Regex("""(?<![\w$.])import\s*\(""")

/** Whether [path] (a file of the extension) may be loaded by web pages (web_accessible_resources). */
fun webAccessible(path: String, patterns: List<String>): Boolean {
    val p = path.trimStart('/')
    return patterns.any { globMatches(it.trimStart('/'), p) }
}

// ---------------------------------------------------------------- compatibility

/** How well an extension works in mi, shown before it's installed and on its page. */
@Serializable
/** A rating; [label]: how every screen words it. */
enum class Fit(val label: String) { WORKS("Works"), MAYBE("Might Have Problems"), NO("Not Recommended") }

/** A rating and its reasons; [serious]: the ones that make it Not recommended (the rest are why it might have problems). */
@Serializable
data class Compatibility(val fit: Fit, val reasons: List<String>, val serious: List<String> = emptyList())

/**
 * Whether an extension is made for phones, as mi requires of every one it adds: its Firefox Add-ons listing's
 * [listing] compatibility (app → versions) names Android, as Firefox for Android requires; one not listed there counts
 * only if its manifest ([manifestRaw]) declares Android itself (browser_specific_settings.gecko_android). Anything else
 * (desktop only, or no word either way) isn't added.
 */
fun madeForPhones(listing: kotlinx.serialization.json.JsonObject?, manifestRaw: kotlinx.serialization.json.JsonObject?): Boolean =
    if (listing != null) "android" in listing
    else (manifestRaw?.get("browser_specific_settings") as? kotlinx.serialization.json.JsonObject)?.containsKey("gecko_android") == true

/**
 * The extension APIs mi implements completely ("namespace" for all of it, or "namespace.member"). Only extensions that
 * use nothing outside this list (and nothing below) are rated Works.
 */
val FULL_APIS = setOf(
    "runtime.sendMessage", "runtime.onMessage", "runtime.getURL", "runtime.getManifest", "runtime.id", "runtime.lastError",
    "runtime.onInstalled", "runtime.onStartup", "runtime.connect", "runtime.onConnect", "runtime.openOptionsPage",
    "runtime.getPlatformInfo", "runtime.setUninstallURL", "runtime.getBrowserInfo", "runtime.onMessageExternal",
    "runtime.onUpdateAvailable", "runtime.onSuspend", "runtime.reload", "runtime.getContexts",
    "storage", "i18n.getMessage", "i18n.getUILanguage", "i18n.getAcceptLanguages",
    "tabs.query", "tabs.get", "tabs.getCurrent", "tabs.create", "tabs.update", "tabs.remove", "tabs.reload",
    "tabs.sendMessage", "tabs.onUpdated", "tabs.onActivated", "tabs.onCreated", "tabs.onRemoved", "tabs.executeScript",
    "tabs.insertCSS", "tabs.removeCSS", "tabs.TAB_ID_NONE",
    "scripting.executeScript", "scripting.insertCSS", "scripting.removeCSS",
    "alarms", "action", "browserAction", "pageAction",
    "extension.getURL", "extension.inIncognitoContext", "extension.isAllowedIncognitoAccess", "extension.isAllowedFileSchemeAccess",
    "permissions.contains", "permissions.getAll", "permissions.request", "permissions.remove", "permissions.onAdded",
    "permissions.onRemoved", "windows.getCurrent", "windows.getLastFocused", "windows.getAll", "windows.WINDOW_ID_CURRENT",
    "notifications.create", "notifications.clear", "notifications.getAll", "notifications.onClicked", "notifications.onClosed",
    "windows.get", "windows.create", "windows.update",
    "tabs.captureVisibleTab", "tabs.goBack", "tabs.goForward", "tabs.duplicate", "tabs.getZoom",
    "scripting.registerContentScripts", "scripting.updateContentScripts", "scripting.getRegisteredContentScripts",
    "scripting.unregisterContentScripts", "contentScripts.register",
    // tab events mi has but that never happen in it (one window, tabs never moved between windows or replaced)
    "tabs.onReplaced", "tabs.onAttached", "tabs.onDetached", "tabs.onMoved", "tabs.onHighlighted", "tabs.onZoomChange", "scripting.ExecutionWorld",
    // shown as a sheet from the extension's button
    "sidePanel", "sidebarAction",
    "offscreen", "dns", "management.getSelf", "management.uninstallSelf",
    "identity.launchWebAuthFlow", "identity.getRedirectURL",
)

/** APIs that work in part, or that mi only approximates: an extension using them might have problems. */
private val PARTIAL = mapOf(
    "contextMenus" to "adds to the long-press menu, which mi supports for links, images and selected text only",
    "menus" to "adds to the long-press menu, which mi supports for links, images and selected text only",
    "webRequest" to "watches and changes web requests, which mi supports except for requests that send data, like forms",
    "webRequestBlocking" to "blocks and changes web requests as they happen, which mi supports except for requests that send data, like forms",
    "declarativeNetRequest" to "changes web requests by rules, which mi supports except for requests that send data, like forms",
    "declarativeNetRequestWithHostAccess" to "changes web requests by rules, which mi supports except for requests that send data, like forms",
    "declarativeNetRequestFeedback" to "reports which of its rules matched, which mi doesn't show",
    "webNavigation" to "follows page loads in detail, which mi only partly reports",
    "cookies" to "reads and changes cookies, which mi supports except for some of their details, like when they expire",
    "downloads" to "manages downloads, which mi supports except for files an extension makes itself",
    "identity" to "signs you in through the browser, which mi supports except for Google accounts",
    "history" to "reads your history, which mi doesn't share with extensions yet",
    "bookmarks" to "reads your bookmarks, which mi doesn't share with extensions yet",
    "topSites" to "reads your most visited sites, which mi doesn't share yet",
    "sessions" to "restores closed tabs, which mi doesn't support yet",
    "commands" to "uses keyboard shortcuts, which phones don't have",
    "omnibox" to "adds a keyword to the address bar, which mi doesn't support yet",
    "userScripts" to "runs your own scripts, which mi doesn't support yet",
    "tabGroups" to "groups tabs, which mi doesn't have",
    "search" to "runs searches, which mi doesn't support yet",
    "management" to "manages other extensions, which mi doesn't allow (it can see and remove only itself)",
    "idle" to "checks if you're idle, which mi tells only by the screen locking",
    "power" to "keeps the screen awake, which mi doesn't support",
    "privacy" to "changes privacy settings, which mi keeps for itself",
    "browsingData" to "clears browsing data, which mi does only all at once, not from a certain time",
    "contentSettings" to "changes site settings, which mi keeps for itself",
    "fontSettings" to "changes fonts, which mi doesn't support",
    "tts" to "reads text aloud, which mi doesn't support yet",
    "ttsEngine" to "adds a voice, which mi doesn't support",
    "contextualIdentities" to "uses containers, which mi doesn't have",
    "find" to "searches the page, which mi doesn't let extensions do",
    "theme" to "changes the browser's look, which mi doesn't allow",
    "geolocation" to "asks where you are from the background, which mi doesn't support",
    "gcm" to "receives push messages, which mi doesn't support",
    "instanceID" to "receives push messages, which mi doesn't support",
    "processes" to "reads browser processes, which mi doesn't support",
    "system" to "reads details of your phone, which mi doesn't share",
    "readingList" to "uses a reading list, which mi doesn't share",
    "windows" to "opens and arranges windows, which mi only approximates with tabs",
    "tabs.discard" to "unloads tabs, which mi doesn't let extensions do",
    "tabs.move" to "moves tabs, which mi doesn't support",
    "tabs.highlight" to "selects several tabs, which mi doesn't have",
    "tabs.setZoom" to "zooms pages, which mi doesn't let extensions do",
    "tabs.detectLanguage" to "detects a page's language, which mi reads from the page's own label",
    "tabs.connect" to "keeps a connection to a page, which mi only partly supports",
    "i18n.detectLanguage" to "detects languages, which mi doesn't support yet",
    "extension.getBackgroundPage" to "reaches into its background page directly, which mi only partly supports",
    "extension.getViews" to "reaches into its own pages directly, which mi only partly supports",
    "runtime.getBackgroundPage" to "reaches into its background page directly, which mi only partly supports",
)

/** What mi can't do at all: an extension that needs any of these is strongly not recommended. */
private val UNSUPPORTED = mapOf(
    "nativeMessaging" to "talks to an app on a computer, which isn't possible on a phone",
    "runtime.connectNative" to "talks to an app on a computer, which isn't possible on a phone",
    "runtime.sendNativeMessage" to "talks to an app on a computer, which isn't possible on a phone",
    "identity.getAuthToken" to "signs you in with the Google account in Chrome, which mi can't do",
    "debugger" to "uses the browser's debugger, which mi doesn't have",
    "devtools" to "adds to developer tools, which mi doesn't have",
    "proxy" to "sends your traffic through a proxy, which mi doesn't allow extensions to do",
    "vpnProvider" to "acts as a VPN, which mi doesn't allow",
    "enterprise" to "needs a managed work device",
    "platformKeys" to "uses your device's security keys, which mi can't share",
    "certificateProvider" to "provides security certificates, which mi can't use",
    "printerProvider" to "adds printers, which mi can't use",
    "documentScan" to "uses a scanner, which mi can't use",
    "desktopCapture" to "records your screen, which mi doesn't allow",
    "tabCapture" to "records tabs, which mi doesn't allow",
    "pageCapture" to "saves pages as files, which mi doesn't support",
    "wallpaper" to "changes the computer's wallpaper",
    "loginState" to "needs a Chromebook",
    "fileSystemProvider" to "adds file systems, which mi can't use",
)

/** The `chrome.x.y` / `browser.x.y` calls in [code], as "x.y" (or "x" alone). */
fun scanApis(code: String): Set<String> =
    Regex("""\b(?:chrome|browser)\.([a-zA-Z]+)(?:\.([a-zA-Z_]+))?""").findAll(code).map { m ->
        val ns = m.groupValues[1]; val member = m.groupValues[2]
        if (member.isEmpty()) ns else "$ns.$member"
    }.toSet()

/** What the code does that mi can't check from the outside, or that pages may block. */
data class CodeFlags(
    /** `chrome[...]` / `browser[...]`: APIs reached by a name worked out while running. */
    val dynamicApi: Boolean = false,
    /** A content script runs code from text (eval, new Function), which many sites' security settings forbid. */
    val contentEval: Boolean = false,
)

fun scanFlags(backgroundCode: String, contentCode: String) = CodeFlags(
    dynamicApi = Regex("""\b(?:chrome|browser)\s*\[""").containsMatchIn(backgroundCode + "\n" + contentCode),
    contentEval = Regex("""\beval\s*\(|\bnew\s+Function\s*\(""").containsMatchIn(contentCode),
)

private fun supportedFully(api: String): Boolean {
    val ns = api.substringBefore('.')
    return api in FULL_APIS || ns in FULL_APIS ||
        // events and constants hang off fully supported namespaces (chrome.storage.local, chrome.alarms.onAlarm)
        (api.contains('.') && ns in setOf("storage", "alarms", "action", "browserAction", "pageAction"))
}

/**
 * How well an extension works in mi, from its [manifest], the APIs its code uses ([apis], from [scanApis]) and [flags].
 * Works only when everything it uses is fully supported; Maybe when anything is partial or can't be checked; No when it
 * needs something mi can't do. Reasons are short, plain lines for the install sheet.
 */
fun compatibility(manifest: Manifest, apis: Set<String>, flags: CodeFlags, passwordManager: Boolean = false): Compatibility {
    val no = linkedSetOf<String>(); val maybe = linkedSetOf<String>()
    if (manifest.isTheme) no += "It's a theme, and themes can't change mi's look"
    if (passwordManager) no += "It's a password manager, and mi can't keep passwords safe from the pages you visit"
    // a developer-tools panel is an extra: Firefox for Android has none either, and mi only adds extensions made for phones
    if (manifest.hasDevtools) maybe += "Some of its extras won't work (it ${UNSUPPORTED.getValue("devtools").substringBefore(", which")})"
    // what it needs to run decides "Not recommended"; what it only asks for later, for extras, is a "Maybe"
    for (p in manifest.permissions) {
        UNSUPPORTED[p]?.let { no += "It $it" }
        PARTIAL[p]?.let { maybe += "It $it" }
    }
    for (p in manifest.optionalPermissions) {
        UNSUPPORTED[p]?.let { maybe += "Some of its extras won't work (it ${it.substringBefore(", which")})" }
        PARTIAL[p]?.let { maybe += "It $it" }
    }
    for (api in apis) {
        val ns = api.substringBefore('.')
        val unsupported = UNSUPPORTED[api] ?: UNSUPPORTED[ns]
        // an API it can't do without: its permission is one it needs (runtime.connectNative needs nativeMessaging)
        val needed = (if (ns == "runtime") "nativeMessaging" else ns) in manifest.permissions
        when {
            unsupported != null && needed -> no += "It $unsupported"
            unsupported != null -> maybe += "Some of its extras won't work (it ${unsupported.substringBefore(", which")})"
            supportedFully(api) -> {}
            PARTIAL[api] != null -> maybe += "It ${PARTIAL.getValue(api)}"
            PARTIAL[ns] != null -> maybe += "It ${PARTIAL.getValue(ns)}"
            // the namespace alone (`if (chrome.tabs)`) or one of its constants (`chrome.runtime.OnInstalledReason`, defined
            // in ext-api.js): fine where mi supports some of it
            (!api.contains('.') || api.substringAfter('.').first().isUpperCase()) && FULL_APIS.any { it.startsWith("$ns.") } -> {}
            ns in KNOWN_NAMESPACES -> maybe += "It uses $api, which mi doesn't fully support yet"
            else -> {} // not an extension API (a variable that happens to be called chrome or browser)
        }
    }
    if (manifest.urlOverrides.isNotEmpty()) maybe += "It replaces the browser's ${manifest.urlOverrides.joinToString(" and ") { it.replace("newtab", "new tab page") }}, which mi doesn't allow yet"
    if (flags.dynamicApi) maybe += "It reaches some features in a way mi can't check before installing"
    if (flags.contentEval) maybe += "It runs code in a way some sites block"
    if (manifest.contentScripts.isEmpty() && !manifest.hasBackground && manifest.popup == null && manifest.optionsPage == null)
        no += "It doesn't have anything mi can run"
    return when {
        no.isNotEmpty() -> Compatibility(Fit.NO, no.toList() + maybe, serious = no.toList())
        maybe.isNotEmpty() -> Compatibility(Fit.MAYBE, maybe.toList())
        else -> Compatibility(Fit.WORKS, emptyList())
    }
}


/** Every extension API namespace (so a variable named `chrome.foo` in a page script isn't mistaken for one). */
private val KNOWN_NAMESPACES = (FULL_APIS.map { it.substringBefore('.') } + PARTIAL.keys.map { it.substringBefore('.') } +
    UNSUPPORTED.keys.map { it.substringBefore('.') }).toSet()

/** Password managers can't be safe here (content scripts share the page's world): told apart by what they say they are. */
fun looksLikePasswordManager(name: String, description: String): Boolean =
    Regex("""password manager|passwords? vault|autofill passwords|bitwarden|lastpass|1password|dashlane|keeper|nordpass|roboform|enpass|proton pass""", RegexOption.IGNORE_CASE)
        .containsMatchIn("$name $description")

/** An id safe as a folder and host name: from Firefox's gecko id, else a hash of the name. */
fun extensionId(geckoId: String?, name: String): String {
    val raw = geckoId ?: name
    val clean = raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return clean.take(40).ifEmpty { "ext" } + "-" + Integer.toHexString(raw.hashCode()).takeLast(6)
}

// ---------------------------------------------------------------- store pages

/** Where an extension can be installed from. */
sealed interface InstallSource {
    /** Firefox Add-ons, by its slug (the name in its address). */
    data class Amo(val slug: String) : InstallSource
    /** A package file (.xpi) at an address. */
    data class File(val url: String) : InstallSource
}

/** An extension's page on Firefox Add-ons, as an install source; null for any other page. */
fun storeSource(url: String?): InstallSource? {
    if (url == null) return null
    Regex("""^https://addons\.mozilla\.org/(?:[a-zA-Z-]+/)?(?:firefox|android)/addon/([^/?#]+)""").find(url)?.let { return InstallSource.Amo(java.net.URLDecoder.decode(it.groupValues[1], "UTF-8")) }
    return null
}

/** The extension's name from its Firefox Add-ons page's title ("Dark Reader – Get this Extension for 🦊 Firefox (en-US)"). */
fun storeTitleName(title: String): String {
    val markers = listOf(" – Get this Extension", " – Get this Add-on")
    // a page that isn't the listing yet (a bot check, "Loading…"): no name rather than its title
    if (markers.none { title.contains(it) }) return ""
    return markers.fold(title) { t, m -> t.substringBefore(m) }.trim()
}
