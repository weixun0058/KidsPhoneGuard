package com.kidsphoneguard.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppDatabase 迁移正确性测试（ISS-003）。
 *
 * 验证 MIGRATION_1_2 在 v1 结构的 app_rules 表上执行后，新增 limitMode 列且默认值为 0（LimitMode.BOTH）。
 *
 * 说明：v1 schema 此前未导出（exportSchema=false），故采用手动建 v1 表 + 执行迁移 SQL 的方式验证，
 * 而非依赖 schema JSON 的 MigrationTestHelper.runMigrationsAndValidate。后续若补齐历史 schema JSON，
 * 可在 ISS-015 中升级为完整的 MigrationTestHelper 校验。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migration_1_2_adds_limitMode_column_with_default_zero() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }

        val factory = FrameworkSQLiteOpenHelperFactory()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 建 v1 结构的 app_rules 表（无 limitMode 列），与 v1 schema 一致
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS app_rules (
                            packageName TEXT NOT NULL,
                            ruleType INTEGER NOT NULL,
                            dailyAllowedMinutes INTEGER NOT NULL,
                            blockedTimeWindows TEXT NOT NULL,
                            isGlobalLocked INTEGER NOT NULL,
                            appName TEXT NOT NULL,
                            PRIMARY KEY(packageName)
                        )
                        """.trimIndent()
                    )
                    // 插入一条 v1 数据
                    db.execSQL(
                        "INSERT INTO app_rules (packageName, ruleType, dailyAllowedMinutes, " +
                            "blockedTimeWindows, isGlobalLocked, appName) " +
                            "VALUES ('com.example.app', 2, 30, '22:00-07:00', 0, '示例应用')"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // 测试中不触发，迁移由 MIGRATION_1_2 手动执行
                }
            })
            .build()

        val helper = factory.create(configuration)
        val db = helper.writableDatabase
        try {
            // 执行迁移（v1 → v2）
            AppDatabase.MIGRATION_1_2.migrate(db)

            // 验证 limitMode 列存在且默认值为 0
            val cursor = db.query(
                "SELECT limitMode FROM app_rules WHERE packageName = 'com.example.app'"
            )
            assertTrue("迁移后应能查到 limitMode 列", cursor.moveToFirst())
            val limitModeIndex = cursor.getColumnIndex("limitMode")
            assertTrue("limitMode 列应存在", limitModeIndex >= 0)
            assertEquals("limitMode 默认值应为 0 (BOTH)", 0, cursor.getInt(limitModeIndex))
            cursor.close()
        } finally {
            db.close()
            helper.close()
            if (dbFile.exists()) {
                dbFile.delete()
            }
        }
    }
}
