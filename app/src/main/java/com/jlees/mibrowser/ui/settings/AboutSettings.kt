// Settings › About: mi itself first (its version, being the default browser), then help (learning it, then reaching
// out), then the fine print (privacy, source, credits).
package com.jlees.mibrowser.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.BuildConfig
import com.jlees.mibrowser.ui.common.*

private const val GUIDE_URL = "https://jleescy.github.io/mi-browser/user"
private const val SOURCE_URL = "https://github.com/jleescy/mi-browser"
private const val NEW_ISSUE_URL = "$SOURCE_URL/issues/new"
private const val PRIVACY_URL = "https://jleescy.github.io/mi-browser/privacy"

/** [openPage] opens an address in a new tab, in front (leaving Settings). */
fun aboutSection(vm: BrowserViewModel, openPage: (String) -> Unit) = SettingsSection("about", "About", "info", "Updates, help, feedback, credits", listOf(
    SettingsHeading("mi browser"),
    SettingsRow("Check for Updates", "version update") {
        Item("restart_alt", "Check for Updates", "mi browser ${BuildConfig.VERSION_NAME}", onClick = { vm.updater.checkNow() })
    },
    SettingsRow("Default Browser", "links open") { DefaultBrowserItem() },
    SettingsHeading("Help & Feedback"),
    SettingsRow("Gesture Walkthrough", "help gestures intro tutorial tour quick guide") {
        Item("toolbar", "Gesture Walkthrough", "Try every swipe, step by step", onClick = { vm.updateSettings { copy(walkthroughSeen = false) } })
    },
    SettingsRow("User Guide", "help") {
        Item("help", "User Guide", "Every gesture, tool and setting", onClick = { openPage(GUIDE_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Report a Bug or Suggest a Feature", "feedback issue bug report feature request idea github problem") {
        Item("bug_report", "Report a Bug or Suggest a Feature", "Open a new issue on GitHub", onClick = { openPage(NEW_ISSUE_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsHeading("Legal & Credits"),
    SettingsRow("Privacy Policy", "privacy data collection policy") {
        Item("shield", "Privacy Policy", "mi collects no personal data", onClick = { openPage(PRIVACY_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Source Code", "github open source") {
        Item("code", "Source Code", "github.com/jleescy/mi-browser", onClick = { openPage(SOURCE_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Credits", "licenses open source attribution") { CreditsItem() },
    SettingsRow("Filter List Credits", "easylist license adblock") {
        Item("info", "Filter lists", "EasyList, EasyPrivacy and the EasyList Cookie List by The EasyList authors (CC BY-SA 3.0). StevenBlack hosts (MIT).")
    },
))

/** Whether mi is the default browser (checked again when Android's picker returns), and asking to become it. */
class DefaultBrowser(val isDefault: Boolean, val request: () -> Unit)

@Composable
fun rememberDefaultBrowser(): DefaultBrowser {
    val ctx = LocalContext.current
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ctx.getSystemService(android.app.role.RoleManager::class.java) else null
    fun roleHeld() = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true
    var isDefault by remember { mutableStateOf(roleHeld()) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = roleHeld() }
    return DefaultBrowser(isDefault) {
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER))
            roleLauncher.launch(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
        else runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }
}

@Composable
private fun DefaultBrowserItem() {
    val ui = LocalUi.current
    val d = rememberDefaultBrowser()
    Item("public", "Default Browser", if (d.isDefault) "mi opens links from other apps" else "Make mi. open links from other apps",
        onClick = if (d.isDefault) null else d.request,
        trailing = { if (d.isDefault) Sym("check", 22f, ui.cs.primary) else Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) })
}

@Composable
private fun CreditsItem() {
    val ui = LocalUi.current
    ExpandItem("info", "Credits", "Open-source projects mi is built with", null) { _ ->
        listOf(
            "Ad & tracker blocklist" to "StevenBlack/hosts, MIT License",
            "Font" to "Google Sans Flex, © Google LLC",
            "Icons" to "Material Symbols, Apache License 2.0",
            "UI" to "Jetpack Compose & Material 3, Apache License 2.0",
            "QR codes" to "ZXing, Apache License 2.0",
            "Image cropping" to "Android Image Cropper, Apache License 2.0",
        ).forEach { (what, who) ->
            Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
                Txt(what, 14f, ui.cs.onSurface, ui.semibold)
                Txt(who, 13f, ui.cs.onSurfaceVariant, ui.regular)
            }
        }
    }
}

