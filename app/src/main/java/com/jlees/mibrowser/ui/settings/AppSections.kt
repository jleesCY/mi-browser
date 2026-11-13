// The app-wide Settings sections that need only the view model (SettingsView lists them; Backup & Reset and About stay
// there, as they use its file pickers and the default-browser prompt).
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.data.ThemeMode
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.Sym

/** Settings › Look & Feel: colours, text, layout and haptics. */
fun lookSection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("look", "Look & Feel", "palette", "Theme, colours, fonts, spacing, haptics", listOf(
        SettingsHeading("Colors"),
        SettingsRow("Theme", "light dark system mode") {
            ChoiceItem("routine", "Theme", null, s.themeMode) { set { copy(themeMode = it) } }
        },
        SettingsRow("Pure Black", "oled amoled black dark battery") {
            if (s.themeMode != ThemeMode.LIGHT) SwitchItem("contrast", "Pure Black (OLED)", "True black backgrounds in dark mode", s.pureBlack) { set { copy(pureBlack = it) } }
        },
        SettingsRow("Accent Color", "colour wallpaper material you dynamic") { AccentItem(vm) },
        SettingsHeading("Text"),
        SettingsRow("Font Size", "text scale app") {
            SliderItem("format_size", "Font Size", "Text size throughout mi", s.fontScale, 0.8f, 1.2f) { set { copy(fontScale = it) } }
        },
        SettingsRow("Font", "typeface rounded system font") { ChoiceItem("font_download", "Font", null, s.font) { set { copy(font = it) } } },
        SettingsHeading("Layout"),
        SettingsRow("Corners", "radius shape") {
            ChoiceItem("rounded_corner", "Corners", null, listOf("0" to "Square", "12" to "Soft", "28" to "Round"), s.cornerRadius.toString()) { set { copy(cornerRadius = it.toInt()) } }
        },
        SettingsRow("Spacing", "density padding compact airy") {
            ChoiceItem("density_medium", "Spacing", "How lists appear in mi", s.uiPadding) { set { copy(uiPadding = it) } }
        },
        SettingsRow("Status Bar", "clock battery immersive") { SwitchItem("battery_charging_full", "Status Bar", null, s.showStatusBar) { set { copy(showStatusBar = it) } } },
        SettingsHeading("Feedback"),
        SettingsRow("Haptic Feedback", "vibration vibrate haptics touch feel") {
            SwitchItem("vibration", "Haptic Feedback", "Taps when gestures are performed", s.hapticFeedback) { set { copy(hapticFeedback = it) } }
        },
    ))
}

/** Settings › Pill: hiding and the loading bar, then what it shows while you type (actions, answers, searches). */
fun pillSection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("pill", "Pill", "toolbar", "Hiding, loading bar, suggestions", listOf(
        SettingsHeading("Display"),
        SettingsRow("Hide on Scroll", "auto hide pill toolbar scrolling recall") {
            SwitchItem("keyboard_arrow_down", "Hide on Scroll", "Hide the Pill while you scroll down a page", s.hidePillOnScroll) { set { copy(hidePillOnScroll = it) } }
        },
        SettingsRow("Loading Bar", "progress") {
            ChoiceItem("hourglass_empty", "Loading Bar", "Fill behind the address while a page loads", s.progressBarMode) { set { copy(progressBarMode = it) } }
        },
        SettingsHeading("While Typing"),
        SettingsRow("Quick Actions", "paste and go clipboard copied link") {
            SwitchItem("content_paste", "Quick Actions", "Actions like Paste and Go, above the Pill when you tap it", s.quickActions) { set { copy(quickActions = it) } }
        },
        com.jlees.mibrowser.answers.inlineAnswersRow(vm),
    ) + com.jlees.mibrowser.search.pillSearchRows(vm) + listOf(
    ))
}

