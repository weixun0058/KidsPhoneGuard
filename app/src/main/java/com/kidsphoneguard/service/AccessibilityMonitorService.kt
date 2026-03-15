package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务监控器
 * 已禁用：功能已合并到GuardAccessibilityService中，避免冲突
 */
class AccessibilityMonitorService : AccessibilityService() {

    companion object {
        /**
         * 检查无障碍服务是否启用
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val serviceName = "${context.packageName}/.service.GuardAccessibilityService"
            return enabledServices.contains(serviceName)
        }

        /**
         * 跳转到无障碍服务设置页面
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 禁用此服务，功能已合并到GuardAccessibilityService
        disableSelf()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理任何事件
    }

    override fun onInterrupt() {}
}
