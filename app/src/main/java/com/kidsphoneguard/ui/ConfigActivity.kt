package com.kidsphoneguard.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.AppScanner
import com.kidsphoneguard.utils.SettingsManager
import com.kidsphoneguard.utils.WhitelistManager
import com.kidsphoneguard.ui.config.AppSelectorDialog
import com.kidsphoneguard.ui.config.ConfigViewModel
import com.kidsphoneguard.ui.config.RuleUsageFormatter
import com.kidsphoneguard.ui.config.TimeWindowCodec
import com.kidsphoneguard.ui.config.ConfigRuleCard
import com.kidsphoneguard.ui.config.ConfigRuleGridCard
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 配置Activity - 家长配置页面
 * 用于设置应用管控规则
 */
class ConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConfigScreen()
                }
            }
        }
    }
}
/**
 * 配置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val configViewModel: ConfigViewModel = viewModel()
    val configState by configViewModel.uiState.collectAsState()

    val appRules = configState.appRules
    var showAddDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AppRule?>(null) }
    var batchApplyResult by remember { mutableStateOf<AppRuleRepository.BatchApplyResult?>(null) }
    var useRuleGridView by remember { mutableStateOf(true) }
    var longPressRule by remember { mutableStateOf<AppRule?>(null) }
    LaunchedEffect(Unit) {
        SettingsManager.getInstance(context).clearSetupSettingsAccess()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("家长配置") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            GlobalModeControlRow()

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(context, SetupWizardActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开配置向导")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("添加应用规则")
                }
                OutlinedButton(
                    onClick = { showBatchDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("批量配置应用规则")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已配置规则 (${appRules.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { useRuleGridView = !useRuleGridView }
                ) {
                    Text(if (useRuleGridView) "详情模式" else "图标模式")
                }
            }

            if (!useRuleGridView) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        WeChatVideoControlCard()
                    }
                    items(appRules) { rule ->
                        ConfigRuleCard(
                            rule = rule,
                            usedSeconds = configState.todayUsageMap[rule.packageName] ?: 0L,
                            bonusSeconds = configState.todayBonusMap[rule.packageName] ?: 0L,
                            onEdit = {
                                editingRule = rule
                            },
                            onDelete = { configViewModel.deleteRule(rule.packageName) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        WeChatVideoControlCard()
                    }
                    items(appRules.size) { index ->
                        val rule = appRules[index]
                        ConfigRuleGridCard(
                            rule = rule,
                            usedSeconds = configState.todayUsageMap[rule.packageName] ?: 0L,
                            bonusSeconds = configState.todayBonusMap[rule.packageName] ?: 0L,
                            onLongPress = { longPressRule = rule }
                        )
                    }
                }
            }
        }
    }

    // 添加规则对话框
    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { packageName, appName, ruleType, limitMode, minutes, timeWindows ->
                configViewModel.saveRule(
                    packageName = packageName,
                    appName = appName,
                    ruleType = ruleType,
                    limitMode = limitMode,
                    minutes = minutes,
                    timeWindows = timeWindows,
                    isGlobalLocked = false
                )
                showAddDialog = false
            }
        )
    }

    if (editingRule != null) {
        AddRuleDialog(
            title = "修改应用规则",
            confirmText = "保存",
            initialRule = editingRule,
            initialUsedSeconds = configState.todayUsageMap[editingRule?.packageName.orEmpty()] ?: 0L,
            initialBonusSeconds = configState.todayBonusMap[editingRule?.packageName.orEmpty()] ?: 0L,
            allowAppSelection = false,
            onDismiss = { editingRule = null },
            onGrantTodayBonus = { packageName, minutes ->
                configViewModel.grantTodayBonus(packageName, minutes)
            },
            onConfirm = { packageName, appName, ruleType, limitMode, minutes, timeWindows ->
                configViewModel.saveRule(
                    packageName = packageName,
                    appName = appName,
                    ruleType = ruleType,
                    limitMode = limitMode,
                    minutes = minutes,
                    timeWindows = timeWindows,
                    isGlobalLocked = editingRule?.isGlobalLocked ?: false
                )
                editingRule = null
            }
        )
    }

    if (showBatchDialog) {
        BatchRuleDialog(
            configuredPackages = appRules.map { it.packageName }.toSet(),
            configuredRules = appRules.associateBy { it.packageName },
            onDismiss = { showBatchDialog = false },
            onConfirm = { selectedApps, ruleType, limitMode, minutes, timeWindows, allowReconfigure ->
                configViewModel.applyBatchRules(
                    selectedApps = selectedApps,
                    ruleType = ruleType,
                    limitMode = limitMode,
                    minutes = minutes,
                    timeWindows = timeWindows,
                    allowReconfigure = allowReconfigure,
                    onApplied = { batchApplyResult = it }
                )
                showBatchDialog = false
            }
        )
    }

    if (batchApplyResult != null) {
        BatchApplyResultDialog(
            result = batchApplyResult!!,
            onDismiss = { batchApplyResult = null }
        )
    }

    if (longPressRule != null) {
        val targetRule = longPressRule!!
        AlertDialog(
            onDismissRequest = { longPressRule = null },
            title = { Text(targetRule.appName.ifBlank { targetRule.packageName }) },
            text = { Text("请选择操作") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingRule = targetRule
                        longPressRule = null
                    }
                ) {
                    Text("修改")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        configViewModel.deleteRule(targetRule.packageName)
                        longPressRule = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            }
        )
    }
}
