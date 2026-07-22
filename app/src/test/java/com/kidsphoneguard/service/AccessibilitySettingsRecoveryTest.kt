package com.kidsphoneguard.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySettingsRecoveryTest {

    private val targetPackage = "com.kidsphoneguard"
    private val targetClass = "com.kidsphoneguard.service.GuardAccessibilityService"

    @Test
    fun mergeEnabledServices_preservesOtherServicesAndAppendsTarget() {
        val existing = "com.reader/com.reader.ReaderService:com.voice/.VoiceService"

        val merged = AccessibilitySettingsRecovery.mergeEnabledServices(
            existing = existing,
            targetPackage = targetPackage,
            targetClass = targetClass
        )

        assertEquals(
            "$existing:$targetPackage/$targetClass",
            merged
        )
    }

    @Test
    fun mergeEnabledServices_doesNotDuplicateFullTargetEntry() {
        val existing = "com.reader/.ReaderService:$targetPackage/$targetClass"

        assertEquals(
            existing,
            AccessibilitySettingsRecovery.mergeEnabledServices(
                existing,
                targetPackage,
                targetClass
            )
        )
    }

    @Test
    fun mergeEnabledServices_recognizesShortTargetClass() {
        val existing = "com.reader/.ReaderService:$targetPackage/.service.GuardAccessibilityService"

        assertEquals(
            existing,
            AccessibilitySettingsRecovery.mergeEnabledServices(
                existing,
                targetPackage,
                targetClass
            )
        )
    }

    @Test
    fun removeTargetService_preservesOtherServicesAndRemovesAllTargetForms() {
        val existing =
            "com.reader/.ReaderService:" +
                "$targetPackage/$targetClass:" +
                "$targetPackage/.service.GuardAccessibilityService:" +
                "com.voice/com.voice.VoiceService"

        assertEquals(
            "com.reader/.ReaderService:com.voice/com.voice.VoiceService",
            AccessibilitySettingsRecovery.removeTargetService(
                existing,
                targetPackage,
                targetClass
            )
        )
    }

    @Test
    fun prepareAutomaticRebindRequiresConfiguredButStaleProtectedService() {
        assertTrue(
            AccessibilitySettingsRecovery.shouldPrepareAutomaticRebind(
                serviceConfigured = true,
                serviceRunning = false,
                heartbeatAgeMs = 120_000L,
                heartbeatTimeoutMs = 15_000L,
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = false,
                permissionGranted = true
            )
        )
        assertFalse(
            AccessibilitySettingsRecovery.shouldPrepareAutomaticRebind(
                serviceConfigured = true,
                serviceRunning = true,
                heartbeatAgeMs = 100L,
                heartbeatTimeoutMs = 15_000L,
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = false,
                permissionGranted = true
            )
        )
        assertFalse(
            AccessibilitySettingsRecovery.shouldPrepareAutomaticRebind(
                serviceConfigured = true,
                serviceRunning = false,
                heartbeatAgeMs = 120_000L,
                heartbeatTimeoutMs = 15_000L,
                globalUnlockEnabled = true,
                setupSettingsAccessAllowed = false,
                permissionGranted = true
            )
        )
    }

    @Test
    fun automaticRecoveryRequiresProtectedStateAndPermission() {
        assertTrue(
            AccessibilitySettingsRecovery.shouldAttemptAutomatically(
                serviceEnabled = false,
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = false,
                permissionGranted = true
            )
        )
        assertFalse(
            AccessibilitySettingsRecovery.shouldAttemptAutomatically(
                serviceEnabled = false,
                globalUnlockEnabled = true,
                setupSettingsAccessAllowed = false,
                permissionGranted = true
            )
        )
        assertFalse(
            AccessibilitySettingsRecovery.shouldAttemptAutomatically(
                serviceEnabled = false,
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = true,
                permissionGranted = true
            )
        )
        assertFalse(
            AccessibilitySettingsRecovery.shouldAttemptAutomatically(
                serviceEnabled = false,
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = false,
                permissionGranted = false
            )
        )
    }
}
