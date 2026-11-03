# mi. — React Native → Kotlin/Compose port plan

Branch: `kotlin-1.0`. Source of truth for behavior: the RN app at commit `c22a4d8` (v0.10.3).
Goal: an Android-only Kotlin + Jetpack Compose app that behaves identically, feature for feature, gesture for gesture.

## Status (2026-11-03)
- D1–D17 approved as written (build intended behavior). Settings do **not** carry over from 0.10.3; release will be signed with the EAS key.
- **Weather widget removed** by request: no home widget, no setting, no Open-Meteo/location fetch. Location permissions stay for website geolocation.
- Phases 0–9 code is written (`app/`); RN source is in `legacy/`. Tests: `./gradlew :app:testDebugUnitTest`.
- Verified on the `mi_test` AVD (API 36): browsing, pill swap/hide/recall, keyboard docking, recent drawer + favorites, submenu, context menu → new tab, Tabs/History/Bookmarks/Settings sheets, swipe rows, confirm/alert/modals, find in page, QR scan (camera) + generate, error pages, SSL bypass, adaptive theme, clock/date, `mibrowser://` deep links.
- **Not yet verified on device**: tab cards + previews, drag-reorder (tabs/bookmarks/favorites/menu icons), favorite drag-off delete, import/export `.midata`, background crop, downloads, file upload, fullscreen video + rotation, `target=_blank`, print, intent:// links, desktop/reader toggles, background refresh, wipe/reset.
- Emulator note: on web pages the status-bar strip rendered white with dark icons even though the window reports transparent + light icons; looks like a swiftshader compositing artifact. Check on a real phone.
- **Design changed to Material 3 Expressive** (user request, after the parity port): M3 color schemes seeded from the accent (MaterialKolor, Vibrant style) or wallpaper dynamic color on Android 12+; theme modes Light/Dark/System; Google Sans Flex font (subset, wght+ROND axes); Material Symbols Rounded icons (`res/drawable/sym_*.xml`, map in `ui/common/Symbols.kt`); M3 dialogs, switches, sliders, connected button groups, FABs, wavy progress, LoadingIndicator, MaterialShapes; expressive motion springs. Corners setting is now 0/12/28. The design specs in §5/§6 (RN colors, Ionicons, Nunito) no longer apply; behavior specs still do.
- **Gestures lightened**: platform touch slop; pill swap at 16dp or 0.3 dp/ms; back/forward at 32dp (or 12dp + fling); hide at 35% or 0.3 dp/ms; recall at 10dp; sheet fling 0.4 dp/ms; drawer at 32dp; swipe-reveal at 1/3; history delete at 25% or fling.
- **Gesture fixes**: `panGesture` tracks the finger in raw screen coordinates (local coordinates made moving elements lag at half speed and jitter) and always delivers a release when a drag is interrupted (fixes the pill stuck half-way). Same raw-coordinate tracking in drag-to-reorder.
- **Rounded Font** setting (Interface): turns on Google Sans Flex's ROND axis for all weights.
- **Recall handle pinning**: swipe the hidden-pill handle sideways; it follows the finger and pins left/center/right (`recallPosition`).
- Accents reduced to: adaptive (wallpaper, Android 12+), gray (monochrome scheme), blue, green, red, orange, yellow, purple — one horizontally scrollable row. Font Weight setting removed. The "mi." logo always uses the rounded font.
- Gesture robustness: drag updates apply undispatched (no stale move can cancel a settle), the toolbar drag is clamped at the search position, swipe rows use synchronous drag state, and drag-to-reorder measures from the touch-down's raw position (the held item was being thrown off-screen).
- **Ponytail audit applied**: `legacy/` and `.vscode/` deleted. Every `legacy/...` path in this plan and in code comments now means "the RN source at commit `c22a4d8`" (`git show c22a4d8:<path>`). Also: springs consolidated into `ui/common/Ui.kt` `Motion`, unused icons/helpers/params removed, settings expanders use local state, find-in-page calls `WebViewHost` directly, `uniqueId()` is a UUID.
- Second audit: color aliases removed (code uses `ui.cs.<M3 role>` directly), all springs come from `Motion`, `proguard-rules.pro` dropped (default R8 rules suffice; release APK builds at ~2.9 MB).
- **In-app updates** (`data/Updates.kt`): on each launch, checks GitHub `releases/latest`; if the tag (`vX.Y.Z`) is newer than `versionName` and has a `.apk` asset on github.com, shows Install / Later / Skip this version (skip is persisted as `skippedUpdate`). Install downloads to `cache/updates/` and opens the system installer (`REQUEST_INSTALL_PACKAGES`). Releases must keep the `vX.Y.Z` tag + single `.apk` asset convention, bump `versionName`, and be signed with the same key, or Android rejects the update.
- Phase 10 (release signing, R8 check, docs, delete `legacy/`) not started.

---

## 0. Approach

### Why not start from the Kotlin that RN "generates"
`expo prebuild` / `expo run:android` produces an `android/` folder containing only `MainActivity.kt` + `MainApplication.kt` (~60 lines of React host boilerplate). Every feature, gesture and animation lives in the JS bundle, so the generated Kotlin has nothing to port. What *is* worth reading is the native source of the RN libraries, because it defines what the app actually does on Android today. That was done for `react-native-webview@13.15.0` while writing this plan (see §1).

So this is a hand port: one RN file maps to one Kotlin file, with the RN source kept next to it for reference.

### Repo layout during the port
- Phase 0 moves the RN app into `legacy/` (`app/`, `src/`, `assets/`, `app.json`, `package.json`, etc.). The Gradle project needs `app/`, so the RN `app/` routes folder has to move anyway.
- Every Kotlin file starts with a header comment naming its legacy source, e.g. `// port of legacy/src/components/Tabs/TabCard.tsx`.
- **Exact pixel values come from the legacy file.** This plan gives the behavioral spec plus the numbers that matter for feel. For padding, font sizes and margins, copy the literal numbers from the referenced legacy file. RN units are dp, so `fontSize: 16` → `16.sp × fontScale`, `padding: 15` → `15.dp`.
- Phase 10 deletes `legacy/`, `bun.lock`, `package*.json`, `eas.json`, `.easignore`, `build_apk.sh`, `bundletool-all-1.18.3.jar`, `tsconfig.json`, `eslint.config.js`, `.vscode/`, and `package.json.backup`.
- Stale output from an earlier, discarded Kotlin attempt sits in the untracked `app/build/` and `build/`, plus `.kotlin/`. Delete it in Phase 0.

---

## 1. Behavior decisions: RN code that never ran on Android

Reading `react-native-webview@13.15.0` showed that several props in `BrowserWebView.tsx` are **not part of its API**, so they were silently ignored. The code shows what you meant to build. The port **implements the intent by default**; the "today" column is the fallback if you want strict parity.

