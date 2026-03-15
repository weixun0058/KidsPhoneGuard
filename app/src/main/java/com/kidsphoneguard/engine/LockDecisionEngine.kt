package com.kidsphoneguard.engine

import android.content.Context
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.data.repository.DailyUsageRepository
import com.kidsphoneguard.utils.SettingsManager
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LockDecisionEngine private constructor(
    private val appRuleRepository: AppRuleRepository,
    private val dailyUsageRepository: DailyUsageRepository,
    private val settingsManager: SettingsManager
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
                settingsManager = SettingsManager.getInstance(context)
            )
        }
    }

    suspend fun getBlockDecision(packageName: String): BlockDecision {
        val globalLocked = settingsManager.isGlobalLockEnabled()
        val rule = appRuleRepository.getRuleByPackageName(packageName)
        val appName = rule?.appName ?: ""

        if (globalLocked || (rule?.isGlobalLocked == true)) {
            return BlockDecision(shouldBlock = true, reason = BlockReason.GLOBAL_LOCK, appName = appName)
        }

        if (rule == null) {
            return BlockDecision(shouldBlock = false, reason = BlockReason.NONE, appName = appName)
        }

        when (rule.ruleType) {
            RuleType.BLOCK ->
                return BlockDecision(shouldBlock = true, reason = BlockReason.APP_BLOCKED, appName = appName)
            RuleType.LIMIT -> {
                if (rule.blockedTimeWindows.isNotEmpty() && isInBlockedTimeWindow(rule.blockedTimeWindows)) {
                    return BlockDecision(shouldBlock = true, reason = BlockReason.TIME_WINDOW_BLOCKED, appName = appName)
                }
                if (rule.dailyAllowedMinutes > 0) {
                    val usedSeconds = dailyUsageRepository.getTodayUsageSeconds(packageName)
                    if (usedSeconds >= rule.dailyAllowedMinutes * 60L) {
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

    private fun isInBlockedTimeWindow(timeWindows: String): Boolean {
        val now = LocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val windows = timeWindows.split(",")
        for (window in windows) {
            val parts = window.trim().split("-")
            if (parts.size != 2) continue

            try {
                val startTime = LocalTime.parse(parts[0].trim(), timeFormatter)
                val endTime = LocalTime.parse(parts[1].trim(), timeFormatter)

                val inWindow = if (startTime.isAfter(endTime)) {
                    now.isAfter(startTime) || now.isBefore(endTime)
                } else {
                    now.isAfter(startTime) && now.isBefore(endTime)
                }

                if (inWindow) {
                    return true
                }
            } catch (e: Exception) {
                return false
            }
        }

        return false
    }
}
