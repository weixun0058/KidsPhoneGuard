package com.kidsphoneguard.utils

internal data class RecoveryAttemptState(
    val failedAttempts: Int = 0,
    val blockedUntilElapsedMillis: Long = 0L
)

internal data class RecoveryFailureUpdate(
    val state: RecoveryAttemptState,
    val remainingAttempts: Int,
    val retryAfterSeconds: Long
)

/**
 * 轻量本地尝试限制：每轮最多 5 次，达到上限后冷却 60 秒。
 */
internal object RecoveryAttemptPolicy {
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 60_000L

    fun retryAfterSeconds(state: RecoveryAttemptState, nowElapsedMillis: Long): Long {
        val remainingMillis = state.blockedUntilElapsedMillis - nowElapsedMillis
        return if (remainingMillis <= 0L) {
            0L
        } else {
            (remainingMillis + 999L) / 1_000L
        }
    }

    fun recordFailure(
        state: RecoveryAttemptState,
        nowElapsedMillis: Long
    ): RecoveryFailureUpdate {
        val existingRetry = retryAfterSeconds(state, nowElapsedMillis)
        if (existingRetry > 0L) {
            return RecoveryFailureUpdate(
                state = state,
                remainingAttempts = 0,
                retryAfterSeconds = existingRetry
            )
        }

        val failedAttempts = state.failedAttempts + 1
        return if (failedAttempts >= MAX_ATTEMPTS) {
            RecoveryFailureUpdate(
                state = RecoveryAttemptState(
                    failedAttempts = 0,
                    blockedUntilElapsedMillis = nowElapsedMillis + LOCKOUT_MILLIS
                ),
                remainingAttempts = 0,
                retryAfterSeconds = LOCKOUT_MILLIS / 1_000L
            )
        } else {
            RecoveryFailureUpdate(
                state = RecoveryAttemptState(failedAttempts = failedAttempts),
                remainingAttempts = MAX_ATTEMPTS - failedAttempts,
                retryAfterSeconds = 0L
            )
        }
    }
}
