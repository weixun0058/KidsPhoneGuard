package com.kidsphoneguard.observer.core

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

object TargetAppInspector {
    fun captureSnapshot(context: Context, source: String, eventAt: Long = System.currentTimeMillis()): ObserverSnapshot {
        val statePrefs = context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
        val lastMainHeartbeatAt = statePrefs.getLong(ObserverContract.keyLastMainHeartbeatAt, 0L)
        val lastMainHeartbeatSource = statePrefs.getString(
            ObserverContract.keyLastMainHeartbeatSource,
            "none"
        ).orEmpty()
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
        ).orEmpty()
        val targetServiceListed = enabledAccessibilityServices
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { it == ObserverContract.targetAccessibilityService }
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
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
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
        return ObserverSnapshot(
            source = source,
            eventAt = eventAt,
            accessibilityGlobalEnabled = accessibilityGlobalEnabled,
            enabledAccessibilityServices = enabledAccessibilityServices.replace("\n", " ").take(240),
            targetServiceListed = targetServiceListed,
            targetProcessRunning = targetProcessRunning,
            targetPackageInstalled = targetPackageInstalled,
            targetPackageEnabled = targetPackageEnabled,
            usageAccessGranted = usageAccessGranted,
            mainHeartbeatFresh = mainHeartbeatFresh,
            mainHeartbeatAgeMs = heartbeatAgeMs,
            inferredMainStopped = inferredMainStopped,
            mainBridgeSummary = statePrefs.getString(ObserverContract.keyLastBridgeSummary, "none").orEmpty(),
            lastMainHeartbeatSource = lastMainHeartbeatSource,
            interactive = interactive,
            powerSaveMode = powerSaveMode
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
