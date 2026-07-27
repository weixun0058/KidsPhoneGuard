package com.kidsphoneguard.engine.uninstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallDecisionEngineTest {

    private val engine = UninstallDecisionEngine()
    private val lockedState = UninstallRuntimeState(
        isGlobalUnlockEnabled = false
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
    fun miuiResourceIdDialogSnapshot_blocksExactLiveConfirmShape() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "com.miui.home:id/uninstall_dialog 卸载“拉钩守护” " +
                    "卸载后其所有数据也将被删除 android:id/button1"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "launcher_uninstall_confirm_page")
        assertEquals("拉钩守护", decision.matchedTarget)
    }

    @Test
    fun miuiTargetAppShortcutMenu_blocksWithoutAccessibleButtonText() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "com.miui.home:id/shortcut_menu"
            ).copy(targetAppShortcutMenuVisible = true),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "launcher_target_app_shortcut_menu")
        assertEquals("target_shortcut_menu", decision.matchedTarget)
    }

    @Test
    fun globalUnlock_allowsInstallerDialog() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.android.packageinstaller",
                text = "要卸载此应用吗？拉钩守护"
            ),
            runtimeState = UninstallRuntimeState(
                isGlobalUnlockEnabled = true
            )
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "global_unlock_enabled")
    }

    @Test
    fun setupAccessDoesNotCreateAnUninstallBypass() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "卸载 KidsPhoneGuard？"
            ),
            runtimeState = UninstallRuntimeState(
                isGlobalUnlockEnabled = false
            )
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "launcher_uninstall_confirm_page")
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

    @Test
    fun installerDialogWithUninstallKeywordAndRecentLongPressButNoAppName_blocksWholePage() {
        // MIUI 卸载确认弹窗可能不显示应用名：长按归因成立时必须仍能拦截。
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.packageinstaller",
                text = "要卸载此应用吗？取消 确定"
            ).copy(recentTargetAppLongPress = true),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_PAGE, "installer_uninstall_confirm_page")
        assertEquals("recent_long_press", decision.matchedTarget)
    }

    @Test
    fun clickedUninstallWithRecentLongPressButNoPageIdentity_blocksAction() {
        // MIUI 桌面长按本应用图标后点"卸载"：页面扫描不含应用名时，长按归因必须保住拦截。
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "shortcut_menu_layer",
                clickedText = "卸载"
            ).copy(recentTargetAppLongPress = true),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.BLOCK_ACTION, "uninstall_action_click")
    }

    @Test
    fun clickedUninstallWithoutIdentityAndWithoutLongPress_isAllowed() {
        // 无应用标识且无长按归因：不得误拦（例如孩子卸载别的应用）。
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.miui.home",
                text = "shortcut_menu_layer",
                clickedText = "卸载"
            ),
            runtimeState = lockedState
        )

        assertDecision(decision, UninstallDecisionType.ALLOW, "no_uninstall_threat_detected")
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
