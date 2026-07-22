package com.kidsphoneguard.engine.settingsprotection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedSettingsDecisionEngineTest {

    private val engine = ProtectedSettingsDecisionEngine(
        listOf(GenericAndroidSettingsRules, HuaweiSettingsRules)
    )
    private val lockedState = ProtectedSettingsRuntimeState(
        isGlobalUnlockEnabled = false,
        isSetupSettingsAccessAllowed = false
    )

    @Test
    fun nonCandidatePage_isAllowedBeforeAnyOtherRule() {
        val decision = engine.evaluate(
            snapshot = snapshot(packageName = "com.example.unrelated", text = "KidsPhoneGuard 无障碍"),
            runtimeState = lockedState
        )

        assertDecision(decision, ProtectedSettingsDecisionType.ALLOW, "not_protected_settings_candidate")
    }

    @Test
    fun globalUnlock_allowsCandidatePageBeforeRiskEvaluation() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "KidsPhoneGuard 无障碍", clickedText = "关闭"),
            runtimeState = ProtectedSettingsRuntimeState(
                isGlobalUnlockEnabled = true,
                isSetupSettingsAccessAllowed = false
            )
        )

        assertDecision(decision, ProtectedSettingsDecisionType.ALLOW, "global_unlock_enabled")
    }

    @Test
    fun guardianPowerModeClick_blocksEvenDuringSetupAccess() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "电池设置", clickedText = "开启超级省电模式"),
            runtimeState = ProtectedSettingsRuntimeState(
                isGlobalUnlockEnabled = false,
                isSetupSettingsAccessAllowed = true
            )
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_ACTION,
            "guardian_disruptive_power_mode_action"
        )
    }

    @Test
    fun huaweiPowerModePage_blocksWholePageBeforeGenericObservation() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                packageName = "com.huawei.systemmanager",
                text = "power_mode_switch super_mode_layout 超级省电模式"
            ),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_PAGE,
            "guardian_disruptive_huawei_power_mode_page"
        )
    }

    @Test
    fun transientSystemUiPage_isAllowedWhenItHasNoDisruptiveSignal() {
        val decision = engine.evaluate(
            snapshot = snapshot(packageName = "com.android.systemui", text = "KidsPhoneGuard"),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.ALLOW,
            "transient_system_surface_without_disruptive_signal"
        )
    }

    @Test
    fun targetAppRiskyAction_blocksOnlyTheAction() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "KidsPhoneGuard 通知", clickedText = "关闭"),
            runtimeState = lockedState
        )

        assertDecision(decision, ProtectedSettingsDecisionType.BLOCK_ACTION, "target_app_risky_action")
        assertTrue(decision.matchedActionKeywords.contains("关闭"))
    }

    @Test
    fun targetAppListNavigation_blocksPageWithoutMisclassifyingClickedAction() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                text = "已安装的服务 拉钩守护 已关闭",
                clickedText = "已安装的服务 8 项服务"
            ),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_PAGE,
            "target_app_settings_page"
        )
    }

    @Test
    fun targetAppSettingsRowClick_blocksBeforePermissionPageFinishesLoading() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                text = "已安装的服务 拉钩守护 已开启",
                clickedText = "拉钩守护 已开启"
            ),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_ACTION,
            "target_app_settings_entry_click"
        )
        assertEquals("拉钩守护", decision.matchedTarget)
    }

    @Test
    fun targetAppCapabilityPage_blocksThePageWithoutAction() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "KidsPhoneGuard 无障碍"),
            runtimeState = lockedState
        )

        assertDecision(decision, ProtectedSettingsDecisionType.BLOCK_PAGE, "target_app_permission_page")
    }

    @Test
    fun huaweiFreeformTargetPage_blocksWhenCapabilityTextIsClipped() {
        val decision = engine.evaluate(
            snapshot = snapshot(
                text = "设置的标题栏。 全屏 androidhwext:id/hw_multiwindow_maximize_window " +
                    "最小化 androidhwext:id/hw_multiwindow_minimize_window 拉钩守护 已开启"
            ),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_PAGE,
            "target_app_protected_window_mode"
        )
        assertEquals("拉钩守护", decision.matchedTarget)
        assertTrue(decision.matchedRiskKeywords.contains("androidhwext:id/hw_multiwindow_"))
    }

    @Test
    fun globalOverlayPermissionList_blocksWithoutTargetAppName() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "显示在其他应用的上层 1号会员店 阿里云"),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_PAGE,
            "guardian_global_permission_page"
        )
    }

    @Test
    fun globalOverlayPermissionList_isAllowedDuringSetup() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "显示在其他应用的上层"),
            runtimeState = ProtectedSettingsRuntimeState(
                isGlobalUnlockEnabled = false,
                isSetupSettingsAccessAllowed = true
            )
        )

        assertDecision(decision, ProtectedSettingsDecisionType.ALLOW, "setup_settings_access_allowed")
    }

    @Test
    fun targetAppWithoutCapabilityText_blocksRestoredHistoryPage() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "KidsPhoneGuard 应用信息"),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.BLOCK_PAGE,
            "target_app_settings_page"
        )
    }

    @Test
    fun targetAppWithoutCapabilityText_isAllowedDuringParentSetup() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "拉钩守护 已开启"),
            runtimeState = ProtectedSettingsRuntimeState(
                isGlobalUnlockEnabled = false,
                isSetupSettingsAccessAllowed = true
            )
        )

        assertDecision(decision, ProtectedSettingsDecisionType.ALLOW, "setup_settings_access_allowed")
    }

    @Test
    fun candidateSettingsWithoutTarget_isObserved() {
        val decision = engine.evaluate(
            snapshot = snapshot(text = "显示设置"),
            runtimeState = lockedState
        )

        assertDecision(
            decision,
            ProtectedSettingsDecisionType.OBSERVE,
            "candidate_settings_observed"
        )
    }

    private fun snapshot(
        packageName: String = "com.android.settings",
        text: String = "",
        clickedText: String = ""
    ): SettingsPageSnapshot {
        return SettingsPageSnapshot(
            packageName = packageName,
            source = "test",
            eventType = 0,
            className = "android.app.Activity",
            text = text,
            clickedText = clickedText,
            windowPackages = emptySet()
        )
    }

    private fun assertDecision(
        decision: ProtectedSettingsDecision,
        expectedType: ProtectedSettingsDecisionType,
        expectedReason: String
    ) {
        assertEquals(expectedType, decision.type)
        assertEquals(expectedReason, decision.reason)
    }
}
