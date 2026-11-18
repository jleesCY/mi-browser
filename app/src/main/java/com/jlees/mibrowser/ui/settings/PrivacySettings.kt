// Settings › Privacy & Security: clearing browsing data, cookies, and connection and site safety.
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.Sym

/** Settings › Privacy & Security: clearing data, cookies, and connection and site safety. */
fun privacySection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("privacy", "Privacy & Security", "shield", "How mi keeps you safe while you browse", listOfNotNull(
        SettingsHeading("Browsing Data"),
        SettingsRow("Clear Browsing Data", "cookies cache storage delete") {
            Item("delete", "Clear Browsing Data", "Cookies, site data, cache, site icons and tab snapshots", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.CLEAR_DATA) })
        },
        SettingsRow("Clear on Exit", "clear exit quit close delete history cookies tabs automatically") {
            SwitchItem("power_settings_new", "Clear on Exit", "Clear all browsing data when exiting mi", s.clearDataOnExit) { set { copy(clearDataOnExit = it) } }
        },
        SettingsHeading("Cookies"),
        SettingsRow("Allow Cookies", "cookies") { SwitchItem("cookie", "Allow Cookies", "Allow mi to store your cookies while browsing", !s.blockCookies) { set { copy(blockCookies = !it) } } },
        SettingsRow("Block Third-Party Cookies", "cross-site tracking") {
            SwitchItem("cookie", "Block Third-Party Cookies", "Blocks mi from storing cross-site cookies", s.blockThirdPartyCookies) { set { copy(blockThirdPartyCookies = it) } }
        },
        SettingsHeading("Security"),
        SettingsRow("HTTPS Only", "secure ssl tls") { SwitchItem("lock", "HTTPS Only", "Only use secure HTTPS connections", s.httpsOnly) { set { copy(httpsOnly = it) } } },
        // only with HTTPS Only on
        if (s.httpsOnly) SettingsRow("Warn Every Time", "https insecure http warning ask continue") {
            SwitchItem("warning", "Warn Every Time", "Warns every time a site cannot be accessed securely", s.httpsWarnEveryTime) { set { copy(httpsWarnEveryTime = it) } }
        } else null,
        SettingsRow("JavaScript", "js scripts") { SwitchItem("code", "JavaScript", "Allow sites to use JavaScript", s.jsEnabled) { set { copy(jsEnabled = it) } } },
        SettingsRow("Pop-ups", "popups windows") {
            val n = s.popupSites.size
            Item("open_in_new", "Pop-ups",
                if (n == 0) "Open when you initiate; blocked otherwise" else "Also allowed without a tap on $n site${if (n == 1) "" else "s"}",
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
