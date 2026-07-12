package com.kidsphoneguard.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityOperationalStateTest {

    private val now = 1_000_000L
    private val timeoutMs = 15_000L

    @Test
    fun settingsEnabledButServiceNotBoundIsNotOperational() {
        assertFalse(
            AccessibilityOperationalState.isOperational(
                enabledInSettings = true,
                serviceRunning = false,
                heartbeatAt = now,
                now = now,
                heartbeatTimeoutMs = timeoutMs
            )
        )
    }

    @Test
    fun staleHeartbeatIsNotOperational() {
        assertFalse(
            AccessibilityOperationalState.isOperational(
                enabledInSettings = true,
                serviceRunning = true,
                heartbeatAt = now - timeoutMs - 1L,
                now = now,
                heartbeatTimeoutMs = timeoutMs
            )
        )
    }

    @Test
    fun boundServiceWithFreshHeartbeatIsOperational() {
        assertTrue(
            AccessibilityOperationalState.isOperational(
                enabledInSettings = true,
                serviceRunning = true,
                heartbeatAt = now - timeoutMs,
                now = now,
                heartbeatTimeoutMs = timeoutMs
            )
        )
    }
}
