package com.kidsphoneguard.service.guard

import com.kidsphoneguard.service.accessibility.GuardActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSurfaceGuardTest {

    /**
     * 验证系统面板命中时会返回带副作用的 `Consumed` 结果。
     * 输入：handled=true；输出：断言返回结果为 `system_panel_collapse`。
     */
    @Test
    fun handledSystemPanelReturnsConsumedResult() {
        val result = SystemSurfaceGuard.collapseResultForHandled(true)

        assertTrue(result is GuardActionResult.Consumed)
        val consumed = result as GuardActionResult.Consumed
        assertEquals("system_panel_collapse", consumed.reason)
        assertTrue(consumed.hasSideEffect)
    }

    /**
     * 验证系统面板未命中时会继续后续路由。
     * 输入：handled=false；输出：断言返回结果为 `Continue`。
     */
    @Test
    fun unhandledSystemPanelReturnsContinue() {
        val result = SystemSurfaceGuard.collapseResultForHandled(false)

        assertSame(GuardActionResult.Continue, result)
    }
}
