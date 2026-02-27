package com.kidsphoneguard.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 密码管理器
 * 用于存储和验证家长配置密码
 */
class PasswordManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "password_prefs"
        private const val KEY_PASSWORD = "parent_password"
        private const val DEFAULT_PASSWORD = "123456" // 默认密码

        @Volatile
        private var instance: PasswordManager? = null

        fun getInstance(context: Context): PasswordManager {
            return instance ?: synchronized(this) {
                instance ?: PasswordManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 设置密码
     * @param password 新密码
     */
    fun setPassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    /**
     * 获取当前密码
     * @return 密码（如果没有设置过，返回默认密码）
     */
    fun getPassword(): String {
        return prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    /**
     * 验证密码
     * @param inputPassword 输入的密码
     * @return 是否正确
     */
    fun verifyPassword(inputPassword: String): Boolean {
        return inputPassword == getPassword()
    }

    /**
     * 检查是否已设置密码（不是默认密码）
     * @return 是否已设置自定义密码
     */
    fun hasCustomPassword(): Boolean {
        return prefs.contains(KEY_PASSWORD)
    }

    /**
     * 重置为默认密码
     */
    fun resetToDefault() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }
}
