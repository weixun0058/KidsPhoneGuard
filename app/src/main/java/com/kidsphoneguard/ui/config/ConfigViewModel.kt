package com.kidsphoneguard.ui.config

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.DailyUsage
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.AppScanner
import com.kidsphoneguard.utils.TemporaryBonusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class ConfigUiState(
    val appRules: List<AppRule> = emptyList(),
    val todayUsageMap: Map<String, Long> = emptyMap(),
    val todayBonusMap: Map<String, Long> = emptyMap()
)

internal class ConfigDependencies(
    val appRules: Flow<List<AppRule>>,
    val todayUsage: Flow<List<DailyUsage>>,
    val getTodayBonusMap: (Collection<String>) -> Map<String, Long>,
    val saveRule: suspend (AppRule) -> Unit,
    val deleteRule: suspend (String) -> Unit,
    val adjustTodayMinutes: (String, Int) -> Unit,
    val resetTodayUsage: suspend (String) -> Unit,
    val clearTodayBonus: (String) -> Unit,
    val applyBatchRules: suspend (
        List<AppRuleRepository.BatchRuleInput>,
        Boolean
    ) -> AppRuleRepository.BatchApplyResult
)

private fun createProductionDependencies(): ConfigDependencies {
    val app = KidsPhoneGuardApp.instance
    val appRuleRepository = app.appRuleRepository
    val dailyUsageRepository = app.dailyUsageRepository
    val temporaryBonusManager = TemporaryBonusManager.getInstance(app)
    return ConfigDependencies(
        appRules = appRuleRepository.getAllRules(),
        todayUsage = dailyUsageRepository.getAllUsageForDate(dailyUsageRepository.getTodayDate()),
        getTodayBonusMap = temporaryBonusManager::getTodayBonusMap,
        saveRule = appRuleRepository::saveRule,
        deleteRule = appRuleRepository::deleteRule,
        adjustTodayMinutes = { packageName, minutes ->
            temporaryBonusManager.adjustTodayMinutes(packageName, minutes)
        },
        resetTodayUsage = dailyUsageRepository::resetTodayUsage,
        clearTodayBonus = temporaryBonusManager::clearTodayBonus,
        applyBatchRules = appRuleRepository::applyBatchRules
    )
}

/**
 * 家长配置页的长生命周期状态与规则写入入口。
 *
 * 对话框开关和列表展示方式属于 Compose 界面的短暂状态，因此不放在这里；规则、当天用量和
 * 当天时间调整则由本类统一观察，避免每个 Composable 分别订阅数据源。
 */
class ConfigViewModel internal constructor(
    private val dependencies: ConfigDependencies,
    scopeOverride: CoroutineScope? = null
) : ViewModel() {
    constructor() : this(createProductionDependencies())

    companion object {
        private const val TAG = "ConfigViewModel"
    }

    private val configScope = scopeOverride ?: viewModelScope
    private val bonusRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ConfigUiState> = combine(
        dependencies.appRules,
        dependencies.todayUsage,
        bonusRefresh
    ) { rules, usageRecords, _ ->
        rules to usageRecords.associate { it.packageName to it.usedTimeInSeconds }
    }.flatMapLatest { (rules, usageMap) ->
        flow {
            emit(
                ConfigUiState(
                    appRules = rules,
                    todayUsageMap = usageMap,
                    todayBonusMap = dependencies.getTodayBonusMap(
                        rules.map { it.packageName }
                    )
                )
            )
        }
    }.stateIn(
        scope = configScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConfigUiState()
    )

    fun saveRule(
        packageName: String,
        appName: String,
        ruleType: RuleType,
        limitMode: LimitMode,
        minutes: Int,
        timeWindows: String,
        isGlobalLocked: Boolean
    ) {
        launchConfigAction("save rule: $packageName") {
            dependencies.saveRule(
                AppRule(
                    packageName = packageName,
                    appName = appName,
                    ruleType = ruleType,
                    limitMode = if (ruleType == RuleType.LIMIT) limitMode else LimitMode.BOTH,
                    dailyAllowedMinutes = if (ruleType == RuleType.LIMIT) minutes else 0,
                    blockedTimeWindows = if (ruleType == RuleType.LIMIT) timeWindows else "",
                    isGlobalLocked = isGlobalLocked
                )
            )
        }
    }

    fun deleteRule(packageName: String) {
        launchConfigAction("delete rule: $packageName") {
            dependencies.deleteRule(packageName)
        }
    }

    fun adjustTodayMinutes(packageName: String, minutes: Int) {
        launchConfigAction("adjust today time: $packageName") {
            dependencies.adjustTodayMinutes(packageName, minutes)
            bonusRefresh.value++
        }
    }

    fun resetTodayUsage(packageName: String) {
        launchConfigAction("reset today usage: $packageName") {
            dependencies.resetTodayUsage(packageName)
            dependencies.clearTodayBonus(packageName)
            bonusRefresh.value++
            Log.d(TAG, "reset_today_usage package=$packageName")
        }
    }

    fun applyBatchRules(
        selectedApps: List<AppScanner.AppInfo>,
        ruleType: RuleType,
        limitMode: LimitMode,
        minutes: Int,
        timeWindows: String,
        allowReconfigure: Boolean,
        onApplied: (AppRuleRepository.BatchApplyResult) -> Unit
    ) {
        launchConfigAction("apply batch rules") {
            val currentRules = uiState.value.appRules
            val selectedPackageSet = selectedApps.map { it.packageName }.toSet()
            val toRemovePackages = if (allowReconfigure) {
                currentRules.filter { rule ->
                    selectedPackageSet.contains(rule.packageName) && when (ruleType) {
                        RuleType.ALLOW -> rule.ruleType == RuleType.ALLOW
                        RuleType.BLOCK -> rule.ruleType == RuleType.BLOCK
                        RuleType.LIMIT -> rule.ruleType == RuleType.LIMIT && rule.limitMode == limitMode
                    }
                }.map { it.packageName }
            } else {
                emptyList()
            }
            toRemovePackages.forEach { packageName ->
                dependencies.deleteRule(packageName)
            }
            val inputs = selectedApps
                .filterNot { it.packageName in toRemovePackages }
                .map {
                    AppRuleRepository.BatchRuleInput(
                        packageName = it.packageName,
                        appName = it.appName,
                        ruleType = ruleType,
                        limitMode = limitMode,
                        dailyAllowedMinutes = minutes,
                        blockedTimeWindows = timeWindows
                    )
                }
            onApplied(
                dependencies.applyBatchRules(inputs, allowReconfigure)
                    .copy(removedCount = toRemovePackages.toSet().size)
            )
        }
    }

    private fun launchConfigAction(
        description: String,
        action: suspend () -> Unit
    ) {
        configScope.launch {
            try {
                action()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to $description", exception)
            }
        }
    }
}
