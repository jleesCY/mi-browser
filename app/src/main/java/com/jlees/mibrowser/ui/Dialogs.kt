// Modal dialogs: the frame they share, alerts, confirmations, renaming a tab and website sign-in.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ConfirmAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Button
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.AlertData
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- modal frame
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
                // the dialog stays clear of the status bar, navigation bar, cutouts and keyboard (DialogCard scrolls to fit)
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).blockTouches { if (dismissOnScrim) onDismiss() }
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center, content = content,
            )
        }
    }
}

/** Alert with a title, message and buttons (showAlert). */
@Composable
fun CustomAlert(data: AlertData?, onButton: () -> Unit, onCancel: () -> Unit) {
    val ui = LocalUi.current
    var last by remember { mutableStateOf(data) }
    if (data != null) last = data
    val d = last ?: return
    // 3+ buttons don't fit in one row: the last (main) action becomes a large full-width button, the rest go below it
    val stacked = d.buttons.size > 2
    ModalFrame(data != null, onCancel, dismissOnScrim = true) {
        // stacked: the main action is a full-width button in the body; the others sit in the actions row, side by side
        DialogCard(d.title, actions = {
            if (!stacked) d.buttons.forEach { b -> DialogAction(b.text, destructive = b.style == ButtonStyle.DESTRUCTIVE) { onButton(); b.onPress() } }
            else d.buttons.dropLast(1).forEach { b -> DialogAction(b.text, b.style == ButtonStyle.DESTRUCTIVE, Modifier.weight(1f)) { onButton(); b.onPress() } }
        }) {
            Txt(d.message, 14f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 20f)
            if (stacked) {
                val main = d.buttons.last()
                Button({ onButton(); main.onPress() }, Modifier.fillMaxWidth().padding(top = 24.dp).heightIn(min = 52.dp)) {
                    Text(main.text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Confirmation for destructive or costly actions (ConfirmAction). */
@Composable
fun ConfirmDialog(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var last by remember { mutableStateOf<ConfirmData?>(null) }
    vm.confirm?.let { last = it }
    val c = last ?: return
    val message = when (c.action) {
        ConfirmAction.CLEAR_HISTORY -> "This will permanently delete history for: ${c.label}."
        ConfirmAction.BACKGROUND_REFRESH -> "Enabling background refresh will reload all open tabs immediately when the app starts. This may consume significant battery and data."
        ConfirmAction.WIPE_DATA -> "This will delete ALL data including history, bookmarks, tabs, and settings. The app will be reset to a fresh state."
        ConfirmAction.CLEAR_DATA -> "This will delete cookies, site storage, cached files, saved site icons and tab snapshots. You will be signed out of websites."
        ConfirmAction.RESET_SETTINGS -> "This will restore all app settings to their default values. Your history and tabs will be preserved."
    }
    val action = when (c.action) {
        ConfirmAction.RESET_SETTINGS -> "Reset"; ConfirmAction.BACKGROUND_REFRESH -> "Enable"; ConfirmAction.WIPE_DATA -> "Wipe App"
        ConfirmAction.CLEAR_DATA -> "Clear"; ConfirmAction.CLEAR_HISTORY -> "Delete"
    }
    ModalFrame(vm.confirm != null, { vm.confirm = null }, dismissOnScrim = true) {
        DialogCard("Are you sure?", actions = {
            DialogAction("Cancel") { vm.confirm = null }
            DialogAction(action, destructive = c.action != ConfirmAction.BACKGROUND_REFRESH) { vm.confirmAction(c) }
        }) { Txt(message, 14f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 20f, scaled = false) }
    }
}

/** Renaming a tab (blank: back to the page's name). */
@Composable
fun RenameDialog(vm: BrowserViewModel) {
    val id = vm.renameTabId
    var text by remember(id) { mutableStateOf(vm.tabs.list.firstOrNull { it.id == id }?.title ?: "") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(id) { if (id != null) { delay(300); runCatching { fr.requestFocus() } } }
    ModalFrame(id != null, { vm.renameTabId = null }) {
        DialogCard("Edit Tab", actions = {
            DialogAction("Cancel") { vm.renameTabId = null }
            DialogAction("Save") { id?.let { vm.renameTab(it, text) } }
        }) { ClearableField(text, { text = it }, fr) }
    }
}

/** HTTP authentication: the site's username/password prompt. Dismissing it cancels the sign-in. */
@Composable
fun AuthDialog(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val req = vm.authRequest
    var last by remember { mutableStateOf(req) }
    if (req != null) last = req
    val r = last ?: return
    var user by remember(r) { mutableStateOf("") }
    var pass by remember(r) { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(req) { if (req != null) { delay(300); runCatching { fr.requestFocus() } } }
    val cancel = { if (vm.authRequest === r) { vm.authRequest = null; r.onCancel() } }
    ModalFrame(req != null, cancel) {
        DialogCard("Sign in", actions = {
            DialogAction("Cancel", onClick = cancel)
            DialogAction("Sign in") { if (vm.authRequest === r) { vm.authRequest = null; r.onSubmit(user, pass) } }
        }) {
            Txt("${r.host} requires a username and password.", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 16.dp), lineHeight = 20f)
            if (r.insecure) Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(12.dp)).background(ui.cs.errorContainer).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Sym("warning", 18f, ui.cs.onErrorContainer, Modifier.padding(end = 10.dp))
                Txt("Your connection to this site isn't private. The username and password will be sent unencrypted.", 13f, ui.cs.onErrorContainer, ui.semibold, lineHeight = 18f)
            }
            ClearableField(user, { user = it }, fr, placeholder = "Username", uri = true)
            Spacer(Modifier.height(8.dp))
            ClearableField(pass, { pass = it }, placeholder = "Password", password = true)
        }
    }
}
