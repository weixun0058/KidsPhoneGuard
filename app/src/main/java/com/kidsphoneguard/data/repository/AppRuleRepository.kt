package com.kidsphoneguard.data.repository

import com.kidsphoneguard.data.db.AppRuleDao
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.utils.WhitelistManager
import kotlinx.coroutines.flow.Flow

/**
 * 应用规则仓库
 * 封装对应用规则数据的访问，为ViewModel提供统一接口
 *
 * @property appRuleDao 应用规则DAO
 */
class AppRuleRepository(private val appRuleDao: AppRuleDao) {

    data class BatchRuleInput(
        val packageName: String,
        val appName: String,
        val ruleType: RuleType,
        val limitMode: LimitMode = LimitMode.BOTH,
        val dailyAllowedMinutes: Int = 0,
        val blockedTimeWindows: String = ""
    )

    enum class BatchSkipReason {
        EMPTY_PACKAGE,
        SYSTEM_WHITELIST,
        ALREADY_CONFIGURED,
        DUPLICATE_REQUEST
    }

    data class BatchSkipItem(
        val packageName: String,
        val appName: String,
        val reason: BatchSkipReason
    )

    data class BatchApplyResult(
        val successCount: Int,
        val skippedItems: List<BatchSkipItem>,
        val removedCount: Int = 0
    )

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

    suspend fun getConfiguredPackageNames(): Set<String> {
        return appRuleDao.getConfiguredPackageNames().toSet()
    }

    suspend fun applyBatchRules(
        inputs: List<BatchRuleInput>,
        allowReconfigure: Boolean
    ): BatchApplyResult {
        val skippedItems = mutableListOf<BatchSkipItem>()
        val uniqueInputs = linkedMapOf<String, BatchRuleInput>()

        inputs.forEach { input ->
            val packageName = input.packageName.trim()
            if (packageName.isBlank()) {
                skippedItems += BatchSkipItem(
                    packageName = input.packageName,
                    appName = input.appName,
                    reason = BatchSkipReason.EMPTY_PACKAGE
                )
            } else if (WhitelistManager.isInWhitelist(packageName)) {
                skippedItems += BatchSkipItem(
                    packageName = packageName,
                    appName = input.appName,
                    reason = BatchSkipReason.SYSTEM_WHITELIST
                )
            } else if (uniqueInputs.containsKey(packageName)) {
                skippedItems += BatchSkipItem(
                    packageName = packageName,
                    appName = input.appName,
                    reason = BatchSkipReason.DUPLICATE_REQUEST
                )
            } else {
                uniqueInputs[packageName] = input.copy(packageName = packageName)
            }
        }

        val packageNames = uniqueInputs.keys.toList()
        if (packageNames.isEmpty()) {
            return BatchApplyResult(successCount = 0, skippedItems = skippedItems)
        }

        val existingRules = appRuleDao.getRulesByPackageNames(packageNames).associateBy { it.packageName }
        val rulesToSave = mutableListOf<AppRule>()

        uniqueInputs.values.forEach { input ->
            val existing = existingRules[input.packageName]
            if (!allowReconfigure && existing != null) {
                skippedItems += BatchSkipItem(
                    packageName = input.packageName,
                    appName = input.appName,
                    reason = BatchSkipReason.ALREADY_CONFIGURED
                )
            } else {
                val appName = input.appName.ifBlank { existing?.appName.orEmpty() }
                rulesToSave += AppRule(
                    packageName = input.packageName,
                    appName = appName,
                    ruleType = input.ruleType,
                    limitMode = if (input.ruleType == RuleType.LIMIT) input.limitMode else LimitMode.BOTH,
                    dailyAllowedMinutes = if (input.ruleType == RuleType.LIMIT) input.dailyAllowedMinutes else 0,
                    blockedTimeWindows = if (input.ruleType == RuleType.LIMIT) input.blockedTimeWindows else "",
                    isGlobalLocked = existing?.isGlobalLocked ?: false
                )
            }
        }

        if (rulesToSave.isNotEmpty()) {
            appRuleDao.insertOrUpdateRules(rulesToSave)
        }

        return BatchApplyResult(
            successCount = rulesToSave.size,
            skippedItems = skippedItems
        )
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
        limitMode: LimitMode = LimitMode.BOTH,
        dailyAllowedMinutes: Int = 0,
        blockedTimeWindows: String = "",
        appName: String = ""
    ) {
        val existingRule = appRuleDao.getRuleByPackageName(packageName)
        val rule = existingRule?.copy(
            ruleType = ruleType,
            limitMode = if (ruleType == RuleType.LIMIT) limitMode else LimitMode.BOTH,
            dailyAllowedMinutes = if (ruleType == RuleType.LIMIT) dailyAllowedMinutes else 0,
            blockedTimeWindows = if (ruleType == RuleType.LIMIT) blockedTimeWindows else ""
        ) ?: AppRule(
            packageName = packageName,
            ruleType = ruleType,
            limitMode = if (ruleType == RuleType.LIMIT) limitMode else LimitMode.BOTH,
            dailyAllowedMinutes = if (ruleType == RuleType.LIMIT) dailyAllowedMinutes else 0,
            blockedTimeWindows = if (ruleType == RuleType.LIMIT) blockedTimeWindows else "",
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