| # | Feature (legacy ref) | What the RN app actually does today | Port default |
|---|---|---|---|
| D1 | `onReceivedSslError` + "Proceed to host (unsafe)" (`BrowserWebView.tsx`) | RNWV always `handler.cancel()`s and shows the error page. "Proceed" adds the host to `ignoredHosts` but the next load fails again, so the bypass never works. | Real bypass: `handler.proceed()` when the host is in `ignoredHosts` or pending. |
| D2 | `onCreateWindow` → new tab (`target=_blank`, `window.open`) | RNWV creates a bare WebView with no client, so the URL goes to the **system browser**. If mi is the default browser, it comes back as a deep link and opens a new tab. | Open in a new mi tab (`addNewTab(url)`). |
| D3 | `onFullScreen` → unlock rotation, hide UI (`index.tsx:1636`) | RNWV's own `onShowCustomView` goes fullscreen, but orientation stays portrait and `isFullscreen` is never set. | Fullscreen custom view + unlock orientation; relock portrait on exit; hide pill/status bar while fullscreen. |
| D4 | "Enable Cookies" toggle (`sharedCookiesEnabled`) | iOS-only prop, so the toggle has **no effect on Android**. | `CookieManager.setAcceptCookie(!blockCookies)` + `setAcceptThirdPartyCookies(webView, !blockCookies)`. |
| D5 | Print (submenu) | Injects JS that posts `PRINT_HTML`; the handler was removed in the security audit, so Print does nothing. | `PrintManager.print(title, webView.createPrintDocumentAdapter(title))`. Native and safe. |
| D6 | `onScrollEndDrag` / `onMomentumScrollEnd` snap | Not RNWV props, so they never fired. | Drop (matches today). |
| D7 | `contentInset {bottom: pillHeight+20}` | iOS-only, so on Android the page content runs under the pill. | Keep (no inset), matching today. |
| D8 | `onPermissionRequest` → grant all | Not a prop. RNWV's built-in handler requests the CAMERA/RECORD_AUDIO runtime permission, then grants. | Same as RNWV's built-in handler (runtime permission, then grant). |
| D9 | Reload button shows ✕ while loading | Tapping ✕ calls `reload()`, not stop. | ✕ calls `stopLoading()`. |
| D10 | Deep links `mibrowser://…` | `parseDeepLinkUrl` only knows `mi://` and `mi-browser://`; the registered scheme is `mibrowser`. Runtime deep links call `addNewTab(rawUrl)` without parsing. | One parser for cold and warm starts that accepts `mibrowser://`, `mi://`, `mi-browser://` (`<scheme>://https://x` and `<scheme>://open?url=`/`href=`) plus raw http(s). |
| D11 | External schemes (`mailto:`, `tel:`, `market:`) via `Linking.canOpenURL` | On Android 11+ without `<queries>`, `canOpenURL` returns false, so these links often do nothing. | `startActivity(Intent(ACTION_VIEW, uri))` in try/catch; on failure show the "Link Error" alert. |
| D12 | Import settings clears `ignoredHosts` | `setAllSettings` sets `ignoredHosts` from `settings.ignoredHosts` (absent), so the state becomes `[]`, and the save effect then overwrites the restored secure value. | Import restores both settings and `ignoredHosts`. |
| D13 | Long-press context menu via injected `oncontextmenu` JS + `ReactNativeWebView.postMessage` | Works. | Native: `WebView.setOnLongClickListener` + `hitTestResult` (`SRC_ANCHOR_TYPE`, `IMAGE_TYPE`, `SRC_IMAGE_ANCHOR_TYPE`) + `requestFocusNodeHref` for link text. Same menu, **no JS bridge at all**, which is a security improvement. |
| D14 | Find in page via injected `window.find` | Works: highlights one match by selection. | Native `findAllAsync` / `findNext(forward)` / `clearMatches`. No JS injection; highlights all matches. |
| D15 | "Copy to Address Bar" | Sets the text and `isInputFocused=true` without real focus, so the keyboard does not open. | Really focus the field (keyboard opens). |
| D16 | Favorite-delete confirm modal (`confirmActionType "deleteFavorite"`) | `onRequestDeleteFavorite` is never called by `RecentSearchesView`, so the modal is unreachable. Dragging a favorite off deletes it with no confirm. | Keep drag-off delete with no confirm; drop the dead modal branch. |
| D17 | `recallPosition` setting | Persisted and in exports, but has no UI and no effect. | Keep in the data model (export compatibility); no UI. |

Change any default here before Phase 2 starts. Everything else in this plan is strict parity.

---

## 2. Stack and architecture

Keep it small: one Gradle module, one Activity, one ViewModel, plain repositories.

- **Gradle**: AGP + Kotlin 2.x + Compose compiler plugin, version catalog. `minSdk 26`, `targetSdk/compileSdk 36`. `applicationId com.jlees.mibrowser`, `versionName 1.0.0`.
- **UI**: Jetpack Compose (BOM) + `material3`, used only for primitives such as `Switch` and text fields. Every visual is custom-styled to match the RN look; nothing should look stock Material.
- **Dependencies** (each one replaces a whole RN package):
  - `androidx.activity:activity-compose`, `lifecycle-viewmodel-compose`, `core-splashscreen`
  - `kotlinx-serialization-json`: persistence and `.midata`
  - `io.coil-kt.coil3:coil-compose` (+ network): favicons, tab previews, background image
  - `androidx.camera:camera-camera2 / lifecycle / view`: live QR scanning
  - `com.google.zxing:core`: QR decode (live and from image) **and** QR generation, one library for all three
  - `com.vanniktech:android-image-cropper`: background crop, the same cropper expo-image-picker uses, so the UX is identical
- **Built-ins replacing RN libraries**: `android.webkit.WebView` (react-native-webview), DownloadManager, PrintManager, `Intent.ACTION_SEND` (Share), `ShortcutManager`/`shortcuts.xml` (expo-quick-actions), `LocationManager` + `Geocoder` (expo-location), Photo Picker `PickVisualMedia` (expo-image-picker), `MediaStore` (expo-media-library), `CreateDocument`/`OpenDocument` (SAF), `java.util.zip` (jszip), Android Keystore AES-GCM (expo-secure-store), `HttpURLConnection` + `org.json` (fetch for Open-Meteo, image download).

### Code layout (`app/src/main/java/com/jlees/mibrowser/`)
```
MainActivity.kt          edge-to-edge, splash, intents (VIEW / shortcut), back dispatch, fullscreen host, orientation
BrowserViewModel.kt      composes repos; UI state (activeView, isSearchActive, input text, find, dialogs…)
data/  Models.kt  JsonStore.kt  SecureStore.kt  SettingsRepo.kt  TabsRepo.kt  HistoryRepo.kt  BookmarksRepo.kt  FavoritesRepo.kt  DataTransfer.kt
logic/ Url.kt (input→URL, host, favicon, deep link, intent parse, search query, history title)  TabHistory.kt  Theme.kt  HistoryGrouping.kt
web/   WebViewHost.kt (pool tabId→WebView, settings, clients)  ErrorPage.kt  Downloads.kt
ui/    App.kt  Pill.kt  SubMenu.kt  RecentDrawer.kt  OverlaySheet.kt  FindBar.kt  ContextMenu.kt  Dialogs.kt (CustomAlert, Confirm, Rename)
       tabs/ bookmarks/ history/ settings/ home/ qr/
       common/ SortableSlots.kt  SwipeRevealRow.kt  Icons.kt  Tokens.kt
```
No interfaces, DI framework, or navigation library. There is one screen with overlays (as in RN: `app/index.tsx` is the whole app).

### State model
- `SettingsRepo`, `TabsRepo`, etc. each hold a `MutableStateFlow` and save with a debounce, matching the RN debounces: tabs 500 ms, bookmarks 500 ms, favorites 500 ms, history 1000 ms, settings immediate.
- **WebViews live outside Compose state**: `WebViewHost` keeps a `Map<tabId, WebView>` owned by the Activity (WebView needs an Activity context for fullscreen, file chooser and dialogs). The Activity declares `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"` so it is never recreated and WebViews survive rotation during fullscreen video.
- Parity with RN rendering: a WebView is created the first time a tab has `hasLoadedOnce=true` (all tabs at startup when Background Refresh is on). Inactive WebViews stay alive and are **not** paused. RN kept them mounted offscreen, so audio keeps playing. Only the active one is attached to the view tree.

---

## 3. Data model and persistence

Port `legacy/src/types.ts` + hooks to `@Serializable` data classes. Files live in `filesDir/store/<key>.json` under the same keys as AsyncStorage.

