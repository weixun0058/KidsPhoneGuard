package com.kidsphoneguard.utils

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.kidsphoneguard.service.AccessibilityOperationalState
import com.kidsphoneguard.service.GuardAccessibilityService
import com.kidsphoneguard.service.GuardHealthState

/**
 * 权限管理器
 * 统一处理所有必要权限的申请和检查
 */
object PermissionManager {
    private const val ACCESSIBILITY_GUIDE_COOLDOWN_MS = 90_000L
    private const val ACCESSIBILITY_HEARTBEAT_TIMEOUT_MS = 15_000L
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val EXTRA_COMPONENT_NAME = "android.intent.extra.COMPONENT_NAME"

    /**
     * 检查是否有悬浮窗权限
     * @param context 上下文
     * @return 是否有权限
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 请求悬浮窗权限
     * @param context 上下文
     */
    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = protectedSettingsLaunchFlags()
        context.startActivity(intent)
    }

    /**
     * 检查无障碍服务是否已启用
     * 使用 Settings.Secure 获取已启用的无障碍服务列表
     * @param context 上下文
     * @return 是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled != 1) {
            return false
        }
        val enabledServicesStr = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val enabledEntries = enabledServicesStr
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return enabledEntries.any { isTargetAccessibilityServiceEntry(context, it) }
    }

    private fun isTargetAccessibilityServiceEntry(context: Context, entry: String): Boolean {
        val component = ComponentName.unflattenFromString(entry) ?: return false
        val target = ComponentName(context, GuardAccessibilityService::class.java)
        return component.packageName == target.packageName &&
            (
                component.className == target.className ||
                    component.className == ".service.GuardAccessibilityService" ||
                    component.className.endsWith(".service.GuardAccessibilityService")
                )
    }

    /**
     * 请求无障碍服务权限
     * @param context 上下文
     */
    fun requestAccessibilityPermission(context: Context, forceOpenWhenEnabled: Boolean = false) {
        val shouldForceOpen = forceOpenWhenEnabled || isAccessibilityRecoveryNeeded(context)
        if (!shouldForceOpen && isAccessibilityServiceEnabled(context)) {
            return
        }

        if (!shouldForceOpen && !canShowAccessibilityGuide(context)) {
            return
        }

        val targetComponent = ComponentName(context, GuardAccessibilityService::class.java)
        val directIntent = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
            flags = protectedSettingsLaunchFlags()
            putExtra(EXTRA_COMPONENT_NAME, targetComponent.flattenToString())
        }

        val launched = tryStartActivity(context, directIntent) ||
            tryStartActivity(
                context,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = protectedSettingsLaunchFlags()
                }
            ) ||
            tryStartActivity(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = protectedSettingsLaunchFlags()
                }
            )

        if (launched) {
            GuardHealthState.markAccessibilityGuideShown(context)
        }
    }

    fun isAccessibilityRecoveryNeeded(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        val accessibilityHeartbeat = GuardHealthState.getAccessibilityHeartbeat(context)
        return !AccessibilityOperationalState.isOperational(
            enabledInSettings = accessibilityEnabled,
            serviceRunning = GuardAccessibilityService.isServiceRunning(),
            heartbeatAt = accessibilityHeartbeat,
            now = now,
            heartbeatTimeoutMs = ACCESSIBILITY_HEARTBEAT_TIMEOUT_MS
        )
    }

    fun isAccessibilityServiceOperational(context: Context): Boolean {
        return !isAccessibilityRecoveryNeeded(context)
    }

    fun canShowAccessibilityGuide(context: Context): Boolean {
        return GuardHealthState.canShowAccessibilityGuide(context, ACCESSIBILITY_GUIDE_COOLDOWN_MS)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { false }
    }

    /**
     * 检查是否有使用统计权限
     * @param context 上下文
     * @return 是否有权限
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 请求使用统计权限
     * @param context 上下文
     */
    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = protectedSettingsLaunchFlags()
        }
        context.startActivity(intent)
    }

    /**
     * 检查是否忽略电池优化
     * @param context 上下文
     * @return 是否已忽略
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求忽略电池优化
     * @param context 上下文
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isHuaweiFamilyDevice()) {
            val opened = requestHuaweiProtectionGuide(context)
            if (opened) {
                return
            }
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = protectedSettingsLaunchFlags()
        }
        context.startActivity(intent)
    }

    fun isHuaweiFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
        val brand = Build.BRAND?.lowercase().orEmpty()
        return manufacturer.contains("huawei") ||
            manufacturer.contains("honor") ||
            brand.contains("huawei") ||
            brand.contains("honor")
    }

    fun requestHuaweiProtectionGuide(context: Context): Boolean {
        if (!isHuaweiFamilyDevice()) {
            return false
        }
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                flags = protectedSettingsLaunchFlags()
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
                flags = protectedSettingsLaunchFlags()
            },
            Intent().apply {
                component = ComponentName(
                    "com.honor.systemmanager",
                    "com.honor.systemmanager.optimize.process.ProtectActivity"
                )
                flags = protectedSettingsLaunchFlags()
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = protectedSettingsLaunchFlags()
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = protectedSettingsLaunchFlags()
            }
        )
        return intents.any { tryStartActivity(context, it) }
    }

    /**
     * Permission/settings pages are parent-authorized temporary surfaces. Recreate the
     * external Settings task and keep it out of Recents so a child cannot reopen a stale
     * permission detail after the setup allowance or global unlock has ended.
     */
    internal fun protectedSettingsLaunchFlags(): Int {
        return Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    }

    /**
     * 检查所有必要权限是否都已获取
     * @param context 上下文
     * @return 权限状态映射
     */
    fun checkAllPermissions(context: Context): Map<PermissionType, Boolean> {
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        val accessibilityHeartbeat = GuardHealthState.getAccessibilityHeartbeat(context)
        val accessibilityRunning = GuardAccessibilityService.isServiceRunning()
        val accessibilityReady = AccessibilityOperationalState.isOperational(
            enabledInSettings = accessibilityEnabled,
            serviceRunning = accessibilityRunning,
            heartbeatAt = accessibilityHeartbeat,
            now = System.currentTimeMillis(),
            heartbeatTimeoutMs = ACCESSIBILITY_HEARTBEAT_TIMEOUT_MS
        )
        return mapOf(
            PermissionType.OVERLAY to canDrawOverlays(context),
            PermissionType.ACCESSIBILITY to accessibilityReady,
            PermissionType.USAGE_STATS to hasUsageStatsPermission(context),
            PermissionType.BATTERY_OPTIMIZATION to isIgnoringBatteryOptimizations(context)
        )
    }

    /**
     * 权限类型枚举
     */
    enum class PermissionType {
        OVERLAY,                // 悬浮窗权限
        ACCESSIBILITY,          // 无障碍服务权限
        USAGE_STATS,            // 使用统计权限
        BATTERY_OPTIMIZATION    // 忽略电池优化
    }
}
