// The page shown when a site can't load, with retry and (for certificate errors) proceed.
package com.jlees.mibrowser.ui

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.web.WebError

// ---------------------------------------------------------------- error page
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorPage(err: WebError, tabUrl: String?, onRetry: () -> Unit, onUpgrade: (() -> Unit)?, onProceed: () -> Unit, onContinueHttp: () -> Unit) {
    val ui = LocalUi.current
    val desc = err.desc
    val code = err.code
    val isSsl = desc.contains("ERR_CERT") || desc.contains("ERR_SSL") || desc.contains("ssl_error") || code == -11 || code in -1206..-1200
    val isCleartext = desc.contains("ERR_CLEARTEXT_NOT_PERMITTED")
    val httpsOnly = code == WebError.HTTPS_ONLY
    val (title, text, icon) = when {
        httpsOnly -> Triple("Secure Connection Not Available", "This site doesn't offer a secure (https) connection. Others on your network could see or change what you send there.", "lock")
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
        if (httpsOnly) TextButton(onClick = onContinueHttp, modifier = Modifier.padding(top = 12.dp)) {
            Txt("Continue to ${displayHost(err.failingUrl ?: tabUrl)} (not secure)", 14f, ui.cs.error, ui.semibold, align = TextAlign.Center)
        }
        if (isSsl || isCleartext) TextButton(onClick = onProceed, modifier = Modifier.padding(top = 12.dp)) {
            Txt("Proceed to ${displayHost(err.failingUrl ?: tabUrl)} (unsafe)", 14f, ui.cs.error, ui.semibold, align = TextAlign.Center)
        }
    }
}
