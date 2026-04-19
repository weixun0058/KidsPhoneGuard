package com.kidsphoneguard.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.engine.LockDecisionEngine
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.utils.BroadcastPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 使用时长统计管理器
 * 负责轮询UsageStatsManager，统计应用使用时长
 * 作为无障碍服务的补充，在无障碍服务失效时也能统计时长
 */
object UsageTrackingManager {
    private const val TAG = "UsageTrackingManager"

    private var trackingJob: Job? = null
    private val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastPackageName: String = ""
    private var lastCheckTime: Long = 0
    private var lockDecisionEngine: LockDecisionEngine? = null

    // 轮询间隔（毫秒）
    private const val POLLING_INTERVAL = 3000L
    private const val EVENT_LOOKBACK_MILLIS = 15000L
    private const val STATS_LOOKBACK_MILLIS = 30 * 60 * 1000L
    private const val MAX_ACCOUNTABLE_ELAPSED_MILLIS = POLLING_INTERVAL + 2500L
    private val transientPackages = setOf(
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.realme.launcher",
        "com.oneplus.launcher",
        "com.vivo.launcher",
        "com.sec.android.app.launcher",
        "com.huawei.gameassistant",
        "com.hihonor.gameassistant"
    )

    fun isTrackingActive(): Boolean {
        return trackingJob?.isActive == true
    }

    /**
     * 开始统计
     * @param context 上下文
     */
    @Synchronized
    fun startTracking(context: Context, forceRestart: Boolean = false, reason: String = "unspecified") {
        if (trackingJob?.isActive == true && !forceRestart) {
            Log.d(TAG, "startTracking ignored: already active reason=$reason")
            return
        }

        if (trackingJob?.isActive == true && forceRestart) {
            Log.w(TAG, "startTracking forcing restart reason=$reason")
            trackingJob?.cancel()
            trackingJob = null
        }

        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "startTracking skipped: usage stats permission denied reason=$reason")
            return
        }

        GuardHealthState.touchUsageHeartbeat(context)
        lastPackageName = ""
        lastCheckTime = 0
        lockDecisionEngine = null
        Log.d(TAG, "startTracking success reason=$reason")

        trackingJob = trackingScope.launch {
            while (isActive) {
                GuardHealthState.touchUsageHeartbeat(context)
                try {
                    trackUsage(context)
                } catch (e: Exception) {
                    Log.e(TAG, "tracking loop failed reason=$reason message=${e.message}", e)
                }
                delay(POLLING_INTERVAL)
            }
        }
    }

    /**
     * 停止统计
     */
    @Synchronized
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        GuardHealthState.clearUsageHeartbeat(KidsPhoneGuardApp.instance)
        lastPackageName = ""
        lastCheckTime = 0
        lockDecisionEngine = null
        Log.d(TAG, "stopTracking")
    }

    /**
     * 检查是否有使用统计权限
     * @param context 上下文
     * @return 是否有权限
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 执行一次统计
     * @param context 上下文
     */
    private suspend fun trackUsage(context: Context) {
        if (!isScreenInteractive(context)) {
            if (lastPackageName.isNotEmpty() || lastCheckTime != 0L) {
                Log.d(TAG, "trackUsage: screen off, reset state lastPackage=$lastPackageName")
            }
            lastPackageName = ""
            lastCheckTime = 0
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val app = context.applicationContext as KidsPhoneGuardApp
        val currentTime = System.currentTimeMillis()
        val packageName = resolveForegroundPackage(usageStatsManager, currentTime)
        if (packageName.isNullOrEmpty()) {
            Log.d(TAG, "trackUsage: no valid foreground app lastPackage=$lastPackageName lastCheckDelta=${if (lastCheckTime == 0L) -1L else currentTime - lastCheckTime}")
            lastPackageName = ""
            lastCheckTime = 0
            return
        }

        if (packageName == lastPackageName && lastCheckTime > 0) {
            val elapsedMillis = (currentTime - lastCheckTime).coerceAtMost(MAX_ACCOUNTABLE_ELAPSED_MILLIS)
            val elapsedSeconds = elapsedMillis / 1000
            if (elapsedSeconds > 0) {
                app.dailyUsageRepository.addTodayUsageTime(packageName, elapsedSeconds)
                Log.d(TAG, "trackUsage: +${elapsedSeconds}s package=$packageName")
                checkAndTriggerBlock(app, packageName)
            }
        }

        lastPackageName = packageName
        lastCheckTime = currentTime
    }

    /**
     * 检查并触发拦截
     * @param app 应用实例
     * @param packageName 包名
     */
    private suspend fun checkAndTriggerBlock(app: KidsPhoneGuardApp, packageName: String) {
        val rule = app.appRuleRepository.getRuleByPackageName(packageName) ?: return
        if (rule.ruleType != RuleType.LIMIT) return
        val decision = getLockDecisionEngine(app).getBlockDecision(packageName)
        if (!decision.shouldBlock) {
            return
        }
        Log.d(TAG, "checkAndTriggerBlock: trigger block package=$packageName reason=${decision.reason}")
        if (GuardAccessibilityService.isServiceRunning()) {
            BroadcastPermissionHelper.sendBlockAppBroadcast(app, packageName)
        } else {
            OverlayService.showOverlay(app, packageName, rule.appName)
        }
    }

    private fun getLockDecisionEngine(context: Context): LockDecisionEngine {
        val cached = lockDecisionEngine
        if (cached != null) {
            return cached
        }
        val engine = LockDecisionEngine.getInstance(context.applicationContext)
        lockDecisionEngine = engine
        return engine
    }

    private fun resolveForegroundPackage(
        usageStatsManager: UsageStatsManager,
        endTime: Long
    ): String? {
        val fromEvents = resolveByUsageEvents(usageStatsManager, endTime)
        if (!fromEvents.isNullOrEmpty()) {
            return fromEvents
        }
        return resolveByUsageStats(usageStatsManager, endTime)
    }

    private fun resolveByUsageEvents(
        usageStatsManager: UsageStatsManager,
        endTime: Long
    ): String? {
        return try {
            val events = usageStatsManager.queryEvents(endTime - EVENT_LOOKBACK_MILLIS, endTime)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestEventTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (!isForegroundEvent(event.eventType)) {
                    continue
                }
                val packageName = event.packageName ?: continue
                if (!isTrackablePackage(packageName)) {
                    continue
                }
                if (event.timeStamp >= latestEventTime) {
                    latestEventTime = event.timeStamp
                    latestPackage = packageName
                }
            }
            latestPackage
        } catch (e: Exception) {
            Log.w(TAG, "resolveByUsageEvents failed: ${e.message}")
            null
        }
    }

    private fun resolveByUsageStats(
        usageStatsManager: UsageStatsManager,
        endTime: Long
    ): String? {
        return try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - STATS_LOOKBACK_MILLIS,
                endTime
            )
            val packageName = stats
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
                ?: return null
            if (isTrackablePackage(packageName)) packageName else null
        } catch (e: Exception) {
            Log.w(TAG, "resolveByUsageStats failed: ${e.message}")
            null
        }
    }

    private fun isForegroundEvent(eventType: Int): Boolean {
        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            return true
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
    }

    private fun isTrackablePackage(packageName: String): Boolean {
        if (packageName.startsWith("android")) {
            return false
        }
        if (packageName == "com.android.systemui") {
            return false
        }
        if (packageName.contains("com.kidsphoneguard")) {
            return false
        }
        if (packageName in transientPackages) {
            return false
        }
        return true
    }

    private fun isScreenInteractive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            true
        }
    }
}
