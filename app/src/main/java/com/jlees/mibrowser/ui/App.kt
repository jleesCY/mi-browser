// Root screen. Port of legacy/app/index.tsx render tree + effects.
package com.jlees.mibrowser.ui

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
import com.jlees.mibrowser.ui.common.sansFlex
import com.jlees.mibrowser.ui.common.sansFlexTypography
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
import com.jlees.mibrowser.ui.bookmarks.BookmarksView
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.ConnectedChoice
import com.jlees.mibrowser.ui.common.UiConfig
import com.jlees.mibrowser.ui.history.HistoryView
import com.jlees.mibrowser.ui.home.HomePage
import com.jlees.mibrowser.ui.qr.QrGenerator
import com.jlees.mibrowser.ui.qr.QrScanner
import com.jlees.mibrowser.ui.settings.SettingsView
import com.jlees.mibrowser.web.MAX_LIVE_TABS
import com.jlees.mibrowser.web.WebViewHost
import com.jlees.mibrowser.updateIncognitoNotice
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(vm: BrowserViewModel, host: WebViewHost, activity: Activity) {
    val s = vm.settings
    val dark = when (s.themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val cs = if (s.accentColor == DYNAMIC_ACCENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else rememberDynamicColorScheme(
        seedColor = Color(hexColor(s.accentColor)), isDark = dark,
        style = if (s.accentColor == GRAY_ACCENT) PaletteStyle.Monochrome else PaletteStyle.Vibrant,
    )
    val ui = UiConfig(cs, dark, s.cornerRadius, s.fontScale, s.uiPadding, sansFlex(s.roundedFont))
    MaterialExpressiveTheme(colorScheme = cs, motionScheme = MotionScheme.expressive(), typography = sansFlexTypography(s.roundedFont)) {
    CompositionLocalProvider(LocalUi provides ui) {
    // incognito: the keyboard app is asked not to learn from anything typed in mi's own fields (pill, dialogs)
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    androidx.compose.ui.platform.InterceptPlatformTextInput({ request, next ->
        if (vm.currentTab?.incognito != true) next.startInputMethod(request)
        else next.startInputMethod(androidx.compose.ui.platform.PlatformTextInputMethodRequest { attrs ->
            request.createInputConnection(attrs).also { attrs.imeOptions = attrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
        })
    }) {
        val scope = rememberCoroutineScope()
        val pill = remember { PillState(scope, vm) }
        val density = LocalDensity.current
        val d = density.density
        // The keyboard height animates every frame while it opens/closes: it is read only inside layout lambdas
        // (imeDp()), so the keyboard animation re-lays-out the affected elements instead of recomposing the whole app.
        val imeInsets = WindowInsets.ime
        val imeDp = { imeInsets.getBottom(density) / d }
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

        // ---- incognito: no screenshots, screen recording or recent-apps thumbnail while an incognito tab is showing
        val incognitoShown = vm.currentTab?.incognito == true
        val incognitoCount = vm.tabs.count { it.incognito }
        val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
            updateIncognitoNotice(activity, vm.tabs.count { t -> t.incognito })
        }
        LaunchedEffect(incognitoCount) {
            // off the main thread: the binder calls would hold up the incognito wave's first frames
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { updateIncognitoNotice(activity, incognitoCount) }
            // asked when the first incognito tab opens (Android stops asking after two refusals)
            if (incognitoCount > 0 && Build.VERSION.SDK_INT >= 33 &&
                activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        LaunchedEffect(incognitoShown) {
            if (incognitoShown) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        // ---- only the visible tab's page runs; the others pause their animations and location updates
        LaunchedEffect(vm.activeTabId) { host.applyActivity() }

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
            fun sideOf(view: String) = if (view == "settings") 1f else -1f
            LaunchedEffect(vm.activeView) {
                if (vm.activeView != "none" && !sheetShown) { // not when switching Bookmarks ⇄ History
                    sheetShown = true
                    sheetX.snapTo(sideOf(vm.activeView))
                    sheetX.animateTo(0f, Motion.switcher)
                }
            }
            // ✕, Back or picking an item: slide back out to the side it came from
            val closeOverlay: () -> Unit = {
                focusManager.clearFocus()
                scope.launch { sheetX.animateTo(sideOf(vm.activeView), timing(250)); vm.onOverlayClosed(); sheetShown = false }
            }

            val popup = vm.currentTab?.openerId != null
            BackHandler(enabled = fullscreen || vm.activeView != "none" || vm.isInputFocused || (vm.currentTab?.currentIndex ?: 0) > 0 || popup) {
                when {
                    fullscreen -> host.exitFullscreen()
                    vm.activeView != "none" -> closeOverlay()
                    vm.isInputFocused -> focusManager.clearFocus()
                    // a popup window with nothing to go back to closes and returns to the page that opened it
                    !vm.goBack() && popup -> vm.closeWindow(vm.activeTabId)
                }
            }
            // remote debugging (chrome://inspect): always in dev builds, a setting in releases
            LaunchedEffect(s.remoteDebugging) { android.webkit.WebView.setWebContentsDebuggingEnabled(com.jlees.mibrowser.BuildConfig.DEBUG || s.remoteDebugging) }

            // ---- background WebViews (Background Refresh / lazy-loaded tabs) and cleanup of home tabs
            LaunchedEffect(vm.tabs, vm.activeTabId, s) {
                vm.tabs.forEach { t ->
                    if (t.isHome) { if (host.has(t.id)) host.destroy(t.id) }
                    // background tabs: keep live ones in sync; only warm new ones while under the live-WebView cap
                    else if (t.hasLoadedOnce && t.id != vm.activeTabId && (host.has(t.id) || host.liveCount < MAX_LIVE_TABS)) host.sync(t)
                }
            }

            // ---- page content
            Box(
                Modifier.fillMaxSize().padding(top = if (s.showStatusBar && !fullscreen) statusDp.dp else 0.dp).paddingDp(bottom = { if (fullscreen) 0f else imeDp() })
                    .onGloballyPositioned { vm.pageBounds = it.boundsInWindow() }
                    .graphicsLayer { switcherZoom(vm, this) }
                    .drawWithContent { switcherOverlay(vm, this, ui.cs.primary) }
                    .clipToBounds(),
            ) {
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
                    key(tab.id, host.generation) {
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
            // fade the browser chrome out as the page zooms into the switcher
            val chromeFade = Modifier.graphicsLayer { alpha = if (vm.switcherVisible) 1f - vm.switcherAnim.value else 1f }
            if (s.showStatusBar && !fullscreen) Box(chromeFade.fillMaxWidth().height(statusDp.dp).background(ui.cs.surface))

            if (!fullscreen) {
                val screenW = maxWidth.value
                Box(Modifier.fillMaxSize()) { // no fade here: the pill morphs into the switcher bar (BottomArea)
                    if (vm.activeView == "none") {
                        BottomArea(vm, pill, imeDp, navDp) {
                            RecentSearches(vm, pill, screenH)
                        }
                    }
                    RecallButton(vm, pill, navDp, screenW, Modifier.align(Alignment.BottomStart).zIndex(3f))
                }
                // above the tab switcher (zIndex 150) when opened from it; closing returns to the switcher
                if (vm.activeView != "none") Box(Modifier.fillMaxSize().zIndex(160f)) {
                    val library = vm.activeView == "bookmarks" || vm.activeView == "history"
                    val header: (@Composable () -> Unit)? = if (library) { {
                        ConnectedChoice(listOf("bookmarks" to "Bookmarks", "history" to "History"), vm.activeView) { vm.activeView = it }
                    } } else null
                    OverlaySheet(sheetX, statusDp, imeDp, closeOverlay, header) {
                        when (vm.activeView) {
                            "history" -> HistoryView(vm, closeOverlay)
                            "bookmarks" -> BookmarksView(vm, kbVisible, closeOverlay)
                            "settings" -> SettingsView(vm, closeOverlay)
                        }
                    }
                }
            }

            if (!fullscreen) ZoomingTabHeader(vm)
            if (!fullscreen) TabSwitcher(vm, navDp, statusDp)

            if (vm.findVisible) FindBar(imeDp, onQuery = { host.findAll(it) }, onNext = { host.findNext(it) }, onClose = {
                focusManager.clearFocus(); vm.findVisible = false; host.findAll("")
            })

            QrScanner(vm)
            QrGenerator(vm)
            ConfirmDialog(vm)
            RenameDialog(vm)
            AuthDialog(vm)
            ContextMenu(vm, onSaveImage = { url -> saveImageFromUrl(activity, vm, url) })
            CustomAlert(vm.alert, onButton = { vm.alert = null }, onCancel = { vm.alert?.onCancel?.invoke(); vm.alert = null })

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
