package com.kidsphoneguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.DailyUsage

/**
 * 应用数据库
 * 包含应用规则表和每日使用统计表
 */
@Database(
    entities = [AppRule::class, DailyUsage::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appRuleDao(): AppRuleDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2 迁移：app_rules 新增 limitMode 列（LimitMode.ordinal，默认 0 = BOTH）。
         *
         * 历史：v1→v2 曾使用 fallbackToDestructiveMigration 静默清空过一次用户数据（提交 9e536a4）。
         * 现改为显式迁移，保护仍在 v1 的用户数据；同时移除 fallbackToDestructiveMigration，
         * 防止未来 schema 变更再次破坏数据（ISS-003）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_rules ADD COLUMN limitMode INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

