package com.kidsphoneguard.ui.config

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.AppScanner
import com.kidsphoneguard.utils.TemporaryBonusManager
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * 家长配置页的长生命周期状态与规则写入入口。
 *
 * 对话框开关和列表展示方式属于 Compose 界面的短暂状态，因此不放在这里；规则、当天用量和
 * 临时奖励则由本类统一观察，避免每个 Composable 分别订阅数据源。
 */
class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ConfigViewModel"
    }

    private val app = application as KidsPhoneGuardApp
    private val temporaryBonusManager = TemporaryBonusManager.getInstance(application)
    private val bonusRefresh = MutableStateFlow(0)

    private val appRules = app.appRuleRepository.getAllRules()
    private val todayUsage = app.dailyUsageRepository
        .getAllUsageForDate(app.dailyUsageRepository.getTodayDate())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ConfigUiState> = combine(
        appRules,
        todayUsage,
        bonusRefresh
    ) { rules, usageRecords, _ ->
        rules to usageRecords.associate { it.packageName to it.usedTimeInSeconds }
    }.flatMapLatest { (rules, usageMap) ->
        flow {
            emit(
                ConfigUiState(
                    appRules = rules,
                    todayUsageMap = usageMap,
                    todayBonusMap = temporaryBonusManager.getTodayBonusMap(
                        rules.map { it.packageName }
                    )
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
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
            app.appRuleRepository.saveRule(
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
            app.appRuleRepository.deleteRule(packageName)
        }
    }

    fun grantTodayBonus(packageName: String, minutes: Int) {
        launchConfigAction("grant today bonus: $packageName") {
            temporaryBonusManager.addTodayBonusMinutes(packageName, minutes)
            bonusRefresh.value++
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
                app.appRuleRepository.deleteRule(packageName)
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
                app.appRuleRepository.applyBatchRules(
                    inputs = inputs,
                    allowReconfigure = allowReconfigure
                ).copy(removedCount = toRemovePackages.toSet().size)
            )
        }
    }

    private fun launchConfigAction(
        description: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                action()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to $description", exception)
            }
        }
    }
}
