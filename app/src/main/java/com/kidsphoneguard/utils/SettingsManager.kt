package com.kidsphoneguard.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置管理器
 * 用于存储全局设置，如全局锁机状态
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val KEY_GLOBAL_LOCK = "global_lock_enabled"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 设置全局锁机状态
     * @param enabled 是否启用全局锁机
     */
    fun setGlobalLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBAL_LOCK, enabled).apply()
    }

    /**
     * 获取全局锁机状态
     * @return 是否启用了全局锁机
     */
    fun isGlobalLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_GLOBAL_LOCK, false)
    }
}
