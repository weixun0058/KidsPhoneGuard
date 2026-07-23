package com.kidsphoneguard.service.guard

import android.util.Log
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kidsphoneguard.engine.uninstall.UninstallDecision
import com.kidsphoneguard.engine.uninstall.UninstallDecisionEngine
import com.kidsphoneguard.engine.uninstall.UninstallDecisionType
import com.kidsphoneguard.engine.uninstall.UninstallRuntimeState
import com.kidsphoneguard.engine.uninstall.UninstallSurfaceSnapshot
import com.kidsphoneguard.engine.uninstall.shouldRearmUninstallOverlayChecks
import com.kidsphoneguard.engine.uninstall.shouldReleaseUninstallOverlay
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.accessibility.WindowInspectorSnapshotApi
import com.kidsphoneguard.service.block.BlockSessionController
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.NavigationExecutor
import com.kidsphoneguard.utils.WhitelistManager

/**
 * 防卸载守卫（薄 Android 壳）。
 *
 * 职责：采集安装器/launcher 家族窗口快照 → 调用纯决策核心 [UninstallDecisionEngine] →
 * 复用现有"BACK/HOME + 红屏遮罩"执行体系（GuardActionScheduler / NavigationExecutor /
 * BlockSessionController）执行阻断；遮蔽层释放由本守卫按"卸载威胁是否仍存在"决策驱动（带持有上限）。
 * 所有权：安装器家族与 launcher 家族表面归本守卫单 owner；设置/管理器页面仍归 ProtectedSurfaceGuard。
 */
