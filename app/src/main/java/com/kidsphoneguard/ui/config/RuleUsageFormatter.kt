package com.kidsphoneguard.ui.config

import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import kotlin.math.max

/** 规则卡片使用的纯文本用量摘要，独立于 Compose，便于单测。 */
object RuleUsageFormatter {
    fun summary(rule: AppRule, usedSeconds: Long, bonusSeconds: Long = 0L): String {
        if (rule.ruleType != RuleType.LIMIT) return ""

        val usedText = formatDuration(max(0L, usedSeconds))
        val durationLimited = rule.limitMode != LimitMode.WINDOW_ONLY && rule.dailyAllowedMinutes > 0
        if (!durationLimited) return "今日已用: $usedText"

        val safeBonusSeconds = max(0L, bonusSeconds)
        val allowedSeconds = rule.dailyAllowedMinutes * 60L + safeBonusSeconds
        val remainingText = formatDuration(max(0L, allowedSeconds - usedSeconds))
        val bonusText = if (safeBonusSeconds > 0L) {
            " / 今日奖励${formatDuration(safeBonusSeconds)}"
        } else {
            ""
        }
        return "已用$usedText / 剩余$remainingText$bonusText"
    }

    fun formatDuration(totalSeconds: Long): String {
        val totalMinutes = max(0L, totalSeconds) / 60L
        if (totalMinutes <= 0L) return "不足1分钟"

        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours <= 0L -> "${minutes}分钟"
            minutes == 0L -> "${hours}小时"
            else -> "${hours}小时${minutes}分钟"
        }
    }
}
