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
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 应用拦截服务
 * 通过无障碍服务执行返回操作阻止被锁定的应用
 * 适用于所有Android系统（HarmonyOS、MIUI、ColorOS等）
 */
class AppBlockerService : Service() {

    companion object {
        const val CHANNEL_ID = "app_blocker_channel"
        const val NOTIFICATION_ID = 1003

        fun startService(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager
    private lateinit var settingsManager: SettingsManager

    private var isRunning = false
    private var lastBlockedPackage: String = ""
    private var lastBlockTime: Long = 0

    // 监控间隔
    private val MONITOR_INTERVAL = 400L

    // 系统包名（不会被拦截）
    private val systemPackages = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher",
        "com.samsung.android.launcher",
        "com.coloros.launcher",
        "com.funtouch.launcher",
        "com.android.settings",
        "com.kidsphoneguard"
    )

    // 浏览器包名关键词（需要特别处理）
    private val browserKeywords = setOf(
        "browser",
        "chrome",
        "firefox",
        "edge",
        "opera",
        "safari",
        "webview",
        "ucmobile",
        "quark",
        "baidu.searchbox"
    )

    // 监控任务
    private val blockerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            checkAndBlockApps()
            handler.postDelayed(this, MONITOR_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as com.kidsphoneguard.KidsPhoneGuardApp
        appRuleRepository = app.appRuleRepository
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        settingsManager = SettingsManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        isRunning = true
        handler.post(blockerRunnable)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(blockerRunnable)
        serviceScope.cancel()
    }

    /**
     * 检查并拦截应用
     */
    private fun checkAndBlockApps() {
        if (!powerManager.isInteractive) return

        val topPackage = getTopPackageName() ?: return

        // 跳过系统应用
        if (systemPackages.any { topPackage.contains(it) }) {
            return
        }

        serviceScope.launch {
            // 检查全局锁定状态（在主线程中读取）
            val isGlobalLocked = settingsManager.isGlobalLockEnabled()

            android.util.Log.d("AppBlocker", "Checking package: $topPackage, globalLocked: $isGlobalLocked")

            val rule = appRuleRepository.getRuleByPackageName(topPackage)

            // 判断是否拦截
            val shouldBlock = when {
                // 全局锁定开启 - 拦截所有非系统应用
                isGlobalLocked -> true
                // 按规则判断
                rule?.ruleType == RuleType.BLOCK -> true
                rule?.ruleType == RuleType.LIMIT -> checkLimitConditions(rule)
                else -> false
            }

            android.util.Log.d("AppBlocker", "Should block $topPackage: $shouldBlock")

            if (shouldBlock) {
                val currentTime = System.currentTimeMillis()

                // 判断是否是浏览器应用（需要更严格的拦截）
                val isBrowser = browserKeywords.any { topPackage.lowercase().contains(it) }

                // 浏览器应用使用更短的拦截间隔（50ms）
                val blockInterval = if (isBrowser) 50L else 200L

                // 防止过于频繁拦截
                if (topPackage == lastBlockedPackage && currentTime - lastBlockTime < blockInterval) {
                    return@launch
                }

                lastBlockedPackage = topPackage
                lastBlockTime = currentTime

                android.util.Log.d("AppBlocker", "Blocking package: $topPackage, isBrowser: $isBrowser")

                // 发送广播让无障碍服务执行返回操作
                sendBroadcast(Intent("ACTION_BLOCK_APP").apply {
                    putExtra("package_name", topPackage)
                })

                // 显示覆盖层
                val appName = rule?.appName ?: topPackage
                OverlayService.showOverlay(this@AppBlockerService, topPackage, appName)
            }
        }
    }

    /**
     * 获取顶层应用包名
     */
    private fun getTopPackageName(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 500
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
                "应用拦截",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "阻止被锁定的应用启动"
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
