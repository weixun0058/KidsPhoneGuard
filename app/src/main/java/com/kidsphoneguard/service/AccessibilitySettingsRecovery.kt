package com.kidsphoneguard.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.kidsphoneguard.utils.PermissionManager

/**
 * 通过 ADB 授予的 WRITE_SECURE_SETTINGS 恢复本应用无障碍设置。
 *
 * 这里只负责设置层恢复；是否允许自动恢复（家长解锁、设置授权窗口等）由调用方决定。
 */
object AccessibilitySettingsRecovery {

    private const val TAG = "AccessibilityRecovery"

    enum class Result {
        RESTORED,
        REBIND_PREPARED,
        NO_PERMISSION,
        WRITE_FAILED
    }

    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun tryRestore(context: Context, source: String): Result {
        if (!hasWriteSecureSettingsPermission(context)) {
            Log.w(TAG, "restore_skipped source=$source reason=no_write_secure_settings")
            return Result.NO_PERMISSION
        }

        return try {
            val target = ComponentName(context, GuardAccessibilityService::class.java)
            val existing = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val merged = mergeEnabledServices(
                existing = existing,
                targetPackage = target.packageName,
                targetClass = target.className
            )

            val servicesWritten = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                merged
            )
            val enabledWritten = servicesWritten && Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            val restored = enabledWritten && PermissionManager.isAccessibilityServiceEnabled(context)
            if (restored) {
                Log.w(TAG, "restore_success source=$source preserved_entries=${serviceEntryCount(merged)}")
                Result.RESTORED
            } else {
                Log.e(
                    TAG,
                    "restore_write_failed source=$source servicesWritten=$servicesWritten enabledWritten=$enabledWritten"
                )
                Result.WRITE_FAILED
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "restore_security_failed source=$source reason=${error.message}", error)
            Result.NO_PERMISSION
        } catch (error: Exception) {
            Log.e(TAG, "restore_failed source=$source reason=${error.message}", error)
            Result.WRITE_FAILED
        }
    }

    /**
     * 从已启用列表中暂时移除本服务，让 AccessibilityManager 清理 DEAD/crashed 连接。
     * 调用方应在短暂延迟后调用 [tryRestore]，形成真实的禁用→启用状态转换。
     */
    fun prepareForRebind(context: Context, source: String): Result {
        if (!hasWriteSecureSettingsPermission(context)) {
            Log.w(TAG, "rebind_prepare_skipped source=$source reason=no_write_secure_settings")
            return Result.NO_PERMISSION
        }

        return try {
            val target = ComponentName(context, GuardAccessibilityService::class.java)
            val existing = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val withoutTarget = removeTargetService(
                existing = existing,
                targetPackage = target.packageName,
                targetClass = target.className
            )
            val servicesWritten = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                withoutTarget
            )
            val enabledWritten = if (servicesWritten && withoutTarget.isBlank()) {
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
            } else {
                servicesWritten
            }
            if (servicesWritten && enabledWritten) {
                Log.w(
                    TAG,
                    "rebind_prepare_success source=$source preserved_entries=${serviceEntryCount(withoutTarget)}"
                )
                Result.REBIND_PREPARED
            } else {
                Log.e(
                    TAG,
                    "rebind_prepare_write_failed source=$source " +
                        "servicesWritten=$servicesWritten enabledWritten=$enabledWritten"
                )
                Result.WRITE_FAILED
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "rebind_prepare_security_failed source=$source reason=${error.message}", error)
            Result.NO_PERMISSION
        } catch (error: Exception) {
            Log.e(TAG, "rebind_prepare_failed source=$source reason=${error.message}", error)
            Result.WRITE_FAILED
        }
    }

    internal fun shouldAttemptAutomatically(
        serviceEnabled: Boolean,
        globalUnlockEnabled: Boolean,
        setupSettingsAccessAllowed: Boolean,
        permissionGranted: Boolean
    ): Boolean {
        return !serviceEnabled &&
            !globalUnlockEnabled &&
            !setupSettingsAccessAllowed &&
            permissionGranted
    }

    internal fun shouldPrepareAutomaticRebind(
        serviceConfigured: Boolean,
        serviceRunning: Boolean,
        heartbeatAgeMs: Long,
        heartbeatTimeoutMs: Long,
        globalUnlockEnabled: Boolean,
        setupSettingsAccessAllowed: Boolean,
        permissionGranted: Boolean
    ): Boolean {
        val bindingStale = !serviceRunning ||
            heartbeatAgeMs < 0L ||
            heartbeatAgeMs > heartbeatTimeoutMs
        return serviceConfigured &&
            bindingStale &&
            !globalUnlockEnabled &&
            !setupSettingsAccessAllowed &&
            permissionGranted
    }

    /**
     * 保留系统中原有的全部无障碍服务；仅在目标服务不存在时追加。
     * 同时识别 Android 常见的完整类名与 ".ClassName" 简写形式。
     */
    internal fun mergeEnabledServices(
        existing: String,
        targetPackage: String,
        targetClass: String
    ): String {
        val entries = existing
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val targetPresent = entries.any { entry ->
            isTargetServiceEntry(entry, targetPackage, targetClass)
        }
        if (targetPresent) {
            return entries.joinToString(":")
        }

        val targetEntry = "$targetPackage/$targetClass"
        return (entries + targetEntry).joinToString(":")
    }

    internal fun removeTargetService(
        existing: String,
        targetPackage: String,
        targetClass: String
    ): String {
        return existing
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { entry ->
                isTargetServiceEntry(entry, targetPackage, targetClass)
            }
            .joinToString(":")
    }

    private fun isTargetServiceEntry(
        entry: String,
        targetPackage: String,
        targetClass: String
    ): Boolean {
        val separator = entry.indexOf('/')
        if (separator <= 0 || separator == entry.lastIndex) {
            return false
        }
        val packageName = entry.substring(0, separator)
        val classToken = entry.substring(separator + 1)
        val className = if (classToken.startsWith('.')) {
            packageName + classToken
        } else {
            classToken
        }
        return packageName == targetPackage && className == targetClass
    }

    private fun serviceEntryCount(value: String): Int {
        return value.split(':').count { it.isNotBlank() }
    }
}
