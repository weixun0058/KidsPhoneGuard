package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.engine.BlockReason
import com.kidsphoneguard.engine.LockDecisionEngine
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsDecision
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsDecisionType
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsPolicy
import com.kidsphoneguard.engine.settingsprotection.SettingsPageSnapshot
import com.kidsphoneguard.utils.BroadcastPermissionHelper
import com.kidsphoneguard.utils.SettingsManager
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
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_FINDER_SURFACE = "com.tencent.mm:finder"
        private const val WECHAT_FINDER_APP_NAME = "微信视频号"

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
    private val protectedSettingsPolicy by lazy {
        ProtectedSettingsPolicy(SettingsManager.getInstance(this))
    }

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
    private val isXiaomiFamilyDevice =
        deviceManufacturer.contains("xiaomi") ||
            deviceManufacturer.contains("redmi") ||
            deviceManufacturer.contains("poco") ||
            deviceBrand.contains("xiaomi") ||
            deviceBrand.contains("redmi") ||
            deviceBrand.contains("poco")
    private val assistantPackages = setOf(
        "com.huawei.gameassistant",
        "com.hihonor.gameassistant"
    )
    private val uninstallKeywords = setOf("卸载", "uninstall", "delete", "移除")
    private val destructiveActionKeywords = uninstallKeywords + setOf(
        "强行停止",
        "强制停止",
        "停止运行",
        "清除数据",
        "删除应用",
        "卸载应用",
        "是否卸载",
        "要卸载",
        "确定卸载"
    )
    private val launcherUninstallConfirmKeywords = setOf(
        "卸载",
        "是否卸载",
        "要卸载",
        "确定卸载",
        "卸载应用",
        "删除应用",
        "Uninstall app",
        "Delete app"
    )
    private val targetAppKeywords = setOf(
        "拉钩守护",
        "KidsPhoneGuard",
        "儿童手机守护",
        "com.kidsphoneguard"
    )
    private val sensitiveCancelKeywords = setOf("取消", "Cancel")
    private var lastSensitiveActionBlockTime = 0L
    private val sensitiveActionCooldownMs = 120L
    private val sensitiveDialogActionCooldownMs = 20L
    private val sensitiveEscapeActions = mutableListOf<Runnable>()
    private var miuiLauncherIconMenuBlockUntil: Long = 0L
    private var forceStopPermissionDenied = false
    private var lastOverlayPackage: String = ""
    private var lastOverlayShowTime: Long = 0
    private var lastEventSignalTimestamp = 0L
    private var lastProtectedWindowLogTime: Long = 0L
    private var lastProtectedWindowSignature: String = ""
    private val protectedWindowLogCooldownMs = 1000L
    private var lastProtectedSettingsDecisionLogTime: Long = 0L
    private var lastProtectedSettingsDecisionSignature: String = ""
    private val protectedSettingsDecisionLogCooldownMs = 1000L
    private val settingsSnapshotTextLimit = 3000
    private val systemPanelSnapshotTextLimit = 16000
    private var lastProtectedWindowSweepPackage: String = ""
    private var lastProtectedWindowSweepTime: Long = 0L
    private val protectedWindowSweepIntervalMs = 180L
    private val protectedWindowSweepCooldownMs = 180L
    private var lastProtectedSurfaceSuppressPackage: String = ""
    private var lastProtectedSurfaceSuppressTime: Long = 0L
    private val protectedSurfaceSuppressCooldownMs = 120L
    private val protectedSurfaceNavigationBurstDelays = longArrayOf(0L, 60L, 140L, 280L, 800L, 1500L, 3000L)
    private val systemPanelPackages = setOf(
        SYSTEM_UI_PACKAGE,
        "com.huawei.controlcenter"
    )
    private var lastSystemPanelCollapseTime: Long = 0L
    private val systemPanelCollapseCooldownMs = 240L
    private val systemPanelCollapseReinforceDelayMs = 45L
    private var lastPowerSaveExitAttemptTime: Long = 0L
    private val powerSaveExitAttemptCooldownMs = 220L
    private val powerSaveExitPackages = setOf(
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher"
    )
    private val powerSaveExitActivitySignals = setOf(
        "powersavemode.PowerSaveModeLauncher",
        "PowerSaveModeLauncher"
    )
    private val powerSaveExitKeywords = setOf(
        "退出",
        "退出超级省电",
        "退出超级省电模式",
        "关闭超级省电",
        "确定"
    )
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
        cancelSensitiveEscapeActions()

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
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
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
        val source = "window_event:${event.eventType}:$eventPackageName"
        val resolvedEventPackage = resolvePolicyPackage(eventPackageName)
        val protectedWindowPackage = if (shouldSweepProtectedWindows(event, resolvedEventPackage)) {
            findProtectedInteractiveWindowPackage(source)
        } else {
            null
        }
        if (eventPackageName in assistantPackages && protectedWindowPackage == null) {
            scheduleAssistantFollowUpChecks()
            return
        }
        val packageName = protectedWindowPackage ?: resolvedEventPackage

        if (exitPowerSaveModeIfNeeded(event, source)) {
            return
        }

        if (collapseSystemPanelIfNeeded(event, packageName, source)) {
            return
        }

        if (shouldBlockSensitiveAction(event, packageName)) {
            blockSensitiveAction(packageName, event)
            return
        }

        if (handleProtectedSettingsPolicyIfCandidate(event, packageName, source)) {
            return
        }

        if (WhitelistManager.isSelfApp(packageName)) {
            val overlayBlockedPackage = OverlayService.getCurrentBlockedPackage()
            val keepProtectedOverlay = OverlayService.isOverlayShowing() &&
                isProtectedSystemSurface(overlayBlockedPackage)
            val keepProtectedPending = isProtectedSystemSurface(pendingBlockPackage)
            if (keepProtectedOverlay || keepProtectedPending) {
                Log.d(
                    TAG,
                    "self_app_event_keep_protected_overlay blocked=$overlayBlockedPackage pending=$pendingBlockPackage"
                )
                return
            }
            cancelPendingBlockActions("self_app_event:$packageName")
            if (OverlayService.isOverlayShowing()) {
                lastBlockedPackage = ""
                hideOverlay()
            }
            return
        }

        if (shouldBlockWeChatFinder(event, packageName)) {
            blockWeChatFinder(event)
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

    private fun shouldBlockWeChatFinder(event: AccessibilityEvent, packageName: String): Boolean {
        if (packageName != WECHAT_PACKAGE) {
            return false
        }
        val settingsManager = SettingsManager.getInstance(this)
        if (!settingsManager.isWeChatFinderBlockEnabled() || settingsManager.isGlobalUnlockEnabled()) {
            return false
        }
        val className = event.className?.toString().orEmpty()
        return className.startsWith("com.tencent.mm.plugin.finder.") && className.endsWith("UI")
    }

    private fun blockWeChatFinder(event: AccessibilityEvent) {
        val currentTime = System.currentTimeMillis()
        val className = event.className?.toString().orEmpty()
        if (lastBlockedPackage == WECHAT_FINDER_SURFACE && (currentTime - lastBlockTime) < 1200L) {
            Log.d(TAG, "wechat_finder_block_skip_cooldown class=$className")
            return
        }

        cancelPendingBlockActions("wechat_finder:$className")
        lastBlockedPackage = WECHAT_FINDER_SURFACE
        lastBlockTime = currentTime
        blockHoldUntil = currentTime + blockHoldDuration
        pendingBlockPackage = WECHAT_PACKAGE
        publishLifecycleSignal("wechat_finder_block:$className")
        Log.w(TAG, "wechat_finder_block class=$className")

        handler.post {
            try {
                OverlayService.showOverlay(this, WECHAT_FINDER_SURFACE, WECHAT_FINDER_APP_NAME)
                lastOverlayPackage = WECHAT_FINDER_SURFACE
                lastOverlayShowTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "显示微信视频号遮蔽层失败: ${e.message}", e)
            }
        }

        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.e(TAG, "退出微信视频号失败: ${e.message}", e)
        }

        handler.postDelayed({
            if (OverlayService.getCurrentBlockedPackage() == WECHAT_FINDER_SURFACE) {
                Log.d(TAG, "wechat_finder_overlay_auto_release")
                hideOverlay()
            }
            if (pendingBlockPackage == WECHAT_PACKAGE) {
                pendingBlockPackage = ""
            }
        }, 1500L)
    }

    private fun handlePotentialProtectedInteraction(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString().orEmpty()
        if (eventPackageName.isEmpty()) {
            return
        }
        val source = "interactive_event:${event.eventType}:$eventPackageName"

        val resolvedEventPackage = resolvePolicyPackage(eventPackageName)
        val protectedWindowPackage = if (shouldSweepProtectedWindows(event, resolvedEventPackage)) {
            findProtectedInteractiveWindowPackage(source)
        } else {
            null
        }
        val packageName = protectedWindowPackage ?: resolvedEventPackage

        if (collapseSystemPanelIfNeeded(event, packageName, source)) {
            return
        }

        if (shouldBlockSensitiveAction(event, packageName)) {
            blockSensitiveAction(packageName, event)
            return
        }

        if (handleProtectedSettingsPolicyIfCandidate(event, packageName, source)) {
            return
        }
    }

    private fun collapseSystemPanelIfNeeded(
        event: AccessibilityEvent,
        packageName: String,
        source: String
    ): Boolean {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return false
        }
        if (!shouldInspectSystemPanel(event, packageName)) {
            return false
        }
        val panelSignal = buildSystemPanelSignal(event)
        if (!protectedSettingsPolicy.containsGuardianDisruptiveCapabilitySignal(panelSignal)) {
            return false
        }

        return collapseSystemPanelWithSignal(packageName, source, panelSignal)
    }

    private fun collapseVisibleSystemPanelIfNeeded(source: String): Boolean {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return false
        }
        val panelSignal = buildVisibleSystemPanelSignal()
        if (!protectedSettingsPolicy.containsGuardianDisruptiveCapabilitySignal(panelSignal)) {
            return false
        }

        return collapseSystemPanelWithSignal(SYSTEM_UI_PACKAGE, source, panelSignal)
    }

    private fun collapseSystemPanelWithSignal(
        packageName: String,
        source: String,
        panelSignal: String
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSystemPanelCollapseTime < systemPanelCollapseCooldownMs) {
            return true
        }
        lastSystemPanelCollapseTime = now

        val handled = performSystemPanelCollapseAction(source)
        Log.w(
            TAG,
            "system_panel_collapse source=$source package=$packageName action=DISMISS_SHADE_OR_BACK handled=$handled " +
                "signal=${panelSignal.take(240)}"
        )
        handler.postDelayed({
            try {
                val secondHandled = performSystemPanelCollapseAction(source)
                Log.w(
                    TAG,
                    "system_panel_collapse_reinforce source=$source package=$packageName " +
                        "action=DISMISS_SHADE_OR_BACK handled=$secondHandled"
                )
            } catch (e: Exception) {
                Log.e(TAG, "system_panel_collapse_reinforce_failed source=$source reason=${e.message}", e)
            }
        }, systemPanelCollapseReinforceDelayMs)
        return true
    }

    private fun performSystemPanelCollapseAction(source: String): Boolean {
        return try {
            if (performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)) {
                true
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "system_panel_collapse_failed source=$source reason=${e.message}", e)
            false
        }
    }

    private fun exitPowerSaveModeIfNeeded(event: AccessibilityEvent, source: String): Boolean {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return false
        }
        val packageName = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        if (!isPowerSaveLauncherPackage(packageName) ||
            powerSaveExitActivitySignals.none { className.contains(it, ignoreCase = true) }
        ) {
            return false
        }
        return triggerPowerSaveExit(source, event.source)
    }

    private fun exitVisiblePowerSaveModeIfNeeded(source: String): Boolean {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return false
        }
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "power_save_exit_root_failed source=$source reason=${e.message}", e)
            null
        }

        try {
            val packageName = root?.packageName?.toString().orEmpty()
            if (!isPowerSaveLauncherPackage(packageName)) {
                return false
            }
            val signal = collectPowerSaveExitSignal(root)
            if (!containsPowerSaveExitSignal(signal)) {
                return false
            }
        } finally {
            root?.recycle()
        }

        return triggerPowerSaveExit(source, null)
    }

    private fun triggerPowerSaveExit(source: String, sourceNode: AccessibilityNodeInfo?): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPowerSaveExitAttemptTime < powerSaveExitAttemptCooldownMs) {
            sourceNode?.recycle()
            return true
        }
        lastPowerSaveExitAttemptTime = now

        val clicked = try {
            clickPowerSaveExitNode(sourceNode, source)
        } finally {
            sourceNode?.recycle()
        }

        schedulePowerSaveExitBurst(source)
        Log.w(TAG, "power_save_exit_attempt source=$source clickedNode=$clicked")
        return true
    }

    private fun clickPowerSaveExitNode(node: AccessibilityNodeInfo?, source: String): Boolean {
        if (node != null && clickPowerSaveExitNodeInTree(node, source)) {
            return true
        }

        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "power_save_exit_click_root_failed source=$source reason=${e.message}", e)
            null
        }
        try {
            if (root != null && clickPowerSaveExitNodeInTree(root, source)) {
                return true
            }
        } finally {
            root?.recycle()
        }

        val windowList = try {
            windows
        } catch (e: Exception) {
            Log.e(TAG, "power_save_exit_click_windows_failed source=$source reason=${e.message}", e)
            null
        }
        windowList?.forEach { window ->
            val windowRoot = try {
                window.root
            } catch (e: Exception) {
                Log.e(TAG, "power_save_exit_click_window_root_failed source=$source reason=${e.message}", e)
                null
            }
            try {
                val packageName = windowRoot?.packageName?.toString().orEmpty()
                if (isPowerSaveLauncherPackage(packageName) &&
                    windowRoot != null &&
                    clickPowerSaveExitNodeInTree(windowRoot, source)
                ) {
                    return true
                }
            } finally {
                windowRoot?.recycle()
            }
        }
        return false
    }

    private fun clickPowerSaveExitNodeInTree(
        node: AccessibilityNodeInfo,
        source: String,
        depth: Int = 0
    ): Boolean {
        if (depth > 40) {
            return false
        }
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val signal = "$text $description"
        if (powerSaveExitKeywords.any { signal.contains(it, ignoreCase = true) }) {
            val clickTarget = findClickableAncestor(node)
            if (clickTarget != null) {
                try {
                    val handled = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.w(
                        TAG,
                        "power_save_exit_click_node source=$source handled=$handled " +
                            "signal=${signal.take(120)}"
                    )
                    return handled
                } finally {
                    clickTarget.recycle()
                }
            }
        }

        for (index in 0 until node.childCount) {
            val child = try {
                node.getChild(index)
            } catch (e: Exception) {
                Log.e(TAG, "power_save_exit_child_failed source=$source reason=${e.message}", e)
                null
            }
            try {
                if (child != null && clickPowerSaveExitNodeInTree(child, source, depth + 1)) {
                    return true
                }
            } finally {
                child?.recycle()
            }
        }
        return false
    }

    private fun schedulePowerSaveExitBurst(source: String) {
        val delays = longArrayOf(0L, 45L, 120L, 260L, 520L)
        delays.forEach { delay ->
            if (delay == 0L) {
                tapPowerSaveExitArea(source, delay)
            } else {
                handler.postDelayed({
                    tapPowerSaveExitArea(source, delay)
                    clickPowerSaveExitNode(null, "$source:burst:$delay")
                }, delay)
            }
        }
    }

    private fun tapPowerSaveExitArea(source: String, delayMs: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.92f, height * 0.055f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 20L))
            .build()
        val dispatched = try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "power_save_exit_gesture_failed source=$source delayMs=$delayMs reason=${e.message}", e)
            false
        }
        Log.w(TAG, "power_save_exit_gesture source=$source delayMs=$delayMs dispatched=$dispatched")
        return dispatched
    }

    private fun collectPowerSaveExitSignal(node: AccessibilityNodeInfo?): String {
        val signals = mutableListOf<String>()
        val packages = mutableSetOf<String>()
        collectNodeSignals(
            node,
            signals,
            packages,
            maxTextLength = systemPanelSnapshotTextLimit,
            visibleOnly = false
        )
        return signals.joinToString(" ")
    }

    private fun containsPowerSaveExitSignal(signal: String): Boolean {
        return powerSaveExitKeywords.any { signal.contains(it, ignoreCase = true) } ||
            signal.contains("超级省电", ignoreCase = true)
    }

    private fun isPowerSaveLauncherPackage(packageName: String): Boolean {
        val normalized = packageName.trim().substringBefore(':').lowercase()
        return powerSaveExitPackages.any { normalized == it || normalized.startsWith("$it.") }
    }

    private fun isSystemPanelPackage(packageName: String): Boolean {
        val normalized = packageName.trim().substringBefore(':').lowercase()
        return systemPanelPackages.any { normalized == it || normalized.startsWith("$it.") }
    }

    private fun shouldInspectSystemPanel(event: AccessibilityEvent, packageName: String): Boolean {
        val eventPackageName = event.packageName?.toString().orEmpty()
        return isSystemPanelPackage(packageName) || isSystemPanelPackage(eventPackageName)
    }

    private fun buildSystemPanelSignal(event: AccessibilityEvent): String {
        val signals = mutableListOf<String>()
        val windowPackages = mutableSetOf<String>()
        val eventPackageName = event.packageName?.toString().orEmpty()
        val eventBelongsToSystemPanel = isSystemPanelPackage(eventPackageName)
        if (eventBelongsToSystemPanel) {
            appendSignal(signals, event.className?.toString().orEmpty())
            appendSignal(signals, event.text.joinToString(" ") { it?.toString().orEmpty() })
            appendSignal(signals, event.contentDescription?.toString().orEmpty())
        }

        val eventSource = try {
            event.source
        } catch (e: Exception) {
            Log.e(TAG, "system_panel_event_source_failed: ${e.message}", e)
            null
        }
        try {
            if (eventBelongsToSystemPanel) {
                collectNodeSignals(
                    eventSource,
                    signals,
                    windowPackages,
                    maxTextLength = systemPanelSnapshotTextLimit,
                    visibleOnly = false
                )
            }
        } finally {
            eventSource?.recycle()
        }

        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "system_panel_root_failed: ${e.message}", e)
            null
        }
        try {
            val rootPackageName = root?.packageName?.toString().orEmpty()
            if (isSystemPanelPackage(rootPackageName)) {
                collectNodeSignals(
                    root,
                    signals,
                    windowPackages,
                    maxTextLength = systemPanelSnapshotTextLimit,
                    visibleOnly = false
                )
            }
        } finally {
            root?.recycle()
        }

        if (windowPackages.any { isSystemPanelPackage(it) } || eventBelongsToSystemPanel) {
            collectSystemPanelWindowNodeSignals(signals, windowPackages)
        }

        return signals.joinToString(" ")
    }

    private fun buildVisibleSystemPanelSignal(): String {
        val signals = mutableListOf<String>()
        val windowPackages = mutableSetOf<String>()

        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "visible_system_panel_root_failed: ${e.message}", e)
            null
        }
        try {
            val rootPackageName = root?.packageName?.toString().orEmpty()
            if (isSystemPanelPackage(rootPackageName)) {
                collectNodeSignals(
                    root,
                    signals,
                    windowPackages,
                    maxTextLength = systemPanelSnapshotTextLimit,
                    visibleOnly = false
                )
            }
        } finally {
            root?.recycle()
        }

        collectSystemPanelWindowNodeSignals(signals, windowPackages)
        return signals.joinToString(" ")
    }

    private fun handleProtectedSettingsPolicyIfCandidate(
        event: AccessibilityEvent?,
        packageName: String,
        source: String
    ): Boolean {
        if (isSystemPanelPackage(packageName)) {
            return false
        }
        val isCandidatePackage = protectedSettingsPolicy.isCandidatePackage(packageName)
        if (!isCandidatePackage && !shouldSweepProtectedWindows(event, packageName)) {
            return false
        }
        val windowPackages = if (isCandidatePackage) {
            setOf(packageName)
        } else {
            collectInteractiveWindowPackages()
        }
        if (!isCandidatePackage && windowPackages.none { protectedSettingsPolicy.isCandidatePackage(it) }) {
            return false
        }

        val snapshot = buildSettingsPageSnapshot(event, packageName, source, windowPackages)
        val decision = protectedSettingsPolicy.evaluate(snapshot)
        logProtectedSettingsDecision(snapshot, decision)

        return when (decision.type) {
            ProtectedSettingsDecisionType.ALLOW,
            ProtectedSettingsDecisionType.OBSERVE -> {
                releaseProtectedSettingsOverlayIfAllowed(snapshot, decision)
                true
            }
            ProtectedSettingsDecisionType.BLOCK_PAGE,
            ProtectedSettingsDecisionType.BLOCK_ACTION -> {
                val candidatePackage = protectedSettingsPolicy.findCandidatePackage(snapshot) ?: packageName
                suppressProtectedSystemSurface(candidatePackage, source, decision)
                true
            }
        }
    }

    private fun shouldSweepProtectedWindows(event: AccessibilityEvent?, packageName: String): Boolean {
        if (protectedSettingsPolicy.isCandidatePackage(packageName) ||
            WhitelistManager.isInstallerOrMarket(packageName)
        ) {
            return true
        }
        return when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
            else -> false
        }
    }

    private fun buildSettingsPageSnapshot(
        event: AccessibilityEvent?,
        packageName: String,
        source: String,
        knownWindowPackages: Set<String>
    ): SettingsPageSnapshot {
        val pageSignals = mutableListOf<String>()
        val clickedSignals = mutableListOf<String>()
        val windowPackages = linkedSetOf<String>()
        windowPackages.addAll(knownWindowPackages)
        val isExplicitUserAction = isExplicitUserActionEvent(event)
        val eventBelongsToSnapshot = event?.packageName?.toString().orEmpty().let { eventPackageName ->
            eventPackageName.isEmpty() || isSameBasePackage(eventPackageName, packageName)
        }

        event?.let {
            if (eventBelongsToSnapshot && isExplicitUserAction) {
                appendSignal(clickedSignals, it.text.joinToString(" ") { text -> text?.toString().orEmpty() })
                appendSignal(clickedSignals, it.contentDescription?.toString().orEmpty())
            }
            if (eventBelongsToSnapshot) {
                appendSignal(pageSignals, it.className?.toString().orEmpty())
                appendSignal(pageSignals, it.packageName?.toString().orEmpty())
            }
        }

        val eventSource = try {
            event?.source
        } catch (e: Exception) {
            Log.e(TAG, "settings_snapshot_event_source_failed: ${e.message}", e)
            null
        }
        try {
            if (eventBelongsToSnapshot && isExplicitUserAction) {
                collectNodeSignals(eventSource, clickedSignals, windowPackages)
            }
        } finally {
            eventSource?.recycle()
        }

        collectCandidateWindowNodeSignals(packageName, pageSignals, windowPackages)

        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "settings_snapshot_root_failed: ${e.message}", e)
            null
        }
        try {
            val rootPackageName = root?.packageName?.toString().orEmpty()
            appendSignal(windowPackages, rootPackageName)
            if (isSameBasePackage(rootPackageName, packageName)) {
                collectNodeSignals(root, pageSignals, windowPackages)
            }
        } finally {
            root?.recycle()
        }

        return SettingsPageSnapshot(
            packageName = packageName,
            source = source,
            eventType = event?.eventType ?: 0,
            className = event?.className?.toString().orEmpty(),
            text = pageSignals.joinToString(" ").take(settingsSnapshotTextLimit),
            clickedText = clickedSignals.joinToString(" ").take(settingsSnapshotTextLimit),
            windowPackages = windowPackages.filter { it.isNotEmpty() }.toSet()
        )
    }

    private fun isExplicitUserActionEvent(event: AccessibilityEvent?): Boolean {
        return event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
    }

    private fun collectNodeSignals(
        node: AccessibilityNodeInfo?,
        signals: MutableList<String>,
        windowPackages: MutableSet<String>,
        depth: Int = 0,
        maxTextLength: Int = settingsSnapshotTextLimit,
        visibleOnly: Boolean = true
    ) {
        if (node == null || depth > 40 || signals.joinToString(" ").length >= maxTextLength) {
            return
        }

        try {
            appendSignal(windowPackages, node.packageName?.toString().orEmpty())
            if (!visibleOnly || node.isVisibleToUser) {
                appendSignal(signals, node.text?.toString().orEmpty())
                appendSignal(signals, node.contentDescription?.toString().orEmpty())
                appendSignal(signals, node.viewIdResourceName.orEmpty())
            }

            for (index in 0 until node.childCount) {
                val child = try {
                    node.getChild(index)
                } catch (e: Exception) {
                    Log.e(TAG, "settings_snapshot_child_failed: ${e.message}", e)
                    null
                }
                try {
                    collectNodeSignals(child, signals, windowPackages, depth + 1, maxTextLength, visibleOnly)
                } finally {
                    child?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "settings_snapshot_node_failed: ${e.message}", e)
        }
    }

    private fun collectCandidateWindowNodeSignals(
        targetPackageName: String,
        signals: MutableList<String>,
        windowPackages: MutableSet<String>
    ) {
        val windowList = try {
            windows
        } catch (e: Exception) {
            Log.e(TAG, "settings_snapshot_windows_failed: ${e.message}", e)
            return
        }

        windowList?.forEach { window ->
            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(TAG, "settings_snapshot_window_root_failed: ${e.message}", e)
                null
            }
            try {
                val windowPackageName = root?.packageName?.toString().orEmpty()
                appendSignal(windowPackages, windowPackageName)
                if (isSameBasePackage(windowPackageName, targetPackageName)) {
                    collectNodeSignals(root, signals, windowPackages)
                }
            } finally {
                root?.recycle()
            }
        }
    }

    private fun isSameBasePackage(first: String, second: String): Boolean {
        val normalizedFirst = first.trim().substringBefore(':').lowercase()
        val normalizedSecond = second.trim().substringBefore(':').lowercase()
        return normalizedFirst.isNotEmpty() && normalizedFirst == normalizedSecond
    }

    private fun collectSystemPanelWindowNodeSignals(
        signals: MutableList<String>,
        windowPackages: MutableSet<String>
    ) {
        val windowList = try {
            windows
        } catch (e: Exception) {
            Log.e(TAG, "system_panel_windows_failed: ${e.message}", e)
            return
        }

        windowList?.forEach { window ->
            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(TAG, "system_panel_window_root_failed: ${e.message}", e)
                null
            }
            try {
                val windowPackageName = root?.packageName?.toString().orEmpty()
                appendSignal(windowPackages, windowPackageName)
                if (isSystemPanelPackage(windowPackageName)) {
                    collectNodeSignals(
                        root,
                        signals,
                        windowPackages,
                        maxTextLength = systemPanelSnapshotTextLimit,
                        visibleOnly = false
                    )
                }
            } finally {
                root?.recycle()
            }
        }
    }

    private fun appendSignal(signals: MutableCollection<String>, value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            signals.add(trimmed)
        }
    }

    private fun collectInteractiveWindowPackages(): Set<String> {
        val packages = linkedSetOf<String>()
        forEachInteractiveWindow { windowPackageName, _, _, _ ->
            if (windowPackageName.isNotEmpty()) {
                packages.add(windowPackageName)
            }
        }
        return packages
    }

    private fun logProtectedSettingsDecision(
        snapshot: SettingsPageSnapshot,
        decision: ProtectedSettingsDecision
    ) {
        if (decision.type == ProtectedSettingsDecisionType.ALLOW &&
            (decision.reason == "not_protected_settings_candidate" ||
                decision.reason == "transient_system_surface_without_disruptive_signal")
        ) {
            return
        }

        val candidatePackage = protectedSettingsPolicy.findCandidatePackage(snapshot) ?: snapshot.packageName
        val signature = listOf(
            decision.type.name,
            decision.reason,
            candidatePackage,
            decision.matchedTarget,
            decision.matchedRiskKeywords.joinToString(","),
            decision.matchedActionKeywords.joinToString(",")
        ).joinToString("|")
        val now = System.currentTimeMillis()
        if (signature == lastProtectedSettingsDecisionSignature &&
            (now - lastProtectedSettingsDecisionLogTime) < protectedSettingsDecisionLogCooldownMs
        ) {
            return
        }

        lastProtectedSettingsDecisionSignature = signature
        lastProtectedSettingsDecisionLogTime = now
        Log.w(
            TAG,
            "protected_settings_decision type=${decision.type} package=$candidatePackage " +
                "reason=${decision.reason} target=${decision.matchedTarget} " +
                "risk=${decision.matchedRiskKeywords.joinToString(",")} " +
                "action=${decision.matchedActionKeywords.joinToString(",")} source=${snapshot.source} " +
                "clicked=${snapshot.clickedText.take(160)} sample=${snapshot.text.take(240)}"
        )
    }

    private fun releaseProtectedSettingsOverlayIfAllowed(
        snapshot: SettingsPageSnapshot,
        decision: ProtectedSettingsDecision
    ) {
        if (!OverlayService.isOverlayShowing()) {
            return
        }
        val blockedPackage = OverlayService.getCurrentBlockedPackage()
        if (!protectedSettingsPolicy.isCandidatePackage(blockedPackage)) {
            return
        }

        Log.d(
            TAG,
            "protected_settings_allow_release_overlay package=$blockedPackage " +
                "reason=${decision.reason} source=${snapshot.source}"
        )
        cancelPendingBlockActions("protected_settings_allowed:${decision.reason}")
        hideOverlay()
        lastBlockedPackage = ""
        lastOverlayPackage = ""
        blockHoldUntil = 0L
    }

    private fun sweepProtectedInteractiveWindows(source: String) {
        if (exitVisiblePowerSaveModeIfNeeded(source)) {
            return
        }

        if (collapseVisibleSystemPanelIfNeeded(source)) {
            return
        }

        val packageName = findProtectedInteractiveWindowPackage(source) ?: return

        val now = System.currentTimeMillis()
        if (packageName == lastProtectedWindowSweepPackage &&
            (now - lastProtectedWindowSweepTime) < protectedWindowSweepCooldownMs
        ) {
            return
        }
        lastProtectedWindowSweepPackage = packageName
        lastProtectedWindowSweepTime = now

        Log.w(TAG, "protected_window_sweep_detected source=$source package=$packageName")
        suppressProtectedSystemSurface(packageName, source)
    }

    private fun suppressProtectedSystemSurface(
        packageName: String,
        source: String,
        decision: ProtectedSettingsDecision? = null
    ) {
        if (WhitelistManager.isSelfApp(packageName)) {
            return
        }
        if (isProtectedSurfaceSuppressionAllowed()) {
            Log.d(TAG, "protected_surface_skip_allowed source=$source package=$packageName")
            return
        }

        val now = System.currentTimeMillis()
        if (packageName == lastProtectedSurfaceSuppressPackage &&
            (now - lastProtectedSurfaceSuppressTime) < protectedSurfaceSuppressCooldownMs
        ) {
            return
        }

        lastProtectedSurfaceSuppressPackage = packageName
        lastProtectedSurfaceSuppressTime = now
        lastBlockedPackage = packageName
        lastBlockTime = now
        blockHoldUntil = now + blockHoldDuration
        pendingBlockPackage = packageName
        publishLifecycleSignal("protected_fast_suppress:$packageName")
        Log.w(
            TAG,
            "protected_surface_fast_suppress source=$source package=$packageName " +
                "decision=${decision?.type ?: "legacy"} reason=${decision?.reason.orEmpty()}"
        )

        handler.post {
            try {
                OverlayService.showOverlay(this, packageName, packageName)
                lastOverlayPackage = packageName
                lastOverlayShowTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "protected_surface_overlay_failed: ${e.message}", e)
            }
        }

        protectedSurfaceNavigationBurstDelays.forEach { delayMs ->
            if (delayMs == 0L) {
                performProtectedSurfaceNavigation(packageName, source, delayMs)
            } else {
                handler.postDelayed({
                    performProtectedSurfaceNavigation(packageName, source, delayMs)
                }, delayMs)
            }
        }
        scheduleOverlayReleaseCheck(packageName)
    }

    private fun isProtectedSurfaceSuppressionAllowed(): Boolean {
        return try {
            val settingsManager = SettingsManager.getInstance(this)
            settingsManager.isGlobalUnlockEnabled() || settingsManager.isSetupSettingsAccessAllowed()
        } catch (e: Exception) {
            Log.e(TAG, "read_protected_surface_allow_state_failed: ${e.message}", e)
            false
        }
    }

    private fun performProtectedSurfaceNavigation(packageName: String, source: String, delayMs: Long) {
        try {
            val action = when (delayMs) {
                60L, 280L, 1500L -> GLOBAL_ACTION_BACK
                else -> GLOBAL_ACTION_HOME
            }
            val actionName = if (action == GLOBAL_ACTION_BACK) "BACK" else "HOME"
            val handled = performGlobalAction(action)
            Log.w(
                TAG,
                "protected_surface_nav source=$source package=$packageName delayMs=$delayMs action=$actionName handled=$handled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "protected_surface_nav_failed: ${e.message}", e)
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
            val action = if (requireStrongExit) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK
            performGlobalAction(action)
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
        scheduleDeferredBlockAction(packageName, 650L, "fallback_nav_650") {
            tryFallbackNavigation(packageName)
        }
        scheduleDeferredBlockAction(packageName, 1200L, "fallback_nav_1200") {
            tryFallbackNavigation(packageName)
        }

        if (isHuaweiFamilyDevice) {
            scheduleDeferredBlockAction(packageName, 420L, "fallback_nav_huawei_420") {
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
        return protectedSettingsPolicy.isCandidatePackage(packageName) ||
            WhitelistManager.isInstallerOrMarket(packageName)
    }

    private fun findProtectedInteractiveWindowPackage(source: String): String? {
        val windowSnapshots = mutableListOf<String>()
        val candidatePackages = linkedSetOf<String>()
        val windowPackages = linkedSetOf<String>()

        forEachInteractiveWindow { packageName, summary, isActive, isFocused ->
            windowSnapshots.add(summary)
            appendSignal(windowPackages, packageName)
            if (isSystemPanelPackage(packageName)) {
                return@forEachInteractiveWindow
            }
            if (packageName.isNotEmpty() &&
                isProtectedSystemSurface(packageName) &&
                (isActive || isFocused)
            ) {
                candidatePackages.add(packageName)
            }
        }

        candidatePackages.forEach { candidatePackage ->
            if (WhitelistManager.isInstallerOrMarket(candidatePackage)) {
                logProtectedWindowSnapshot(source, candidatePackage, windowSnapshots)
                return candidatePackage
            }
            val snapshot = buildSettingsPageSnapshot(
                event = null,
                packageName = candidatePackage,
                source = source,
                knownWindowPackages = windowPackages
            )
            val decision = protectedSettingsPolicy.evaluate(snapshot)
            logProtectedSettingsDecision(snapshot, decision)
            if (decision.type == ProtectedSettingsDecisionType.BLOCK_PAGE ||
                decision.type == ProtectedSettingsDecisionType.BLOCK_ACTION
            ) {
                logProtectedWindowSnapshot(source, candidatePackage, windowSnapshots)
                return candidatePackage
            }
        }

        return null
    }

    private fun isPackageVisibleInInteractiveWindows(packageName: String): Boolean {
        var found = false
        forEachInteractiveWindow { windowPackageName, _, _, _ ->
            if (windowPackageName == packageName) {
                found = true
            }
        }
        return found
    }

    private fun forEachInteractiveWindow(consumer: (packageName: String, summary: String, isActive: Boolean, isFocused: Boolean) -> Unit) {
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
            consumer(packageName, summary, window.isActive, window.isFocused)
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
        if (isGlobalUnlockEnabledForSensitiveAction()) {
            Log.d(TAG, "sensitive_action_skip_global_unlock package=$packageName")
            return false
        }
        val isLauncherSource = WhitelistManager.isLauncher(packageName)
        val sensitiveSource = isLauncherSource ||
            WhitelistManager.isSettings(packageName) ||
            WhitelistManager.isInstallerOrMarket(packageName)
        if (!sensitiveSource) {
            return false
        }
        val textSignal = buildEventSignal(event)
        if (isLauncherSource) {
            return shouldBlockLauncherSensitiveAction(event, packageName, textSignal)
        }

        val signalMatch = destructiveActionKeywords.any { textSignal.contains(it, ignoreCase = true) }
        val nodeMatch = containsSensitiveActionNodeText(
            event = event,
            keywords = destructiveActionKeywords,
            includeActiveRoot = true
        )
        if (!signalMatch && !nodeMatch) {
            return false
        }
        val targetAppMatch = targetAppKeywords.any { textSignal.contains(it, ignoreCase = true) } ||
            containsSensitiveActionNodeText(
                event = event,
                keywords = targetAppKeywords,
                includeActiveRoot = true
            )
        Log.w(
            TAG,
            "sensitive_action_detected package=$packageName signalMatch=$signalMatch " +
                "nodeMatch=$nodeMatch targetAppMatch=$targetAppMatch launcher=false"
        )
        return targetAppMatch
    }

    private fun shouldBlockLauncherSensitiveAction(
        event: AccessibilityEvent,
        packageName: String,
        textSignal: String
    ): Boolean {
        if (!isXiaomiFamilyDevice || packageName != "com.miui.home") {
            return false
        }
        val isLongClickEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        val isMiuiTargetIconProbeEvent =
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                eventSourceSelfContainsKeyword(
                    event = event,
                    keywords = targetAppKeywords
                ) &&
                eventSourceLooksLikeLauncherIcon(event)
        val eventTargetMatch = targetAppKeywords.any {
            textSignal.contains(it, ignoreCase = true)
        } || eventSourceSelfContainsKeyword(
            event = event,
            keywords = targetAppKeywords
        )
        if ((isLongClickEvent && eventTargetMatch) || isMiuiTargetIconProbeEvent) {
            miuiLauncherIconMenuBlockUntil = System.currentTimeMillis() + 300L
            Log.w(
                TAG,
                "launcher_uninstall_block_on_target_icon package=$packageName eventType=${event.eventType} " +
                    "longClick=$isLongClickEvent iconProbe=$isMiuiTargetIconProbeEvent signal=${textSignal.take(120)}"
            )
            return true
        }
        if (isLauncherUninstallConfirmEvent(textSignal)) {
            Log.w(
                TAG,
                "launcher_uninstall_confirm_detected package=$packageName eventType=${event.eventType} signal=${textSignal.take(120)}"
            )
            return true
        }
        return false
    }

    private fun eventSourceLooksLikeLauncherIcon(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        return try {
            val className = source.className?.toString().orEmpty()
            className == "android.widget.TextView" &&
                source.isClickable &&
                source.isLongClickable &&
                source.isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "launcher_icon_source_check_failed: ${e.message}", e)
            false
        } finally {
            source.recycle()
        }
    }

    private fun isLauncherShortcutMenuEvent(textSignal: String): Boolean {
        return textSignal.contains("弹出式窗口") ||
            textSignal.contains("popup", ignoreCase = true) ||
            textSignal.contains("shortcut", ignoreCase = true)
    }

    private fun isLauncherUninstallConfirmEvent(textSignal: String): Boolean {
        return textSignal.contains("DeleteDialog", ignoreCase = true) ||
            textSignal.contains("卸载“拉钩守护”") ||
            textSignal.contains("卸载\"拉钩守护\"") ||
            (
                textSignal.contains("卸载后") &&
                    textSignal.contains("取消") &&
                    textSignal.contains("卸载")
                )
    }

    private fun isDialogLikeEvent(textSignal: String): Boolean {
        return textSignal.contains("Dialog", ignoreCase = true) ||
            textSignal.contains("弹窗", ignoreCase = true) ||
            textSignal.contains("对话框", ignoreCase = true) ||
            textSignal.contains("弹出式窗口")
    }

    private fun isGlobalUnlockEnabledForSensitiveAction(): Boolean {
        return try {
            SettingsManager.getInstance(this).isGlobalUnlockEnabled()
        } catch (e: Exception) {
            Log.e(TAG, "read_sensitive_action_unlock_state_failed: ${e.message}", e)
            false
        }
    }

    private fun isGlobalProtectedSurfaceUnlockAllowed(): Boolean {
        return try {
            SettingsManager.getInstance(this).isGlobalUnlockEnabled()
        } catch (e: Exception) {
            Log.e(TAG, "read_global_unlock_state_failed: ${e.message}", e)
            false
        }
    }

    private fun eventSourceSelfContainsKeyword(
        event: AccessibilityEvent,
        keywords: Set<String>
    ): Boolean {
        val source = event.source ?: return false
        return try {
            val nodeSignal = listOf(
                source.text?.toString().orEmpty(),
                source.contentDescription?.toString().orEmpty(),
                source.className?.toString().orEmpty(),
                source.viewIdResourceName.orEmpty()
            ).joinToString("|")
            keywords.any { nodeSignal.contains(it, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "sensitive_source_self_check_failed: ${e.message}", e)
            false
        } finally {
            source.recycle()
        }
    }

    private fun containsSensitiveActionNodeText(
        event: AccessibilityEvent,
        keywords: Set<String>,
        includeActiveRoot: Boolean
    ): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seen = mutableSetOf<Int>()

        fun addRoot(node: AccessibilityNodeInfo?) {
            if (node == null) {
                return
            }
            if (seen.add(System.identityHashCode(node))) {
                roots.add(node)
            } else {
                node.recycle()
            }
        }

        addRoot(event.source)
        if (includeActiveRoot) {
            addRoot(rootInActiveWindow)
        }

        try {
            roots.forEach { root ->
                keywords.forEach { keyword ->
                    val nodes = try {
                        root.findAccessibilityNodeInfosByText(keyword)
                    } catch (e: Exception) {
                        Log.e(TAG, "sensitive_node_search_failed keyword=$keyword error=${e.message}", e)
                        emptyList()
                    }
                    val matched = nodes.isNotEmpty()
                    nodes.forEach { it.recycle() }
                    if (matched) {
                        Log.w(TAG, "sensitive_action_node_match keyword=$keyword")
                        return true
                    }
                }
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return false
    }

    private fun buildEventSignal(event: AccessibilityEvent): String {
        val eventText = event.text.joinToString("|") { it?.toString().orEmpty() }
        val contentDescription = event.contentDescription?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        return listOf(eventText, contentDescription, className).joinToString("|")
    }

    private fun blockSensitiveAction(packageName: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        val signal = buildEventSignal(event).take(200)
        val isConfirmDialog = isLauncherUninstallConfirmEvent(signal)
        val cooldownMs = if (isConfirmDialog) sensitiveDialogActionCooldownMs else sensitiveActionCooldownMs
        val elapsedMs = now - lastSensitiveActionBlockTime
        if (elapsedMs < cooldownMs) {
            Log.d(
                TAG,
                "sensitive_action_skip_cooldown elapsedMs=$elapsedMs cooldownMs=$cooldownMs " +
                    "confirm=$isConfirmDialog signal=$signal"
            )
            return
        }
        lastSensitiveActionBlockTime = now
        Log.w(TAG, "sensitive_action_block package=$packageName confirm=$isConfirmDialog signal=$signal")
        cancelSensitiveEscapeActions()
        if (isConfirmDialog && packageName == "com.miui.home") {
            tryClickSensitiveCancel(event)
            runSensitiveActionFastBackBurst()
        } else if (isConfirmDialog) {
            tryClickSensitiveCancel(event)
            runSensitiveActionEscapeBurst()
        } else if (packageName == "com.miui.home" && now <= miuiLauncherIconMenuBlockUntil) {
            runSensitiveActionEscapeBurst()
        } else {
            runSensitiveActionEscapeBurst()
        }
    }

    private fun runSensitiveActionFastBackBurst() {
        val actions = listOf(
            0L to GLOBAL_ACTION_BACK,
            12L to GLOBAL_ACTION_BACK,
            30L to GLOBAL_ACTION_BACK,
            60L to GLOBAL_ACTION_BACK
        )
        actions.forEach { (delayMs, action) ->
            if (delayMs == 0L) {
                performSensitiveEscapeAction(action, delayMs)
                return@forEach
            }
            lateinit var runnable: Runnable
            runnable = Runnable {
                sensitiveEscapeActions.remove(runnable)
                performSensitiveEscapeAction(action, delayMs)
            }
            sensitiveEscapeActions.add(runnable)
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun runSensitiveActionEscapeBurst() {
        val actions = listOf(
            0L to GLOBAL_ACTION_BACK,
            16L to GLOBAL_ACTION_BACK,
            45L to GLOBAL_ACTION_HOME,
            85L to GLOBAL_ACTION_BACK,
            140L to GLOBAL_ACTION_HOME,
            240L to GLOBAL_ACTION_BACK
        )
        actions.forEach { (delayMs, action) ->
            if (delayMs == 0L) {
                performSensitiveEscapeAction(action, delayMs)
                return@forEach
            }
            lateinit var runnable: Runnable
            runnable = Runnable {
                sensitiveEscapeActions.remove(runnable)
                performSensitiveEscapeAction(action, delayMs)
            }
            sensitiveEscapeActions.add(runnable)
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun cancelSensitiveEscapeActions() {
        sensitiveEscapeActions.forEach { handler.removeCallbacks(it) }
        sensitiveEscapeActions.clear()
    }

    private fun performSensitiveEscapeAction(action: Int, delayMs: Long) {
        try {
            val handled = performGlobalAction(action)
            Log.w(TAG, "sensitive_action_escape action=$action delay=$delayMs handled=$handled")
        } catch (e: Exception) {
            Log.e(TAG, "sensitive_action_escape_failed action=$action delay=$delayMs: ${e.message}", e)
        }
    }

    private fun tryClickSensitiveCancel(event: AccessibilityEvent): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seen = mutableSetOf<Int>()

        fun addRoot(node: AccessibilityNodeInfo?) {
            if (node == null) {
                return
            }
            if (seen.add(System.identityHashCode(node))) {
                roots.add(node)
            } else {
                node.recycle()
            }
        }

        addRoot(event.source)
        addRoot(rootInActiveWindow)

        try {
            roots.forEach { root ->
                sensitiveCancelKeywords.forEach { keyword ->
                    val nodes = try {
                        root.findAccessibilityNodeInfosByText(keyword)
                    } catch (e: Exception) {
                        Log.e(TAG, "sensitive_cancel_search_failed keyword=$keyword error=${e.message}", e)
                        emptyList()
                    }
                    try {
                        nodes.forEach { node ->
                            val clickTarget = findClickableAncestor(node)
                            if (clickTarget != null && clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                Log.w(TAG, "sensitive_action_cancel_clicked keyword=$keyword")
                                if (clickTarget !== node) {
                                    clickTarget.recycle()
                                }
                                return true
                            }
                            if (clickTarget != null && clickTarget !== node) {
                                clickTarget.recycle()
                            }
                        }
                    } finally {
                        nodes.forEach { it.recycle() }
                    }
                }
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isClickable && current.isEnabled) {
                return current
            }
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        current?.recycle()
        return null
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
        if (isProtectedSystemSurface(packageName)) {
            scheduleProtectedOverlayReleaseCheck(packageName)
            return
        }
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

    private fun scheduleProtectedOverlayReleaseCheck(packageName: String) {
        val releaseCheckDelays = longArrayOf(900L, 1600L, 2600L, 4200L)
        releaseCheckDelays.forEach { delayMillis ->
            handler.postDelayed({
                if (!OverlayService.isOverlayShowing()) {
                    return@postDelayed
                }
                if (OverlayService.getCurrentBlockedPackage() != packageName) {
                    return@postDelayed
                }
                val targetStillActive = isTargetPackageActive(packageName)
                if (!targetStillActive || delayMillis >= 2600L) {
                    if (targetStillActive) {
                        Log.w(TAG, "protected_overlay_force_release package=$packageName delayMs=$delayMillis")
                        performProtectedSurfaceNavigation(packageName, "protected_overlay_release", delayMillis)
                    } else {
                        Log.d(TAG, "protected_overlay_auto_release package=$packageName delayMs=$delayMillis")
                    }
                    hideOverlay()
                    lastBlockedPackage = ""
                    lastOverlayPackage = ""
                    pendingBlockPackage = ""
                }
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
