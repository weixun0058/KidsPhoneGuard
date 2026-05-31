package com.kidsphoneguard.service.guard

import com.kidsphoneguard.service.accessibility.GuardActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatFinderGuardTest {

    /**
     * 验证当前视频号类名匹配仍保持原有包名/类名规则。
     * 输入：命中与未命中的包名/类名形状；输出：断言当前规则没有被扩大。
     */
    @Test
    fun finderClassShapeMatchesCurrentRuleOnly() {
        assertTrue(
            WeChatFinderGuard.shouldBlockCurrentFinderShape(
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
                blockEnabled = true,
                globalUnlockEnabled = false
            )
        )
        assertFalse(
            WeChatFinderGuard.shouldBlockCurrentFinderShape(
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.ui.LauncherUI",
                blockEnabled = true,
                globalUnlockEnabled = false
            )
        )
        assertFalse(
            WeChatFinderGuard.shouldBlockCurrentFinderShape(
                packageName = "com.example.app",
                className = "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
                blockEnabled = true,
                globalUnlockEnabled = false
            )
        )
    }

    /**
     * 验证视频号 guard 在不同输入形状上返回稳定的 router 结果语义。
     * 输入：非微信包、命中视频号形状与全局解锁场景；输出：断言 Continue/Consumed 语义稳定。
     */
    @Test
    fun resultSemanticsMatchCurrentFinderRule() {
        assertEquals(
            GuardActionResult.Continue,
            WeChatFinderGuard.resultForCurrentShape(
                packageName = "com.example.app",
                className = "com.example.AnyActivity",
                blockEnabled = true,
                globalUnlockEnabled = false
            )
        )

        val finderResult = WeChatFinderGuard.resultForCurrentShape(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.finder.ui.FinderFeedDetailUI",
            blockEnabled = true,
            globalUnlockEnabled = false
        )
        assertTrue(finderResult is GuardActionResult.Consumed)
        assertFalse(finderResult.continueRouting)
        assertEquals("wechat_finder", (finderResult as GuardActionResult.Consumed).reason)
        assertTrue(finderResult.hasSideEffect)

        assertEquals(
            GuardActionResult.Continue,
            WeChatFinderGuard.resultForCurrentShape(
                packageName = "com.tencent.mm",
                className = "com.tencent.mm.plugin.finder.ui.FinderFeedDetailUI",
                blockEnabled = true,
                globalUnlockEnabled = true
            )
        )
    }
}
