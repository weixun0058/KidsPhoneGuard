package com.kidsphoneguard.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.security.SecureRandom

data class RecoverySnapshot(
    val recoveryId: String,
    val displayRecoveryId: String,
    val recoveryDate: String
)

sealed interface RecoveryVerificationResult {
    data object Success : RecoveryVerificationResult

    data class Rejected(
        val remainingAttempts: Int,
        val retryAfterSeconds: Long
    ) : RecoveryVerificationResult

    data class RateLimited(
        val retryAfterSeconds: Long
    ) : RecoveryVerificationResult
}

/**
 * Android 侧恢复入口：采集应用可读设备号/可信日期并执行轻量尝试限制。
 */
object RecoveryCodeManager {
    private const val TAG = "RecoveryCodeManager"
    private const val PREFS_NAME = "recovery_code_prefs"
    private const val KEY_FALLBACK_ID = "fallback_recovery_id"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_BLOCKED_UNTIL_ELAPSED = "blocked_until_elapsed"
    private const val KEY_LAST_ATTEMPT_ELAPSED = "last_attempt_elapsed"

    /*
     * 产品已明确采用“防普通儿童”的离线客服算号模型。
     * 客服算号器必须与此值保持一致；专业逆向不在当前威胁模型内。
     */
    private const val MASTER_SECRET =
        "45250811B5D0C9934D02ADDC38EA65B745D05A73F85521C6C22E7B6BFE89881E"

    @SuppressLint("HardwareIds")
    fun snapshot(context: Context): RecoverySnapshot {
        val appContext = context.applicationContext
        // ISS-025 产品决策：本地离线恢复需要 App 与客服共同使用稳定、免权限的设备号。
        val rawRecoveryId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).takeUnless { it.isNullOrBlank() } ?: readOrCreateFallbackId(appContext)
        val normalizedId = RecoveryCodeEngine.normalizeRecoveryId(rawRecoveryId)
        return RecoverySnapshot(
            recoveryId = normalizedId,
            displayRecoveryId = RecoveryCodeEngine.formatRecoveryId(normalizedId),
            recoveryDate = TrustedTimeProvider.trustedToday(appContext)
        )
    }

    @Synchronized
    fun verify(
        context: Context,
        enteredCode: String,
        recoverySnapshot: RecoverySnapshot = snapshot(context)
    ): RecoveryVerificationResult {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val lastAttemptElapsed = prefs.getLong(KEY_LAST_ATTEMPT_ELAPSED, 0L)
        val rebootedSinceLastAttempt = lastAttemptElapsed > 0L && now < lastAttemptElapsed
        val state = if (rebootedSinceLastAttempt) {
            prefs.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_BLOCKED_UNTIL_ELAPSED)
                .remove(KEY_LAST_ATTEMPT_ELAPSED)
                .apply()
            RecoveryAttemptState()
        } else {
            RecoveryAttemptState(
                failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
                blockedUntilElapsedMillis = prefs.getLong(KEY_BLOCKED_UNTIL_ELAPSED, 0L)
            )
        }
        val retryAfterSeconds = RecoveryAttemptPolicy.retryAfterSeconds(state, now)
        if (retryAfterSeconds > 0L) {
            Log.w(TAG, "recovery_code_rate_limited retryAfterSeconds=$retryAfterSeconds")
            return RecoveryVerificationResult.RateLimited(retryAfterSeconds)
        }

        val verified = try {
            RecoveryCodeEngine.isValidCode(
                recoveryId = recoverySnapshot.recoveryId,
                recoveryDate = recoverySnapshot.recoveryDate,
                enteredCode = enteredCode,
                masterSecret = MASTER_SECRET
            )
        } catch (e: Exception) {
            Log.e(TAG, "recovery_code_verification_failed", e)
            false
        }
        if (verified) {
            prefs.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_BLOCKED_UNTIL_ELAPSED)
                .remove(KEY_LAST_ATTEMPT_ELAPSED)
                .apply()
            Log.w(
                TAG,
                "recovery_code_verified date=${recoverySnapshot.recoveryDate}"
            )
            return RecoveryVerificationResult.Success
        }

        val update = RecoveryAttemptPolicy.recordFailure(state, now)
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, update.state.failedAttempts)
            .putLong(
                KEY_BLOCKED_UNTIL_ELAPSED,
                update.state.blockedUntilElapsedMillis
            )
            .putLong(KEY_LAST_ATTEMPT_ELAPSED, now)
            .apply()
        Log.w(
            TAG,
            "recovery_code_rejected remainingAttempts=${update.remainingAttempts} " +
                "retryAfterSeconds=${update.retryAfterSeconds}"
        )
        return RecoveryVerificationResult.Rejected(
            remainingAttempts = update.remainingAttempts,
            retryAfterSeconds = update.retryAfterSeconds
        )
    }

    private fun readOrCreateFallbackId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_FALLBACK_ID, null)?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val generated = bytes.joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
        prefs.edit().putString(KEY_FALLBACK_ID, generated).apply()
        Log.w(TAG, "android_id_unavailable fallback_recovery_id_created")
        return generated
    }
}
