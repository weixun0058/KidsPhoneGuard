package com.kidsphoneguard.service

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
