package com.kidsphoneguard.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AccessibilityEventRouterTest {

    /**
     * 验证 `Continue` 结果会让 router 继续执行后续步骤。
     * 输入：三个按顺序执行的路由步骤；输出：断言所有步骤都会被执行。
     */
    @Test
    fun continueResultAllowsNextStepsToRun() {
        val visited = mutableListOf<String>()

        val result = AccessibilityEventRouter.runRouteSteps(
            {
                visited += "step1"
                GuardActionResult.Continue
            },
            {
                visited += "step2"
                GuardActionResult.Continue
            },
            {
                visited += "step3"
                GuardActionResult.Continue
            }
        )

        assertSame(GuardActionResult.Continue, result)
        assertEquals(listOf("step1", "step2", "step3"), visited)
    }

    /**
     * 验证 `Consumed` 会立即终止后续步骤执行。
     * 输入：包含一个消费步骤的路由链；输出：断言消费步骤之后的步骤不会再执行。
     */
    @Test
    fun consumedResultStopsRemainingSteps() {
        val visited = mutableListOf<String>()
        val consumed = GuardActionResult.Consumed(
            reason = "consumed",
            hasSideEffect = false
        )

        val result = AccessibilityEventRouter.runRouteSteps(
            {
                visited += "step1"
                GuardActionResult.Continue
            },
            {
                visited += "step2"
                consumed
            },
            {
                visited += "step3"
                GuardActionResult.Continue
            }
        )

        assertSame(consumed, result)
        assertEquals(listOf("step1", "step2"), visited)
    }

    /**
     * 验证 `ScheduleFollowUp` 会作为最终结果直接停止路由。
     * 输入：包含 follow-up 步骤的路由链；输出：断言 follow-up 之后的步骤不会再执行。
     */
    @Test
    fun scheduleFollowUpResultStopsRemainingSteps() {
        val visited = mutableListOf<String>()
        val scheduled = GuardActionResult.ScheduleFollowUp("assistant_follow_up")

        val result = AccessibilityEventRouter.runRouteSteps(
            {
                visited += "step1"
                GuardActionResult.Continue
            },
            {
                visited += "step2"
                scheduled
            },
            {
                visited += "step3"
                GuardActionResult.Continue
            }
        )

        assertSame(scheduled, result)
        assertEquals(listOf("step1", "step2"), visited)
    }

    /**
     * 验证 `Blocked` 会作为最终结果直接停止路由。
     * 输入：包含阻断步骤的路由链；输出：断言阻断之后的步骤不会再执行。
     */
    @Test
    fun blockedResultStopsRemainingSteps() {
        val visited = mutableListOf<String>()
        val blocked = GuardActionResult.Blocked(
            packageName = "com.kidsphoneguard",
            reason = "blocked"
        )

        val result = AccessibilityEventRouter.runRouteSteps(
            {
                visited += "step1"
                GuardActionResult.Continue
            },
            {
                visited += "step2"
                blocked
            },
            {
                visited += "step3"
                GuardActionResult.Continue
            }
        )

        assertSame(blocked, result)
        assertEquals(listOf("step1", "step2"), visited)
    }
}
