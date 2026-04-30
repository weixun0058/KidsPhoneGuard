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
        private const val KEY_GLOBAL_UNLOCK = "global_unlock_enabled"
        private const val KEY_BRAND_SETUP_CONFIRMED = "brand_setup_confirmed"
        private const val KEY_SETUP_SETTINGS_ALLOWED_UNTIL = "setup_settings_allowed_until"
        private const val KEY_WECHAT_FINDER_BLOCK = "wechat_finder_block_enabled"

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

    fun setGlobalUnlock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBAL_UNLOCK, enabled).apply()
    }

    fun isGlobalUnlockEnabled(): Boolean {
        return prefs.getBoolean(KEY_GLOBAL_UNLOCK, false)
    }

    fun setBrandSetupConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_BRAND_SETUP_CONFIRMED, confirmed).apply()
    }

    fun isBrandSetupConfirmed(): Boolean {
        return prefs.getBoolean(KEY_BRAND_SETUP_CONFIRMED, false)
    }

    fun allowSetupSettingsAccess(durationMillis: Long) {
        prefs.edit()
            .putLong(KEY_SETUP_SETTINGS_ALLOWED_UNTIL, System.currentTimeMillis() + durationMillis)
            .apply()
    }

    fun clearSetupSettingsAccess() {
        prefs.edit()
            .remove(KEY_SETUP_SETTINGS_ALLOWED_UNTIL)
            .apply()
    }

    fun isSetupSettingsAccessAllowed(): Boolean {
        return prefs.getLong(KEY_SETUP_SETTINGS_ALLOWED_UNTIL, 0L) > System.currentTimeMillis()
    }

    fun setWeChatFinderBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WECHAT_FINDER_BLOCK, enabled).apply()
    }

    fun isWeChatFinderBlockEnabled(): Boolean {
        return prefs.getBoolean(KEY_WECHAT_FINDER_BLOCK, false)
    }
}
