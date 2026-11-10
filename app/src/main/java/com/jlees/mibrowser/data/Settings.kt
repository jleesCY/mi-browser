// The user's settings (saved as settings.json) and the choices they offer. Each choice is an enum saved under its
// original text value (@SerialName), so settings from older versions load unchanged; an unknown value falls back to the
// default (Store's Json has coerceInputValues).
package com.jlees.mibrowser.data



import com.jlees.mibrowser.search.SEARCH_ENGINES
import com.jlees.mibrowser.search.isValidSearchTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A choice shown in Settings: its label there. */
interface Choice { val label: String }

@Serializable enum class ThemeMode(override val label: String) : Choice {
    @SerialName("light") LIGHT("Light"), @SerialName("dark") DARK("Dark"), @SerialName("system") SYSTEM("System")
}

@Serializable enum class Spacing(override val label: String) : Choice {
    @SerialName("compact") COMPACT("Compact"), @SerialName("normal") NORMAL("Normal"), @SerialName("airy") AIRY("Airy")
}

/** The typeface for mi's own text: Google Sans Flex, its rounded variant, or the phone's own font. */
@Serializable enum class AppFont(override val label: String) : Choice {
    @SerialName("sans_flex") STANDARD("Standard"), @SerialName("rounded") ROUNDED("Rounded"), @SerialName("system") SYSTEM("System")
}

/** The loading fill behind the Pill's address. */
@Serializable enum class LoadingBar(override val label: String) : Choice {
    @SerialName("ltr") STANDARD("Standard"), @SerialName("center") CENTER("Center Out"), @SerialName("none") HIDDEN("Hidden")
}

/** Where the recall handle (shown while the Pill is hidden) sits. */
@Serializable enum class RecallPosition { @SerialName("left") LEFT, @SerialName("center") CENTER, @SerialName("right") RIGHT }

/** Who downloads a file a page offers (Settings → Downloads). */
@Serializable enum class DownloadHandler(override val label: String) : Choice {
    // a saved "app" (the removed Another App choice) loads as the default, mi
    @SerialName("mi") MI("mi"), @SerialName("android") ANDROID("Android"),
}

/** How long History's Recently Closed keeps a closed tab. Past SESSION they're saved, so they outlast a restart (never incognito tabs). */
@Serializable enum class KeepClosedTabs(override val label: String, val ms: Long) : Choice {
    @SerialName("session") SESSION("Until mi Closes", 0), @SerialName("hour") HOUR("1 Hour", 3_600_000), @SerialName("day") DAY("1 Day", 86_400_000),
    @SerialName("week") WEEK("1 Week", 604_800_000),
}

@Serializable enum class StartupTabs(override val label: String) : Choice {
    @SerialName("new") NEW("New Tab"), @SerialName("last") LAST("Continue Session")
}

@Serializable enum class HistoryGrouping(override val label: String) : Choice {
    @SerialName("Time") TIME("Time"), @SerialName("Site") SITE("Site")
}

@Serializable enum class HomeClock(override val label: String) : Choice {
    @SerialName("None") NONE("None"), @SerialName("12h") H12("12h"), @SerialName("24h") H24("24h")
}

@Serializable enum class HomeDate(override val label: String) : Choice {
    @SerialName("None") NONE("None"), @SerialName("Above") ABOVE("Above"), @SerialName("Below") BELOW("Below")
}

@Serializable enum class HomeLogo(override val label: String) : Choice {
    @SerialName("None") NONE("None"), @SerialName("Static") STATIC("Static"), @SerialName("Fidget") FIDGET("Fidget")
}

