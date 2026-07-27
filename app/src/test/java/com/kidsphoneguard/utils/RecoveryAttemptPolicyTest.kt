package com.kidsphoneguard.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryAttemptPolicyTest {

    @Test
    fun fiveFailures_startSixtySecondLockout() {
        var state = RecoveryAttemptState()
        repeat(4) { index ->
            val update = RecoveryAttemptPolicy.recordFailure(state, nowElapsedMillis = 10_000L)
            state = update.state
            assertEquals(4 - index, update.remainingAttempts)
            assertEquals(0L, update.retryAfterSeconds)
        }

        val fifth = RecoveryAttemptPolicy.recordFailure(state, nowElapsedMillis = 10_000L)

        assertEquals(0, fifth.remainingAttempts)
        assertEquals(60L, fifth.retryAfterSeconds)
        assertEquals(70_000L, fifth.state.blockedUntilElapsedMillis)
    }

    @Test
    fun activeLockout_roundsRemainingSecondsUp() {
        val state = RecoveryAttemptState(blockedUntilElapsedMillis = 70_000L)

        assertEquals(60L, RecoveryAttemptPolicy.retryAfterSeconds(state, 10_000L))
        assertEquals(1L, RecoveryAttemptPolicy.retryAfterSeconds(state, 69_001L))
        assertEquals(0L, RecoveryAttemptPolicy.retryAfterSeconds(state, 70_000L))
    }

    @Test
    fun expiredLockout_allowsFreshAttemptRound() {
        val expired = RecoveryAttemptState(
            failedAttempts = 0,
            blockedUntilElapsedMillis = 70_000L
        )

        val update = RecoveryAttemptPolicy.recordFailure(
            state = expired,
            nowElapsedMillis = 70_001L
        )

        assertEquals(1, update.state.failedAttempts)
        assertEquals(4, update.remainingAttempts)
        assertEquals(0L, update.retryAfterSeconds)
    }
}
