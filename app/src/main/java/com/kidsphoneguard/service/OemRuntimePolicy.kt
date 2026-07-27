package com.kidsphoneguard.service

/**
 * 汇总 OEM 运行时差异，避免小米降载策略散落并误改华为/荣耀路径。
 */
internal object OemRuntimePolicy {
    const val DEFAULT_PROTECTED_WINDOW_SWEEP_INTERVAL_MS = 180L
    const val XIAOMI_PROTECTED_WINDOW_SWEEP_INTERVAL_MS = 1_000L

    fun isXiaomiFamily(manufacturer: String?, brand: String?): Boolean {
        val identity = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
        return identity.contains("xiaomi") ||
            identity.contains("redmi") ||
            identity.contains("poco")
    }

    fun protectedWindowSweepIntervalMs(isXiaomiFamilyDevice: Boolean): Long {
        return if (isXiaomiFamilyDevice) {
            XIAOMI_PROTECTED_WINDOW_SWEEP_INTERVAL_MS
        } else {
            DEFAULT_PROTECTED_WINDOW_SWEEP_INTERVAL_MS
        }
    }

    /**
     * 当前华为/荣耀小窗与省电节点树守卫在非小米设备上保持原路径；
     * 小米设备跳过，避免每个事件和每次周期扫描都遍历无关节点树。
     */
    fun shouldRunHuaweiSpecificWindowGuards(isXiaomiFamilyDevice: Boolean): Boolean {
        return !isXiaomiFamilyDevice
    }

    /**
     * 小米 UsageEvents 的最新前台事件若已切到 launcher/system/self 等不可统计表面，
     * 必须把它视为明确的前台边界，不能继续沿用更早的受限应用。
     * 非小米保持原有“最新可统计应用优先、否则回退 UsageStats”语义。
     */
    fun resolveUsageForegroundEvent(
        latestForegroundPackage: String?,
        latestTrackablePackage: String?,
        isXiaomiFamilyDevice: Boolean
    ): UsageForegroundEventResolution {
        if (isXiaomiFamilyDevice) {
            if (latestForegroundPackage.isNullOrEmpty()) {
                // Xiaomi UsageStats.lastTimeUsed can keep an old app at the top indefinitely
                // while launcher is quiet. With no current boundary, fail closed on accounting:
                // wait for a real foreground event instead of resurrecting stale usage.
                return UsageForegroundEventResolution(
                    type = UsageForegroundEventResolutionType.RESET_FOREGROUND,
                    packageName = UNKNOWN_FOREGROUND_BOUNDARY
                )
            }
            if (latestForegroundPackage != latestTrackablePackage) {
                return UsageForegroundEventResolution(
                    type = UsageForegroundEventResolutionType.RESET_FOREGROUND,
                    packageName = latestForegroundPackage
                )
            }
        }
        if (!latestTrackablePackage.isNullOrEmpty()) {
            return UsageForegroundEventResolution(
                type = UsageForegroundEventResolutionType.USE_TRACKABLE_PACKAGE,
                packageName = latestTrackablePackage
            )
        }
        return UsageForegroundEventResolution(
            type = UsageForegroundEventResolutionType.FALLBACK_TO_USAGE_STATS
        )
    }
}

internal const val UNKNOWN_FOREGROUND_BOUNDARY = "unknown"

/**
 * Retains Xiaomi's most recent foreground boundary across quiet UsageEvents windows. The retained
 * value may be a launcher/system package; that is intentional because it prevents fallback
 * UsageStats from reviving an app that has already moved to the background.
 */
internal class UsageForegroundBoundaryTracker {
    private var lastObservedPackage = ""

    fun resolve(
        latestForegroundPackage: String?,
        retainAcrossQuietWindow: Boolean
    ): String? {
        val latest = latestForegroundPackage.orEmpty()
        if (latest.isNotBlank()) {
            if (retainAcrossQuietWindow) {
                lastObservedPackage = latest
            }
            return latest
        }
        return if (retainAcrossQuietWindow) {
            lastObservedPackage.ifBlank { null }
        } else {
            null
        }
    }

    fun reset() {
        lastObservedPackage = ""
    }
}

internal enum class UsageForegroundEventResolutionType {
    USE_TRACKABLE_PACKAGE,
    RESET_FOREGROUND,
    FALLBACK_TO_USAGE_STATS
}

internal data class UsageForegroundEventResolution(
    val type: UsageForegroundEventResolutionType,
    val packageName: String = ""
)
