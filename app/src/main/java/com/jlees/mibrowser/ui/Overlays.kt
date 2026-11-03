// Ports: submenu + context menu + confirm/rename modals (legacy/app/index.tsx), OverlaySheet.tsx, FindOnPageBar.tsx,
// CustomAlert.tsx, renderError (BrowserWebView.tsx).
package com.jlees.mibrowser.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
fun SubMenu(vm: BrowserViewModel, modifier: Modifier, @Suppress("UNUSED_PARAMETER") onAction: (String) -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val hasUrl = vm.activeUrl != null
    val tab = vm.currentTab
    val shape = RoundedCornerShape(ui.radius.dp)
    @Composable
    fun Item(icon: String, label: String, needsUrl: Boolean, last: Boolean = false, onPress: () -> Unit) {
        val enabled = !needsUrl || hasUrl
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).touchable(enabled, onClick = onPress).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ion(icon, 20f, ui.text, Modifier.padding(end = 12.dp))
            Txt(label, 14f, ui.text, FontWeight.W700)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
    }
    @Composable
    fun Toggle(icon: String, label: String, value: Boolean, last: Boolean, onPress: () -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).touchable(onClick = onPress).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Ion(icon, 20f, ui.text, Modifier.padding(end = 12.dp))
            Txt(label, 14f, ui.text, FontWeight.W700, Modifier.weight(1f))
            MiSwitch(value, null)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
    }
    Column(modifier.width(240.dp).zIndex(10f).shadow(20.dp, shape).clip(shape).background(ui.glass).blockTouches()) {
        Item("home-outline", "Home", false) { vm.isSubMenuVisible = false; vm.goHome() }
        Item("bookmark-outline", "Bookmark", true) { vm.isSubMenuVisible = false; vm.bookmarksAutoAdd = true; vm.openView("bookmarks") }
        Item("share-social-outline", "Share", true) { vm.activeUrl?.let { shareText(ctx, it, "Share Link") }; vm.isSubMenuVisible = false }
        Item("search-outline", "Find in Page", true) { vm.isSubMenuVisible = false; vm.findVisible = true }
        Item("qr-code-outline", "Scan QR Code", false) { vm.isSubMenuVisible = false; vm.qrScannerVisible = true }
        Item("barcode-outline", "Generate QR Code", true) { vm.isSubMenuVisible = false; vm.qrGeneratorVisible = true }
        Item("print-outline", "Print", true) { vm.isSubMenuVisible = false; vm.web.tryEmit(WebCmd.Print) }
        Toggle("desktop-outline", "Desktop Mode", tab?.desktopMode ?: vm.settings.desktopMode, false) { vm.setDesktopMode(!(tab?.desktopMode ?: vm.settings.desktopMode)) }
        Toggle("book-outline", "Reader Mode", tab?.readerMode ?: vm.settings.readerModeEnabled, true) { vm.setReaderMode(!(tab?.readerMode ?: vm.settings.readerModeEnabled)) }
    }
}

// ---------------------------------------------------------------- recall handle
@Composable
fun RecallButton(pill: PillState, navInsetDp: Float, modifier: Modifier) {
    val ui = LocalUi.current
    val opacity = interp(pill.scrollY.value, listOf(0f, HIDDEN_TRANSLATE_Y - 20, HIDDEN_TRANSLATE_Y), listOf(0f, 0f, 1f))
    Box(modifier.padding(bottom = maxOf(navInsetDp + 10f, 10f).dp).alpha(opacity)) {
        Box(
            Modifier.size(50.dp, 30.dp).clip(RoundedCornerShape(25.dp)).background(ui.inputBg)
                .panGesture(
                    enabled = pill.isBarHidden, slopDp = 2f,
                    onMove = { _, dy -> pill.recallMove(dy) },
                    onRelease = { _, dy, _, vy -> pill.recallRelease(dy, vy) },
                    onTap = { pill.showBar() },
                ),
            contentAlignment = Alignment.Center,
        ) { Ion("chevron-up", 24f, ui.text) }
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
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).blockTouches(onClose))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h.value.coerceAtLeast(0f).dp)
                .shadow(12.dp, shape).clip(shape).background(ui.surface).blockTouches(),
        ) {
            Column(
                Modifier.fillMaxWidth().background(ui.sheetHeader)
                    .panGesture(
                        enabled = true, slopDp = 5f,
                        onStart = { start = h.value; scope.launch { h.stop() } },
                        onMove = { _, dy -> scope.launch { h.snapTo(minOf(start - dy, snapFull)) } },
                        onRelease = { _, dy, _, vy ->
                            val final = start - dy
                            var target = when {
                                final > (snapFull + snapDefault) / 2 -> snapFull
                                final > snapDefault * 0.7f -> snapDefault
                                else -> 0f
                            }
                            if (vy < -1) target = snapFull
                            if (vy > 1) target = if (final > snapDefault) snapDefault else 0f
                            if (target == 0f) onClose() else scope.launch { h.animateTo(target, springT50F8()) }
                        },
                    )
                    .padding(top = 15.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(vertical = 10.dp).size(40.dp, 5.dp).background(ui.text.copy(alpha = 0.3f), RoundedCornerShape(2.5.dp)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(title, 24f, ui.text, ui.extrabold, Modifier.weight(1f))
                    Box(Modifier.touchable(onClick = onClose).padding(5.dp)) { Ion("close-circle", 28f, ui.accent) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg.copy(alpha = 0.5f)))
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = imeDp.dp)) { content() }
        }
    }
}


