package com.kidsphoneguard.service.guard.oem

import com.kidsphoneguard.service.accessibility.GuardActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiPowerSaveHandlerTest {

    /**
     * 验证当前省电活动页匹配仍保持现有包名/类名规则。
     * 输入：命中与未命中的包名/类名形状；输出：断言当前规则没有被扩大。
     */
    @Test
    fun powerSaveActivityShapeMatchesCurrentRuleOnly() {
        assertTrue(
            HuaweiPowerSaveHandler.matchesCurrentPowerSaveActivity(
                packageName = "com.huawei.android.launcher",
                className = "powersavemode.PowerSaveModeLauncher",
                globalUnlockAllowed = false
            )
        )
        assertFalse(
            HuaweiPowerSaveHandler.matchesCurrentPowerSaveActivity(
                packageName = "com.huawei.android.launcher",
                className = "com.example.OtherActivity",
                globalUnlockAllowed = false
            )
        )
        assertFalse(
            HuaweiPowerSaveHandler.matchesCurrentPowerSaveActivity(
                packageName = "com.example.launcher",
                className = "powersavemode.PowerSaveModeLauncher",
                globalUnlockAllowed = false
            )
        )
    }

    /**
     * 验证省电 handler 的 router 结果语义在命中与未命中形状上保持稳定。
     * 输入：命中与未命中的包名/类名；输出：断言 Continue/Consumed 语义符合当前规则。
     */
    @Test
    fun resultSemanticsMatchCurrentPowerSaveRule() {
        assertEquals(
            GuardActionResult.Continue,
            HuaweiPowerSaveHandler.resultForCurrentActivityShape(
                packageName = "com.example.launcher",
                className = "powersavemode.PowerSaveModeLauncher",
                globalUnlockAllowed = false
            )
        )

        val hitResult = HuaweiPowerSaveHandler.resultForCurrentActivityShape(
            packageName = "com.huawei.android.launcher",
            className = "PowerSaveModeLauncher",
            globalUnlockAllowed = false
        )
        assertTrue(hitResult is GuardActionResult.Consumed)
        assertFalse(hitResult.continueRouting)
        assertEquals("power_save_exit", (hitResult as GuardActionResult.Consumed).reason)
        assertTrue(hitResult.hasSideEffect)
    }

    /**
     * 验证可见 root 场景下的包名/信号判断仍保持当前语义。
     * 输入：命中与未命中的可见包名/信号；输出：断言 visible power-save 判断结果稳定。
     */
    @Test
    fun visiblePowerSaveSignalMatchesCurrentRule() {
        assertTrue(
            HuaweiPowerSaveHandler.shouldExitVisiblePowerSave(
                packageName = "com.hihonor.android.launcher",
                signal = "关闭超级省电 返回桌面",
                globalUnlockAllowed = false
            )
        )
        assertFalse(
            HuaweiPowerSaveHandler.shouldExitVisiblePowerSave(
                packageName = "com.hihonor.android.launcher",
                signal = "普通桌面内容",
                globalUnlockAllowed = false
            )
        )
        assertFalse(
            HuaweiPowerSaveHandler.shouldExitVisiblePowerSave(
                packageName = "com.hihonor.android.launcher",
                signal = "关闭超级省电 返回桌面",
                globalUnlockAllowed = true
            )
        )
    }
}
