package com.kidsphoneguard.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsPolicy
import com.kidsphoneguard.service.accessibility.AccessibilityEventRouter
import com.kidsphoneguard.service.accessibility.AssistantOverlayRoutingSupport
import com.kidsphoneguard.service.accessibility.EventRoutingState
import com.kidsphoneguard.service.accessibility.SelfAppEventHandler
import com.kidsphoneguard.service.accessibility.ServiceRuntimeSupport
import com.kidsphoneguard.service.accessibility.WindowInspectorSnapshotApi
import com.kidsphoneguard.service.block.AppBlockCoordinator
import com.kidsphoneguard.service.block.BlockSessionController
import com.kidsphoneguard.service.block.BlockSessionState
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.LockDecisionEngineProvider
import com.kidsphoneguard.service.block.NavigationExecutor
import com.kidsphoneguard.service.block.OverlayCoordinator
import com.kidsphoneguard.service.guard.ProtectedSurfaceGuard
import com.kidsphoneguard.service.guard.ProtectedSurfaceState
import com.kidsphoneguard.service.guard.SystemSurfaceGuard
import com.kidsphoneguard.service.guard.UninstallGuard
import com.kidsphoneguard.service.guard.WeChatForegroundActivity
import com.kidsphoneguard.service.guard.WeChatFinderGuard
import com.kidsphoneguard.service.guard.oem.HuaweiPowerSaveHandler
import com.kidsphoneguard.utils.BroadcastPermissionHelper
import com.kidsphoneguard.utils.SettingsManager
import com.kidsphoneguard.utils.WhitelistManager
import com.kidsphoneguard.utils.SystemSurfaceClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GuardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GuardAccessibilityService"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SCHEDULER_OWNER_SERVICE_INIT = "service_init"
        private const val SCHEDULER_OWNER_HEARTBEAT = "heartbeat"
        private const val SCHEDULER_OWNER_PROTECTED_SWEEP = "protected_window_sweep"
        private const val SCHEDULER_OWNER_SYSTEM_PANEL = "system_panel"
        private const val SCHEDULER_OWNER_PROTECTED_SURFACE = "protected_surface"
        private const val SCHEDULER_OWNER_ASSISTANT_FOLLOW_UP = "assistant_follow_up"
        private const val SCHEDULER_OWNER_PENDING_BLOCK = "pending_block"
        private const val SCHEDULER_OWNER_OVERLAY_RELEASE = "overlay_release"
        private const val SCHEDULER_OWNER_UNINSTALL_GUARD = "uninstall_guard"
        private const val SCHEDULER_OWNER_UNINSTALL_RELEASE = "uninstall_overlay_release"

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
    private val navigationExecutor by lazy { NavigationExecutor(this, TAG) }
    private val guardActionScheduler by lazy { GuardActionScheduler(handler) }
    private val windowInspectorSnapshotApi by lazy {
        WindowInspectorSnapshotApi(this, usageStatsManager, TAG)
    }
    private val eventRoutingState = EventRoutingState()
    private val protectedSurfaceState = ProtectedSurfaceState()
    private val uninstallGuardState = ProtectedSurfaceState()
    private val accessibilityEventRouter by lazy {
        AccessibilityEventRouter(
            logTag = TAG,
            adapters = createAccessibilityEventRouterAdapters(),
            state = eventRoutingState
        )
    }
    private val overlayCoordinator by lazy { OverlayCoordinator(this, TAG) }
    private val blockSessionController by lazy {
        BlockSessionController(
            overlayCoordinator = overlayCoordinator,
            scheduler = guardActionScheduler,
            state = BlockSessionState()
        )
    }

    private lateinit var activityManager: ActivityManager
    private lateinit var usageStatsManager: UsageStatsManager
    private val protectedSettingsPolicy by lazy {
        ProtectedSettingsPolicy(SettingsManager.getInstance(this))
    }
    private val systemSurfaceGuard by lazy {
        SystemSurfaceGuard(
            logTag = TAG,
            protectedSettingsPolicy = protectedSettingsPolicy,
            navigationExecutor = navigationExecutor,
            guardActionScheduler = guardActionScheduler,
            readRootInActiveWindow = { rootInActiveWindow },
            readWindows = { windows },
            isGlobalProtectedSurfaceUnlockAllowed = ::isGlobalProtectedSurfaceUnlockAllowed,
            systemPanelPackages = systemPanelPackages,
            systemPanelSnapshotTextLimit = systemPanelSnapshotTextLimit,
            systemPanelCollapseCooldownMs = systemPanelCollapseCooldownMs,
            systemPanelCollapseReinforceDelayMs = systemPanelCollapseReinforceDelayMs,
            schedulerOwnerSystemPanel = SCHEDULER_OWNER_SYSTEM_PANEL,
            dismissNotificationShadeAction = GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE,
            backAction = GLOBAL_ACTION_BACK
        )
    }
    private val protectedSurfaceGuard: ProtectedSurfaceGuard by lazy {
        ProtectedSurfaceGuard(
            logTag = TAG,
            protectedSettingsPolicy = protectedSettingsPolicy,
            state = protectedSurfaceState,
            windowInspectorSnapshotApi = windowInspectorSnapshotApi,
            navigationExecutor = navigationExecutor,
            guardActionScheduler = guardActionScheduler,
            blockSessionController = blockSessionController,
            readRootInActiveWindow = { rootInActiveWindow },
            readWindows = { windows },
            postToMain = { action -> handler.post(action) },
            publishLifecycleSignal = ::publishLifecycleSignal,
            cancelPendingBlockActions = { reason -> appBlockCoordinator.cancelPendingBlockActions(reason) },
            hideOverlay = { appBlockCoordinator.hideOverlay() },
            isOverlayShowing = { overlayCoordinator.isShowing() },
            readCurrentBlockedPackage = { overlayCoordinator.currentBlockedPackage() },
            isTargetPackageActive = { packageName -> appBlockCoordinator.isTargetPackageActive(packageName) },
            isGlobalUnlockEnabled = { SettingsManager.getInstance(this).isGlobalUnlockEnabled() },
            isSetupSettingsAccessAllowed = { SettingsManager.getInstance(this).isSetupSettingsAccessAllowed() },
            exitVisiblePowerSaveModeIfNeeded = huaweiPowerSaveHandler::exitVisiblePowerSaveModeIfNeeded,
            collapseVisibleSystemPanelIfNeeded = systemSurfaceGuard::collapseVisibleSystemPanelIfNeeded,
            isSystemPanelPackage = systemSurfaceGuard::isSystemPanelPackage,
            isUninstallGuardOwnedSurface = { packageName ->
                uninstallGuard.isOwnedSurface(packageName)
            },
            runHuaweiSpecificWindowGuards =
                OemRuntimePolicy.shouldRunHuaweiSpecificWindowGuards(isXiaomiFamilyDevice),
            settingsSnapshotTextLimit = settingsSnapshotTextLimit,
            protectedWindowLogCooldownMs = protectedWindowLogCooldownMs,
            protectedSettingsDecisionLogCooldownMs = protectedSettingsDecisionLogCooldownMs,
            protectedWindowSweepCooldownMs = protectedWindowSweepCooldownMs,
            protectedSurfaceSuppressCooldownMs = protectedSurfaceSuppressCooldownMs,
            protectedSurfaceNavigationBurstDelays = protectedSurfaceNavigationBurstDelays,
            blockHoldDuration = blockHoldDuration,
            schedulerOwnerProtectedSurface = SCHEDULER_OWNER_PROTECTED_SURFACE,
            schedulerOwnerOverlayRelease = SCHEDULER_OWNER_OVERLAY_RELEASE,
            backAction = GLOBAL_ACTION_BACK,
            homeAction = GLOBAL_ACTION_HOME
        )
    }
    private val uninstallGuard: UninstallGuard by lazy {
        UninstallGuard(
            logTag = TAG,
            state = uninstallGuardState,
            windowInspectorSnapshotApi = windowInspectorSnapshotApi,
            navigationExecutor = navigationExecutor,
            guardActionScheduler = guardActionScheduler,
            blockSessionController = blockSessionController,
            readRootInActiveWindow = { rootInActiveWindow },
            readWindows = { windows },
            postToMain = { action -> handler.post(action) },
            publishLifecycleSignal = ::publishLifecycleSignal,
            hideOverlay = { appBlockCoordinator.hideOverlay() },
            isOverlayShowing = { overlayCoordinator.isShowing() },
            readCurrentBlockedPackage = { overlayCoordinator.currentBlockedPackage() },
            schedulerOwnerUninstallRelease = SCHEDULER_OWNER_UNINSTALL_RELEASE,
            isGlobalUnlockEnabled = { SettingsManager.getInstance(this).isGlobalUnlockEnabled() },
            isXiaomiFamilyDevice = isXiaomiFamilyDevice,
            snapshotTextLimit = settingsSnapshotTextLimit,
            suppressCooldownMs = protectedSurfaceSuppressCooldownMs,
            sweepCooldownMs = uninstallSweepCooldownMs,
            navigationBurstDelays = protectedSurfaceNavigationBurstDelays,
            blockHoldDuration = blockHoldDuration,
            schedulerOwnerUninstallGuard = SCHEDULER_OWNER_UNINSTALL_GUARD,
            backAction = GLOBAL_ACTION_BACK,
            homeAction = GLOBAL_ACTION_HOME
        )
    }
    private val weChatFinderGuard by lazy {
        WeChatFinderGuard(
            logTag = TAG,
            guardActionScheduler = guardActionScheduler,
            navigationExecutor = navigationExecutor,
            isWeChatFinderBlockEnabled = { SettingsManager.getInstance(this).isWeChatFinderBlockEnabled() },
            isGlobalUnlockEnabled = { SettingsManager.getInstance(this).isGlobalUnlockEnabled() },
            readRecentForegroundActivity = {
                windowInspectorSnapshotApi.recentForegroundActivity()?.let {
                    WeChatForegroundActivity(it.packageName, it.className)
                }
            },
            readActivePackageName = windowInspectorSnapshotApi::activePackageName,
            publishLifecycleSignal = ::publishLifecycleSignal,
            backAction = GLOBAL_ACTION_BACK
        )
    }
    private val huaweiPowerSaveHandler by lazy {
        HuaweiPowerSaveHandler(
            logTag = TAG,
            navigationExecutor = navigationExecutor,
            guardActionScheduler = guardActionScheduler,
            readRootInActiveWindow = { rootInActiveWindow },
            readWindows = { windows },
            isGlobalProtectedSurfaceUnlockAllowed = ::isGlobalProtectedSurfaceUnlockAllowed,
            displayMetricsProvider = {
                HuaweiPowerSaveHandler.DisplayMetricsSnapshot(
                    widthPixels = resources.displayMetrics.widthPixels,
                    heightPixels = resources.displayMetrics.heightPixels
                )
            },
            snapshotTextLimit = systemPanelSnapshotTextLimit
        )
    }
    private val lockDecisionEngineProvider by lazy {
        LockDecisionEngineProvider(
            logTag = TAG,
            context = this
        )
    }
    private val appBlockCoordinator: AppBlockCoordinator by lazy {
        AppBlockCoordinator(
            dependencies = AppBlockCoordinator.Dependencies(
                logTag = TAG,
                appScope = serviceScope,
                blockSessionController = blockSessionController,
                guardActionScheduler = guardActionScheduler,
                navigationExecutor = navigationExecutor,
                windowInspectorSnapshotApi = windowInspectorSnapshotApi,
                activityManager = activityManager,
                lockDecisionEngineProvider = lockDecisionEngineProvider
            ),
            callbacks = AppBlockCoordinator.Callbacks(
                postToMain = { action -> handler.post(action) },
                readOverlayShowing = { overlayCoordinator.isShowing() },
                readCurrentBlockedPackage = { overlayCoordinator.currentBlockedPackage() },
                protectedSurfaceCallbacks = AppBlockCoordinator.ProtectedSurfaceCallbacks(
                    isProtectedSystemSurface = { packageName ->
                        protectedSurfaceGuard.isProtectedSystemSurface(packageName)
                    },
                    scheduleProtectedReleaseCheck = { packageName ->
                        protectedSurfaceGuard.scheduleProtectedOverlayReleaseCheck(packageName)
                    }
                ),
                isSelfAppPackage = WhitelistManager::isSelfApp,
                isInWhitelist = WhitelistManager::isInWhitelist,
                isSettingsPackage = SystemSurfaceClassifier::isSettingsSurface,
                isInstallerOrMarketPackage = SystemSurfaceClassifier::isInstallerOrMarketSurface
            ),
            config = AppBlockCoordinator.Config(
                isHuaweiFamilyDevice = isHuaweiFamilyDevice,
                systemUiPackage = SYSTEM_UI_PACKAGE,
                systemUiReleaseDelayMs = systemUiReleaseDelay,
                blockCooldownMs = blockCooldown,
                blockHoldDurationMs = blockHoldDuration,
                overlayReshowCooldownMs = overlayReshowCooldown,
                overlayStabilityWindowMs = overlayStabilityWindow,
                forceStopDelaysMs = longArrayOf(120L, 360L, 700L),
                fallbackNavigationDelaysMs = longArrayOf(650L, 1200L),
                huaweiFallbackDelayMs = 420L,
                schedulerOwnerPendingBlock = SCHEDULER_OWNER_PENDING_BLOCK,
                schedulerOwnerOverlayRelease = SCHEDULER_OWNER_OVERLAY_RELEASE,
                backAction = GLOBAL_ACTION_BACK,
                homeAction = GLOBAL_ACTION_HOME
            )
        )
    }
    private val assistantOverlayRoutingSupport by lazy {
        AssistantOverlayRoutingSupport(
            logTag = TAG,
            debounceIntervalMs = debounceInterval,
            scheduleFollowUpAction = { key, delayMs, action ->
                guardActionScheduler.schedule(
                    owner = SCHEDULER_OWNER_ASSISTANT_FOLLOW_UP,
                    key = key,
                    delayMs = delayMs,
                    action = action
                )
            },
            readActivePackageName = windowInspectorSnapshotApi::activePackageName,
            getRecentTopPackageName = appBlockCoordinator::getRecentTopPackageName,
            isSelfApp = WhitelistManager::isSelfApp,
            isInWhitelist = WhitelistManager::isInWhitelist,
            launchPolicyCheck = { packageName ->
                serviceScope.launch {
                    try {
                        appBlockCoordinator.checkPolicyAndExecute(packageName)
                    } catch (e: Exception) {
                        Log.e(TAG, "补偿检测策略时出错: ${e.message}", e)
                    }
                }
            }
        )
    }
    private val selfAppEventHandler by lazy {
        SelfAppEventHandler(
            logTag = TAG,
            isSelfApp = WhitelistManager::isSelfApp,
            isOverlayShowing = { overlayCoordinator.isShowing() },
            readCurrentBlockedPackage = { overlayCoordinator.currentBlockedPackage() },
            pendingBlockPackage = blockSessionController::pendingBlockPackage,
            isProtectedSystemSurface = protectedSurfaceGuard::isProtectedSystemSurface,
            isTargetPackageActive = appBlockCoordinator::isTargetPackageActive,
            cancelPendingBlockActions = appBlockCoordinator::cancelPendingBlockActions,
            clearLastBlockedPackage = blockSessionController::clearLastBlockedPackage,
            hideOverlay = appBlockCoordinator::hideOverlay
        )
    }
    private val serviceRuntimeSupport by lazy {
        ServiceRuntimeSupport(
            logTag = TAG,
            protectedWindowSweepIntervalMs = protectedWindowSweepIntervalMs,
            schedulerOwnerHeartbeat = SCHEDULER_OWNER_HEARTBEAT,
            schedulerOwnerProtectedSweep = SCHEDULER_OWNER_PROTECTED_SWEEP,
            scheduleAction = { owner, key, delayMs, action ->
                guardActionScheduler.schedule(
                    owner = owner,
                    key = key,
                    delayMs = delayMs,
                    action = action
                )
            },
            readAccessibilitySettingsSnapshot = ::readAccessibilitySettingsSnapshot,
            touchHeartbeat = { GuardHealthState.touchAccessibilityHeartbeat(this) },
            clearHeartbeat = { GuardHealthState.clearAccessibilityHeartbeat(this) },
            setRunning = { running -> isRunning = running },
            publishLifecycleSignal = ::publishLifecycleSignal,
            sweepProtectedInteractiveWindows = { source ->
                uninstallGuard.sweepOwnedSurfaces(source)
                protectedSurfaceGuard.sweepProtectedInteractiveWindows(source)
            },
            handleBlockBroadcast = blockBroadcast@{ packageName ->
                if (isXiaomiFamilyDevice &&
                    !appBlockCoordinator.isTargetPackageActiveOrFocused(packageName)
                ) {
                    Log.d(TAG, "xiaomi_stale_usage_block_ignored package=$packageName")
                    return@blockBroadcast
                }
                serviceScope.launch {
                    try {
                        appBlockCoordinator.checkPolicyAndExecute(packageName)
                    } catch (e: Exception) {
                        Log.e(TAG, "拦截应用时出错: ${e.message}", e)
                    }
                }
            },
            registerBlockReceiver = { receiver ->
                BroadcastPermissionHelper.registerInternalBroadcastReceiver(
                    this,
                    receiver,
                    BroadcastPermissionHelper.ACTION_BLOCK_APP
                )
            },
            unregisterBlockReceiver = { receiver ->
                BroadcastPermissionHelper.unregisterReceiver(this, receiver)
            }
        )
    }
    private val debounceInterval = 500L

    private val blockCooldown = 5000L
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
        OemRuntimePolicy.isXiaomiFamily(deviceManufacturer, deviceBrand)
    private val protectedWindowLogCooldownMs = 1000L
    private val protectedSettingsDecisionLogCooldownMs = 1000L
    private val settingsSnapshotTextLimit = 3000
    private val systemPanelSnapshotTextLimit = 16000
    private val protectedWindowSweepIntervalMs =
        OemRuntimePolicy.protectedWindowSweepIntervalMs(isXiaomiFamilyDevice)
    private val protectedWindowSweepCooldownMs = 180L
    private val protectedSurfaceSuppressCooldownMs = 120L
    private val uninstallSweepCooldownMs = 1500L
    private val protectedSurfaceNavigationBurstDelays = longArrayOf(0L, 60L, 140L, 280L, 800L, 1500L, 3000L)
    private val systemPanelPackages = setOf(
        SYSTEM_UI_PACKAGE,
        "com.huawei.controlcenter"
    )
    private val systemPanelCollapseCooldownMs = 240L
    private val systemPanelCollapseReinforceDelayMs = 45L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRuntimeSupport.onServiceConnected()
    }

    override fun onCreate() {
        super.onCreate()
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        Log.d(
            TAG,
            "设备厂商: $deviceManufacturer, 品牌: $deviceBrand, 华为策略: $isHuaweiFamilyDevice, " +
                "小米策略: $isXiaomiFamilyDevice, windowSweepMs=$protectedWindowSweepIntervalMs"
        )
        serviceRuntimeSupport.onCreate()

        guardActionScheduler.schedule(
            owner = SCHEDULER_OWNER_SERVICE_INIT,
            key = "initialize",
            delayMs = 100L
        ) {
            try {
                initializeService()
            } catch (e: Exception) {
                Log.e(TAG, "Service初始化失败: ${e.message}", e)
            }
        }
    }

    private fun initializeService() {
        serviceScope.launch {
            lockDecisionEngineProvider.initialize()
        }
        serviceRuntimeSupport.registerBlockReceiverForServiceInit()
        Log.d(TAG, "Service created successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceRuntimeSupport.onDestroy()
        serviceScope.cancel()
        guardActionScheduler.cancelAll()
        appBlockCoordinator.cancelPendingBlockActions("service_onDestroy")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceRuntimeSupport.onUnbind(intent?.action.orEmpty())
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        serviceRuntimeSupport.onRebind(intent?.action.orEmpty())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        serviceRuntimeSupport.onAccessibilityEvent(event)
        try {
            if (protectedSurfaceGuard.closeAnySmallWindowForEvent(event)) {
                return
            }
            accessibilityEventRouter.route(event)
        } catch (e: Exception) {
            Log.e(TAG, "处理无障碍事件时出错: ${e.message}", e)
        }
    }

    /**
     * 创建 router 所需的 service 适配器集合。
     * 输入：无；输出：保留现有业务实现的 router 回调集合。
     */
    private fun createAccessibilityEventRouterAdapters(): AccessibilityEventRouter.Adapters {
        return AccessibilityEventRouter.Adapters(
            resolvePolicyPackage = assistantOverlayRoutingSupport::resolvePolicyPackage,
            shouldSweepProtectedWindows = protectedSurfaceGuard::shouldSweepProtectedWindows,
            findProtectedInteractiveWindowPackage = protectedSurfaceGuard::findProtectedInteractiveWindowPackage,
            isAssistantPackage = assistantOverlayRoutingSupport::isAssistantPackage,
            scheduleAssistantFollowUpChecks = assistantOverlayRoutingSupport::scheduleFollowUpChecks,
            exitPowerSaveModeIfNeeded = huaweiPowerSaveHandler::handle,
            collapseSystemPanelIfNeeded = systemSurfaceGuard::collapseSystemPanelIfNeeded,
            handleUninstallGuard = uninstallGuard::handleOwnedSurfaceEvent,
            handleProtectedSettingsPolicyIfCandidate = protectedSurfaceGuard::handleProtectedSettingsPolicyIfCandidate,
            handleSelfAppWindowEvent = selfAppEventHandler::handle,
            handleWeChatFinder = weChatFinderGuard::handle,
            ensureLockDecisionEngineInitialized = appBlockCoordinator::ensureLockDecisionEngineInitializedAsResult,
            isInstallerOrMarketPackage = SystemSurfaceClassifier::isInstallerOrMarketSurface,
            handleBlockHold = appBlockCoordinator::handleBlockHold,
            debounceIntervalMs = debounceInterval,
            handleWhitelistWindowEvent = appBlockCoordinator::handleWhitelistWindowEvent,
            launchNormalPolicyCheck = appBlockCoordinator::launchNormalPolicyCheck
        )
    }

    override fun onInterrupt() {
        serviceRuntimeSupport.onInterrupt()
    }

    private fun publishLifecycleSignal(signal: String) {
        latestLifecycleSignal = "${System.currentTimeMillis()}|$signal"
    }

    private fun readAccessibilitySettingsSnapshot(): ServiceRuntimeSupport.AccessibilitySettingsSnapshot {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.replace("\n", " ")?.take(240)
        return ServiceRuntimeSupport.AccessibilitySettingsSnapshot(
            accessibilityEnabled = enabled,
            enabledServices = enabledServices
        )
    }

    private fun isGlobalProtectedSurfaceUnlockAllowed(): Boolean {
        return try {
            SettingsManager.getInstance(this).isGlobalUnlockEnabled()
        } catch (e: Exception) {
            Log.e(TAG, "read_global_unlock_state_failed: ${e.message}", e)
            false
        }
    }

}
