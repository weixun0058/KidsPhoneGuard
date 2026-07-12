package com.kidsphoneguard.engine.settingsprotection

import android.util.Log
import com.kidsphoneguard.utils.SettingsManager

class ProtectedSettingsPolicy(
    private val settingsManager: SettingsManager,
    rules: List<BrandSettingsRules> = BrandSettingsRuleProvider.currentRules()
) {
    companion object {
        private const val TAG = "ProtectedSettingsPolicy"
    }

    private val decisionEngine = ProtectedSettingsDecisionEngine(rules)

    fun evaluate(snapshot: SettingsPageSnapshot): ProtectedSettingsDecision {
        return decisionEngine.evaluate(
            snapshot = snapshot,
            runtimeState = ProtectedSettingsRuntimeState(
                isGlobalUnlockEnabled = settingsManager.isGlobalUnlockEnabled(),
                isSetupSettingsAccessAllowed = settingsManager.isSetupSettingsAccessAllowed()
            )
        )
    }

    fun isCandidate(snapshot: SettingsPageSnapshot): Boolean = decisionEngine.isCandidate(snapshot)

    fun isCandidatePackage(packageName: String): Boolean =
        decisionEngine.isCandidatePackage(packageName)

    fun findCandidatePackage(snapshot: SettingsPageSnapshot): String? =
        decisionEngine.findCandidatePackage(snapshot)

    fun containsTargetAppSignal(text: String): Boolean =
        decisionEngine.containsTargetAppSignal(text)

    fun containsGuardianDisruptiveCapabilitySignal(text: String): Boolean =
        decisionEngine.containsGuardianDisruptiveCapabilitySignal(text)

    init {
        Log.d(TAG, "protected_settings_policy_ready ${decisionEngine.describeRules()}")
    }
}
