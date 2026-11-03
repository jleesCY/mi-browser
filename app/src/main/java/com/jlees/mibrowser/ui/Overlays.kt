// Ports: submenu + context menu + confirm/rename modals (legacy/app/index.tsx), OverlaySheet.tsx, FindOnPageBar.tsx,
// CustomAlert.tsx, renderError (BrowserWebView.tsx).
package com.jlees.mibrowser.ui

import kotlinx.coroutines.CoroutineStart
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.AlertData
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.web.WebError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

fun shareText(ctx: android.content.Context, text: String, title: String? = null) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (title != null) send.putExtra(Intent.EXTRA_TITLE, title)
    ctx.startActivity(Intent.createChooser(send, title))
}

/** Full-screen catcher that swallows touches (TouchableWithoutFeedback backdrop). */
fun Modifier.blockTouches(onTap: () -> Unit = {}) = this.clickable(MutableInteractionSource(), null, onClick = onTap)

// ---------------------------------------------------------------- submenu (index.tsx 1942-2290)
@Composable
fun SubMenu(vm: BrowserViewModel, modifier: Modifier) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val hasUrl = vm.activeUrl != null
    val tab = vm.currentTab
    val shape = RoundedCornerShape(minOf(ui.radius, 24).dp)
    @Composable
    fun Item(icon: String, label: String, needsUrl: Boolean, onPress: () -> Unit) {
        val enabled = !needsUrl || hasUrl
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).alpha(if (enabled) 1f else 0.38f)
                .touchable(enabled, onClick = onPress).heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon, 22f, ui.cs.onSurfaceVariant, Modifier.padding(end = 14.dp))
            Txt(label, 15f, ui.cs.onSurface, ui.semibold)
        }
    }
    @Composable
    fun Toggle(icon: String, label: String, value: Boolean, onPress: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).touchable(onClick = onPress).heightIn(min = 52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon, 22f, ui.cs.onSurfaceVariant, Modifier.padding(end = 14.dp))
            Txt(label, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
            MiSwitch(value, null)
        }
    }
    Column(modifier.width(256.dp).zIndex(10f).shadow(8.dp, shape).clip(shape).background(ui.cs.surfaceContainer).blockTouches().padding(vertical = 8.dp)) {
        Item("home", "Home", false) { vm.isSubMenuVisible = false; vm.goHome() }
        Item("bookmark_add", "Bookmark", true) { vm.isSubMenuVisible = false; vm.bookmarksAutoAdd = true; vm.openView("bookmarks") }
        Item("share", "Share", true) { vm.activeUrl?.let { shareText(ctx, it, "Share Link") }; vm.isSubMenuVisible = false }
        Item("search", "Find in Page", true) { vm.isSubMenuVisible = false; vm.findVisible = true }
        Item("qr_code_scanner", "Scan QR Code", false) { vm.isSubMenuVisible = false; vm.qrScannerVisible = true }
        Item("qr_code_2", "Generate QR Code", true) { vm.isSubMenuVisible = false; vm.qrGeneratorVisible = true }
        Item("print", "Print", true) { vm.isSubMenuVisible = false; vm.web.tryEmit(WebCmd.Print) }
        Box(Modifier.padding(vertical = 4.dp, horizontal = 16.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant))
        Toggle("desktop_windows", "Desktop Mode", tab?.desktopMode ?: vm.settings.desktopMode) { vm.setDesktopMode(!(tab?.desktopMode ?: vm.settings.desktopMode)) }
        Toggle("chrome_reader_mode", "Reader Mode", tab?.readerMode ?: vm.settings.readerModeEnabled) { vm.setReaderMode(!(tab?.readerMode ?: vm.settings.readerModeEnabled)) }
    }
}

// ---------------------------------------------------------------- recall handle
/**
 * Shown while the pill is hidden. Tap or swipe up → show the pill. Swipe left/right → the handle follows the finger
 * and pins to the left, center or right third it is released in (persisted as Settings.recallPosition).
 */
