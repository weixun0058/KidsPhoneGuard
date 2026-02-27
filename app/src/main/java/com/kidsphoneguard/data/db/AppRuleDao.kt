package com.kidsphoneguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.RuleType
import kotlinx.coroutines.flow.Flow

/**
 * 应用规则数据访问对象
 * 提供对 app_rules 表的增删改查操作
 */
@Dao
interface AppRuleDao {

    /**
     * 获取所有应用规则
     * @return 所有规则的Flow流
     */
    @Query("SELECT * FROM app_rules")
    fun getAllRules(): Flow<List<AppRule>>

    /**
     * 获取指定包名的规则
     * @param packageName 应用包名
     * @return 对应的规则，如果不存在返回null
     */
    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getRuleByPackageName(packageName: String): AppRule?

    /**
     * 获取指定包名的规则（Flow版本）
     * @param packageName 应用包名
     * @return 对应规则的Flow流
     */
    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    fun getRuleByPackageNameFlow(packageName: String): Flow<AppRule?>

    /**
     * 插入或更新规则
     * @param rule 要插入的规则
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRule(rule: AppRule)

    /**
     * 删除指定包名的规则
     * @param packageName 应用包名
     */
    @Query("DELETE FROM app_rules WHERE packageName = :packageName")
    suspend fun deleteRule(packageName: String)

    /**
     * 获取所有黑名单/受限应用
     * @return 非ALLOW类型的规则列表
     */
    @Query("SELECT * FROM app_rules WHERE ruleType != 'ALLOW'")
    fun getRestrictedApps(): Flow<List<AppRule>>

    /**
     * 更新全局锁机状态
     * @param packageName 应用包名
     * @param locked 是否锁定
     */
    @Query("UPDATE app_rules SET isGlobalLocked = :locked WHERE packageName = :packageName")
    suspend fun updateGlobalLock(packageName: String, locked: Boolean)

    /**
     * 设置所有应用的全局锁机状态
     * @param locked 是否锁定
     */
    @Query("UPDATE app_rules SET isGlobalLocked = :locked")
    suspend fun setGlobalLockForAll(locked: Boolean)
}
