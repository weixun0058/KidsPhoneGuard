package com.kidsphoneguard.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardProtectionHealthEvaluatorTest {

    @Test
    fun allHealthy_isOperationalAndNotDegraded() {
        val health = evaluate()

        assertTrue(health.accessibilityOperational)
        assertFalse(health.accessibilityMissing)
        assertFalse(health.usagePermissionMissing)
        assertFalse(health.accessibilityStale)
        assertFalse(health.usageStale)
        assertFalse(health.degraded)
    }

    @Test
    fun accessibilityDisabled_isMissingAndDegradedButNotStale() {
        val health = evaluate(accessibilityEnabled = false)

        assertFalse(health.accessibilityOperational)
        assertTrue(health.accessibilityMissing)
        assertFalse(health.accessibilityStale)
        assertTrue(health.degraded)
    }

    @Test
    fun accessibilityEnabledButServiceNotBound_isStaleAndDegraded() {
        val health = evaluate(accessibilityServiceRunning = false)

        assertFalse(health.accessibilityOperational)
        assertTrue(health.accessibilityStale)
        assertTrue(health.degraded)
    }

    @Test
    fun missingAccessibilityHeartbeat_isStaleAndUsesUnknownAge() {
        val health = evaluate(accessibilityHeartbeat = 0L)

        assertFalse(health.accessibilityOperational)
        assertTrue(health.accessibilityStale)
        assertTrue(health.degraded)
        assertTrue(health.accessibilityHeartbeatAge == -1L)
    }

    @Test
    fun heartbeatAtTimeoutBoundary_isStillHealthy() {
        val health = evaluate(
            accessibilityHeartbeat = NOW - ACCESSIBILITY_TIMEOUT,
            usageHeartbeat = NOW - USAGE_TIMEOUT
        )

        assertTrue(health.accessibilityOperational)
        assertFalse(health.accessibilityStale)
        assertFalse(health.usageStale)
        assertFalse(health.degraded)
    }

    @Test
    fun missingUsagePermission_isDegradedWithoutRestartSignal() {
        val health = evaluate(
            usagePermissionGranted = false,
            usageTrackingActive = false,
            usageHeartbeat = 0L
        )

        assertTrue(health.usagePermissionMissing)
        assertFalse(health.usageStale)
        assertTrue(health.degraded)
    }

    @Test
    fun grantedUsagePermissionWithInactiveTracker_isStaleAndDegraded() {
        val health = evaluate(usageTrackingActive = false)

        assertTrue(health.usageStale)
        assertTrue(health.degraded)
    }

    @Test
    fun transition_detectsDegradedChangeAndAccessibilityRestoration() {
        val healthy = evaluate()
        val transition = GuardProtectionHealthEvaluator.transition(
            previousDegraded = true,
            previousAccessibilityOperational = false,
            current = healthy
        )

        assertTrue(transition.degradedChanged)
        assertTrue(transition.accessibilityRestored)
    }

    @Test
    fun degradedLock_isShownWhenAccessibilityIsNotOperationalAndPolicyBlocks() {
        assertTrue(
            GuardProtectionHealthEvaluator.shouldShowDegradedLock(
                accessibilityOperational = false,
                policyShouldBlock = true
            )
        )
    }

    @Test
    fun degradedLock_isDismissedAfterAccessibilityRecovers() {
        assertFalse(
            GuardProtectionHealthEvaluator.shouldShowDegradedLock(
                accessibilityOperational = true,
                policyShouldBlock = true
            )
        )
    }

    @Test
    fun degradedLock_isNotShownWhenPolicyAllowsForegroundApp() {
        assertFalse(
            GuardProtectionHealthEvaluator.shouldShowDegradedLock(
                accessibilityOperational = false,
                policyShouldBlock = false
            )
        )
    }

    @Test
    fun recentForegroundPackage_keepsLastObservedPackageWhenPollingWindowIsQuiet() {
        val tracker = RecentForegroundPackageTracker()

        val observed = tracker.resolve("com.android.settings")
        val quietWindow = tracker.resolve(null)

        assertEquals("com.android.settings", observed.packageName)
        assertEquals("event", observed.source)
        assertEquals("com.android.settings", quietWindow.packageName)
        assertEquals("cached", quietWindow.source)
    }

    @Test
    fun recentForegroundPackage_updatesWhenARealTransitionArrives() {
        val tracker = RecentForegroundPackageTracker()
        tracker.resolve("com.kidsphoneguard")

        val transitioned = tracker.resolve("com.miui.home")
        val quietWindow = tracker.resolve("")

        assertEquals("com.miui.home", transitioned.packageName)
        assertEquals("event", transitioned.source)
        assertEquals("com.miui.home", quietWindow.packageName)
        assertEquals("cached", quietWindow.source)
    }

    @Test
    fun recentForegroundPackage_isUnknownOnlyBeforeFirstObservation() {
        val resolution = RecentForegroundPackageTracker().resolve(null)

        assertEquals("unknown", resolution.packageName)
        assertEquals("none", resolution.source)
    }

    @Test
    fun standardOverlay_isSuppressedWhileDegradedLockOwnsTheScreen() {
        assertFalse(
            GuardOverlayArbitrationPolicy.shouldAllowStandardOverlay(
                degradedLockRequestedOrShowing = true
            )
        )
    }

    @Test
    fun standardOverlay_isAllowedAfterDegradedLockReleasesTheScreen() {
        assertTrue(
            GuardOverlayArbitrationPolicy.shouldAllowStandardOverlay(
                degradedLockRequestedOrShowing = false
            )
        )
    }

    @Test
    fun standardOverlay_isSuppressedDuringParentTemporaryUnlock() {
        assertFalse(
            GuardOverlayArbitrationPolicy.shouldAllowStandardOverlay(
                degradedLockRequestedOrShowing = false,
                parentTemporaryUnlockActive = true
            )
        )
    }

    @Test
    fun standardOverlay_isSuppressedDuringGlobalUnlock() {
        assertFalse(
            GuardOverlayArbitrationPolicy.shouldAllowStandardOverlay(
                degradedLockRequestedOrShowing = false,
                parentTemporaryUnlockActive = false,
                globalUnlockActive = true
            )
        )
    }

    @Test
    fun standardOverlay_isSuppressedWhileReturningHome() {
        assertFalse(
            GuardOverlayArbitrationPolicy.shouldAllowStandardOverlay(
                degradedLockRequestedOrShowing = false,
                exitToHomeInProgress = true
            )
        )
    }

    @Test
    fun exitToHome_suppressesOnlyOriginalBlockedPackageDuringTransition() {
        val pending = DegradedExitToHomePolicy.evaluate(
            active = true,
            blockedPackageName = "com.example.blocked",
            observedPackageName = "com.example.blocked",
            safeDestination = false,
            expiresAtElapsedRealtime = NOW + 5_000L,
            nowElapsedRealtime = NOW
        )
        val otherRestrictedPackage = DegradedExitToHomePolicy.evaluate(
            active = true,
            blockedPackageName = "com.example.blocked",
            observedPackageName = "com.android.settings",
            safeDestination = false,
            expiresAtElapsedRealtime = NOW + 5_000L,
            nowElapsedRealtime = NOW
        )

        assertEquals(DegradedExitToHomeDecision.SUPPRESS_TRANSITION_PENDING, pending)
        assertEquals(
            DegradedExitToHomeDecision.CANCELLED_BY_OTHER_FOREGROUND,
            otherRestrictedPackage
        )
    }

    @Test
    fun exitToHome_finishesWhenSafeDestinationIsObserved() {
        assertEquals(
            DegradedExitToHomeDecision.SAFE_DESTINATION_REACHED,
            DegradedExitToHomePolicy.evaluate(
                active = true,
                blockedPackageName = "com.example.blocked",
                observedPackageName = "com.miui.home",
                safeDestination = true,
                expiresAtElapsedRealtime = NOW + 5_000L,
                nowElapsedRealtime = NOW
            )
        )
    }

    @Test
    fun exitToHome_expiresAtTransitionBoundary() {
        assertEquals(
            DegradedExitToHomeDecision.EXPIRED,
            DegradedExitToHomePolicy.evaluate(
                active = true,
                blockedPackageName = "com.example.blocked",
                observedPackageName = "com.example.blocked",
                safeDestination = false,
                expiresAtElapsedRealtime = NOW,
                nowElapsedRealtime = NOW
            )
        )
    }

    @Test
    fun degradedEmergencyPolicy_allowsResolvedHomeAndDialer() {
        assertTrue(
            DegradedEmergencySurfacePolicy.shouldAllow(
                packageName = "com.vendor.launcher",
                ownPackageName = "com.kidsphoneguard",
                resolvedHomePackages = setOf("com.vendor.launcher"),
                resolvedDialerPackages = emptySet()
            )
        )
        assertTrue(
            DegradedEmergencySurfacePolicy.shouldAllow(
                packageName = "com.vendor.phone",
                ownPackageName = "com.kidsphoneguard",
                resolvedHomePackages = emptySet(),
                resolvedDialerPackages = setOf("com.vendor.phone")
            )
        )
    }

    @Test
    fun degradedEmergencyPolicy_allowsInCallButNotSettingsOrMarket() {
        assertTrue(
            DegradedEmergencySurfacePolicy.shouldAllow(
                packageName = "com.android.incallui",
                ownPackageName = "com.kidsphoneguard",
                resolvedHomePackages = emptySet(),
                resolvedDialerPackages = emptySet()
            )
        )
        assertFalse(
            DegradedEmergencySurfacePolicy.shouldAllow(
                packageName = "com.android.settings",
                ownPackageName = "com.kidsphoneguard",
                resolvedHomePackages = emptySet(),
                resolvedDialerPackages = emptySet()
            )
        )
        assertFalse(
            DegradedEmergencySurfacePolicy.shouldAllow(
                packageName = "com.xiaomi.market",
                ownPackageName = "com.kidsphoneguard",
                resolvedHomePackages = emptySet(),
                resolvedDialerPackages = emptySet()
            )
        )
    }

    @Test
    fun parentTemporaryUnlock_isActiveOnlyBeforeExpiry() {
        val expiresAt = DegradedTemporaryUnlockPolicy.expiresAt(NOW)

        assertTrue(
            DegradedTemporaryUnlockPolicy.isActive(
                untilElapsedRealtime = expiresAt,
                nowElapsedRealtime = expiresAt - 1L
            )
        )
        assertFalse(
            DegradedTemporaryUnlockPolicy.isActive(
                untilElapsedRealtime = expiresAt,
                nowElapsedRealtime = expiresAt
            )
        )
    }

    @Test
    fun parentTemporaryUnlock_usesFiveMinuteBound() {
        assertEquals(
            NOW + 5 * 60 * 1_000L,
            DegradedTemporaryUnlockPolicy.expiresAt(NOW)
        )
    }

    private fun evaluate(
        accessibilityEnabled: Boolean = true,
        usagePermissionGranted: Boolean = true,
        accessibilityServiceRunning: Boolean = true,
        usageTrackingActive: Boolean = true,
        accessibilityHeartbeat: Long = NOW - 1_000L,
        usageHeartbeat: Long = NOW - 1_000L
    ): GuardProtectionHealthSnapshot = GuardProtectionHealthEvaluator.evaluate(
        GuardProtectionHealthInput(
            now = NOW,
            accessibilityEnabled = accessibilityEnabled,
            usagePermissionGranted = usagePermissionGranted,
            accessibilityServiceRunning = accessibilityServiceRunning,
            usageTrackingActive = usageTrackingActive,
            accessibilityHeartbeat = accessibilityHeartbeat,
            usageHeartbeat = usageHeartbeat,
            accessibilityHeartbeatTimeoutMs = ACCESSIBILITY_TIMEOUT,
            usageHeartbeatTimeoutMs = USAGE_TIMEOUT
        )
    )

    companion object {
        private const val NOW = 1_000_000L
        private const val ACCESSIBILITY_TIMEOUT = 15_000L
        private const val USAGE_TIMEOUT = 20_000L
    }
}
