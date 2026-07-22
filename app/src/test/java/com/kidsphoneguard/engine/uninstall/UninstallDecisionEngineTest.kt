package com.kidsphoneguard.engine.uninstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallDecisionEngineTest {

    private val engine = UninstallDecisionEngine()
    private val lockedState = UninstallRuntimeState(
        isGlobalUnlockEnabled = false,
        isSetupAccessAllowed = false
    )

    @Test
    fun installerDialogWithAppIdentity_blocksWholePage() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.packageinstaller",
                text = "要卸载此应用吗？拉钩守护"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "installer_uninstall_confirm_page")
        assertEquals("拉钩守护", decision.matchedTarget)
    }

    @Test
    fun installerDialogWithoutAppIdentity_isAllowed() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.packageinstaller",
                text = "要卸载此应用吗？其他应用"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "no_uninstall_threat_detected")
    }

    @Test
    fun installerInstallPromptWithAppIdentityButNoUninstallSignal_isAllowed() {
        // 2026-07-23 真机回归：安装/更新确认页提到本应用名，但没有卸载语义，必须放行，
        // 否则 pm install / 商店更新本应用的确认页会被误杀，安装流程挂起。
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.packageinstaller",
                text = "要安装此应用吗？拉钩守护 取消 安装"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "no_uninstall_threat_detected")
    }

    @Test
    fun clickedUninstallTextWithAppIdentity_blocksOnlyTheAction() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.settings",
                text = "儿童手机守护 应用信息",
                clickedText = "卸载"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_ACTION, "uninstall_action_click")
        assertTrue(decision.matchedUninstallKeywords.contains("卸载"))
    }

    @Test
    fun launcherWindowWithAppIdentityAndUninstallKeyword_blocksWholePage() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "卸载 KidsPhoneGuard？卸载后将删除此应用"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "launcher_uninstall_confirm_page")
    }

    @Test
    fun globalUnlock_allowsInstallerDialog() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.packageinstaller",
                text = "要卸载此应用吗？拉钩守护"
            ),
            runtimeState = UninstallRuntimeState(
                isGlobalUnlockEnabled = true,
                isSetupAccessAllowed = false
            )
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "global_unlock_enabled")
    }

    @Test
    fun setupAccessAllowed_allowsLauncherUninstallConfirm() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "卸载 KidsPhoneGuard？"
            ),
            runtimeState = UninstallRuntimeState(
                isGlobalUnlockEnabled = false,
                isSetupAccessAllowed = true
            )
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "setup_access_allowed")
    }

    @Test
    fun unrelatedPackageWithoutUninstallSignal_isAllowedBeforeAnyOtherRule() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.example.unrelated",
                text = "KidsPhoneGuard 正在运行"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "not_uninstall_candidate")
    }

    @Test
    fun miuiChineseConfirmText_blocksInstallerPage() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.packageinstaller",
                className = "com.miui.packageinstaller.ui.UninstallConfirmActivity",
                text = "卸载应用 儿童手机守护 此应用将被卸载 取消 确定"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "installer_uninstall_confirm_page")
        assertEquals("儿童手机守护", decision.matchedTarget)
    }

    @Test
    fun englishUninstallClickWithAppIdentity_blocksAction() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.sec.android.app.launcher",
                text = "KidsPhoneGuard App info",
                clickedText = "Uninstall"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_ACTION, "uninstall_action_click")
        assertTrue(decision.matchedUninstallKeywords.contains("uninstall"))
    }

    @Test
    fun englishLauncherUninstallConfirm_blocksWholePage() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.google.android.apps.nexuslauncher",
                text = "Uninstall KidsPhoneGuard? Remove app data"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "launcher_uninstall_confirm_page")
    }

    @Test
    fun launcherHomeScreenWithoutUninstallSignal_isAllowed() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.huawei.android.launcher",
                text = "儿童手机守护 微信 相机 设置"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "no_uninstall_threat_detected")
    }

    @Test
    fun processSuffixPackage_isNormalizedForOwnership() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.packageinstaller:installer",
                text = "Uninstall com.kidsphoneguard?"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "installer_uninstall_confirm_page")
    }

    @Test
    fun overlayReleasePolicy_releasesWhenThreatGone() {
        assertTrue(shouldReleaseUninstallOverlay(suppressionAllowed = false, threatStillPresent = false))
    }

    @Test
    fun overlayReleasePolicy_releasesWhenParentAllowedEvenIfThreatRemains() {
        assertTrue(shouldReleaseUninstallOverlay(suppressionAllowed = true, threatStillPresent = true))
    }

    @Test
    fun overlayReleasePolicy_holdsWhileThreatRemains() {
        assertFalse(shouldReleaseUninstallOverlay(suppressionAllowed = false, threatStillPresent = true))
    }

    @Test
    fun overlayRearmPolicy_rearmsOnlyOnFinalCheckWithinCycleCap() {
        assertTrue(shouldRearmUninstallOverlayChecks(isFinalCheck = true, cycle = 0, maxCycles = 1))
        assertFalse(shouldRearmUninstallOverlayChecks(isFinalCheck = false, cycle = 0, maxCycles = 1))
        assertFalse(shouldRearmUninstallOverlayChecks(isFinalCheck = true, cycle = 1, maxCycles = 1))
    }

    private fun snapshot(
        packageName: String,
        className: String = "android.app.Activity",
        text: String = "",
        clickedText: String = "",
        windowPackages: Set<String> = emptySet()
    ): UninstallSurfaceSnapshot {
        return UninstallSurfaceSnapshot(
            packageName = packageName,
            className = className,
            pageText = text,
            windowPackages = windowPackages,
            clickedText = clickedText
        )
    }

    private fun assertDecision(
        decision: UninstallDecision,
        expectedType: UninstallDecisionType,
        expectedReason: String
    ) {
        assertEquals(expectedType, decision.type)
        assertEquals(expectedReason, decision.reason)
    }
}