| Key | Content | Notes |
|---|---|---|
| `settings` | all settings below except `ignoredHosts` | |
| `ignoredHosts` | `List<String>` | **Encrypted** (Keystore AES-GCM), replacing SecureStore |
| `tabs` | `[{id,title,historyStack,currentIndex,desktopMode?,readerMode?,previewImage?,isCustomTitle?}]` | runtime fields (url, loading, canGoBack…) are never saved |
| `activeTabId` | string | |
| `history` | `[{url,title,timestamp}]` | ids regenerated on load (`generateUniqueId`) |
| `recent_searches` | same shape; `title` = the query | |
| `bookmarks` | `[{id,type:"bookmark"\|"folder",title,url?,parentId,order,icon?}]` | |
| `favorites` | `[{id,url,title,icon?}]` | max 5 |

**Settings and defaults** (from `useBrowserSettings.ts`; reset/import use the same defaults):
`themeMode "dark"` (light/dark/adaptive) · `accentColor "#007AFF"` · `searchEngineIndex 0` (clamped to range on load) · `cornerRadius 22` (0/10/22) · `uiPadding "normal"` (compact/normal/airy) · `fontScale 1.0` (0.8–1.2) · `fontWeight "normal"` (light/normal/bold) · `showStatusBar true` · `pillHeight 70` (60/70/80) · `progressBarMode "ltr"` (ltr/center/none) · `recallPosition "center"` · `startupTabMode "new"` (new/last) · `tabViewMode "rows"` (rows/cards) · `showTabLogo true` · `showTabPreview true` · `expandMenus false` · `showBookmarkIcons true` · `desktopMode false` · `forceSearchMode false` · `readerModeEnabled false` · `recentSearchesExpanded false` · `showFavoritesDefault false` · `jsEnabled true` · `httpsOnly false` · `blockCookies false` · `historyLoadCount 10` (10/25/50/100) · `historyGrouping "Time"` (Time/Site) · `backgroundRefresh false` · `menuBarOrder [tabs,bookmarks,history,settings,menu]` (on load: append missing ids, drop unknowns, reset if corrupt) · `homeClockType "None"` (None/12h/24h) · `homeDateType "None"` (None/Above/Below) · `homeWeatherType "None"` (None/Simple/Detailed/Hourly) · `homeLogoType "Fidget"` (None/Static/Fidget) · `homeBackgroundImage null` · `showHomeShortcuts false` · `homeShortcutAction "qr"` (newTab/qr/bookmarks/history) · `homeBackgroundOverlayOpacity 0.2`.

Transient UI state that lives in the settings hook in RN but is not persisted: `isAccentExpanded`, `isSearchEngineOpen`, `isShortcutMenuOpen`, `isClearHistoryOpen`. `isSearchEngineOpen` and `isClearHistoryOpen` are reset whenever an overlay opens.

**Migrating from the RN install** (per the "no legacy compat" preference, no automatic migration):
- Keep the `.midata` format **byte-compatible** (zip with `data.json` = `{settings, ignoredHosts, backgroundImageName}` + `background.<ext>`). Users can then Export in 0.10.3 and Import in 1.0. This costs nothing.
- Bookmarks, history and tabs are not carried over. A one-time reader of RN's `RKStorage` SQLite would be about 40 lines if you want them.
- Updating in place over the 0.10.3 APK needs the same signing key: `eas credentials -p android` → download keystore (Phase 10).

---

## 4. Pure logic (port verbatim + one test file)

All of these go in `logic/` with a single `LogicTest.kt` (JUnit), because they are where silent behavior drift would happen.

1. **Input → URL** (`handleGoPress` / `handleScanResult`, which are identical; dedupe into one function):
   - trim; empty → no-op.
   - `forceSearchMode` → `engine.url + encode(text)`.
   - `^https?://` (case-insensitive) → as-is; if `httpsOnly` and it starts with `http://` → upgrade to https.
   - else, if there is no space and it matches domain `^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(/.*)?$`, IP `^(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(/.*)?$` or `^localhost(?::[0-9]{1,5})?(/.*)?$`: localhost/IP → `http://`, domain → `https://`.
   - else search. The tab title is set to the raw text.
   - Use `URLEncoder` with `+`→`%20` to match `encodeURIComponent`.
2. **Search engines** (`constants.ts`): Google `https://www.google.com/search?q=`, Bing `https://www.bing.com/search?q=`, DuckDuckGo `https://duckduckgo.com/?q=`, Brave `https://search.brave.com/search?q=`, Ecosia `https://www.ecosia.org/search?q=`, Yahoo `https://search.yahoo.com/search?p=`. Icons: logo-google, grid-sharp, logo-tux, shield-half-outline, leaf, logo-yahoo.
3. **Search-query extraction / history title** (`utils.ts getSearchQueryFromUrl`, `getHistoryTitle`): host contains the engine host minus `www.` → param `q` or `p`. Title = `"<Engine> Search: <q>"`; else page title if non-empty and not containing `://`; else host.
4. **Per-tab history stack**: port `handleTabUpdate` (`index.tsx:244-331`) and `navigateTab` (`index.tsx:401-456`) exactly, including the `normalize` (strip scheme, `www.`, trailing `/`, `/index.html`, lowercase) and the `canGoForward` redirect case. Test: new nav truncates forward history; back/forward detection; redirect on the current entry; duplicate tip.
5. **History add** (`useHistory.addToHistory`): skip blank/`about:blank`; skip if newest url is identical; remove entries equal ignoring a trailing `/`; prepend; cap 1000. If it is a search, recent searches are deduped by query, prepended and capped at 20. `deleteHistory(-1)` clears both; otherwise remove items with `timestamp >= now-ms` from both.
6. **History grouping** (`groupHistoryByDate` Today/Yesterday/Last 7 Days/Older, `groupHistoryBySite` sorted by host, "Unknown" fallback) and **smart date** (today `hh:mm`; `Yesterday, hh:mm`; `MMM d, hh:mm`, locale-formatted).
7. **Adaptive theme** (`generateAdaptiveTheme`): exact mix math (bg base 10 @0.15, surface 25 @0.2, card 45 @0.2, glass 30 @0.15, inputBg 50 @0.15, `floor`), luminance rule, fixed text `#eaeaea`/`#aaaaaa`, fallback palette. Light/dark palettes from `constants.ts COLORS`.
8. **Deep link parse** (D10) and **intent URL handling** (`navigationUtils.ts`):
   - try `scheme=` + path → `scheme://path`; else raw intent; else `browser_fallback_url` (decode; must be http/https or "Invalid Fallback URL" alert) loaded in the current tab; else `package=` → "App Required … View Store?" alert → `market://details?id=`; else "Action Failed" alert.
   - On Android, prefer `Intent.parseUri(url, URI_INTENT_SCHEME)` for the first attempt, keeping the same fallback chain and the same messages.
9. **Bookmarks ops** (`useBookmarks.ts`): add (order = max sibling + 1, favicon icon), addFolder, delete (recursive), update (recompute icon when the url changes), move (append to the end of the new parent), reorder within a parent (renumber orders).
10. **Favicon URL**: `https://www.google.com/s2/favicons?domain=<host>&sz=128` (prefix `https://` if there is no scheme).
11. **Unique id**: `base36(now) + random base36(9)`. Tab ids are `now.toString()`, as in RN.

---

## 5. Animation and gesture spec

### RN spring → Compose `spring(dampingRatio, stiffness)`
RN `Animated.spring({tension, friction})` uses Origami conversion: `stiffness = (tension-30)*3.62+194`, `damping = (friction-8)*3+25`, mass 1, `ratio = damping / (2√stiffness)`.

