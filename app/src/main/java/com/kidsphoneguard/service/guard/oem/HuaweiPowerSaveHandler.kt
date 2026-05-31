package com.kidsphoneguard.service.guard.oem

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.NavigationExecutor

/**
 * 负责华为/荣耀省电模式退出检测、节点点击与手势补偿。
 * 输入：事件/窗口读取能力、导航执行器、调度器与全局解锁状态；输出：统一的 `GuardActionResult` 与省电退出动作。
 */
class HuaweiPowerSaveHandler(
    private val logTag: String,
    private val navigationExecutor: NavigationExecutor,
    private val guardActionScheduler: GuardActionScheduler,
    private val readRootInActiveWindow: () -> AccessibilityNodeInfo?,
    private val readWindows: () -> List<AccessibilityWindowInfo>?,
    private val isGlobalProtectedSurfaceUnlockAllowed: () -> Boolean,
    private val displayMetricsProvider: () -> DisplayMetricsSnapshot,
    private val snapshotTextLimit: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * 暴露给手势分发的最小屏幕尺寸快照。
     * 输入：无；输出：当前屏幕宽高像素。
     */
    data class DisplayMetricsSnapshot(
        val widthPixels: Int,
        val heightPixels: Int
    )

    companion object {
        private const val POWER_SAVE_EXIT_ATTEMPT_COOLDOWN_MS = 220L
        private const val SCHEDULER_OWNER_POWER_SAVE = "power_save_exit"
        private val POWER_SAVE_EXIT_PACKAGES = setOf(
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher"
        )
        private val POWER_SAVE_EXIT_ACTIVITY_SIGNALS = setOf(
            "powersavemode.PowerSaveModeLauncher",
            "PowerSaveModeLauncher"
        )
        private val POWER_SAVE_EXIT_KEYWORDS = setOf(
            "退出",
            "退出超级省电",
            "退出超级省电模式",
            "关闭超级省电",
            "确定"
        )
        private val POWER_SAVE_EXIT_BURST_DELAYS = longArrayOf(0L, 45L, 120L, 260L, 520L)

        /**
         * 判断当前包名/类名是否命中现有省电模式活动页规则。
         * 输入：包名、类名与全局解锁状态；输出：是否需要触发省电退出。
         */
        internal fun matchesCurrentPowerSaveActivity(
            packageName: String,
            className: String,
            globalUnlockAllowed: Boolean
        ): Boolean {
            if (globalUnlockAllowed || !isPowerSaveLauncherPackage(packageName)) {
                return false
            }
            return POWER_SAVE_EXIT_ACTIVITY_SIGNALS.any { className.contains(it, ignoreCase = true) }
        }

        /**
         * 判断给定包名是否属于当前已支持的华为/荣耀省电 launcher 包。
         * 输入：包名；输出：是否命中当前包规则。
         */
        internal fun isPowerSaveLauncherPackage(packageName: String): Boolean {
            val normalized = packageName.trim().substringBefore(':').lowercase()
            return POWER_SAVE_EXIT_PACKAGES.any { normalized == it || normalized.startsWith("$it.") }
        }

        /**
         * 判断聚合后的节点信号是否包含当前支持的省电退出关键词。
         * 输入：信号文本；输出：是否命中退出信号。
         */
        internal fun containsPowerSaveExitSignal(signal: String): Boolean {
            return POWER_SAVE_EXIT_KEYWORDS.any { signal.contains(it, ignoreCase = true) } ||
                signal.contains("超级省电", ignoreCase = true)
        }

        /**
         * 计算当前包名/类名组合在 router 中应返回的结果语义。
         * 输入：包名、类名与全局解锁状态；输出：命中时返回省电退出消费结果，否则继续。
         */
        internal fun resultForCurrentActivityShape(
            packageName: String,
            className: String,
            globalUnlockAllowed: Boolean
        ): GuardActionResult {
            return if (matchesCurrentPowerSaveActivity(packageName, className, globalUnlockAllowed)) {
                GuardActionResult.Consumed(reason = "power_save_exit", hasSideEffect = true)
            } else {
                GuardActionResult.Continue
            }
        }

        /**
         * 根据可见 root 的包名和聚合信号判断是否应触发省电退出。
         * 输入：包名、聚合信号与全局解锁状态；输出：是否命中当前可见省电页。
         */
        internal fun shouldExitVisiblePowerSave(
            packageName: String,
            signal: String,
            globalUnlockAllowed: Boolean
        ): Boolean {
            if (globalUnlockAllowed || !isPowerSaveLauncherPackage(packageName)) {
                return false
            }
            return containsPowerSaveExitSignal(signal)
        }
    }

    private var lastPowerSaveExitAttemptTime: Long = 0L

    /**
     * 处理一条省电模式相关事件并返回 router 可消费结果。
     * 输入：原始事件与来源标记；输出：命中时消费路由，否则继续。
     */
    fun handle(event: AccessibilityEvent, source: String): GuardActionResult {
        val packageName = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        return handle(packageName, className, source) { event.source }
    }

    /**
     * 处理已提取包名/类名的省电模式判断，便于在 JVM 测试中验证结果语义。
     * 输入：包名、类名、来源与延迟获取的事件节点；输出：命中时消费路由，否则继续。
     */
    internal fun handle(
        packageName: String,
        className: String,
        source: String,
        sourceNodeProvider: (() -> AccessibilityNodeInfo?)? = null
    ): GuardActionResult {
        val result = resultForCurrentActivityShape(
            packageName = packageName,
            className = className,
            globalUnlockAllowed = isGlobalProtectedSurfaceUnlockAllowed()
        )
        if (result == GuardActionResult.Continue) {
            return GuardActionResult.Continue
        }
        return if (triggerPowerSaveExit(source, sourceNodeProvider?.invoke())) {
            result
        } else {
            GuardActionResult.Continue
        }
    }

    /**
     * 在当前可见 root 上尝试识别并触发省电模式退出。
     * 输入：来源标记；输出：是否触发了退出尝试。
     */
    fun exitVisiblePowerSaveModeIfNeeded(source: String): Boolean {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return false
        }
        val root = try {
            readRootInActiveWindow()
        } catch (e: Exception) {
            Log.e(logTag, "power_save_exit_root_failed source=$source reason=${e.message}", e)
            null
        }

        try {
            val packageName = root?.packageName?.toString().orEmpty()
            val signal = collectPowerSaveExitSignal(root)
            if (!shouldExitVisiblePowerSave(
                    packageName = packageName,
                    signal = signal,
                    globalUnlockAllowed = isGlobalProtectedSurfaceUnlockAllowed()
                )
            ) {
                return false
            }
        } finally {
            root?.recycle()
        }

        return triggerPowerSaveExit(source, null)
    }

    /**
     * 执行一次省电退出尝试，并安排当前 burst 手势补偿。
     * 输入：来源标记与可选事件节点；输出：命中省电路径时返回 true。
     */
    private fun triggerPowerSaveExit(source: String, sourceNode: AccessibilityNodeInfo?): Boolean {
        val now = nowProvider()
        if (now - lastPowerSaveExitAttemptTime < POWER_SAVE_EXIT_ATTEMPT_COOLDOWN_MS) {
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
        Log.w(logTag, "power_save_exit_attempt source=$source clickedNode=$clicked")
        return true
    }

    /**
     * 在事件节点、活动 root 与窗口 root 中查找可点击的省电退出入口。
     * 输入：可选节点与来源标记；输出：是否成功执行点击。
     */
    private fun clickPowerSaveExitNode(node: AccessibilityNodeInfo?, source: String): Boolean {
        if (node != null && clickPowerSaveExitNodeInTree(node, source)) {
            return true
        }

        val root = try {
            readRootInActiveWindow()
        } catch (e: Exception) {
            Log.e(logTag, "power_save_exit_click_root_failed source=$source reason=${e.message}", e)
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
            readWindows()
        } catch (e: Exception) {
            Log.e(logTag, "power_save_exit_click_windows_failed source=$source reason=${e.message}", e)
            null
        }
        windowList?.forEach { window ->
            val windowRoot = try {
                window.root
            } catch (e: Exception) {
                Log.e(logTag, "power_save_exit_click_window_root_failed source=$source reason=${e.message}", e)
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

    /**
     * 在节点树中递归查找省电退出关键词，并尝试点击最近的可点击祖先。
     * 输入：当前节点、来源标记与递归深度；输出：是否成功执行点击。
     */
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
        if (POWER_SAVE_EXIT_KEYWORDS.any { signal.contains(it, ignoreCase = true) }) {
            val clickTarget = findClickableAncestor(node)
            if (clickTarget != null) {
                try {
                    val handled = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.w(
                        logTag,
                        "power_save_exit_click_node source=$source handled=$handled signal=${signal.take(120)}"
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
                Log.e(logTag, "power_save_exit_child_failed source=$source reason=${e.message}", e)
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

    /**
     * 安排一次省电退出 burst，保留当前延迟与手势/节点补偿顺序。
     * 输入：来源标记；输出：无。
     */
    private fun schedulePowerSaveExitBurst(source: String) {
        POWER_SAVE_EXIT_BURST_DELAYS.forEach { delay ->
            if (delay == 0L) {
                tapPowerSaveExitArea(source, delay)
            } else {
                guardActionScheduler.schedule(
                    owner = SCHEDULER_OWNER_POWER_SAVE,
                    key = source,
                    delayMs = delay
                ) {
                    tapPowerSaveExitArea(source, delay)
                    clickPowerSaveExitNode(null, "$source:burst:$delay")
                }
            }
        }
    }

    /**
     * 点击当前约定的省电退出热点区域。
     * 输入：来源标记与延迟毫秒；输出：手势是否被系统接受。
     */
    private fun tapPowerSaveExitArea(source: String, delayMs: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val metrics = displayMetricsProvider()
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.92f, height * 0.055f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 20L))
            .build()
        val dispatched = navigationExecutor.dispatchGesture(gesture)
        if (!dispatched) {
            Log.e(logTag, "power_save_exit_gesture_failed source=$source delayMs=$delayMs")
        }
        Log.w(logTag, "power_save_exit_gesture source=$source delayMs=$delayMs dispatched=$dispatched")
        return dispatched
    }

    /**
     * 汇总节点树中的文本、描述与 viewId，供省电退出信号判断使用。
     * 输入：根节点；输出：拼接后的信号文本。
     */
    private fun collectPowerSaveExitSignal(node: AccessibilityNodeInfo?): String {
        val signals = mutableListOf<String>()
        collectPowerSaveNodeSignals(
            node = node,
            signals = signals,
            maxTextLength = snapshotTextLimit,
            visibleOnly = false
        )
        return signals.joinToString(" ")
    }

    /**
     * 递归采集节点树中的文本/描述/viewId 信号，并在同一层完成 recycle。
     * 输入：节点、信号列表、深度、最大文本长度与可见性要求；输出：无。
     */
    private fun collectPowerSaveNodeSignals(
        node: AccessibilityNodeInfo?,
        signals: MutableList<String>,
        depth: Int = 0,
        maxTextLength: Int,
        visibleOnly: Boolean
    ) {
        if (node == null || depth > 40 || signals.joinToString(" ").length >= maxTextLength) {
            return
        }

        try {
            if (!visibleOnly || node.isVisibleToUser) {
                val text = node.text?.toString().orEmpty().trim()
                if (text.isNotEmpty()) {
                    signals.add(text)
                }
                val description = node.contentDescription?.toString().orEmpty().trim()
                if (description.isNotEmpty()) {
                    signals.add(description)
                }
                val viewId = node.viewIdResourceName.orEmpty().trim()
                if (viewId.isNotEmpty()) {
                    signals.add(viewId)
                }
            }

            for (index in 0 until node.childCount) {
                val child = try {
                    node.getChild(index)
                } catch (e: Exception) {
                    Log.e(logTag, "power_save_exit_child_signal_failed: ${e.message}", e)
                    null
                }
                try {
                    collectPowerSaveNodeSignals(
                        node = child,
                        signals = signals,
                        depth = depth + 1,
                        maxTextLength = maxTextLength,
                        visibleOnly = visibleOnly
                    )
                } finally {
                    child?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "power_save_exit_signal_node_failed: ${e.message}", e)
        }
    }

    /**
     * 查找距离当前节点最近的可点击祖先。
     * 输入：起始节点；输出：可点击祖先副本，调用方负责 recycle。
     */
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
}
