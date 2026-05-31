package com.kidsphoneguard.service.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedSurfaceStateTest {

    /**
     * 验证 protected window 日志节流会拦截冷却期内的重复签名。
     * 输入：同一签名在冷却期内的两次调用；输出：断言第一次通过、第二次被节流。
     */
    @Test
    fun protectedWindowLogIsThrottledBySignatureAndCooldown() {
        val state = ProtectedSurfaceState()

        assertTrue(state.shouldLogProtectedWindow("pkg|windows", now = 1000L, cooldownMs = 500L))
        assertFalse(state.shouldLogProtectedWindow("pkg|windows", now = 1200L, cooldownMs = 500L))
        assertTrue(state.shouldLogProtectedWindow("pkg|windows", now = 1600L, cooldownMs = 500L))
    }

    /**
     * 验证 protected settings 决策日志节流会按签名和冷却期生效。
     * 输入：重复与变更的决策签名；输出：断言重复签名被节流，新签名可再次记录。
     */
    @Test
    fun protectedSettingsDecisionLogTracksSignatureAndCooldown() {
        val state = ProtectedSurfaceState()

        assertTrue(state.shouldLogProtectedSettingsDecision("allow|pkg", now = 1000L, cooldownMs = 500L))
        assertFalse(state.shouldLogProtectedSettingsDecision("allow|pkg", now = 1200L, cooldownMs = 500L))
        assertTrue(state.shouldLogProtectedSettingsDecision("block|pkg", now = 1200L, cooldownMs = 500L))
    }

    /**
     * 验证 protected window sweep 会按包名和冷却期去重。
     * 输入：同一包名的连续 sweep 请求；输出：断言冷却期内重复 sweep 被跳过。
     */
    @Test
    fun protectedWindowSweepUsesPackageCooldown() {
        val state = ProtectedSurfaceState()

        assertTrue(state.shouldProcessProtectedWindowSweep("com.android.settings", now = 1000L, cooldownMs = 180L))
        assertFalse(state.shouldProcessProtectedWindowSweep("com.android.settings", now = 1100L, cooldownMs = 180L))
        assertTrue(state.shouldProcessProtectedWindowSweep("com.android.settings", now = 1300L, cooldownMs = 180L))
    }

    /**
     * 验证 protected surface suppression 会按包名和冷却期去重。
     * 输入：同一包名的连续 suppression 请求；输出：断言冷却期内重复 suppression 被跳过。
     */
    @Test
    fun protectedSurfaceSuppressionUsesPackageCooldown() {
        val state = ProtectedSurfaceState()

        assertTrue(state.shouldSuppressProtectedSurface("com.android.settings", now = 1000L, cooldownMs = 120L))
        assertFalse(state.shouldSuppressProtectedSurface("com.android.settings", now = 1080L, cooldownMs = 120L))
        assertTrue(state.shouldSuppressProtectedSurface("com.android.settings", now = 1200L, cooldownMs = 120L))
    }
}
