package com.kidsphoneguard.service.guard

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kidsphoneguard.engine.settingsprotection.ProtectedSettingsPolicy
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.NavigationExecutor

/**
 * 负责系统面板识别、信号收集与折叠动作，避免 protected surface 逻辑与系统面板逻辑继续混在 service 中。
 * 输入：无障碍事件、窗口/根节点读取能力与系统面板相关依赖；输出：统一的 `GuardActionResult` 或可见面板折叠结果。
 */
class SystemSurfaceGuard(
    private val logTag: String,
    private val protectedSettingsPolicy: ProtectedSettingsPolicy,
    private val navigationExecutor: NavigationExecutor,
    private val guardActionScheduler: GuardActionScheduler,
    private val readRootInActiveWindow: () -> AccessibilityNodeInfo?,
    private val readWindows: () -> List<AccessibilityWindowInfo>?,
    private val isGlobalProtectedSurfaceUnlockAllowed: () -> Boolean,
    private val systemPanelPackages: Set<String>,
    private val systemPanelSnapshotTextLimit: Int,
    private val systemPanelCollapseCooldownMs: Long,
    private val systemPanelCollapseReinforceDelayMs: Long,
    private val schedulerOwnerSystemPanel: String,
    private val dismissNotificationShadeAction: Int,
    private val backAction: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var lastSystemPanelCollapseTime: Long = 0L

    /**
     * 处理 router 调用的系统面板折叠分支。
     * 输入：事件、归一化包名与来源标记；输出：命中时消费路由，否则继续。
     */
    fun collapseSystemPanelIfNeeded(
        event: AccessibilityEvent,
        packageName: String,
        source: String
    ): GuardActionResult {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return GuardActionResult.Continue
        }
        if (!shouldInspectSystemPanel(event, packageName)) {
            return GuardActionResult.Continue
        }
        val panelSignal = buildSystemPanelSignal(event)
        if (!protectedSettingsPolicy.containsGuardianDisruptiveCapabilitySignal(panelSignal)) {
            return GuardActionResult.Continue
        }
        return toCollapseResult(collapseSystemPanelWithSignal(packageName, source, panelSignal))
    }

    /**
     * 处理可见系统面板的主动收起逻辑，供 protected surface 扫描阶段临时调用。
     * 输入：来源标记；输出：是否触发了系统面板折叠。
     */
    fun collapseVisibleSystemPanelIfNeeded(source: String): Boolean {
        if (isGlobalProtectedSurfaceUnlockAllowed()) {
            return false
        }
        val panelSignal = buildVisibleSystemPanelSignal()
        if (!protectedSettingsPolicy.containsGuardianDisruptiveCapabilitySignal(panelSignal)) {
            return false
        }
        return collapseSystemPanelWithSignal(
            packageName = SYSTEM_UI_PACKAGE,
            source = source,
            panelSignal = panelSignal
        )
    }

    /**
     * 判断给定包名是否属于系统面板相关包。
     * 输入：包名；输出：是否命中系统面板包集合。
     */
    fun isSystemPanelPackage(packageName: String): Boolean {
        val normalized = packageName.trim().substringBefore(':').lowercase()
        return systemPanelPackages.any { normalized == it || normalized.startsWith("$it.") }
    }

    /**
     * 统一把布尔 handled 结果映射成 router 可消费的 `GuardActionResult`。
     * 输入：是否已处理系统面板；输出：命中时返回 `Consumed`，否则返回 `Continue`。
     */
    internal fun toCollapseResult(handled: Boolean): GuardActionResult {
        return collapseResultForHandled(handled)
    }

    /**
     * 判断当前事件是否值得进入系统面板信号收集。
     * 输入：事件与归一化包名；输出：是否应继续检查系统面板。
     */
    private fun shouldInspectSystemPanel(event: AccessibilityEvent, packageName: String): Boolean {
        val eventPackageName = event.packageName?.toString().orEmpty()
        return isSystemPanelPackage(packageName) || isSystemPanelPackage(eventPackageName)
    }

    /**
     * 构建由当前事件触发的系统面板文本信号。
     * 输入：无障碍事件；输出：用于 disruptive capability 判断的信号文本。
     */
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
            Log.e(logTag, "system_panel_event_source_failed: ${e.message}", e)
            null
        }
        try {
            if (eventBelongsToSystemPanel) {
                collectNodeSignals(
                    node = eventSource,
                    signals = signals,
                    windowPackages = windowPackages,
                    maxTextLength = systemPanelSnapshotTextLimit,
                    visibleOnly = false
                )
            }
        } finally {
            eventSource?.recycle()
        }

        val root = try {
            readRootInActiveWindow()
        } catch (e: Exception) {
            Log.e(logTag, "system_panel_root_failed: ${e.message}", e)
            null
        }
        try {
            val rootPackageName = root?.packageName?.toString().orEmpty()
            if (isSystemPanelPackage(rootPackageName)) {
                collectNodeSignals(
                    node = root,
                    signals = signals,
                    windowPackages = windowPackages,
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

    /**
     * 构建当前可见系统面板的文本信号。
     * 输入：无；输出：用于可见系统面板折叠判断的信号文本。
     */
    private fun buildVisibleSystemPanelSignal(): String {
        val signals = mutableListOf<String>()
        val windowPackages = mutableSetOf<String>()

        val root = try {
            readRootInActiveWindow()
        } catch (e: Exception) {
            Log.e(logTag, "visible_system_panel_root_failed: ${e.message}", e)
            null
        }
        try {
            val rootPackageName = root?.packageName?.toString().orEmpty()
            if (isSystemPanelPackage(rootPackageName)) {
                collectNodeSignals(
                    node = root,
                    signals = signals,
                    windowPackages = windowPackages,
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

    /**
     * 执行系统面板折叠并保留原有 cooldown / reinforce 调度语义。
     * 输入：包名、来源标记与面板信号；输出：是否已触发系统面板折叠。
     */
    private fun collapseSystemPanelWithSignal(
        packageName: String,
        source: String,
        panelSignal: String
    ): Boolean {
        val now = nowProvider()
        if (now - lastSystemPanelCollapseTime < systemPanelCollapseCooldownMs) {
            return true
        }
        lastSystemPanelCollapseTime = now

        val handled = performSystemPanelCollapseAction(source)
        Log.w(
            logTag,
            "system_panel_collapse source=$source package=$packageName action=DISMISS_SHADE_OR_BACK handled=$handled " +
                "signal=${panelSignal.take(240)}"
        )
        guardActionScheduler.schedule(
            owner = schedulerOwnerSystemPanel,
            key = packageName,
            delayMs = systemPanelCollapseReinforceDelayMs
        ) {
            try {
                val secondHandled = performSystemPanelCollapseAction(source)
                Log.w(
                    logTag,
                    "system_panel_collapse_reinforce source=$source package=$packageName " +
                        "action=DISMISS_SHADE_OR_BACK handled=$secondHandled"
                )
            } catch (e: Exception) {
                Log.e(logTag, "system_panel_collapse_reinforce_failed source=$source reason=${e.message}", e)
            }
        }
        return true
    }

    /**
     * 执行实际的系统面板折叠动作。
     * 输入：来源标记；输出：是否成功执行 dismiss shade 或 back。
     */
    private fun performSystemPanelCollapseAction(source: String): Boolean {
        return try {
            if (navigationExecutor.performGlobalAction(dismissNotificationShadeAction)) {
                true
            } else {
                navigationExecutor.performGlobalAction(backAction)
            }
        } catch (e: Exception) {
            Log.e(logTag, "system_panel_collapse_failed source=$source reason=${e.message}", e)
            false
        }
    }

    /**
     * 收集交互窗口里的系统面板节点信号。
     * 输入：信号集合与窗口包集合；输出：无，集合被就地追加。
     */
    private fun collectSystemPanelWindowNodeSignals(
        signals: MutableList<String>,
        windowPackages: MutableSet<String>
    ) {
        val windowList = try {
            readWindows()
        } catch (e: Exception) {
            Log.e(logTag, "system_panel_windows_failed: ${e.message}", e)
            null
        } ?: return

        windowList.forEach { window ->
            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(logTag, "system_panel_window_root_failed: ${e.message}", e)
                null
            }
            try {
                val windowPackageName = root?.packageName?.toString().orEmpty()
                appendSignal(windowPackages, windowPackageName)
                if (isSystemPanelPackage(windowPackageName)) {
                    collectNodeSignals(
                        node = root,
                        signals = signals,
                        windowPackages = windowPackages,
                        maxTextLength = systemPanelSnapshotTextLimit,
                        visibleOnly = false
                    )
                }
            } finally {
                root?.recycle()
            }
        }
    }

    /**
     * 深度优先收集系统面板节点树的文本与资源标识。
     * 输入：节点、信号集合、窗口包集合与遍历参数；输出：无，集合被就地追加。
     */
    private fun collectNodeSignals(
        node: AccessibilityNodeInfo?,
        signals: MutableList<String>,
        windowPackages: MutableSet<String>,
        depth: Int = 0,
        maxTextLength: Int,
        visibleOnly: Boolean
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
                    Log.e(logTag, "system_panel_child_failed: ${e.message}", e)
                    null
                }
                try {
                    collectNodeSignals(
                        node = child,
                        signals = signals,
                        windowPackages = windowPackages,
                        depth = depth + 1,
                        maxTextLength = maxTextLength,
                        visibleOnly = visibleOnly
                    )
                } finally {
                    child?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "system_panel_node_failed: ${e.message}", e)
        }
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
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /**
         * 纯结果 helper：把 handled 布尔值转换为系统面板路由结果。
         * 输入：是否已处理系统面板；输出：对应的 `GuardActionResult`。
         */
        internal fun collapseResultForHandled(handled: Boolean): GuardActionResult {
            return if (handled) {
                GuardActionResult.Consumed(reason = "system_panel_collapse", hasSideEffect = true)
            } else {
                GuardActionResult.Continue
            }
        }
    }
}
