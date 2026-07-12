package com.kidsphoneguard.service.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatFinderGuardTest {

    @Test
    fun finderActivityShapeMatchesOnlyFinderUi() {
        assertTrue(
            WeChatFinderGuard.isFinderActivityClass(
                "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI"
            )
        )
        assertFalse(WeChatFinderGuard.isFinderActivityClass("com.tencent.mm.ui.LauncherUI"))
        assertFalse(WeChatFinderGuard.isFinderActivityClass("android.widget.FrameLayout"))
    }
}
