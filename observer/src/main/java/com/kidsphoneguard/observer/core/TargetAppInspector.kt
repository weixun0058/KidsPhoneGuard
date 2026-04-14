package com.kidsphoneguard.observer.core

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

object TargetAppInspector {
    fun captureSnapshot(context: Context, source: String, eventAt: Long = System.currentTimeMillis()): ObserverSnapshot {
        val statePrefs = context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
        val lastMainHeartbeatAt = statePrefs.getLong(ObserverContract.keyLastMainHeartbeatAt, 0L)
        val heartbeatAgeMs = if (lastMainHeartbeatAt == 0L) -1L else eventAt - lastMainHeartbeatAt
        val mainHeartbeatFresh = heartbeatAgeMs in 0..ObserverContract.mainHeartbeatTimeoutMs
        val accessibilityGlobalEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        val enabledAccessibilityServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val targetServiceListed = enabledAccessibilityServices
            ?.split(':')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.any { it == ObserverContract.targetAccessibilityService } ?: false
        val targetProcessRunning = mainHeartbeatFresh || isTargetProcessRunning(context)
        val packageInfo = runCatching {
            context.packageManager.getApplicationInfo(ObserverContract.targetPackageName, 0)
        }.getOrNull()
        val targetPackageInstalled = packageInfo != null
        val targetPackageEnabled = packageInfo?.enabled == true
        val usageAccessGranted = hasUsageStatsPermission(context)
        val inferredMainStopped = !mainHeartbeatFresh &&
            !targetProcessRunning &&
            (!accessibilityGlobalEnabled || !targetServiceListed)
        return ObserverSnapshot(
            source = source,
            eventAt = eventAt,
            accessibilityGlobalEnabled = accessibilityGlobalEnabled,
            targetServiceListed = targetServiceListed,
            targetProcessRunning = targetProcessRunning,
            targetPackageInstalled = targetPackageInstalled,
            targetPackageEnabled = targetPackageEnabled,
            usageAccessGranted = usageAccessGranted,
            mainHeartbeatFresh = mainHeartbeatFresh,
            mainHeartbeatAgeMs = heartbeatAgeMs,
            inferredMainStopped = inferredMainStopped,
            mainBridgeSummary = statePrefs.getString(ObserverContract.keyLastBridgeSummary, "none").orEmpty()
        )
    }

    private fun isTargetProcessRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.runningAppProcesses
            ?.any { process -> process.processName == ObserverContract.targetPackageName } ?: false
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}
