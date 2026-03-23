package com.kidsphoneguard.service

import android.content.Context

object GuardHealthState {
    private const val PREFS_NAME = "guard_health_state"
    private const val KEY_ACCESSIBILITY_HEARTBEAT = "accessibility_heartbeat"
    private const val KEY_USAGE_HEARTBEAT = "usage_heartbeat"
    private const val KEY_LAST_ACCESSIBILITY_GUIDE_TIME = "last_accessibility_guide_time"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun touchAccessibilityHeartbeat(context: Context, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_ACCESSIBILITY_HEARTBEAT, timestamp).apply()
    }

    fun getAccessibilityHeartbeat(context: Context): Long {
        return prefs(context).getLong(KEY_ACCESSIBILITY_HEARTBEAT, 0L)
    }

    fun clearAccessibilityHeartbeat(context: Context) {
        prefs(context).edit().putLong(KEY_ACCESSIBILITY_HEARTBEAT, 0L).apply()
    }

    fun touchUsageHeartbeat(context: Context, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_USAGE_HEARTBEAT, timestamp).apply()
    }

    fun getUsageHeartbeat(context: Context): Long {
        return prefs(context).getLong(KEY_USAGE_HEARTBEAT, 0L)
    }

    fun clearUsageHeartbeat(context: Context) {
        prefs(context).edit().putLong(KEY_USAGE_HEARTBEAT, 0L).apply()
    }

    fun markAccessibilityGuideShown(context: Context, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_ACCESSIBILITY_GUIDE_TIME, timestamp).apply()
    }

    fun canShowAccessibilityGuide(context: Context, cooldownMillis: Long): Boolean {
        val lastShownTime = prefs(context).getLong(KEY_LAST_ACCESSIBILITY_GUIDE_TIME, 0L)
        return System.currentTimeMillis() - lastShownTime >= cooldownMillis
    }
}
