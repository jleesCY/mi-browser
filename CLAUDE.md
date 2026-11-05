# mi browser: code standard

Kotlin, Jetpack Compose (Material 3 Expressive) and Android WebView. Code is in `app/src/main/java/com/jlees/mibrowser/`.
Read this before adding or changing a feature. A new feature isn't done until it meets every rule here.

## 1. A feature is one package
Everything that exists only for one feature lives in that feature's package:
- its rules,
- its store,
- its Settings section,
- its screens,
- its hooks into the WebView.

Deleting the package (plus one line in BrowserViewModel and one in SettingsView) should remove the feature.

| Package | Owns |
|---|---|
| `adblock/` | filter engine, content blocker (WebView hooks), tracking-parameter cleaning, Ad Blocking section |
| `search/` | engines, `!` shortcuts, suggestions, Pill highlighting of shortcuts, search settings |
| `incognito/` | WebView profiles, screen/keyboard protections, notification, toggle |
| `tabs/` | TabsStore, TabRules, per-tab back/forward stack, tab switcher, Tabs section |
| `library/` | HistoryStore, BookmarksStore, their rules, History and Bookmarks screens, Library section |
| `home/` | new-tab page, Home Page section |
| `qr/` | scanner and generator |
| `launcher/` | launcher shortcuts, search widget |
| `update/` | GitHub release checks and installs |

Shared plumbing, used by several features, stays in the shell packages:
- `data/`: models, Settings, JsonStore/Saver, Favicons, backup;
- `logic/`: URL parsing, list helpers;
- `web/`: WebViewHost and one class per WebView job;
- `ui/`: App, Pill, sheets, dialogs, the tab menu, Settings shell, `common/` widgets.

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
- Run `./gradlew :app:testDebugUnitTest` before calling anything done.

## 4. Settings
- A feature contributes its own section, `fun xSection(vm): SettingsSection`, built from `ui/settings/SettingsKit.kt` (`SettingsRow`, `Item`, `SwitchItem`, `ExpandItem`, `ChoiceItem`…).
- SettingsView only lists the sections and keeps the app-wide ones (Appearance, Browsing, Privacy, Backup, About).
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

## 8. Checklist for a new feature
1. Create a package. Put the rules in it (pure, tested).
2. If it keeps state, add a store with `load()` and `savers`, and hold it in BrowserViewModel (add it to `start()` and `savers`).
3. Put actions that span stores in extension functions in the package.
4. Add a Settings section function, and list it in SettingsView.
5. Put WebView behaviour in its own class, delegated from WebViewHost.
6. Apply the `keepsTraces` rule to anything recorded.
7. Write tests, then build and run on the emulator `mi_test` (never the user's phone).
8. Update the package table in §1 of this file if packages changed.

## Project rules
- Versioning: MAJOR = massive changes, MINOR = significant features, PATCH = fixes/QoL.
- Commit or push only when asked. Never commit or print `keystore.properties` or `*.jks`.
- Debug builds are slow on the emulator. Judge performance on release builds.
