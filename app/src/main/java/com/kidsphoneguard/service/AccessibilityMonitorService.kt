package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/**
 * 无障碍服务监控器
 * 监控无障碍服务状态，如果被关闭则自动重新启用
 */
class AccessibilityMonitorService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null

    companion object {
        private const val CHECK_INTERVAL = 2000L // 2秒检查一次

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
        startMonitoring()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监控设置页面的操作
        event?.let {
            val packageName = it.packageName?.toString() ?: return
            val className = it.className?.toString() ?: return

            // 如果用户在设置页面操作无障碍服务
            if (packageName == "com.android.settings") {
                // 检测是否正在关闭我们的无障碍服务
                if (isTryingToDisableOurService(it)) {
                    // 立即返回桌面
                    performGlobalAction(GLOBAL_ACTION_HOME)

                    // 重新启用我们的无障碍服务
                    serviceScope.launch {
                        delay(500)
                        GuardAccessibilityService.startService(this@AccessibilityMonitorService)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}

    private fun startMonitoring() {
        monitorJob = serviceScope.launch {
            while (isActive) {
                // 检查主无障碍服务是否运行
                if (!GuardAccessibilityService.isRunning) {
                    // 重新启动
                    GuardAccessibilityService.startService(this@AccessibilityMonitorService)
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    private fun isTryingToDisableOurService(event: AccessibilityEvent): Boolean {
        // 检查是否包含我们的服务名称或相关操作
        val texts = listOf(
            "儿童手机守护",
            "KidsPhoneGuard",
            "GuardAccessibilityService",
            "关闭",
            "停用"
        )

        for (text in texts) {
            val nodes = event.source?.findAccessibilityNodeInfosByText(text)
            if (nodes?.isNotEmpty() == true) {
                return true
            }
        }

        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        serviceScope.cancel()

        // 尝试重新启动监控
        startService(Intent(this, AccessibilityMonitorService::class.java))
    }
}
