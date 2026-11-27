# mi browser: code standard

Kotlin, Jetpack Compose (Material 3 Expressive) and Android WebView. Code is in `app/src/main/java/com/jlees/mibrowser/`.
Read this before adding or changing a feature. A new feature isn't done until it meets every rule here.

## 1. A feature is one package
Everything that exists only for one feature lives in that feature's package:
- its rules,
- its store,
- its screens,
- its hooks into the WebView.

Its settings are the exception: they live with the Settings section they appear in (§4). Deleting the package (plus
one line in BrowserViewModel and its rows in the Settings section files) should remove the feature.

| Package | Owns |
|---|---|
| `adblock/` | filter engine, content blocker (WebView hooks), scriptlets (ScriptletRules + assets/js/scriptlets.js, from uBlock Origin's lists), tracking-parameter cleaning |
| `search/` | engines, `!` shortcuts, suggestions, Pill highlighting of shortcuts, the Add search shortcut dialog |
| `incognito/` | WebView profiles, screen/keyboard protections, notification, toggle |
| `tabs/` | TabsStore, TabRules, per-tab back/forward stack, tab switcher and its search, the switcher Pill's layout (SwitcherLayout), tab groups (TabGroupRules; TabGroups: the switcher's chips and the group dialogs) |
| `containers/` | Profiles (called containers in the code, apart from WebView's own profiles): spaces shown by an icon and a colour, with their own tabs, tab groups, history and (if kept separate) cookies and storage. ContainerRules (a profile's tabs, which profile a new tab goes in, which have their own storage, storage to delete), ContainerProfiles (each one's WebView profile), ContainerActions (switching profile, moving a tab, making, changing and deleting profiles), ContainerScreens (the tab switcher's current profile and list of profiles, History's profile chips, the profile dialogs) |
| `library/` | HistoryStore, BookmarksStore (favorites are the bookmarks in its Favorites folder), their rules, bookmark file import/export (Netscape HTML), History and Bookmarks screens |
| `downloads/` | mi's downloader (engine, foreground service, notifications), DownloadsStore, the download prompt and other handlers, BlobDownloads (files a page makes itself, handed over by blob-download.js), Downloads page |
| `sites/` | Site settings page (one site's permissions, pop-ups, ad blocking, data), opened from the tab menu |
| `answers/` | inline answers in the Pill (math, unit conversions, number bases, time and date, coin and dice): the rules, the answer row |
| `follow/` | Following prices: FollowStore (follows, price history, the changes found), FollowRules (price parsing, product data, alerts), PriceChecker (quick fetch, else a hidden page load in its own profile), FollowJob (background checks via JobScheduler), FollowNotifier, FollowWidget (home-screen widget), the page hook (follow-detect.js), Library › Following, the follow and product sheets |
| `find/` | find in page: FindInPage state (query, match count) and the Pill's find content |
| `home/` | new-tab page and its favorites grid |
| `qr/` | scanner and generator |
| `media/` | background playback (page media script, media notification service), picture-in-picture |
| `launcher/` | launcher shortcuts, search widget, home-screen pins (opened as apps: AppWindow, a window and Recents card of their own), app mode for a tab (no Pill; a notification turns it off) |
| `extensions/` | the extension engine (WebExtensions on WebView): ExtensionRules (manifests, match patterns, API scan, the Works / Might Have Problems / Not Recommended rating), ExtensionPackage (.xpi), ExtensionsStore (installed extensions) + ExtensionStorage (chrome.storage, one value per file, read and written per key off the main thread), ExtensionEngine (where extension code runs, messages, events), ExtensionApi (the API calls), ExtensionPages (backgrounds, offscreen pages and popups in WebViews, serving files), ExtensionTabs (content scripts in tabs), ExtensionRequests + ExtensionNetRules (webRequest, declarativeNetRequest), ExtensionSitePolicies (sites whose security policy blocks extensions: their pages fetched with it relaxed), ExtensionAlarms (chrome.alarms), ExtensionScripting (scripts registered while running, privacy), ExtensionNavigation (webNavigation), ExtensionPlatform (cookies, sign-in, side panels, downloads and other browser APIs), ExtensionMenus (contextMenus), ExtensionInstaller (Firefox Add-ons: only extensions made for phones are offered or added), VerifiedExtensions (the hand-tested, recommended list: add one only after testing it on a phone), quirks/ (code for one particular extension: one file per extension, e.g. quirks/DarkReader.kt; never mixed into the engine), Library › Extensions (ExtensionsLibrary) and the other screens; page JS in assets/js/ext-*.js |
| `update/` | GitHub release checks and installs, What's New (the release notes shown after every update) |
| `onboarding/` | the Gesture Walkthrough (hands-on gesture tour, shown once to every user) |

Shared plumbing, used by several features, stays in the shell packages:
- `data/`: models, Settings, JsonStore/Saver, Favicons, backup;
- `logic/`: URL parsing, list helpers;
- `web/`: WebViewHost and one class per WebView job;
- `ui/`: App, Pill, sheets, dialogs, the tab menu, Settings (`ui/settings/`: the screen and every section, see §4), `common/` widgets.

A file in a shell package that serves only one feature is in the wrong place.

## 2. State lives in a store
- **A store** (`XStore`) owns a feature's state.
  - It exposes it as Compose state with `private set`.
  - It has `load()` (runs off the main thread at start) and `savers` (one `Saver` per file, debounced).
  - It changes state only through named methods (`add`, `delete`, `toggleFavorite`…).
  - The UI never assigns a store's state.
- **BrowserViewModel** holds the stores and coordinates across them only. Example: a page finished loading in tab X, so history adds it if X keeps traces. It also holds app-wide UI state (open sheet, dialogs, Pill text).
- **A feature action that needs several stores or the UI flows** is an extension function on `BrowserViewModel` in the feature's package (e.g. `fun BrowserViewModel.toggleAdsHere()`). It is not a new member of the ViewModel.

## 3. Decisions are pure functions
- Rules (which tab shows next, what gets saved, how history groups) are plain functions with no Android and no state, in `*Rules.kt` or the feature's logic file.
- Every rule and store gets a unit test (`app/src/test/...`: `StructureTest`, `StoresTest`, `LogicTest`, or a feature test file).
- Code that needs Android (a downloader, a service, a ContentResolver) is tested with Robolectric (`@RunWith(RobolectricTestRunner::class)`), against local fakes like the JDK's `HttpServer` (see `DownloaderTest`). A test for a bug must fail with the bug put back.
- Run `./gradlew :app:testDebugUnitTest` before calling anything done.

## 4. Settings
- Settings are organized the way the app shows them: **one file per section** in `ui/settings/`, named for it
  (`LookAndFeelSettings.kt`, `PillSettings.kt`, `LibrarySettings.kt`, `HistorySettings.kt`…), each with one
  `fun xSection(vm): SettingsSection`.
- **Every setting a section shows is written out in its file**, however long that makes it, along with the helper
  composables only it uses (a folder picker, a dialog). A section never pulls rows in from another file. A setting
  belongs to the section it appears in, whichever feature it controls (the search-suggestion switches are in
  `PillSettings.kt`; Downloads and Following are in `LibrarySettings.kt`, as both are on the Library page).
- Rows are built from `ui/settings/SettingsKit.kt` (`SettingsRow`, `Item`, `SwitchItem`, `ExpandItem`, `ChoiceItem`…).
  A row's `SettingsRow` title is what search shows and matches: keep it the same as the item's title, and put other
  words people might search for (old names included) in its keywords.
- `SettingsView.kt` is the screen only: it lists the sections and the main page's headings over them, and handles
  search and paging. A section in none of the headings doesn't show.
- A long section can be divided with `SettingsHeading(...)` rows: each starts its own block (search shows a setting
  with its heading).
- New settings fields:
  - go in `data/Settings.kt` with a default;
  - use an enum implementing `Choice` for multi-way options, with `@SerialName` on persisted values;
  - change an existing default only through `Settings.migrated()`, with a test.

## 5. WebView
- `WebViewHost` keeps the WebView pool and wires the clients.
- Each job is its own class and the host delegates to it. Examples: `SitePermissions`, `PopupPolicy`, `Downloads`, `Printer`, `PageBridge`, `UserAgents`, `CertificateExceptions`, `ExternalApps`, `IncognitoProfiles`, `ContentBlocker`.
- A new WebView behaviour is a new class (in its feature's package if it belongs to a feature), never another 50 lines in the host.
- Page JavaScript lives in `app/src/main/assets/js/*.js` (loaded by `PageScripts`), with a header comment. It's never inlined in Kotlin strings.

## 6. Privacy
- Anything that records, caches or remembers must check `TabItem.keepsTraces`. Examples: history, recents, previews, icons, saved tabs, suggestion caching, Undo.
- Incognito state is per profile: key session data by the profile name (`scope`).
- Nothing from incognito is ever written to disk.

## 7. Types and style
- Use enums or sealed types for modes and kinds, never strings like `"edit"` / `"add_folder"`.
- One job per file. Past ~400 lines, look for the second job and split it out.
- Comments explain *why* and how the thing behaves, written for someone new to the code. Don't reference the old React Native app (`legacy/`, `.tsx`, hook names).
- No speculative abstraction: no interfaces with one implementation, no config nobody sets.
- Web addresses shown on one line never pan (`Txt(…, pan = false)`), and show the site (`displayHost`), not the whole address: "…" is fine there.

## 8. Checklist for a new feature
1. Create a package. Put the rules in it (pure, tested).
2. If it keeps state, add a store with `load()` and `savers`, and hold it in BrowserViewModel (add it to `start()` and `savers`).
3. Put actions that span stores in extension functions in the package.
4. Add its settings to the section file(s) in `ui/settings/` where they appear; a new section gets its own file, listed in SettingsView and in one of the main page's `headings` there.
5. Put WebView behaviour in its own class, delegated from WebViewHost.
6. Apply the `keepsTraces` rule to anything recorded.
7. Write tests, then build and run on mi-browser's own emulator, `mi_browser_test` (emulator-5556; never the shared `mi_test`, never the user's phone).
8. Update the package table in §1 of this file if packages changed.

## Project rules
- The mi. logo inside the app (`MiLogo`: home page, tab cards, the walkthrough) is drawn in the text colour
  (`onSurface`), never the app icon's Sky blue `#3D9BFF`. The rest of the app is coloured by the Accent setting (default: the same Sky blue).
- Versioning: MAJOR = massive changes, MINOR = significant features, PATCH = fixes/QoL.
- Commit or push only when asked. Never commit or print `keystore.properties` or `*.jks`.
- Debug builds are slow on the emulator. Judge performance on release builds.
