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
import android.content.BroadcastReceiver
import android.database.ContentObserver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.R
import com.kidsphoneguard.engine.LockDecisionEngine
import com.kidsphoneguard.receiver.ScreenStateReceiver
import com.kidsphoneguard.ui.MainActivity
import com.kidsphoneguard.utils.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

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
        const val ACTION_GUARD_WATCHDOG = "com.kidsphoneguard.action.GUARD_WATCHDOG"
        private const val OBSERVER_PACKAGE_NAME = "com.kidsphoneguard.observer"
        private const val OBSERVER_RECEIVE_PERMISSION = "com.kidsphoneguard.observer.permission.RECEIVE_GUARD_STATUS"
        private const val ACTION_OBSERVER_GUARD_STATUS = "com.kidsphoneguard.observer.action.GUARD_STATUS"
        private const val RESTART_REQUEST_CODE = 3001
        private const val WATCHDOG_REQUEST_CODE = 3002
        private const val WATCHDOG_INTERVAL_MS = 10 * 60 * 1000L
        private const val FORENSICS_PREFS_NAME = "guard_forensics"
        private const val KEY_LAST_PERSIST_AT = "last_persist_at"
        private const val KEY_LAST_PERSIST_EVENT = "last_persist_event"
        private val FORENSICS_FILE_LOCK = Any()

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

        fun scheduleWatchdog(context: Context, delayMillis: Long = WATCHDOG_INTERVAL_MS) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val watchdogIntent = Intent(context, ScreenStateReceiver::class.java).apply {
                action = ACTION_GUARD_WATCHDOG
                setPackage(context.packageName)
            }
            val watchdogPendingIntent = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                watchdogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = SystemClock.elapsedRealtime() + delayMillis
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                watchdogPendingIntent
            )
        }

        fun recordReceiverSignal(context: Context, source: String, action: String) {
            appendForensicsLine(
                context,
                "receiver_signal",
                "source=$source|action=$action"
            )
        }

        private fun appendForensicsLine(context: Context, event: String, payload: String): String? {
            return runCatching {
                synchronized(FORENSICS_FILE_LOCK) {
                    val appContext = context.applicationContext
                    val prefs = appContext.getSharedPreferences(FORENSICS_PREFS_NAME, Context.MODE_PRIVATE)
                    val rootDir = appContext.getExternalFilesDir("forensics") ?: File(appContext.filesDir, "forensics")
                    if (!rootDir.exists()) {
                        rootDir.mkdirs()
                    }
                    val logFile = File(rootDir, "accessibility_forensics.log")
                    if (logFile.exists() && logFile.length() > 2 * 1024 * 1024) {
                        val backupFile = File(rootDir, "accessibility_forensics.prev.log")
                        if (backupFile.exists()) {
                            backupFile.delete()
                        }
                        logFile.renameTo(backupFile)
                    }
                    val now = System.currentTimeMillis()
                    val line = "$now|$event|$payload\n"
                    logFile.appendText(line)
                    prefs.edit()
                        .putLong(KEY_LAST_PERSIST_AT, now)
                        .putString(KEY_LAST_PERSIST_EVENT, event)
                        .apply()
                    logFile.absolutePath
                }
            }.onFailure {
                Log.e(TAG, "persist_forensics_failed event=$event reason=${it.message}", it)
            }.getOrNull()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isProtectionDegraded = false
    private val accessibilityHeartbeatTimeoutMs = 15000L
    private val usageHeartbeatTimeoutMs = 20000L
    private val accessibilityRecoveryCheckIntervalMs = 5000L
    private val observerBridgeRefreshIntervalMs = 15000L
    private var lastHealthSnapshotDigest = ""
    private var lastObserverBridgeEmitAt = 0L
    private var lastAccessibilityEnabledSetting: Int? = null
    private var lastEnabledAccessibilityServices: String? = null
    private var lastForensicsDigest = ""
    private var lastRecoveryDigest = ""
    private var lastPolicyDigest = ""
    private var wasAccessibilityEnabled = true  // 跟踪恢复事件
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lockDecisionCheckId = 0L
    private var lockDecisionEngine: LockDecisionEngine? = null
    private var lastForensicsFilePath: String? = null
    private val forensicsPrefs by lazy {
        getSharedPreferences(FORENSICS_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val trackedSystemPackages = setOf(
        "com.android.settings",
        "com.huawei.systemmanager",
        "com.huawei.powergenie",
        "com.huawei.security",
        "com.huawei.iaware"
    )

    /** 屏幕亮起广播接收器：亮屏时检查是否需要显示锁定遮罩 */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action.orEmpty()
            val packageNameFromIntent = intent.data?.schemeSpecificPart.orEmpty()
            when (action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "screen_event: ${intent.action}")
                    DegradedLockManager.onScreenOn(context)
                    // 亮屏后立即触发恢复检查
                    handler.postDelayed({ performAccessibilityRecoveryCheck() }, 1000)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "screen_event: ${intent.action}")
                    persistForensicsLine("screen_event", "action=$action")
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    Log.w(TAG, "power_event action=$action")
                    persistForensicsLine("power_event", "action=$action")
                    emitRuntimePolicySnapshot("broadcast:$action")
                    emitAccessibilityForensics("broadcast:$action")
                }
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_RESTARTED -> {
                    if (shouldTrackPackageEvent(packageNameFromIntent)) {
                        Log.w(TAG, "package_event action=$action package=$packageNameFromIntent")
                        persistForensicsLine("package_event", "action=$action|package=$packageNameFromIntent")
                        emitAccessibilityForensics("broadcast:$action:$packageNameFromIntent")
                        if (packageNameFromIntent == packageName &&
                            action == Intent.ACTION_PACKAGE_REPLACED
                        ) {
                            emitInstallStateForensics("broadcast:$action")
                        }
                    }
                }
            }
        }
    }

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
        emitMonitorGapIfNeeded("foreground_onCreate")
        persistForensicsLine("service_lifecycle", "event=onCreate")
        logAccessibilitySettingsSnapshot("foreground_onCreate_before_register")
        emitAccessibilityForensics("foreground_onCreate_before_register")
        emitInstallStateForensics("foreground_onCreate_before_register")
        emitRuntimePolicySnapshot("foreground_onCreate_before_register")
        registerAccessibilitySettingsObserver()
        registerScreenReceiver()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KidsPhoneGuard::GuardForegroundService"
        )
        wakeLock.acquire(10*60*1000L) // 10分钟

        startForeground(NOTIFICATION_ID, createNotification(false))

        AppBlockerService.startService(this)
        UsageTrackingManager.startTracking(this)
        scheduleWatchdog(this, 60_000L)
        wasAccessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        refreshProtectionHealthState()
        emitObserverBridgeSnapshot("foreground_onCreate")

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
        emitInstallStateForensics("foreground_onStartCommand")
        UsageTrackingManager.startTracking(this)
        scheduleWatchdog(this)
        refreshProtectionHealthState()
        emitObserverBridgeSnapshot("foreground_onStartCommand")

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        emitObserverBridgeSnapshot("foreground_onDestroy")
        super.onDestroy()
        persistForensicsLine("service_lifecycle", "event=onDestroy")
        unregisterAccessibilitySettingsObserver()
        unregisterScreenReceiver()
        logAccessibilitySettingsSnapshot("foreground_onDestroy", force = true)
        emitAccessibilityForensics("foreground_onDestroy")

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(accessibilityRecoveryRunnable)
        serviceScope.cancel()

        // 清理锁定遮罩
        DegradedLockManager.dismissLockScreen(this)

        AppBlockerService.stopService(this)
        UsageTrackingManager.stopTracking()

        Log.d(TAG, "Service destroyed, trying to restart...")
        scheduleRestart(this, 1200L)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        persistForensicsLine("service_lifecycle", "event=onTaskRemoved")
        emitObserverBridgeSnapshot("foreground_onTaskRemoved")
        Log.d(TAG, "Task removed, restarting service...")
        scheduleRestart(this, 800L)
    }

    private fun emitMonitorGapIfNeeded(source: String) {
        val lastPersistAt = forensicsPrefs.getLong(KEY_LAST_PERSIST_AT, 0L)
        if (lastPersistAt <= 0L) {
            return
        }
        val gapMs = System.currentTimeMillis() - lastPersistAt
        if (gapMs < 45_000L) {
            return
        }
        val lastEvent = forensicsPrefs.getString(KEY_LAST_PERSIST_EVENT, "unknown").orEmpty()
        persistForensicsLine(
            "monitor_gap_detected",
            "source=$source|gapMs=$gapMs|lastEvent=$lastEvent"
        )
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

        val healthSnapshotChanged = healthSnapshotDigest != lastHealthSnapshotDigest
        if (healthSnapshotChanged) {
            lastHealthSnapshotDigest = healthSnapshotDigest
            Log.w(TAG, "health_snapshot $healthSnapshotDigest")
            persistForensicsLine("health_snapshot", healthSnapshotDigest)
        }
        if (shouldEmitObserverBridge(now, healthSnapshotChanged)) {
            emitObserverBridgeSnapshot(
                if (healthSnapshotChanged) "health_snapshot" else "health_heartbeat"
            )
            lastObserverBridgeEmitAt = now
        }

        if (degraded != isProtectionDegraded) {
            isProtectionDegraded = degraded
            Log.w(TAG, "degraded_state_changed degraded=$degraded")
            persistForensicsLine("degraded_state_changed", "degraded=$degraded")
            logAccessibilitySettingsSnapshot("degraded_state_changed", force = true)
            emitAccessibilityForensics("degraded_state_changed")
            if (degraded) {
                emitProcessTreeForensics("degraded_state_changed")
            }
            updateForegroundNotification(degraded)
        }

        // ★ 核心：检测无障碍恢复事件 → 自动解除锁定
        val currentAccessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        if (currentAccessibilityEnabled && !wasAccessibilityEnabled) {
            onAccessibilityRestored()
        }
        wasAccessibilityEnabled = currentAccessibilityEnabled

        // ★ 核心：无障碍掉权 + 没有锁定 → 显示锁定遮罩
        refreshDegradedLockVisibility(currentAccessibilityEnabled)

        if (usageStale) {
            UsageTrackingManager.startTracking(this)
        }
    }

    /**
     * 决定是否需要向 observer 补发状态广播，避免 observer 丢失本地状态后长时间拿不到主应用心跳。
     */
    private fun shouldEmitObserverBridge(now: Long, healthSnapshotChanged: Boolean): Boolean {
        return healthSnapshotChanged ||
            lastObserverBridgeEmitAt == 0L ||
            now - lastObserverBridgeEmitAt >= observerBridgeRefreshIntervalMs
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
        val previousEnabled = lastAccessibilityEnabledSetting
        val previousServices = lastEnabledAccessibilityServices
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
        persistForensicsLine(
            "accessibility_settings_changed",
            "source=$source|accessibility_enabled=$enabled|service_enabled=$serviceEnabled|enabled_services=$collapsedServices"
        )
        emitObserverBridgeSnapshot("accessibility_settings_changed:$source")
        val ownServiceToken = "$packageName/.service.GuardAccessibilityService"
        val ownServicePreviouslyEnabled = previousServices?.contains(ownServiceToken) == true
        val ownServiceNowEnabled = enabledServices?.contains(ownServiceToken) == true
        val dropped = previousEnabled == 1 && enabled == 0
        val serviceRemoved = ownServicePreviouslyEnabled && !ownServiceNowEnabled
        if (dropped || serviceRemoved) {
            persistForensicsLine(
                "drop_detected",
                "source=$source|dropped=$dropped|service_removed=$serviceRemoved|previous_enabled=$previousEnabled|current_enabled=$enabled"
            )
            emitRuntimePolicySnapshot("drop_detected:$source")
            emitInstallStateForensics("drop_detected:$source")
            emitAccessibilityForensics("drop_detected:$source")
        }
    }

    private fun emitObserverBridgeSnapshot(source: String) {
        val now = System.currentTimeMillis()
        val accessibilityEnabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        val serviceEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
        val accessibilityRunning = GuardAccessibilityService.isServiceRunning()
        val usagePermissionGranted = UsageTrackingManager.hasUsageStatsPermission(this)
        val usageRunning = UsageTrackingManager.isTrackingActive()
        val accessibilityHeartbeat = GuardHealthState.getAccessibilityHeartbeat(this)
        val usageHeartbeat = GuardHealthState.getUsageHeartbeat(this)
        val accessibilityHeartbeatAge = if (accessibilityHeartbeat == 0L) -1L else now - accessibilityHeartbeat
        val usageHeartbeatAge = if (usageHeartbeat == 0L) -1L else now - usageHeartbeat
        val accessibilityStale = serviceEnabled &&
            (!accessibilityRunning ||
                accessibilityHeartbeatAge < 0L ||
                accessibilityHeartbeatAge > accessibilityHeartbeatTimeoutMs)
        val usageStale = usagePermissionGranted &&
            (!usageRunning ||
                usageHeartbeatAge < 0L ||
                usageHeartbeatAge > usageHeartbeatTimeoutMs)
        val degraded = !serviceEnabled || !usagePermissionGranted || accessibilityStale || usageStale
        val intent = Intent(ACTION_OBSERVER_GUARD_STATUS).apply {
            setPackage(OBSERVER_PACKAGE_NAME)
            putExtra("source", source)
            putExtra("eventAt", now)
            putExtra("accessibilityEnabled", accessibilityEnabled)
            putExtra("serviceEnabled", serviceEnabled)
            putExtra("accessibilityRunning", accessibilityRunning)
            putExtra("usagePermissionGranted", usagePermissionGranted)
            putExtra("usageRunning", usageRunning)
            putExtra("accessibilityHeartbeatAge", accessibilityHeartbeatAge)
            putExtra("usageHeartbeatAge", usageHeartbeatAge)
            putExtra("degraded", degraded)
        }
        runCatching {
            sendBroadcast(intent, OBSERVER_RECEIVE_PERMISSION)
        }.onFailure {
            Log.d(TAG, "observer_bridge_failed source=$source reason=${it.message}")
        }
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
        persistForensicsLine("accessibility_forensics", digest)
        emitRuntimePolicySnapshot(source)
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
        persistForensicsLine("accessibility_recovery_check", "$digest|source=$source")
        emitAccessibilityForensics(source)
        emitProcessTreeForensics(source)

        // ★ 优化：屏幕关闭时不做无效的引导弹出
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "screen_off_skip_recovery_guide")
            return
        }

        // ★ 掉权后：显示锁定遮罩（替代无效的设置页引导）
        if (!isEnabled) {
            refreshDegradedLockVisibility(false)
        }
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

    private fun refreshDegradedLockVisibility(accessibilityEnabled: Boolean) {
        if (accessibilityEnabled) {
            lockDecisionCheckId += 1L
            DegradedLockManager.dismissLockScreen(this)
            return
        }
        val topPackage = resolveRecentForegroundPackage(System.currentTimeMillis())
        if (
            topPackage == "unknown" ||
            topPackage.startsWith("error:") ||
            topPackage == packageName ||
            topPackage == "com.android.systemui"
        ) {
            DegradedLockManager.dismissLockScreen(this)
            return
        }
        val checkId = lockDecisionCheckId + 1L
        lockDecisionCheckId = checkId
        val engine = getLockDecisionEngine()
        serviceScope.launch(Dispatchers.IO) {
            val shouldBlock = try {
                engine.getBlockDecision(topPackage).shouldBlock
            } catch (e: Exception) {
                Log.e(TAG, "degraded_lock_decision_failed package=$topPackage reason=${e.message}", e)
                false
            }
            handler.post {
                if (checkId != lockDecisionCheckId) {
                    return@post
                }
                if (PermissionManager.isAccessibilityServiceEnabled(this@GuardForegroundService)) {
                    DegradedLockManager.dismissLockScreen(this@GuardForegroundService)
                    return@post
                }
                if (shouldBlock) {
                    DegradedLockManager.showLockScreen(this@GuardForegroundService)
                } else {
                    DegradedLockManager.dismissLockScreen(this@GuardForegroundService)
                }
            }
        }
    }

    private fun getLockDecisionEngine(): LockDecisionEngine {
        val cached = lockDecisionEngine
        if (cached != null) {
            return cached
        }
        val engine = LockDecisionEngine.getInstance(applicationContext)
        lockDecisionEngine = engine
        return engine
    }

    /** 无障碍权限恢复时调用：解除锁定 + 提示 */
    private fun onAccessibilityRestored() {
        Log.w(TAG, "accessibility_restored: dismissing lock screen")
        DegradedLockManager.dismissLockScreen(this)
        lastRecoveryDigest = ""  // 重置以便下次循环重新评估
        try {
            handler.post {
                Toast.makeText(this, "✅ 安全保护已恢复", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "toast_failed: ${e.message}")
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_RESTARTED)
            addDataScheme("package")
        }
        registerReceiver(screenOnReceiver, filter)
    }

    private fun shouldTrackPackageEvent(targetPackage: String): Boolean {
        if (targetPackage.isBlank()) {
            return false
        }
        if (targetPackage == packageName || targetPackage.startsWith("$packageName.")) {
            return true
        }
        return trackedSystemPackages.any { tracked ->
            targetPackage == tracked || targetPackage.startsWith("$tracked.")
        }
    }

    private fun emitInstallStateForensics(source: String) {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val firstInstallTime = packageInfo.firstInstallTime
        val lastUpdateTime = packageInfo.lastUpdateTime
        val previousVersionCode = forensicsPrefs.getLong("previous_version_code", -1L)
        val previousLastUpdateTime = forensicsPrefs.getLong("previous_last_update_time", -1L)
        val changedSinceLastBootCheck = previousLastUpdateTime > 0L && previousLastUpdateTime != lastUpdateTime
        val digest = listOf(
            "source=$source",
            "versionCode=$versionCode",
            "firstInstallTime=$firstInstallTime",
            "lastUpdateTime=$lastUpdateTime",
            "prevVersionCode=$previousVersionCode",
            "prevLastUpdateTime=$previousLastUpdateTime",
            "changed=$changedSinceLastBootCheck"
        ).joinToString("|")
        Log.w(TAG, "install_state_forensics $digest")
        persistForensicsLine("install_state_forensics", digest)
        forensicsPrefs.edit()
            .putLong("previous_version_code", versionCode)
            .putLong("previous_last_update_time", lastUpdateTime)
            .apply()
    }

    private fun emitRuntimePolicySnapshot(source: String) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            true
        }
        val powerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
        val deviceIdleMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false
        }
        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activityManager.isBackgroundRestricted
        } else {
            false
        }
        val standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            usageStatsManager.appStandbyBucket
        } else {
            -1
        }
        val ignoreBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        val globalLowPower = Settings.Global.getInt(contentResolver, "low_power", 0)
        val digest = listOf(
            "source=$source",
            "interactive=$interactive",
            "powerSave=$powerSaveMode",
            "deviceIdle=$deviceIdleMode",
            "backgroundRestricted=$backgroundRestricted",
            "standbyBucket=$standbyBucket",
            "ignoreBattery=$ignoreBatteryOptimizations",
            "globalLowPower=$globalLowPower"
        ).joinToString("|")
        if (digest == lastPolicyDigest) {
            return
        }
        lastPolicyDigest = digest
        Log.e(TAG, "runtime_policy_forensics $digest")
        persistForensicsLine("runtime_policy_forensics", digest)
    }

    private fun persistForensicsLine(event: String, payload: String) {
        val filePath = appendForensicsLine(this, event, payload)
        if (filePath != null && lastForensicsFilePath != filePath) {
            lastForensicsFilePath = filePath
            Log.w(TAG, "forensics_file_path $filePath")
        }
    }

    private fun unregisterScreenReceiver() {
        runCatching {
            unregisterReceiver(screenOnReceiver)
        }.onFailure {
            Log.e(TAG, "unregister screen receiver failed: ${it.message}")
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
