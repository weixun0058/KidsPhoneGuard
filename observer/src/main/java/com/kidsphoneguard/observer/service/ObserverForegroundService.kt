package com.kidsphoneguard.observer.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.kidsphoneguard.observer.ObserverApp
import com.kidsphoneguard.observer.R
import com.kidsphoneguard.observer.core.ObserverContract
import com.kidsphoneguard.observer.core.ObserverLogStore
import com.kidsphoneguard.observer.core.TargetAppInspector
import com.kidsphoneguard.observer.receiver.ObserverBootReceiver
import com.kidsphoneguard.observer.ui.MainActivity

class ObserverForegroundService : Service() {
    companion object {
        private const val notificationId = 2001
        private const val actionStart = "com.kidsphoneguard.observer.action.START"
        private const val extraSource = "extra_source"
        private const val watchdogRequestCode = 4001

        fun start(context: Context, source: String) {
            val intent = Intent(context, ObserverForegroundService::class.java).apply {
                action = actionStart
                putExtra(extraSource, source)
            }
            context.startForegroundService(intent)
        }

        private fun scheduleWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ObserverBootReceiver::class.java).apply {
                action = ObserverContract.observerWatchdogAction
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                watchdogRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = SystemClock.elapsedRealtime() + ObserverContract.observerWatchdogIntervalMs
            if (canScheduleExactAlarm(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            }
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        private fun canScheduleExactAlarm(alarmManager: AlarmManager): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshotSummary = ""
    private var lastIncidentSignature = ""
    private val pollRunnable = object : Runnable {
        override fun run() {
            captureAndPersist("periodic")
            handler.postDelayed(this, ObserverContract.observerIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(notificationId, createNotification(ObserverLogStore.readLatestSummary(this)))
        ObserverLogStore.appendLine(this, "observer_service_lifecycle", "event=onCreate")
        scheduleWatchdog(this)
        captureAndPersist("service_onCreate")
        handler.post(pollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = intent?.getStringExtra(extraSource).orEmpty().ifEmpty { "onStartCommand" }
        ObserverLogStore.appendLine(this, "observer_service_start", "source=$source|startId=$startId|flags=$flags")
        scheduleWatchdog(this)
        captureAndPersist(source)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        captureAndPersist("service_onDestroy")
        ObserverLogStore.appendLine(this, "observer_service_lifecycle", "event=onDestroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun captureAndPersist(source: String) {
        val snapshot = TargetAppInspector.captureSnapshot(this, source)
        ObserverLogStore.persistSnapshot(this, snapshot)
        val latestSummary = ObserverLogStore.readLatestSummary(this)
        if (latestSummary != lastSnapshotSummary) {
            lastSnapshotSummary = latestSummary
            updateNotification(latestSummary)
        }
        val incidentSignature = buildIncidentSignature(snapshot)
        if (incidentSignature != null && incidentSignature != lastIncidentSignature) {
            lastIncidentSignature = incidentSignature
            ObserverLogStore.appendLine(this, "observer_incident", "source=$source|$incidentSignature")
        }
        if (incidentSignature == null && lastIncidentSignature.isNotEmpty()) {
            ObserverLogStore.appendLine(
                this,
                "observer_recovery",
                "source=$source|previous=$lastIncidentSignature"
            )
            lastIncidentSignature = ""
        }
    }

    /**
     * 汇总当前 observer 看到的故障征兆，生成稳定的事件签名，便于后续取证和去重。
     */
    private fun buildIncidentSignature(snapshot: com.kidsphoneguard.observer.core.ObserverSnapshot): String? {
        val reasons = mutableListOf<String>()
        if (!snapshot.accessibilityGlobalEnabled) {
            reasons += "global_accessibility_disabled"
        }
        if (!snapshot.targetServiceListed) {
            reasons += "target_service_not_listed"
        }
        if (!snapshot.targetProcessRunning) {
            reasons += "target_process_missing"
        }
        if (!snapshot.targetPackageInstalled) {
            reasons += "target_package_missing"
        } else if (!snapshot.targetPackageEnabled) {
            reasons += "target_package_disabled"
        }
        if (!snapshot.usageAccessGranted) {
            reasons += "observer_usage_access_missing"
        }
        if (!snapshot.mainHeartbeatFresh) {
            reasons += if (snapshot.mainHeartbeatAgeMs < 0L) {
                "main_heartbeat_missing"
            } else {
                "main_heartbeat_stale"
            }
        }
        if (snapshot.mainBridgeSummary == "none") {
            reasons += "main_bridge_missing"
        }
        if (reasons.isEmpty()) {
            return null
        }
        return listOf(
            "reasons=${reasons.joinToString(",")}",
            "eventAt=${snapshot.eventAt}",
            "globalAe=${snapshot.accessibilityGlobalEnabled}",
            "serviceListed=${snapshot.targetServiceListed}",
            "processRunning=${snapshot.targetProcessRunning}",
            "pkgInstalled=${snapshot.targetPackageInstalled}",
            "pkgEnabled=${snapshot.targetPackageEnabled}",
            "usageAccess=${snapshot.usageAccessGranted}",
            "heartbeatFresh=${snapshot.mainHeartbeatFresh}",
            "heartbeatAgeMs=${snapshot.mainHeartbeatAgeMs}",
            "bridge=${snapshot.mainBridgeSummary}"
        ).joinToString("|")
    }

    private fun updateNotification(summary: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(notificationId, createNotification(summary))
    }

    private fun createNotification(summary: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ObserverApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(summary.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
