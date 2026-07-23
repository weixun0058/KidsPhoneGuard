package com.kidsphoneguard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentProtectedActivityTest {

    @Test
    fun parentAccess_doesNotRelockBeforeFirstForegroundEntry() {
        assertFalse(
            ParentProtectedActivity.shouldRelockParentAccess(
                hasEnteredForeground = false,
                isFinishing = false
            )
        )
    }

    @Test
    fun parentAccess_relocksWhenForegroundPageIsPaused() {
        assertTrue(
            ParentProtectedActivity.shouldRelockParentAccess(
                hasEnteredForeground = true,
                isFinishing = false
            )
        )
    }

    @Test
    fun parentAccess_doesNotFinishTwiceDuringNormalExit() {
        assertFalse(
            ParentProtectedActivity.shouldRelockParentAccess(
                hasEnteredForeground = true,
                isFinishing = true
            )
        )
    }
}