// ---------------------------------------------------------------- find in page
@Composable
fun FindBar(vm: BrowserViewModel, imeDp: Float, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val ui = LocalUi.current
    val slide = remember { Animatable(100f) }
    val fr = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        slide.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.824f, stiffness = 230.2f))
        fr.requestFocus()
    }
    Box(Modifier.fillMaxSize().zIndex(100f)) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = imeDp.dp).offset { IntOffset(0, (slide.value * density).roundToInt()) }
                .fillMaxWidth().height(60.dp)
                .shadow(4.dp, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp))
                .background(ui.surface, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp)).blockTouches()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(40.dp).padding(end = 10.dp).background(ui.inputBg, RoundedCornerShape(maxOf(ui.radius - 4, 0).dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ion("search", 20f, ui.textSec, Modifier.padding(start = 8.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; onQuery(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(fr),
                    singleLine = true,
                    textStyle = TextStyle(color = ui.text, fontFamily = Nunito, fontWeight = ui.semibold, fontSize = (16 * ui.fontScale).sp),
                    cursorBrush = SolidColor(ui.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotEmpty()) vm.web.tryEmit(WebCmd.Find(text, true)) }),
                    decorationBox = { inner -> Box { if (text.isEmpty()) Txt("Find in page...", 16f, ui.textSec, ui.semibold); inner() } },
                )
            }
            Box(Modifier.touchable { if (text.isNotEmpty()) vm.web.tryEmit(WebCmd.Find(text, false)) }.padding(4.dp)) { Ion("chevron-up", 24f, ui.text) }
            Box(Modifier.touchable { if (text.isNotEmpty()) vm.web.tryEmit(WebCmd.Find(text, true)) }.padding(4.dp)) { Ion("chevron-down", 24f, ui.text) }
            Box(Modifier.padding(start = 8.dp).touchable(onClick = onClose).padding(4.dp)) { Ion("close", 24f, ui.textSec) }
        }
    }
}

// ---------------------------------------------------------------- modal frame (RN <Modal transparent animationType="fade">)
@Composable
fun ModalFrame(visible: Boolean, onDismiss: () -> Unit, scrim: Float = 0.5f, dismissOnScrim: Boolean = false, content: @Composable BoxScope.() -> Unit) {
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
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)).blockTouches { if (dismissOnScrim) onDismiss() }.imePadding(),
                contentAlignment = Alignment.Center, content = content,
            )
        }
    }
}

/** CustomAlert.tsx */
@Composable
fun CustomAlert(data: AlertData?, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    var last by remember { mutableStateOf(data) }
    if (data != null) last = data
    val d = last ?: return
    ModalFrame(data != null, onDismiss) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 320.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)).background(ui.surface, RoundedCornerShape(20.dp)).blockTouches().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(d.title, 18f, ui.text, ui.bold, Modifier.padding(bottom = 10.dp), align = TextAlign.Center)
            Txt(d.message, 14f, ui.textSec, ui.regular, Modifier.padding(bottom = 24.dp), align = TextAlign.Center, lineHeight = 22f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                d.buttons.forEach { b ->
                    val base = Modifier.weight(1f).defaultMinSize(minWidth = 80.dp).clip(RoundedCornerShape(10.dp))
                    val styled = when (b.style) {
                        "cancel" -> base.border(1.dp, ui.textSec, RoundedCornerShape(10.dp))
                        "destructive" -> base.background(Red)
                        else -> base.background(ui.accent)
                    }
                    Box(styled.touchable { b.onPress(); onDismiss() }.padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Txt(b.text, 16f, if (b.style == "cancel") ui.text else Color.White, ui.bold, align = TextAlign.Center)
                    }
                }
            }
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
    ModalFrame(vm.confirm != null, { vm.confirm = null }) {
        Column(Modifier.fillMaxWidth(0.8f).shadow(5.dp, RoundedCornerShape(ui.radius.dp)).background(ui.surface, RoundedCornerShape(ui.radius.dp)).blockTouches().padding(20.dp)) {
            Txt("Are you sure?", 20f, ui.text, FontWeight.W700, Modifier.padding(bottom = 15.dp), scaled = false)
            Txt(message, 16f, ui.textSec, FontWeight.W600, Modifier.padding(bottom = 20.dp), scaled = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.touchable { vm.confirm = null }.padding(horizontal = 15.dp, vertical = 10.dp)) { Txt("Cancel", 14f, ui.textSec, FontWeight.W700, scaled = false) }
                Box(Modifier.padding(start = 10.dp).background(Color(0xFFFF3B30), RoundedCornerShape((ui.radius / 2f).dp)).touchable { vm.confirmAction(c) }.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Txt(action, 14f, Color.White, FontWeight.W700, scaled = false)
                }
            }
        }
    }
}

