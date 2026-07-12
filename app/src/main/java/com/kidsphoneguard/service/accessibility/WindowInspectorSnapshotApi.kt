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
    companion object {
        private const val INITIAL_ACTIVITY_LOOKBACK_MS = 90_000L
        private const val ACTIVITY_QUERY_OVERLAP_MS = 1_000L
    }

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

    data class ForegroundActivitySnapshot(
        val packageName: String,
        val className: String
    )

    private var lastForegroundActivityQueryEndTime = 0L
    private var cachedForegroundActivity: ForegroundActivitySnapshot? = null

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
     * 增量读取最近恢复到前台的 Activity。
     * 首次最多回看 90 秒，之后只读取上次查询后的新增事件（带 1 秒重叠），
     * 避免在高频无障碍事件中反复扫描大段使用记录。
     */
    fun recentForegroundActivity(): ForegroundActivitySnapshot? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = if (lastForegroundActivityQueryEndTime <= 0L) {
                endTime - INITIAL_ACTIVITY_LOOKBACK_MS
            } else {
                (lastForegroundActivityQueryEndTime - ACTIVITY_QUERY_OVERLAP_MS).coerceAtLeast(0L)
            }
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var latestTime = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isResumed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                if (!isResumed || event.timeStamp < latestTime) {
                    continue
                }
                latestTime = event.timeStamp
                cachedForegroundActivity = ForegroundActivitySnapshot(
                    packageName = event.packageName.orEmpty(),
                    className = event.className.orEmpty()
                )
            }
            lastForegroundActivityQueryEndTime = endTime
            cachedForegroundActivity
        } catch (e: Exception) {
            Log.e(logTag, "window_snapshot_recent_foreground_activity_failed: ${e.message}", e)
            cachedForegroundActivity
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

}
