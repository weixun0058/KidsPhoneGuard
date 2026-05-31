package com.kidsphoneguard.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardActionResultTest {

    /**
     * 验证 Continue 结果会继续路由且没有副作用。
     * 输入：无；输出：断言 Continue 的语义标记保持正确。
     */
    @Test
    fun continueResultKeepsRoutingWithoutSideEffect() {
        val result: GuardActionResult = GuardActionResult.Continue

        assertTrue(result.continueRouting)
        assertFalse(result.hasSideEffect)
    }

    /**
     * 验证 Consumed 结果会停止路由，并显式暴露副作用标记。
     * 输入：无；输出：断言 Consumed 的两个语义字段符合约定。
     */
    @Test
    fun consumedResultStopsRoutingAndKeepsExplicitSideEffectFlag() {
        val withoutSideEffect = GuardActionResult.Consumed(
            reason = "ignored",
            hasSideEffect = false
        )
        val withSideEffect = GuardActionResult.Consumed(
            reason = "handled",
            hasSideEffect = true
        )

        assertFalse(withoutSideEffect.continueRouting)
        assertFalse(withoutSideEffect.hasSideEffect)
        assertFalse(withSideEffect.continueRouting)
        assertTrue(withSideEffect.hasSideEffect)
    }

    /**
     * 验证 ScheduleFollowUp 与 Blocked 都会停止路由并标记副作用已产生。
     * 输入：无；输出：断言两种结果的公共路由语义保持一致。
     */
    @Test
    fun followUpAndBlockedResultsStopRoutingWithSideEffects() {
        val scheduled: GuardActionResult = GuardActionResult.ScheduleFollowUp("scheduled")
        val blocked: GuardActionResult = GuardActionResult.Blocked(
            packageName = "com.kidsphoneguard",
            reason = "blocked"
        )

        assertFalse(scheduled.continueRouting)
        assertTrue(scheduled.hasSideEffect)
        assertFalse(blocked.continueRouting)
        assertTrue(blocked.hasSideEffect)
    }
}
