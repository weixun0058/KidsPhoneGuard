package com.kidsphoneguard.ui.config

import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import kotlin.math.abs
import kotlin.math.max

/** 规则卡片使用的纯文本用量摘要，独立于 Compose，便于单测。 */
object RuleUsageFormatter {
    fun summary(rule: AppRule, usedSeconds: Long, bonusSeconds: Long = 0L): String {
        if (rule.ruleType != RuleType.LIMIT) return ""

        val usedText = formatDuration(max(0L, usedSeconds))
        val durationLimited = rule.limitMode != LimitMode.WINDOW_ONLY && rule.dailyAllowedMinutes > 0
        if (!durationLimited) return "今日已用: $usedText"

        val allowedSeconds = max(0L, rule.dailyAllowedMinutes * 60L + bonusSeconds)
        val remainingText = formatDuration(max(0L, allowedSeconds - usedSeconds))
        val bonusText = when {
            bonusSeconds > 0L -> " / 今日奖励${formatDuration(bonusSeconds)}"
            bonusSeconds < 0L -> " / 今日惩罚${formatDuration(safeMagnitude(bonusSeconds))}"
            else -> ""
        }
        return "已用$usedText / 剩余$remainingText$bonusText"
    }

    fun formatSignedDuration(totalSeconds: Long): String = when {
        totalSeconds > 0L -> "+${formatDuration(totalSeconds)}"
        totalSeconds < 0L -> "-${formatDuration(safeMagnitude(totalSeconds))}"
        else -> "0分钟"
    }

    fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = max(0L, totalSeconds)
        if (safeSeconds == 0L) return "0分钟"
        val totalMinutes = safeSeconds / 60L
        if (totalMinutes <= 0L) return "不足1分钟"

        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours <= 0L -> "${minutes}分钟"
            minutes == 0L -> "${hours}小时"
            else -> "${hours}小时${minutes}分钟"
        }
    }

    private fun safeMagnitude(value: Long): Long =
        if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)
}
