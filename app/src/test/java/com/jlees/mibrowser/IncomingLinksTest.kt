package com.jlees.mibrowser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A link sent from another app (ACTION_VIEW): its new tab is the one that shows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncomingLinksTest {
    @Test fun aLinkFromAnotherAppShowsOverTheTabSwitcher() {
        val vm = BrowserViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.switcherVisible = true; vm.isSubMenuVisible = true
        vm.handleIncomingUrl("https://example.com/")
        // it used to open behind the switcher, which kept showing the tab that was current before
        assertFalse(vm.switcherVisible); assertFalse(vm.isSubMenuVisible)
        assertEquals("https://example.com/", vm.tabs.current?.url)
    }
}
