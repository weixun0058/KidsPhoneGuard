package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.engine.BlockReason
import com.kidsphoneguard.engine.LockDecisionEngine
import com.kidsphoneguard.utils.BroadcastPermissionHelper
import com.kidsphoneguard.utils.WhitelistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GuardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GuardAccessibilityService"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        @Volatile
        private var isRunning = false
            private set

        fun isServiceRunning(): Boolean {
            return isRunning
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var lockDecisionEngine: LockDecisionEngine
    private lateinit var activityManager: ActivityManager
    private lateinit var usageStatsManager: UsageStatsManager

    private var currentPackageName: String = ""
    private var lastBlockedPackage: String = ""
    private var lastBlockTime: Long = 0

    private var lastHandledPackage: String = ""
    private var lastHandledTime: Long = 0
    private val debounceInterval = 500L

    private val blockCooldown = 5000L
    private var blockHoldUntil: Long = 0
    private val blockHoldDuration = 700L
    private val systemUiReleaseDelay = 1200L
    private val deviceManufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
    private val deviceBrand = Build.BRAND?.lowercase().orEmpty()
    private val isHuaweiFamilyDevice =
        deviceManufacturer.contains("huawei") ||
            deviceManufacturer.contains("honor") ||
            deviceBrand.contains("huawei") ||
            deviceBrand.contains("honor")
    private val assistantPackages = setOf(
        "com.huawei.gameassistant",
        "com.hihonor.gameassistant"
    )
    private var forceStopPermissionDenied = false

    private val blockAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BroadcastPermissionHelper.ACTION_BLOCK_APP) return
            val packageName = intent.getStringExtra("package_name") ?: return

            serviceScope.launch {
                try {
                    checkPolicyAndExecute(packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "拦截应用时出错: ${e.message}", e)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "Service connected")
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        Log.d(TAG, "设备厂商: $deviceManufacturer, 品牌: $deviceBrand, 华为策略: $isHuaweiFamilyDevice")

        handler.postDelayed({
            try {
                initializeService()
            } catch (e: Exception) {
                Log.e(TAG, "Service初始化失败: ${e.message}", e)
            }
        }, 100)
    }

    private fun initializeService() {
        try {
            val app = applicationContext as? KidsPhoneGuardApp
            if (app == null) {
                Log.e(TAG, "ApplicationContext为null或类型错误")
                return
            }

            serviceScope.launch {
                try {
                    lockDecisionEngine = LockDecisionEngine.getInstance(this@GuardAccessibilityService)
                    Log.d(TAG, "LockDecisionEngine 初始化成功")
                } catch (e: Exception) {
                    Log.e(TAG, "LockDecisionEngine 初始化失败: ${e.message}", e)
                }
            }

            try {
                BroadcastPermissionHelper.registerInternalBroadcastReceiver(
                    this,
                    blockAppReceiver,
                    BroadcastPermissionHelper.ACTION_BLOCK_APP
                )
            } catch (e: Exception) {
                Log.e(TAG, "注册blockAppReceiver失败: ${e.message}")
            }

            Log.d(TAG, "Service created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Service创建失败: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        BroadcastPermissionHelper.unregisterReceiver(this, blockAppReceiver)
        serviceScope.cancel()

        Log.d(TAG, "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    handleWindowEvent(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理无障碍事件时出错: ${e.message}", e)
        }
    }

    private fun handleWindowEvent(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString() ?: return
        if (eventPackageName in assistantPackages) {
            scheduleAssistantFollowUpChecks()
            return
        }
        val packageName = resolvePolicyPackage(eventPackageName)

        if (WhitelistManager.isSelfApp(packageName)) {
            if (OverlayService.isOverlayShowing()) {
                lastBlockedPackage = ""
                hideOverlay()
            }
            return
        }

        if (!::lockDecisionEngine.isInitialized) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val blockedPackage = OverlayService.getCurrentBlockedPackage()
        if (currentTime < blockHoldUntil && packageName == blockedPackage) {
            return
        }
        if (packageName == lastHandledPackage && (currentTime - lastHandledTime) < debounceInterval) {
            return
        }
        lastHandledPackage = packageName
        lastHandledTime = currentTime

        if (WhitelistManager.isInWhitelist(packageName)) {
            Log.d(TAG, "应用 $packageName 在白名单中，跳过锁定")
            if (OverlayService.isOverlayShowing()) {
                val overlayBlockedPackage = OverlayService.getCurrentBlockedPackage()
                if (overlayBlockedPackage.isEmpty()) {
                    lastBlockedPackage = ""
                    hideOverlay()
                    return
                }

                if (packageName == SYSTEM_UI_PACKAGE && (currentTime - lastBlockTime) < systemUiReleaseDelay) {
                    return
                }

                if (overlayBlockedPackage != packageName) {
                    lastBlockedPackage = ""
                    hideOverlay()
                }
            }
            return
        }

        currentPackageName = packageName

        serviceScope.launch {
            try {
                checkPolicyAndExecute(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "检查策略时出错: ${e.message}", e)
            }
        }
    }

    private fun scheduleAssistantFollowUpChecks() {
        val followUpDelays = longArrayOf(120L, 320L, 680L)
        followUpDelays.forEach { delayMillis ->
            handler.postDelayed({
                val activePackageName = rootInActiveWindow?.packageName?.toString().orEmpty()
                val candidatePackage = if (activePackageName.isNotEmpty()) {
                    activePackageName
                } else {
                    getRecentTopPackageName().orEmpty()
                }
                if (candidatePackage.isEmpty() ||
                    candidatePackage in assistantPackages ||
                    WhitelistManager.isSelfApp(candidatePackage) ||
                    WhitelistManager.isInWhitelist(candidatePackage)
                ) {
                    return@postDelayed
                }

                val now = System.currentTimeMillis()
                if (candidatePackage == lastHandledPackage && (now - lastHandledTime) < debounceInterval) {
                    return@postDelayed
                }

                lastHandledPackage = candidatePackage
                lastHandledTime = now
                serviceScope.launch {
                    try {
                        Log.d(TAG, "助手覆盖场景补偿检测: $candidatePackage")
                        checkPolicyAndExecute(candidatePackage)
                    } catch (e: Exception) {
                        Log.e(TAG, "补偿检测策略时出错: ${e.message}", e)
                    }
                }
            }, delayMillis)
        }
    }

    private fun resolvePolicyPackage(eventPackageName: String): String {
        if (eventPackageName !in assistantPackages) {
            return eventPackageName
        }

        val activePackageName = rootInActiveWindow?.packageName?.toString().orEmpty()
        val fallbackPackageName = getRecentTopPackageName().orEmpty()
        val candidatePackageName = if (activePackageName.isNotEmpty()) activePackageName else fallbackPackageName
        if (candidatePackageName.isNotEmpty() &&
            candidatePackageName != eventPackageName &&
            !WhitelistManager.isSelfApp(candidatePackageName)
        ) {
            Log.d(TAG, "事件包名 $eventPackageName 映射为活动窗口包名 $candidatePackageName")
            return candidatePackageName
        }

        return eventPackageName
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    private suspend fun checkPolicyAndExecute(packageName: String) {
        try {
            val decision = lockDecisionEngine.getBlockDecision(packageName)

            Log.d(TAG, "检查应用 $packageName, 决策结果: ${decision.reason}, 是否阻塞: ${decision.shouldBlock}")

            if (decision.shouldBlock) {
                when (decision.reason) {
                    BlockReason.GLOBAL_LOCK ->
                        Log.d(TAG, "全局锁开启，拦截应用: $packageName")
                    BlockReason.APP_BLOCKED ->
                        Log.d(TAG, "应用被永久禁用: $packageName")
                    BlockReason.TIME_LIMIT_EXCEEDED ->
                        Log.d(TAG, "应用使用时长已达限制: $packageName")
                    BlockReason.TIME_WINDOW_BLOCKED ->
                        Log.d(TAG, "应用在禁用时段内: $packageName")
                    else -> {}
                }
                enforceBlock(packageName, decision.appName.ifEmpty { packageName })
            } else {
                if (OverlayService.isOverlayShowing()) {
                    lastBlockedPackage = ""
                    hideOverlay()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查策略时出错: ${e.message}", e)
            hideOverlay()
            lastBlockedPackage = ""
        }
    }

    private fun enforceBlock(packageName: String, appName: String) {
        val currentTime = System.currentTimeMillis()
        if (OverlayService.isOverlayShowing() && OverlayService.getCurrentBlockedPackage() == packageName) {
            Log.d(TAG, "应用 $packageName 遮蔽层已显示，跳过重复拦截")
            return
        }

        var requireStrongExit = false
        if (lastBlockedPackage == packageName && (currentTime - lastBlockTime) < blockCooldown) {
            if (isTargetPackageActive(packageName)) {
                Log.d(TAG, "应用 $packageName 冷却期内仍在前台，继续执行兜底拦截")
                requireStrongExit = true
            } else {
                Log.d(TAG, "应用 $packageName 在拦截冷却期内，跳过")
                return
            }
        }

        lastBlockedPackage = packageName
        lastBlockTime = currentTime
        blockHoldUntil = currentTime + blockHoldDuration

        handler.post {
            try {
                OverlayService.showOverlay(this, packageName, appName)
            } catch (e: Exception) {
                Log.e(TAG, "显示覆盖层失败: ${e.message}")
            }
        }

        try {
            if (requireStrongExit || isHuaweiFamilyDevice) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行导航失败: ${e.message}", e)
        }

        handler.postDelayed({
            tryForceStopApp(packageName)
        }, 120)

        handler.postDelayed({
            tryForceStopApp(packageName)
        }, 360)

        handler.postDelayed({
            tryForceStopApp(packageName)
        }, 700)

        handler.postDelayed({
            tryFallbackNavigation(packageName)
        }, 520)

        handler.postDelayed({
            tryFallbackNavigation(packageName)
        }, 980)

        if (isHuaweiFamilyDevice) {
            handler.postDelayed({
                tryFallbackNavigation(packageName)
            }, 160)
            handler.postDelayed({
                tryFallbackNavigation(packageName)
            }, 320)
            handler.postDelayed({
                tryFallbackNavigation(packageName)
            }, 760)
        }

        scheduleOverlayReleaseCheck(packageName)
    }

    private fun isTargetPackageActive(packageName: String): Boolean {
        val activePackage = rootInActiveWindow?.packageName?.toString().orEmpty()
        if (activePackage == packageName) {
            return true
        }
        return getRecentTopPackageName() == packageName
    }

    private fun getRecentTopPackageName(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1200
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "读取前台应用失败: ${e.message}", e)
            null
        }
    }

    private fun tryFallbackNavigation(packageName: String) {
        if (!isTargetPackageActive(packageName)) {
            Log.d(TAG, "应用 $packageName 已离开前台，跳过兜底导航")
            return
        }

        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "兜底回桌面失败: ${e.message}", e)
        }
    }

    private fun scheduleOverlayReleaseCheck(packageName: String) {
        val releaseCheckDelays = longArrayOf(900L, 1700L, 2600L)
        releaseCheckDelays.forEach { delayMillis ->
            handler.postDelayed({
                if (!OverlayService.isOverlayShowing()) {
                    return@postDelayed
                }
                if (OverlayService.getCurrentBlockedPackage() != packageName) {
                    return@postDelayed
                }
                if (isTargetPackageActive(packageName)) {
                    return@postDelayed
                }
                Log.d(TAG, "应用 $packageName 不在前台，自动关闭遮蔽层")
                hideOverlay()
                lastBlockedPackage = ""
            }, delayMillis)
        }
    }

    private fun hideOverlay() {
        try {
            OverlayService.hideOverlay(this)
        } catch (e: Exception) {
            Log.e(TAG, "隐藏覆盖层失败: ${e.message}")
        }
    }

    private fun tryForceStopApp(packageName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activityManager.appTasks?.forEach { task ->
                    val taskInfo = task.taskInfo
                    val taskPackage = taskInfo.baseActivity?.packageName
                    val topPackage = taskInfo.topActivity?.packageName
                    val intentPackage = taskInfo.baseIntent.component?.packageName
                    if (taskPackage == packageName || topPackage == packageName || intentPackage == packageName) {
                        try {
                            task.finishAndRemoveTask()
                        } catch (e: Exception) {
                            Log.e(TAG, "结束任务失败: ${e.message}", e)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val runningApps = activityManager.runningAppProcesses
                runningApps?.forEach { processInfo ->
                    if (processInfo.pkgList.contains(packageName)) {
                        try {
                            Process.killProcess(processInfo.pid)
                        } catch (e: Exception) {
                            Log.e(TAG, "杀进程失败: ${e.message}", e)
                        }
                    }
                }
            }

            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "杀后台失败: ${e.message}", e)
        }

        if (forceStopPermissionDenied) {
            return
        }

        try {
            val method = activityManager.javaClass.getMethod("forceStopPackage", String::class.java)
            method.invoke(activityManager, packageName)
        } catch (e: Exception) {
            val securityDenied = e is SecurityException || e.cause is SecurityException
            if (securityDenied) {
                forceStopPermissionDenied = true
                Log.w(TAG, "forceStopPackage无权限，后续改用前台压制策略")
                return
            }
            Log.e(TAG, "forceStopPackage失败: ${e.message}", e)
        }

        try {
            Runtime.getRuntime().exec("am force-stop $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "am force-stop失败: ${e.message}", e)
        }
    }

}
