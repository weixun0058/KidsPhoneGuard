package com.kidsphoneguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kidsphoneguard.service.GuardForegroundService
import com.kidsphoneguard.service.UsageTrackingManager

/**
 * 屏幕状态广播接收器
 * 监听屏幕亮灭事件，控制使用时长统计的启停
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
        private var isTrackingStarted = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                if (!isTrackingStarted) {
                    UsageTrackingManager.startTracking(context)
                    isTrackingStarted = true
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                if (isTrackingStarted) {
                    UsageTrackingManager.stopTracking()
                    isTrackingStarted = false
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            GuardForegroundService.ACTION_GUARD_WATCHDOG -> {
                Log.w(TAG, "restart_trigger action=${intent.action}")
                GuardForegroundService.start(context)
                GuardForegroundService.scheduleWatchdog(context)
            }
        }
    }
}
