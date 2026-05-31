package com.kidsphoneguard.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.kidsphoneguard.utils.WhitelistManager

/**
 * 提供只读的窗口与前台应用快照，避免业务层直接持有可变节点对象。
 * 输入：AccessibilityService、UsageStatsManager 与日志标签；输出：只读快照查询能力。
 */
class WindowInspectorSnapshotApi(
    private val service: AccessibilityService,
    private val usageStatsManager: UsageStatsManager,
    private val logTag: String
) {

    /**
     * 描述事件源节点的只读快照。
     * 输入：事件源节点的基础属性；输出：供敏感动作检测使用的不可变值对象。
     */
    data class EventSourceSnapshot(
        val signal: String,
        val className: String,
        val isClickable: Boolean,
        val isLongClickable: Boolean,
        val isEnabled: Boolean,
        val bounds: String,
        val boundsLeft: Int,
        val boundsTop: Int,
        val boundsRight: Int,
        val boundsBottom: Int
    )

    /**
     * 描述一个可交互窗口的只读快照。
     * 输入：窗口基础属性；输出：供路由与日志使用的不可变值对象。
     */
    data class InteractiveWindowSnapshot(
        val packageName: String,
        val summary: String,
        val isActive: Boolean,
        val isFocused: Boolean
    )

    /**
     * 读取当前活动根窗口的包名。
     * 输入：无；输出：活动窗口包名，读取失败时返回空字符串。
     */
    fun activePackageName(): String {
        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.e(logTag, "window_snapshot_active_root_failed: ${e.message}", e)
            null
        }

        return try {
            root?.packageName?.toString().orEmpty()
        } finally {
            root?.recycle()
        }
    }

    /**
     * 读取最近一次进入前台的应用包名。
     * 输入：无；输出：最近前台包名，读取失败时返回 null。
     */
    fun recentTopPackageName(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 4000L
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
            Log.e(logTag, "window_snapshot_recent_top_failed: ${e.message}", e)
            null
        }
    }

    /**
     * 读取当前可交互窗口列表的只读快照。
     * 输入：无；输出：窗口快照列表，读取失败时返回空列表。
     */
    fun interactiveWindowSnapshots(): List<InteractiveWindowSnapshot> {
        val windowList = try {
            service.windows
        } catch (e: Exception) {
            Log.e(logTag, "window_snapshot_windows_failed: ${e.message}", e)
            null
        } ?: return emptyList()

        return windowList.map { window ->
            val bounds = Rect()
            try {
                window.getBoundsInScreen(bounds)
            } catch (e: Exception) {
                Log.e(logTag, "window_snapshot_bounds_failed: ${e.message}", e)
            }

            val root = try {
                window.root
            } catch (e: Exception) {
                Log.e(logTag, "window_snapshot_root_failed: ${e.message}", e)
                null
            }
            val packageName = try {
                root?.packageName?.toString().orEmpty()
            } finally {
                root?.recycle()
            }

            InteractiveWindowSnapshot(
                packageName = packageName,
                summary = buildString {
                    append("id=").append(window.id)
                    append(",type=").append(window.type)
                    append(",active=").append(window.isActive)
                    append(",focused=").append(window.isFocused)
                    append(",pkg=").append(packageName.ifEmpty { "unknown" })
                    append(",bounds=").append(bounds.flattenToString())
                },
                isActive = window.isActive,
                isFocused = window.isFocused
            )
        }
    }

    /**
     * 读取事件本身的文本信号。
     * 输入：无障碍事件；输出：事件文本、描述与类名拼接后的信号字符串。
     */
    fun eventSignal(event: android.view.accessibility.AccessibilityEvent): String {
        val eventText = event.text.joinToString("|") { it?.toString().orEmpty() }
        val contentDescription = event.contentDescription?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        return listOf(eventText, contentDescription, className).joinToString("|")
    }

    /**
     * 读取事件源的只读属性快照。
     * 输入：无障碍事件与错误日志标记；输出：事件源快照，缺失或失败时返回 null。
     */
    fun eventSourceSnapshot(
        event: android.view.accessibility.AccessibilityEvent,
        errorMarker: String
    ): EventSourceSnapshot? {
        val source = event.source ?: return null
        return try {
            val bounds = Rect()
            try {
                source.getBoundsInScreen(bounds)
            } catch (e: Exception) {
                Log.e(logTag, "$errorMarker bounds_failed: ${e.message}", e)
            }
            EventSourceSnapshot(
                signal = listOf(
                    source.text?.toString().orEmpty(),
                    source.contentDescription?.toString().orEmpty(),
                    source.className?.toString().orEmpty(),
                    source.viewIdResourceName.orEmpty()
                ).joinToString("|"),
                className = source.className?.toString().orEmpty(),
                isClickable = source.isClickable,
                isLongClickable = source.isLongClickable,
                isEnabled = source.isEnabled,
                bounds = bounds.flattenToString(),
                boundsLeft = bounds.left,
                boundsTop = bounds.top,
                boundsRight = bounds.right,
                boundsBottom = bounds.bottom
            )
        } catch (e: Exception) {
            Log.e(logTag, "$errorMarker: ${e.message}", e)
            null
        } finally {
            source.recycle()
        }
    }

    /**
     * 判断事件源信号是否包含关键字。
     * 输入：无障碍事件、关键字集合与错误日志标记；输出：是否命中关键字。
     */
    fun eventSourceMatchesKeywords(
        event: android.view.accessibility.AccessibilityEvent,
        keywords: Set<String>,
        errorMarker: String
    ): Boolean {
        val source = eventSourceSnapshot(event, errorMarker) ?: return false
        return keywords.any { source.signal.contains(it, ignoreCase = true) }
    }

    /**
     * 判断事件源节点树是否命中关键字。
     * 输入：无障碍事件与关键字集合；输出：是否在事件源节点树中找到文本匹配。
     */
    fun eventSourceTreeMatchesKeywords(
        event: android.view.accessibility.AccessibilityEvent,
        keywords: Set<String>
    ): Boolean {
        return matchesKeywordsInTree(event.source, keywords)
    }

    /**
     * 判断当前活动根窗口节点树是否命中关键字。
     * 输入：关键字集合；输出：是否在活动根窗口中找到文本匹配。
     */
    fun activeRootMatchesKeywords(keywords: Set<String>): Boolean {
        return matchesKeywordsInTree(
            try {
                service.rootInActiveWindow
            } catch (e: Exception) {
                null
            },
            keywords
        )
    }

    /**
     * 在单棵节点树中搜索关键字文本并负责节点回收。
     * 输入：根节点与关键字集合；输出：是否命中任一关键字。
     */
    private fun matchesKeywordsInTree(
        root: android.view.accessibility.AccessibilityNodeInfo?,
        keywords: Set<String>
    ): Boolean {
        root ?: return false
        return try {
            keywords.any { keyword ->
                val nodes = try {
                    root.findAccessibilityNodeInfosByText(keyword)
                } catch (e: Exception) {
                    Log.e(logTag, "sensitive_node_search_failed keyword=$keyword error=${e.message}", e)
                    emptyList()
                }
                val matched = nodes.isNotEmpty()
                nodes.forEach { it.recycle() }
                if (matched) {
                    Log.w(logTag, "sensitive_action_node_match keyword=$keyword")
                }
                matched
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * 检测事件源子树是否包含弹出菜单结构特征。
     * 输入：无障碍事件；输出：是否检测到同时包含多个菜单项的结构。
     */
    fun eventSourceHasLauncherMenuStructure(
        event: android.view.accessibility.AccessibilityEvent
    ): Boolean {
        val source = event.source ?: return false
        return try {
            val menuKeywords = setOf("卸载", "应用信息", "分享", "移除", "详情")
            val matchedKeywords = menuKeywords.filter { keyword ->
                val nodes = try {
                    source.findAccessibilityNodeInfosByText(keyword)
                } catch (e: Exception) {
                    emptyList()
                }
                val matched = nodes.isNotEmpty()
                nodes.forEach { it.recycle() }
                matched
            }
            val hasStructure = matchedKeywords.size >= 2
            if (hasStructure) {
                Log.w(logTag, "launcher_menu_structure_detected keywords=$matchedKeywords")
            }
            hasStructure
        } finally {
            source.recycle()
        }
    }

    /**
     * 检测当前活动根窗口是否包含弹出菜单结构特征。
     * 输入：无；输出：是否检测到同时包含多个菜单项的结构。
     */
    fun activeRootHasLauncherMenuStructure(): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.e(logTag, "active_root_menu_structure_failed: ${e.message}", e)
            null
        } ?: return false

        return try {
            val menuKeywords = setOf("卸载", "应用信息", "分享", "移除", "详情")
            val matchedKeywords = menuKeywords.filter { keyword ->
                val nodes = try {
                    root.findAccessibilityNodeInfosByText(keyword)
                } catch (e: Exception) {
                    emptyList()
                }
                val matched = nodes.isNotEmpty()
                nodes.forEach { it.recycle() }
                matched
            }
            val hasStructure = matchedKeywords.size >= 2
            if (hasStructure) {
                Log.w(logTag, "active_root_menu_structure_detected keywords=$matchedKeywords")
            }
            hasStructure
        } finally {
            root.recycle()
        }
    }
}