/** Rename tab modal (index.tsx 2827-2956) */
@Composable
fun RenameDialog(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val id = vm.renameTabId
    var text by remember(id) { mutableStateOf(vm.tabs.firstOrNull { it.id == id }?.title ?: "") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(id) { if (id != null) { delay(300); runCatching { fr.requestFocus() } } }
    ModalFrame(id != null, { vm.renameTabId = null }) {
        Column(Modifier.fillMaxWidth(0.8f).background(ui.surface, RoundedCornerShape(ui.radius.dp)).blockTouches().padding(20.dp)) {
            Txt("Edit Tab", 20f, ui.text, FontWeight.W700, Modifier.padding(bottom = 15.dp), scaled = false)
            ClearableField(text, { text = it }, fr, Modifier.padding(bottom = 20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.touchable { vm.renameTabId = null }.padding(horizontal = 15.dp, vertical = 10.dp)) { Txt("Cancel", 14f, ui.textSec, FontWeight.W700, scaled = false) }
                Box(Modifier.padding(start = 10.dp).background(ui.accent, RoundedCornerShape((ui.radius / 2f).dp)).touchable { id?.let { vm.renameTab(it, text) } }.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Txt("Save", 14f, Color.White, FontWeight.W700, scaled = false)
                }
            }
        }
    }
}

/** Bordered input with a clear (close-circle) button, used by modals. */
@Composable
fun ClearableField(value: String, onChange: (String) -> Unit, fr: FocusRequester = remember { FocusRequester() }, modifier: Modifier = Modifier, uri: Boolean = false) {
    val ui = LocalUi.current
    Row(
        modifier.fillMaxWidth().height(48.dp).background(ui.inputBg, RoundedCornerShape((ui.radius / 2f).dp))
            .border(1.dp, Color(0x33808080), RoundedCornerShape((ui.radius / 2f).dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr), singleLine = true,
            textStyle = TextStyle(color = ui.text, fontFamily = Nunito, fontWeight = FontWeight.W600, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.accent),
            keyboardOptions = if (uri) KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri) else KeyboardOptions.Default,
        )
        if (value.isNotEmpty()) Box(Modifier.touchable { onChange(""); fr.requestFocus() }) { Ion("close-circle", 20f, ui.textSec) }
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
        modifier.fillMaxWidth().height(50.dp).background(ui.bg, RoundedCornerShape(ui.radius.dp)).border(1.dp, ui.card, RoundedCornerShape(ui.radius.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ion("search", 20f, ui.textSec, Modifier.padding(end = 10.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused) onFocus() }, singleLine = true,
            textStyle = TextStyle(color = ui.text, fontFamily = Nunito, fontWeight = ui.semibold, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.accent),
            decorationBox = { inner -> Box { if (value.isEmpty()) Txt(placeholder, 16f, ui.textSec, ui.semibold, scaled = false); inner() } },
        )
        if (value.isNotEmpty()) Box(Modifier.touchable { onChange(""); fr.requestFocus() }) { Ion("close-circle", 20f, ui.textSec) }
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
    ModalFrame(vm.contextMenu != null, close, scrim = 0.4f, dismissOnScrim = true) {
        Column(
            Modifier.fillMaxWidth(0.7f).widthIn(max = 280.dp).shadow(10.dp, RoundedCornerShape(ui.radius.dp)).clip(RoundedCornerShape(ui.radius.dp)).background(ui.surface).blockTouches(),
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Txt(d.text.ifEmpty { if (d.imgUrl != null) "Image" else "Link" }, 14f, ui.text, FontWeight.W700, Modifier.padding(bottom = 2.dp), maxLines = 2)
                Txt(target ?: "", 11f, ui.textSec, FontWeight.W600, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
            @Composable
            fun Action(icon: String, label: String, onPress: () -> Unit) {
                Row(Modifier.fillMaxWidth().touchable(onClick = onPress).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ion(icon, 18f * ui.fontScale, ui.text, Modifier.padding(end = 10.dp))
                    Txt(label, 14f, ui.text, FontWeight.W600)
                }
            }
            Column(Modifier.padding(vertical = 2.dp)) {
                if (target != null) Action("open-outline", "Open in New Tab") {
                    if (target.startsWith("http://") || target.startsWith("https://")) vm.addNewTab(target)
                    close()
                }
                Action("copy-outline", "Copy to Address Bar") {
                    if (target != null) { vm.activeView = "none"; focusInput(); vm.inputUrl = target }
                    close()
                }
                Action("share-social-outline", "Share") { target?.let { shareText(ctx, it) }; close() }
                if (d.imgUrl != null) Action("download-outline", "Save Image") { onSaveImage(d.imgUrl) }
            }
            Box(Modifier.fillMaxWidth().background(ui.card).touchable(onClick = close).padding(12.dp), contentAlignment = Alignment.Center) {
                Txt("Cancel", 13f, ui.textSec, FontWeight.W700)
            }
        }
    }
}

// ---------------------------------------------------------------- error page (renderError)
@Composable
fun ErrorPage(err: WebError, tabUrl: String?, onRetry: () -> Unit, onUpgrade: (() -> Unit)?, onProceed: () -> Unit) {
    val ui = LocalUi.current
    val desc = err.desc
    val code = err.code
    val isSsl = desc.contains("ERR_CERT") || desc.contains("ERR_SSL") || desc.contains("ssl_error") || code == -11 || code in -1206..-1200
    val isCleartext = desc.contains("ERR_CLEARTEXT_NOT_PERMITTED")
    val (title, text, icon) = when {
        desc.contains("ERR_NAME_NOT_RESOLVED") || code == -2 -> Triple("Site Not Found", "We couldn't find this site. Please check your spelling.", "search-outline")
        desc.contains("ERR_INTERNET_DISCONNECTED") -> Triple("No Internet", "Please check your Wi-Fi or mobile data connection.", "wifi-outline")
        desc.contains("ERR_CONNECTION_TIMED_OUT") || code == -8 -> Triple("Connection Timed Out", "The server took too long to respond.", "time-outline")
        desc.contains("ERR_CONNECTION_REFUSED") -> Triple("Connection Refused", "The website refused the connection.", "hand-left-outline")
        isCleartext -> Triple("Security Error", "This site requires a secure HTTPS connection.", "lock-closed-outline")
        isSsl -> Triple("Security Warning", "The connection to this site is not secure.", "warning-outline")
        else -> Triple("Can't Load Page", "Something went wrong while loading this website.", "alert-circle-outline")
    }
    Column(
        Modifier.fillMaxSize().background(ui.bg).blockTouches().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Ion(icon, 64f, ui.textSec, Modifier.padding(bottom = 20.dp))
        Txt(title, 20f, ui.text, ui.extrabold, Modifier.padding(bottom = 10.dp), align = TextAlign.Center)
        Txt(tabUrl ?: "", 14f, ui.accent, ui.bold, Modifier.fillMaxWidth(0.85f).padding(bottom = 10.dp), maxLines = 1, align = TextAlign.Center)
        Txt(text, 14f, ui.textSec, ui.semibold, Modifier.padding(bottom = 30.dp), align = TextAlign.Center, scaled = false)
        Box(Modifier.background(ui.accent, RoundedCornerShape(12.dp)).touchable(onClick = onRetry).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Txt("Try Again", 16f, Color.White, ui.bold)
        }
        if (onUpgrade != null) Box(Modifier.padding(top = 15.dp).background(ui.card, RoundedCornerShape(12.dp)).touchable(onClick = onUpgrade).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Txt("Upgrade to HTTPS", 16f, ui.text, ui.bold, scaled = false)
        }
        if (isSsl || isCleartext) Box(Modifier.padding(top = 15.dp).touchable(onClick = onProceed).padding(10.dp)) {
            Txt("Proceed to ${displayHost(err.failingUrl ?: tabUrl)} (unsafe)", 14f, ui.textSec, ui.regular, align = TextAlign.Center, underline = true)
        }
    }
}
