package com.kidsphoneguard.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置管理器
 * 用于存储全局设置，如全局锁机状态
 */
internal interface SettingsStorage {
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putLong(key: String, value: Long)
    fun getLong(key: String, defaultValue: Long): Long
    fun remove(key: String)
}

private class SharedPreferencesSettingsStorage(
    private val prefs: SharedPreferences
) : SettingsStorage {
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

class SettingsManager internal constructor(
    private val storage: SettingsStorage,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {

    constructor(context: Context) : this(
        storage = SharedPreferencesSettingsStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    )

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
        storage.putBoolean(KEY_GLOBAL_LOCK, enabled)
    }

    /**
     * 获取全局锁机状态
     * @return 是否启用了全局锁机
     */
    fun isGlobalLockEnabled(): Boolean {
        return storage.getBoolean(KEY_GLOBAL_LOCK, false)
    }

    fun setGlobalUnlock(enabled: Boolean) {
        storage.putBoolean(KEY_GLOBAL_UNLOCK, enabled)
    }

    fun isGlobalUnlockEnabled(): Boolean {
        return storage.getBoolean(KEY_GLOBAL_UNLOCK, false)
    }

    fun setBrandSetupConfirmed(confirmed: Boolean) {
        storage.putBoolean(KEY_BRAND_SETUP_CONFIRMED, confirmed)
    }

    fun isBrandSetupConfirmed(): Boolean {
        return storage.getBoolean(KEY_BRAND_SETUP_CONFIRMED, false)
    }

    fun allowSetupSettingsAccess(durationMillis: Long) {
        storage.putLong(
            KEY_SETUP_SETTINGS_ALLOWED_UNTIL,
            currentTimeMillis() + durationMillis
        )
    }

    fun clearSetupSettingsAccess() {
        storage.remove(KEY_SETUP_SETTINGS_ALLOWED_UNTIL)
    }

    fun isSetupSettingsAccessAllowed(): Boolean {
        return storage.getLong(KEY_SETUP_SETTINGS_ALLOWED_UNTIL, 0L) > currentTimeMillis()
    }

    fun setWeChatFinderBlockEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_WECHAT_FINDER_BLOCK, enabled)
    }

    fun isWeChatFinderBlockEnabled(): Boolean {
        return storage.getBoolean(KEY_WECHAT_FINDER_BLOCK, false)
    }
}
