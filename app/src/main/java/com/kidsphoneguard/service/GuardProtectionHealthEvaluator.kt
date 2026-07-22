package com.kidsphoneguard.service

data class GuardProtectionHealthInput(
    val now: Long,
    val accessibilityEnabled: Boolean,
    val usagePermissionGranted: Boolean,
    val accessibilityServiceRunning: Boolean,
    val usageTrackingActive: Boolean,
    val accessibilityHeartbeat: Long,
    val usageHeartbeat: Long,
    val accessibilityHeartbeatTimeoutMs: Long,
    val usageHeartbeatTimeoutMs: Long
)

data class GuardProtectionHealthSnapshot(
    val accessibilityOperational: Boolean,
    val accessibilityMissing: Boolean,
    val usagePermissionMissing: Boolean,
    val accessibilityStale: Boolean,
    val usageStale: Boolean,
    val degraded: Boolean,
    val accessibilityHeartbeatAge: Long,
    val usageHeartbeatAge: Long,
    val accessibilityEnabled: Boolean,
    val usagePermissionGranted: Boolean,
    val accessibilityServiceRunning: Boolean,
    val usageTrackingActive: Boolean
) {
    fun digest(): String = listOf(
        "ae=$accessibilityEnabled",
        "aOperational=$accessibilityOperational",
        "ap=$usagePermissionGranted",
        "ar=$accessibilityServiceRunning",
        "ur=$usageTrackingActive",
        "aHbAge=$accessibilityHeartbeatAge",
        "uHbAge=$usageHeartbeatAge",
        "aMissing=$accessibilityMissing",
        "uMissing=$usagePermissionMissing",
        "aStale=$accessibilityStale",
        "uStale=$usageStale",
        "degraded=$degraded"
    ).joinToString("|")
}

data class GuardProtectionHealthTransition(
    val degradedChanged: Boolean,
    val accessibilityRestored: Boolean
)

object GuardProtectionHealthEvaluator {
    fun evaluate(input: GuardProtectionHealthInput): GuardProtectionHealthSnapshot {
        val accessibilityOperational = AccessibilityOperationalState.isOperational(
            enabledInSettings = input.accessibilityEnabled,
            serviceRunning = input.accessibilityServiceRunning,
            heartbeatAt = input.accessibilityHeartbeat,
            now = input.now,
            heartbeatTimeoutMs = input.accessibilityHeartbeatTimeoutMs
        )
        val accessibilityMissing = !input.accessibilityEnabled
        val usagePermissionMissing = !input.usagePermissionGranted
        val accessibilityStale = input.accessibilityEnabled &&
            (!input.accessibilityServiceRunning ||
                input.accessibilityHeartbeat == 0L ||
                input.now - input.accessibilityHeartbeat > input.accessibilityHeartbeatTimeoutMs)
        val usageStale = input.usagePermissionGranted &&
            (!input.usageTrackingActive ||
                input.usageHeartbeat == 0L ||
                input.now - input.usageHeartbeat > input.usageHeartbeatTimeoutMs)
        return GuardProtectionHealthSnapshot(
            accessibilityOperational = accessibilityOperational,
            accessibilityMissing = accessibilityMissing,
            usagePermissionMissing = usagePermissionMissing,
            accessibilityStale = accessibilityStale,
            usageStale = usageStale,
            degraded = accessibilityMissing ||
                usagePermissionMissing ||
                accessibilityStale ||
                usageStale,
            accessibilityHeartbeatAge = heartbeatAge(input.now, input.accessibilityHeartbeat),
            usageHeartbeatAge = heartbeatAge(input.now, input.usageHeartbeat),
            accessibilityEnabled = input.accessibilityEnabled,
            usagePermissionGranted = input.usagePermissionGranted,
            accessibilityServiceRunning = input.accessibilityServiceRunning,
            usageTrackingActive = input.usageTrackingActive
        )
    }

    fun transition(
        previousDegraded: Boolean,
        previousAccessibilityOperational: Boolean,
        current: GuardProtectionHealthSnapshot
    ): GuardProtectionHealthTransition = GuardProtectionHealthTransition(
        degradedChanged = current.degraded != previousDegraded,
        accessibilityRestored = current.accessibilityOperational &&
            !previousAccessibilityOperational
    )

    fun shouldShowDegradedLock(
        accessibilityOperational: Boolean,
        policyShouldBlock: Boolean
    ): Boolean = !accessibilityOperational && policyShouldBlock

    private fun heartbeatAge(now: Long, heartbeat: Long): Long =
        if (heartbeat == 0L) -1L else now - heartbeat
}
