// The home-screen icon's look (Look & Feel › App Icon): mi's globe on its near-black background (Dark) or a near-white
// one (Light). Android takes an app's home-screen icon from its launcher entry, so the manifest has one entry per look
// (AppIconDark, AppIconLight), and the one picked is the one turned on. Both open LaunchActivity, which hands over to
// MainActivity: Android closes the task an entry opened when that entry is turned off, so mi's own task must never
// have come from one, or picking a look would close mi.
package com.jlees.mibrowser.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R
import com.jlees.mibrowser.data.AppIconStyle

/** What the home-screen entries open: MainActivity, started in its own task, then it goes (it never shows). */
class LaunchActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

/** The launcher entry (manifest activity-alias) that shows [style]'s icon. */
private fun entryOf(ctx: Context, style: AppIconStyle) = ComponentName(ctx.packageName, MainActivity::class.java.name.substringBeforeLast('.') + when (style) {
    AppIconStyle.DARK -> ".AppIconDark"
    AppIconStyle.LIGHT -> ".AppIconLight"
})

/**
 * Turns on [style]'s launcher entry and off the other's (nothing, if they already are). The new one goes on first, so
 * mi always has an icon. Android drops the long-press shortcuts with the entry they were under: they're published again.
 */
fun applyAppIcon(ctx: Context, style: AppIconStyle) {
    val pm = ctx.packageManager
    var changed = false
    AppIconStyle.entries.sortedBy { it != style }.forEach { s ->
        val c = entryOf(ctx, s)
        val now = pm.getComponentEnabledSetting(c)
        val on = now == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && s == AppIconStyle.DARK)
        if (on != (s == style)) {
            pm.setComponentEnabledSetting(c, if (s == style) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            changed = true
        }
    }
    if (changed) LauncherShortcuts.publish(ctx)
}

/**
 * [style]'s icon: its background colour resource (the adaptive icon's own, values.xml) and its foreground drawable (the
 * glyph, its cut-outs in that background).
 */
fun appIconLook(style: AppIconStyle): Pair<Int, Int> = when (style) {
    AppIconStyle.DARK -> R.color.icon_background to R.drawable.ic_launcher_foreground
    AppIconStyle.LIGHT -> R.color.icon_background_light to R.drawable.ic_launcher_foreground_light
}

/**
 * An app icon as the home screen shows it, [size] across: [foreground] (a 108dp adaptive-icon layer) on the colour
 * resource [background] (mi.'s icons: R.color.icon_background), cut to a circle. The layer's 72dp visible part fills
 * the circle, so it's drawn at 1.5 times [size].
 */
@Composable
fun AppIconPreview(foreground: Int, background: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(androidx.compose.ui.res.colorResource(background)), contentAlignment = Alignment.Center) {
        Image(painterResource(foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}
