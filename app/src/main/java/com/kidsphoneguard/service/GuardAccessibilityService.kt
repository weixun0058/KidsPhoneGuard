package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
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
        @Volatile
        private var latestLifecycleSignal = "init"

        fun isServiceRunning(): Boolean {
            return isRunning
        }

        fun getLatestLifecycleSignal(): String {
            return latestLifecycleSignal
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
    private val overlayReshowCooldown = 6000L
    private val overlayStabilityWindow = 2200L
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
    private val uninstallKeywords = setOf("卸载", "uninstall", "delete", "移除")
    private var lastSensitiveActionBlockTime = 0L
    private val sensitiveActionCooldownMs = 1200L
    private var forceStopPermissionDenied = false
    private var lastOverlayPackage: String = ""
    private var lastOverlayShowTime: Long = 0
    private var lastEventSignalTimestamp = 0L
    private var lastProtectedWindowLogTime: Long = 0L
    private var lastProtectedWindowSignature: String = ""
    private val protectedWindowLogCooldownMs = 1000L
    private var lastProtectedWindowSweepPackage: String = ""
    private var lastProtectedWindowSweepTime: Long = 0L
    private val protectedWindowSweepIntervalMs = 900L
    private val protectedWindowSweepCooldownMs = 1000L
    private var pendingBlockPackage: String = ""
    private val pendingBlockActions = mutableListOf<Runnable>()
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            GuardHealthState.touchAccessibilityHeartbeat(this@GuardAccessibilityService)
            handler.postDelayed(this, 4000L)
        }
    }
    private val protectedWindowSweepRunnable = object : Runnable {
        override fun run() {
            try {
                sweepProtectedInteractiveWindows("periodic_window_sweep")
            } catch (e: Exception) {
                Log.e(TAG, "protected_window_sweep_failed: ${e.message}", e)
            } finally {
                handler.postDelayed(this, protectedWindowSweepIntervalMs)
            }
        }
    }

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
        publishLifecycleSignal("onServiceConnected")
        GuardHealthState.touchAccessibilityHeartbeat(this)
        Log.d(TAG, "Service connected")
        logAccessibilitySettingsSnapshot("service_connected")
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        publishLifecycleSignal("onCreate")
        GuardHealthState.touchAccessibilityHeartbeat(this)
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        Log.d(TAG, "设备厂商: $deviceManufacturer, 品牌: $deviceBrand, 华为策略: $isHuaweiFamilyDevice")
        logAccessibilitySettingsSnapshot("service_onCreate")

        handler.postDelayed({
            try {
                initializeService()
            } catch (e: Exception) {
                Log.e(TAG, "Service初始化失败: ${e.message}", e)
            }
        }, 100)
        handler.post(heartbeatRunnable)
        handler.postDelayed(protectedWindowSweepRunnable, protectedWindowSweepIntervalMs)
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
        publishLifecycleSignal("onDestroy")
        GuardHealthState.clearAccessibilityHeartbeat(this)

        BroadcastPermissionHelper.unregisterReceiver(this, blockAppReceiver)
        serviceScope.cancel()
        handler.removeCallbacks(heartbeatRunnable)
        handler.removeCallbacks(protectedWindowSweepRunnable)
        cancelPendingBlockActions("service_onDestroy")

        Log.d(TAG, "Service destroyed")
        logAccessibilitySettingsSnapshot("service_onDestroy")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        GuardHealthState.clearAccessibilityHeartbeat(this)
        publishLifecycleSignal("onUnbind:${intent?.action.orEmpty()}")
        Log.w(TAG, "Service onUnbind intentAction=${intent?.action}")
        logAccessibilitySettingsSnapshot("service_onUnbind")
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        publishLifecycleSignal("onRebind:${intent?.action.orEmpty()}")
        Log.w(TAG, "Service onRebind intentAction=${intent?.action}")
        logAccessibilitySettingsSnapshot("service_onRebind")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        GuardHealthState.touchAccessibilityHeartbeat(this)
        publishEventSignalIfNeeded(event)
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    handleWindowEvent(event)
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handlePotentialProtectedInteraction(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理无障碍事件时出错: ${e.message}", e)
        }
    }

    private fun handleWindowEvent(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString() ?: return
        val protectedWindowPackage = findProtectedInteractiveWindowPackage(
            "window_event:${event.eventType}:$eventPackageName"
        )
        if (eventPackageName in assistantPackages && protectedWindowPackage == null) {
            scheduleAssistantFollowUpChecks()
            return
        }
        val packageName = protectedWindowPackage ?: resolvePolicyPackage(eventPackageName)
        if (shouldBlockSensitiveAction(event, packageName)) {
            blockSensitiveAction(packageName, event)
            return
        }

        if (WhitelistManager.isSelfApp(packageName)) {
            cancelPendingBlockActions("self_app_event:$packageName")
            if (OverlayService.isOverlayShowing()) {
                lastBlockedPackage = ""
                hideOverlay()
            }
            return
        }

        if (!ensureLockDecisionEngineInitialized()) {
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

        if (WhitelistManager.isInWhitelist(packageName) &&
            !WhitelistManager.isSettings(packageName) &&
            !WhitelistManager.isInstallerOrMarket(packageName)
        ) {
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

                if (overlayBlockedPackage != packageName && isTargetPackageActive(overlayBlockedPackage)) {
                    Log.d(TAG, "白名单过渡界面 $packageName 出现，但被拦截应用 $overlayBlockedPackage 仍在前台，保持遮蔽层")
                    return
                }

                if (overlayBlockedPackage != packageName) {
                    cancelPendingBlockActions("whitelist_transition:$packageName")
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

    private fun handlePotentialProtectedInteraction(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString().orEmpty()
        if (eventPackageName.isEmpty()) {
            return
        }

        val protectedWindowPackage = findProtectedInteractiveWindowPackage(
            "interactive_event:${event.eventType}:$eventPackageName"
        )
        val packageName = protectedWindowPackage ?: resolvePolicyPackage(eventPackageName)

        if (shouldBlockSensitiveAction(event, packageName)) {
            blockSensitiveAction(packageName, event)
            return
        }

        if (!isProtectedSystemSurface(packageName)) {
            return
        }
        if (WhitelistManager.isSelfApp(packageName)) {
            return
        }
        if (!ensureLockDecisionEngineInitialized()) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (packageName == lastHandledPackage && (currentTime - lastHandledTime) < debounceInterval) {
            return
        }
        lastHandledPackage = packageName
        lastHandledTime = currentTime

        Log.w(TAG, "protected_interaction_detected event=${event.eventType} package=$packageName source=$eventPackageName")
        serviceScope.launch {
            try {
                checkPolicyAndExecute(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "protected_interaction_policy_failed: ${e.message}", e)
            }
        }
    }

    private fun sweepProtectedInteractiveWindows(source: String) {
        val packageName = findProtectedInteractiveWindowPackage(source) ?: return
        if (WhitelistManager.isSelfApp(packageName)) {
            return
        }
        if (!ensureLockDecisionEngineInitialized()) {
            return
        }

        val now = System.currentTimeMillis()
        if (packageName == lastProtectedWindowSweepPackage &&
            (now - lastProtectedWindowSweepTime) < protectedWindowSweepCooldownMs
        ) {
            return
        }
        lastProtectedWindowSweepPackage = packageName
        lastProtectedWindowSweepTime = now

        Log.w(TAG, "protected_window_sweep_detected source=$source package=$packageName")
        serviceScope.launch {
            try {
                checkPolicyAndExecute(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "protected_window_sweep_policy_failed: ${e.message}", e)
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
        isRunning = false
        GuardHealthState.clearAccessibilityHeartbeat(this)
        publishLifecycleSignal("onInterrupt")
        Log.d(TAG, "Service interrupted")
        logAccessibilitySettingsSnapshot("service_onInterrupt")
    }

    private fun publishEventSignalIfNeeded(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastEventSignalTimestamp < 2000L) {
            return
        }
        lastEventSignalTimestamp = now
        val eventPackage = event.packageName?.toString().orEmpty()
        publishLifecycleSignal("event:${event.eventType}:$eventPackage")
    }

    private fun publishLifecycleSignal(signal: String) {
        latestLifecycleSignal = "${System.currentTimeMillis()}|$signal"
    }

    private fun logAccessibilitySettingsSnapshot(source: String) {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.replace("\n", " ")?.take(240)
        Log.w(
            TAG,
            "accessibility_service_snapshot source=$source accessibility_enabled=$enabled enabled_services=$enabledServices"
        )
    }

    private suspend fun checkPolicyAndExecute(packageName: String) {
        try {
            if (!ensureLockDecisionEngineInitialized()) {
                return
            }
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
                    val now = System.currentTimeMillis()
                    if ((now - lastBlockTime) >= overlayStabilityWindow) {
                        lastBlockedPackage = ""
                        hideOverlay()
                    } else {
                        Log.d(TAG, "保持遮蔽层稳定窗口，暂不隐藏")
                    }
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
        cancelPendingBlockActions("new_block:$packageName")
        val protectedSystemSurface = isProtectedSystemSurface(packageName)
        if (OverlayService.isOverlayShowing() &&
            OverlayService.getCurrentBlockedPackage() == packageName &&
            !protectedSystemSurface
        ) {
            Log.d(TAG, "应用 $packageName 遮蔽层已显示，跳过重复拦截")
            return
        }

        if (OverlayService.isOverlayShowing() && OverlayService.getCurrentBlockedPackage() == packageName) {
            Log.w(TAG, "protected_surface_reinforce package=$packageName")
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
        pendingBlockPackage = packageName
        val shouldReshowOverlay = !(lastOverlayPackage == packageName &&
            (currentTime - lastOverlayShowTime) < overlayReshowCooldown)

        if (shouldReshowOverlay) {
            handler.post {
                try {
                    OverlayService.showOverlay(this, packageName, appName)
                    lastOverlayPackage = packageName
                    lastOverlayShowTime = System.currentTimeMillis()
                } catch (e: Exception) {
                    Log.e(TAG, "显示覆盖层失败: ${e.message}")
                }
            }
        } else {
            Log.d(TAG, "应用 $packageName 处于遮蔽层重展示冷却期，执行静默压制")
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

        scheduleDeferredBlockAction(packageName, 120L, "force_stop_120") {
            tryForceStopApp(packageName)
        }
        scheduleDeferredBlockAction(packageName, 360L, "force_stop_360") {
            tryForceStopApp(packageName)
        }
        scheduleDeferredBlockAction(packageName, 700L, "force_stop_700") {
            tryForceStopApp(packageName)
        }
        scheduleDeferredBlockAction(packageName, 520L, "fallback_nav_520") {
            tryFallbackNavigation(packageName)
        }
        scheduleDeferredBlockAction(packageName, 980L, "fallback_nav_980") {
            tryFallbackNavigation(packageName)
        }

        if (isHuaweiFamilyDevice) {
            scheduleDeferredBlockAction(packageName, 160L, "fallback_nav_huawei_160") {
                tryFallbackNavigation(packageName)
            }
            scheduleDeferredBlockAction(packageName, 320L, "fallback_nav_huawei_320") {
                tryFallbackNavigation(packageName)
            }
            scheduleDeferredBlockAction(packageName, 760L, "fallback_nav_huawei_760") {
                tryFallbackNavigation(packageName)
            }
        }

        scheduleOverlayReleaseCheck(packageName)
    }

    private fun isTargetPackageActive(packageName: String): Boolean {
        val activePackage = rootInActiveWindow?.packageName?.toString().orEmpty()
        if (activePackage == packageName) {
            return true
        }
        if (isPackageVisibleInInteractiveWindows(packageName)) {
            return true
        }
        return getRecentTopPackageName() == packageName
    }

    private fun isProtectedSystemSurface(packageName: String): Boolean {
        return WhitelistManager.isSettings(packageName) || WhitelistManager.isInstallerOrMarket(packageName)
    }

    private fun findProtectedInteractiveWindowPackage(source: String): String? {
        val windowSnapshots = mutableListOf<String>()
        val protectedPackages = linkedSetOf<String>()

        forEachInteractiveWindow { packageName, summary ->
            windowSnapshots.add(summary)
            if (packageName.isNotEmpty() && isProtectedSystemSurface(packageName)) {
                protectedPackages.add(packageName)
            }
        }

        val targetPackage = protectedPackages.firstOrNull() ?: return null
        logProtectedWindowSnapshot(source, targetPackage, windowSnapshots)
        return targetPackage
    }

    private fun isPackageVisibleInInteractiveWindows(packageName: String): Boolean {
        var found = false
        forEachInteractiveWindow { windowPackageName, _ ->
            if (windowPackageName == packageName) {
                found = true
            }
        }
        return found
    }

    private fun forEachInteractiveWindow(consumer: (packageName: String, summary: String) -> Unit) {
        val windowList = try {
            windows
        } catch (e: Exception) {
            Log.e(TAG, "read_interactive_windows_failed: ${e.message}", e)
            return
        }

        windowList?.forEach { window ->
            val bounds = Rect()
            try {
                window.getBoundsInScreen(bounds)
            } catch (e: Exception) {
                Log.e(TAG, "window_bounds_failed: ${e.message}", e)
            }

            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(TAG, "window_root_failed: ${e.message}", e)
                null
            }
            val packageName = root?.packageName?.toString().orEmpty()
            root?.recycle()

            val summary = buildString {
                append("id=").append(window.id)
                append(",type=").append(window.type)
                append(",active=").append(window.isActive)
                append(",focused=").append(window.isFocused)
                append(",pkg=").append(packageName.ifEmpty { "unknown" })
                append(",bounds=").append(bounds.flattenToString())
            }
            consumer(packageName, summary)
        }
    }

    private fun logProtectedWindowSnapshot(
        source: String,
        targetPackage: String,
        windowSnapshots: List<String>
    ) {
        val now = System.currentTimeMillis()
        val signature = "$targetPackage|${windowSnapshots.joinToString(";")}"
        if (signature == lastProtectedWindowSignature &&
            (now - lastProtectedWindowLogTime) < protectedWindowLogCooldownMs
        ) {
            return
        }

        lastProtectedWindowSignature = signature
        lastProtectedWindowLogTime = now
        Log.w(
            TAG,
            "protected_window_detected source=$source target=$targetPackage windows=" +
                windowSnapshots.joinToString(" || ").take(900)
        )
        publishLifecycleSignal("protected_window:$targetPackage")
    }

    private fun getRecentTopPackageName(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 4000
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (!isForegroundEvent) {
                    continue
                }
                val packageName = event.packageName ?: continue
                if (WhitelistManager.isSelfApp(packageName)) {
                    continue
                }
                if (event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = packageName
                }
            }
            if (!latestPackage.isNullOrEmpty()) {
                return latestPackage
            }
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

    private fun ensureLockDecisionEngineInitialized(): Boolean {
        if (::lockDecisionEngine.isInitialized) {
            return true
        }
        return try {
            lockDecisionEngine = LockDecisionEngine.getInstance(this)
            Log.w(TAG, "LockDecisionEngine 延迟初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "LockDecisionEngine 延迟初始化失败: ${e.message}", e)
            false
        }
    }

    private fun shouldBlockSensitiveAction(event: AccessibilityEvent, packageName: String): Boolean {
        val sensitiveSource = WhitelistManager.isLauncher(packageName) ||
            WhitelistManager.isSettings(packageName) ||
            WhitelistManager.isInstallerOrMarket(packageName)
        if (!sensitiveSource) {
            return false
        }
        val textSignal = buildEventSignal(event)
        return uninstallKeywords.any { textSignal.contains(it, ignoreCase = true) }
    }

    private fun buildEventSignal(event: AccessibilityEvent): String {
        val eventText = event.text.joinToString("|") { it?.toString().orEmpty() }
        val contentDescription = event.contentDescription?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        return listOf(eventText, contentDescription, className).joinToString("|")
    }

    private fun blockSensitiveAction(packageName: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastSensitiveActionBlockTime < sensitiveActionCooldownMs) {
            return
        }
        lastSensitiveActionBlockTime = now
        val signal = buildEventSignal(event).take(200)
        Log.w(TAG, "sensitive_action_block package=$packageName signal=$signal")
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "sensitive_action_home_failed: ${e.message}", e)
        }
        handler.post {
            try {
                OverlayService.showOverlay(this, packageName, "系统受保护")
            } catch (e: Exception) {
                Log.e(TAG, "sensitive_action_overlay_failed: ${e.message}", e)
            }
        }
        handler.postDelayed({
            if (OverlayService.getCurrentBlockedPackage() == packageName) {
                hideOverlay()
                lastBlockedPackage = ""
            }
        }, 1500L)
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

    private fun scheduleDeferredBlockAction(
        targetPackage: String,
        delayMs: Long,
        actionLabel: String,
        action: () -> Unit
    ) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            pendingBlockActions.remove(runnable)
            if (!canExecuteDeferredBlockAction(targetPackage, actionLabel)) {
                return@Runnable
            }
            action()
        }
        pendingBlockActions.add(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    private fun canExecuteDeferredBlockAction(targetPackage: String, actionLabel: String): Boolean {
        if (pendingBlockPackage != targetPackage) {
            Log.d(TAG, "延迟动作 $actionLabel 取消：目标已切换为 $pendingBlockPackage")
            return false
        }

        val activePackage = rootInActiveWindow?.packageName?.toString().orEmpty()
        val targetActiveOrVisible = isTargetPackageActive(targetPackage)
        if (WhitelistManager.isSelfApp(activePackage)) {
            Log.d(TAG, "延迟动作 $actionLabel 取消：当前前台为本应用")
            return false
        }
        if (activePackage.isNotEmpty() && activePackage != targetPackage && !targetActiveOrVisible) {
            Log.d(TAG, "延迟动作 $actionLabel 取消：当前前台=$activePackage, 目标=$targetPackage")
            return false
        }
        if (!targetActiveOrVisible) {
            Log.d(TAG, "延迟动作 $actionLabel 取消：目标已不在前台")
            return false
        }
        return true
    }

    private fun cancelPendingBlockActions(reason: String) {
        if (pendingBlockActions.isNotEmpty()) {
            pendingBlockActions.forEach { runnable ->
                handler.removeCallbacks(runnable)
            }
            pendingBlockActions.clear()
        }
        if (pendingBlockPackage.isNotEmpty()) {
            Log.d(TAG, "清理延迟拦截任务 reason=$reason package=$pendingBlockPackage")
        }
        pendingBlockPackage = ""
    }

    private fun scheduleOverlayReleaseCheck(packageName: String) {
        val releaseCheckDelays = longArrayOf(1500L, 2800L, 4200L)
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
                lastOverlayPackage = ""
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
