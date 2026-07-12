package com.kidsphoneguard.ui.config

import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleUsageFormatterTest {
    @Test
    fun `formats a duration limited rule with today's bonus`() {
        val rule = AppRule(
            packageName = "example.game",
            appName = "Game",
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.DURATION_ONLY,
            dailyAllowedMinutes = 30
        )

        assertEquals(
            "已用10分钟 / 剩余25分钟 / 今日奖励5分钟",
            RuleUsageFormatter.summary(rule, usedSeconds = 600, bonusSeconds = 300)
        )
    }

    @Test
    fun `formats a window only rule without a duration allowance`() {
        val rule = AppRule(
            packageName = "example.game",
            appName = "Game",
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.WINDOW_ONLY
        )

        assertEquals("今日已用: 1小时1分钟", RuleUsageFormatter.summary(rule, usedSeconds = 3_660))
    }

    @Test
    fun `does not show usage summary for non limit rules`() {
        val rule = AppRule(packageName = "example.app", appName = "App", ruleType = RuleType.BLOCK)

        assertEquals("", RuleUsageFormatter.summary(rule, usedSeconds = 60))
    }
}