/** Settings › Web Pages: how pages are shown. */
fun pagesSection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("pages", "Web Pages", "public", "Text size, dark pages, desktop and reader mode", listOf(
        SettingsRow("Page Text Size", "zoom font web websites") {
            SliderItem("format_size", "Page Text Size", "Text size on websites", s.textZoom / 100f, 0.5f, 2f) { set { copy(textZoom = Math.round(it * 100)) } }
        },
        SettingsRow("Darken Web Pages", "dark mode force night websites") {
            SwitchItem("contrast", "Darken Web Pages", "Darken sites that have no dark mode", s.darkenPages) { set { copy(darkenPages = it) } }
        },
        SettingsRow("Desktop Site by Default", "desktop mode user agent") {
            SwitchItem("desktop_windows", "Desktop Site by Default", "Request desktop versions of websites (each tab can still change it)", s.desktopMode) { set { copy(desktopMode = it) } }
        },
        SettingsRow("Reader Mode by Default", "reader readability") {
            SwitchItem("chrome_reader_mode", "Reader Mode by Default", "Simplify pages for reading (each tab can still change it)", s.readerModeEnabled) { set { copy(readerModeEnabled = it) } }
        },
    ))
}

/** Settings › Privacy & Security: clearing data, cookies, and connection and site safety. */
fun privacySection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("privacy", "Privacy & Security", "shield", "Clearing data, cookies, HTTPS, JavaScript, pop-ups", listOfNotNull(
        SettingsHeading("Browsing Data"),
        SettingsRow("Clear Browsing Data", "cookies cache storage delete") {
            Item("delete", "Clear Browsing Data", "Cookies, site data, cache, site icons and tab snapshots", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.CLEAR_DATA) })
        },
        SettingsRow("Clear on Exit", "clear exit quit close delete history cookies tabs automatically") {
            SwitchItem("power_settings_new", "Clear on Exit", "Clear all browsing data when exiting mi", s.clearDataOnExit) { set { copy(clearDataOnExit = it) } }
        },
        SettingsHeading("Cookies"),
        SettingsRow("Allow Cookies", "cookies") { SwitchItem("cookie", "Allow Cookies", "Turning this off signs you out of most sites", !s.blockCookies) { set { copy(blockCookies = !it) } } },
        SettingsRow("Block Third-Party Cookies", "cross-site tracking") {
            SwitchItem("cookie", "Block Third-Party Cookies", "Stops cross-site cookies; can break payment and sign-in windows", s.blockThirdPartyCookies) { set { copy(blockThirdPartyCookies = it) } }
        },
        SettingsHeading("Security"),
        SettingsRow("HTTPS Only", "secure ssl tls") { SwitchItem("lock", "HTTPS Only", "Always use secure connections", s.httpsOnly) { set { copy(httpsOnly = it) } } },
        // only with HTTPS Only on
        if (s.httpsOnly) SettingsRow("Warn Every Time", "https insecure http warning ask continue") {
            SwitchItem("warning", "Warn Every Time", "Ask before every visit to an insecure site", s.httpsWarnEveryTime) { set { copy(httpsWarnEveryTime = it) } }
        } else null,
        SettingsRow("JavaScript", "js scripts") { SwitchItem("code", "JavaScript", "Most sites need it to work", s.jsEnabled) { set { copy(jsEnabled = it) } } },
        SettingsRow("Pop-ups", "popups windows") {
            val n = s.popupSites.size
            Item("open_in_new", "Pop-ups",
                if (n == 0) "Open when you tap; blocked otherwise" else "Also allowed without a tap on $n site${if (n == 1) "" else "s"}",
                onClick = if (n == 0) null else { {
                    vm.showAlert("Pop-ups", "Allowed without a tap on:\n" + s.popupSites.joinToString("\n"), listOf(
                        AlertButton("Close", ButtonStyle.CANCEL),
                        AlertButton("Block all again", ButtonStyle.DESTRUCTIVE) { set { copy(popupSites = emptyList()) } },
                    ), onCancel = {})
                } },
                trailing = if (n == 0) null else { { Sym("chevron_right", 22f, LocalUi.current.cs.onSurfaceVariant) } })
        },
    ))
}
