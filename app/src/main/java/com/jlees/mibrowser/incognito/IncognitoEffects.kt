// What incognito changes on screen and in the keyboard: no screenshots while an incognito tab shows, the "close
// incognito tabs" notification, and keyboards asked not to learn from what's typed.
package com.jlees.mibrowser.incognito

import android.app.Activity
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import com.jlees.mibrowser.BrowserViewModel

/**
 * Incognito side effects: no screenshots / screen recording / recent-apps thumbnail while an incognito tab shows, and
 * the "close incognito tabs" notification. Its own composable, so tab-list updates (titles, loading) recompose only
 * this, not the whole app.
 */
@Composable
fun IncognitoEffects(vm: BrowserViewModel, activity: Activity) {
    // ---- incognito: no screenshots, screen recording or recent-apps thumbnail while an incognito tab is showing
    val incognitoShown = vm.tabs.current?.incognito == true
    val incognitoCount = vm.tabs.list.count { it.incognito }
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        IncognitoNotice.update(activity, vm.tabs.list.count { t -> t.incognito })
    }
    LaunchedEffect(incognitoCount) {
        // off the main thread: the binder calls would hold up the incognito wave's first frames
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IncognitoNotice.update(activity, incognitoCount) }
        // asked when the first incognito tab opens (Android stops asking after two refusals)
        if (incognitoCount > 0 && Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(incognitoShown) {
        if (incognitoShown) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * While [incognito] is true, keyboards are asked not to learn from anything typed in mi's own fields (the Pill,
 * dialogs). Page fields get the same flag from the incognito WebView (MiWebView).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoTextInput(incognito: () -> Boolean, content: @Composable () -> Unit) {
    InterceptPlatformTextInput({ request, next ->
        if (!incognito()) next.startInputMethod(request)
        else next.startInputMethod(PlatformTextInputMethodRequest { attrs ->
            request.createInputConnection(attrs).also { attrs.imeOptions = attrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
        })
    }, content)
}
