# Extensions plan (Chrome / Firefox extensions on Android WebView)

Status: design notes, not started. Written 2026-11-16 after 1.7.0.

## Decision

Don't switch engines. Implement the WebExtensions API ourselves on top of the system WebView, in a new
`extensions/` package. Precedent: Kagi's Orion runs Chrome/Firefox extensions on WebKit the same way (partial support).

### Why not GeckoView
- Roughly +40–60 MB of APK per ABI and ~150 MB installed (estimates); mi is 4.3 MB today.
- Gecko security updates become ours to ship (Firefox's monthly cadence, forever). The system WebView updates itself through Play.
- Full rewrite: WebViewHost, ContentBlocker, PageScripts, media, incognito profiles and site permissions are all WebView-specific.
- Its Android extension support is limited too.
- Fallback if this plan proves insufficient: ship a second build, "mi Gecko", behind an engine interface. Don't start there.

### This plan vs GeckoView

| | WebView + our own extension layer (this plan) | GeckoView |
|---|---|---|
| APK size | +a few hundred KB (4.3 MB → ~5 MB) | +~40–60 MB per ABI (one universal APK carries every ABI's copy) |
| Installed size | Barely changes; the engine is shared with the system | ~150 MB+ |
| RAM | WebView as now; +a JavaScriptEngine sandbox only while a background script runs (+~20–40 MB for a background WebView, MV2 only) | Gecko runs its own processes; usually heavier than the system WebView, and it can't share memory with other apps' WebViews |
| Startup | Unchanged | Slower cold start (loading libxul) |
| Engine security updates | Google ships them through Play, for free | We ship one every Firefox security release, forever; a missed release leaves users exposed |
| Existing code | Kept; extensions are a new package | Rewrite WebViewHost, ContentBlocker, PageScripts, media, incognito profiles, site permissions, downloads hooks, previews |
| Extension compatibility | Partial: content-script and storage/messaging extensions work; webRequest is partial; no real isolated world; no password managers | Much higher: Mozilla's own API implementation, real isolated worlds, full blocking webRequest (uBlock Origin works) |
| Security of extensions | Weaker: content scripts share the page's world (run-first private channel mitigates spoofing) | Strong: real isolation, Mozilla-reviewed and signed add-ons |
| Who maintains the API | Us: every API we add, every Chrome/Firefox change we track | Mozilla |
| Install source | Firefox Add-ons (works) and Chrome Web Store (unofficial endpoint) | Firefox Add-ons only, natively |
| Site compatibility | Chromium, the same as Chrome | Gecko; occasional sites tested only on Chrome break |
| Development effort | Phased: phase 1 is small and useful alone (and gives userscripts) | Large up front, nothing until the port is done |
| Reversibility | Easy to stop at any phase | All in |

**Choose GeckoView instead if** users mainly want extensions this plan can't run (uBlock Origin-level request
control, password managers), or real isolation becomes a requirement. Even then, prefer a separate "mi Gecko" build
so the small default build stays as it is.

### Why not a Chromium fork (Kiwi-style)
Even bigger, and maintaining it is a full-time job.

## What we already have
`ContentBlocker` already uses all three hooks this needs:
- `WebViewCompat.addDocumentStartJavaScript`: inject before page scripts.
- `WebViewCompat.addWebMessageListener`: the bridge between page JS and Kotlin.
- `WebViewClient.shouldInterceptRequest`: see and block requests.

## Architecture

| Extension part | How it runs in mi |
|---|---|
| Package | `.xpi` (zip) / `.crx` (zip behind a header): unpack to app storage, then parse `manifest.json` |
| Content scripts | `addDocumentStartJavaScript` with a loader that checks the manifest's `matches` / `exclude_matches` in JS (the WebView API only filters by origin) and honours `run_at` and `all_frames` |
| Background (MV3 service worker) | Jetpack JavaScriptEngine (`androidx.javascriptengine`): sandboxed V8 with no DOM, which service workers don't have anyway, and far less memory than a WebView. Provide `fetch`, timers and `chrome.*` through the bridge |
| Background (MV2 page that uses the DOM) | Fallback: one hidden WebView for all extensions, with an iframe per extension |
| Extension origin | `WebViewAssetLoader` serves the unpacked files at `https://<id>.ext.mi/` |
| Popup / options / side panel | A bottom sheet holding a WebView at the extension's origin |
| `chrome.*` and `browser.*` | One JS layer exposing both callback style (Chrome) and promise style (Firefox). Calls go to Kotlin over the message bridge |

Background lifetime: start on an event or message, stop after ~30 s idle, as Chrome does for MV3 service workers.

### Isolating content scripts
WebView has no isolated worlds, so content scripts share the page's JavaScript world.
- **Run first and keep the keys private:** the document-start script runs before any page script. It opens the bridge
  channel and keeps it in a closure, deletes any global the page could reach, and keeps its own copies of
  `JSON`, `Array.prototype` methods, `fetch` and the like before the page can patch them.
- Kotlin accepts privileged calls only on the first channel opened per document or frame. Page scripts run too late to get it.
- This is the same approach Tampermonkey and Violentmonkey use. It doesn't isolate globals, but it stops pages from faking extension calls.
- Content scripts only get what Chrome gives them anyway (storage, runtime messaging, i18n), with storage split per origin. Anything sensitive goes through the background script.
- `world: "MAIN"` scripts (MV3) need none of this.
- Later: ShadowRealm, if Chromium ships it, gives real isolation under the same layer.
- **Never safe here:** password managers. Block them or warn.

### Network (`webRequest` / `declarativeNetRequest`)
- **Block and redirect:** done the same way as ContentBlocker (an empty response for sub-resources; `shouldOverrideUrlLoading` for the main frame).
- **Header changes:** for requests that match an extension's rule only, make the request in Kotlin from `shouldInterceptRequest`:
  - copy cookies from CookieManager;
  - add or remove request headers;
  - write `Set-Cookie` back;
  - strip response headers if asked (for example CSP or X-Frame-Options).
  Everything else stays on WebView's normal path, so its cache still works.
- **Request bodies:** WebView never exposes POST bodies. A document-start script wraps `fetch`, XHR and form submit to report bodies keyed by URL. That feeds `onBeforeRequest.requestBody` and lets POST requests be re-sent natively.
- **`chrome.proxy`:** androidx.webkit `ProxyController` (app-wide), optionally pointed at a small proxy inside mi. That gives host-level blocking and logging plus proxy/VPN-style extensions.
- **Never:** decrypting HTTPS with a self-installed certificate.

### API coverage

| API | Implementation | Expected |
|---|---|---|
| `storage` | One JSON store per extension | Full |
| `runtime` messaging | Kotlin routes between content scripts, background and popups | Full |
| `i18n` | `_locales/` | Full |
| `tabs` | TabsStore (query, create, update, sendMessage, onUpdated) | Good |
| `scripting` / `tabs.executeScript` | `evaluateJavascript` | Good |
| `alarms` | Handler timers | Good |
| `notifications` | Android notifications | Good |
| `contextMenus` | mi's link menu (`ui/ContextMenu.kt`) | Good |
| `downloads` | mi's downloader | Good |
| `declarativeNetRequest` | See Network | Good (block, redirect, header changes) |
| `webRequest` | See Network | Partial |
| `webNavigation` | WebViewClient callbacks | Partial |
| `cookies` | CookieManager | Partial |
| `proxy` | ProxyController | Partial |
| Cross-origin `fetch` (host permissions) | Native HTTP | Good |
| `identity`, `nativeMessaging`, `devtools` | None | Not supported |

### Where extension features appear in mi

| Extension feature | In mi |
|---|---|
| Toolbar button / badge | A row in the tab menu; a dot on the menu when there's a badge |
| Popup / side panel | Bottom sheet |
| `chrome.omnibox` | The Pill: the extension's keyword works like a `!` shortcut, and its suggestions show in the Pill's list |
| Context menus | Link / long-press menu |
| New-tab override | Home page (with a setting) |
| Search engine override | Search engine list |
| Options page | Settings › Extensions › the extension |
| Keyboard shortcuts | Skip (or gesture slots) |

## Installing
- **Firefox Add-ons (addons.mozilla.org) first:** a public API, signed `.xpi` files, update checks, and an Android-compatible list of extensions (already mobile-friendly, so it's the first target set).
- **Chrome Web Store:** the `.crx` download endpoint is unofficial. Treat it as best effort.
- **Before installing:** check the manifest's permissions and scan the code for the `chrome.*` / `browser.*` calls it uses. Show "Works", "Mostly works (no X)" or "Won't work".
- **Later:** a community-kept "tested in mi" JSON file in the repo, shown as a badge.

## Rules (from CLAUDE.md)
- `extensions/` package with:
  - an `ExtensionsStore` (installed extensions, their storage);
  - `ExtensionRules.kt`, pure functions: URL-pattern matching, `run_at` ordering, the compatibility check, permission diffing;
  - a WebView hook class delegated from WebViewHost;
  - an Extensions section in Settings.
- Page JS goes in `assets/js/` (the extension layer and the content-script loader), never inline.
- Incognito: extensions off by default, with a per-extension "Allow in incognito" switch. Storage is split by profile, and nothing from incognito is written to disk (`keepsTraces`).
- Tests:
  - the rules (URL patterns especially);
  - Robolectric for the store and the native request path, against a local HttpServer.

## Known limits
- No real isolated world (globals are shared).
- No header changes on requests that take WebView's normal path. Only re-sent requests can be changed.
- The memory cost of a background WebView if an MV2 extension needs one (~20–40 MB, estimated).
- CSP still blocks extensions that add `<script>` tags or use `eval` inside the page.

## Phases
1. **Content scripts, storage and i18n**, with the run-first private channel. Userscripts (Greasemonkey/Tampermonkey `// ==UserScript==` headers, the basic `GM_*` functions) come almost free here.
2. **Background in JavaScriptEngine** and runtime messaging; popups and options in a sheet.
3. **`tabs`, `scripting`, `contextMenus`, `alarms`, `notifications`.**
4. **`declarativeNetRequest`**, the native re-send path, body capture, `omnibox` in the Pill.
5. **Install from Firefox Add-ons** with the compatibility check; test against the top 20 Android-compatible extensions and record the results.

**Likely to work:** Dark Reader, Return YouTube Dislike, SponsorBlock, cookie-banner hiders, translation and dictionary popups, site tweaks.

**Not targeted:** uBlock Origin (mi's blocker covers it), password managers, VPNs, devtools.