@Composable
fun RecallButton(vm: BrowserViewModel, pill: PillState, navInsetDp: Float, screenW: Float, modifier: Modifier) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val w = 56f
    val margin = 16f
    fun baseX(pos: String) = when (pos) { "left" -> margin; "right" -> screenW - margin - w; else -> (screenW - w) / 2 }
    val x = remember { Animatable(baseX(vm.settings.recallPosition)) }
    LaunchedEffect(screenW) { x.snapTo(baseX(vm.settings.recallPosition)) }
    var horizontal by remember { mutableStateOf<Boolean?>(null) }
    var startX by remember { mutableFloatStateOf(0f) }
    val opacity = interp(pill.scrollY.value, listOf(0f, HIDDEN_TRANSLATE_Y - 20, HIDDEN_TRANSLATE_Y), listOf(0f, 0f, 1f))
    Box(modifier.offset { IntOffset((x.value * density).roundToInt(), 0) }.padding(bottom = maxOf(navInsetDp + 10f, 10f).dp).alpha(opacity)) {
        Box(
            Modifier.size(w.dp, 32.dp).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(ui.cs.secondaryContainer)
                .panGesture(
                    enabled = pill.isBarHidden, slopDp = 4f,
                    onStart = { startX = x.value; horizontal = null },
                    onMove = { dx, dy ->
                        if (horizontal == null) horizontal = abs(dx) > abs(dy)
                        if (horizontal == true) scope.launch(start = CoroutineStart.UNDISPATCHED) { x.snapTo((startX + dx).coerceIn(margin, screenW - margin - w)) }
                        else pill.recallMove(dy)
                    },
                    onRelease = { _, dy, _, vy ->
                        if (horizontal == true) {
                            val center = x.value + w / 2
                            val pos = when { center < screenW / 3 -> "left"; center > screenW * 2 / 3 -> "right"; else -> "center" }
                            vm.updateSettings { copy(recallPosition = pos) }
                            scope.launch { x.animateTo(baseX(pos), Motion.spatial()) }
                        } else pill.recallRelease(dy, vy)
                        horizontal = null
                    },
                    onTap = { pill.showBar() },
                ),
            contentAlignment = Alignment.Center,
        ) { Sym("keyboard_arrow_up", 24f, ui.cs.onSecondaryContainer) }
    }
}

// ---------------------------------------------------------------- overlay sheet (OverlaySheet.tsx + sheetPanResponder)
@Composable
fun OverlaySheet(
    title: String, height: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, snapDefault: Float, snapFull: Float, imeDp: Float,
    onClose: () -> Unit, scope: kotlinx.coroutines.CoroutineScope, content: @Composable () -> Unit,
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp)
    val h = height
    var start by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().zIndex(100f)) {
        Box(Modifier.fillMaxSize().background(ui.cs.scrim.copy(alpha = 0.32f)).blockTouches(onClose))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h.value.coerceAtLeast(0f).dp)
                .shadow(6.dp, shape).clip(shape).background(ui.cs.surfaceContainerLow).blockTouches(),
        ) {
            Column(
                Modifier.fillMaxWidth().background(ui.cs.surfaceContainerLow)
                    .panGesture(
                        enabled = true,
                        onStart = { start = h.value; scope.launch { h.stop() } },
                        onMove = { _, dy -> scope.launch(start = CoroutineStart.UNDISPATCHED) { h.snapTo(minOf(start - dy, snapFull)) } },
                        onRelease = { _, dy, _, vy ->
                            val final = start - dy
                            var target = when {
                                final > (snapFull + snapDefault) / 2 -> snapFull
                                final > snapDefault * 0.7f -> snapDefault
                                else -> 0f
                            }
                            if (vy < -0.4f) target = snapFull
                            if (vy > 0.4f) target = if (start > snapDefault + 1f && final > snapDefault * 0.6f) snapDefault else 0f
                            if (target == 0f) onClose() else scope.launch { h.animateTo(target, Motion.spatial()) }
                        },
                    )
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(vertical = 16.dp).size(32.dp, 4.dp).background(ui.cs.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(title, 28f, ui.cs.onSurface, ui.extrabold, Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onClose) { Sym("close", 24f, ui.cs.onSecondaryContainer) }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = imeDp.dp)) { content() }
        }
    }
}


