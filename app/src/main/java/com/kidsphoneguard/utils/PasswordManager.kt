package com.kidsphoneguard.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal data class StoredPasswordHash(
    val encodedHash: String?,
    val encodedSalt: String?,
    val version: Int?
) {
    val isComplete: Boolean
        get() = encodedHash != null && encodedSalt != null
}

internal interface PasswordStorage {
    fun readLegacyPassword(): String?
    fun readHashedPassword(): StoredPasswordHash
    fun storeHashedPassword(encodedHash: String, encodedSalt: String, version: Int)
    fun removeLegacyPassword()
    fun clearPassword()
}

internal interface PasswordManagerLogger {
    fun info(message: String)
    fun error(message: String, exception: Exception)
}

private object NoOpPasswordManagerLogger : PasswordManagerLogger {
    override fun info(message: String) = Unit
    override fun error(message: String, exception: Exception) = Unit
}

private object AndroidPasswordManagerLogger : PasswordManagerLogger {
    private const val TAG = "PasswordManager"

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun error(message: String, exception: Exception) {
        Log.e(TAG, message, exception)
    }
}

private class SharedPreferencesPasswordStorage(
    private val prefs: SharedPreferences
) : PasswordStorage {
    override fun readLegacyPassword(): String? =
        prefs.getString(KEY_LEGACY_PASSWORD, null)

    override fun readHashedPassword(): StoredPasswordHash = StoredPasswordHash(
        encodedHash = prefs.getString(KEY_PASSWORD_HASH, null),
        encodedSalt = prefs.getString(KEY_PASSWORD_SALT, null),
        version = if (prefs.contains(KEY_PASSWORD_VERSION)) {
            prefs.getInt(KEY_PASSWORD_VERSION, PASSWORD_VERSION_PBKDF2)
        } else {
            null
        }
    )

    override fun storeHashedPassword(encodedHash: String, encodedSalt: String, version: Int) {
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, encodedHash)
            .putString(KEY_PASSWORD_SALT, encodedSalt)
            .putInt(KEY_PASSWORD_VERSION, version)
            .remove(KEY_LEGACY_PASSWORD)
            .apply()
    }

    override fun removeLegacyPassword() {
        prefs.edit().remove(KEY_LEGACY_PASSWORD).apply()
    }

    override fun clearPassword() {
        prefs.edit()
            .remove(KEY_LEGACY_PASSWORD)
            .remove(KEY_PASSWORD_HASH)
            .remove(KEY_PASSWORD_SALT)
            .remove(KEY_PASSWORD_VERSION)
            .apply()
    }

    companion object {
        private const val KEY_LEGACY_PASSWORD = "parent_password"
        private const val KEY_PASSWORD_HASH = "parent_password_hash"
        private const val KEY_PASSWORD_SALT = "parent_password_salt"
        private const val KEY_PASSWORD_VERSION = "parent_password_version"
        private const val PASSWORD_VERSION_PBKDF2 = 1
    }
}

/**
 * 密码管理器
 * 用于存储和验证家长配置密码
 */
