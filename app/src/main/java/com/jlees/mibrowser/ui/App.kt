// The one screen: the page, the Pill, the tab switcher, and the sheets and dialogs over them.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.ui.common.Txt

import com.jlees.mibrowser.sites.SiteSettingsView

import com.jlees.mibrowser.ui.common.LocalHaptics

import com.jlees.mibrowser.downloads.DownloadsView

import com.jlees.mibrowser.tabs.TabSwitcher
import com.jlees.mibrowser.tabs.ZoomingTabHeader
import com.jlees.mibrowser.tabs.switcherOverlay
import com.jlees.mibrowser.tabs.switcherZoom

import com.jlees.mibrowser.incognito.IncognitoEffects
import com.jlees.mibrowser.incognito.IncognitoTextInput

import com.jlees.mibrowser.ui.common.blockTouches

import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.data.HomeShortcut
import com.jlees.mibrowser.data.ThemeMode

import androidx.compose.ui.draw.clip
import com.jlees.mibrowser.ui.common.Sym
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import com.jlees.mibrowser.ui.common.Motion
import com.jlees.mibrowser.ui.common.paddingDp
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
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import com.jlees.mibrowser.logic.DYNAMIC_ACCENT
import com.jlees.mibrowser.logic.GRAY_ACCENT
import com.jlees.mibrowser.ui.common.appFont
import com.jlees.mibrowser.onboarding.GestureWalkthrough
import com.jlees.mibrowser.ui.common.appTypography
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.logic.hexColor
import com.jlees.mibrowser.library.BookmarksView
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.ConnectedChoice
import com.jlees.mibrowser.ui.common.UiConfig
import com.jlees.mibrowser.library.HistoryView
import com.jlees.mibrowser.home.HomePage
import com.jlees.mibrowser.qr.QrGenerator
import com.jlees.mibrowser.qr.QrScanner
import com.jlees.mibrowser.ui.settings.SettingsView
import com.jlees.mibrowser.web.MAX_LIVE_TABS
import com.jlees.mibrowser.web.WebViewHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(vm: BrowserViewModel, host: WebViewHost, activity: Activity) {
    val s = vm.settings
    val dark = when (s.themeMode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val baseScheme = if (s.accentColor == DYNAMIC_ACCENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else rememberDynamicColorScheme(
        seedColor = Color(hexColor(s.accentColor)), isDark = dark,
        style = if (s.accentColor == GRAY_ACCENT) PaletteStyle.Monochrome else PaletteStyle.Vibrant,
    )
    val scheme = if (dark && s.pureBlack) remember(baseScheme) { pureBlack(baseScheme) } else baseScheme
    val ui = UiConfig(scheme, dark, s.cornerRadius, s.fontScale, s.uiPadding, appFont(s.font), pureBlack = dark && s.pureBlack)
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = appTypography(s.font)) {
    CompositionLocalProvider(LocalUi provides ui) {
    IncognitoTextInput(incognito = { vm.tabs.current?.incognito == true }) {
        val scope = rememberCoroutineScope()
        val haptics = LocalHaptics.current
        val pill = remember { PillState(scope, vm, haptics) }
        val density = LocalDensity.current
        val d = density.density
        // The keyboard height animates every frame while it opens/closes: it is read only inside layout lambdas
        // (imeDp()), so the keyboard animation re-lays-out the affected elements instead of recomposing the whole app.
        val imeInsets = WindowInsets.ime
        val imeDp = { imeInsets.getBottom(density) / d }
        val imeTargetDp = WindowInsets.imeAnimationTarget.getBottom(density) / d
        // the top that's safe to draw in: the status bar's height, or with it hidden, the camera cutout's (else pages and
        // sheets would sit behind the camera)
        val statusDp = maxOf(WindowInsets.statusBars.getTop(density), WindowInsets.displayCutout.getTop(density)) / d
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
                    UiEvent.TabSwitched -> pill.tabSwitched()
                    UiEvent.FocusInput -> {}
                }
            }
        }

        // ---- system bars / orientation (StatusBar + onFullScreen)
        LaunchedEffect(s.showStatusBar, fullscreen, ui.isDark) {
            val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            ctl.isAppearanceLightStatusBars = !ui.isDark
            ctl.isAppearanceLightNavigationBars = !ui.isDark
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) ctl.hide(WindowInsetsCompat.Type.systemBars())
            else {
                ctl.show(WindowInsetsCompat.Type.navigationBars())
                if (s.showStatusBar) ctl.show(WindowInsetsCompat.Type.statusBars()) else ctl.hide(WindowInsetsCompat.Type.statusBars())
            }
            activity.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        IncognitoEffects(vm, activity)

        // ---- only the visible tab's page runs; the others pause their animations and location updates
        LaunchedEffect(vm.tabs.activeId) { host.applyActivity() }

        // ---- pages read light/dark when their WebView is created: rebuild them when the theme flips
        var lastDark by remember { mutableStateOf(dark) }
        LaunchedEffect(dark) { if (dark != lastDark) { lastDark = dark; vm.reloadAllTabs() } }

        // ---- pill focus animations
        LaunchedEffect(vm.isInputFocused) { pill.focusAnim.animateTo(if (vm.isInputFocused) 1f else 0f, timing(200)) }
        LaunchedEffect(vm.isInputFocused, kbVisible) { pill.handleAnim.animateTo(if (vm.isInputFocused && kbVisible) 1f else 0f, timing(200)) }

        BoxWithConstraints(Modifier.fillMaxSize().background(ui.cs.surface)) {
            // Focus sink: when Android re-assigns view focus after clearFocus(), Compose gives it to the first focusable
            // node. Without this that is the address bar, which would pop the keyboard back up.
            Box(Modifier.size(1.dp).focusable())
            val screenH = maxHeight.value

            // ---- recent searches drawer sizing
            pill.recentMin = if (s.showFavoritesDefault) 65f else 0f
            pill.recentMax = screenH - (PILL_HEIGHT + 24) - imeTargetDp - statusDp - navDp
            LaunchedEffect(vm.isInputFocused, kbVisible) { if (!vm.isInputFocused || !kbVisible) pill.animateRecent(0f, spring = false) }
            LaunchedEffect(vm.isInputFocused, kbVisible, s.recentSearchesExpanded, s.showFavoritesDefault) {
                if (vm.isInputFocused && kbVisible) {
                    val target = if (s.recentSearchesExpanded) pill.recentMax else if (s.showFavoritesDefault) 65f else 0f
                    if (target > 0) pill.animateRecent(target)
                }
            }

            // ---- overlay sheet
            // Sheets are full screen and slide in from the side of the button that opens them: Bookmarks/History from
            // the left, Settings from the right. sheetX is the offset in screen widths (-1 left, 0 shown, 1 right).
            // Closed, it rests off screen (|sheetX| = 1), so the sheet's first frame is already hidden when it opens;
            // resting at 0 flashed it fully open for a frame before the slide began.
            val sheetX = remember { Animatable(1f) }
            var sheetShown by remember { mutableStateOf(false) }
            // History comes up from the bottom (1 = below the screen; see OverlaySheet's fromBottom)
            fun sideOf(view: Sheet) = if (view == Sheet.SETTINGS || view == Sheet.SITE || view == Sheet.HISTORY || view == Sheet.RECENTLY_CLOSED) 1f else -1f
            LaunchedEffect(vm.activeView) {
                if (vm.activeView != Sheet.NONE && !sheetShown) { // not when switching Bookmarks ⇄ Downloads
                    sheetShown = true
                    sheetX.snapTo(sideOf(vm.activeView))
                    sheetX.animateTo(0f, Motion.switcher)
                } else if (vm.activeView == Sheet.NONE && sheetShown) {
                    // closed without its slide (e.g. the Quick Guide finishing): rest off screen, so the next one slides in
                    sheetShown = false
                    sheetX.snapTo(1f)
                }
            }
            // ✕, Back or picking an item: slide back out to the side it came from
            val closeOverlay: () -> Unit = {
                focusManager.clearFocus()
                scope.launch { sheetX.animateTo(sideOf(vm.activeView), timing(250)); vm.onOverlayClosed(); sheetShown = false }
            }

            val popup = vm.tabs.current?.openerId != null
            BackHandler(enabled = fullscreen || vm.activeView != Sheet.NONE || vm.isInputFocused || (vm.tabs.current?.currentIndex ?: 0) > 0 || popup) {
                when {
                    fullscreen -> host.exitFullscreen()
                    vm.activeView != Sheet.NONE -> closeOverlay()
                    vm.isInputFocused -> focusManager.clearFocus()
                    // a popup window with nothing to go back to closes and returns to the page that opened it
                    !vm.goBack() && popup -> vm.closeWindow(vm.tabs.activeId)
                }
            }
            // remote debugging (chrome://inspect): always in dev builds, a setting in releases
            // pages can be inspected from desktop Chrome (chrome://inspect) in dev builds only
            LaunchedEffect(Unit) { android.webkit.WebView.setWebContentsDebuggingEnabled(com.jlees.mibrowser.BuildConfig.DEBUG) }

            // ---- background WebViews (Background Refresh / lazy-loaded tabs) and cleanup of home tabs
            LaunchedEffect(vm.tabs.list, vm.tabs.activeId, s) {
                vm.tabs.list.forEach { t ->
                    if (t.isHome) { if (host.has(t.id)) host.destroy(t.id) }
                    // background tabs: keep live ones in sync; only warm new ones while under the live-WebView cap
                    else if (t.hasLoadedOnce && t.id != vm.tabs.activeId && (host.has(t.id) || host.liveCount < MAX_LIVE_TABS)) host.sync(t)
                }
            }

            // ---- page content
            Box(
                // with the status bar hidden, pages run behind the camera cutout and leave the space themselves (TopInset); with it
                // shown they start below it
                Modifier.fillMaxSize().padding(top = if (!fullscreen && s.showStatusBar) statusDp.dp else 0.dp).paddingDp(bottom = { if (fullscreen) 0f else imeDp() })
                    .onGloballyPositioned { vm.switcher.pageBounds = it.boundsInWindow() }
                    .graphicsLayer { switcherZoom(vm, this) }
                    .drawWithContent { switcherOverlay(vm, this, ui.cs.primary) { host.errors[it] != null } }
                    .clipToBounds(),
            ) {
                val tab = vm.tabs.current
                // mi's own pages (the blank tab, error pages) leave the camera cutout's space as web pages do
                val cutoutPad = if (!fullscreen && !s.showStatusBar) statusDp.dp else 0.dp
                if (tab != null && tab.isHome) Box(Modifier.fillMaxSize().background(com.jlees.mibrowser.home.homeBackground(tab.incognito)).padding(top = cutoutPad)) {
                    HomePage(vm, onAction = { a ->
                        when (a) {
                            HomeShortcut.NEW_TAB -> vm.addNewTab()
                            HomeShortcut.QR -> vm.qrScannerVisible = true
                            HomeShortcut.BOOKMARKS -> vm.openView(Sheet.BOOKMARKS)
                            HomeShortcut.HISTORY -> vm.openView(Sheet.HISTORY)
                        }
                    })
                } else if (tab != null && tab.hasLoadedOnce) {
                    key(tab.id, host.generation) {
                        // re-synced only when what the WebView uses changes (the page to load, desktop mode; the settings
                        // sync reads are watched too), not on every title or loading update of the tab
                        val syncKey by remember(tab.id) { derivedStateOf { vm.tabs[tab.id]?.let { Triple(it.requestedUrl, it.loadNonce, it.desktopMode) } } }
                        AndroidView(
                            factory = { host.sync(tab).also { (it.parent as? ViewGroup)?.removeView(it) } },
                            update = { syncKey; androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { vm.tabs[tab.id] }?.let { t -> host.sync(t) } },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    host.errors[tab.id]?.let { err -> Box(Modifier.fillMaxSize().background(ui.cs.surface).padding(top = cutoutPad)) {
                        ErrorPage(
                            err, tab.url,
                            onRetry = { host.retry(tab.id) },
                            onUpgrade = tab.url?.takeIf { it.startsWith("http://") }?.let { u -> { vm.loadInTab(tab.id, u.replaceFirst("http://", "https://")) } },
                            onProceed = { host.proceedUnsafe(tab.id) },
                            onContinueHttp = { host.continueOverHttp(tab.id) },
                        )
                    } }
                    if (vm.isInputFocused) Box(Modifier.fillMaxSize().zIndex(99f).blockTouches { focusManager.clearFocus() })
                }
            }

            // status bar backdrop: keeps page content from showing behind the (transparent) status bar
            // fade the browser chrome out as the page zooms into the switcher
            val chromeFade = Modifier.graphicsLayer { alpha = if (vm.switcherVisible) 1f - vm.switcher.anim.value else 1f }
            if (s.showStatusBar && !fullscreen) Box(chromeFade.fillMaxWidth().height(statusDp.dp).background(ui.cs.surface))

            if (!fullscreen) {
                val screenW = maxWidth.value
                Box(Modifier.fillMaxSize()) { // no fade here: the pill morphs into the switcher bar (BottomArea)
                    if (vm.activeView == Sheet.NONE) {
                        BottomArea(vm, pill, imeDp, navDp) {
                            RecentSearches(vm, pill)
                        }
                    }
                    RecallButton(vm, pill, navDp, screenW, Modifier.align(Alignment.BottomStart).zIndex(3f))
                }
                // above the tab switcher (zIndex 150) when opened from it; closing returns to the switcher
                if (vm.activeView != Sheet.NONE) Box(Modifier.fillMaxSize().zIndex(160f)) {
                    val library = vm.activeView == Sheet.BOOKMARKS || vm.activeView == Sheet.DOWNLOADS
                    val history = vm.activeView == Sheet.HISTORY || vm.activeView == Sheet.RECENTLY_CLOSED
                    val header: (@Composable () -> Unit)? = if (library) { { Txt("Library", 28f, ui.cs.onSurface, ui.extrabold) } } else if (vm.activeView == Sheet.SITE) { { Txt("Site Settings", 28f, ui.cs.onSurface, ui.extrabold) } }
                    else if (history) { { Txt("History", 28f, ui.cs.onSurface, ui.extrabold) } } else null
                    val side = sideOf(vm.activeView)
                    OverlaySheet(
                        sheetX, statusDp, imeDp, closeOverlay,
                        // swiping the view's search Pill pushes the sheet back out the way it came, with the finger
                        backTowardEnd = side > 0,
                        fromBottom = history,
                        onBackDrag = { p -> scope.launch { sheetX.snapTo(side * p) } },
                        onBackRelease = { close -> if (close) closeOverlay() else scope.launch { sheetX.animateTo(0f, Motion.switcher) } },
                        // opened from the switcher: the search Pill grows out of the switcher's Pill, and back into it
                        morphFrom = { if (vm.switcherVisible) vm.switcher.barPill else null },
                        morphContent = { com.jlees.mibrowser.tabs.SwitcherPillButtons({}, {}) },
                        header = header,
                        // the page's two sections, under its title
                        belowHeader = when {
                            library -> { { ConnectedChoice(listOf(Sheet.BOOKMARKS.name to "Bookmarks", Sheet.DOWNLOADS.name to "Downloads"), vm.activeView.name) { vm.activeView = Sheet.valueOf(it) } } }
                            history -> { { ConnectedChoice(listOf(Sheet.HISTORY.name to "History", Sheet.RECENTLY_CLOSED.name to "Recently Closed"), vm.activeView.name) { vm.activeView = Sheet.valueOf(it) } } }
                            else -> null
                        },
                    ) {
                        when (vm.activeView) {
                            Sheet.HISTORY -> HistoryView(vm, closeOverlay)
                            Sheet.RECENTLY_CLOSED -> com.jlees.mibrowser.library.RecentlyClosedView(vm, closeOverlay)
                            Sheet.BOOKMARKS -> BookmarksView(vm, kbVisible, closeOverlay)
                            Sheet.DOWNLOADS -> DownloadsView(vm)
                            Sheet.SETTINGS -> SettingsView(vm, closeOverlay)
                            Sheet.SITE -> SiteSettingsView(vm, host, closeOverlay)
                            Sheet.NONE -> {}
                        }
                    }
                }
            }

            if (!fullscreen) PullIndicator(vm, statusDp)
            if (!fullscreen) ZoomingTabHeader(vm)
            if (!fullscreen) TabSwitcher(
                vm, navDp, statusDp, errorFor = { host.errors[it] },
                // a swipe on the switcher's Pill pulls Library / Settings in with the finger, then settles it open or shut
                dragSheet = { view, p ->
                    if (vm.activeView != view) { sheetShown = true; vm.openView(view) } // shown: the open animation doesn't run
                    scope.launch { sheetX.snapTo(sideOf(view) * (1f - p)) }
                },
                releaseSheet = { open -> if (open) scope.launch { sheetX.animateTo(0f, Motion.switcher) } else closeOverlay() },
            )


            QrScanner(vm)
            QrGenerator(vm)
            ConfirmDialog(vm)
            RenameDialog(vm)
            AuthDialog(vm)
            ContextMenu(vm, onSaveImage = { url -> saveImageFromUrl(activity, vm, url) })
            CustomAlert(vm.alert, onButton = { vm.alert = null }, onCancel = { vm.alert?.onCancel?.invoke(); vm.alert = null })
            GestureWalkthrough(vm)

            host.customView?.let { v ->
                AndroidView(
                    factory = { v.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize().zIndex(5000f).background(Color.Black),
                )
            }
        }
    }
    }
    }
}

/** Pull to refresh: a round refresh badge that slides down with the pull and turns accent-filled once release reloads. */
@Composable
private fun PullIndicator(vm: BrowserViewModel, statusDp: Float) {
    val ui = LocalUi.current
    val target = vm.pullProgress
    val p by androidx.compose.animation.core.animateFloatAsState(target, if (target == 0f) Motion.spatial() else androidx.compose.animation.core.snap(), label = "pull")
    if (p <= 0.01f) return
    val ready = target >= 1f
    Box(Modifier.fillMaxWidth().padding(top = statusDp.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.graphicsLayer {
                translationY = (p.coerceAtMost(1.4f) * 64f).dp.toPx() - 48.dp.toPx() + 12.dp.toPx()
                alpha = (p * 1.6f).coerceIn(0f, 1f)
                val sc = 0.6f + 0.4f * p.coerceAtMost(1f); scaleX = sc; scaleY = sc
            }.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(if (ready) ui.cs.primaryContainer else ui.cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Sym("refresh", 24f, if (ready) ui.cs.onPrimaryContainer else ui.cs.onSurfaceVariant, Modifier.graphicsLayer { rotationZ = p * 300f })
        }
    }
}

/**
 * The OLED variant of a dark scheme: everything that fills the screen is true black (pixels off); cards, menus,
 * dialogs and the pill stay a step above it, with a hint of the accent, so they still read as surfaces.
 */
private fun pureBlack(cs: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    fun step(v: Int) = androidx.compose.ui.graphics.lerp(Color(0xFF000000.toInt() or (v shl 16) or (v shl 8) or v), cs.primary, 0.04f)
    return cs.copy(
        // full-screen backgrounds (pages, Settings, Library, the search drawer: surfaceContainerLow) are black
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black, surfaceContainer = step(0x12), surfaceContainerHigh = step(0x1C),
        surfaceContainerHighest = step(0x26), surfaceBright = step(0x2E), inverseOnSurface = Color.Black,
    )
}

