package com.kidsphoneguard.service.block

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockCoordinatorTest {

    /**
     * 验证非 protected surface 的重复 overlay 会被直接短路。
     * 输入：overlay 显示状态、当前遮蔽包名、目标包名与 protected 标记；输出：断言是否跳过重复拦截。
     */
    @Test
    fun duplicateNonProtectedOverlayIsSkipped() {
        assertTrue(
            AppBlockCoordinator.shouldSkipDuplicateOverlay(
                overlayShowing = true,
                currentBlockedPackage = "com.example.app",
                targetPackage = "com.example.app",
                protectedSystemSurface = false
            )
        )
        assertFalse(
            AppBlockCoordinator.shouldSkipDuplicateOverlay(
                overlayShowing = true,
                currentBlockedPackage = "com.example.app",
                targetPackage = "com.example.app",
                protectedSystemSurface = true
            )
        )
    }

    /**
     * 验证 protected surface 在新展示 overlay 时不会再额外走 normal release check。
     * 输入：protected 标记与是否重展示 overlay；输出：断言 normal release check 的调度开关。
     */
    @Test
    fun protectedOverlayUsesDedicatedReleaseTiming() {
        assertFalse(
            AppBlockCoordinator.shouldScheduleNormalOverlayRelease(
                protectedSystemSurface = true,
                shouldReshowOverlay = true
            )
        )
        assertTrue(
            AppBlockCoordinator.shouldScheduleNormalOverlayRelease(
                protectedSystemSurface = true,
                shouldReshowOverlay = false
            )
        )
        assertTrue(
            AppBlockCoordinator.shouldScheduleNormalOverlayRelease(
                protectedSystemSurface = false,
                shouldReshowOverlay = true
            )
        )
    }
}