| RN config | Used by | stiffness | dampingRatio |
|---|---|---|---|
| tension 60, friction 9 | pill swap, sheet open, snapToSearch, focus-expand | 302.6 | 0.805 |
| tension 50, friction 8 | sheet drag release | 266.4 | 0.766 |
| tension 50, friction 12, overshootClamping | recent drawer | 266.4 | 1.13 (critically damped, clamp) |
| tension 80, friction 6 | fidget logo return | 375 | 0.49 |
| default (tension 40, friction 7) | horizontal drag reset, recall spring-back, logo grab, history row reset, pill reset | 230.2 | 0.725 |
| friction 8 (tension 40) | Find bar slide-in | 230.2 | 0.824 |
| Reanimated `damping 50, stiffness 500`, clamp | sortable lists / grids | 500 | 1.118 |

`Animated.timing` durations are `tween(ms, FastOutSlowInEasing)` (RN's default easing is ease-in-out). LayoutAnimation `easeInEaseOut` maps to `Modifier.animateItem()` / `animateContentSize()` / `AnimatedVisibility(fade+expand)`.

**Units**: RN gesture `dx/dy` are dp and `vx/vy` are **dp/ms**. In Compose, convert drag px→dp, and thresholds `vy 0.5` → `500 dp/s`, `vy 1` → `1000 dp/s`.

### Gesture inventory
| Gesture | Where | Rule (legacy ref) |
|---|---|---|
| Pill pan claim | search/menu pill | claim when \|dx\|>10 or \|dy\|>10; disabled while input is focused (`index.tsx:1168`) |
| Swipe right on search pill | back | release \|dx\|>50 and \|dx\|>\|dy\| → `goBack()` if `canGoBack && currentIndex>0`, else load `historyStack[i-1]` if `i>0`. Then reset drag, `showBar()` |
| Swipe left on search pill | forward | mirror, using `canGoForward` / `historyStack[i+1]` |
| Horizontal drag feedback | search pill | back arrow (left 20) opacity dx 0→50 → 0→1; forward arrow (right 20) −50→0 → 1→0; input content opacity −50/0/50 → 0/1/0. Arrows render only if back/forward is possible |
| Swipe up on search pill | → menu pill | live `animVal = dy` (dy<0); release dy<−30 → spring to −80 (`SWAP_DISTANCE`), `isSearchActive=false`; else spring to 0 |
| Swipe down on search pill | hide pill | live translateY `clamp(dy,0,110)`; release vy>0.5 or translate>55 → hide, else show |
| Swipe down on menu pill | → search pill | live `animVal = −80+dy`; release dy>30 or vy>0.5 → spring 0; else spring −80 |
| Recall handle | when hidden | tap → show; drag up live `110+dy` (≥0); release dy<−20 or vy<−0.5 → show, else spring back to 110 |
| Page scroll hide/show | WebView `onScrollChange` | ignore while input focused, overlay open, or y<0; ignore the first event after tab switch / load start; \|Δy\|>1: down & visible → hide, up & hidden → show (100 ms timing) |
| Overlay sheet drag | sheet header | claim \|dy\|>5; height = start−dy (≤ full). Release: final>(full+default)/2 → full; >default×0.7 → default; else close. vy<−1 → full; vy>1 → default if above default else close |
| Recent drawer drag | 40×4 handle on focused pill | height = start−dy clamped [min, max]. Release dy<−60 or vy<−0.5 → max; dy>60 or vy>0.5 → min; else nearest (>max/2 → max) |
| Favorites reorder | favorites bar | long-press 150 ms, horizontal slots 60 dp, scale 1.1. On release, if pointer y < screenH−150 → **remove favorite** |
| Menu icon reorder | Settings › Pill | same horizontal sortable, 48+2 dp slots |
| Tab / bookmark reorder | Tabs and Bookmarks lists | long-press 200 ms; grid (cards, 2 cols) or list; scale 1.02 + shadow while dragging; disabled while searching |
| Swipe-reveal row | tab rows, bookmark rows | swipe left reveals [pencil (textSec bg)] [trash (#FF3B30)] buttons (RNGH `Swipeable`). Use `AnchoredDraggable` with anchors 0 / −actionsWidth |
| Swipe-to-delete row | history rows | claim dx<−10 and \|dx\|>\|dy\|; live translateX; release past −30% of width → slide to −W, height→0, opacity→0 (200 ms) then delete; else spring 0. Trash icon behind scales over translateX −100/−50/0 → 1.2/0.5/0 |
| Fidget logo | home page | grab: scale spring → 1.2; drag follows finger; release: spring position to 0,0 and scale to 1 (tension 80/friction 6) |
| Long-press link/image | WebView | context menu (D13) |

### Interpolation inventory (pill and overlays)
- **Search pill**: translateY = animVal; opacity over animVal [−80,−40,0] → [0,0,1]. Hit-testing only when `isSearchActive`.
- **Menu pill**: scale [−80,0] → [1,0.9]; opacity [−80,−10,0] → [1,0,0]. Hit-testing only when not search-active.
- **Hide**: container translateY = scrollTranslateY (0..110); scale [0,110] → [1,0.6]; opacity [0,82.5,110] → [1,0.5,0]. Recall opacity [0,90,110] → [0,0,1]. Hit-testing: recall only when hidden, pill only when shown.
- **Keyboard (pill focused only)**: `k = imeHeight × focusAnim` (focusAnim 0→1 over 200 ms on focus). Bottom corners: pillRadius → 0 over k 0..100; shadow 0.2→0 over 0..100; elevation 10→0 over 0..10; container horizontal padding 10→0; bottom padding `max(navInset+10,10)` → 0, plus k. The pill ends up docked flat on the keyboard at full width.
- **Pill radius**: `cornerRadius >= 20 ? pillHeight/2 : cornerRadius*2`. Input: height `pillHeight*0.7`, radius `cornerRadius*1.5`.
- **Focused pill**: when focused and the keyboard is visible, the pill grows by 25 dp (200 ms), with the content padded down by the same amount and the drag handle shown.
- **Progress**: load start → 0, then tween to 0.1 (300 ms). Progress events tween to p (200 ms). End → 1 (200 ms), then reset to 0 after 200 ms. `ltr` = width fraction, `center` = scaleX from center, `none` = hidden. Accent at 0.2 alpha behind the text, shown only while loading. On tab switch: 0.2 if loading else 0.
- **Overlay FABs** (tabs/bookmarks): bottom = sheetHeight mapped [0, default] → [−100, 20] (they rise with the sheet). Hidden and non-interactive while the keyboard is up.
- **Delete animations** (tab row, tab card, bookmark row): scale 1→0 with opacity [0,0.3,1] → [0,1,1] over 150 ms, then delete.
- **Accent swatch selected**: 3 dp border (text color) + scale 0.9.

### Keyboard handling
Use `enableEdgeToEdge()` + `WindowInsets.ime`; Compose animates IME insets natively, which replaces RN's 150 ms `keyboardHeight` tween. Pad the WebView container, overlay sheet content and Find bar by the IME height, as RN does. `windowSoftInputMode="adjustResize"`.

---

## 6. Feature inventory: everything to build

Each bullet is an acceptance item. Legacy refs are under `legacy/`.

### 6.1 App shell (`app/_layout.tsx`, `app/index.tsx`, `app.json`)
- [ ] App name "mi", package `com.jlees.mibrowser`, portrait lock, launcher icon (`assets/images/icon.png`), adaptive icon (`adaptive-icon.png` foreground, `#000000` background).
- [ ] Splash: black background, `splash.png` centered (SplashScreen API). Keep it up until settings, tabs and fonts are ready; RN shows a large spinner in the accent color (`#007AFF` before load), so show the same if needed.
- [ ] Nunito 400/600/700/800 bundled in `res/font`. The font-weight setting remaps the slots (`useBrowserSettings.effectiveTheme`):
  - normal: regular 400, semibold 600, bold 700, extrabold 800
  - bold: 600/700/800/800
  - light: 400/400/600/700
- [ ] Ionicons as vector drawables. Write a script (`tools/ionicons.sh`) that converts the needed SVGs from the `ionicons` npm package (MIT) with `vd-tool`. Needed set: add, add-circle-outline, alert-circle-outline, apps-outline, arrow-back, arrow-forward, arrow-up, barbell-outline, barcode-outline, battery-charging-outline, book-outline, bookmark-outline, bookmarks-outline, cafe-outline, checkmark, chevron-back, chevron-down, chevron-forward, chevron-up, close, close-circle, cloud-download-outline, cloud-outline, cloud-upload-outline, cloudy, code-slash-outline, color-palette-outline, copy-outline, desktop-outline, download-outline, ellipsis-vertical, expand-outline, eye-outline, filter, flash-outline, folder, folder-outline, globe-outline, grid-outline, grid-sharp, hand-left-outline, help-circle-outline, home-outline, hourglass-outline, image-outline, information-circle-outline, layers-outline, leaf, list-outline, lock-closed-outline, logo-google, logo-tux, logo-yahoo, menu-outline, open-outline, partly-sunny, pencil, phone-portrait-outline, power-outline, print-outline, qr-code-outline, rainy, refresh, refresh-outline, remove-circle-outline, reorder-four-outline, resize-outline, save-outline, scan-outline, search, search-outline, settings-outline, shapes-outline, share-social-outline, shield-half-outline, snow, star-outline, sunny, text-outline, thunderstorm, time-outline, trash, trash-outline, unlink-outline, warning-outline, water, wifi-outline. (Grep `legacy/` for `name="` and `icon:` before finalizing.)
- [ ] Status bar: transparent, light/dark icons per `theme.isDark`, hidden when `showStatusBar=false`. When shown, content is padded by the status-bar height. Both bars are hidden during fullscreen video.
- [ ] Root background = `theme.bg`.
- [ ] Manifest permissions: INTERNET, CAMERA, RECORD_AUDIO, ACCESS_FINE/COARSE_LOCATION, MODIFY_AUDIO_SETTINGS, WRITE_EXTERNAL_STORAGE (maxSdk 28, for image/QR/download saves on old devices). `usesCleartextTraffic=true`.
- [ ] Intent filters: VIEW + BROWSABLE + DEFAULT for `http`, `https`, `mibrowser`. mi can be chosen as the default browser.
- [ ] App shortcut "Scan QR Code" / subtitle "Open camera to scan" → opens the QR scanner (`index.tsx:182-201`). A static `shortcuts.xml` is enough.
- [ ] Deep links: cold start → new tab at the front, titled "External Link", active. Warm start (`onNewIntent`) → `addNewTab(url)` and close any overlay (D10).
- [ ] Unmatched-route screen (`app/[...unmatched].tsx`) is an Expo Router artifact: **drop it**.

### 6.2 Startup tab logic (`useTabs.ts loadTabs`)
- [ ] Restore saved tabs: url = `historyStack[currentIndex]` (index defaults to the last entry); `hasLoadedOnce = backgroundRefresh`.
- [ ] Deep-link URL present → new "External Link" tab first, active.
- [ ] Else `startupTabMode=="last"` and tabs exist → activate the saved `activeTabId` (fallback: first tab with a url, else the first tab) and mark it loaded.
- [ ] Else ("new") → reuse an existing blank tab if one exists, otherwise prepend a fresh "New Tab".
- [ ] After load, delete orphaned `preview_*.png` files in the cache that no tab references.
- [ ] The active tab is always marked `hasLoadedOnce` on activation (lazy load of background tabs).

### 6.3 WebView (`BrowserWebView.tsx`) — one configured WebView per tab
- [ ] Settings: JS = `jsEnabled`; domStorage, database, cacheMode default; saveFormData on; geolocation on; `mediaPlaybackRequiresUserGesture=false`; `javaScriptCanOpenWindowsAutomatically=true`; `setSupportMultipleWindows(true)`; builtInZoomControls on, displayZoomControls off; allowFile/ContentAccess off; `MIXED_CONTENT_NEVER_ALLOW`; overScroll never; background white; container bg `theme.bg` (black when fullscreen).
- [ ] User agent:
  - desktop (tab override `tab.desktopMode ?? settings.desktopMode`): `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36`
  - mobile: `Mozilla/5.0 (Linux; Android {Build.VERSION.RELEASE}; {Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36`
- [ ] Reader mode (`tab.readerMode ?? settings.readerModeEnabled`): inject the exact CSS from `BrowserWebView.tsx` (body max-width 800, padding 20, 18px/1.6, #333 on #fff, images responsive, hide nav/header/footer/.ad/.advertisement/.sidebar) in `onPageFinished`.
- [ ] Cookies per D4.
- [ ] `shouldOverrideUrlLoading` (main and sub frames), in order:
  1. `blob:`/`data:` → allow
  2. `mibrowser:` → external handler
  3. `httpsOnly` and `http://` and the host is not an IPv4 address/localhost → rewrite to https in the same tab (`url`, `requestedUrl`, loading)
  4. `googleapp://` → block
  5. http/https/about containing `intent://` or `#Intent;` → intent handler
  6. other http/https/about → allow
  7. anything else → external handler (§4.8 / D11)
- [ ] Navigation state → tab update: ignore `intent://`, `android-app://`, `about:blank`. Clear error overlays when a load finishes. Title rule: page title if non-empty and not containing `://`, else host, else "New Tab"; do not overwrite when `isCustomTitle`. Run the TabHistory update (§4.4). For the active tab: update back/forward/loading, **add to history when not loading**, and update the input text (host) **only if the input is not focused**.
  - Hook points: `doUpdateVisitedHistory`, `onPageStarted`/`onPageFinished`, `onReceivedTitle`, `onProgressChanged`.
- [ ] Only the active tab drives progress, history and input. Inactive tabs update their own tab record only. (RN parity: inactive tabs never write history.)
- [ ] Error overlay (`renderError`) on main-frame `onReceivedError`, plus SSL (D1). Mapping by description substring / code:
  - `ERR_NAME_NOT_RESOLVED` / −2 → "Site Not Found" / "We couldn't find this site. Please check your spelling." (search-outline)
  - `ERR_INTERNET_DISCONNECTED` → "No Internet" / "Please check your Wi-Fi or mobile data connection." (wifi-outline)
  - `ERR_CONNECTION_TIMED_OUT` → "Connection Timed Out" / "The server took too long to respond." (time-outline)
  - `ERR_CONNECTION_REFUSED` → "Connection Refused" / "The website refused the connection." (hand-left-outline)
  - `ERR_CLEARTEXT_NOT_PERMITTED` → "Security Error" / "This site requires a secure HTTPS connection." (lock-closed-outline)
  - `ERR_CERT`/`ERR_SSL`/`ssl_error`/−11/SSL → "Security Warning" / "The connection to this site is not secure." (warning-outline)
  - default → "Can't Load Page" / "Something went wrong while loading this website." (alert-circle-outline)
  - Layout: 64 dp icon (textSec), title extrabold 20, URL in accent bold 14 (one line, 85% width), description, **Try Again** (accent, radius 12) → clear and reload, **Upgrade to HTTPS** (card bg, only for `http://`), **Proceed to {host} (unsafe)** (underlined, SSL/cleartext only) → add the host to `ignoredHosts` (+ pending) and reload.
- [ ] Fullscreen video per D3.
- [ ] Permissions per D8. Geolocation prompt → request ACCESS_FINE_LOCATION, then `callback.invoke(origin, granted, false)`.
- [ ] File upload `onShowFileChooser`: content chooser with accept types and multiple, plus camera image/video capture when `isCaptureEnabled` (RNWV parity).
- [ ] Downloads (`setDownloadListener`, RNWV built-in): DownloadManager to Downloads with `guessFileName` (invalid chars → `_`), Cookie + User-Agent headers, notification on completion, toast "Downloading <file>".
- [ ] New windows per D2.
- [ ] While the pill input is focused, a transparent layer over the WebView swallows touches, and touching the page dismisses the keyboard (`blockGestures`, `onTouchStart`).

### 6.4 Pill (`index.tsx:1867-2656`)
- [ ] Two stacked pills + all gestures and interpolations from §5.
- [ ] **Menu pill**: items in `menuBarOrder`. tabs (copy-outline, "Tabs"; **captures the active tab preview first**), bookmarks (bookmarks-outline), history (time-outline), settings (settings-outline), menu (menu-outline; toggles the submenu). Icon 24, label 10sp bold, `space-around`.
- [ ] **Search pill input**: placeholder "Search"; URL keyboard, IME action Go, no autocap, select-all on focus. Focus → show the full `activeUrl`; blur → show the host. Clear button (close-circle) only when focused and non-empty, and it re-focuses. Engine icon toggles `forceSearchMode` (accent when on, textSec when off). Trailing: focused → search icon (accent) = Go; unfocused → refresh / ✕-while-loading (D9), disabled at 0.3 alpha when there is no URL.
- [ ] Submit → §4.1 → `navigateTab(url, rawText)` → reload if same URL → `snapToSearch`.
- [ ] **Submenu** (240 wide, right-aligned, `pillHeight+4` above the pill, `cornerRadius`, elevation 20, rows divided by `theme.bg`):
  - Home → `goHome` (tab url null, "New Tab", showLogo)
  - Bookmark → open Bookmarks with the add modal auto-opened
  - Share → system share of `activeUrl`, title "Share Link"
  - Find in Page
  - Scan QR Code
  - Generate QR Code
  - Print (D5)
  - Desktop Mode switch: per-tab toggle + reload
  - Reader Mode switch: per-tab toggle + reload
  - Items that need a URL are 0.5 alpha and disabled when there is no URL. A tap outside closes the submenu. It also closes when the search pill becomes active or an overlay opens.
- [ ] **Recall handle**: 50×30, radius 25, `inputBg`, chevron-up, centered `max(navInset+10,10)` from the bottom.
- [ ] `showBar` on load start and on back/forward.

### 6.5 Recent searches drawer + favorites (`RecentSearchesView.tsx`, `HorizontalSortableList.tsx`, `index.tsx:547-706, 2571-2650`)
- [ ] Drawer under the pill (above the keyboard), `theme.surface` background.
  - Height rules: min = 65 if `showFavoritesDefault` else 0. Max = screenH − (pillHeight+24) − ime − topInset − bottomInset.
  - On focus with the keyboard visible: animate to max if `recentSearchesExpanded`, else to 65 if favorites are pinned.
  - Collapse to 0 (200 ms) when focus or the keyboard is lost.
  - Drag rules in §5.
- [ ] List: recent searches filtered by input text (show all when the text equals `activeUrl` or its host; otherwise title contains, case-insensitive). Row: title + ✕ (remove). Tap fills the input with the query and keeps focus. Empty state: "No recent history" / "No matches found".
- [ ] Favorites bar (bottom, top border `theme.bg`): up to 5 favicons (36 dp round, globe fallback, 200 ms fade-in). Tap → navigate, blur, `snapToSearch`. "+" button when fewer than 5 adds the current page (title or host); duplicates are ignored. Long-press reorder; drag up and off to delete.
- [ ] Floating chevron-down button → collapse the drawer to min.

### 6.6 Overlay sheet (`OverlaySheet.tsx`, `index.tsx:713-906, 1686-1865`)
- [ ] Backdrop black 50%; tap closes. Sheet snaps: closed 0, default 0.6H, full 0.95H.
  - Open: spring to full if `expandMenus` else default.
  - Close: 250 ms tween, then `activeView=none`, `snapToSearch`, and the settings/tabs/history search texts are cleared.
  - Focusing any in-sheet search field springs the sheet to full.
- [ ] Header: `sheetHeader` bg, drag handle 40×5 (text at 30%), title extrabold 24×fontScale (History / Tabs / Bookmarks / Settings), close-circle in accent. Top corners `cornerRadius`. Content padded by IME height.
- [ ] Back button priority (`index.tsx:932`): Bookmarks' own handler first (modal/picker → search → folder) → overlay open: close → input focused: blur → native goBack if possible and index>0 (+showBar) → manual stack back → default (leave the app). Use one `OnBackPressedCallback` that asks the active Bookmarks view first.

### 6.7 Tabs (`TabsView.tsx`, `TabCard.tsx`, `SwipeableTabRow.tsx`, `SortableGrid.tsx`)
- [ ] Search header: "Search Tabs..." (filters title or url, case-insensitive). ⋮ menu → "Clear all tabs" → CustomAlert "Clear All Tabs" / "Are you sure you want to close all tabs?" [Cancel] [Clear All (destructive)]. Clear all deletes preview files and leaves one fresh tab.
- [ ] **Rows** mode: row height by spacing (compact 64, normal 74, airy 88) × fontScale; gap compact 8, normal 15, airy 25. Active row: `card` bg + 2 dp accent border. Favicon box (accent bg when active), globe fallback, title (or "New Tab") and host (or "Home"). Swipe-reveal rename/delete.
- [ ] **Cards** mode: 2 columns, width (W−60)/2, aspect 0.85. Preview image at 0.6 alpha under an overlay (dark `#000` 50% / light `#FFF` 70%), favicon box, ✕ close (top right), title (2 lines), host, small pencil (rename).
- [ ] Long-press reorder (§5), disabled while searching. Scroll-to-top button appears after 100 dp of scroll. New-tab FAB (accent, 56 dp, "+") → `addNewTab()` + close the overlay.
- [ ] Tap → activate the tab; input shows its host. Close → delete the preview file, then `deleteTab` (next active = previous index; the last tab is replaced by a fresh one; the keyboard is dismissed).
- [ ] **Previews**: on opening Tabs, if `showTabPreview`, draw the active WebView to a bitmap (half scale, PNG) at `cache/preview_<id>_<ts>.png`; set it, then delete the old file. If no tab has a URL, purge all previews. "Preview Content" setting row is visible only in cards mode.
- [ ] **Rename** modal "Edit Tab": text field focused after 300 ms, clear button, [Cancel] [Save (accent)]. Trimmed non-empty → custom title; empty → host or "New Tab" and the custom flag is cleared.
- [ ] `addNewTab` **prepends** and activates.

### 6.8 History (`HistoryView.tsx`, `SwipeableHistoryRow.tsx`)
- [ ] Search header "Search History..." (title/url). ⋮ menu → "Clear History" expander → ranges (Last Hour, Last 24 Hours, Last 7 Days, Last 4 Weeks, All Time) → confirm modal.
- [ ] Sections by Time or Site (site headers show a 16 dp favicon). Tap a header to collapse/expand (chevron); collapse is ignored while searching. Not sticky.
- [ ] Paging: show `historyLoadCount` items, load more near the end. If every visible section is collapsed and more items exist, auto-load more. Reset the count when the search changes.
- [ ] Row: time icon box, title (or "Untitled"), smart date + host, chevron. Height compact 48 / normal 58 / airy 72 × fontScale. Tap → navigate + close the overlay. Swipe-to-delete (§5).
- [ ] Empty: time-outline 50 dp + "No history found." Scroll-to-top button after 100 dp.

### 6.9 Bookmarks (`BookmarksView.tsx`, `BookmarkRow.tsx`)
- [ ] Root list of the current folder sorted by `order`. Search "Search Bookmarks..." matches title/url **across all folders**. The breadcrumb row (back arrow + `/Folder/Sub`, or "Search Results") appears when in a folder or searching.
- [ ] Row: 60×fontScale height, 10 dp gap. Folder icon (accent) + chevron, or favicon (when `showBookmarkIcons`) with globe fallback, or the first letter when icons are off. Title + url. Swipe-reveal edit/delete. Tap a folder → enter; tap a bookmark → navigate + close.
- [ ] Long-press reorder within the folder (disabled while searching).
- [ ] FABs (hidden while searching): scroll-to-top, new folder (folder-outline, card bg), add bookmark (accent "+").
- [ ] Modal "Add Bookmark" / "New Folder" / "Edit Item":
  - Title (auto-focus, clear button) and URL (bookmarks only, no autocap, clear button).
  - Location row (folder name or "Bookmarks") opens the picker: "Select Location" list of Root + all folders by full path ("A / B / C", max depth 10), sorted. When editing a folder, the folder and its descendants are excluded. A checkmark marks the selection.
  - Save: add / addFolder / update (+ move if the parent changed).
  - Defaults: add bookmark prefills the active title/url and the current folder; new folder is titled "New Folder".
- [ ] Auto-add: opening via Submenu › Bookmark opens the add modal immediately.
- [ ] Back handling: picker → modal → search → folder up → overlay.
- [ ] Note: the folder picker in RN is a `SortableGrid` with a no-op reorder (an accident). Use a plain list.

### 6.10 Settings (`SettingsView.tsx`)
- [ ] Top search "Search Settings..." (focus → sheet full). Clear button. While searching, show every group whose title or row labels match; a group-title match shows the whole group. Scroll resets to the top on category or search change.
- [ ] Home grid of categories (cards with `cornerRadius`, separators `theme.bg`):
  - [Colors, Interface, Pill]
  - [Tabs, History, Bookmarks]
  - [Browsing, Security, Home Page]
  - [App Data]
  - Icons: color-palette-outline, phone-portrait-outline, unlink-outline, copy-outline, time-outline, bookmarks-outline, search-outline, lock-closed-outline, home-outline, save-outline.
  - Inside a category: "‹ Back" in accent.
- [ ] Row widgets: segmented chips (selected = accent bg + white text, radius `cornerRadius`); switch (track off `#767577`, on accent, thumb `#f4f3f4`); stepper (−/+ circles, 4 dp accent bar, % label).
- [ ] **Colors**:
  - Theme [Light|Dark|Adaptive]
  - Accent Color: 18 swatches in a 6-wide grid (`constants.ts ACCENTS`); first row only until "Show More"/"Show Less", which animates; swatch 14% width, radius 10
- [ ] **Interface**:
  - Font Size (0.8–1.2 ±0.1, %)
  - Font Weight [Light|Normal|Bold]
  - Corners [Square 0|Semi 10|Round 22]
  - Spacing [Compact|Normal|Airy]
  - Status Bar (switch)
  - Expand Menus (switch)
- [ ] **Pill**:
  - Size [Thin 60|Normal 70|Tall 80]
  - Loading Bar [Standard|Center Out|Hidden]
  - Pin Favorites (switch)
  - Expand Searches (switch)
  - Reorder Icons (horizontal sortable of the 5 menu icons). RN hardcodes white icons, which is invisible in Light theme; use `theme.text`.
- [ ] **Home Page**:
  - Center Logo [None|Static|Fidget]
  - Clock [None|12h|24h]
  - Date [None|Above|Below]
  - Weather [None|Simple|Detailed|Hourly]
  - Shortcut (switch); when on, an "Action" dropdown [New Tab (add) | Scan (qr-code-outline) | Saved (bookmarks-outline) | Recent (time-outline)]
  - Background Image: "Select from Gallery" → photo picker → cropper locked to the **screen aspect ratio** → **copy into `filesDir`**. RN stored a cache URI that could vanish. Shows "Remove Image" (red) and a screen-aspect preview with the darkness overlay.
  - Darkness (0–1 ±0.1, %): only when an image is set
- [ ] **Tabs**:
  - Style [Rows|Cards]
  - Site Logo (switch)
  - Preview Content (switch, cards only)
  - Background Refresh (switch; turning it on opens a confirm: "Enabling background refresh will reload all open tabs immediately when the app starts. This may consume significant battery and data." [Enable])
- [ ] **History**:
  - Group By [Time|Site]
  - Load Count [10|25|50|100]
  - Clear History expander → ranges → confirm "This will permanently delete history for: <label>." [Delete]
- [ ] **Bookmarks**: Site Logo (switch)
- [ ] **Browsing**:
  - Search Engine dropdown (icon + name, checkmark on the selected one)
  - On Startup [New Tab|Last Tab]. Copy the exact chip labels from the legacy file.
- [ ] **Security**:
  - Enable JavaScript
  - Enable Cookies (inverted `blockCookies`)
  - HTTPS Only
- [ ] **App Data**:
  - Export Settings (cloud-upload-outline) → `.midata` via `CreateDocument("application/zip")`, name `settings_<ts>.midata`, then "Success" / "Data exported successfully!"
  - Import Settings (cloud-download-outline) → `OpenDocument`. The name must end with `.midata` or `.zip` ("Invalid File"); `data.json` must exist ("Invalid Data") and contain `settings` ("Structure mismatch."). Then confirm "Restore Settings?" / "This will replace your current settings and home page background. Continue?" [Cancel][Restore] → apply (D12) → "Success" / "Settings restored successfully."
  - Reset All Settings (red) → confirm "This will restore all app settings to their default values. Your history and tabs will be preserved." [Reset]. This also clears `ignoredHosts`.
  - Wipe All Data (red) → confirm "This will delete ALL data including history, bookmarks, tabs, and settings. The app will be reset to a fresh state." [Wipe App] → reset settings, clear history + recents + bookmarks + favorites, one fresh tab, delete all stores and cache files, close the overlay. Also clear WebView cookies, cache and storage, which is the intent of a wipe.
  - Use the in-app CustomAlert for all of these. RN used the native `Alert` for import/export; unify on CustomAlert.
- [ ] Footer: "Help" (help-circle-outline, accent 70%) → new tab `https://jleescy.github.io/mi-browser/user` + close. "mi browser v{versionName}" (11sp, 40% alpha), using `BuildConfig.VERSION_NAME`.

### 6.11 Home page (`HomePage.tsx`, `useWeather.ts`)
- [ ] Rendered for the active tab when its url is null/`about:blank`. Tapping anywhere dismisses the keyboard.
- [ ] Background image (cover) + black overlay at `homeBackgroundOverlayOpacity`.
- [ ] Top widgets (≈60 dp from the top, 15 dp gap):
  - Clock: bold 72×fontScale, `hh:mm`, 12h/24h, ticks every second while the clock or date is on.
  - Date: semibold 16, "Tuesday, November 3" (weekday long, month long, day), Above or Below the clock, or alone.
- [x] ~~Weather~~ — **removed in 1.0 by request.** (Original spec kept below for reference.) Weather (needs location permission; one fetch per app process, cached in memory; concurrent callers wait):
  - Open-Meteo `https://api.open-meteo.com/v1/forecast?latitude=…&longitude=…&current_weather=true&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum&hourly=temperature_2m,precipitation_probability,weathercode&temperature_unit=fahrenheit&windspeed_unit=mph&timezone=auto`
  - City via `Geocoder`: locality, else admin area, else "Your Location".
  - Hourly data sliced from the first hour ≥ now−1h, for 24 hours.
  - Simple: icon 32 + "{temp}°F" bold 24 + city.
  - Detailed / Hourly: "H: x°" / "L: y°" on the left; "filter-icon {wind} mph" / "water-icon {precipProb}%" on the right.
  - Hourly: 5 slots (every 3rd hour), "Now" first (extrabold, full alpha); others "3pm"/"12am" at 0.6 alpha; icon 18 + rounded temp.
  - States: spinner while loading; error text ("Permission denied" or the message).
  - Icon map: 0 sunny; 1–3 partly-sunny; 45–48 cloudy; 51–67 rainy; 71–77 snow; 80–82 rainy; 85–86 snow; ≥95 thunderstorm; else cloud-outline.
- [ ] Center logo "mi." extrabold 60×fontScale, letter-spacing −1, 0.9 alpha. Static, or Fidget (§5).
- [ ] Shortcut (bottom 100 dp): 60 dp circle (card bg, elevation 5) + icon + label. New Tab ("add"/"New Tab"), Scan ("qr-code-outline"/"Scan"), Saved ("bookmarks-outline"), Recent ("time-outline").
  - Actions: new tab / QR scanner / open bookmarks / open history.

### 6.12 Find in page (`FindOnPageBar.tsx`)
- [ ] A 60 dp bar at the bottom above the keyboard. Top corners `cornerRadius`, surface bg, elevation. Slides up (spring), then focuses.
- [ ] Field "Find in page..." (search icon, no autocorrect, IME search = next). Buttons: ▲ prev, ▼ next, ✕ close.
- [ ] Each keystroke searches from the start. Close clears matches and the text (D14).

### 6.13 Context menu (`index.tsx:2958-3169`)
- [ ] Dimmed backdrop (40%); card 70% width, max 280, `cornerRadius`.
- [ ] Header: link text, or "Image"/"Link" (2 lines, bold 14), then the URL (1 line, 11).
- [ ] Actions (icons 18×fontScale):
  - Open in New Tab: only for http/https
  - Copy to Address Bar (D15)
  - Share
  - Save Image: images only. http/https/data only; Pictures via MediaStore; extension from the url (.png/.gif/.webp, else .jpg); "Success" / "Image saved to gallery!" or "Save Error"
- [ ] Footer "Cancel" (card bg).

### 6.14 QR (`QRScannerView.tsx`, `QRGeneratorView.tsx`, `qrPolyfill.ts`)
- [ ] **Scanner** (full-screen, slides up):
  - No permission: "We need your permission to show the camera" + [Grant Permission] [Close].
  - Camera preview + overlay: close button (surface circle) left, "Scan QR Code" (white, bold, text shadow), gallery button right (spinner while processing), center marker square, footer "Align the QR code within the frame".
  - The first decode wins. Result → §4.1 (the input shows the raw data) → navigate → close.
  - Gallery: photo picker → ZXing on the bitmap (try normal and inverted / downscaled) → otherwise "No QR Code Found" / "Could not detect a QR code in this image."
- [ ] **Generator** (fade modal, max 320 wide): title "QR Code", ✕, QR of `activeUrl` (200 dp, black on white, quiet zone 10), URL caption (2 lines).
  - [Save]: Pictures via MediaStore, "Success" / "QR Code saved to gallery!"; on API ≤28 with permission denied, show the "Open Settings" alert copy from the legacy file.
  - [Share]: PNG via FileProvider + ACTION_SEND.
- [ ] Entry points: submenu, home shortcut, app shortcut.

### 6.15 Dialogs
- [ ] **CustomAlert** (`CustomAlert.tsx`): fade, black 50% scrim, card max 320, radius 20, padding 24, centered title (bold 18) and message (regular 14, line height 22). Buttons share a row: cancel = outline textSec; destructive = `#FF3B30`; default = accent; white text. Any button press also dismisses.
- [ ] **Confirm modal** (`index.tsx:2676`): "Are you sure?", the per-action message (above), [Cancel] [action (red `#ff3b30`)]. Width 80%, radius `cornerRadius`.
- [ ] Every alert title and message string from the legacy code is reproduced verbatim. Grep `showAlert(` and `Alert.alert(`.

---

## 7. Deliberately not ported (dead or iOS-only)
- `src/design-system/components/*` (Button, Card, IconButton, Separator) and the style factories in `styles.ts`: never imported. Port only `tokens.ts` values to `Tokens.kt` (spacing, typography sizes, icon sizes, shadows → elevation).
- `src/components/Settings/SettingsSection.tsx`: unused.
- iOS UA branch, `Platform.OS === 'ios'` paths, iOS Info.plist strings, `allowsBackForwardNavigationGestures`, `pullToRefreshEnabled`, `contentInset` (D7).
- The `console.log` dumps in the Tabs, History and Bookmarks views.
- `rn-qr-generator` / jsQR / jpeg-js / pngjs fallbacks: replaced by ZXing.
- The `PRINT_HTML` JS bridge and the `ReactNativeWebView` interface: no JS bridge at all (D5, D13, D14).
- The unreachable `deleteFavorite` confirm (D16); the unused `onClear` in RecentSearches.
- EAS/bundletool APK pipeline: replaced by `./gradlew assembleRelease`.

---

## 8. Build order
Each phase ends with a debug APK on the `mi_test` AVD (API 36) or a device.

| Phase | Scope | Done when |
|---|---|---|
| **0 Setup** | Move RN to `legacy/`; Gradle project; manifest, icons, splash, fonts, Ionicons script; `Tokens.kt`, `Theme.kt`; delete stale `app/build`, `build/`, `.kotlin/` | Black splash → empty themed screen; `./gradlew assembleDebug` is green |
| **1 Data + logic** | Models, JsonStore, SecureStore, repos with debounced saves, all of §4 + `LogicTest.kt` | `./gradlew test` is green |
| **2 Browser core** | WebViewHost (§6.3 minus fullscreen/downloads/uploads), tabs + startup logic (§6.2), search pill input + Go + progress + reload/stop, error page, back button, history recording | Browse, open/switch tabs (temporary debug button), back works, history persists across restart |
| **3 Pill complete** | Menu pill, all pill gestures, hide/recall, scroll-hide, keyboard docking, submenu (all items), find bar, context menu, share, print, desktop/reader | Every row of the §5 gesture table checked by hand |
| **4 Overlay + Tabs** | OverlaySheet with snaps, `SortableSlots`, `SwipeRevealRow`, Tabs rows/cards, previews, rename, clear all | Matches the RN app side by side |
| **5 History + recents** | History view, recent drawer, favorites bar with drag-off delete | " |
| **6 Bookmarks** | Folders, picker, modal, auto-add, back stack | " |
| **7 Settings** | All categories and search, import/export, reset, wipe, background crop | Every setting changes the UI live, survives restart, and round-trips through export/import (including a 0.10.3 `.midata`) |
| **8 Home** | Home page, clock/date, weather, fidget logo, shortcut | " |
| **9 Platform** | QR scan/generate, app shortcut, deep links (D10), external/intent links, downloads, file uploads, permissions, fullscreen (D3), SSL bypass (D1), cookies (D4) | Test pages: a `target=_blank` link, `mailto:`, `intent://` with fallback, `badssl.com` expired cert, a YouTube fullscreen video, a file download, a file upload, a WebRTC camera test |
| **10 Release** | R8 + resource shrink, EAS keystore → `keystore.properties` (gitignored), `versionName 1.0.0`, update README/docs developer guide, delete `legacy/` and the RN tooling (§0), update `AGENTS.md` / notes | Signed release APK installs **over** 0.10.3 |

### Parity checklist method
Keep the 0.10.3 APK installed on a second device (or a second AVD). For each §6 checkbox, perform the action on both and compare. Pay the most attention to the pill (spring feel, thresholds, keyboard docking), because that is the product.

---

## 9. Open questions (defaults are already chosen, so work does not block on these)
1. D1–D17 defaults: intent over strict parity for the plumbing that never fired. Change any before Phase 2.
2. Carry over bookmarks/history from 0.10.3 (≈40-line RKStorage reader)? Default: **no**; settings come over via `.midata`.
3. minSdk 26 (Android 8). RN 0.81 allowed 24. Default 26.
