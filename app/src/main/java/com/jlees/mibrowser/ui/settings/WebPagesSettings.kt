// Settings › Web Pages: how pages are shown (each tab can still change desktop and reader mode from the tab menu).
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings

/** Settings › Web Pages: how pages are shown. */
fun webPagesSection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("pages", "Web Pages", "public", "Global web page settings", listOf(
        SettingsRow("Text Size", "page text size zoom font web websites") {
            SliderItem("format_size", "Text Size", "Default text size on web pages", s.textZoom / 100f, 0.5f, 2f) { set { copy(textZoom = Math.round(it * 100)) } }
        },
        SettingsRow("Darken", "darken web pages dark mode force night websites") {
            SwitchItem("contrast", "Darken", "Darken web pages that are too bright", s.darkenPages) { set { copy(darkenPages = it) } }
        },
        SettingsRow("Desktop Mode", "desktop site by default user agent") {
            SwitchItem("desktop_windows", "Desktop Mode", "Request the desktop versions of web pages by default", s.desktopMode) { set { copy(desktopMode = it) } }
        },
        SettingsRow("Reader Mode", "reader mode by default readability") {
            SwitchItem("chrome_reader_mode", "Reader Mode", "Simplify pages for reading by default", s.readerModeEnabled) { set { copy(readerModeEnabled = it) } }
        },
    ))
}

