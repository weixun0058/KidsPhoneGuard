package com.kidsphoneguard.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * 应用拦截服务
 * 已禁用：功能已合并到GuardAccessibilityService中，避免冲突
 */
class AppBlockerService : Service() {

    companion object {
        fun startService(context: Context) {
            // 不再启动此服务
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 立即停止自己，不执行任何操作
        stopSelf()
        return START_NOT_STICKY
    }
}
