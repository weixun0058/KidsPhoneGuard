package com.kidsphoneguard.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import com.kidsphoneguard.R
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.WhitelistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 应用强制停止服务
 * 在检测到被锁定的应用时，立即强制停止其进程
 * 阻止游戏等应用启动
 */
class AppKillerService : Service() {

    companion object {
        const val CHANNEL_ID = "app_killer_channel"
        const val NOTIFICATION_ID = 1002

        fun startService(context: Context) {
            val intent = Intent(context, AppKillerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppKillerService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var activityManager: ActivityManager
    private lateinit var powerManager: PowerManager

    private var isRunning = false
    private var lastKilledPackage: String = ""
    private var lastKillTime: Long = 0

    // 监控间隔 - 更频繁
    private val MONITOR_INTERVAL = 50L

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
    private val killerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            checkAndKillApps()
            handler.postDelayed(this, MONITOR_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as com.kidsphoneguard.KidsPhoneGuardApp
        appRuleRepository = app.appRuleRepository
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        isRunning = true
        handler.post(killerRunnable)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(killerRunnable)
        serviceScope.cancel()
    }

    /**
     * 检查并杀死被锁定的应用
     */
    private fun checkAndKillApps() {
        if (!powerManager.isInteractive) return

        // 获取最近使用的应用事件
        val currentTime = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(currentTime - 1000, currentTime)

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // 只处理移动到前台的事件
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val packageName = event.packageName

                // 关键修复：使用白名单检查替代系统包名检查
                if (WhitelistManager.isInWhitelist(packageName)) continue

                serviceScope.launch {
                    checkAndKillPackage(packageName)
                }
            }
        }

        // 同时检查当前顶层应用
        val topPackage = getTopPackageName()
        if (topPackage != null && !WhitelistManager.isInWhitelist(topPackage)) {
            serviceScope.launch {
                checkAndKillPackage(topPackage)
            }
        }
    }

    /**
     * 检查包名并决定是否杀死
     */
    private suspend fun checkAndKillPackage(packageName: String) {
        val rule = appRuleRepository.getRuleByPackageName(packageName)
        val shouldBlock = when (rule?.ruleType) {
            RuleType.BLOCK -> true
            RuleType.LIMIT -> checkLimitConditions(rule)
            else -> false
        }

        if (shouldBlock) {
            // 防止过于频繁地杀死同一个应用
            val currentTime = System.currentTimeMillis()
            if (packageName == lastKilledPackage && currentTime - lastKillTime < 500) {
                return
            }

            lastKilledPackage = packageName
            lastKillTime = currentTime

            // 强制停止应用
            forceStopApp(packageName)

            // 显示覆盖层提示
            OverlayService.showOverlay(this, packageName, rule?.appName ?: "")

            // 延迟再次确认
            handler.postDelayed({
                forceStopApp(packageName)
                if (OverlayService.getCurrentBlockedPackage() == packageName) {
                    OverlayService.showOverlay(this, packageName, rule?.appName ?: "")
                }
            }, 100)

            handler.postDelayed({
                forceStopApp(packageName)
            }, 300)
        }
    }

    /**
     * 强制停止应用 - MIUI兼容版
     */
    private fun forceStopApp(packageName: String) {
        try {
            // 方法1: 使用ActivityManager强制停止任务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activityManager.appTasks?.forEach { task ->
                    try {
                        task.finishAndRemoveTask()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 方法2: 获取运行中的应用并强制停止
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val runningApps = activityManager.getRunningAppProcesses()
                runningApps?.forEach { processInfo ->
                    if (processInfo.pkgList.contains(packageName)) {
                        try {
                            Process.killProcess(processInfo.pid)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // 方法3: 杀死后台进程（MIUI可能限制此操作）
            try {
                activityManager.killBackgroundProcesses(packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 方法4: 使用shell命令（需要root，可能失败）
            try {
                Runtime.getRuntime().exec("am force-stop $packageName")
            } catch (e: Exception) {
                // 忽略，可能没有root权限
            }

            // 方法5: MIUI专用 - 使用反射调用forceStopPackage
            try {
                val method = activityManager.javaClass.getMethod("forceStopPackage", String::class.java)
                method.invoke(activityManager, packageName)
            } catch (e: Exception) {
                // MIUI可能不支持此方法
            }

            // 方法6: 通过返回桌面来打断应用
            val intent = Intent().apply {
                action = "android.intent.action.MAIN"
                addCategory("android.intent.category.HOME")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)

            // 方法7: 发送自定义广播通知系统停止应用
            sendBroadcast(Intent("ACTION_KILL_APP").apply {
                putExtra("package_name", packageName)
            })

        } catch (e: Exception) {
            e.printStackTrace()
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
                "应用强制停止",
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
            .setContentText("正在阻止被锁定的应用")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
