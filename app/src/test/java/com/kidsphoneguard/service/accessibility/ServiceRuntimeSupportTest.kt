package com.kidsphoneguard.service.accessibility

import android.content.BroadcastReceiver
import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.utils.BroadcastPermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRuntimeSupportTest {

    /**
     * 验证 onCreate 会启动 heartbeat 与 protected sweep 调度。
     * 输入：空 runtime support；输出：断言运行态、生命周期信号与调度请求。
     */
    @Test
    fun onCreateSchedulesHeartbeatAndSweep() {
        val signals = mutableListOf<String>()
        val scheduleCalls = mutableListOf<String>()
        val support = createSupport(
            setRunning = { running -> signals += "running:$running" },
            publishLifecycleSignal = { signal -> signals += signal },
            scheduleAction = { owner, key, delayMs, _ ->
                scheduleCalls += "$owner|$key|$delayMs"
            }
        )

        support.onCreate()

        assertEquals(listOf("running:true", "onCreate"), signals.take(2))
        assertEquals(
            listOf(
                "heartbeat|tick|4000",
                "protected_window_sweep|tick|180"
            ),
            scheduleCalls
        )
    }

    /**
     * 验证 event lifecycle signal 会按既有节流窗口发布。
     * 输入：两次时间相近的 accessibility event；输出：断言只发布一次 event signal。
     */
    @Test
    fun eventSignalIsThrottled() {
        var now = 2_000L
        val signals = mutableListOf<String>()
        val support = createSupport(
            publishLifecycleSignal = { signal -> signals += signal },
            nowProvider = { now }
        )
        support.publishEventSignalIfNeeded(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            eventPackage = "com.example.app"
        )
        now += 500L
        support.publishEventSignalIfNeeded(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            eventPackage = "com.example.app"
        )
        now += 2_000L
        support.publishEventSignalIfNeeded(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            eventPackage = "com.example.app"
        )

        assertEquals(
            listOf(
                "event:${AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED}:com.example.app",
                "event:${AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED}:com.example.app"
            ),
            signals
        )
    }

    /**
     * 验证 block receiver 由 runtime support 持有并能把内部广播转发给 block callback。
     * 输入：注册后的 receiver 与内部 block intent；输出：断言目标包名被转发。
     */
    @Test
    fun blockReceiverRoutesBroadcastPackage() {
        val routedPackages = mutableListOf<String>()
        val support = createSupport(
            registerBlockReceiver = {},
            handleBlockBroadcast = { packageName -> routedPackages += packageName }
        )

        support.registerBlockReceiverForServiceInit()
        support.handleBlockReceiverInput(
            action = BroadcastPermissionHelper.ACTION_BLOCK_APP,
            packageName = "com.example.app"
        )

        assertEquals(listOf("com.example.app"), routedPackages)
    }

    /**
     * 验证 event signal 节流判断仅在超过窗口后才返回 true。
     * 输入：上次时间、当前时间与节流窗口；输出：断言判断结果。
     */
    @Test
    fun shouldPublishEventSignalRespectsThrottleWindow() {
        val support = createSupport()

        assertFalse(support.shouldPublishEventSignal(lastTimestamp = 100L, now = 150L, throttleMs = 100L))
        assertTrue(support.shouldPublishEventSignal(lastTimestamp = 100L, now = 200L, throttleMs = 100L))
    }

    /**
     * 构建用于测试的 runtime support。
     * 输入：可覆盖的回调；输出：测试实例。
     */
    private fun createSupport(
        scheduleAction: (owner: String, key: String, delayMs: Long, action: () -> Unit) -> Unit =
            { _, _, _, _ -> },
        readAccessibilitySettingsSnapshot: () -> ServiceRuntimeSupport.AccessibilitySettingsSnapshot =
            {
                ServiceRuntimeSupport.AccessibilitySettingsSnapshot(
                    accessibilityEnabled = 1,
                    enabledServices = "service"
                )
            },
        touchHeartbeat: () -> Unit = {},
        clearHeartbeat: () -> Unit = {},
        setRunning: (Boolean) -> Unit = {},
        publishLifecycleSignal: (String) -> Unit = {},
        sweepProtectedInteractiveWindows: (String) -> Unit = {},
        handleBlockBroadcast: (String) -> Unit = {},
        registerBlockReceiver: (BroadcastReceiver) -> Unit = {},
        unregisterBlockReceiver: (BroadcastReceiver) -> Unit = {},
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ): ServiceRuntimeSupport {
        return ServiceRuntimeSupport(
            logTag = "ServiceRuntimeSupportTest",
            protectedWindowSweepIntervalMs = 180L,
            schedulerOwnerHeartbeat = "heartbeat",
            schedulerOwnerProtectedSweep = "protected_window_sweep",
            scheduleAction = scheduleAction,
            readAccessibilitySettingsSnapshot = readAccessibilitySettingsSnapshot,
            touchHeartbeat = touchHeartbeat,
            clearHeartbeat = clearHeartbeat,
            setRunning = setRunning,
            publishLifecycleSignal = publishLifecycleSignal,
            sweepProtectedInteractiveWindows = sweepProtectedInteractiveWindows,
            handleBlockBroadcast = handleBlockBroadcast,
            registerBlockReceiver = registerBlockReceiver,
            unregisterBlockReceiver = unregisterBlockReceiver,
            nowProvider = nowProvider,
            logDebug = {},
            logWarn = {},
            logError = { _, _ -> }
        )
    }
}
