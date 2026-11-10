// The new-tab page: logo, clock, date and an optional shortcut button (set in Settings → Home Page).
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.home


import com.jlees.mibrowser.data.HomeClock
import com.jlees.mibrowser.data.HomeDate
import com.jlees.mibrowser.data.HomeLogo
import com.jlees.mibrowser.data.HomeShortcut

import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.blockTouches
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomePage(vm: BrowserViewModel, onAction: (HomeShortcut) -> Unit) {
    val ui = LocalUi.current
    val s = vm.settings
    val focusManager = LocalFocusManager.current

    // clock
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(s.homeClockType, s.homeDateType) {
        if (s.homeClockType == HomeClock.NONE && s.homeDateType == HomeDate.NONE) return@LaunchedEffect
        // the clock shows minutes: wake on each minute boundary, and only while mi is on screen
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(60_000 - now % 60_000) }
        }
    }

    val incognito = vm.tabs.current?.incognito == true
    // incognito: a slightly deeper, tinted page. Toggling it sends a wave of that colour out from the centre; toggling
    // back draws the wave back in. Keyed to the tab, so switching to an incognito tab just shows it (no wave).
    val wave = remember(vm.tabs.activeId) { Animatable(if (incognito) 1f else 0f) }
    LaunchedEffect(incognito) { wave.animateTo(if (incognito) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 240f)) }
    val waveColor = homeBackground(true)
    val ringColor = ui.cs.primary
    Box(Modifier.fillMaxSize().background(homeBackground(false)).drawBehind {
        val p = wave.value
        if (p <= 0f) return@drawBehind
        val full = kotlin.math.hypot(size.width, size.height) / 2f // centre to corner
        val r = (full * p).coerceAtLeast(0f)
        drawCircle(waveColor, r)
        // the wave's leading edge: a soft accent ring, strongest mid-way, gone at rest
        val edge = kotlin.math.sin(p.coerceIn(0f, 1f) * Math.PI.toFloat())
        if (edge > 0.01f) drawCircle(ringColor.copy(alpha = 0.22f * edge), r + 10.dp.toPx() * edge, style = Stroke(14.dp.toPx() * edge))
    }.blockTouches { focusManager.clearFocus() }) {
        // top widgets
        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            val dateText = @Composable { Txt(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)), 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(vertical = 2.dp)) }
            if (s.homeClockType != HomeClock.NONE) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.homeDateType == HomeDate.ABOVE) dateText()
                Txt(SimpleDateFormat(if (s.homeClockType == HomeClock.H12) "hh:mm a" else "HH:mm", Locale.getDefault()).format(Date(now)), 72f, ui.cs.onSurface, ui.bold)
                if (s.homeDateType == HomeDate.BELOW) dateText()
            } else if (s.homeDateType != HomeDate.NONE) dateText()

        }

        // centre: a fidgetable mask on incognito tabs, the logo otherwise; they pop in and out with the wave
        AnimatedContent(
            incognito, Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 60)) + scaleIn(Motion.fastSpatial(), initialScale = 0.4f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.4f))
            },
            contentAlignment = Alignment.Center, label = "home centre",
        ) { inc ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (inc) FidgetLogo { IncognitoMark() }
                else if (s.homeLogoType == HomeLogo.FIDGET) FidgetLogo { MiLogo() }
                else if (s.homeLogoType != HomeLogo.NONE) MiLogo()
            }
        }

        // shortcut
        if (s.showHomeShortcuts) {
            val shortcut = s.homeShortcutAction
            val (icon, label) = shortcut.icon to shortcut.short
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
fun MiLogo() {
    val ui = LocalUi.current
    Txt("mi.", 60f, ui.cs.onSurface, ui.extrabold, Modifier.alpha(0.9f), letterSpacing = -1f, font = sansFlex(true))
}

@Composable
fun IncognitoMark() = Sym("incognito", 88f, LocalUi.current.cs.onSurface.copy(alpha = 0.9f))

/** The blank page's background: [incognito] tabs get a slightly deeper tint. Light mode mixes in a little accent,
 *  since its near-white surfaces otherwise read as colourless next to dark mode's. */
@Composable
fun homeBackground(incognito: Boolean): Color {
    val ui = LocalUi.current
    val base = if (incognito) ui.cs.surfaceContainer else ui.cs.surface
    return if (ui.isDark) base else androidx.compose.ui.graphics.lerp(base, ui.cs.primaryContainer, if (incognito) 0.35f else 0.15f)
}

/** Drag it around; it springs back. */
@Composable
private fun FidgetLogo(content: @Composable () -> Unit) {
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
    ) { content() }
}
