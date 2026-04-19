package com.kidsphoneguard.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 密码管理器
 * 用于存储和验证家长配置密码
 */
class PasswordManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "password_prefs"
        private const val KEY_LEGACY_PASSWORD = "parent_password"
        private const val KEY_PASSWORD_HASH = "parent_password_hash"
        private const val KEY_PASSWORD_SALT = "parent_password_salt"
        private const val KEY_PASSWORD_VERSION = "parent_password_version"
        private const val PASSWORD_VERSION_PBKDF2 = 1
        private const val SALT_BYTES = 16
        private const val HASH_ITERATIONS = 120_000
        private const val HASH_LENGTH_BITS = 256

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
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, encodeToBase64(hash))
            .putString(KEY_PASSWORD_SALT, encodeToBase64(salt))
            .putInt(KEY_PASSWORD_VERSION, PASSWORD_VERSION_PBKDF2)
            .remove(KEY_LEGACY_PASSWORD)
            .apply()
    }

    fun hasPasswordConfigured(): Boolean {
        return hasHashedPassword() || prefs.contains(KEY_LEGACY_PASSWORD)
    }

    /**
     * 验证密码
     * @param inputPassword 输入的密码
     * @return 是否正确
     */
    fun verifyPassword(inputPassword: String): Boolean {
        if (inputPassword.isBlank()) {
            return false
        }
        if (hasHashedPassword()) {
            return verifyHashedPassword(inputPassword)
        }
        val legacyPassword = prefs.getString(KEY_LEGACY_PASSWORD, null) ?: return false
        if (legacyPassword != inputPassword) {
            return false
        }
        setPassword(inputPassword)
        return true
    }

    /**
     * 检查是否已设置密码（不是默认密码）
     * @return 是否已设置自定义密码
     */
    fun hasCustomPassword(): Boolean {
        return hasPasswordConfigured()
    }

    /**
     * 重置为默认密码
     */
    fun resetToDefault() {
        prefs.edit()
            .remove(KEY_LEGACY_PASSWORD)
            .remove(KEY_PASSWORD_HASH)
            .remove(KEY_PASSWORD_SALT)
            .remove(KEY_PASSWORD_VERSION)
            .apply()
    }

    private fun hasHashedPassword(): Boolean {
        return prefs.contains(KEY_PASSWORD_HASH) && prefs.contains(KEY_PASSWORD_SALT)
    }

    private fun verifyHashedPassword(inputPassword: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false
        val version = prefs.getInt(KEY_PASSWORD_VERSION, PASSWORD_VERSION_PBKDF2)
        if (version != PASSWORD_VERSION_PBKDF2) {
            return false
        }
        val calculatedHash = hashPassword(inputPassword, decodeFromBase64(storedSalt))
        return MessageDigest.isEqual(calculatedHash, decodeFromBase64(storedHash))
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, HASH_ITERATIONS, HASH_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        } catch (_: Exception) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun encodeToBase64(value: ByteArray): String {
        return Base64.encodeToString(value, Base64.NO_WRAP)
    }

    private fun decodeFromBase64(value: String): ByteArray {
        return Base64.decode(value, Base64.NO_WRAP)
    }
}
