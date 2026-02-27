package com.kidsphoneguard.service

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 使用时长统计管理器
 * 负责轮询UsageStatsManager，统计应用使用时长
 * 作为无障碍服务的补充，在无障碍服务失效时也能统计时长
 */
object UsageTrackingManager {

    private var trackingJob: Job? = null
    private val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastPackageName: String = ""
    private var lastCheckTime: Long = 0

    // 轮询间隔（毫秒）
    private const val POLLING_INTERVAL = 3000L

    /**
     * 开始统计
     * @param context 上下文
     */
    fun startTracking(context: Context) {
        if (trackingJob?.isActive == true) return

        if (!hasUsageStatsPermission(context)) {
            return
        }

        trackingJob = trackingScope.launch {
            while (isActive) {
                trackUsage(context)
                delay(POLLING_INTERVAL)
            }
        }
    }

    /**
     * 停止统计
     */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
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
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val app = context.applicationContext as KidsPhoneGuardApp

        // 查询最近的使用统计
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.MINUTES.toMillis(5) // 查询最近5分钟

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (stats.isNullOrEmpty()) return

        // 找到最近使用的应用
        val recentStats = stats.maxByOrNull { it.lastTimeUsed } ?: return
        val packageName = recentStats.packageName

        // 忽略系统应用和本应用
        if (packageName.startsWith("android") ||
            packageName == "com.android.systemui" ||
            packageName.contains("com.kidsphoneguard")
        ) {
            return
        }

        val currentTime = System.currentTimeMillis()

        // 如果是同一个应用，累加时长
        if (packageName == lastPackageName && lastCheckTime > 0) {
            val elapsedSeconds = (currentTime - lastCheckTime) / 1000
            if (elapsedSeconds > 0) {
                // 更新数据库
                app.dailyUsageRepository.addTodayUsageTime(packageName, elapsedSeconds)

                // 检查是否需要拦截
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

        // 只检查LIMIT类型的应用
        if (rule.ruleType != RuleType.LIMIT) return

        // 检查时长限制
        if (rule.dailyAllowedMinutes > 0) {
            val todayUsage = app.dailyUsageRepository.getTodayUsageSeconds(packageName)
            val allowedSeconds = rule.dailyAllowedMinutes * 60L

            if (todayUsage >= allowedSeconds) {
                // 触发拦截，显示覆盖层
                OverlayService.showOverlay(app, packageName, rule.appName)
            }
        }
    }
}
