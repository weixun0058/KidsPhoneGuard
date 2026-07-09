package com.kidsphoneguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.kidsphoneguard.data.db.AppDatabase
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.data.repository.DailyUsageRepository

/**
 * 应用入口类
 * 负责初始化数据库、通知通道等全局资源
 */
class KidsPhoneGuardApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "guard_service_channel"
        lateinit var instance: KidsPhoneGuardApp
            private set
    }

    // 数据库实例（懒加载）
    val database by lazy { AppDatabase.getInstance(this) }

    // 仓库实例
    val appRuleRepository by lazy { AppRuleRepository(database.appRuleDao()) }
    val dailyUsageRepository by lazy { DailyUsageRepository(database.dailyUsageDao(), applicationContext) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // ISS-013：启动时清理残留明文密码（明文分支已淘汰）
        com.kidsphoneguard.utils.PasswordManager.getInstance(this).cleanupLegacyPassword()
    }

    /**
     * 创建通知通道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                name,
                importance
            ).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
