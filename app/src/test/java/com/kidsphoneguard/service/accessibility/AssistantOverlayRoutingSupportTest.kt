package com.kidsphoneguard.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantOverlayRoutingSupportTest {

    /**
     * 验证助手包识别仅命中既有 gameassistant 包。
     * 输入：助手包与无关包；输出：断言识别结果。
     */
    @Test
    fun recognizesAssistantPackages() {
        val support = createSupport()

        assertTrue(support.isAssistantPackage("com.huawei.gameassistant"))
        assertTrue(support.isAssistantPackage("com.hihonor.gameassistant"))
        assertFalse(support.isAssistantPackage("com.example.other"))
    }

    /**
     * 验证助手事件包优先映射到活动窗口包名，并跳过 self-app。
     * 输入：助手事件包、活动包与 self-app 规则；输出：断言最终策略包名。
     */
    @Test
    fun resolvePolicyPackagePrefersActiveWindowAndSkipsSelfApp() {
        var activePackage = "com.example.target"
        val support = createSupport(
            readActivePackageName = { activePackage },
            getRecentTopPackageName = { "com.example.fallback" },
            isSelfApp = { false }
        )

        assertEquals(
            "com.example.target",
            support.resolvePolicyPackage("com.huawei.gameassistant")
        )

        activePackage = "com.example.self"
        val selfAppSupport = createSupport(
            readActivePackageName = { activePackage },
            getRecentTopPackageName = { "com.example.fallback" },
            isSelfApp = { packageName -> packageName == "com.example.self" }
        )
        assertEquals(
            "com.huawei.gameassistant",
            selfAppSupport.resolvePolicyPackage("com.huawei.gameassistant")
        )
    }

    /**
     * 验证助手补偿调度复用同一份 router state，去抖后只触发一次策略检查。
     * 输入：共享 `EventRoutingState` 与固定当前时间；输出：断言 launch 次数与 follow-up 结果。
     */
    @Test
    fun followUpChecksReuseSharedRoutingState() {
        val scheduledActions = mutableListOf<() -> Unit>()
        val launchedPackages = mutableListOf<String>()
        val support = createSupport(
            scheduleFollowUpAction = { _, _, action -> scheduledActions += action },
            readActivePackageName = { "com.example.target" },
            launchPolicyCheck = { packageName -> launchedPackages += packageName },
            nowProvider = { 1_000L }
        )

        val result = support.scheduleFollowUpChecks(EventRoutingState())

        assertEquals(GuardActionResult.ScheduleFollowUp("assistant_follow_up"), result)
        assertEquals(3, scheduledActions.size)

        scheduledActions.forEach { action -> action() }

        assertEquals(listOf("com.example.target"), launchedPackages)
    }

    /**
     * 构建用于测试的 assistant routing support。
     * 输入：可覆盖的依赖；输出：测试实例。
     */
    private fun createSupport(
        scheduleFollowUpAction: (key: String, delayMs: Long, action: () -> Unit) -> Unit =
            { _, _, _ -> },
        readActivePackageName: () -> String = { "" },
        getRecentTopPackageName: () -> String? = { null },
        isSelfApp: (String) -> Boolean = { false },
        isInWhitelist: (String) -> Boolean = { false },
        launchPolicyCheck: (String) -> Unit = {},
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ): AssistantOverlayRoutingSupport {
        return AssistantOverlayRoutingSupport(
            logTag = "AssistantOverlayRoutingSupportTest",
            debounceIntervalMs = 500L,
            scheduleFollowUpAction = scheduleFollowUpAction,
            readActivePackageName = readActivePackageName,
            getRecentTopPackageName = getRecentTopPackageName,
            isSelfApp = isSelfApp,
            isInWhitelist = isInWhitelist,
            launchPolicyCheck = launchPolicyCheck,
            nowProvider = nowProvider,
            logDebug = {}
        )
    }
}
