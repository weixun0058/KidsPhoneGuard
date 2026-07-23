package com.kidsphoneguard.service.guard

import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsDecisionType
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

    @Test
    fun setupAllowanceCannotBypassGuardianGlobalPackageBlock() {
        assertFalse(
            ProtectedSurfaceGuard.shouldAllowProtectedSurfaceSuppression(
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = true,
                guardianGlobalPackageBlocked = true
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldAllowProtectedSurfaceSuppression(
                globalUnlockEnabled = false,
                setupSettingsAccessAllowed = true,
                guardianGlobalPackageBlocked = false
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldAllowProtectedSurfaceSuppression(
                globalUnlockEnabled = true,
                setupSettingsAccessAllowed = true,
                guardianGlobalPackageBlocked = true
            )
        )
    }

    @Test
    fun huaweiSmallWindowCloseControl_requiresExactEnabledClickableVisibleChromeButton() {
        assertTrue(
            ProtectedSurfaceGuard.isProtectedSmallWindowCloseControl(
                viewIdResourceName = "androidhwext:id/hw_multiwindow_close_window",
                clickable = true,
                enabled = true,
                visibleToUser = true
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.isProtectedSmallWindowCloseControl(
                viewIdResourceName = "android:id/button1",
                clickable = true,
                enabled = true,
                visibleToUser = true
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.isProtectedSmallWindowCloseControl(
                viewIdResourceName = "androidhwext:id/hw_multiwindow_close_window",
                clickable = false,
                enabled = true,
                visibleToUser = true
            )
        )
    }

    @Test
    fun smallWindowCloseResult_retriesOnlyWhenSmallWindowStillExists() {
        assertFalse(
            ProtectedSurfaceGuard.shouldRetrySmallWindowCloseAfterVerification(
                smallWindowStillPresent = false
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldRetrySmallWindowCloseAfterVerification(
                smallWindowStillPresent = true
            )
        )
    }

    @Test
    fun smallWindowCloseAttempt_isThrottledToAvoidRepeatedGestureFlashing() {
        assertTrue(
            ProtectedSurfaceGuard.shouldAttemptSmallWindowClose(
                lastAttemptAt = 0L,
                now = 1_000L,
                cooldownMs = 120L
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.shouldAttemptSmallWindowClose(
                lastAttemptAt = 1_000L,
                now = 1_119L,
                cooldownMs = 120L
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldAttemptSmallWindowClose(
                lastAttemptAt = 1_000L,
                now = 1_120L,
                cooldownMs = 120L
            )
        )
    }

    @Test
    fun globalSmallWindowGuard_checksWindowAndContentEvents() {
        assertTrue(
            ProtectedSurfaceGuard.shouldCheckAnySmallWindowForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldCheckAnySmallWindowForEvent(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldCheckAnySmallWindowForEvent(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.shouldCheckAnySmallWindowForEvent(
                AccessibilityEvent.TYPE_VIEW_CLICKED
            )
        )
    }

    @Test
    fun huaweiFreeformBounds_detectsWindowBeforeCloseNodeAppears() {
        assertTrue(
            ProtectedSurfaceGuard.isHuaweiFreeformWindowBounds(
                left = 113,
                top = 496,
                right = 1_193,
                bottom = 2_277,
                screenWidth = 1_080,
                screenHeight = 2_400
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.isHuaweiFreeformWindowBounds(
                left = 0,
                top = 0,
                right = 1_080,
                bottom = 2_400,
                screenWidth = 1_080,
                screenHeight = 2_400
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.isHuaweiFreeformWindowBounds(
                left = 967,
                top = 740,
                right = 1_273,
                bottom = 1_010,
                screenWidth = 1_080,
                screenHeight = 2_400
            )
        )
    }

    @Test
    fun smallWindowCloseGesture_usesFixedHonorCloseButtonPosition() {
        val tapPoint = ProtectedSurfaceGuard.resolveHuaweiFixedSmallWindowCloseTapPoint(
            screenWidth = 1_080,
            screenHeight = 2_400
        )
        assertTrue(
            tapPoint != null &&
                tapPoint.first in 889.9f..890.1f &&
                tapPoint.second in 549.9f..550.1f
        )
    }

    @Test
    fun smallWindowCloseGesture_rejectsInvalidScreenSize() {
        assertTrue(
            ProtectedSurfaceGuard.resolveHuaweiFixedSmallWindowCloseTapPoint(
                screenWidth = 0,
                screenHeight = 2_400
            ) == null
        )
    }

    @Test
    fun protectedOverlayStaysWhileUntrustedTargetIsStillActive() {
        assertFalse(
            ProtectedSurfaceGuard.shouldReleaseProtectedOverlayForDecision(
                ProtectedSettingsDecisionType.OBSERVE
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.shouldReleaseProtectedOverlay(
                targetStillActive = true,
                suppressionAllowed = false
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldRepeatProtectedOverlayChecks(
                targetStillActive = true,
                suppressionAllowed = false,
                isFinalCheck = true
            )
        )
    }

    @Test
    fun protectedOverlayReleasesAfterTargetLeavesOrParentAllowsAccess() {
        assertTrue(
            ProtectedSurfaceGuard.shouldReleaseProtectedOverlayForDecision(
                ProtectedSettingsDecisionType.ALLOW
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldReleaseProtectedOverlay(
                targetStillActive = false,
                suppressionAllowed = false
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldReleaseProtectedOverlay(
                targetStillActive = true,
                suppressionAllowed = true
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.shouldRepeatProtectedOverlayChecks(
                targetStillActive = true,
                suppressionAllowed = true,
                isFinalCheck = true
            )
        )
    }

    @Test
    fun protectedNavigationStopsAfterTargetIsNoLongerInteractive() {
        assertTrue(
            ProtectedSurfaceGuard.shouldExecuteProtectedSurfaceNavigation(
                delayMs = 0L,
                targetStillInteractive = false
            )
        )
        assertTrue(
            ProtectedSurfaceGuard.shouldExecuteProtectedSurfaceNavigation(
                delayMs = 60L,
                targetStillInteractive = true
            )
        )
        assertFalse(
            ProtectedSurfaceGuard.shouldExecuteProtectedSurfaceNavigation(
                delayMs = 60L,
                targetStillInteractive = false
            )
        )
    }
}
