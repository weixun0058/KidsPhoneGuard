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

data class RecentForegroundPackageResolution(
    val packageName: String,
    val source: String
)

/**
 * UsageEvents only reports transitions, so a quiet foreground activity can have no event in
 * the next polling window. Keep the last observed activity instead of treating that silence as
 * a foreground change to "unknown", which would make the degraded lock oscillate.
 */
class RecentForegroundPackageTracker {
    private var lastObservedPackage = ""

    fun resolve(latestObservedPackage: String?): RecentForegroundPackageResolution {
        val observed = latestObservedPackage.orEmpty()
        if (observed.isNotBlank()) {
            lastObservedPackage = observed
            return RecentForegroundPackageResolution(observed, "event")
        }
        if (lastObservedPackage.isNotBlank()) {
            return RecentForegroundPackageResolution(lastObservedPackage, "cached")
        }
        return RecentForegroundPackageResolution("unknown", "none")
    }
}

/**
 * Only one full-screen guard surface may own input. Degraded protection has priority because it
 * contains the controls needed to restore accessibility; a normal block overlay has no controls.
 */
object GuardOverlayArbitrationPolicy {
    fun shouldAllowStandardOverlay(
        degradedLockRequestedOrShowing: Boolean,
        parentTemporaryUnlockActive: Boolean = false,
        globalUnlockActive: Boolean = false,
        exitToHomeInProgress: Boolean = false
    ): Boolean =
        !degradedLockRequestedOrShowing &&
            !parentTemporaryUnlockActive &&
            !globalUnlockActive &&
            !exitToHomeInProgress
}

enum class DegradedExitToHomeDecision {
    NONE,
    SUPPRESS_TRANSITION_PENDING,
    SAFE_DESTINATION_REACHED,
    CANCELLED_BY_OTHER_FOREGROUND,
    EXPIRED
}

/**
 * HOME launches and UsageEvents are asynchronous. Suppress only the stale package that originally
 * owned the lock (plus unknown samples) until a safe destination is observed, never a newly opened
 * restricted package.
 */
object DegradedExitToHomePolicy {
    const val MAX_TRANSITION_MS = 5_000L

    fun evaluate(
        active: Boolean,
        blockedPackageName: String,
        observedPackageName: String,
        safeDestination: Boolean,
        expiresAtElapsedRealtime: Long,
        nowElapsedRealtime: Long
    ): DegradedExitToHomeDecision {
        if (!active) return DegradedExitToHomeDecision.NONE
        if (nowElapsedRealtime >= expiresAtElapsedRealtime) {
            return DegradedExitToHomeDecision.EXPIRED
        }
        if (safeDestination) {
            return DegradedExitToHomeDecision.SAFE_DESTINATION_REACHED
        }
        val observed = normalizePackageName(observedPackageName)
        if (
            observed.isBlank() ||
            observed == "unknown" ||
            observed.startsWith("error:") ||
            observed == normalizePackageName(blockedPackageName)
        ) {
            return DegradedExitToHomeDecision.SUPPRESS_TRANSITION_PENDING
        }
        return DegradedExitToHomeDecision.CANCELLED_BY_OTHER_FOREGROUND
    }
}

/**
 * Minimal degraded-mode safety allow-list. It intentionally excludes Settings, installers,
 * markets and browsers even though some appear in the broader runtime whitelist.
 */
object DegradedEmergencySurfacePolicy {
    private val STATIC_SAFE_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher",
        "com.samsung.android.launcher",
        "com.android.phone",
        "com.android.incallui",
        "com.android.telecom",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.miui.phone",
        "com.huawei.phone",
        "com.hihonor.phone",
        "com.android.emergency",
        "com.google.android.apps.emergency"
    )

    fun shouldAllow(
        packageName: String,
        ownPackageName: String,
        resolvedHomePackages: Set<String>,
        resolvedDialerPackages: Set<String>
    ): Boolean {
        val normalized = normalizePackageName(packageName)
        if (normalized.isBlank()) return false
        return normalized == normalizePackageName(ownPackageName) ||
            normalized in STATIC_SAFE_PACKAGES ||
            resolvedHomePackages.any { normalizePackageName(it) == normalized } ||
            resolvedDialerPackages.any { normalizePackageName(it) == normalized }
    }
}

/**
 * Parent-authorized degraded-protection bypass. Elapsed realtime keeps the short session immune
 * to wall-clock edits and makes the expiry rule independently testable.
 */
object DegradedTemporaryUnlockPolicy {
    const val DURATION_MS = 5 * 60 * 1000L

    fun expiresAt(startedAtElapsedRealtime: Long): Long =
        startedAtElapsedRealtime + DURATION_MS

    fun isActive(untilElapsedRealtime: Long, nowElapsedRealtime: Long): Boolean =
        untilElapsedRealtime > nowElapsedRealtime
}

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

private fun normalizePackageName(packageName: String): String =
    packageName.trim().substringBefore(':').lowercase()
