package com.kidsphoneguard.engine.settingsprotection

internal data class ProtectedSettingsRuntimeState(
    val isGlobalUnlockEnabled: Boolean,
    val isSetupSettingsAccessAllowed: Boolean
)

/**
 * Pure decision core for protected-settings routing.
 *
 * Android state is read by [ProtectedSettingsPolicy] and supplied as
 * [ProtectedSettingsRuntimeState], so the priority rules can be verified in JVM tests.
 */
internal class ProtectedSettingsDecisionEngine(rules: List<BrandSettingsRules>) {
    companion object {
        private val TRANSIENT_SYSTEM_SURFACE_PACKAGES = setOf(
            "com.android.systemui",
            "com.huawei.controlcenter"
        )
    }

    private val protectedSettingPackages =
        rules.flatMap { it.protectedSettingPackages }.map { normalizePackageName(it) }.toSet()
    private val targetAppKeywords = rules.flatMap { it.targetAppKeywords }.distinct()
    private val riskyCapabilityKeywords = rules.flatMap { it.riskyCapabilityKeywords }.distinct()
    private val riskyActionKeywords = rules.flatMap { it.riskyActionKeywords }.distinct()
    private val guardianGlobalPageBlockKeywords =
        rules.flatMap { it.guardianGlobalPageBlockKeywords }.distinct()
    private val observeOnlyKeywords = rules.flatMap { it.observeOnlyKeywords }.distinct()
    private val guardianDisruptiveCapabilityKeywords =
        rules.flatMap { it.guardianDisruptiveCapabilityKeywords }.distinct()
    private val guardianDisruptiveActionKeywords =
        rules.flatMap { it.guardianDisruptiveActionKeywords }.distinct()
    private val protectedWindowModeKeywords =
        rules.flatMap { it.protectedWindowModeKeywords }.distinct()