// ---------------------------------------------------------------- find in page
@Composable
fun FindBar(imeDp: Float, onQuery: (String) -> Unit, onNext: (forward: Boolean) -> Unit, onClose: () -> Unit) {
    val ui = LocalUi.current
    val slide = remember { Animatable(100f) }
    val fr = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        slide.animateTo(0f, Motion.spatial())
        fr.requestFocus()
    }
    Box(Modifier.fillMaxSize().zIndex(100f)) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = imeDp.dp).offset { IntOffset(0, (slide.value * density).roundToInt()) }
                .fillMaxWidth().height(60.dp)
                .shadow(3.dp, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp))
                .background(ui.cs.surfaceContainer, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp)).blockTouches()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(44.dp).padding(end = 6.dp).background(ui.cs.surfaceContainerHighest, RoundedCornerShape(22.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("search", 20f, ui.cs.onSurfaceVariant, Modifier.padding(start = 8.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; onQuery(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(fr),
                    singleLine = true,
                    textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = (16 * ui.fontScale).sp),
                    cursorBrush = SolidColor(ui.cs.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotEmpty()) onNext(true) }),
                    decorationBox = { inner -> Box { if (text.isEmpty()) Txt("Find in page...", 16f, ui.cs.onSurfaceVariant, ui.semibold); inner() } },
                )
            }
            IconButton(onClick = { if (text.isNotEmpty()) onNext(false) }) { Sym("keyboard_arrow_up", 24f, ui.cs.onSurface) }
            IconButton(onClick = { if (text.isNotEmpty()) onNext(true) }) { Sym("keyboard_arrow_down", 24f, ui.cs.onSurface) }
            FilledTonalIconButton(onClick = onClose) { Sym("close", 22f, ui.cs.onSecondaryContainer) }
        }
    }
}

// ---------------------------------------------------------------- modal frame (RN <Modal transparent animationType="fade">)
@Composable
fun ModalFrame(visible: Boolean, onDismiss: () -> Unit, dismissOnScrim: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            @Suppress("DEPRECATION") window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        AnimatedVisibility(state, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).blockTouches { if (dismissOnScrim) onDismiss() }.imePadding(),
                contentAlignment = Alignment.Center, content = content,
            )
        }
    }
}

/** CustomAlert.tsx → M3 alert dialog */
@Composable
fun CustomAlert(data: AlertData?, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    var last by remember { mutableStateOf(data) }
    if (data != null) last = data
    val d = last ?: return
    ModalFrame(data != null, onDismiss, dismissOnScrim = true) {
        DialogCard(d.title, actions = {
            d.buttons.forEach { b -> DialogAction(b.text, destructive = b.style == "destructive") { b.onPress(); onDismiss() } }
        }) {
            Txt(d.message, 14f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 20f)
        }
    }
}

/** Confirm modal (index.tsx 2676-2825) */
@Composable
fun ConfirmDialog(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var last by remember { mutableStateOf<ConfirmData?>(null) }
    vm.confirm?.let { last = it }
    val c = last ?: return
    val message = when (c.type) {
        "history" -> "This will permanently delete history for: ${c.label}."
        "bgRefresh" -> "Enabling background refresh will reload all open tabs immediately when the app starts. This may consume significant battery and data."
        "wipeData" -> "This will delete ALL data including history, bookmarks, tabs, and settings. The app will be reset to a fresh state."
        else -> "This will restore all app settings to their default values. Your history and tabs will be preserved."
    }
    val action = when (c.type) { "resetSettings" -> "Reset"; "bgRefresh" -> "Enable"; "wipeData" -> "Wipe App"; else -> "Delete" }
    ModalFrame(vm.confirm != null, { vm.confirm = null }, dismissOnScrim = true) {
        DialogCard("Are you sure?", actions = {
            DialogAction("Cancel") { vm.confirm = null }
            DialogAction(action, destructive = c.type != "bgRefresh") { vm.confirmAction(c) }
        }) { Txt(message, 14f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 20f, scaled = false) }
    }
}

