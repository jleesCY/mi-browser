// Port of legacy/src/components/HomePage/HomePage.tsx (weather widget intentionally removed in 1.0)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.ui.home

import kotlinx.coroutines.CoroutineStart
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.blockTouches
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.panGesture
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomePage(vm: BrowserViewModel, onAction: (String) -> Unit) {
    val ui = LocalUi.current
    val s = vm.settings
    val focusManager = LocalFocusManager.current

    // clock
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.homeClockType, s.homeDateType) {
        if (s.homeClockType == "None" && s.homeDateType == "None") return@LaunchedEffect
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }

    Box(Modifier.fillMaxSize().background(ui.cs.surface).blockTouches { focusManager.clearFocus() }) {
        s.homeBackgroundImage?.let { bg ->
            NetImage(bg, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.homeBackgroundOverlayOpacity)))
        }

        // top widgets
        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            val dateText = @Composable { Txt(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)), 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(vertical = 2.dp)) }
            if (s.homeClockType != "None") Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.homeDateType == "Above") dateText()
                Txt(SimpleDateFormat(if (s.homeClockType == "12h") "hh:mm a" else "HH:mm", Locale.getDefault()).format(Date(now)), 72f, ui.cs.onSurface, ui.bold)
                if (s.homeDateType == "Below") dateText()
            } else if (s.homeDateType != "None") dateText()

        }

        // center logo
        if (s.homeLogoType != "None") Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (s.homeLogoType == "Fidget") FidgetLogo() else Txt("mi.", 60f, ui.cs.onSurface, ui.extrabold, Modifier.alpha(0.9f), letterSpacing = -1f, font = sansFlex(true))
        }

        // shortcut
        if (s.showHomeShortcuts) {
            val (icon, label) = when (s.homeShortcutAction) {
                "newTab" -> "add" to "New Tab"
                "bookmarks" -> "bookmarks" to "Saved"
                "history" -> "history" to "Recent"
                else -> "qr_code_scanner" to "Scan"
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).touchable { onAction(s.homeShortcutAction) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(72.dp).shadow(3.dp, MaterialShapes.Cookie9Sided.toShape()).clip(MaterialShapes.Cookie9Sided.toShape()).background(ui.cs.primaryContainer), contentAlignment = Alignment.Center) { Sym(icon, 30f, ui.cs.onPrimaryContainer) }
                Txt(label, 12f, ui.cs.onSurface, ui.bold)
            }
        }
    }
}

@Composable
private fun FidgetLogo() {
    val ui = LocalUi.current
    val scale = remember { Animatable(1f) }
    val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .offset { IntOffset((pan.value.x * density).roundToInt(), (pan.value.y * density).roundToInt()) }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .panGesture(
                true, claimImmediately = true,
                onStart = { scope.launch { scale.animateTo(1.2f, Motion.spatial()) } },
                onMove = { dx, dy -> scope.launch(start = CoroutineStart.UNDISPATCHED) { pan.snapTo(Offset(dx, dy)) } },
                onRelease = { _, _, _, _ ->
                    scope.launch { pan.animateTo(Offset.Zero, Motion.playful()) }
                    scope.launch { scale.animateTo(1f, Motion.playful()) }
                },
            ),
    ) { Txt("mi.", 60f, ui.cs.onSurface, ui.extrabold, Modifier.alpha(0.9f), letterSpacing = -1f, font = sansFlex(true)) }
}
