package com.kidsphoneguard.service.block

import android.app.ActivityManager
import android.os.Build
import android.os.Process
import android.util.Log
import com.kidsphoneguard.engine.BlockReason
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.accessibility.WindowInspectorSnapshotApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 统一承接常规应用拦截编排。
 * 输入：锁决策、导航、调度、可见性读取与受保护页面桥接回调；输出：normal block 相关路由结果与执行副作用。
 */
class AppBlockCoordinator(
    private val logTag: String,
    private val appScope: CoroutineScope,
    private val blockSessionController: BlockSessionController,
    private val guardActionScheduler: GuardActionScheduler,
    private val navigationExecutor: NavigationExecutor,
    private val windowInspectorSnapshotApi: WindowInspectorSnapshotApi,
    private val activityManager: ActivityManager,
    private val lockDecisionEngineProvider: LockDecisionEngineProvider,
    private val postToMain: ((() -> Unit) -> Unit),
    private val readOverlayShowing: () -> Boolean,
    private val readCurrentBlockedPackage: () -> String,
    private val protectedSurfaceCallbacks: ProtectedSurfaceCallbacks,
    private val isSelfAppPackage: (String) -> Boolean,
    private val isInWhitelist: (String) -> Boolean,
    private val isSettingsPackage: (String) -> Boolean,
    private val isInstallerOrMarketPackage: (String) -> Boolean,
    private val isHuaweiFamilyDevice: Boolean,
    private val systemUiPackage: String,
    private val systemUiReleaseDelayMs: Long,
    private val blockCooldownMs: Long,
    private val blockHoldDurationMs: Long,
    private val overlayReshowCooldownMs: Long,
    private val overlayStabilityWindowMs: Long,
    private val forceStopDelaysMs: LongArray,
    private val fallbackNavigationDelaysMs: LongArray,
    private val huaweiFallbackDelayMs: Long,
    private val schedulerOwnerPendingBlock: String,
    private val schedulerOwnerOverlayRelease: String,
    private val backAction: Int,
    private val homeAction: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * 暴露给 normal block 的受保护页面最小桥接能力。
     * 输入：目标包名；输出：受保护页面判断与 release check 调度能力。
     */
    data class ProtectedSurfaceCallbacks(
        val isProtectedSystemSurface: (String) -> Boolean,
        val scheduleProtectedReleaseCheck: (String) -> Unit
    )

    private var forceStopPermissionDenied = false

    companion object {
        /**
         * 判断非 protected surface 的重复遮蔽层是否可直接跳过。
         * 输入：overlay 显示状态、当前遮蔽包名、目标包名、是否 protected；输出：是否可短路返回。
         */
        internal fun shouldSkipDuplicateOverlay(
            overlayShowing: Boolean,
            currentBlockedPackage: String,
            targetPackage: String,
            protectedSystemSurface: Boolean
        ): Boolean {
            return overlayShowing && currentBlockedPackage == targetPackage && !protectedSystemSurface
        }

        /**
         * 判断当前 normal 路径是否仍需安排普通 overlay release check。
         * 输入：是否 protected 与是否刚重新展示 overlay；输出：是否走 normal release check。
         */
        internal fun shouldScheduleNormalOverlayRelease(
            protectedSystemSurface: Boolean,
            shouldReshowOverlay: Boolean
        ): Boolean {
            return !(protectedSystemSurface && shouldReshowOverlay)
        }
    }

    /**
     * 将锁决策引擎可用性检查适配为统一路由结果。
     * 输入：无；输出：可用时继续，不可用时消费当前路由。
     */
    fun ensureLockDecisionEngineInitializedAsResult(): GuardActionResult {
        return if (lockDecisionEngineProvider.ensureInitialized()) {
            GuardActionResult.Continue
        } else {
            GuardActionResult.Consumed(reason = "lock_engine_unavailable", hasSideEffect = false)
        }
    }

    /**
     * 处理 block hold 保护窗口，避免短时间内重复进入相同拦截包。
     * 输入：包名与当前时间；输出：命中 hold 时消费路由，否则继续。
     */
    fun handleBlockHold(packageName: String, currentTime: Long): GuardActionResult {
        val blockedPackage = readCurrentBlockedPackage()
        return if (currentTime < blockSessionController.blockHoldUntil() && packageName == blockedPackage) {
            GuardActionResult.Consumed(reason = "block_hold", hasSideEffect = false)
        } else {
            GuardActionResult.Continue
        }
    }

    /**
     * 处理白名单过渡场景对遮蔽层和待执行动作的收口。
     * 输入：包名与当前时间；输出：白名单命中时消费路由，否则继续。
     */
    fun handleWhitelistWindowEvent(packageName: String, currentTime: Long): GuardActionResult {
        if (!isInWhitelist(packageName) ||
            isSettingsPackage(packageName) ||
            isInstallerOrMarketPackage(packageName)
        ) {
            return GuardActionResult.Continue
        }

        Log.d(logTag, "应用 $packageName 在白名单中，跳过锁定")
        if (!readOverlayShowing()) {
            return GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = false)
        }

        val overlayBlockedPackage = readCurrentBlockedPackage()
        if (overlayBlockedPackage.isEmpty()) {
            blockSessionController.clearLastBlockedPackage()
            hideOverlay()
            return GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = true)
        }

        if (packageName == systemUiPackage &&
            (currentTime - blockSessionController.lastBlockTime()) < systemUiReleaseDelayMs
        ) {
            return GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = false)
        }

        if (overlayBlockedPackage != packageName && isTargetPackageActive(overlayBlockedPackage)) {
            Log.d(
                logTag,
                "白名单过渡界面 $packageName 出现，但被拦截应用 $overlayBlockedPackage 仍在前台，保持遮蔽层"
            )
            return GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = false)
        }

        if (overlayBlockedPackage != packageName) {
            cancelPendingBlockActions("whitelist_transition:$packageName")
            blockSessionController.clearLastBlockedPackage()
            hideOverlay()
            return GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = true)
        }

        return GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = false)
    }

    /**
     * 触发常规应用策略检查协程。
     * 输入：目标包名；输出：已安排 follow-up 的统一路由结果。
     */
    fun launchNormalPolicyCheck(packageName: String): GuardActionResult {
        appScope.launch {
            try {
                checkPolicyAndExecute(packageName)
            } catch (e: Exception) {
                Log.e(logTag, "检查策略时出错: ${e.message}", e)
            }
        }
        return GuardActionResult.ScheduleFollowUp(reason = "normal_policy_check")
    }

    /**
     * 执行一次常规应用策略检查并根据结果触发后续行为。
     * 输入：目标包名；输出：无，副作用由 block session / overlay / navigation 完成。
     */
    suspend fun checkPolicyAndExecute(packageName: String) {
        try {
            if (!lockDecisionEngineProvider.ensureInitialized()) {
                return
            }
            val decision = lockDecisionEngineProvider.getBlockDecision(packageName)

            Log.d(logTag, "检查应用 $packageName, 决策结果: ${decision.reason}, 是否阻塞: ${decision.shouldBlock}")

            if (decision.shouldBlock) {
                logBlockReason(packageName, decision.reason)
                enforceBlock(packageName, decision.appName.ifEmpty { packageName })
            } else if (readOverlayShowing()) {
                val now = nowProvider()
                if ((now - blockSessionController.lastBlockTime()) >= overlayStabilityWindowMs) {
                    blockSessionController.clearLastBlockedPackage()
                    hideOverlay()
                } else {
                    Log.d(logTag, "保持遮蔽层稳定窗口，暂不隐藏")
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "检查策略时出错: ${e.message}", e)
            hideOverlay()
            blockSessionController.clearLastBlockedPackage()
        }
    }

    /**
     * 执行一次常规应用拦截。
     * 输入：目标包名与展示名称；输出：无，副作用包括遮蔽层、导航和延迟动作。
     */
    fun enforceBlock(packageName: String, appName: String) {
        val currentTime = nowProvider()
        cancelPendingBlockActions("new_block:$packageName")
        val protectedSystemSurface = protectedSurfaceCallbacks.isProtectedSystemSurface(packageName)
        if (shouldSkipDuplicateOverlay(
                overlayShowing = readOverlayShowing(),
                currentBlockedPackage = readCurrentBlockedPackage(),
                targetPackage = packageName,
                protectedSystemSurface = protectedSystemSurface
            )
        ) {
            Log.d(logTag, "应用 $packageName 遮蔽层已显示，跳过重复拦截")
            return
        }

        if (readOverlayShowing() && readCurrentBlockedPackage() == packageName) {
            Log.w(logTag, "protected_surface_reinforce package=$packageName")
        }

        var requireStrongExit = false
        if (blockSessionController.lastBlockedPackage() == packageName &&
            (currentTime - blockSessionController.lastBlockTime()) < blockCooldownMs
        ) {
            if (isTargetPackageActive(packageName)) {
                Log.d(logTag, "应用 $packageName 冷却期内仍在前台，继续执行兜底拦截")
                requireStrongExit = true
            } else {
                Log.d(logTag, "应用 $packageName 在拦截冷却期内，跳过")
                return
            }
        }

        blockSessionController.recordBlock(packageName, currentTime, blockHoldDurationMs)
        val shouldReshowOverlay = blockSessionController.shouldReshowOverlay(
            packageName = packageName,
            currentTime = currentTime,
            cooldownMs = overlayReshowCooldownMs
        )

        if (shouldReshowOverlay) {
            postToMain {
                blockSessionController.showOverlay(
                    packageName = packageName,
                    appName = appName,
                    shownAt = nowProvider()
                )
                if (protectedSystemSurface) {
                    protectedSurfaceCallbacks.scheduleProtectedReleaseCheck(packageName)
                }
            }
        } else {
            Log.d(logTag, "应用 $packageName 处于遮蔽层重展示冷却期，执行静默压制")
        }

        navigationExecutor.performGlobalAction(if (requireStrongExit) homeAction else backAction)

        forceStopDelaysMs.forEach { delayMs ->
            scheduleDeferredBlockAction(packageName, delayMs, "force_stop_$delayMs") {
                tryForceStopApp(packageName)
            }
        }
        fallbackNavigationDelaysMs.forEach { delayMs ->
            scheduleDeferredBlockAction(packageName, delayMs, "fallback_nav_$delayMs") {
                tryFallbackNavigation(packageName)
            }
        }
        if (isHuaweiFamilyDevice) {
            scheduleDeferredBlockAction(
                targetPackage = packageName,
                delayMs = huaweiFallbackDelayMs,
                actionLabel = "fallback_nav_huawei_$huaweiFallbackDelayMs"
            ) {
                tryFallbackNavigation(packageName)
            }
        }

        if (shouldScheduleNormalOverlayRelease(protectedSystemSurface, shouldReshowOverlay)) {
            scheduleOverlayReleaseCheck(packageName)
        }
    }

    /**
     * 取消当前 normal block 相关的延迟动作。
     * 输入：取消原因；输出：无。
     */
    fun cancelPendingBlockActions(reason: String) {
        guardActionScheduler.cancelOwner(schedulerOwnerPendingBlock)
        if (blockSessionController.pendingBlockPackage().isNotEmpty()) {
            Log.d(
                logTag,
                "清理延迟拦截任务 reason=$reason package=${blockSessionController.pendingBlockPackage()}"
            )
        }
        blockSessionController.clearPendingBlockPackage()
    }

    /**
     * 隐藏 overlay 并取消 normal overlay release 检查。
     * 输入：无；输出：无。
     */
    fun hideOverlay() {
        blockSessionController.cancelReleaseChecks(schedulerOwnerOverlayRelease)
        blockSessionController.hideOverlay()
    }

    /**
     * 判断目标包名是否仍在前台或可交互窗口中可见。
     * 输入：目标包名；输出：是否仍可视为活跃目标。
     */
    fun isTargetPackageActive(packageName: String): Boolean {
        val activePackage = windowInspectorSnapshotApi.activePackageName()
        if (activePackage == packageName) {
            return true
        }
        if (isPackageVisibleInInteractiveWindows(packageName)) {
            return true
        }
        return getRecentTopPackageName() == packageName
    }

    /**
     * 读取最近一次系统认为的顶部包名。
     * 输入：无；输出：最近顶部包名或 `null`。
     */
    fun getRecentTopPackageName(): String? {
        return windowInspectorSnapshotApi.recentTopPackageName()
    }

    /**
     * 记录 block reason 对应的调试日志。
     * 输入：目标包名与 block reason；输出：无。
     */
    private fun logBlockReason(packageName: String, reason: BlockReason) {
        when (reason) {
            BlockReason.GLOBAL_LOCK ->
                Log.d(logTag, "全局锁开启，拦截应用: $packageName")
            BlockReason.APP_BLOCKED ->
                Log.d(logTag, "应用被永久禁用: $packageName")
            BlockReason.TIME_LIMIT_EXCEEDED ->
                Log.d(logTag, "应用使用时长已达限制: $packageName")
            BlockReason.TIME_WINDOW_BLOCKED ->
                Log.d(logTag, "应用在禁用时段内: $packageName")
            else -> Unit
        }
    }

    /**
     * 判断目标包名是否出现在交互窗口快照中。
     * 输入：目标包名；输出：是否可见。
     */
    private fun isPackageVisibleInInteractiveWindows(packageName: String): Boolean {
        return windowInspectorSnapshotApi.interactiveWindowSnapshots().any { snapshot ->
            snapshot.packageName == packageName
        }
    }

    /**
     * 尝试执行兜底导航，将仍在前台的目标带离当前界面。
     * 输入：目标包名；输出：无。
     */
    private fun tryFallbackNavigation(packageName: String) {
        if (!isTargetPackageActive(packageName)) {
            Log.d(logTag, "应用 $packageName 已离开前台，跳过兜底导航")
            return
        }

        navigationExecutor.performGlobalAction(homeAction)
    }

    /**
     * 安排一条 normal block 延迟动作。
     * 输入：目标包名、延迟毫秒、动作标签与执行体；输出：无。
     */
    private fun scheduleDeferredBlockAction(
        targetPackage: String,
        delayMs: Long,
        actionLabel: String,
        action: () -> Unit
    ) {
        guardActionScheduler.schedule(
            owner = schedulerOwnerPendingBlock,
            key = targetPackage,
            delayMs = delayMs
        ) {
            if (!canExecuteDeferredBlockAction(targetPackage, actionLabel)) {
                return@schedule
            }
            action()
        }
    }

    /**
     * 判断某条延迟动作在执行时是否仍然有效。
     * 输入：目标包名与动作标签；输出：是否允许继续执行。
     */
    private fun canExecuteDeferredBlockAction(targetPackage: String, actionLabel: String): Boolean {
        if (blockSessionController.pendingBlockPackage() != targetPackage) {
            Log.d(
                logTag,
                "延迟动作 $actionLabel 取消：目标已切换为 ${blockSessionController.pendingBlockPackage()}"
            )
            return false
        }

        val activePackage = windowInspectorSnapshotApi.activePackageName()
        val targetActiveOrVisible = isTargetPackageActive(targetPackage)
        if (isSelfAppPackage(activePackage)) {
            Log.d(logTag, "延迟动作 $actionLabel 取消：当前前台为本应用")
            return false
        }
        if (activePackage.isNotEmpty() && activePackage != targetPackage && !targetActiveOrVisible) {
            Log.d(logTag, "延迟动作 $actionLabel 取消：当前前台=$activePackage, 目标=$targetPackage")
            return false
        }
        if (!targetActiveOrVisible) {
            Log.d(logTag, "延迟动作 $actionLabel 取消：目标已不在前台")
            return false
        }
        return true
    }

    /**
     * 为目标包名安排 overlay release 检查。
     * 输入：目标包名；输出：无。
     */
    private fun scheduleOverlayReleaseCheck(packageName: String) {
        if (protectedSurfaceCallbacks.isProtectedSystemSurface(packageName)) {
            if (readOverlayShowing() && readCurrentBlockedPackage() == packageName) {
                protectedSurfaceCallbacks.scheduleProtectedReleaseCheck(packageName)
            }
            return
        }

        val releaseCheckDelays = longArrayOf(1500L, 2800L, 4200L)
        releaseCheckDelays.forEach { delayMillis ->
            blockSessionController.scheduleReleaseCheck(
                owner = schedulerOwnerOverlayRelease,
                key = packageName,
                delayMs = delayMillis
            ) {
                if (!readOverlayShowing()) {
                    return@scheduleReleaseCheck
                }
                if (readCurrentBlockedPackage() != packageName) {
                    return@scheduleReleaseCheck
                }
                if (isTargetPackageActive(packageName)) {
                    return@scheduleReleaseCheck
                }
                Log.d(logTag, "应用 $packageName 不在前台，自动关闭遮蔽层")
                hideOverlay()
                blockSessionController.clearLastBlockedPackage()
                blockSessionController.clearOverlayTracking()
            }
        }
    }

    /**
     * 尝试结束目标应用的前台任务、后台进程与 force-stop 路径。
     * 输入：目标包名；输出：无。
     */
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
                            Log.e(logTag, "结束任务失败: ${e.message}", e)
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
                            Log.e(logTag, "杀进程失败: ${e.message}", e)
                        }
                    }
                }
            }

            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.e(logTag, "杀后台失败: ${e.message}", e)
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
                Log.w(logTag, "forceStopPackage无权限，后续改用前台压制策略")
                return
            }
            Log.e(logTag, "forceStopPackage失败: ${e.message}", e)
        }

        try {
            Runtime.getRuntime().exec("am force-stop $packageName")
        } catch (e: Exception) {
            Log.e(logTag, "am force-stop失败: ${e.message}", e)
        }
    }
}