    fun evaluate(
        snapshot: SettingsPageSnapshot,
        runtimeState: ProtectedSettingsRuntimeState
    ): ProtectedSettingsDecision {
        if (!isCandidate(snapshot)) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.ALLOW,
                reason = "not_protected_settings_candidate"
            )
        }
        if (runtimeState.isGlobalUnlockEnabled) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.ALLOW,
                reason = "global_unlock_enabled"
            )
        }
        val pageSignal = normalizeText(
            listOf(
                snapshot.text,
                snapshot.className,
                snapshot.packageName,
                snapshot.windowPackages.joinToString(" ")
            ).joinToString(" ")
        )
        val clickedSignal = normalizeText(snapshot.clickedText)
        val combinedSignal = "$clickedSignal $pageSignal"
        val disruptiveCapabilityKeywords = keywordMatches(combinedSignal, guardianDisruptiveCapabilityKeywords)
        val pageDisruptiveCapabilityKeywords =
            keywordMatches(pageSignal, guardianDisruptiveCapabilityKeywords)
        val clickedDisruptiveCapabilityKeywords =
            keywordMatches(clickedSignal, guardianDisruptiveCapabilityKeywords)
        val clickedDisruptiveActionKeywords =
            keywordMatches(clickedSignal, guardianDisruptiveActionKeywords)
        if (clickedDisruptiveCapabilityKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_ACTION,
                reason = "guardian_disruptive_power_mode_action",
                matchedRiskKeywords = clickedDisruptiveCapabilityKeywords,
                matchedActionKeywords = clickedDisruptiveActionKeywords
            )
        }
        if (pageDisruptiveCapabilityKeywords.isNotEmpty() && clickedDisruptiveActionKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_ACTION,
                reason = "guardian_disruptive_power_mode_confirm_action",
                matchedRiskKeywords = pageDisruptiveCapabilityKeywords,
                matchedActionKeywords = clickedDisruptiveActionKeywords
            )
        }
        if (isHuaweiPowerModePage(snapshot, pageSignal) && pageDisruptiveCapabilityKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_PAGE,
                reason = "guardian_disruptive_huawei_power_mode_page",
                matchedRiskKeywords = pageDisruptiveCapabilityKeywords
            )
        }
        if (disruptiveCapabilityKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.OBSERVE,
                reason = "guardian_disruptive_power_mode_observed",
                matchedRiskKeywords = disruptiveCapabilityKeywords
            )
        }
        if (runtimeState.isSetupSettingsAccessAllowed) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.ALLOW,
                reason = "setup_settings_access_allowed"
            )
        }

        val candidatePackage = findCandidatePackage(snapshot).orEmpty()
        if (TRANSIENT_SYSTEM_SURFACE_PACKAGES.any { candidatePackage == it }) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.ALLOW,
                reason = "transient_system_surface_without_disruptive_signal"
            )
        }

        // A settings row click can open the destructive permission detail before that
        // destination exposes words such as "无障碍" or "悬浮窗" in the node tree.
        // Suppress the explicit target-app click immediately instead of waiting for a
        // later content-change event or periodic window sweep to recognize the page.
        val clickedTargetKeyword = firstKeywordMatch(clickedSignal, targetAppKeywords)
        if (clickedTargetKeyword.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_ACTION,
                reason = "target_app_settings_entry_click",
                matchedTarget = clickedTargetKeyword
            )
        }

        val globalPageKeywords = keywordMatches(pageSignal, guardianGlobalPageBlockKeywords)
        if (globalPageKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_PAGE,
                reason = "guardian_global_permission_page",
                matchedRiskKeywords = globalPageKeywords
            )
        }

        val targetKeyword = firstKeywordMatch(pageSignal, targetAppKeywords)
        // BLOCK_ACTION must be backed by the node that the user actually clicked.
        // Page-wide text often contains status labels such as "已关闭" for unrelated
        // rows; combining that text with a harmless navigation click (for example
        // "已安装的服务") incorrectly turns the navigation into a destructive action.
        val actionKeywords = keywordMatches(clickedSignal, riskyActionKeywords)
        val capabilityKeywords = keywordMatches(pageSignal, riskyCapabilityKeywords)
        val windowModeKeywords = keywordMatches(pageSignal, protectedWindowModeKeywords)

        if (targetKeyword.isNotEmpty() && actionKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_ACTION,
                reason = "target_app_risky_action",
                matchedTarget = targetKeyword,
                matchedRiskKeywords = capabilityKeywords,
                matchedActionKeywords = actionKeywords
            )
        }
        // EMUI/Honor can keep a sensitive Settings detail page alive as an always-on-top
        // freeform window while omitting the page's accessibility/overlay labels from the
        // exposed node tree. The OEM window chrome remains visible, so combine that signal
        // with the target app name instead of treating the clipped page as observe-only.
        if (targetKeyword.isNotEmpty() && windowModeKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_PAGE,
                reason = "target_app_protected_window_mode",
                matchedTarget = targetKeyword,
                matchedRiskKeywords = windowModeKeywords
            )
        }
        if (targetKeyword.isNotEmpty() && capabilityKeywords.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_PAGE,
                reason = "target_app_permission_page",
                matchedTarget = targetKeyword,
                matchedRiskKeywords = capabilityKeywords
            )
        }
        if (targetKeyword.isNotEmpty()) {
            return ProtectedSettingsDecision(
                type = ProtectedSettingsDecisionType.BLOCK_PAGE,
                reason = "target_app_settings_page",
                matchedTarget = targetKeyword
            )
        }

        val observeKeywords = keywordMatches(pageSignal, observeOnlyKeywords)
        return ProtectedSettingsDecision(
            type = ProtectedSettingsDecisionType.OBSERVE,
            reason = if (observeKeywords.isEmpty()) {
                "candidate_settings_without_target"
            } else {
                "candidate_settings_observed"
            },
            matchedRiskKeywords = observeKeywords
        )
    }

    fun isCandidate(snapshot: SettingsPageSnapshot): Boolean {
        return findCandidatePackage(snapshot) != null
    }

    fun isCandidatePackage(packageName: String): Boolean {
        val normalized = normalizePackageName(packageName)
        if (normalized.isEmpty()) {
            return false
        }
        return protectedSettingPackages.any { candidate ->
            normalized == candidate || normalized.startsWith("$candidate.")
        }
    }

    fun findCandidatePackage(snapshot: SettingsPageSnapshot): String? {
        val packages = linkedSetOf<String>()
        packages.add(snapshot.packageName)
        packages.addAll(snapshot.windowPackages)
        return packages.firstOrNull { isCandidatePackage(it) }
    }

    fun containsTargetAppSignal(text: String): Boolean {
        val normalized = normalizeText(text)
        return targetAppKeywords.any { keyword ->
            normalized.contains(normalizeText(keyword))
        }
    }

    fun containsGuardianDisruptiveCapabilitySignal(text: String): Boolean {
        val normalized = normalizeText(text)
        return guardianDisruptiveCapabilityKeywords.any { keyword ->
            normalized.contains(normalizeText(keyword))
        }
    }

    fun describeRules(): String {
        return "packages=${protectedSettingPackages.size} targets=${targetAppKeywords.size} " +
            "risks=${riskyCapabilityKeywords.size} disruptive=${guardianDisruptiveCapabilityKeywords.size}"
    }

    private fun isHuaweiPowerModePage(snapshot: SettingsPageSnapshot, pageSignal: String): Boolean {
        val candidatePackage = findCandidatePackage(snapshot).orEmpty()
        if (candidatePackage != "com.huawei.systemmanager" &&
            candidatePackage != "com.hihonor.systemmanager"
        ) {
            return false
        }
        val hasPowerModeContainer =
            pageSignal.contains("power_mode_switch") ||
                pageSignal.contains("power_mode_switch_scrollview")
        val hasModeSwitches =
            pageSignal.contains("super_mode_layout") ||
                pageSignal.contains("save_mode_switch") ||
                pageSignal.contains("super_mode_switch")
        return hasPowerModeContainer && hasModeSwitches
    }

    private fun firstKeywordMatch(signal: String, keywords: List<String>): String {
        return keywords.firstOrNull { keyword ->
            signal.contains(normalizeText(keyword))
        }.orEmpty()
    }

    private fun keywordMatches(signal: String, keywords: List<String>): List<String> {
        return keywords.filter { keyword ->
            signal.contains(normalizeText(keyword))
        }
    }

    private fun normalizeText(text: String): String = text.lowercase()

    private fun normalizePackageName(packageName: String): String {
        return packageName.trim().substringBefore(':').lowercase()
    }
}
