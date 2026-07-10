package com.kidsphoneguard.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.kidsphoneguard.service.GuardForegroundService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 可信时间提供者 —— 防御系统时间篡改绕过（ISS-001）
 *
 * 纯本地实现，无网络依赖（符合项目 §0.8 排序约束：P0/P1 未清前不引入网络模块）。
 *
 * 两个被攻击的杠杆：
 *  1. 时段禁用依赖 [java.time.LocalTime.now]
 *  2. 每日限额重置依赖 [java.time.LocalDate.now]
 *  儿童改系统时间/日期即可绕过。
 *
 * 防御思路：
 *  - 以 [SystemClock.elapsedRealtime]（单调递增，不受系统时间影响）作为时间增量基准；
 *  - 以持久化的"上次已知系统时间"作为锚点；
 *  - [checkpoint] 在服务启动与保活循环中定期调用，比较墙钟增量与单调时钟增量；
 *  - 检测到篡改：写取证日志 + 设置 tamper 标志 + 冻结"今日日期"（限额累计不清零）；
 *  - 时段判定在篡改期由 [com.kidsphoneguard.engine.LockDecisionEngine] 直接短路为拦截（反向激励）；
 *  - 家长验证密码后调用 [clearTamperFlag] 解除。
 *
 * 已知限制（纯本地方案固有）：设备重启后 [SystemClock.elapsedRealtime] 会归零，
 * 因而只能继续识别倒拨，无法可靠地区分“关机期间正常过夜”和“关机后前拨时间”。
 */
object TrustedTimeProvider {
    private const val TAG = "TrustedTimeProvider"
    private const val PREFS_NAME = "trusted_time"
    private const val KEY_LAST_WALL_MILLIS = "last_wall_millis"
    private const val KEY_LAST_ELAPSED_MILLIS = "last_elapsed_millis"
    private const val KEY_FROZEN_DATE = "frozen_date"
    private const val KEY_TAMPER_FLAG = "tamper_detected"
    private const val KEY_TAMPER_AT = "tamper_at"

    /** 墙钟与单调时钟允许的最大偏差，覆盖正常微调与调度抖动。 */
    private const val CLOCK_SKEW_TOLERANCE_MILLIS = 60_000L

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 时间锚点校验。在服务启动与保活循环中定期调用。
     * 检测到篡改时写取证日志并冻结日期；正常时更新锚点。
     */
    @Synchronized
    fun checkpoint(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val lastWall = prefs.getLong(KEY_LAST_WALL_MILLIS, 0L)
        val lastElapsed = prefs.getLong(KEY_LAST_ELAPSED_MILLIS, 0L)
        val tamperAlready = prefs.getBoolean(KEY_TAMPER_FLAG, false)

        if (lastWall == 0L || lastElapsed == 0L) {
            // 首次记录，仅写锚点
            prefs.edit()
                .putLong(KEY_LAST_WALL_MILLIS, wallNow)
                .putLong(KEY_LAST_ELAPSED_MILLIS, elapsedNow)
                .apply()
            return
        }

        val integrity = evaluateClockIntegrity(lastWall, lastElapsed, wallNow, elapsedNow)
        if (integrity.kind != null) {
            val kind = integrity.kind.logValue
            val prevDate = formatDate(Instant.ofEpochMilli(lastWall))
            val nowDate = formatDate(Instant.ofEpochMilli(wallNow))
            Log.w(
                TAG,
                "time_tamper_detected kind=$kind lastWall=$lastWall wallNow=$wallNow " +
                    "wallDelta=${integrity.wallDeltaMillis} elapsedDelta=${integrity.elapsedDeltaMillis} " +
                    "rebooted=${integrity.rebooted} prevDate=$prevDate nowDate=$nowDate already=$tamperAlready"
            )
            // 篡改期不更新锚点（保留篡改前锚点）；仅首次写入 tamper 标志与冻结日期，避免重复取证
            if (!tamperAlready) {
                prefs.edit()
                    .putBoolean(KEY_TAMPER_FLAG, true)
                    .putLong(KEY_TAMPER_AT, wallNow)
                    .putString(KEY_FROZEN_DATE, prevDate)
                    .apply()
                GuardForegroundService.recordForensicsLine(
                    appContext,
                    "time_tamper",
                    "kind=$kind|lastWall=$lastWall|wallNow=$wallNow|wallDeltaMs=${integrity.wallDeltaMillis}|" +
                        "elapsedDeltaMs=${integrity.elapsedDeltaMillis}|rebooted=${integrity.rebooted}|" +
                        "prevDate=$prevDate|nowDate=$nowDate"
                )
            }
            return
        }

        // 正常：更新锚点。tamper 标志不在此自动清除（需家长密码），但非篡改期 trustedToday 返回真实今天
        prefs.edit()
            .putLong(KEY_LAST_WALL_MILLIS, wallNow)
            .putLong(KEY_LAST_ELAPSED_MILLIS, elapsedNow)
            .apply()
    }