class PasswordManager internal constructor(
    private val storage: PasswordStorage,
    private val saltGenerator: () -> ByteArray = {
        ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    },
    private val onPasswordVerified: () -> Unit = {},
    private val logger: PasswordManagerLogger = NoOpPasswordManagerLogger
) {

    constructor(context: Context) : this(
        storage = SharedPreferencesPasswordStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ),
        onPasswordVerified = {
            TrustedTimeProvider.clearTamperFlag(context.applicationContext)
        },
        logger = AndroidPasswordManagerLogger
    )

    companion object {
        private const val PREFS_NAME = "password_prefs"
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

        internal fun legacyPasswordMigrationAction(
            hasHashedPassword: Boolean,
            legacyPassword: String?
        ): LegacyPasswordMigrationAction = when {
            legacyPassword == null -> LegacyPasswordMigrationAction.NONE
            hasHashedPassword -> LegacyPasswordMigrationAction.REMOVE_REDUNDANT
            else -> LegacyPasswordMigrationAction.MIGRATE
        }
    }

    /**
     * 设置密码
     * @param password 新密码
     */
    fun setPassword(password: String) {
        val salt = saltGenerator()
        val hash = hashPassword(password, salt)
        storage.storeHashedPassword(
            encodedHash = encodeToBase64(hash),
            encodedSalt = encodeToBase64(salt),
            version = PASSWORD_VERSION_PBKDF2
        )
    }

    fun hasPasswordConfigured(): Boolean {
        // ISS-013: 不再把明文密码视为已配置；仅 PBKDF2 hash 算已配置
        return hasHashedPassword()
    }

    /**
     * 验证密码
     * @param inputPassword 输入的密码
     * @return 是否正确
     *
     * 验证成功（即家长身份确认）时，顺带解除时间篡改冻结（ISS-001）：
     * 家长在场操作，可信时间可重新锚定到当前系统时间。
     *
     * ISS-013：已移除运行时明文验证分支，只支持 PBKDF2 hash 验证。
     * 旧安装的残留明文由 [migrateLegacyPasswordIfNeeded] 在启动时先哈希迁移再删除。
     */
    fun verifyPassword(inputPassword: String): Boolean {
        if (inputPassword.isBlank()) {
            return false
        }
        val verified = hasHashedPassword() && verifyHashedPassword(inputPassword)
        if (verified) {
            onPasswordVerified()
        }
        return verified
    }

    /**
     * 迁移旧格式明文密码（ISS-013）。应在应用启动时调用。
     *
     * 若用户只有 `KEY_LEGACY_PASSWORD`，必须先写入 PBKDF2 hash 再删除明文，
     * 不能把其误判为“未设置密码”，否则会给儿童重设家长密码的机会。
     * 已有 hash 时，明文仅是历史残留，可直接删除。
     */
    fun migrateLegacyPasswordIfNeeded() {
        val legacyPassword = storage.readLegacyPassword()
        when (legacyPasswordMigrationAction(hasHashedPassword(), legacyPassword)) {
            LegacyPasswordMigrationAction.NONE -> Unit
            LegacyPasswordMigrationAction.REMOVE_REDUNDANT -> {
                storage.removeLegacyPassword()
                logger.info("legacy_plaintext_password_removed_after_hash_exists")
            }
            LegacyPasswordMigrationAction.MIGRATE -> {
                try {
                    setPassword(requireNotNull(legacyPassword))
                    logger.info("legacy_plaintext_password_migrated_to_pbkdf2")
                } catch (e: Exception) {
                    // 迁移失败时保留明文，避免丢失家长密码；下次启动可重试。
                    logger.error("legacy_password_migration_failed", e)
                }
            }
        }
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
        storage.clearPassword()
    }

    private fun hasHashedPassword(): Boolean {
        return storage.readHashedPassword().isComplete
    }

    private fun verifyHashedPassword(inputPassword: String): Boolean {
        val storedPassword = storage.readHashedPassword()
        val storedHash = storedPassword.encodedHash ?: return false
        val storedSalt = storedPassword.encodedSalt ?: return false
        val version = storedPassword.version ?: PASSWORD_VERSION_PBKDF2
        if (version != PASSWORD_VERSION_PBKDF2) {
            return false
        }
        return try {
            val calculatedHash = hashPassword(inputPassword, decodeFromBase64(storedSalt))
            MessageDigest.isEqual(calculatedHash, decodeFromBase64(storedHash))
        } catch (exception: Exception) {
            logger.error("stored_password_hash_invalid", exception)
            false
        }
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
        return Base64.getEncoder().encodeToString(value)
    }

    private fun decodeFromBase64(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }
}

internal enum class LegacyPasswordMigrationAction {
    NONE,
    REMOVE_REDUNDANT,
    MIGRATE
}
