package com.kidsphoneguard.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppDatabase 迁移正确性测试（ISS-003）。
 *
 * 验证 MIGRATION_1_2 在 v1 结构的 app_rules 表上执行后，新增 limitMode 列且默认值为 0（LimitMode.BOTH）。
 *
 * v1 schema 由历史提交 9e536a4^ 的实体源码使用原 Room 2.6.1 编译器重新导出，
 * MigrationTestHelper 会验证迁移后的完整 v2 表结构，并确认已有数据与新增列默认值均被保留。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration_1_2_preservesData_addsLimitMode_andMatchesCompleteSchema() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO app_rules (packageName, ruleType, dailyAllowedMinutes, " +
                    "blockedTimeWindows, isGlobalLocked, appName) " +
                    "VALUES ('com.example.app', 2, 30, '22:00-07:00', 0, '示例应用')"
            )
            execSQL(
                "INSERT INTO daily_usage (date, packageName, usedTimeInSeconds, lastUpdated) " +
                    "VALUES ('2026-07-21', 'com.example.app', 600, 123456789)"
            )
            close()
        }

        val db = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )
        migrationHelper.closeWhenFinished(db)

        try {
            db.query(
                "SELECT limitMode FROM app_rules WHERE packageName = 'com.example.app'"
            ).use { cursor ->
                assertTrue("迁移后应保留 app_rules 数据", cursor.moveToFirst())
                val limitModeIndex = cursor.getColumnIndex("limitMode")
                assertTrue("limitMode 列应存在", limitModeIndex >= 0)
                assertEquals("limitMode 默认值应为 0 (BOTH)", 0, cursor.getInt(limitModeIndex))
            }

            db.query(
                "SELECT usedTimeInSeconds FROM daily_usage " +
                    "WHERE date = '2026-07-21' AND packageName = 'com.example.app'"
            ).use { cursor ->
                assertTrue("迁移后应保留 daily_usage 数据", cursor.moveToFirst())
                assertEquals(600, cursor.getInt(cursor.getColumnIndexOrThrow("usedTimeInSeconds")))
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
