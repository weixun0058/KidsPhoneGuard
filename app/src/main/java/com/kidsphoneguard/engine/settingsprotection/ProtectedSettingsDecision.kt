package com.kidsphoneguard.engine.settingsprotection

enum class ProtectedSettingsDecisionType {
    ALLOW,
    OBSERVE,
    BLOCK_PAGE,
    BLOCK_ACTION
}

data class ProtectedSettingsDecision(
    val type: ProtectedSettingsDecisionType,
    val reason: String,
    val matchedTarget: String = "",
    val matchedRiskKeywords: List<String> = emptyList(),
    val matchedActionKeywords: List<String> = emptyList()
)
