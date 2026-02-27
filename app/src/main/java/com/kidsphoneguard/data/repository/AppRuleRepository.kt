package com.kidsphoneguard.data.repository

import com.kidsphoneguard.data.db.AppRuleDao
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.RuleType
import kotlinx.coroutines.flow.Flow

/**
 * 应用规则仓库
 * 封装对应用规则数据的访问，为ViewModel提供统一接口
 *
 * @property appRuleDao 应用规则DAO
 */
class AppRuleRepository(private val appRuleDao: AppRuleDao) {

    /**
     * 获取所有应用规则
     * @return 所有规则的Flow流
     */
    fun getAllRules(): Flow<List<AppRule>> = appRuleDao.getAllRules()

    /**
     * 获取指定包名的规则
     * @param packageName 应用包名
     * @return 对应的规则，如果不存在返回null
     */
    suspend fun getRuleByPackageName(packageName: String): AppRule? {
        return appRuleDao.getRuleByPackageName(packageName)
    }

    /**
     * 获取指定包名的规则（Flow版本）
     * @param packageName 应用包名
     * @return 对应规则的Flow流
     */
    fun getRuleByPackageNameFlow(packageName: String): Flow<AppRule?> {
        return appRuleDao.getRuleByPackageNameFlow(packageName)
    }

    /**
     * 添加或更新应用规则
     * @param rule 要保存的规则
     */
    suspend fun saveRule(rule: AppRule) {
        appRuleDao.insertOrUpdateRule(rule)
    }

    /**
     * 删除指定包名的规则
     * @param packageName 应用包名
     */
    suspend fun deleteRule(packageName: String) {
        appRuleDao.deleteRule(packageName)
    }

    /**
     * 获取所有受限应用（非ALLOW类型）
     * @return 受限应用规则列表的Flow流
     */
    fun getRestrictedApps(): Flow<List<AppRule>> = appRuleDao.getRestrictedApps()

    /**
     * 设置应用规则类型
     * @param packageName 应用包名
     * @param ruleType 规则类型
     * @param dailyAllowedMinutes 每日允许时长（分钟）
     * @param blockedTimeWindows 禁用时段
     */
    suspend fun setRuleType(
        packageName: String,
        ruleType: RuleType,
        dailyAllowedMinutes: Int = 0,
        blockedTimeWindows: String = "",
        appName: String = ""
    ) {
        val existingRule = appRuleDao.getRuleByPackageName(packageName)
        val rule = existingRule?.copy(
            ruleType = ruleType,
            dailyAllowedMinutes = dailyAllowedMinutes,
            blockedTimeWindows = blockedTimeWindows
        ) ?: AppRule(
            packageName = packageName,
            ruleType = ruleType,
            dailyAllowedMinutes = dailyAllowedMinutes,
            blockedTimeWindows = blockedTimeWindows,
            appName = appName
        )
        appRuleDao.insertOrUpdateRule(rule)
    }

    /**
     * 更新全局锁机状态
     * @param packageName 应用包名
     * @param locked 是否锁定
     */
    suspend fun updateGlobalLock(packageName: String, locked: Boolean) {
        appRuleDao.updateGlobalLock(packageName, locked)
    }

    /**
     * 设置所有应用的全局锁机状态
     * @param locked 是否锁定
     */
    suspend fun setGlobalLockForAll(locked: Boolean) {
        appRuleDao.setGlobalLockForAll(locked)
    }
}
