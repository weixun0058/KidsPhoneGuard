package com.kidsphoneguard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.R
import com.kidsphoneguard.ui.MainActivity

/**
 * 守护前台服务
 * 通过常驻通知保持应用在后台运行，防止被系统杀死
 * 并启动应用拦截服务
 */
class GuardForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001

        /**
         * 启动服务
         * @param context 上下文
         */
        fun start(context: Context) {
            val intent = Intent(context, GuardForegroundService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * 停止服务
         * @param context 上下文
         */
        fun stop(context: Context) {
            val intent = Intent(context, GuardForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wakeLock: PowerManager.WakeLock

    // 保活任务 - 定期检查服务状态
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            // 确保拦截服务在运行
            AppBlockerService.startService(this@GuardForegroundService)

            // 每10秒检查一次
            handler.postDelayed(this, 10000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 获取唤醒锁，防止CPU休眠
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KidsPhoneGuard::GuardForegroundService"
        )
        wakeLock.acquire(10*60*1000L) // 10分钟

        startForeground(NOTIFICATION_ID, createNotification())

        // 启动应用拦截服务（核心功能）
        AppBlockerService.startService(this)

        // 启动保活任务
        handler.post(keepAliveRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: 如果服务被杀死，系统会尝试重新创建服务
        // 但Intent会为null
        if (intent == null) {
            // 服务被系统重启，重新初始化
            android.util.Log.d("GuardForegroundService", "Service restarted by system")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        // 释放唤醒锁
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // 停止保活任务
        handler.removeCallbacks(keepAliveRunnable)

        // 服务被销毁时，停止拦截服务
        AppBlockerService.stopService(this)

        // 尝试重新启动服务
        android.util.Log.d("GuardForegroundService", "Service destroyed, trying to restart...")
        start(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 当用户从最近任务中移除应用时，尝试重新启动服务
        android.util.Log.d("GuardForegroundService", "Task removed, restarting service...")
        start(this)
    }

    /**
     * 创建前台服务通知
     * @return 通知对象
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, KidsPhoneGuardApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
