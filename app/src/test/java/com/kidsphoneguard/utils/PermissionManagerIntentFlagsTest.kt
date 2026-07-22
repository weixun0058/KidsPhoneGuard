package com.kidsphoneguard.utils

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionManagerIntentFlagsTest {

    @Test
    fun protectedSettingsLaunchFlags_recreateAndExcludeExternalTaskFromRecents() {
        val flags = PermissionManager.protectedSettingsLaunchFlags()

        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS != 0)
    }
}
