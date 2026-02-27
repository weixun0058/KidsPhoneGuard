package com.kidsphoneguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kidsphoneguard.data.model.DailyUsage
import kotlinx.coroutines.flow.Flow

/**
 * 每日使用时长数据访问对象
 * 提供对 daily_usage 表的增删改查操作
 */
@Dao
interface DailyUsageDao {

    /**
     * 获取指定日期和包名的使用记录
     * @param date 日期（格式：yyyy-MM-dd）
     * @param packageName 应用包名
     * @return 使用记录，如果不存在返回null
     */
    @Query("SELECT * FROM daily_usage WHERE date = :date AND packageName = :packageName LIMIT 1")
    suspend fun getUsage(date: String, packageName: String): DailyUsage?

    /**
     * 获取指定日期和包名的使用记录（Flow版本）
     * @param date 日期（格式：yyyy-MM-dd）
     * @param packageName 应用包名
     * @return 使用记录的Flow流
     */
    @Query("SELECT * FROM daily_usage WHERE date = :date AND packageName = :packageName LIMIT 1")
    fun getUsageFlow(date: String, packageName: String): Flow<DailyUsage?>

    /**
     * 获取指定日期的所有使用记录
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 该日所有应用的使用记录列表
     */
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun getAllUsageForDate(date: String): Flow<List<DailyUsage>>

    /**
     * 获取指定包名的所有历史使用记录
     * @param packageName 应用包名
     * @return 该应用的所有历史使用记录
     */
    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName ORDER BY date DESC")
    fun getUsageHistory(packageName: String): Flow<List<DailyUsage>>

    /**
     * 插入或更新使用记录
     * @param usage 要插入的使用记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUsage(usage: DailyUsage)

    /**
     * 增加使用时长
     * @param date 日期
     * @param packageName 应用包名
     * @param seconds 要增加的秒数
     */
    @Query("""
        INSERT INTO daily_usage (date, packageName, usedTimeInSeconds, lastUpdated)
        VALUES (:date, :packageName, :seconds, :timestamp)
        ON CONFLICT(date, packageName) DO UPDATE SET
        usedTimeInSeconds = usedTimeInSeconds + :seconds,
        lastUpdated = :timestamp
    """)
    suspend fun addUsageTime(date: String, packageName: String, seconds: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * 更新使用时长（覆盖）
     * @param date 日期
     * @param packageName 应用包名
     * @param seconds 新的使用秒数
     */
    @Query("UPDATE daily_usage SET usedTimeInSeconds = :seconds, lastUpdated = :timestamp WHERE date = :date AND packageName = :packageName")
    suspend fun updateUsageTime(date: String, packageName: String, seconds: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * 删除指定日期的所有记录
     * @param date 日期
     */
    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun deleteUsageForDate(date: String)

    /**
     * 删除指定日期之前的所有记录
     * @param date 日期边界
     */
    @Query("DELETE FROM daily_usage WHERE date < :date")
    suspend fun deleteUsageBeforeDate(date: String)
}