/** Rename tab modal (index.tsx 2827-2956) */
@Composable
fun RenameDialog(vm: BrowserViewModel) {
    val id = vm.renameTabId
    var text by remember(id) { mutableStateOf(vm.tabs.firstOrNull { it.id == id }?.title ?: "") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(id) { if (id != null) { delay(300); runCatching { fr.requestFocus() } } }
    ModalFrame(id != null, { vm.renameTabId = null }) {
        DialogCard("Edit Tab", actions = {
            DialogAction("Cancel") { vm.renameTabId = null }
            DialogAction("Save") { id?.let { vm.renameTab(it, text) } }
        }) { ClearableField(text, { text = it }, fr) }
    }
}

/** Bordered input with a clear (close-circle) button, used by modals. */
@Composable
fun ClearableField(value: String, onChange: (String) -> Unit, fr: FocusRequester = remember { FocusRequester() }, modifier: Modifier = Modifier, uri: Boolean = false) {
    val ui = LocalUi.current
    Row(
        modifier.fillMaxWidth().height(56.dp).background(ui.cs.surfaceContainerHighest, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr), singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = FontWeight.W600, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            keyboardOptions = if (uri) KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri) else KeyboardOptions.Default,
        )
        if (value.isNotEmpty()) Box(Modifier.touchable { onChange(""); fr.requestFocus() }) { Sym("cancel", 20f, ui.cs.onSurfaceVariant) }
    }
}

/** Search field used at the top of the sheet views ("Search Tabs..." etc.). */
@Composable
fun SheetSearchField(
    value: String, onChange: (String) -> Unit, placeholder: String, onFocus: () -> Unit, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    val fr = remember { FocusRequester() }
    Row(
        modifier.fillMaxWidth().height(56.dp).background(ui.cs.surfaceContainerHigh, RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("search", 24f, ui.cs.onSurfaceVariant, Modifier.padding(end = 12.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused) onFocus() }, singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            decorationBox = { inner -> Box { if (value.isEmpty()) Txt(placeholder, 16f, ui.cs.onSurfaceVariant, ui.semibold, scaled = false); inner() } },
        )
        if (value.isNotEmpty()) IconButton(onClick = { onChange(""); fr.requestFocus() }) { Sym("close", 22f, ui.cs.onSurfaceVariant) }
        else trailing?.invoke()
    }
}

// ---------------------------------------------------------------- context menu (index.tsx 2958-3169)
@Composable
fun ContextMenu(vm: BrowserViewModel, onSaveImage: (String) -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val focusInput = { vm.ui.tryEmit(UiEvent.FocusInput) }
    var last by remember { mutableStateOf(vm.contextMenu) }
    vm.contextMenu?.let { last = it }
    val d = last ?: return
    val target = d.url ?: d.imgUrl
    val close = { vm.contextMenu = null }
    ModalFrame(vm.contextMenu != null, close, dismissOnScrim = true) {
        DialogCard(null, actions = { DialogAction("Cancel", onClick = close) }) {
            Txt(d.text.ifEmpty { if (d.imgUrl != null) "Image" else "Link" }, 18f, ui.cs.onSurface, ui.semibold, Modifier.padding(bottom = 4.dp), maxLines = 2)
            Txt(target ?: "", 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 12.dp), maxLines = 1)
            @Composable
            fun Action(icon: String, label: String, onPress: () -> Unit) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).touchable(onClick = onPress).heightIn(min = 48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym(icon, 22f, ui.cs.onSurfaceVariant, Modifier.padding(end = 16.dp))
                    Txt(label, 15f, ui.cs.onSurface, ui.semibold)
                }
            }
            if (target != null) Action("open_in_new", "Open in New Tab") {
                if (target.startsWith("http://") || target.startsWith("https://")) vm.addNewTab(target)
                close()
            }
            Action("content_copy", "Copy to Address Bar") {
                if (target != null) { vm.activeView = "none"; focusInput(); vm.inputUrl = target }
                close()
            }
            Action("share", "Share") { target?.let { shareText(ctx, it) }; close() }
            if (d.imgUrl != null) Action("download", "Save Image") { onSaveImage(d.imgUrl) }
        }
    }
}

