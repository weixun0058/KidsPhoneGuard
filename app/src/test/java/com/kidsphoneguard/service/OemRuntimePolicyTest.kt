package com.kidsphoneguard.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemRuntimePolicyTest {

    @Test
    fun xiaomiRuntimeTuningDoesNotChangeHonorSweepPath() {
        assertTrue(OemRuntimePolicy.isXiaomiFamily("Xiaomi", "Redmi"))
        assertFalse(OemRuntimePolicy.isXiaomiFamily("HONOR", "HONOR"))
        assertEquals(
            1_000L,
            OemRuntimePolicy.protectedWindowSweepIntervalMs(isXiaomiFamilyDevice = true)
        )
        assertEquals(
            180L,
            OemRuntimePolicy.protectedWindowSweepIntervalMs(isXiaomiFamilyDevice = false)
        )
        assertFalse(
            OemRuntimePolicy.shouldRunHuaweiSpecificWindowGuards(isXiaomiFamilyDevice = true)
        )
        assertTrue(
            OemRuntimePolicy.shouldRunHuaweiSpecificWindowGuards(isXiaomiFamilyDevice = false)
        )
    }

    @Test
    fun xiaomiLauncherEventResetsEarlierTrackableApp() {
        val resolution = OemRuntimePolicy.resolveUsageForegroundEvent(
            latestForegroundPackage = "com.miui.home",
            latestTrackablePackage = "com.focustodo.focus_todo",
            isXiaomiFamilyDevice = true
        )

        assertEquals(UsageForegroundEventResolutionType.RESET_FOREGROUND, resolution.type)
        assertEquals("com.miui.home", resolution.packageName)
    }

    @Test
    fun honorKeepsExistingLatestTrackableAppSemantics() {
        val resolution = OemRuntimePolicy.resolveUsageForegroundEvent(
            latestForegroundPackage = "com.hihonor.android.launcher",
            latestTrackablePackage = "com.example.app",
            isXiaomiFamilyDevice = false
        )

        assertEquals(UsageForegroundEventResolutionType.USE_TRACKABLE_PACKAGE, resolution.type)
        assertEquals("com.example.app", resolution.packageName)
    }

    @Test
    fun xiaomiTrackableForegroundStillTriggersNormalPolicy() {
        val resolution = OemRuntimePolicy.resolveUsageForegroundEvent(
            latestForegroundPackage = "com.focustodo.focus_todo",
            latestTrackablePackage = "com.focustodo.focus_todo",
            isXiaomiFamilyDevice = true
        )

        assertEquals(UsageForegroundEventResolutionType.USE_TRACKABLE_PACKAGE, resolution.type)
        assertEquals("com.focustodo.focus_todo", resolution.packageName)
    }

    @Test
    fun xiaomiQuietWindowKeepsLauncherBoundary() {
        val tracker = UsageForegroundBoundaryTracker()

        assertEquals(
            "com.miui.home",
            tracker.resolve("com.miui.home", retainAcrossQuietWindow = true)
        )
        val retainedBoundary = tracker.resolve(null, retainAcrossQuietWindow = true)
        val resolution = OemRuntimePolicy.resolveUsageForegroundEvent(
            latestForegroundPackage = retainedBoundary,
            latestTrackablePackage = null,
            isXiaomiFamilyDevice = true
        )

        assertEquals(UsageForegroundEventResolutionType.RESET_FOREGROUND, resolution.type)
        assertEquals("com.miui.home", resolution.packageName)
    }

    @Test
    fun xiaomiQuietWindowKeepsTrackableAppBoundary() {
        val tracker = UsageForegroundBoundaryTracker()

        assertEquals(
            "com.focustodo.focus_todo",
            tracker.resolve("com.focustodo.focus_todo", retainAcrossQuietWindow = true)
        )
        val retainedBoundary = tracker.resolve(null, retainAcrossQuietWindow = true)
        val resolution = OemRuntimePolicy.resolveUsageForegroundEvent(
            latestForegroundPackage = retainedBoundary,
            latestTrackablePackage = retainedBoundary,
            isXiaomiFamilyDevice = true
        )

        assertEquals(
            UsageForegroundEventResolutionType.USE_TRACKABLE_PACKAGE,
            resolution.type
        )
        assertEquals("com.focustodo.focus_todo", resolution.packageName)
    }

    @Test
    fun xiaomiWithoutKnownBoundaryDoesNotFallbackToStaleUsageStats() {
        val resolution = OemRuntimePolicy.resolveUsageForegroundEvent(
            latestForegroundPackage = null,
            latestTrackablePackage = null,
            isXiaomiFamilyDevice = true
        )

        assertEquals(UsageForegroundEventResolutionType.RESET_FOREGROUND, resolution.type)
        assertEquals(UNKNOWN_FOREGROUND_BOUNDARY, resolution.packageName)
    }

    @Test
    fun honorQuietWindowDoesNotRetainBoundary() {
        val tracker = UsageForegroundBoundaryTracker()

        assertEquals(
            "com.hihonor.android.launcher",
            tracker.resolve(
                "com.hihonor.android.launcher",
                retainAcrossQuietWindow = false
            )
        )
        assertNull(tracker.resolve(null, retainAcrossQuietWindow = false))
    }
}
