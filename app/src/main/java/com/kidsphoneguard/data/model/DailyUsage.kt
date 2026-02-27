package com.kidsphoneguard.data.model

import androidx.room.Entity

/**
 * 应用每日使用时长统计实体类
 * 存储每个应用每天的使用时长
 *
 * @property date 日期（格式：yyyy-MM-dd）
 * @property packageName 应用包名
 * @property usedTimeInSeconds 累计使用秒数
 * @property lastUpdated 最后更新时间戳
 */
@Entity(
    tableName = "daily_usage",
    primaryKeys = ["date", "packageName"]
)
data class DailyUsage(
    val date: String,
    val packageName: String,
    val usedTimeInSeconds: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
