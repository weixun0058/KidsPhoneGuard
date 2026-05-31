package com.kidsphoneguard.service.guard

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedSurfaceGuardTest {

    /**
     * 验证 protected window sweep helper 在候选包和 installer/market 包上都会开启。
     * 输入：候选包、installer/market 包与普通包三种形状；输出：断言 sweep 开关保持原有语义。
     */
    @Test
    fun shouldSweepProtectedWindowsMatchesCurrentShapes() {
        assertTrue(
            ProtectedSurfaceGuard.shouldSweepProtectedWindows(
                isCandidatePackage = true,
                isInstallerOrMarketPackage = false,
                eventType = null
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldSweepProtectedWindows(
                isCandidatePackage = false,
                isInstallerOrMarketPackage = true,
                eventType = null
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldSweepProtectedWindows(
                isCandidatePackage = false,
                isInstallerOrMarketPackage = false,
                eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.shouldSweepProtectedWindows(
                isCandidatePackage = false,
                isInstallerOrMarketPackage = false,
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED
            )
        )
    }

    /**
     * 验证同基包判断仍会忽略 `:` 之后的进程后缀。
     * 输入：同基包与不同基包两组包名；输出：断言基包比较结果保持不变。
     */
    @Test
    fun sameBasePackageIgnoresProcessSuffix() {
        assertTrue(
            ProtectedSurfaceGuard.isSameBasePackage(
                first = "com.android.settings:subprocess",
                second = "com.android.settings"
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.isSameBasePackage(
                first = "com.android.settings",
                second = "com.xiaomi.market"
            )
        )
    }
}
