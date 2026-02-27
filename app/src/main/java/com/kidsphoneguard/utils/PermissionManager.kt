package com.kidsphoneguard.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.kidsphoneguard.service.GuardAccessibilityService

/**
 * 权限管理器
 * 统一处理所有必要权限的申请和检查
 */
object PermissionManager {

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
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * 检查无障碍服务是否已启用
     * 使用 Settings.Secure 获取已启用的无障碍服务列表
     * @param context 上下文
     * @return 是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        // 方法1: 检查系统设置中已启用的服务
        val enabledServicesStr = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val targetService = "${context.packageName}/${GuardAccessibilityService::class.java.name}"

        // 检查目标服务是否在已启用列表中
        if (enabledServicesStr.contains(targetService)) {
            return true
        }

        // 方法2: 备用检查方式
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        return enabledServices.any { serviceInfo ->
            val resolveInfo = serviceInfo.resolveInfo
            resolveInfo.serviceInfo.packageName == context.packageName &&
            resolveInfo.serviceInfo.name == GuardAccessibilityService::class.java.name
        }
    }

    /**
     * 请求无障碍服务权限
     * @param context 上下文
     */
    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 检查所有必要权限是否都已获取
     * @param context 上下文
     * @return 权限状态映射
     */
    fun checkAllPermissions(context: Context): Map<PermissionType, Boolean> {
        return mapOf(
            PermissionType.OVERLAY to canDrawOverlays(context),
            PermissionType.ACCESSIBILITY to isAccessibilityServiceEnabled(context),
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
