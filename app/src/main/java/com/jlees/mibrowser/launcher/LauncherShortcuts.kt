// mi on the home screen: the shortcuts on its launcher icon, and sites pinned as their own home-screen icons.
package com.jlees.mibrowser.launcher

import com.jlees.mibrowser.incognito.incognitoSupported

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.displayHost

object LauncherShortcuts {
    /**
     * Long-press the launcher icon: New tab, New incognito tab (where WebView supports profiles) and Scan QR. Published
     * at runtime so they target this build's own package (the debug build has a different one).
     */
    fun publish(ctx: Context) {
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
        fun shortcut(id: String, label: String, icon: Int, action: String) =
            ShortcutInfo.Builder(ctx, id).setShortLabel(label).setIcon(icon(ctx, icon))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)).build()
        val list = listOfNotNull(
            shortcut("new_tab", "New tab", R.drawable.sym_add, MainActivity.ACTION_NEW_TAB),
            if (incognitoSupported) shortcut("new_incognito", "New incognito tab", R.drawable.sym_incognito, MainActivity.ACTION_NEW_INCOGNITO) else null,
            shortcut("scan_qr", "Scan QR code", R.drawable.sym_qr_code_scanner, MainActivity.ACTION_SCAN_QR), // same id as the old static one: pinned copies keep working
        )
        runCatching { sm.dynamicShortcuts = list }
    }

    /** Pins a home-screen shortcut that opens [url] in mi (uses the site's favicon when cached). */
    fun pinSite(ctx: Context, title: String, url: String) {
        val icon = Favicons.bitmapFor(url)?.let { androidx.core.graphics.drawable.IconCompat.createWithBitmap(it) }
            ?: androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, com.jlees.mibrowser.R.mipmap.ic_launcher)
        val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "site_" + url.hashCode())
            .setShortLabel(title.ifBlank { displayHost(url) }.take(25))
            .setIcon(icon)
            .setIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setClass(ctx, com.jlees.mibrowser.MainActivity::class.java))
            .build()
        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }

    /** A shortcut icon: the symbol on a tinted adaptive background, like the system's own shortcuts. */
    private fun icon(ctx: Context, res: Int): Icon {
        val d = ctx.resources.displayMetrics.density
        val px = (108 * d).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFD3E3FD.toInt())
        val glyph = (44 * d).toInt(); val o = (px - glyph) / 2
        ctx.getDrawable(res)!!.mutate().apply { setTint(0xFF0B57D0.toInt()); setBounds(o, o, o + glyph, o + glyph); draw(canvas) }
        return Icon.createWithAdaptiveBitmap(bmp)
    }
}
