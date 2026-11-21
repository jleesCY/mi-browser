// App mode, for a tab: turned on from the tab menu, the tab hides the Pill entirely; Android's own status and navigation
// bars stay, coloured like the site. While it shows, a quiet notification turns it off and brings the Pill back. (A
// home-screen pin opened as an app gets a window of its own instead: AppWindow.) Also the pin dialog, and the rules both share.
package com.jlees.mibrowser.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconHost
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.adblock.cleanLink
import com.jlees.mibrowser.logic.parseDeepLink
import com.jlees.mibrowser.ui.common.*

/** What assets/js/app-color.js gave (a JSON string, or null) as ARGB; null when it found no colour of its own. */
fun appColorOf(result: String?): Int? = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()?.let(::cssColor)

/** A CSS colour as ARGB: "#rgb", "#rrggbb", "#rrggbbaa", "rgb(…)" or "rgba(…)"; null for anything else or see-through. */
fun cssColor(css: String): Int? {
    val s = css.trim().lowercase()
    if (s.startsWith("#")) {
        val h = s.drop(1).let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
        if (h.length != 6 && h.length != 8) return null
        val v = h.take(6).toLongOrNull(16) ?: return null
        val a = if (h.length == 8) h.drop(6).toIntOrNull(16) ?: return null else 255
        return if (a == 0) null else (a shl 24) or v.toInt()
    }
    val parts = Regex("""^rgba?\(([^)]*)\)$""").find(s)?.groupValues?.get(1)?.split(Regex("[,/\\s]+"))?.filter { it.isNotEmpty() } ?: return null
    if (parts.size < 3) return null
    val (r, g, b) = parts.take(3).map { it.toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null }
    val a = parts.getOrNull(3)?.let { p -> if (p.endsWith("%")) p.dropLast(1).toFloatOrNull()?.div(100) else p.toFloatOrNull() } ?: 1f
    return if (a <= 0f) null else (Math.round(a.coerceAtMost(1f) * 255) shl 24) or (r shl 16) or (g shl 8) or b
}

private val SIGN_IN = Regex("""oauth|authori[sz]e|sign-?in|log-?in|/sso\b|/auth\b|/session""", RegexOption.IGNORE_CASE)

/**
 * In app mode, does going to [url] leave the app (opening in an ordinary tab instead)? Only a link you tapped
 * ([gesture], not a [redirect]) to another site than the app's [start]; a sign-in page (Google, GitHub…) stays in the
 * app, as signing in comes back to it.
 */
fun leavesApp(start: String, url: String, gesture: Boolean, redirect: Boolean): Boolean {
    if (!gesture || redirect || SIGN_IN.containsMatchIn(url)) return false
    val from = faviconHost(start) ?: return false
    val to = faviconHost(url) ?: return false
    return com.jlees.mibrowser.adblock.AdBlock.registrable(from) != com.jlees.mibrowser.adblock.AdBlock.registrable(to)
}

/** The tab menu's App Mode: on, it opens at the page showing now; off, it's an ordinary tab again. */
fun BrowserViewModel.toggleAppMode() {
    val t = tabs.current ?: return
    tabs.setApp(t.id, if (t.appStart == null) t.url ?: t.requestedUrl else null)
}

/** The notification's tap: [tabId] leaves app mode, in front, with the Pill showing. */
fun BrowserViewModel.exitAppMode(tabId: String) {
    if (tabs[tabId] == null) return
    setActiveTab(tabId)
    tabs.setApp(tabId, null)
    activeView = Sheet.NONE
    switcherVisible = false
}

/**
 * While the tab showing is in app mode: a silent notification, "Tap to exit immersive mode". The only way out besides the
 * tab menu (which needs the Pill), so with notifications off for mi, Back on the page's first page exits instead
 * (App.kt). Cancelled at launch too, in case the process ended while it showed.
 */
object AppModeNotice {
    private const val ID = 0x1c1

    fun update(ctx: Context, tab: TabItem?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (tab?.appStart == null || !nm.areNotificationsEnabled()) { nm.cancel(ID); return }
        nm.createNotificationChannel(NotificationChannel("app_mode", "Immersive mode", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a tab is immersive, to exit immersive mode"; setShowBadge(false)
        })
        val exit = PendingIntent.getActivity(ctx, 1,
            Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_EXIT_APP_MODE).putExtra(MainActivity.EXTRA_TAB, tab.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(ID, Notification.Builder(ctx, "app_mode")
            .setSmallIcon(R.drawable.sym_fullscreen)
            .setContentTitle(tab.title.ifBlank { displayHost(tab.url) })
            .setContentText("Tap to exit immersive mode")
            .setContentIntent(exit)
            .setLocalOnly(true).setOngoing(true).setShowWhen(false).setOnlyAlertOnce(true)
            .build())
    }
}

/** Adding a page to the home screen: its name, and whether it opens as an app. */
class PinDraft(val title: String, val url: String)

object PinFlow { var draft by mutableStateOf<PinDraft?>(null) }

/** "Add to Home Screen": name the pin, and choose Open as App (on by default: full screen, without the Pill). */
@Composable
fun PinDialog() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d = PinFlow.draft
    var name by remember(d) { mutableStateOf(d?.title.orEmpty()) }
    var asApp by remember(d) { mutableStateOf(true) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    val askNotify = rememberNotifyAsk() // app mode's way out is a notification
    com.jlees.mibrowser.ui.ModalFrame(d != null, { PinFlow.draft = null }) {
        DialogCard("Add to Home Screen", actions = {
            DialogAction("Cancel") { PinFlow.draft = null }
            DialogAction("Add") { d?.let { LauncherShortcuts.pinSite(ctx, name, it.url, asApp) }; if (asApp) askNotify(false); PinFlow.draft = null }
        }) {
            ClearableField(name, { name = it }, fr, placeholder = "Name")
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).touchable { asApp = !asApp }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Txt("Open as App", 15f, ui.cs.onSurface, ui.semibold)
                    Txt("Opens full screen, without the Pill", 13f, ui.cs.onSurfaceVariant, ui.regular)
                }
                MiSwitch(asApp, null)
            }
        }
    }
}
