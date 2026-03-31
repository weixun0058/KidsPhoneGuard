package com.kidsphoneguard.service

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.database.ContentObserver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.R
import com.kidsphoneguard.ui.MainActivity
import com.kidsphoneguard.utils.PermissionManager

/**
 * 守护前台服务
 * 通过常驻通知保持应用在后台运行，防止被系统杀死
 * 并启动应用拦截服务
 */
class GuardForegroundService : Service() {

    companion object {
        private const val TAG = "GuardForegroundService"
        const val NOTIFICATION_ID = 1001
        private const val ACTION_RESTART_GUARD_SERVICE = "com.kidsphoneguard.action.RESTART_GUARD_SERVICE"
        private const val RESTART_REQUEST_CODE = 3001

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

        fun scheduleRestart(context: Context, delayMillis: Long = 1200L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val restartIntent = Intent(context, GuardForegroundService::class.java).apply {
                action = ACTION_RESTART_GUARD_SERVICE
            }
            val restartPendingIntent = PendingIntent.getService(
                context,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = SystemClock.elapsedRealtime() + delayMillis
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                restartPendingIntent
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isProtectionDegraded = false
    private val accessibilityHeartbeatTimeoutMs = 15000L
    private val usageHeartbeatTimeoutMs = 20000L
    private val accessibilityRecoveryCheckIntervalMs = 5000L
    private var lastHealthSnapshotDigest = ""
    private var lastAccessibilityEnabledSetting: Int? = null
    private var lastEnabledAccessibilityServices: String? = null
    private var lastForensicsDigest = ""
    private var lastRecoveryDigest = ""

    private val accessibilitySettingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            logAccessibilitySettingsSnapshot("settings_observer:${uri?.lastPathSegment.orEmpty()}")
            refreshProtectionHealthState()
            emitAccessibilityForensics("settings_observer:${uri?.lastPathSegment.orEmpty()}")
        }
    }

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            AppBlockerService.startService(this@GuardForegroundService)
            UsageTrackingManager.startTracking(this@GuardForegroundService)
            refreshProtectionHealthState()

            handler.postDelayed(this, 10000)
        }
    }

    private val accessibilityRecoveryRunnable = object : Runnable {
        override fun run() {
            performAccessibilityRecoveryCheck()
            handler.postDelayed(this, accessibilityRecoveryCheckIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        logAccessibilitySettingsSnapshot("foreground_onCreate_before_register")
        emitAccessibilityForensics("foreground_onCreate_before_register")
        registerAccessibilitySettingsObserver()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KidsPhoneGuard::GuardForegroundService"
        )
        wakeLock.acquire(10*60*1000L) // 10分钟

        startForeground(NOTIFICATION_ID, createNotification(false))

        AppBlockerService.startService(this)
        UsageTrackingManager.startTracking(this)
        refreshProtectionHealthState()

        handler.post(keepAliveRunnable)
        handler.post(accessibilityRecoveryRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART_GUARD_SERVICE) {
            Log.d(TAG, "Service restarted by alarm startId=$startId flags=$flags")
        }
        if (intent == null) {
            Log.d(TAG, "Service restarted by system startId=$startId flags=$flags")
        }
        logAccessibilitySettingsSnapshot("foreground_onStartCommand")
        emitAccessibilityForensics("foreground_onStartCommand")
        UsageTrackingManager.startTracking(this)
        refreshProtectionHealthState()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterAccessibilitySettingsObserver()
        logAccessibilitySettingsSnapshot("foreground_onDestroy", force = true)
        emitAccessibilityForensics("foreground_onDestroy")

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(accessibilityRecoveryRunnable)

        AppBlockerService.stopService(this)
        UsageTrackingManager.stopTracking()

        Log.d(TAG, "Service destroyed, trying to restart...")
        scheduleRestart(this, 1200L)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, restarting service...")
        scheduleRestart(this, 800L)
    }

    private fun refreshProtectionHealthState() {
        val now = System.currentTimeMillis()
        val accessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        val usagePermissionGranted = UsageTrackingManager.hasUsageStatsPermission(this)
        val accessibilityHeartbeat = GuardHealthState.getAccessibilityHeartbeat(this)
        val usageHeartbeat = GuardHealthState.getUsageHeartbeat(this)
        val accessibilityMissing = !accessibilityEnabled
        val usagePermissionMissing = !usagePermissionGranted
        val accessibilityStale = accessibilityEnabled &&
            (!GuardAccessibilityService.isServiceRunning() ||
                accessibilityHeartbeat == 0L ||
                now - accessibilityHeartbeat > accessibilityHeartbeatTimeoutMs)
        val usageStale = usagePermissionGranted &&
            (!UsageTrackingManager.isTrackingActive() ||
                usageHeartbeat == 0L ||
                now - usageHeartbeat > usageHeartbeatTimeoutMs)
        val degraded = accessibilityMissing || usagePermissionMissing || accessibilityStale || usageStale
        val healthSnapshotDigest = listOf(
            "ae=$accessibilityEnabled",
            "ap=$usagePermissionGranted",
            "ar=${GuardAccessibilityService.isServiceRunning()}",
            "ur=${UsageTrackingManager.isTrackingActive()}",
            "aHbAge=${if (accessibilityHeartbeat == 0L) -1L else now - accessibilityHeartbeat}",
            "uHbAge=${if (usageHeartbeat == 0L) -1L else now - usageHeartbeat}",
            "aMissing=$accessibilityMissing",
            "uMissing=$usagePermissionMissing",
            "aStale=$accessibilityStale",
            "uStale=$usageStale",
            "degraded=$degraded"
        ).joinToString("|")

        if (healthSnapshotDigest != lastHealthSnapshotDigest) {
            lastHealthSnapshotDigest = healthSnapshotDigest
            Log.w(TAG, "health_snapshot $healthSnapshotDigest")
        }

        if (degraded != isProtectionDegraded) {
            isProtectionDegraded = degraded
            Log.w(TAG, "degraded_state_changed degraded=$degraded")
            logAccessibilitySettingsSnapshot("degraded_state_changed", force = true)
            emitAccessibilityForensics("degraded_state_changed")
            if (degraded) {
                emitProcessTreeForensics("degraded_state_changed")
            }
            updateForegroundNotification(degraded)
        }

        if (usageStale) {
            UsageTrackingManager.startTracking(this)
        }
    }

    private fun updateForegroundNotification(degraded: Boolean) {
        if (!canPostNotifications()) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(degraded))
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerAccessibilitySettingsObserver() {
        val resolver = contentResolver
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            accessibilitySettingsObserver
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilitySettingsObserver
        )
        logAccessibilitySettingsSnapshot("observer_registered")
    }

    private fun unregisterAccessibilitySettingsObserver() {
        runCatching {
            contentResolver.unregisterContentObserver(accessibilitySettingsObserver)
        }.onFailure {
            Log.e(TAG, "unregister observer failed: ${it.message}", it)
        }
    }

    private fun logAccessibilitySettingsSnapshot(source: String, force: Boolean = false) {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (!force && enabled == lastAccessibilityEnabledSetting && enabledServices == lastEnabledAccessibilityServices) {
            return
        }
        lastAccessibilityEnabledSetting = enabled
        lastEnabledAccessibilityServices = enabledServices
        val collapsedServices = enabledServices?.replace("\n", " ")?.take(240)
        val serviceEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        Log.w(
            TAG,
            "accessibility_settings_changed source=$source accessibility_enabled=$enabled service_enabled=$serviceEnabled enabled_services=$collapsedServices"
        )
    }

    private fun emitAccessibilityForensics(source: String) {
        val now = System.currentTimeMillis()
        val serviceEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        val heartbeat = GuardHealthState.getAccessibilityHeartbeat(this)
        val heartbeatAge = if (heartbeat == 0L) -1L else now - heartbeat
        val topPackage = resolveRecentForegroundPackage(now)
        val processImportance = resolveOwnProcessImportance()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoreBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        val powerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
        val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            true
        }
        val latestSignal = GuardAccessibilityService.getLatestLifecycleSignal()
        val settingsEnabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.replace("\n", " ")?.take(240)
        val digest = listOf(
            "source=$source",
            "ae=$settingsEnabled",
            "serviceEnabled=$serviceEnabled",
            "ar=${GuardAccessibilityService.isServiceRunning()}",
            "aHbAge=$heartbeatAge",
            "procImp=$processImportance",
            "top=$topPackage",
            "ignoreBattery=$ignoreBatteryOptimizations",
            "powerSave=$powerSaveMode",
            "interactive=$interactive",
            "signal=$latestSignal",
            "enabledServices=$enabledServices"
        ).joinToString("|")
        if (digest == lastForensicsDigest) {
            return
        }
        lastForensicsDigest = digest
        Log.e(TAG, "accessibility_forensics $digest")
    }

    private fun performAccessibilityRecoveryCheck() {
        val now = System.currentTimeMillis()
        val isEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        val isRunning = GuardAccessibilityService.isServiceRunning()
        val heartbeat = GuardHealthState.getAccessibilityHeartbeat(this)
        val heartbeatAge = if (heartbeat == 0L) -1L else now - heartbeat
        val shouldRecover = !isEnabled || !isRunning || (heartbeatAge >= 0 && heartbeatAge > accessibilityHeartbeatTimeoutMs)
        val digest = "enabled=$isEnabled|running=$isRunning|heartbeatAge=$heartbeatAge|recover=$shouldRecover"
        if (digest == lastRecoveryDigest) {
            return
        }
        lastRecoveryDigest = digest
        if (!shouldRecover) {
            return
        }
        val source = if (!isEnabled) {
            "auto_recovery_guide_disabled"
        } else {
            "auto_recovery_guide_stale"
        }
        Log.w(TAG, "accessibility_recovery_check $digest")
        emitAccessibilityForensics(source)
        emitProcessTreeForensics(source)
        PermissionManager.requestAccessibilityPermission(this, forceOpenWhenEnabled = isEnabled)
    }

    private fun emitProcessTreeForensics(source: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = activityManager.runningAppProcesses
                ?.take(30)
                ?.joinToString("|") { "${it.processName}:${it.importance}" }
                .orEmpty()
            Log.e(TAG, "process_tree_forensics source=$source processes=$processes")
        } catch (e: Exception) {
            Log.e(TAG, "process_tree_forensics_failed source=$source reason=${e.message}", e)
        }
    }

    private fun resolveOwnProcessImportance(): Int {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }
                ?.importance
                ?: -1
        } catch (e: Exception) {
            Log.e(TAG, "resolveOwnProcessImportance failed: ${e.message}", e)
            -1
        }
    }

    private fun resolveRecentForegroundPackage(now: Long): String {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usageStatsManager.queryEvents(now - 12000L, now)
            val event = UsageEvents.Event()
            var latestPackage = ""
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (!isForegroundEvent) {
                    continue
                }
                val eventPackage = event.packageName ?: continue
                if (event.timeStamp >= latestTs) {
                    latestTs = event.timeStamp
                    latestPackage = eventPackage
                }
            }
            if (latestPackage.isNotEmpty()) {
                return latestPackage
            }
            "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "resolveRecentForegroundPackage failed: ${e.message}", e)
            "error:${e.javaClass.simpleName}"
        }
    }

    private fun createNotification(degraded: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (degraded) {
            getString(R.string.notification_content_degraded)
        } else {
            getString(R.string.notification_content)
        }

        return NotificationCompat.Builder(this, KidsPhoneGuardApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
