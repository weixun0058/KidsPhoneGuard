package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.data.repository.DailyUsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.os.Handler
import android.os.Looper

/**
 * 核心无障碍服务
 * 监控应用切换事件，执行管控策略
 * HarmonyOS优化版：通过执行返回操作阻止应用启动
 */
class GuardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * 启动服务（无障碍服务需要通过系统设置启用，此方法仅用于检查状态）
         */
        fun startService(context: Context) {
            // 无障碍服务无法通过代码直接启动，需要用户手动启用
            // 此方法仅用于检查服务状态
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var dailyUsageRepository: DailyUsageRepository

    private var currentPackageName: String = ""
    private var lastBlockedPackage: String = ""

    // 系统界面包名
    private val systemUIPackages = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.samsung.android.launcher",
        "com.coloros.launcher",
        "com.funtouch.launcher",
        "com.android.inputmethod",
        "com.google.android.inputmethod",
        "com.baidu.input",
        "com.sohu.inputmethod",
        "com.huawei.systemmanager",
        "com.hihonor.systemmanager"
    )

    // 返回桌面广播接收器
    private val homeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_GO_HOME") {
                performGlobalAction(GLOBAL_ACTION_HOME)
                lastBlockedPackage = ""
            }
        }
    }

    // 浏览器包名关键词
    private val browserKeywords = setOf(
        "browser", "chrome", "firefox", "edge", "opera",
        "webview", "ucmobile", "quark", "baidu.searchbox"
    )

    // 拦截应用广播接收器
    private val blockAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_BLOCK_APP") {
                val packageName = intent.getStringExtra("package_name") ?: return

                // 判断是否是浏览器
                val isBrowser = browserKeywords.any { packageName.lowercase().contains(it) }

                // 立即执行返回操作
                performGlobalAction(GLOBAL_ACTION_BACK)

                // 延迟再次执行
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 50)

                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 100)

                // 浏览器应用需要更严格的拦截
                if (isBrowser) {
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 150)

                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 200)

                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                }

                // 记录
                lastBlockedPackage = packageName
            }
        }
    }

    // 超激进的强制锁定
    private val forceLockRunnable = object : Runnable {
        override fun run() {
            if (lastBlockedPackage.isNotEmpty()) {
                // 每50ms检查一次，确保覆盖层始终显示
                if (!OverlayService.isOverlayShowing()) {
                    serviceScope.launch {
                        OverlayService.showOverlay(this@GuardAccessibilityService, lastBlockedPackage, "")
                    }
                }
            }
            handler.postDelayed(this, 50)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        android.util.Log.d("GuardAccessibilityService", "Service connected")
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        val app = applicationContext as KidsPhoneGuardApp
        appRuleRepository = app.appRuleRepository
        dailyUsageRepository = app.dailyUsageRepository

        registerReceiver(homeBroadcastReceiver, IntentFilter("ACTION_GO_HOME"))
        registerReceiver(blockAppReceiver, IntentFilter("ACTION_BLOCK_APP"))
        handler.post(forceLockRunnable)

        android.util.Log.d("GuardAccessibilityService", "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        unregisterReceiver(homeBroadcastReceiver)
        unregisterReceiver(blockAppReceiver)
        handler.removeCallbacks(forceLockRunnable)
        serviceScope.cancel()

        android.util.Log.d("GuardAccessibilityService", "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowEvent(event)
            }
        }
    }

    private fun handleWindowEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        if (packageName.contains("com.kidsphoneguard")) {
            return
        }

        // 防卸载保护 - 检测设置页面
        if (isTryingToUninstall(event, packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // 防止关闭无障碍服务 - 检测无障碍服务设置页面
        if (isAccessibilitySettings(event, packageName)) {
            // 如果检测到正在操作无障碍服务设置，立即返回
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // 如果是系统界面
        if (systemUIPackages.any { packageName.contains(it) }) {
            return
        }

        // 即使包名相同，也重新检查（处理横屏切换）
        currentPackageName = packageName

        serviceScope.launch {
            checkPolicyAndExecute(packageName)
        }
    }

    /**
     * 检测是否正在无障碍服务设置页面
     */
    private fun isAccessibilitySettings(event: AccessibilityEvent, packageName: String): Boolean {
        if (packageName != "com.android.settings") return false

        // 检查是否包含无障碍服务相关文本
        val accessibilityTexts = listOf(
            "无障碍",
            "Accessibility",
            "辅助功能",
            "服务",
            "儿童手机守护",
            "KidsPhoneGuard",
            "GuardAccessibilityService"
        )

        val rootNode = event.source ?: return false

        // 检查页面标题或内容
        for (text in accessibilityTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                // 进一步检查是否包含"关闭"、"停用"等操作按钮
                val actionTexts = listOf("关闭", "停用", "停止", "禁用", "关闭服务")
                for (action in actionTexts) {
                    val actionNodes = rootNode.findAccessibilityNodeInfosByText(action)
                    if (actionNodes.isNotEmpty()) {
                        return true
                    }
                }
            }
        }

        return false
    }

    override fun onInterrupt() {}

    private suspend fun checkPolicyAndExecute(packageName: String) {
        val rule = appRuleRepository.getRuleByPackageName(packageName)

        when (rule?.ruleType) {
            RuleType.ALLOW -> {
                hideOverlay()
                lastBlockedPackage = ""
            }
            RuleType.BLOCK -> {
                // 立即锁定
                lastBlockedPackage = packageName

                // 执行返回操作阻止应用
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 50)
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 100)

                OverlayService.showOverlay(this, packageName, rule.appName)

                // 延迟多次确认
                handler.postDelayed({
                    if (lastBlockedPackage == packageName) {
                        OverlayService.showOverlay(this, packageName, rule.appName)
                    }
                }, 200)

                handler.postDelayed({
                    if (lastBlockedPackage == packageName) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        OverlayService.showOverlay(this, packageName, rule.appName)
                    }
                }, 400)
            }
            RuleType.LIMIT -> {
                val shouldBlock = checkLimitConditions(rule)
                if (shouldBlock) {
                    lastBlockedPackage = packageName

                    // 执行返回操作阻止应用
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 50)
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 100)

                    OverlayService.showOverlay(this, packageName, rule.appName)

                    handler.postDelayed({
                        if (lastBlockedPackage == packageName) {
                            OverlayService.showOverlay(this, packageName, rule.appName)
                        }
                    }, 200)
                } else {
                    hideOverlay()
                    lastBlockedPackage = ""
                }
            }
            null -> {
                hideOverlay()
                lastBlockedPackage = ""
            }
        }
    }

    private suspend fun checkLimitConditions(rule: com.kidsphoneguard.data.model.AppRule): Boolean {
        if (rule.isGlobalLocked) {
            return true
        }

        if (rule.blockedTimeWindows.isNotEmpty()) {
            if (isInBlockedTimeWindow(rule.blockedTimeWindows)) {
                return true
            }
        }

        if (rule.dailyAllowedMinutes > 0) {
            val todayUsage = dailyUsageRepository.getTodayUsageSeconds(rule.packageName)
            val allowedSeconds = rule.dailyAllowedMinutes * 60L
            if (todayUsage >= allowedSeconds) {
                return true
            }
        }

        return false
    }

    private fun isInBlockedTimeWindow(timeWindows: String): Boolean {
        val now = LocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val windows = timeWindows.split(",")
        for (window in windows) {
            val parts = window.trim().split("-")
            if (parts.size != 2) continue

            try {
                val startTime = LocalTime.parse(parts[0].trim(), timeFormatter)
                val endTime = LocalTime.parse(parts[1].trim(), timeFormatter)

                val inWindow = if (startTime.isAfter(endTime)) {
                    now.isAfter(startTime) || now.isBefore(endTime)
                } else {
                    now.isAfter(startTime) && now.isBefore(endTime)
                }

                if (inWindow) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return false
    }

    private fun isTryingToUninstall(event: AccessibilityEvent, packageName: String): Boolean {
        if (packageName == "com.android.settings") {
            val rootNode = event.source ?: return false
            val sensitiveTexts = listOf("卸载", "强行停止", "清除数据", "KidsPhoneGuard", "儿童手机守护")

            for (text in sensitiveTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                if (nodes.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun hideOverlay() {
        OverlayService.hideOverlay(this)
    }
}
