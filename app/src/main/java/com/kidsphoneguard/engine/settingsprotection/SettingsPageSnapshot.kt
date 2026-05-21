package com.kidsphoneguard.engine.settingsprotection

data class SettingsPageSnapshot(
    val packageName: String,
    val source: String,
    val eventType: Int,
    val className: String,
    val text: String,
    val clickedText: String,
    val windowPackages: Set<String>
)
