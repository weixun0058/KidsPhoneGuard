package com.kidsphoneguard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * 应用管控规则实体类
 * 存储每个应用的管控规则配置
 *
 * @property packageName 应用包名（主键）
 * @property ruleType 规则类型：ALLOW(放行), BLOCK(永久禁用), LIMIT(限时/限时段)
 * @property dailyAllowedMinutes 每日允许使用时长（分钟），仅当ruleType为LIMIT时有效
 * @property blockedTimeWindows 禁用时段列表，格式为 "HH:mm-HH:mm"，多个时段用逗号分隔
 * @property isGlobalLocked 全局一键锁机标志位
 * @property appName 应用名称（用于显示）
 */
@Entity(tableName = "app_rules")
@TypeConverters(RuleTypeConverter::class)
data class AppRule(
    @PrimaryKey
    val packageName: String,
    val ruleType: RuleType = RuleType.ALLOW,
    val limitMode: LimitMode = LimitMode.BOTH,
    val dailyAllowedMinutes: Int = 0,
    val blockedTimeWindows: String = "", // 格式: "22:00-07:00,14:00-15:00"
    val isGlobalLocked: Boolean = false,
    val appName: String = ""
)

/**
 * 规则类型枚举
 */
enum class RuleType {
    ALLOW,      // 0: 放行，不受限制
    BLOCK,      // 1: 永久禁用
    LIMIT       // 2: 限时/限时段可用
}

enum class LimitMode {
    BOTH,
    DURATION_ONLY,
    WINDOW_ONLY
}

/**
 * RuleType 类型转换器
 */
class RuleTypeConverter {
    @TypeConverter
    fun fromRuleType(ruleType: RuleType): Int = ruleType.ordinal

    @TypeConverter
    fun toRuleType(value: Int): RuleType = RuleType.entries[value]

    @TypeConverter
    fun fromLimitMode(limitMode: LimitMode): Int = limitMode.ordinal

    @TypeConverter
    fun toLimitMode(value: Int): LimitMode = LimitMode.entries[value]
}
