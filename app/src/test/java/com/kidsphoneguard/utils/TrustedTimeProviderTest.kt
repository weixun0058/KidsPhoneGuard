package com.kidsphoneguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedTimeProviderTest {

    @Test
    fun normalMidnightCrossing_isNotTamper() {
        val result = TrustedTimeProvider.evaluateClockIntegrity(
            lastWallMillis = 86_395_000L,
            lastElapsedMillis = 1_000_000L,
            wallNowMillis = 86_405_000L,
            elapsedNowMillis = 1_010_000L
        )

        assertEquals(null, result.kind)
        assertFalse(result.rebooted)
    }

    @Test
    fun forwardClockEdit_isTamper() {
        val result = TrustedTimeProvider.evaluateClockIntegrity(
            lastWallMillis = 1_000_000L,
            lastElapsedMillis = 500_000L,
            wallNowMillis = 1_900_000L,
            elapsedNowMillis = 510_000L
        )

        assertEquals(ClockTamperKind.FORWARD, result.kind)
    }

    @Test
    fun backwardClockEdit_isTamper() {
        val result = TrustedTimeProvider.evaluateClockIntegrity(
            lastWallMillis = 1_000_000L,
            lastElapsedMillis = 500_000L,
            wallNowMillis = 800_000L,
            elapsedNowMillis = 510_000L
        )

        assertEquals(ClockTamperKind.BACKWARD, result.kind)
    }

    @Test
    fun rebootWithNormalForwardWallClock_reanchorsWithoutFalsePositive() {
        val result = TrustedTimeProvider.evaluateClockIntegrity(
            lastWallMillis = 1_000_000L,
            lastElapsedMillis = 500_000L,
            wallNowMillis = 3_000_000L,
            elapsedNowMillis = 10_000L
        )

        assertEquals(null, result.kind)
        assertTrue(result.rebooted)
    }

    @Test
    fun rebootWithBackwardWallClock_isTamper() {
        val result = TrustedTimeProvider.evaluateClockIntegrity(
            lastWallMillis = 1_000_000L,
            lastElapsedMillis = 500_000L,
            wallNowMillis = 800_000L,
            elapsedNowMillis = 10_000L
        )

        assertEquals(ClockTamperKind.BACKWARD, result.kind)
        assertTrue(result.rebooted)
    }
}
