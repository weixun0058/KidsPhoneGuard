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

    @Test
    fun sessionRejectsDuplicateArmWhileActionIsPending() {
        val state = WeChatFinderSessionState()

        assertTrue(state.tryArm() != null)
        assertTrue(state.tryArm() == null)
    }

    @Test
    fun sessionCanRearmImmediatelyAfterCompletedAction() {
        val state = WeChatFinderSessionState()
        val firstSession = requireNotNull(state.tryArm())

        assertTrue(state.complete(firstSession))
        assertTrue(state.tryArm() != null)
    }

    @Test
    fun staleCompletionCannotReleaseNewerSession() {
        val state = WeChatFinderSessionState()
        val firstSession = requireNotNull(state.tryArm())
        assertTrue(state.complete(firstSession))
        val secondSession = requireNotNull(state.tryArm())

        assertFalse(state.complete(firstSession))
        assertTrue(state.complete(secondSession))
    }
}
