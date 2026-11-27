package com.jlees.mibrowser

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.jlees.mibrowser.data.AppIconStyle
import com.jlees.mibrowser.launcher.applyAppIcon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Look & Feel › App Icon: the look picked is the launcher entry turned on, and only that one (launcher/AppIcon). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppIconTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun state(entry: String) = ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx.packageName, "com.jlees.mibrowser.$entry"))

    @Test fun theIconPickedIsTheOneOnTheHomeScreen() {
        applyAppIcon(ctx, AppIconStyle.LIGHT)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("AppIconLight"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state("AppIconDark"))
        applyAppIcon(ctx, AppIconStyle.DARK)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("AppIconDark"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state("AppIconLight"))
    }

    @Test fun theEntriesOpenTheHandOffNotMi() {
        // turning off the entry that opened mi's task would close mi: the entries open LaunchActivity, which starts mi apart
        for (entry in listOf("AppIconDark", "AppIconLight")) {
            val info = ctx.packageManager.getActivityInfo(ComponentName(ctx.packageName, "com.jlees.mibrowser.$entry"), PackageManager.MATCH_DISABLED_COMPONENTS)
            assertEquals(com.jlees.mibrowser.launcher.LaunchActivity::class.java.name, info.targetActivity)
        }
    }
}
