package com.kidsphoneguard.ui.config

import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.DailyUsage
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.AppScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigViewModelTest {

    @Test
    fun uiState_combinesRulesUsageAndTodayBonus() = runBlocking {
        val fake = FakeConfigData()
        val rule = AppRule(
            packageName = "com.example.game",
            appName = "Game",
            ruleType = RuleType.LIMIT,
            dailyAllowedMinutes = 30
        )
        fake.rules.value = listOf(rule)
        fake.usage.value = listOf(
            DailyUsage(
                date = "2026-07-19",
                packageName = rule.packageName,
                usedTimeInSeconds = 420L
            )
        )
        fake.bonuses[rule.packageName] = 600L
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)

        try {
            val state = viewModel.awaitState { it.appRules == listOf(rule) }

            assertEquals(420L, state.todayUsageMap[rule.packageName])
            assertEquals(600L, state.todayBonusMap[rule.packageName])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun saveRule_nonLimitRuleClearsLimitOnlyFields() {
        val fake = FakeConfigData()
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)

        try {
            viewModel.saveRule(
                packageName = "com.example.video",
                appName = "Video",
                ruleType = RuleType.BLOCK,
                limitMode = LimitMode.WINDOW_ONLY,
                minutes = 45,
                timeWindows = "22:00-07:00",
                isGlobalLocked = true
            )

            val saved = fake.savedRules.single()
            assertEquals(RuleType.BLOCK, saved.ruleType)
            assertEquals(LimitMode.BOTH, saved.limitMode)
            assertEquals(0, saved.dailyAllowedMinutes)
            assertEquals("", saved.blockedTimeWindows)
            assertTrue(saved.isGlobalLocked)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteRule_delegatesPackageName() {
        val fake = FakeConfigData()
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)

        try {
            viewModel.deleteRule("com.example.deleted")

            assertEquals(listOf("com.example.deleted"), fake.deletedPackages)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun adjustTodayMinutes_refreshesPositiveAdjustmentInUiState() = runBlocking {
        val fake = FakeConfigData()
        val packageName = "com.example.reader"
        fake.rules.value = listOf(AppRule(packageName = packageName, appName = "Reader"))
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)

        try {
            viewModel.awaitState { it.appRules.any { rule -> rule.packageName == packageName } }
            viewModel.adjustTodayMinutes(packageName, 15)

            val state = viewModel.awaitState { it.todayBonusMap[packageName] == 900L }
            assertEquals(900L, state.todayBonusMap[packageName])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun adjustTodayMinutes_supportsNegativePunishment() = runBlocking {
        val fake = FakeConfigData()
        val packageName = "com.example.game"
        fake.rules.value = listOf(AppRule(packageName = packageName, appName = "Game"))
        fake.bonuses[packageName] = 5 * 60L
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)

        try {
            viewModel.awaitState { it.todayBonusMap[packageName] == 5 * 60L }
            viewModel.adjustTodayMinutes(packageName, -15)

            val state = viewModel.awaitState { it.todayBonusMap[packageName] == -10 * 60L }
            assertEquals(-10 * 60L, state.todayBonusMap[packageName])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun resetTodayUsage_clearsUsageAndBonus() = runBlocking {
        val fake = FakeConfigData()
        val packageName = "com.example.game"
        fake.rules.value = listOf(AppRule(packageName = packageName, appName = "Game"))
        fake.usage.value = listOf(
            DailyUsage(
                date = "2026-07-23",
                packageName = packageName,
                usedTimeInSeconds = 1_548L
            )
        )
        fake.bonuses[packageName] = 1_560L
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)

        try {
            viewModel.awaitState { it.todayUsageMap[packageName] == 1_548L }
            viewModel.resetTodayUsage(packageName)

            val state = viewModel.awaitState {
                it.todayUsageMap[packageName] == 0L &&
                    it.todayBonusMap[packageName] == 0L
            }
            assertEquals(0L, state.todayUsageMap[packageName])
            assertEquals(0L, state.todayBonusMap[packageName])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun applyBatchRules_reconfigureSameGroupRemovesRuleInsteadOfReapplying() = runBlocking {
        val fake = FakeConfigData()
        val packageName = "com.example.allowed"
        fake.rules.value = listOf(
            AppRule(
                packageName = packageName,
                appName = "Allowed",
                ruleType = RuleType.ALLOW
            )
        )
        val scope = testScope()
        val viewModel = ConfigViewModel(fake.dependencies(), scope)
        var appliedResult: AppRuleRepository.BatchApplyResult? = null

        try {
            viewModel.awaitState { it.appRules.any { rule -> rule.packageName == packageName } }
            viewModel.applyBatchRules(
                selectedApps = listOf(
                    AppScanner.AppInfo(
                        packageName = packageName,
                        appName = "Allowed",
                        icon = null,
                        isSystemApp = false
                    )
                ),
                ruleType = RuleType.ALLOW,
                limitMode = LimitMode.BOTH,
                minutes = 0,
                timeWindows = "",
                allowReconfigure = true,
                onApplied = { appliedResult = it }
            )

            assertEquals(listOf(packageName), fake.deletedPackages)
            assertTrue(fake.batchCalls.single().inputs.isEmpty())
            assertTrue(fake.batchCalls.single().allowReconfigure)
            assertNotNull(appliedResult)
            assertEquals(1, appliedResult?.removedCount)
        } finally {
            scope.cancel()
        }
    }

    private fun testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private suspend fun ConfigViewModel.awaitState(
        predicate: (ConfigUiState) -> Boolean
    ): ConfigUiState = withTimeout(1_000L) {
        uiState.first(predicate)
    }

    private class FakeConfigData {
        data class BatchCall(
            val inputs: List<AppRuleRepository.BatchRuleInput>,
            val allowReconfigure: Boolean
        )

        val rules = MutableStateFlow<List<AppRule>>(emptyList())
        val usage = MutableStateFlow<List<DailyUsage>>(emptyList())
        val bonuses = mutableMapOf<String, Long>()
        val savedRules = mutableListOf<AppRule>()
        val deletedPackages = mutableListOf<String>()
        val batchCalls = mutableListOf<BatchCall>()

        fun dependencies(): ConfigDependencies = ConfigDependencies(
            appRules = rules,
            todayUsage = usage,
            getTodayBonusMap = { packageNames ->
                packageNames.associateWith { packageName -> bonuses[packageName] ?: 0L }
            },
            saveRule = { rule -> savedRules += rule },
            deleteRule = { packageName -> deletedPackages += packageName },
            adjustTodayMinutes = { packageName, minutes ->
                bonuses[packageName] = bonuses.getOrDefault(packageName, 0L) + minutes * 60L
            },
            resetTodayUsage = { packageName ->
                usage.value = usage.value.map { record ->
                    if (record.packageName == packageName) {
                        record.copy(usedTimeInSeconds = 0L)
                    } else {
                        record
                    }
                }
            },
            clearTodayBonus = { packageName ->
                bonuses.remove(packageName)
            },
            applyBatchRules = { inputs, allowReconfigure ->
                batchCalls += BatchCall(inputs, allowReconfigure)
                AppRuleRepository.BatchApplyResult(
                    successCount = inputs.size,
                    skippedItems = emptyList()
                )
            }
        )
    }
}
