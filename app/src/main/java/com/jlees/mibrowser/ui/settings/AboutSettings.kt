// Settings › About: mi itself first (its version, being the default browser), then help (learning it, reaching out,
// the other mi. apps), then the fine print (privacy, source, credits).
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
import com.jlees.mibrowser.logic.Feedback
import com.jlees.mibrowser.ui.common.*

private const val GUIDE_URL = "https://mi-app-hub.github.io/mi-browser/user"
private const val SOURCE_URL = com.jlees.mibrowser.logic.Feedback.REPO
private const val PRIVACY_URL = "https://mi-app-hub.github.io/mi-browser/privacy"

/** [openPage] opens an address in a new tab, in front (leaving Settings). */
fun aboutSection(vm: BrowserViewModel, openPage: (String) -> Unit) = SettingsSection("about", "About", "info", "Updates, help, feedback, credits", listOf(
    SettingsHeading("mi. Browser"),
    SettingsRow("Check for Updates", "version update") {
        Item("restart_alt", "Check for Updates", "mi. Browser ${BuildConfig.VERSION_NAME}", onClick = { vm.updater.checkNow() })
    },
    SettingsRow("Default Browser", "links open") { DefaultBrowserItem() },
    SettingsHeading("Help & Feedback"),
    SettingsRow("Gesture Walkthrough", "help gestures intro tutorial tour quick guide") {
        Item("toolbar", "Gesture Walkthrough", "Try every swipe, step by step", onClick = { vm.updateSettings { copy(walkthroughSeen = false) } })
    },
    SettingsRow("User Guide", "help") {
        Item("help", "User Guide", "Every gesture, tool and setting", onClick = { openPage(GUIDE_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    // each opens its GitHub form with mi's version filled in
    SettingsRow("Report a Bug", "feedback issue bug report problem broken crash github report a bug or suggest a feature") {
        Item("bug_report", "Report a Bug", "Tell us what isn't working, on GitHub", onClick = { openPage(Feedback.bugReport(BuildConfig.VERSION_NAME)) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Suggest a Feature", "feedback feature request idea wish github report a bug or suggest a feature") {
        Item("star", "Suggest a Feature", "Share an idea for mi. Browser, on GitHub", onClick = { openPage(Feedback.featureRequest(BuildConfig.VERSION_NAME)) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Request Extension Support", "extension add-on addon support request doesn't work github") {
        RequestExtensionSupportItem(openPage)
    },
    SettingsRow("More mi. Apps", "more mi apps other apps family " + MI_APPS.joinToString(" ") { it.slug }) { MoreMiAppsItem(openPage) },
    SettingsHeading("Legal & Credits"),
    SettingsRow("Privacy Policy", "privacy data collection policy") {
        Item("shield", "Privacy Policy", "mi collects no personal data", onClick = { openPage(PRIVACY_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Source Code", "github open source") {
        Item("code", "Source Code", "github.com/mi-app-hub/mi-browser", onClick = { openPage(SOURCE_URL) }, trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
    },
    SettingsRow("Credits", "licenses open source attribution") { CreditsItem() },
    SettingsRow("Filter List Credits", "easylist license adblock") {
        Item("info", "Filter List Credits", "EasyList, EasyPrivacy and the EasyList Cookie List by The EasyList authors (CC BY-SA 3.0). StevenBlack hosts (MIT).")
    },
))

/**
 * Another mi. app (mi-brand's family table: every one but this, released or not). Its links follow the family's
 * pattern, its website at mi-app-hub.github.io/mi-<slug>, so they're right before that page goes up.
 */
private class MiApp(val name: String, val slug: String, val line: String, val icon: Int) {
    val packageName get() = "com.jlees.mi$slug"
    val site get() = "https://mi-app-hub.github.io/mi-$slug/"
}

private val MI_APPS = listOf(
    MiApp("mi. Notes", "notes", "Notes in folders on one calm page", com.jlees.mibrowser.R.drawable.ic_family_notes),
    MiApp("mi. Budget", "budget", "Your purchases, budgets and subscriptions, private on your phone", com.jlees.mibrowser.R.drawable.ic_family_budget),
    MiApp("mi. Edits", "edits", "A simple, private video and audio editor", com.jlees.mibrowser.R.drawable.ic_family_edits),
    MiApp("mi. Sports", "sports", "Live scores for the teams, players and leagues you follow", com.jlees.mibrowser.R.drawable.ic_family_sports),
    MiApp("mi. Weather", "weather", "Every detail of the weather where you are, all inside the app", com.jlees.mibrowser.R.drawable.ic_family_weather),
)

/**
 * The other mi. apps, tucked under one row: each with its own launcher icon (its glyph in its colour on the icon's
 * near-black circle), its name and one line. A tap opens it if it's installed (its package is in the manifest's
 * <queries>), else its website.
 */
@Composable
private fun MoreMiAppsItem(openPage: (String) -> Unit) {
    val ctx = LocalContext.current
    val names = MI_APPS.map { it.name.removePrefix("mi. ") }
    ExpandItem("apps", "More mi. Apps", names.dropLast(1).joinToString(", ") + " and " + names.last(), null) { _ ->
        MI_APPS.forEach { app ->
            OptionRow(app.name, selected = false, leading = { com.jlees.mibrowser.launcher.AppIconPreview(app.icon, com.jlees.mibrowser.R.color.icon_background, 36.dp) }, detail = app.line) {
                val launch = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launch != null) runCatching { ctx.startActivity(launch) } else openPage(app.site)
            }
        }
    }
}

/** Opens the extension support form with mi's and this phone's WebView version filled in (extensions run on WebView). */
@Composable
private fun RequestExtensionSupportItem(openPage: (String) -> Unit) {
    val ctx = LocalContext.current
    Item("extension", "Request Extension Support", "Ask for an extension to work in mi, on GitHub",
        onClick = { openPage(Feedback.extensionSupport(BuildConfig.VERSION_NAME, com.jlees.mibrowser.extensions.webViewVersion(ctx))) },
        trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
}

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
            "Colors" to "materialkolor, Apache License 2.0",
            "Camera" to "CameraX, Apache License 2.0",
        ).forEach { (what, who) ->
            Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
                Txt(what, 14f, ui.cs.onSurface, ui.semibold)
                Txt(who, 13f, ui.cs.onSurfaceVariant, ui.regular)
            }
        }
    }
}

