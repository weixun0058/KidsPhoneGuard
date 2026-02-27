package com.kidsphoneguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kidsphoneguard.service.UsageTrackingManager

/**
 * 屏幕状态广播接收器
 * 监听屏幕亮灭事件，控制使用时长统计的启停
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private var isTrackingStarted = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                // 屏幕亮起，开始统计
                if (!isTrackingStarted) {
                    UsageTrackingManager.startTracking(context)
                    isTrackingStarted = true
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                // 屏幕熄灭，停止统计
                if (isTrackingStarted) {
                    UsageTrackingManager.stopTracking()
                    isTrackingStarted = false
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 开机完成，启动前台服务
                com.kidsphoneguard.service.GuardForegroundService.start(context)
            }
        }
    }
}
