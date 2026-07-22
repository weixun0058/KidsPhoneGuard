package com.kidsphoneguard.service.guard

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsDecision
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsDecisionType
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsPolicy
import com.kidsphoneguard.engine.settingsprotection.SettingsPageSnapshot
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.accessibility.WindowInspectorSnapshotApi
import com.kidsphoneguard.service.block.BlockSessionController
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.NavigationExecutor
import com.kidsphoneguard.utils.WhitelistManager
import com.kidsphoneguard.utils.SystemSurfaceClassifier

/**
 * 负责 protected settings / protected windows / protected suppression 的编排与状态管理。
 * 输入：受保护页面检测所需依赖、窗口读取回调与 block/session 控制能力；输出：统一的 `GuardActionResult` 与 protected surface 执行动作。
 */
class ProtectedSurfaceGuard(
    private val logTag: String,
    private val protectedSettingsPolicy: ProtectedSettingsPolicy,
    private val state: ProtectedSurfaceState,
    private val windowInspectorSnapshotApi: WindowInspectorSnapshotApi,
    private val navigationExecutor: NavigationExecutor,
    private val guardActionScheduler: GuardActionScheduler,
    private val blockSessionController: BlockSessionController,
    private val readRootInActiveWindow: () -> AccessibilityNodeInfo?,
    private val readWindows: () -> List<AccessibilityWindowInfo>?,
    private val postToMain: ((() -> Unit) -> Unit),
    private val publishLifecycleSignal: (String) -> Unit,
    private val cancelPendingBlockActions: (String) -> Unit,
    private val hideOverlay: () -> Unit,
    private val isOverlayShowing: () -> Boolean,
    private val readCurrentBlockedPackage: () -> String,
    private val isTargetPackageActive: (String) -> Boolean,
    private val isGlobalUnlockEnabled: () -> Boolean,
    private val isSetupSettingsAccessAllowed: () -> Boolean,
    private val exitVisiblePowerSaveModeIfNeeded: (String) -> Boolean,
    private val collapseVisibleSystemPanelIfNeeded: (String) -> Boolean,
    private val isSystemPanelPackage: (String) -> Boolean,
    private val settingsSnapshotTextLimit: Int,
    private val protectedWindowLogCooldownMs: Long,
    private val protectedSettingsDecisionLogCooldownMs: Long,
    private val protectedWindowSweepCooldownMs: Long,
    private val protectedSurfaceSuppressCooldownMs: Long,
    private val protectedSurfaceNavigationBurstDelays: LongArray,
    private val blockHoldDuration: Long,
    private val schedulerOwnerProtectedSurface: String,
    private val schedulerOwnerOverlayRelease: String,
    private val backAction: Int,
    private val homeAction: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * 判断当前包名与事件是否需要进入 protected window sweep 逻辑。
     * 输入：事件与归一化包名；输出：是否应继续检查 protected windows。
     */
    fun shouldSweepProtectedWindows(event: AccessibilityEvent?, packageName: String): Boolean {
        return shouldSweepProtectedWindows(
            isCandidatePackage = protectedSettingsPolicy.isCandidatePackage(packageName),
            isInstallerOrMarketPackage = SystemSurfaceClassifier.isInstallerOrMarketSurface(packageName),
            eventType = event?.eventType
        )
    }

    /**
     * 处理 protected settings policy 分支并返回 router 可消费结果。
     * 输入：事件、归一化包名与来源标记；输出：命中时消费路由，否则继续。
     */
    fun handleProtectedSettingsPolicyIfCandidate(
        event: AccessibilityEvent?,
        packageName: String,
        source: String
    ): GuardActionResult {
        if (isSystemPanelPackage(packageName)) {
            return GuardActionResult.Continue
        }

        val isCandidatePackage = protectedSettingsPolicy.isCandidatePackage(packageName)
        if (!isCandidatePackage && !shouldSweepProtectedWindows(event, packageName)) {
            return GuardActionResult.Continue
        }
        val windowPackages = if (isCandidatePackage) {
            setOf(packageName)
        } else {
            collectInteractiveWindowPackages()
        }
        if (!isCandidatePackage && windowPackages.none { protectedSettingsPolicy.isCandidatePackage(it) }) {
            return GuardActionResult.Continue
        }

        val snapshot = buildSettingsPageSnapshot(event, packageName, source, windowPackages)
        val decision = protectedSettingsPolicy.evaluate(snapshot)
        logProtectedSettingsDecision(snapshot, decision)

        when (decision.type) {
            ProtectedSettingsDecisionType.ALLOW -> {
                releaseProtectedSettingsOverlayIfAllowed(snapshot, decision)
            }

            ProtectedSettingsDecisionType.OBSERVE -> {
                // OBSERVE means that the current accessibility snapshot is incomplete,
                // not that a previously blocked surface has become safe. Releasing here
                // made the overlay flash and disappear during Recents/window transitions.
                if (SystemSurfaceClassifier.isAppMarketSurface(packageName)) {
                    return GuardActionResult.Continue
                }
            }

            ProtectedSettingsDecisionType.BLOCK_PAGE,
            ProtectedSettingsDecisionType.BLOCK_ACTION -> {
                val candidatePackage = protectedSettingsPolicy.findCandidatePackage(snapshot) ?: packageName
                suppressProtectedSystemSurface(candidatePackage, source, decision)
            }
        }
        return GuardActionResult.Consumed(reason = "protected_settings_policy", hasSideEffect = true)
    }

    /**
     * 在交互窗口快照里查找当前最应被压制的 protected surface 包名。
     * 输入：来源标记；输出：命中的 protected surface 包名，未命中时返回 null。
     */
    fun findProtectedInteractiveWindowPackage(source: String): String? {
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
            if (SystemSurfaceClassifier.isInstallerOrMarketSurface(candidatePackage)) {
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

    /**
     * 扫描可交互窗口中的 protected surface，并在命中时触发压制。
     * 输入：来源标记；输出：无，必要时触发压制/导航/遮蔽层动作。
     */
    fun sweepProtectedInteractiveWindows(source: String) {
        if (exitVisiblePowerSaveModeIfNeeded(source)) {
            return
        }
        if (collapseVisibleSystemPanelIfNeeded(source)) {
            return
        }

        val packageName = findProtectedInteractiveWindowPackage(source) ?: return
        val now = nowProvider()
        if (!state.shouldProcessProtectedWindowSweep(packageName, now, protectedWindowSweepCooldownMs)) {
            return
        }
        Log.w(logTag, "protected_window_sweep_detected source=$source package=$packageName")
        suppressProtectedSystemSurface(packageName, source)
    }

    /**
     * 判断给定包名是否属于 protected system surface。
     * 输入：包名；输出：是否命中 settings candidate 或 installer/market。
     */
    fun isProtectedSystemSurface(packageName: String): Boolean {
        return protectedSettingsPolicy.isCandidatePackage(packageName) ||
            SystemSurfaceClassifier.isInstallerOrMarketSurface(packageName)
    }

    /**
     * 执行 protected surface 压制逻辑并保留原有日志、调度与遮蔽层语义。
     * 输入：包名、来源标记与可选决策；输出：无，必要时安排导航与遮蔽层释放。
     */
    fun suppressProtectedSystemSurface(
        packageName: String,
        source: String,
        decision: ProtectedSettingsDecision? = null
    ) {
        if (WhitelistManager.isSelfApp(packageName)) {
            return
        }
        if (isProtectedSurfaceSuppressionAllowed()) {
            Log.d(logTag, "protected_surface_skip_allowed source=$source package=$packageName")
            return
        }

        val now = nowProvider()
        if (!state.shouldSuppressProtectedSurface(packageName, now, protectedSurfaceSuppressCooldownMs)) {
            return
        }

        blockSessionController.recordBlock(packageName, now, blockHoldDuration)
        publishLifecycleSignal("protected_fast_suppress:$packageName")
        Log.w(
            logTag,
            "protected_surface_fast_suppress source=$source package=$packageName " +
                "decision=${decision?.type ?: "legacy"} reason=${decision?.reason.orEmpty()}"
        )

        postToMain {
            if (!isProtectedTargetInteractiveNow(packageName)) {
                Log.d(logTag, "protected_overlay_skip_target_left package=$packageName")
                cancelProtectedSurfaceActions(packageName)
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
            // Start release checks after the overlay show request is actually posted,
            // so a delayed main-thread show does not consume all release windows early.
            scheduleProtectedOverlayReleaseCheck(packageName)
        }

        guardActionScheduler.cancelKey(schedulerOwnerProtectedSurface, packageName)
        protectedSurfaceNavigationBurstDelays.forEach { delayMs ->
            if (delayMs == 0L) {
                performProtectedSurfaceNavigation(packageName, source, delayMs)
            } else {
                guardActionScheduler.schedule(
                    owner = schedulerOwnerProtectedSurface,
                    key = packageName,
                    delayMs = delayMs
                ) {
                    performProtectedSurfaceNavigation(packageName, source, delayMs)
                }
            }
        }
    }

    /**
     * 构建 protected settings 页面快照。
     * 输入：事件、归一化包名、来源标记与已知窗口包集合；输出：供策略评估的页面快照。
     */
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
            Log.e(logTag, "settings_snapshot_event_source_failed: ${e.message}", e)
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
            Log.e(logTag, "settings_snapshot_root_failed: ${e.message}", e)
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
            Log.e(logTag, "settings_snapshot_windows_failed: ${e.message}", e)
            null
        } ?: return

        windowList.forEach { window ->
            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(logTag, "settings_snapshot_window_root_failed: ${e.message}", e)
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

    /**
     * 递归收集节点树文本、描述和资源标识。
     * 输入：节点、信号集合与窗口包集合；输出：无，集合被就地追加。
     */
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
                    Log.e(logTag, "settings_snapshot_child_failed: ${e.message}", e)
                    null
                }
                try {
                    collectNodeSignals(child, signals, windowPackages, depth + 1, maxTextLength, visibleOnly)
                } finally {
                    child?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "settings_snapshot_node_failed: ${e.message}", e)
        }
    }

    /**
     * 收集当前交互窗口里暴露出的包名集合。
     * 输入：无；输出：交互窗口包名集合。
     */
    private fun collectInteractiveWindowPackages(): Set<String> {
        val packages = linkedSetOf<String>()
        forEachInteractiveWindow { windowPackageName, _, _, _ ->
            if (windowPackageName.isNotEmpty()) {
                packages.add(windowPackageName)
            }
        }
        return packages
    }

    /**
     * 输出 protected settings 决策日志并应用原有的日志节流语义。
     * 输入：页面快照与策略决策；输出：无，必要时输出日志。
     */
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
        val now = nowProvider()
        if (!state.shouldLogProtectedSettingsDecision(signature, now, protectedSettingsDecisionLogCooldownMs)) {
            return
        }

        Log.w(
            logTag,
            "protected_settings_decision type=${decision.type} package=$candidatePackage " +
                "reason=${decision.reason} target=${decision.matchedTarget} " +
                "risk=${decision.matchedRiskKeywords.joinToString(",")} " +
                "action=${decision.matchedActionKeywords.joinToString(",")} source=${snapshot.source} " +
                "clicked=${snapshot.clickedText.take(160)} sample=${snapshot.text.take(240)}"
        )
    }

    /**
     * 在策略允许时释放当前由 protected settings 持有的遮蔽层。
     * 输入：页面快照与策略决策；输出：无，必要时释放遮蔽层并清理待执行动作。
     */
    private fun releaseProtectedSettingsOverlayIfAllowed(
        snapshot: SettingsPageSnapshot,
        decision: ProtectedSettingsDecision
    ) {
        if (!shouldReleaseProtectedOverlayForDecision(decision.type)) {
            return
        }
        if (!isOverlayShowing()) {
            return
        }
        val blockedPackage = readCurrentBlockedPackage()
        if (!protectedSettingsPolicy.isCandidatePackage(blockedPackage)) {
            return
        }
        if (SystemSurfaceClassifier.isAppMarketSurface(blockedPackage) && isTargetPackageActive(blockedPackage)) {
            Log.d(
                logTag,
                "protected_settings_keep_active_app_market_overlay package=$blockedPackage " +
                    "reason=${decision.reason} source=${snapshot.source}"
            )
            return
        }

        Log.d(
            logTag,
            "protected_settings_allow_release_overlay package=$blockedPackage " +
                "reason=${decision.reason} source=${snapshot.source}"
        )
        cancelPendingBlockActions("protected_settings_allowed:${decision.reason}")
        hideOverlay()
        blockSessionController.clearSession(clearPendingPackage = false)
    }

    /**
     * 执行 protected surface 导航突发动作。
     * 输入：包名、来源标记与延迟时间；输出：无，必要时记录导航日志。
     */
    private fun performProtectedSurfaceNavigation(packageName: String, source: String, delayMs: Long) {
        try {
            val targetStillInteractive = isProtectedTargetInteractiveNow(packageName)
            if (!shouldExecuteProtectedSurfaceNavigation(delayMs, targetStillInteractive)) {
                Log.d(
                    logTag,
                    "protected_surface_nav_cancel source=$source package=$packageName " +
                        "delayMs=$delayMs reason=target_left"
                )
                cancelProtectedSurfaceActions(packageName)
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
                "protected_surface_nav source=$source package=$packageName delayMs=$delayMs action=$actionName handled=$handled"
            )
        } catch (e: Exception) {
            Log.e(logTag, "protected_surface_nav_failed: ${e.message}", e)
        }
    }

    /**
     * 判断目标包是否仍占据当前活动或聚焦的可交互窗口。
     * 与包含 UsageStats 历史回退的 isTargetPackageActive 不同，此判断只用于取消已过期的导航动作。
     */
    private fun isProtectedTargetInteractiveNow(packageName: String): Boolean {
        val activePackage = windowInspectorSnapshotApi.activePackageName()
        if (isSameBasePackage(activePackage, packageName)) {
            return true
        }
        return windowInspectorSnapshotApi.interactiveWindowSnapshots().any { snapshot ->
            isSameBasePackage(snapshot.packageName, packageName) &&
                (snapshot.isActive || snapshot.isFocused)
        }
    }

    /** 取消同一 protected surface 剩余的导航突发动作。 */
    private fun cancelProtectedSurfaceActions(packageName: String) {
        guardActionScheduler.cancelKey(schedulerOwnerProtectedSurface, packageName)
    }

    /**
     * 判断当前是否允许放行 protected surface。
     * 输入：无；输出：是否应跳过压制。
     */
    private fun isProtectedSurfaceSuppressionAllowed(): Boolean {
        return try {
            isGlobalUnlockEnabled() || isSetupSettingsAccessAllowed()
        } catch (e: Exception) {
            Log.e(logTag, "read_protected_surface_allow_state_failed: ${e.message}", e)
            false
        }
    }

    /**
     * 对 protected surface 遮蔽层执行延迟释放检查。
     * 输入：包名；输出：无，必要时强制导航并释放遮蔽层。
     */
    fun scheduleProtectedOverlayReleaseCheck(packageName: String) {
        val releaseCheckDelays = longArrayOf(900L, 1600L, 2600L, 4200L)
        blockSessionController.cancelReleaseChecks(schedulerOwnerOverlayRelease)
        releaseCheckDelays.forEach { delayMillis ->
            blockSessionController.scheduleReleaseCheck(
                owner = schedulerOwnerOverlayRelease,
                key = packageName,
                delayMs = delayMillis
            ) {
                if (!isOverlayShowing()) {
                    return@scheduleReleaseCheck
                }
                if (readCurrentBlockedPackage() != packageName) {
                    return@scheduleReleaseCheck
                }
                val suppressionAllowed = isProtectedSurfaceSuppressionAllowed()
                val targetStillActive = isTargetPackageActive(packageName)
                if (shouldReleaseProtectedOverlay(targetStillActive, suppressionAllowed)) {
                    val reason = if (suppressionAllowed) "parent_allowance" else "target_left"
                    Log.d(
                        logTag,
                        "protected_overlay_auto_release package=$packageName delayMs=$delayMillis reason=$reason"
                    )
                    hideOverlay()
                    blockSessionController.clearSession()
                    return@scheduleReleaseCheck
                }

                if (delayMillis >= 2600L) {
                    Log.w(logTag, "protected_overlay_reinforce package=$packageName delayMs=$delayMillis")
                    performProtectedSurfaceNavigation(packageName, "protected_overlay_reinforce", delayMillis)
                }
                if (shouldRepeatProtectedOverlayChecks(
                        targetStillActive = targetStillActive,
                        suppressionAllowed = suppressionAllowed,
                        isFinalCheck = delayMillis == releaseCheckDelays.last()
                    )
                ) {
                    Log.w(logTag, "protected_overlay_hold package=$packageName delayMs=$delayMillis")
                    scheduleProtectedOverlayReleaseCheck(packageName)
                }
            }
        }
    }

    /**
     * 遍历当前交互窗口快照，供 protected window 扫描重用。
     * 输入：窗口消费者；输出：无，对每个窗口快照执行一次消费者。
     */
    private fun forEachInteractiveWindow(
        consumer: (packageName: String, summary: String, isActive: Boolean, isFocused: Boolean) -> Unit
    ) {
        windowInspectorSnapshotApi.interactiveWindowSnapshots().forEach { snapshot ->
            consumer(snapshot.packageName, snapshot.summary, snapshot.isActive, snapshot.isFocused)
        }
    }

    /**
     * 输出 protected window 快照日志并保留原有日志节流与 lifecycle signal。
     * 输入：来源标记、目标包名与窗口摘要列表；输出：无，必要时输出日志。
     */
    private fun logProtectedWindowSnapshot(
        source: String,
        targetPackage: String,
        windowSnapshots: List<String>
    ) {
        val now = nowProvider()
        val signature = "$targetPackage|${windowSnapshots.joinToString(";")}"
        if (!state.shouldLogProtectedWindow(signature, now, protectedWindowLogCooldownMs)) {
            return
        }

        Log.w(
            logTag,
            "protected_window_detected source=$source target=$targetPackage windows=" +
                windowSnapshots.joinToString(" || ").take(900)
        )
        publishLifecycleSignal("protected_window:$targetPackage")
    }

    /**
     * 向信号集合追加一个非空字符串。
     * 输入：目标集合与待追加值；输出：无，集合可能被追加。
     */
    private fun appendSignal(signals: MutableCollection<String>, value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            signals.add(trimmed)
        }
    }

    companion object {
        /**
         * 纯判断 helper：决定当前事件是否需要进入 protected window sweep。
         * 输入：是否为候选包、是否为 installer/market 与事件类型；输出：是否应继续 sweep。
         */
        internal fun shouldSweepProtectedWindows(
            isCandidatePackage: Boolean,
            isInstallerOrMarketPackage: Boolean,
            eventType: Int?
        ): Boolean {
            if (isCandidatePackage || isInstallerOrMarketPackage) {
                return true
            }
            return when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
                else -> false
            }
        }

        /**
         * 纯判断 helper：比较两个包名是否属于同一基包。
         * 输入：两个包名字符串；输出：去掉 `:` 后是否为同一基包。
         */
        internal fun isSameBasePackage(first: String, second: String): Boolean {
            val normalizedFirst = first.trim().substringBefore(':').lowercase()
            val normalizedSecond = second.trim().substringBefore(':').lowercase()
            return normalizedFirst.isNotEmpty() && normalizedFirst == normalizedSecond
        }

        /** A protected overlay may disappear only after the target left or a parent allowance became active. */
        internal fun shouldReleaseProtectedOverlay(
            targetStillActive: Boolean,
            suppressionAllowed: Boolean
        ): Boolean = suppressionAllowed || !targetStillActive

        /** OBSERVE is uncertainty, not permission to tear down an already active protection. */
        internal fun shouldReleaseProtectedOverlayForDecision(
            decisionType: ProtectedSettingsDecisionType
        ): Boolean = decisionType == ProtectedSettingsDecisionType.ALLOW

        /** Keep polling while an untrusted protected surface remains visible after the final check. */
        internal fun shouldRepeatProtectedOverlayChecks(
            targetStillActive: Boolean,
            suppressionAllowed: Boolean,
            isFinalCheck: Boolean
        ): Boolean = isFinalCheck && targetStillActive && !suppressionAllowed

        /** 首次 HOME 必须立即执行；后续突发动作仅在目标仍可交互时执行。 */
        internal fun shouldExecuteProtectedSurfaceNavigation(
            delayMs: Long,
            targetStillInteractive: Boolean
        ): Boolean = delayMs == 0L || targetStillInteractive
    }
}
