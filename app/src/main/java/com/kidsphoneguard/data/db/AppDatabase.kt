package com.kidsphoneguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.DailyUsage

/**
 * 应用数据库
 * 包含应用规则表和每日使用统计表
 */
@Database(
    entities = [AppRule::class, DailyUsage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appRuleDao(): AppRuleDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库实例（单例模式）
         * @param context 应用上下文
         * @return 数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kids_phone_guard.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