class UninstallGuard(
    private val logTag: String,
    private val state: ProtectedSurfaceState,
    private val windowInspectorSnapshotApi: WindowInspectorSnapshotApi,
    private val navigationExecutor: NavigationExecutor,
    private val guardActionScheduler: GuardActionScheduler,
    private val blockSessionController: BlockSessionController,
    private val readRootInActiveWindow: () -> AccessibilityNodeInfo?,
    private val readWindows: () -> List<AccessibilityWindowInfo>?,
    private val postToMain: ((() -> Unit) -> Unit),
    private val publishLifecycleSignal: (String) -> Unit,
    private val hideOverlay: () -> Unit,
    private val isOverlayShowing: () -> Boolean,
    private val readCurrentBlockedPackage: () -> String,
    private val schedulerOwnerUninstallRelease: String,
    private val isGlobalUnlockEnabled: () -> Boolean,
    private val isSetupAccessAllowed: () -> Boolean,
    private val snapshotTextLimit: Int,
    private val suppressCooldownMs: Long,
    private val sweepCooldownMs: Long,
    private val navigationBurstDelays: LongArray,
    private val blockHoldDuration: Long,
    private val schedulerOwnerUninstallGuard: String,
    private val backAction: Int,
    private val homeAction: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val decisionEngine = UninstallDecisionEngine()

    /** 最近一次"长按本应用图标"的时间戳（0 = 从未记录）；用于卸载判定的归因窗口。 */
    private var lastTargetAppLongPressAtMs: Long = 0L

    init {
        Log.d(logTag, "uninstall_guard_ready ${decisionEngine.describeRules()}")
    }

    /**
     * 判断包名是否属于本守卫所有权（安装器家族或 launcher 家族）。
     * 输入：任意包名；输出：是否由 UninstallGuard 单 owner 处理。
     */
    fun isOwnedSurface(packageName: String): Boolean {
        return decisionEngine.isOwnedPackage(packageName)
    }

    /**
     * 处理 router 派发的事件；命中卸载威胁时消费路由并执行阻断。
     * 输入：事件、归一化包名与来源标记；输出：命中阻断时 Consumed，否则 Continue。
     */
    fun handleOwnedSurfaceEvent(
        event: AccessibilityEvent?,
        packageName: String,
        source: String
    ): GuardActionResult {
        if (!decisionEngine.isOwnedPackage(packageName)) {
            return GuardActionResult.Continue
        }
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            recordTargetAppLongPress(event)
        }
        // launcher 是高频表面：窗口状态变化（新窗口/卸载确认对话框出现）必须评估；
        // 仅内容变化等高频事件在没有任何卸载/本应用信号时才跳过，避免主屏每次事件都构建快照。
        if (decisionEngine.isLauncherPackage(packageName) &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !hasCheapEventSignal(event)
        ) {
            return GuardActionResult.Continue
        }

        val snapshot = buildSurfaceSnapshot(event, packageName)
        val decision = evaluateSnapshot(snapshot)
        logDecision(snapshot, decision, source)

        return when (decision.type) {
            UninstallDecisionType.ALLOW -> GuardActionResult.Continue
            UninstallDecisionType.BLOCK_PAGE,
            UninstallDecisionType.BLOCK_ACTION -> {
                suppressUninstallSurface(packageName, source, decision)
                GuardActionResult.Consumed(reason = "uninstall_guard", hasSideEffect = true)
            }
        }
    }

    /**
     * 周期性扫描可交互窗口中的安装器/launcher 表面，命中卸载威胁时触发阻断。
     * 输入：来源标记；输出：无，必要时安排导航与遮罩动作。
     */
    fun sweepOwnedSurfaces(source: String) {
        // 只在活跃/聚焦窗口中寻找候选：不抓取非活跃窗口的根节点。
        // interactiveWindowSnapshots() 会为每个窗口抓取整棵节点树（每窗一次 IPC），
        // MIUI 桌面窗口多，每 480ms 全量抓取曾压死无障碍主线程（2026-07-23 真机实证）。
        val candidatePackage = findActiveOwnedWindowPackage() ?: return

        // 同一表面的遮罩已在显示时无需重复扫描：释放检查会自行按节奏评估威胁是否消失。
        if (isOverlayShowing() && isSameBasePackage(readCurrentBlockedPackage(), candidatePackage)) {
            return
        }

        val now = nowProvider()
        if (!state.shouldProcessProtectedWindowSweep(candidatePackage, now, sweepCooldownMs)) {
            return
        }

        val scanStart = nowProvider()
        val snapshot = buildSurfaceSnapshot(event = null, packageName = candidatePackage)
        val decision = evaluateSnapshot(snapshot)
        val scanElapsedMs = nowProvider() - scanStart
        if (scanElapsedMs > SWEEP_SLOW_SCAN_LOG_THRESHOLD_MS) {
            Log.w(logTag, "uninstall_sweep_slow_scan package=$candidatePackage elapsedMs=$scanElapsedMs")
        }
        if (decision.type == UninstallDecisionType.ALLOW) {
            return
        }
        Log.w(
            logTag,
            "uninstall_sweep_detected source=$source package=$candidatePackage " +
                "decision=${decision.type} reason=${decision.reason}"
        )
        suppressUninstallSurface(candidatePackage, source, decision)
    }

    /**
     * 在活跃/聚焦窗口中寻找安装器家族候选包名（廉价发现，不抓取非活跃窗口根节点）。
     * 周期 sweep 只覆盖安装器家族：launcher 桌面树遍历代价过高（MIUI 逐节点 IPC 实测数秒），
     * launcher 侧检测全部由事件路径（点击/长按/窗口状态变化 + 长按归因）承担。
     * 输入：无；输出：命中的安装器包名，未命中或读取失败返回 null。
     */
    private fun findActiveOwnedWindowPackage(): String? {
        val windowList = try {
            readWindows()
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_sweep_windows_failed: ${e.message}", e)
            null
        } ?: return null

        windowList.forEach { window ->
            if (!window.isActive && !window.isFocused) {
                return@forEach
            }
            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(logTag, "uninstall_sweep_window_root_failed: ${e.message}", e)
                null
            }
            try {
                val packageName = root?.packageName?.toString().orEmpty()
                if (decisionEngine.isInstallerPackage(packageName)) {
                    return packageName
                }
            } finally {
                root?.recycle()
            }
        }
        return null
    }

    /**
     * 构建卸载表面快照（镜像 ProtectedSurfaceGuard 的采集语义：事件文本 + 候选窗口节点 + 根窗口节点）。
     * 输入：可空事件与目标包名；输出：供决策核心评估的快照。
     */
    private fun buildSurfaceSnapshot(
        event: AccessibilityEvent?,
        packageName: String
    ): UninstallSurfaceSnapshot {
        val pageSignals = mutableListOf<String>()
        val clickedSignals = mutableListOf<String>()
        val windowPackages = linkedSetOf<String>()
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
            Log.e(logTag, "uninstall_snapshot_event_source_failed: ${e.message}", e)
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
            readRootInActiveWindow()
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_snapshot_root_failed: ${e.message}", e)
            null
        }
        try {
            val rootPackageName = root?.packageName?.toString().orEmpty()
            appendSignal(windowPackages, rootPackageName)
            // 只有安装器家族的活动窗口才遍历节点（卸载确认对话框树很小）；
            // launcher 主窗口（桌面工作区）逐节点 IPC 代价极高（MIUI 实测数百节点 ≈ 3 秒），绝不遍历。
            if (decisionEngine.isInstallerPackage(rootPackageName) &&
                isSameBasePackage(rootPackageName, packageName)
            ) {
                collectNodeSignals(
                    root,
                    pageSignals,
                    windowPackages,
                    nodeBudget = intArrayOf(DIALOG_NODE_BUDGET)
                )
            }
        } finally {
            root?.recycle()
        }

        return UninstallSurfaceSnapshot(
            packageName = packageName,
            className = event?.className?.toString().orEmpty(),
            pageText = pageSignals.joinToString(" ").take(snapshotTextLimit),
            windowPackages = windowPackages.filter { it.isNotEmpty() }.toSet(),
            clickedText = clickedSignals.joinToString(" ").take(snapshotTextLimit),
            recentTargetAppLongPress = isRecentTargetAppLongPress()
        )
    }

    /**
     * 调用纯决策核心并注入运行时状态；任何异常都降级为 ALLOW，绝不让异常逃出守卫。
     * 输入：窗口表面快照；输出：卸载拦截决策。
     */
    private fun evaluateSnapshot(snapshot: UninstallSurfaceSnapshot): UninstallDecision {
        return try {
            decisionEngine.evaluate(
                snapshot = snapshot,
                runtimeState = UninstallRuntimeState(
                    isGlobalUnlockEnabled = isGlobalUnlockEnabled(),
                    isSetupAccessAllowed = isSetupAccessAllowed()
                )
            )
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_decision_failed: ${e.message}", e)
            UninstallDecision(type = UninstallDecisionType.ALLOW, reason = "decision_error")
        }
    }

    /**
     * 执行卸载表面阻断，复用与 ProtectedSurfaceGuard 相同的遮罩 + BACK/HOME 突发导航体系。
     * 输入：包名、来源标记与决策；输出：无，必要时安排导航与遮罩动作。
     */
    private fun suppressUninstallSurface(
        packageName: String,
        source: String,
        decision: UninstallDecision
    ) {
        if (WhitelistManager.isSelfApp(packageName)) {
            return
        }
        if (isSuppressionAllowed()) {
            Log.d(logTag, "uninstall_surface_skip_allowed source=$source package=$packageName")
            return
        }

        val now = nowProvider()
        if (!state.shouldSuppressProtectedSurface(packageName, now, suppressCooldownMs)) {
            return
        }

        blockSessionController.recordBlock(packageName, now, blockHoldDuration)
        publishLifecycleSignal("uninstall_guard_block:$packageName")
        Log.w(
            logTag,
            "uninstall_surface_block source=$source package=$packageName " +
                "decision=${decision.type} reason=${decision.reason} target=${decision.matchedTarget} " +
                "uninstall=${decision.matchedUninstallKeywords.joinToString(",")}"
        )

        postToMain {
            if (!isOwnedTargetInteractiveNow(packageName)) {
                Log.d(logTag, "uninstall_overlay_skip_target_left package=$packageName")
                cancelUninstallSurfaceActions(packageName)
                if (isOverlayShowing() && readCurrentBlockedPackage() == packageName) {
                    hideOverlay()
                }
                blockSessionController.clearSession()
                return@postToMain
            }
            blockSessionController.showOverlay(
                packageName = packageName,
                appName = packageName,
                shownAt = nowProvider()
            )
            scheduleUninstallOverlayReleaseCheck(packageName, cycle = 0)
        }

        guardActionScheduler.cancelKey(schedulerOwnerUninstallGuard, packageName)
        navigationBurstDelays.forEach { delayMs ->
            if (delayMs == 0L) {
                performUninstallSurfaceNavigation(packageName, source, delayMs)
            } else {
                guardActionScheduler.schedule(
                    owner = schedulerOwnerUninstallGuard,
                    key = packageName,
                    delayMs = delayMs
                ) {
                    performUninstallSurfaceNavigation(packageName, source, delayMs)
                }
            }
        }
    }

    /**
     * 卸载遮蔽层的延迟释放检查（决策驱动，带持有上限）。
     * 与 ProtectedSurfaceGuard 的"目标离开前台即释放"不同：launcher 被拦截后 HOME 的归宿仍是 launcher，
     * 前台条件永不收敛，必须以"卸载威胁是否仍存在"为释放依据，并设硬上限避免遮蔽层被无限持有。
     * 输入：包名与已重排轮次；输出：无，必要时释放遮蔽层或按节奏重排检查。
     */
    private fun scheduleUninstallOverlayReleaseCheck(packageName: String, cycle: Int) {
        blockSessionController.cancelReleaseChecks(schedulerOwnerUninstallRelease)
        UNINSTALL_RELEASE_CHECK_DELAYS.forEach { delayMillis ->
            blockSessionController.scheduleReleaseCheck(
                owner = schedulerOwnerUninstallRelease,
                key = packageName,
                delayMs = delayMillis
            ) {
                if (!isOverlayShowing()) {
                    return@scheduleReleaseCheck
                }
                if (readCurrentBlockedPackage() != packageName) {
                    return@scheduleReleaseCheck
                }
                val suppressionAllowed = isSuppressionAllowed()
                val threatStillPresent = isUninstallThreatStillPresent(packageName)
                if (shouldReleaseUninstallOverlay(suppressionAllowed, threatStillPresent)) {
                    val reason = if (suppressionAllowed) "parent_allowance" else "threat_gone"
                    Log.d(
                        logTag,
                        "uninstall_overlay_auto_release package=$packageName delayMs=$delayMillis reason=$reason"
                    )
                    hideOverlay()
                    blockSessionController.clearSession()
                    return@scheduleReleaseCheck
                }
                val isFinalCheck = delayMillis == UNINSTALL_RELEASE_CHECK_DELAYS.last()
                if (shouldRearmUninstallOverlayChecks(isFinalCheck, cycle, MAX_UNINSTALL_OVERLAY_HOLD_CYCLES)) {
                    Log.w(logTag, "uninstall_overlay_hold package=$packageName delayMs=$delayMillis cycle=$cycle")
                    scheduleUninstallOverlayReleaseCheck(packageName, cycle + 1)
                } else if (isFinalCheck) {
                    // 持有达上限仍判为有威胁：先释放避免无限遮蔽；周期 sweep 会在威胁真实存在时立即重新拦截。
                    Log.w(logTag, "uninstall_overlay_max_hold_release package=$packageName cycle=$cycle")
                    hideOverlay()
                    blockSessionController.clearSession()
                }
            }
        }
    }

    /**
     * 重新评估目标表面当前是否仍存在卸载威胁（供释放检查使用）。
     * 输入：包名；输出：当前快照是否仍判定为阻断；任何异常按威胁已消失处理，避免遮蔽层卡死。
     */
    private fun isUninstallThreatStillPresent(packageName: String): Boolean {
        return try {
            val decision = evaluateSnapshot(buildSurfaceSnapshot(event = null, packageName = packageName))
            decision.type != UninstallDecisionType.ALLOW
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_overlay_release_eval_failed: ${e.message}", e)
            false
        }
    }

    /**
     * 执行卸载表面导航突发动作（与 ProtectedSurfaceGuard 同一 BACK/HOME 节奏）。
     * 输入：包名、来源标记与延迟时间；输出：无，必要时记录导航日志。
     */
    private fun performUninstallSurfaceNavigation(packageName: String, source: String, delayMs: Long) {
        try {
            val targetStillInteractive = isOwnedTargetInteractiveNow(packageName)
            if (!shouldExecuteUninstallSurfaceNavigation(delayMs, targetStillInteractive)) {
                Log.d(
                    logTag,
                    "uninstall_surface_nav_cancel source=$source package=$packageName " +
                        "delayMs=$delayMs reason=target_left"
                )
                cancelUninstallSurfaceActions(packageName)
                return
            }
            val action = when (delayMs) {
                60L, 280L, 1500L -> backAction
                else -> homeAction
            }
            val actionName = if (action == backAction) "BACK" else "HOME"
            val handled = navigationExecutor.performGlobalAction(action)
            Log.w(
                logTag,
                "uninstall_surface_nav source=$source package=$packageName delayMs=$delayMs action=$actionName handled=$handled"
            )
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_surface_nav_failed: ${e.message}", e)
        }
    }

    /**
     * 判断目标包是否仍占据当前活动或聚焦的可交互窗口。
     * 输入：包名；输出：是否仍可交互。
     */
    private fun isOwnedTargetInteractiveNow(packageName: String): Boolean {
        val activePackage = windowInspectorSnapshotApi.activePackageName()
        if (isSameBasePackage(activePackage, packageName)) {
            return true
        }
        return windowInspectorSnapshotApi.interactiveWindowSnapshots().any { snapshot ->
            isSameBasePackage(snapshot.packageName, packageName) &&
                (snapshot.isActive || snapshot.isFocused)
        }
    }

    /** 取消同一卸载表面剩余的导航突发动作。 */
    private fun cancelUninstallSurfaceActions(packageName: String) {
        guardActionScheduler.cancelKey(schedulerOwnerUninstallGuard, packageName)
    }

    /**
     * 判断当前是否处于家长逃生口（全局解锁或设置向导放行）。
     * 输入：无；输出：是否应跳过压制。
     */
    private fun isSuppressionAllowed(): Boolean {
        return try {
            isGlobalUnlockEnabled() || isSetupAccessAllowed()
        } catch (e: Exception) {
            Log.e(logTag, "read_uninstall_allow_state_failed: ${e.message}", e)
            false
        }
    }

    /**
     * 记录"长按本应用图标"的归因时间戳。
     * MIUI 卸载确认界面可能不显示应用名，而遍历桌面整树取图标文本代价过高；
     * 长按是桌面卸载流程的必要第一步，用它做 8 秒归因窗口即可保住后续点击/弹窗判定。
     * 输入：长按事件；输出：无，命中时更新 [lastTargetAppLongPressAtMs]。
     */
    private fun recordTargetAppLongPress(event: AccessibilityEvent) {
        val eventSignal = listOf(
            event.text.joinToString(" ") { it?.toString().orEmpty() },
            event.contentDescription?.toString().orEmpty()
        ).joinToString(" ")
        if (decisionEngine.containsTargetAppSignal(eventSignal)) {
            lastTargetAppLongPressAtMs = nowProvider()
            Log.d(logTag, "uninstall_target_long_press_recorded source=event")
            return
        }
        // 事件文本为空时回退检查源节点自身（只看本节点，不递归，成本恒定）。
        val source = try {
            event.source
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_long_press_source_failed: ${e.message}", e)
            null
        }
        try {
            val sourceSignal = listOf(
                source?.text?.toString().orEmpty(),
                source?.contentDescription?.toString().orEmpty()
            ).joinToString(" ")
            if (decisionEngine.containsTargetAppSignal(sourceSignal)) {
                lastTargetAppLongPressAtMs = nowProvider()
                Log.d(logTag, "uninstall_target_long_press_recorded source=node")
            }
        } finally {
            source?.recycle()
        }
    }

    /**
     * 判断是否处于长按归因窗口内（近期长按过本应用图标）。
     */
    private fun isRecentTargetAppLongPress(): Boolean {
        return nowProvider() - lastTargetAppLongPressAtMs <= TARGET_LONG_PRESS_ATTRIBUTION_WINDOW_MS
    }

    /**
     * 对 launcher 高频事件做廉价预筛：事件自身文本不含卸载/本应用信号时跳过整树快照。
     * 输入：可空事件；输出：是否存在值得构建快照的信号。
     */
    private fun hasCheapEventSignal(event: AccessibilityEvent?): Boolean {
        event ?: return false
        val signal = listOf(
            event.text.joinToString(" ") { it?.toString().orEmpty() },
            event.contentDescription?.toString().orEmpty(),
            event.className?.toString().orEmpty()
        ).joinToString(" ")
        return decisionEngine.containsUninstallSignal(signal) ||
            decisionEngine.containsTargetAppSignal(signal)
    }

    /**
     * 输出卸载决策日志：BLOCK 决策全量记录，候选 ALLOW 仅调试记录。
     * 输入：页面快照、决策与来源标记；输出：无，必要时输出日志。
     */
    private fun logDecision(
        snapshot: UninstallSurfaceSnapshot,
        decision: UninstallDecision,
        source: String
    ) {
        when (decision.type) {
            UninstallDecisionType.BLOCK_PAGE,
            UninstallDecisionType.BLOCK_ACTION -> {
                Log.w(
                    logTag,
                    "uninstall_decision type=${decision.type} package=${snapshot.packageName} " +
                        "reason=${decision.reason} target=${decision.matchedTarget} " +
                        "uninstall=${decision.matchedUninstallKeywords.joinToString(",")} source=$source " +
                        "clicked=${snapshot.clickedText.take(160)} sample=${snapshot.pageText.take(240)}"
                )
            }

            UninstallDecisionType.ALLOW -> {
                if (decision.reason != "not_uninstall_candidate") {
                    Log.d(
                        logTag,
                        "uninstall_decision_allow package=${snapshot.packageName} " +
                            "reason=${decision.reason} source=$source"
                    )
                }
            }
        }
    }

    /**
     * 判断事件是否属于用户显式点击/长按动作。
     * 输入：可空事件；输出：是否属于显式交互事件。
     */
    private fun isExplicitUserActionEvent(event: AccessibilityEvent?): Boolean {
        return event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
    }

    /**
     * 收集与目标包同基包名的窗口节点信号。
     * 遍历策略（2026-07-23 MIUI 实证后修订）：安装器家族窗口（卸载确认对话框树小）总是遍历；
     * launcher 家族只遍历"非全屏"窗口（卸载确认对话框），**全屏 launcher 主窗口（桌面工作区）绝不遍历**——
     * 桌面树逐节点 IPC，数百节点即数秒，是遮蔽层卡死与拦截过慢的根因。
     * 输入：目标包名、信号集合与窗口包集合；输出：无，集合被就地追加。
     */
    private fun collectCandidateWindowNodeSignals(
        targetPackageName: String,
        signals: MutableList<String>,
        windowPackages: MutableSet<String>
    ) {
        val windowList = try {
            readWindows()
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_snapshot_windows_failed: ${e.message}", e)
            null
        } ?: return

        var maxWindowHeight = 0
        val boundsBuffer = Rect()
        windowList.forEach { window ->
            try {
                window.getBoundsInScreen(boundsBuffer)
                maxWindowHeight = maxOf(maxWindowHeight, boundsBuffer.height())
            } catch (e: Exception) {
                Log.e(logTag, "uninstall_snapshot_bounds_failed: ${e.message}", e)
            }
        }

        windowList.forEach { window ->
            // 非活跃/非聚焦窗口不抓根节点：每次 root 抓取都是一次 IPC，全量抓取会压死主线程。
            if (!window.isActive && !window.isFocused) {
                return@forEach
            }
            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(logTag, "uninstall_snapshot_window_root_failed: ${e.message}", e)
                null
            }
            try {
                val windowPackageName = root?.packageName?.toString().orEmpty()
                appendSignal(windowPackages, windowPackageName)
                if (windowPackageName.isEmpty() || !isSameBasePackage(windowPackageName, targetPackageName)) {
                    return@forEach
                }
                val shouldWalk = decisionEngine.isInstallerPackage(windowPackageName) ||
                    (decisionEngine.isLauncherPackage(windowPackageName) &&
                        isSmallDialogWindow(window, maxWindowHeight))
                if (shouldWalk) {
                    collectNodeSignals(
                        root,
                        signals,
                        windowPackages,
                        nodeBudget = intArrayOf(DIALOG_NODE_BUDGET)
                    )
                }
            } finally {
                root?.recycle()
            }
        }
    }

    /**
     * 判断窗口是否为"非全屏"对话框（相对屏幕上最高窗口的高度占比）。
     * 输入：窗口与当前最大窗口高度；输出：是否应视为可遍历的小对话框。
     */
    private fun isSmallDialogWindow(window: AccessibilityWindowInfo, maxWindowHeight: Int): Boolean {
        if (maxWindowHeight <= 0) {
            return true
        }
        val bounds = Rect()
        return try {
            window.getBoundsInScreen(bounds)
            bounds.height() < maxWindowHeight * 85 / 100
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_window_bounds_failed: ${e.message}", e)
            false
        }
    }

    /**
     * 递归收集节点树文本、描述和资源标识。
     * 长度预算用 [collectedLength] 增量累计，禁止每节点 joinToString 重算总长：
     * MIUI 桌面树节点多，O(n²) 重算会阻塞无障碍主线程数秒（2026-07-23 小米真机 ANR 实证），
     * 导致遮罩 show/hide 指令排队超时、遮蔽层长时间不退并反复触发。
     * 输入：节点、信号集合、窗口包集合与长度预算；输出：无，集合被就地追加。
     */
    private fun collectNodeSignals(
        node: AccessibilityNodeInfo?,
        signals: MutableList<String>,
        windowPackages: MutableSet<String>,
        depth: Int = 0,
        maxTextLength: Int = snapshotTextLimit,
        visibleOnly: Boolean = true,
        collectedLength: IntArray = IntArray(1),
        nodeBudget: IntArray = intArrayOf(MAX_SNAPSHOT_NODES)
    ) {
        if (node == null || depth > 40 || collectedLength[0] >= maxTextLength || nodeBudget[0] <= 0) {
            return
        }
        nodeBudget[0] -= 1

        try {
            appendSignal(windowPackages, node.packageName?.toString().orEmpty())
            if (!visibleOnly || node.isVisibleToUser) {
                collectedLength[0] += appendSignal(signals, node.text?.toString().orEmpty())
                collectedLength[0] += appendSignal(signals, node.contentDescription?.toString().orEmpty())
                collectedLength[0] += appendSignal(signals, node.viewIdResourceName.orEmpty())
            }

            for (index in 0 until node.childCount) {
                if (collectedLength[0] >= maxTextLength || nodeBudget[0] <= 0) {
                    return
                }
                val child = try {
                    node.getChild(index)
                } catch (e: Exception) {
                    Log.e(logTag, "uninstall_snapshot_child_failed: ${e.message}", e)
                    null
                }
                try {
                    collectNodeSignals(
                        child,
                        signals,
                        windowPackages,
                        depth + 1,
                        maxTextLength,
                        visibleOnly,
                        collectedLength,
                        nodeBudget
                    )
                } finally {
                    child?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "uninstall_snapshot_node_failed: ${e.message}", e)
        }
    }

    /**
     * 向信号集合追加一个非空字符串，返回计入长度预算的值（含一个拼接空格）。
     * 输入：目标集合与待追加值；输出：本次追加的长度，未追加返回 0。
     */
    private fun appendSignal(signals: MutableCollection<String>, value: String): Int {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            signals.add(trimmed)
            return trimmed.length + 1
        }
        return 0
    }

    companion object {
        /** 卸载遮蔽层释放检查节奏（决策驱动）；最大重排 1 轮，防无限持有。 */
        private val UNINSTALL_RELEASE_CHECK_DELAYS = longArrayOf(900L, 1600L, 2600L, 4200L)
        private const val MAX_UNINSTALL_OVERLAY_HOLD_CYCLES = 1

        /** 单次快照遍历的节点数硬上限：卸载界面都位于视图树浅层，封顶保证每次扫描成本有界。 */
        private const val MAX_SNAPSHOT_NODES = 400

        /** 卸载确认对话框等小树的遍历预算（节点数）。 */
        private const val DIALOG_NODE_BUDGET = 250

        /** "长按本应用图标"归因窗口：长按后在该时长内的卸载点击/确认弹窗均归因到本应用。 */
        private const val TARGET_LONG_PRESS_ATTRIBUTION_WINDOW_MS = 8000L

        /** 周期扫描耗时超过该阈值时输出慢扫描日志（真机性能观测用）。 */
        private const val SWEEP_SLOW_SCAN_LOG_THRESHOLD_MS = 150L

        /**
         * 纯判断 helper：比较两个包名是否属于同一基包。
         * 输入：两个包名字符串；输出：去掉 `:` 后是否为同一基包。
         */
        internal fun isSameBasePackage(first: String, second: String): Boolean {
            val normalizedFirst = first.trim().substringBefore(':').lowercase()
            val normalizedSecond = second.trim().substringBefore(':').lowercase()
            return normalizedFirst.isNotEmpty() && normalizedFirst == normalizedSecond
        }

        /** 首次 HOME 必须立即执行；后续突发动作仅在目标仍可交互时执行。 */
        internal fun shouldExecuteUninstallSurfaceNavigation(
            delayMs: Long,
            targetStillInteractive: Boolean
        ): Boolean = delayMs == 0L || targetStillInteractive
    }
}
