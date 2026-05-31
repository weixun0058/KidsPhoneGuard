package com.kidsphoneguard.service.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.utils.BroadcastPermissionHelper

/**
 * 统一承接 service 级 runtime 调度、快照日志与内部广播收发。
 * 输入：生命周期/调度/日志回调与 Android 事件；输出：runtime 副作用，不产生新的业务决策。
 */
class ServiceRuntimeSupport(
    private val logTag: String,
    private val protectedWindowSweepIntervalMs: Long,
    private val schedulerOwnerHeartbeat: String,
    private val schedulerOwnerProtectedSweep: String,
    private val heartbeatIntervalMs: Long = 4000L,
    private val eventSignalThrottleMs: Long = 2000L,
    private val scheduleAction: (owner: String, key: String, delayMs: Long, action: () -> Unit) -> Unit,
    private val readAccessibilitySettingsSnapshot: () -> AccessibilitySettingsSnapshot,
    private val touchHeartbeat: () -> Unit,
    private val clearHeartbeat: () -> Unit,
    private val setRunning: (Boolean) -> Unit,
    private val publishLifecycleSignal: (String) -> Unit,
    private val sweepProtectedInteractiveWindows: (String) -> Unit,
    private val handleBlockBroadcast: (String) -> Unit,
    private val registerBlockReceiver: (BroadcastReceiver) -> Unit,
    private val unregisterBlockReceiver: (BroadcastReceiver) -> Unit,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val logDebug: (String) -> Unit = { message -> Log.d(logTag, message) },
    private val logWarn: (String) -> Unit = { message -> Log.w(logTag, message) },
    private val logError: (String, Throwable?) -> Unit = { message, throwable ->
        if (throwable != null) {
            Log.e(logTag, message, throwable)
        } else {
            Log.e(logTag, message)
        }
    }
) {

    /**
     * 保存一次无障碍设置快照，供日志输出使用。
     * 输入：系统设置读取结果；输出：仅作为日志结构化数据。
     */
    data class AccessibilitySettingsSnapshot(
        val accessibilityEnabled: Int,
        val enabledServices: String?
    )

    private var lastEventSignalTimestamp = 0L
    private var blockReceiverRegistered = false

    private val blockAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleBlockReceiverInput(
                action = intent?.action,
                packageName = intent?.getStringExtra("package_name")
            )
        }
    }

    /**
     * 处理 service onCreate 对应的 runtime 副作用。
     * 输入：无；输出：无。
     */
    fun onCreate() {
        setRunning(true)
        publishLifecycleSignal("onCreate")
        touchHeartbeat()
        logAccessibilitySettingsSnapshot("service_onCreate")
        scheduleHeartbeatTick()
        scheduleProtectedSweepTick()
    }

    /**
     * 处理 service connected 对应的 runtime 副作用。
     * 输入：无；输出：无。
     */
    fun onServiceConnected() {
        setRunning(true)
        publishLifecycleSignal("onServiceConnected")
        touchHeartbeat()
        logDebug("Service connected")
        logAccessibilitySettingsSnapshot("service_connected")
    }

    /**
     * 处理一次 accessibility event 的 runtime 信号更新。
     * 输入：Android accessibility event；输出：无。
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        touchHeartbeat()
        publishEventSignalIfNeeded(event)
    }

    /**
     * 处理 onInterrupt 的 runtime 副作用。
     * 输入：无；输出：无。
     */
    fun onInterrupt() {
        setRunning(false)
        clearHeartbeat()
        publishLifecycleSignal("onInterrupt")
        logDebug("Service interrupted")
        logAccessibilitySettingsSnapshot("service_onInterrupt")
    }

    /**
     * 处理 onUnbind 的 runtime 副作用。
     * 输入：intent action 文本；输出：无。
     */
    fun onUnbind(intentAction: String) {
        setRunning(false)
        clearHeartbeat()
        publishLifecycleSignal("onUnbind:$intentAction")
        logWarn("Service onUnbind intentAction=$intentAction")
        logAccessibilitySettingsSnapshot("service_onUnbind")
    }

    /**
     * 处理 onRebind 的 runtime 副作用。
     * 输入：intent action 文本；输出：无。
     */
    fun onRebind(intentAction: String) {
        publishLifecycleSignal("onRebind:$intentAction")
        logWarn("Service onRebind intentAction=$intentAction")
        logAccessibilitySettingsSnapshot("service_onRebind")
    }

    /**
     * 处理 onDestroy 的 runtime 副作用。
     * 输入：无；输出：无。
     */
    fun onDestroy() {
        setRunning(false)
        publishLifecycleSignal("onDestroy")
        clearHeartbeat()
        unregisterBlockReceiverIfNeeded()
        logDebug("Service destroyed")
        logAccessibilitySettingsSnapshot("service_onDestroy")
    }

    /**
     * 在延迟初始化路径中注册内部 block receiver。
     * 输入：无；输出：无。
     */
    fun registerBlockReceiverForServiceInit() {
        if (blockReceiverRegistered) {
            return
        }
        try {
            registerBlockReceiver(blockAppReceiver)
            blockReceiverRegistered = true
        } catch (e: Exception) {
            logError("注册blockAppReceiver失败: ${e.message}", null)
        }
    }

    /**
     * 根据节流规则发布 event 级生命周期信号。
     * 输入：当前 accessibility event；输出：无。
     */
    fun publishEventSignalIfNeeded(event: AccessibilityEvent) {
        publishEventSignalIfNeeded(
            eventType = event.eventType,
            eventPackage = event.packageName?.toString().orEmpty()
        )
    }

    /**
     * 根据节流规则发布 event 级生命周期信号。
     * 输入：事件类型与包名；输出：无。
     */
    internal fun publishEventSignalIfNeeded(eventType: Int, eventPackage: String) {
        val now = nowProvider()
        if (!shouldPublishEventSignal(lastEventSignalTimestamp, now, eventSignalThrottleMs)) {
            return
        }
        lastEventSignalTimestamp = now
        publishLifecycleSignal("event:$eventType:$eventPackage")
    }

    /**
     * 处理内部 block receiver 输入，便于 JVM 单测不依赖 Android Intent。
     * 输入：广播 action 与目标包名；输出：无。
     */
    internal fun handleBlockReceiverInput(action: String?, packageName: String?) {
        if (action != BroadcastPermissionHelper.ACTION_BLOCK_APP || packageName == null) {
            return
        }
        handleBlockBroadcast(packageName)
    }

    /**
     * 输出一次无障碍设置快照日志。
     * 输入：日志来源；输出：无。
     */
    fun logAccessibilitySettingsSnapshot(source: String) {
        val snapshot = readAccessibilitySettingsSnapshot()
        logWarn(
            "accessibility_service_snapshot source=$source accessibility_enabled=${snapshot.accessibilityEnabled} " +
                "enabled_services=${snapshot.enabledServices}"
        )
    }

    /**
     * 判断本次事件是否应发布新的 event lifecycle signal。
     * 输入：上次发布时间、当前时间与节流窗口；输出：是否允许发布。
     */
    internal fun shouldPublishEventSignal(lastTimestamp: Long, now: Long, throttleMs: Long): Boolean {
        return now - lastTimestamp >= throttleMs
    }

    /**
     * 安排下一次 heartbeat tick。
     * 输入：无；输出：无。
     */
    private fun scheduleHeartbeatTick() {
        scheduleAction(schedulerOwnerHeartbeat, "tick", heartbeatIntervalMs) {
            touchHeartbeat()
            scheduleHeartbeatTick()
        }
    }

    /**
     * 安排下一次 protected window sweep tick。
     * 输入：无；输出：无。
     */
    private fun scheduleProtectedSweepTick() {
        scheduleAction(schedulerOwnerProtectedSweep, "tick", protectedWindowSweepIntervalMs) {
            try {
                sweepProtectedInteractiveWindows("periodic_window_sweep")
            } catch (e: Exception) {
                logError("protected_window_sweep_failed: ${e.message}", e)
            } finally {
                scheduleProtectedSweepTick()
            }
        }
    }

    /**
     * 在 destroy 阶段注销内部 receiver。
     * 输入：无；输出：无。
     */
    private fun unregisterBlockReceiverIfNeeded() {
        if (!blockReceiverRegistered) {
            return
        }
        unregisterBlockReceiver(blockAppReceiver)
        blockReceiverRegistered = false
    }
}
