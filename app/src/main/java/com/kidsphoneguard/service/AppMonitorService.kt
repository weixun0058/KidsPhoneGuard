package com.kidsphoneguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kidsphoneguard.R
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 应用监控服务
 * 前台服务持续监控顶层应用，强制拦截被限制的应用
 * 针对游戏等特殊启动模式优化
 */
class AppMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "app_monitor_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, AppMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppMonitorService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager

    private var lastBlockedPackage: String = ""
    private var isRunning = false

    // 监控间隔
    private val MONITOR_INTERVAL = 100L

    // 系统包名
    private val systemPackages = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.samsung.android.launcher",
        "com.coloros.launcher",
        "com.funtouch.launcher",
        "com.android.settings",
        "com.kidsphoneguard"
    )

    // 监控任务
    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            checkTopApp()
            handler.postDelayed(this, MONITOR_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as com.kidsphoneguard.KidsPhoneGuardApp
        appRuleRepository = app.appRuleRepository
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        isRunning = true
        handler.post(monitorRunnable)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        serviceScope.cancel()

        // 如果被销毁，尝试重新启动
        if (lastBlockedPackage.isNotEmpty()) {
            startService(this)
        }
    }

    /**
     * 检查顶层应用
     */
    private fun checkTopApp() {
        if (!powerManager.isInteractive) return

        val topPackage = getTopPackageName() ?: return

        // 跳过系统应用
        if (systemPackages.any { topPackage.contains(it) }) {
            if (lastBlockedPackage.isNotEmpty()) {
                // 如果到了系统界面，清空锁定状态
                lastBlockedPackage = ""
                OverlayService.hideOverlay(this)
            }
            return
        }

        serviceScope.launch {
            val rule = appRuleRepository.getRuleByPackageName(topPackage)
            val shouldBlock = when (rule?.ruleType) {
                RuleType.BLOCK -> true
                RuleType.LIMIT -> checkLimitConditions(rule)
                else -> false
            }

            if (shouldBlock) {
                if (lastBlockedPackage != topPackage || !OverlayService.isOverlayShowing()) {
                    lastBlockedPackage = topPackage
                    // 强制显示覆盖层
                    forceShowOverlay(topPackage, rule?.appName ?: "")
                }
            } else {
                if (lastBlockedPackage == topPackage) {
                    lastBlockedPackage = ""
                    OverlayService.hideOverlay(this@AppMonitorService)
                }
            }
        }
    }

    /**
     * 强制显示覆盖层
     */
    private fun forceShowOverlay(packageName: String, appName: String) {
        // 多次尝试显示
        OverlayService.showOverlay(this, packageName, appName)

        handler.postDelayed({
            if (lastBlockedPackage == packageName) {
                OverlayService.showOverlay(this, packageName, appName)
            }
        }, 50)

        handler.postDelayed({
            if (lastBlockedPackage == packageName) {
                OverlayService.showOverlay(this, packageName, appName)
            }
        }, 150)

        handler.postDelayed({
            if (lastBlockedPackage == packageName) {
                OverlayService.showOverlay(this, packageName, appName)
            }
        }, 300)
    }

    /**
     * 获取顶层应用包名
     */
    private fun getTopPackageName(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 检查限时条件
     */
    private suspend fun checkLimitConditions(rule: com.kidsphoneguard.data.model.AppRule): Boolean {
        if (rule.isGlobalLocked) {
            return true
        }

        if (rule.blockedTimeWindows.isNotEmpty()) {
            if (isInBlockedTimeWindow(rule.blockedTimeWindows)) {
                return true
            }
        }

        return false
    }

    /**
     * 检查当前是否在禁用时段内
     */
    private fun isInBlockedTimeWindow(timeWindows: String): Boolean {
        val now = LocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val windows = timeWindows.split(",")
        for (window in windows) {
            val parts = window.trim().split("-")
            if (parts.size != 2) continue

            try {
                val startTime = LocalTime.parse(parts[0].trim(), timeFormatter)
                val endTime = LocalTime.parse(parts[1].trim(), timeFormatter)

                val inWindow = if (startTime.isAfter(endTime)) {
                    now.isAfter(startTime) || now.isBefore(endTime)
                } else {
                    now.isAfter(startTime) && now.isBefore(endTime)
                }

                if (inWindow) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return false
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监控应用使用情况"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, com.kidsphoneguard.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("儿童手机守护")
            .setContentText("正在保护您的孩子")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