    /**
     * 纯逻辑时钟完整性判断，供单测覆盖正常跨午夜、前拨、倒拨与重启边界。
     *
     * 正常跨午夜时，墙钟与单调时钟都只前进数秒，二者增量相同，不能误判。
     */
    internal fun evaluateClockIntegrity(
        lastWallMillis: Long,
        lastElapsedMillis: Long,
        wallNowMillis: Long,
        elapsedNowMillis: Long
    ): ClockIntegrity {
        val rebooted = elapsedNowMillis < lastElapsedMillis
        val wallDelta = wallNowMillis - lastWallMillis
        if (rebooted) {
            return ClockIntegrity(
                kind = if (wallDelta < -CLOCK_SKEW_TOLERANCE_MILLIS) ClockTamperKind.BACKWARD else null,
                rebooted = true,
                wallDeltaMillis = wallDelta,
                elapsedDeltaMillis = 0L
            )
        }

        val elapsedDelta = elapsedNowMillis - lastElapsedMillis
        val clockSkew = wallDelta - elapsedDelta
        val kind = when {
            clockSkew < -CLOCK_SKEW_TOLERANCE_MILLIS -> ClockTamperKind.BACKWARD
            clockSkew > CLOCK_SKEW_TOLERANCE_MILLIS -> ClockTamperKind.FORWARD
            else -> null
        }
        return ClockIntegrity(kind, rebooted = false, wallDelta, elapsedDelta)
    }

    /** 是否处于时间篡改冻结状态。 */
    fun isTamperDetected(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TAMPER_FLAG, false)
    }

    /**
     * 家长验证密码后调用，解除篡改冻结并重新锚定到当前时间。
     */
    @Synchronized
    fun clearTamperFlag(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_TAMPER_FLAG, false)) return
        prefs.edit()
            .putBoolean(KEY_TAMPER_FLAG, false)
            .remove(KEY_TAMPER_AT)
            .remove(KEY_FROZEN_DATE)
            .putLong(KEY_LAST_WALL_MILLIS, System.currentTimeMillis())
            .putLong(KEY_LAST_ELAPSED_MILLIS, SystemClock.elapsedRealtime())
            .apply()
        Log.i(TAG, "time_tamper_cleared_by_parent")
        GuardForegroundService.recordForensicsLine(appContext, "time_tamper_cleared", "")
    }

    /**
     * 可信今日日期字符串（yyyy-MM-dd）。
     * 篡改期：返回冻结日期（篡改前的日期），限额累计不清零。
     * 非篡改期：返回真实今天。
     */
    fun trustedToday(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_TAMPER_FLAG, false)) {
            return LocalDate.now().format(dateFormatter)
        }
        val frozen = prefs.getString(KEY_FROZEN_DATE, null)
        if (!frozen.isNullOrEmpty()) {
            return frozen
        }
        // tamper 但无 frozen_date（异常情况），用 lastWall 推算
        val lastWall = prefs.getLong(KEY_LAST_WALL_MILLIS, 0L)
        return if (lastWall == 0L) {
            LocalDate.now().format(dateFormatter)
        } else {
            formatDate(Instant.ofEpochMilli(lastWall))
        }
    }

    private fun formatDate(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
    }
}

internal enum class ClockTamperKind(val logValue: String) {
    BACKWARD("backward"),
    FORWARD("forward")
}

internal data class ClockIntegrity(
    val kind: ClockTamperKind?,
    val rebooted: Boolean,
    val wallDeltaMillis: Long,
    val elapsedDeltaMillis: Long
)
