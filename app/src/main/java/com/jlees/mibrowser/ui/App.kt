// Root screen. Port of legacy/app/index.tsx render tree + effects.
package com.jlees.mibrowser.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.logic.hexColor
import com.jlees.mibrowser.ui.bookmarks.BookmarksView
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.UiConfig
import com.jlees.mibrowser.ui.common.c
import com.jlees.mibrowser.ui.history.HistoryView
import com.jlees.mibrowser.ui.home.HomePage
import com.jlees.mibrowser.ui.qr.QrGenerator
import com.jlees.mibrowser.ui.qr.QrScanner
import com.jlees.mibrowser.ui.settings.SettingsView
import com.jlees.mibrowser.ui.tabs.TabsView
import com.jlees.mibrowser.web.WebViewHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(vm: BrowserViewModel, host: WebViewHost, activity: Activity) {
    val s = vm.settings
    val ui = UiConfig(vm.palette, c(hexColor(s.accentColor)), s.cornerRadius, s.fontScale, s.uiPadding, s.fontWeight)
    CompositionLocalProvider(LocalUi provides ui) {
        val scope = rememberCoroutineScope()
        val pill = remember { PillState(scope, vm) }
        val density = LocalDensity.current
        val d = density.density
        val imeDp = WindowInsets.ime.getBottom(density) / d
        val imeTargetDp = WindowInsets.imeAnimationTarget.getBottom(density) / d
        val statusDp = WindowInsets.statusBars.getTop(density) / d
        val navDp = WindowInsets.navigationBars.getBottom(density) / d
        val kbVisible = WindowInsets.isImeVisible
        val fullscreen = host.customView != null
        val focusManager = LocalFocusManager.current

        // ---- events from VM / WebViews
        LaunchedEffect(Unit) {
            vm.ui.collect {
                when (it) {
                    UiEvent.LoadStart -> pill.loadStart()
                    is UiEvent.LoadProgress -> pill.loadProgress(it.p)
                    UiEvent.LoadEnd -> pill.loadEnd()
                    is UiEvent.Scroll -> pill.onScroll(it.yDp)
                    UiEvent.ShowBar -> pill.showBar()
                    UiEvent.SnapToSearch -> pill.snapToSearch()
                    UiEvent.TabSwitched -> pill.tabSwitched()
                    UiEvent.FocusInput -> {}
                }
            }
        }

        // ---- system bars / orientation (StatusBar + onFullScreen)
        LaunchedEffect(s.showStatusBar, fullscreen, ui.p.isDark) {
            val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            ctl.isAppearanceLightStatusBars = !ui.p.isDark
            ctl.isAppearanceLightNavigationBars = !ui.p.isDark
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) ctl.hide(WindowInsetsCompat.Type.systemBars())
            else {
                ctl.show(WindowInsetsCompat.Type.navigationBars())
                if (s.showStatusBar) ctl.show(WindowInsetsCompat.Type.statusBars()) else ctl.hide(WindowInsetsCompat.Type.statusBars())
            }
            activity.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // ---- pill focus animations
        LaunchedEffect(vm.isInputFocused) { pill.focusAnim.animateTo(if (vm.isInputFocused) 1f else 0f, timing(200)) }
        LaunchedEffect(vm.isInputFocused, kbVisible) { pill.handleAnim.animateTo(if (vm.isInputFocused && kbVisible) 1f else 0f, timing(200)) }
        LaunchedEffect(vm.isSearchActive) { if (vm.isSearchActive) vm.isSubMenuVisible = false }

        BoxWithConstraints(Modifier.fillMaxSize().background(ui.bg)) {
            // Focus sink: when Android re-assigns view focus after clearFocus(), Compose gives it to the first focusable
            // node. Without this that is the address bar, which would pop the keyboard back up.
            Box(Modifier.size(1.dp).focusable())
            val screenH = maxHeight.value
            val snapDefault = screenH * 0.6f
            val snapFull = screenH * 0.95f

            // ---- recent searches drawer sizing
            pill.recentMin = if (s.showFavoritesDefault) 65f else 0f
            pill.recentMax = screenH - (s.pillHeight + 24) - imeTargetDp - statusDp - navDp
            LaunchedEffect(vm.isInputFocused, kbVisible) { if (!vm.isInputFocused || !kbVisible) pill.animateRecent(0f, spring = false) }
            LaunchedEffect(vm.isInputFocused, kbVisible, s.recentSearchesExpanded, s.showFavoritesDefault) {
                if (vm.isInputFocused && kbVisible) {
                    val target = if (s.recentSearchesExpanded) pill.recentMax else if (s.showFavoritesDefault) 65f else 0f
                    if (target > 0) pill.animateRecent(target)
                }
            }

            // ---- overlay sheet
            val sheetH = remember { Animatable(0f) }
            LaunchedEffect(vm.activeView, s.expandMenus) {
                if (vm.activeView != "none") sheetH.animateTo(if (s.expandMenus) snapFull else snapDefault, springT60F9())
            }
            val closeOverlay: () -> Unit = {
                focusManager.clearFocus()
                scope.launch { sheetH.animateTo(0f, timing(250)); vm.onOverlayClosed() }
            }
            val focusSheetSearch: () -> Unit = { scope.launch { sheetH.animateTo(snapFull, springT60F9()) } }

            BackHandler(enabled = fullscreen || vm.activeView != "none" || vm.isInputFocused || (vm.currentTab?.currentIndex ?: 0) > 0) {
                when {
                    fullscreen -> host.exitFullscreen()
                    vm.activeView != "none" -> closeOverlay()
                    vm.isInputFocused -> focusManager.clearFocus()
                    else -> vm.goBack()
                }
            }

            // ---- background WebViews (Background Refresh / lazy-loaded tabs) and cleanup of home tabs
            LaunchedEffect(vm.tabs, vm.activeTabId, s) {
                vm.tabs.forEach { t ->
                    if (t.isHome) { if (host.has(t.id)) host.destroy(t.id) }
                    else if (t.hasLoadedOnce && t.id != vm.activeTabId) host.sync(t)
                }
            }

            // ---- page content
            Box(Modifier.fillMaxSize().padding(top = if (s.showStatusBar && !fullscreen) statusDp.dp else 0.dp).padding(bottom = if (fullscreen) 0.dp else imeDp.dp).clipToBounds()) {
                val tab = vm.currentTab
                if (tab != null && tab.isHome) {
                    HomePage(vm, onAction = { a ->
                        when (a) {
                            "newTab" -> vm.addNewTab()
                            "qr" -> vm.qrScannerVisible = true
                            else -> vm.openView(a)
                        }
                    })
                } else if (tab != null && tab.hasLoadedOnce) {
                    key(tab.id) {
                        AndroidView(
                            factory = { host.sync(tab).also { (it.parent as? ViewGroup)?.removeView(it) } },
                            update = { vm.tabs.firstOrNull { it.id == tab.id }?.let { t -> host.sync(t) } },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    host.errors[tab.id]?.let { err ->
                        ErrorPage(
                            err, tab.url,
                            onRetry = { host.retry(tab.id) },
                            onUpgrade = tab.url?.takeIf { it.startsWith("http://") }?.let { u -> { vm.loadInTab(tab.id, u.replaceFirst("http://", "https://")) } },
                            onProceed = { host.proceedUnsafe(tab.id) },
                        )
                    }
                    if (vm.isInputFocused) Box(Modifier.fillMaxSize().zIndex(99f).blockTouches { focusManager.clearFocus() })
                }
            }

            // status bar backdrop: keeps page content from showing behind the (transparent) status bar
            if (s.showStatusBar && !fullscreen) Box(Modifier.fillMaxWidth().height(statusDp.dp).background(ui.bg))

            if (!fullscreen) {
                if (vm.activeView == "none") {
                    BottomArea(vm, pill, imeDp, navDp, onSubmenuAction = {}) {
                        RecentSearches(vm, pill, screenH)
                    }
                }
                RecallButton(pill, navDp, Modifier.align(Alignment.BottomCenter).zIndex(3f))
                if (vm.activeView != "none") {
                    val title = when (vm.activeView) { "history" -> "History"; "tabs" -> "Tabs"; "bookmarks" -> "Bookmarks"; else -> "Settings" }
                    OverlaySheet(title, sheetH, snapDefault, snapFull, imeDp, closeOverlay, scope) {
                        when (vm.activeView) {
                            "tabs" -> TabsView(vm, sheetH.value, snapDefault, kbVisible, focusSheetSearch, closeOverlay)
                            "history" -> HistoryView(vm, focusSheetSearch, closeOverlay)
                            "bookmarks" -> BookmarksView(vm, sheetH.value, snapDefault, kbVisible, focusSheetSearch, closeOverlay)
                            "settings" -> SettingsView(vm, focusSheetSearch, closeOverlay)
                        }
                    }
                }
            }

            if (vm.findVisible) FindBar(vm, imeDp, onQuery = { host.findAll(it) }, onClose = {
                focusManager.clearFocus(); vm.findVisible = false; host.handle(com.jlees.mibrowser.WebCmd.ClearFind)
            })

            QrScanner(vm)
            QrGenerator(vm)
            ConfirmDialog(vm)
            RenameDialog(vm)
            ContextMenu(vm, onSaveImage = { url -> saveImageFromUrl(activity, vm, url) })
            CustomAlert(vm.alert) { vm.alert = null }

            host.customView?.let { v ->
                AndroidView(
                    factory = { v.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize().zIndex(5000f).background(Color.Black),
                )
            }
        }
    }
}
