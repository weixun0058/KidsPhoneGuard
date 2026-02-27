package com.kidsphoneguard.data.repository

import com.kidsphoneguard.data.db.DailyUsageDao
import com.kidsphoneguard.data.model.DailyUsage
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 每日使用时长仓库
 * 封装对使用时长数据的访问，为ViewModel提供统一接口
 *
 * @property dailyUsageDao 每日使用时长DAO
 */
class DailyUsageRepository(private val dailyUsageDao: DailyUsageDao) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 获取今天的日期字符串
     * @return 格式化的日期字符串
     */
    fun getTodayDate(): String = LocalDate.now().format(dateFormatter)

    /**
     * 获取指定日期和包名的使用记录
     * @param date 日期（格式：yyyy-MM-dd）
     * @param packageName 应用包名
     * @return 使用记录，如果不存在返回null
     */
    suspend fun getUsage(date: String, packageName: String): DailyUsage? {
        return dailyUsageDao.getUsage(date, packageName)
    }

    /**
     * 获取指定日期和包名的使用记录（Flow版本）
     * @param date 日期（格式：yyyy-MM-dd）
     * @param packageName 应用包名
     * @return 使用记录的Flow流
     */
    fun getUsageFlow(date: String, packageName: String): Flow<DailyUsage?> {
        return dailyUsageDao.getUsageFlow(date, packageName)
    }

    /**
     * 获取今天指定应用的使用记录
     * @param packageName 应用包名
     * @return 使用记录的Flow流
     */
    fun getTodayUsageFlow(packageName: String): Flow<DailyUsage?> {
        return dailyUsageDao.getUsageFlow(getTodayDate(), packageName)
    }

    /**
     * 获取指定日期的所有使用记录
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 该日所有应用的使用记录列表
     */
    fun getAllUsageForDate(date: String): Flow<List<DailyUsage>> {
        return dailyUsageDao.getAllUsageForDate(date)
    }

    /**
     * 获取指定包名的所有历史使用记录
     * @param packageName 应用包名
     * @return 该应用的所有历史使用记录
     */
    fun getUsageHistory(packageName: String): Flow<List<DailyUsage>> {
        return dailyUsageDao.getUsageHistory(packageName)
    }

    /**
     * 增加使用时长
     * @param date 日期
     * @param packageName 应用包名
     * @param seconds 要增加的秒数
     */
    suspend fun addUsageTime(date: String, packageName: String, seconds: Long) {
        dailyUsageDao.addUsageTime(date, packageName, seconds)
    }

    /**
     * 增加今天的使用时长
     * @param packageName 应用包名
     * @param seconds 要增加的秒数
     */
    suspend fun addTodayUsageTime(packageName: String, seconds: Long) {
        dailyUsageDao.addUsageTime(getTodayDate(), packageName, seconds)
    }

    /**
     * 更新使用时长（覆盖）
     * @param date 日期
     * @param packageName 应用包名
     * @param seconds 新的使用秒数
     */
    suspend fun updateUsageTime(date: String, packageName: String, seconds: Long) {
        dailyUsageDao.updateUsageTime(date, packageName, seconds)
    }

    /**
     * 获取今天指定应用的使用秒数
     * @param packageName 应用包名
     * @return 使用秒数，如果没有记录返回0
     */
    suspend fun getTodayUsageSeconds(packageName: String): Long {
        val usage = dailyUsageDao.getUsage(getTodayDate(), packageName)
        return usage?.usedTimeInSeconds ?: 0
    }

    /**
     * 清理过期数据（保留最近30天）
     */
    suspend fun cleanupOldData() {
        val thirtyDaysAgo = LocalDate.now().minusDays(30).format(dateFormatter)
        dailyUsageDao.deleteUsageBeforeDate(thirtyDaysAgo)
    }
}
