package com.kidsphoneguard.engine

import android.content.Context
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.data.repository.DailyUsageRepository
import com.kidsphoneguard.utils.SettingsManager
import com.kidsphoneguard.utils.TemporaryBonusManager
import com.kidsphoneguard.utils.TrustedTimeProvider
import com.kidsphoneguard.utils.WhitelistManager
import com.kidsphoneguard.utils.SystemSurfaceClassifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LockDecisionEngine private constructor(
    private val appRuleRepository: AppRuleRepository,
    private val dailyUsageRepository: DailyUsageRepository,
    private val settingsManager: SettingsManager,
    private val temporaryBonusManager: TemporaryBonusManager,
    private val appPackageName: String,
    private val appContext: android.content.Context
) {
    companion object {
        @Volatile
        private var instance: LockDecisionEngine? = null

        fun getInstance(context: Context): LockDecisionEngine {
            return instance ?: synchronized(this) {
                instance ?: createInstance(context.applicationContext).also { instance = it }
            }
        }

        private fun createInstance(context: Context): LockDecisionEngine {
            val app = context as? KidsPhoneGuardApp
                ?: throw IllegalStateException("Application context is not KidsPhoneGuardApp")
            return LockDecisionEngine(
                appRuleRepository = app.appRuleRepository,
                dailyUsageRepository = app.dailyUsageRepository,
                settingsManager = SettingsManager.getInstance(context),
                temporaryBonusManager = TemporaryBonusManager.getInstance(context),
                appPackageName = context.packageName,
                appContext = context.applicationContext
            )
        }

        /**
         * 判断 [now] 是否落在任一禁用时段内（ISS-015：提取为纯逻辑以便单测）。
         *
         * 时段格式 "HH:mm-HH:mm"，多个用逗号分隔。跨午夜窗口（start > end）按"晚于 start 或早于 end"判定。
         * start == end 视为全天禁用。非法格式跳过该窗口。
         */
        internal fun isInBlockedTimeWindow(timeWindows: String, now: LocalTime): Boolean {
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val windows = timeWindows.split(",")
            for (window in windows) {
                val parts = window.trim().split("-")
                if (parts.size != 2) continue

                try {
                    val startTime = LocalTime.parse(parts[0].trim(), timeFormatter)
                    val endTime = LocalTime.parse(parts[1].trim(), timeFormatter)
                    if (startTime == endTime) {
                        return true
                    }

                    val inWindow = if (startTime.isAfter(endTime)) {
                        !now.isBefore(startTime) || !now.isAfter(endTime)
                    } else {
                        !now.isBefore(startTime) && !now.isAfter(endTime)
                    }

                    if (inWindow) {
                        return true
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            return false
        }
    }

    suspend fun getBlockDecision(packageName: String): BlockDecision {
        if (settingsManager.isGlobalUnlockEnabled()) {
            return BlockDecision(shouldBlock = false, reason = BlockReason.NONE, appName = "")
        }

        if (packageName == appPackageName) {
            return BlockDecision(shouldBlock = false, reason = BlockReason.NONE, appName = "")
        }
        if (SystemSurfaceClassifier.isSettingsSurface(packageName)) {
            return BlockDecision(shouldBlock = true, reason = BlockReason.APP_BLOCKED, appName = "系统设置")
        }
        if (SystemSurfaceClassifier.isInstallerOrMarketSurface(packageName)) {
            return BlockDecision(shouldBlock = true, reason = BlockReason.APP_BLOCKED, appName = "安装器/应用市场")
        }

        val globalLocked = settingsManager.isGlobalLockEnabled()
        val rule = appRuleRepository.getRuleByPackageName(packageName)
        val appName = rule?.appName ?: ""

        // ISS-004：全局锁统一以 SettingsManager 为单一真相源。
        // AppRule.isGlobalLocked 字段保留（避免破坏性 DB 迁移）但不再作为判断依据，
        // 消除双源不一致风险。
        if (globalLocked) {
            return BlockDecision(shouldBlock = true, reason = BlockReason.GLOBAL_LOCK, appName = appName)
        }

        if (rule == null) {
            return BlockDecision(shouldBlock = false, reason = BlockReason.NONE, appName = appName)
        }

        when (rule.ruleType) {
            RuleType.BLOCK ->
                return BlockDecision(shouldBlock = true, reason = BlockReason.APP_BLOCKED, appName = appName)
            RuleType.LIMIT -> {
                val checkTimeWindow = rule.limitMode == LimitMode.BOTH || rule.limitMode == LimitMode.WINDOW_ONLY
                val checkDuration = rule.limitMode == LimitMode.BOTH || rule.limitMode == LimitMode.DURATION_ONLY

                if (checkTimeWindow && rule.blockedTimeWindows.isNotEmpty()) {
                    // ISS-001：系统时间篡改冻结期，时段规则直接短路为拦截（反向激励）。
                    // 儿童改时间反而触发时段拦截，没有动机篡改；纯时长应用不受影响（累计已冻结）。
                    if (TrustedTimeProvider.isTamperDetected(appContext)) {
                        return BlockDecision(
                            shouldBlock = true,
                            reason = BlockReason.TIME_WINDOW_BLOCKED,
                            appName = appName
                        )
                    }
                    if (isInBlockedTimeWindow(rule.blockedTimeWindows)) {
                        return BlockDecision(shouldBlock = true, reason = BlockReason.TIME_WINDOW_BLOCKED, appName = appName)
                    }
                }
                if (checkDuration && rule.dailyAllowedMinutes > 0) {
                    val usedSeconds = dailyUsageRepository.getTodayUsageSeconds(packageName)
                    val allowedSeconds = rule.dailyAllowedMinutes * 60L +
                        temporaryBonusManager.getTodayBonusSeconds(packageName)
                    if (usedSeconds >= allowedSeconds) {
                        return BlockDecision(
                            shouldBlock = true,
                            reason = BlockReason.TIME_LIMIT_EXCEEDED,
                            appName = appName
                        )
                    }
                }
            }
            RuleType.ALLOW -> {}
        }

        return BlockDecision(shouldBlock = false, reason = BlockReason.NONE, appName = appName)
    }

    private fun isInBlockedTimeWindow(timeWindows: String): Boolean =
        isInBlockedTimeWindow(timeWindows, LocalTime.now())
}
