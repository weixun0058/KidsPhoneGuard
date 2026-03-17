package com.kidsphoneguard.data.repository

import com.kidsphoneguard.data.db.AppRuleDao
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuleRepositoryTest {

    @Test
    fun applyBatchRules_defaultMode_onlyWritesUnconfiguredApps() = runBlocking {
        val dao = FakeAppRuleDao(
            mutableMapOf(
                "com.test.configured" to AppRule(
                    packageName = "com.test.configured",
                    appName = "Configured",
                    ruleType = RuleType.BLOCK
                )
            )
        )
        val repository = AppRuleRepository(dao)

        val result = repository.applyBatchRules(
            inputs = listOf(
                AppRuleRepository.BatchRuleInput(
                    packageName = "com.test.configured",
                    appName = "Configured",
                    ruleType = RuleType.ALLOW
                ),
                AppRuleRepository.BatchRuleInput(
                    packageName = "com.test.new",
                    appName = "NewApp",
                    ruleType = RuleType.LIMIT,
                    limitMode = LimitMode.WINDOW_ONLY,
                    blockedTimeWindows = "22:00-07:00"
                )
            ),
            allowReconfigure = false
        )

        assertEquals(1, result.successCount)
        assertEquals(1, result.skippedItems.size)
        assertEquals(
            AppRuleRepository.BatchSkipReason.ALREADY_CONFIGURED,
            result.skippedItems.first().reason
        )
        assertTrue(dao.rules.containsKey("com.test.new"))
        assertEquals(LimitMode.WINDOW_ONLY, dao.rules["com.test.new"]?.limitMode)
        assertEquals(0, dao.rules["com.test.new"]?.dailyAllowedMinutes)
    }

    @Test
    fun applyBatchRules_reconfigureMode_overridesExistingRuleAndKeepsSingleRule() = runBlocking {
        val dao = FakeAppRuleDao(
            mutableMapOf(
                "com.test.app" to AppRule(
                    packageName = "com.test.app",
                    appName = "OldName",
                    ruleType = RuleType.BLOCK,
                    isGlobalLocked = true
                )
            )
        )
        val repository = AppRuleRepository(dao)

        val result = repository.applyBatchRules(
            inputs = listOf(
                AppRuleRepository.BatchRuleInput(
                    packageName = "com.test.app",
                    appName = "NewName",
                    ruleType = RuleType.LIMIT,
                    limitMode = LimitMode.DURATION_ONLY,
                    dailyAllowedMinutes = 45
                )
            ),
            allowReconfigure = true
        )

        assertEquals(1, result.successCount)
        assertTrue(result.skippedItems.isEmpty())
        assertEquals(1, dao.rules.size)
        assertEquals(RuleType.LIMIT, dao.rules["com.test.app"]?.ruleType)
        assertEquals(LimitMode.DURATION_ONLY, dao.rules["com.test.app"]?.limitMode)
        assertEquals(45, dao.rules["com.test.app"]?.dailyAllowedMinutes)
        assertEquals("", dao.rules["com.test.app"]?.blockedTimeWindows)
        assertEquals(true, dao.rules["com.test.app"]?.isGlobalLocked)
    }
}

private class FakeAppRuleDao(
    initialRules: MutableMap<String, AppRule> = mutableMapOf()
) : AppRuleDao {
    val rules: MutableMap<String, AppRule> = initialRules

    override fun getAllRules(): Flow<List<AppRule>> = flowOf(rules.values.toList())

    override suspend fun getRuleByPackageName(packageName: String): AppRule? = rules[packageName]

    override fun getRuleByPackageNameFlow(packageName: String): Flow<AppRule?> = flowOf(rules[packageName])

    override suspend fun insertOrUpdateRule(rule: AppRule) {
        rules[rule.packageName] = rule
    }

    override suspend fun insertOrUpdateRules(rules: List<AppRule>) {
        rules.forEach { this.rules[it.packageName] = it }
    }

    override suspend fun deleteRule(packageName: String) {
        rules.remove(packageName)
    }

    override suspend fun getConfiguredPackageNames(): List<String> = rules.keys.toList()

    override suspend fun getRulesByPackageNames(packageNames: List<String>): List<AppRule> {
        return packageNames.mapNotNull { rules[it] }
    }

    override fun getRestrictedApps(): Flow<List<AppRule>> {
        return flowOf(rules.values.filter { it.ruleType != RuleType.ALLOW })
    }

    override suspend fun updateGlobalLock(packageName: String, locked: Boolean) {
        val existing = rules[packageName] ?: return
        rules[packageName] = existing.copy(isGlobalLocked = locked)
    }

    override suspend fun setGlobalLockForAll(locked: Boolean) {
        rules.keys.toList().forEach { key ->
            rules[key] = rules[key]!!.copy(isGlobalLocked = locked)
        }
    }
}