// ---------------------------------------------------------------- error page (renderError)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorPage(err: WebError, tabUrl: String?, onRetry: () -> Unit, onUpgrade: (() -> Unit)?, onProceed: () -> Unit) {
    val ui = LocalUi.current
    val desc = err.desc
    val code = err.code
    val isSsl = desc.contains("ERR_CERT") || desc.contains("ERR_SSL") || desc.contains("ssl_error") || code == -11 || code in -1206..-1200
    val isCleartext = desc.contains("ERR_CLEARTEXT_NOT_PERMITTED")
    val (title, text, icon) = when {
        desc.contains("ERR_NAME_NOT_RESOLVED") || code == -2 -> Triple("Site Not Found", "We couldn't find this site. Please check your spelling.", "search")
        desc.contains("ERR_INTERNET_DISCONNECTED") -> Triple("No Internet", "Please check your Wi-Fi or mobile data connection.", "wifi_off")
        desc.contains("ERR_CONNECTION_TIMED_OUT") || code == -8 -> Triple("Connection Timed Out", "The server took too long to respond.", "history")
        desc.contains("ERR_CONNECTION_REFUSED") -> Triple("Connection Refused", "The website refused the connection.", "block")
        isCleartext -> Triple("Security Error", "This site requires a secure HTTPS connection.", "lock")
        isSsl -> Triple("Security Warning", "The connection to this site is not secure.", "warning")
        else -> Triple("Can't Load Page", "Something went wrong while loading this website.", "error")
    }
    Column(
        Modifier.fillMaxSize().background(ui.cs.surface).blockTouches().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.padding(bottom = 24.dp).size(112.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Sym(icon, 56f, ui.cs.onSecondaryContainer)
        }
        Txt(title, 20f, ui.cs.onSurface, ui.extrabold, Modifier.padding(bottom = 10.dp), align = TextAlign.Center)
        Txt(tabUrl ?: "", 14f, ui.cs.primary, ui.bold, Modifier.fillMaxWidth(0.85f).padding(bottom = 10.dp), maxLines = 1, align = TextAlign.Center)
        Txt(text, 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(bottom = 30.dp), align = TextAlign.Center, scaled = false)
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Sym("refresh", 20f, ui.cs.onPrimary, Modifier.padding(end = 8.dp)); Txt("Try Again", 16f, ui.cs.onPrimary, ui.bold) }
        if (onUpgrade != null) FilledTonalButton(onClick = onUpgrade, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Sym("lock", 18f, ui.cs.onSecondaryContainer, Modifier.padding(end = 8.dp)); Txt("Upgrade to HTTPS", 15f, ui.cs.onSecondaryContainer, ui.bold, scaled = false)
        }
        if (isSsl || isCleartext) TextButton(onClick = onProceed, modifier = Modifier.padding(top = 12.dp)) {
            Txt("Proceed to ${displayHost(err.failingUrl ?: tabUrl)} (unsafe)", 14f, ui.cs.error, ui.semibold, align = TextAlign.Center)
        }
    }
}