/** The home page's shortcut button: its icon, its label on the home page, and its name in Settings. */
@Serializable enum class HomeShortcut(val icon: String, val short: String, override val label: String) : Choice {
    @SerialName("newTab") NEW_TAB("add", "New Tab", "New Tab"),
    @SerialName("qr") QR("qr_code_scanner", "Scan", "Scan QR Code"),
    @SerialName("bookmarks") BOOKMARKS("bookmarks", "Saved", "Bookmarks"),
    @SerialName("history") HISTORY("history", "Recent", "History"),
}

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Dark theme on true black (OLED screens): backgrounds black, cards a hair lighter. */
    val pureBlack: Boolean = false,
    val accentColor: String = "#007AFF",
    val searchEngineIndex: Int = 0,
    /** Your own search engine (index = number of built-ins): a name and an address with %s for the query. */
    val customSearchName: String = "",
    val customSearchUrl: String = "",
    /** Pull down at the very top of a page to reload it. */
    val pullToRefresh: Boolean = true,
    /** Haptic ticks at gesture thresholds (swipes, the switcher, pull to refresh, long presses). */
    val hapticFeedback: Boolean = true,
    /** Clear on Exit: all browsing data is cleared when mi closes (and at the next start, if it was ended without closing). */
    val clearDataOnExit: Boolean = false,
    /** Downloads: who downloads, where mi saves (a picked folder's tree URI; null: Downloads), and what's asked. */
    val downloadHandler: DownloadHandler = DownloadHandler.MI,
    val downloadFolder: String? = null,
    val askWhereToSave: Boolean = false,
    val confirmDownloads: Boolean = true,
    /** Your own search shortcuts: key ("ebay") → search address with %s. Checked before the built-ins. */
    val customBangs: Map<String, String> = emptyMap(),
    val cornerRadius: Int = 28, // 0 | 12 | 28
    val uiPadding: Spacing = Spacing.NORMAL,
    val fontScale: Float = 1f,
    val font: AppFont = AppFont.STANDARD,
    /** Before 1.6 (replaced by [font]); read once by the v4 migration. */
    val roundedFont: Boolean = false,
    /** The Quick Guide was shown (or skipped). Until then it shows at start, for new and existing users alike. */
    @SerialName("quickGuideSeen") val walkthroughSeen: Boolean = false, // its saved name from before the rename
    val showStatusBar: Boolean = true,
    val progressBarMode: LoadingBar = LoadingBar.STANDARD,
    val recallPosition: RecallPosition = RecallPosition.CENTER, // set by swiping the recall handle sideways
    val startupTabMode: StartupTabs = StartupTabs.NEW,
    val keepClosedTabs: KeepClosedTabs = KeepClosedTabs.SESSION,
    val showTabLogo: Boolean = true,
    val showBookmarkIcons: Boolean = true,
    val desktopMode: Boolean = false,
    val readerModeEnabled: Boolean = false,
    val recentSearchesExpanded: Boolean = false,
    val showFavoritesDefault: Boolean = false,
    /** Scrolling a page down hides the Pill (into the recall button), scrolling up brings it back. */
    val hidePillOnScroll: Boolean = true,
    val jsEnabled: Boolean = true,
    val httpsOnly: Boolean = true,
    /** With HTTPS Only: ask before every visit to an insecure site (off: continuing lets the site through for the session). */
    val httpsWarnEveryTime: Boolean = true,
    val blockCookies: Boolean = false,
    val blockThirdPartyCookies: Boolean = false, // payment and sign-in iframes (PayPal, SSO) need them, as in Chrome
    /** Ad blocking (AdBlock.kt), each part on its own: ads (EasyList + hosts list), trackers (EasyPrivacy), cookie banners. */
    val blockAds: Boolean = true,
    val blockTrackers: Boolean = true,
    val hideCookieBanners: Boolean = true,
    /** Strip tracking parameters (utm_*, fbclid, gclid…) from links you open, copy or share. */
    val stripTrackingParams: Boolean = true,
    /** Sites (hosts) where ad, tracker and cookie-banner blocking is off. */
    val adAllowlist: List<String> = emptyList(),
    /** Videos wait for a tap instead of playing on their own. */
    val blockAutoplay: Boolean = false,
    val searchSuggestions: Boolean = true,
    val incognitoSuggestions: Boolean = false,
    val historyLoadCount: Int = 10,
    val historyGrouping: HistoryGrouping = HistoryGrouping.TIME,
    val backgroundRefresh: Boolean = false,
    val homeClockType: HomeClock = HomeClock.NONE,
    val homeDateType: HomeDate = HomeDate.NONE,
    val homeLogoType: HomeLogo = HomeLogo.FIDGET,
    val showHomeShortcuts: Boolean = false,
    val homeShortcutAction: HomeShortcut = HomeShortcut.QR,
    /** Sites allowed to open pop-ups without a tap (pop-ups opened by a tap are always allowed). */
    val popupSites: List<String> = emptyList(),
    /** Text size on web pages, in percent (WebSettings.textZoom). Separate from the app's own font size. */
    val textZoom: Int = 100,
    /** In dark theme, darken sites that have no dark mode of their own (algorithmic darkening). */
    val darkenPages: Boolean = false,
    /** When the automatic update check last reached GitHub (ms); it runs at most every 12 hours. */
    val lastUpdateCheck: Long = 0,
    /** Bumped when a default changes for existing installs; see BrowserViewModel.migrate. */
    val settingsVersion: Int = 0,
) {

    /** Load-time sanitising. */
    fun sanitized() = copy(searchEngineIndex = if (searchEngineIndex in SEARCH_ENGINES.indices || (searchEngineIndex == SEARCH_ENGINES.size && isValidSearchTemplate(customSearchUrl))) searchEngineIndex else 0)
}

/** Defaults changed for existing installs. v1: third-party cookies allowed (payment/sign-in iframes need them). */
fun Settings.migrated(): Settings {
    var m = this
    if (m.settingsVersion < 1) m = m.copy(blockThirdPartyCookies = false, settingsVersion = 1)
    // 1.3: "Block Ads & Trackers" (blockTrackers) split into ads / trackers / cookie banners: whoever had it off keeps
    // all three off (cookie banners only worked with it on)
    if (m.settingsVersion < 2) m = m.copy(blockAds = m.blockTrackers, hideCookieBanners = m.hideCookieBanners && m.blockTrackers, settingsVersion = 2)
    // 1.5: HTTPS Only (with Warn Every Time) is on by default, existing installs included, once
    if (m.settingsVersion < 3) m = m.copy(httpsOnly = true, httpsWarnEveryTime = true, settingsVersion = 3)
    // 1.6: the Rounded Font switch became the Font choice
    if (m.settingsVersion < 4) m = m.copy(font = if (m.roundedFont) AppFont.ROUNDED else m.font, settingsVersion = 4)
    return m
}
