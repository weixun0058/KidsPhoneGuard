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
                        RuleCard(
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
                        RuleGridCard(
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

@Composable
private fun SetupWizardEntryCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "基础权限与后台保活",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "系统设置入口已收进家长配置。需要重新配置无障碍、悬浮窗、使用统计、电池优化或品牌后台保活时，请从这里进入。",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开配置向导")
            }
        }
    }
}

@Composable
fun GlobalModeControlRow() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }

    var refreshKey by remember { mutableStateOf(0) }
    var isUnlocked by remember(refreshKey) { mutableStateOf(settingsManager.isGlobalUnlockEnabled()) }
    var isLocked by remember(refreshKey) { mutableStateOf(settingsManager.isGlobalLockEnabled()) }

    androidx.compose.runtime.SideEffect {
        val currentUnlockState = settingsManager.isGlobalUnlockEnabled()
        val currentLockState = settingsManager.isGlobalLockEnabled()
        if (currentUnlockState != isUnlocked) {
            isUnlocked = currentUnlockState
            android.util.Log.d("GlobalUnlock", "State refreshed to: $currentUnlockState")
        }
        if (currentLockState != isLocked) {
            isLocked = currentLockState
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8FAFC)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "全局模式",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "全局解锁",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            text = "系统维护时使用",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = isUnlocked,
                        onCheckedChange = { checked ->
                            isUnlocked = checked
                            settingsManager.setGlobalUnlock(checked)
                            if (checked) {
                                isLocked = false
                                settingsManager.setGlobalLock(false)
                            }
                            refreshKey++
                            android.util.Log.d("GlobalUnlock", "Global unlock set to: $checked")
                        }
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "全局锁机",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFD32F2F)
                        )
                        Text(
                            text = "全拦截",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = isLocked,
                        onCheckedChange = { checked ->
                            isLocked = checked
                            settingsManager.setGlobalLock(checked)
                            if (checked) {
                                isUnlocked = false
                                settingsManager.setGlobalUnlock(false)
                            }
                            refreshKey++
                            android.util.Log.d("GlobalLock", "Global lock set to: $checked")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    isUnlocked -> "当前：全局解锁。此状态下允许进入系统设置维护。"
                    isLocked -> "当前：全局锁机"
                    else -> "当前：规则模式"
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun WeChatVideoControlCard() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var enabled by remember { mutableStateOf(settingsManager.isWeChatFinderBlockEnabled()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF7FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "拦截微信视频号",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                Text(
                    text = "只拦截视频号页面，不影响微信聊天列表和普通聊天。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    settingsManager.setWeChatFinderBlockEnabled(checked)
                    android.util.Log.d("WeChatVideoControl", "WeChat finder block set to: $checked")
                }
            )
        }
    }
}

/**
 * 规则卡片
 */
@Composable
fun RuleCard(
    rule: AppRule,
    usedSeconds: Long,
    bonusSeconds: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (rule.ruleType) {
                RuleType.ALLOW -> Color(0xFFE8F5E9)
                RuleType.BLOCK -> Color(0xFFFFEBEE)
                RuleType.LIMIT -> Color(0xFFFFF3E0)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.appName.ifEmpty { rule.packageName },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rule.packageName,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Row {
                    TextButton(onClick = onEdit) {
                        Text("修改")
                    }
                    TextButton(onClick = onDelete) {
                        Text("删除", color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 规则详情
            when (rule.ruleType) {
                RuleType.ALLOW -> {
                    Text("规则: 放行", color = Color(0xFF4CAF50))
                }
                RuleType.BLOCK -> {
                    Text("规则: 永久禁用", color = Color(0xFFF44336))
                }
                RuleType.LIMIT -> {
                    Column {
                        Text(
                            text = when (rule.limitMode) {
                                LimitMode.BOTH -> "模式: 限时长+限时段"
                                LimitMode.DURATION_ONLY -> "模式: 仅限时长"
                                LimitMode.WINDOW_ONLY -> "模式: 仅限时段"
                            }
                        )
                        if (rule.dailyAllowedMinutes > 0) {
                            Text("每日限制: ${rule.dailyAllowedMinutes} 分钟")
                        }
                        val usageSummary = RuleUsageFormatter.summary(rule, usedSeconds, bonusSeconds)
                        if (usageSummary.isNotEmpty()) {
                            Text(usageSummary, color = Color(0xFF5D4037))
                        }
                        if (rule.blockedTimeWindows.isNotEmpty()) {
                            Text("禁用时段: ${rule.blockedTimeWindows}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun RuleGridCard(
    rule: AppRule,
    usedSeconds: Long,
    bonusSeconds: Long,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(rule.packageName) { AppScanner.getAppInfo(context, rule.packageName)?.icon }
    val ruleColor = when (rule.ruleType) {
        RuleType.ALLOW -> Color(0xFF2E7D32)
        RuleType.BLOCK -> Color(0xFFC62828)
        RuleType.LIMIT -> Color(0xFFEF6C00)
    }
    val ruleLabel = when (rule.ruleType) {
        RuleType.ALLOW -> "放行"
        RuleType.BLOCK -> "禁用"
        RuleType.LIMIT -> "限时"
    }
    val usageSummary = RuleUsageFormatter.summary(rule, usedSeconds, bonusSeconds)
    val cardBackgroundColor = when (rule.ruleType) {
        RuleType.ALLOW -> Color(0xFFE8F5E9)
        RuleType.BLOCK -> Color(0xFFFFEBEE)
        RuleType.LIMIT -> Color.White
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Text(
                    text = rule.appName.ifBlank { "?" }.take(1),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = rule.appName.ifBlank { rule.packageName },
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ruleLabel,
                fontSize = 11.sp,
                color = ruleColor,
                fontWeight = FontWeight.Medium
            )
            if (usageSummary.isNotEmpty()) {
                Text(
                    text = usageSummary,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TimeWindowSelector(
    startMinutes: Int,
    endMinutes: Int,
    onStartMinutesChange: (Int) -> Unit,
    onEndMinutesChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePointRow(
            label = "开始",
            totalMinutes = startMinutes,
            onTotalMinutesChange = onStartMinutesChange
        )
        TimePointRow(
            label = "结束",
            totalMinutes = endMinutes,
            onTotalMinutesChange = onEndMinutesChange
        )
    }
}

@Composable
private fun TimePointRow(
    label: String,
    totalMinutes: Int,
    onTotalMinutesChange: (Int) -> Unit
) {
    val safeMinutes = TimeWindowCodec.normalize(totalMinutes)
    val hour = safeMinutes / 60
    val minute = safeMinutes % 60
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF455A64),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(40.dp)
        )
        TimePartDropdown(
            value = hour,
            range = 0..23,
            suffix = "时",
            onValueChange = { newHour -> onTotalMinutesChange(newHour * 60 + minute) }
        )
        Text(
            text = ":",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        TimePartDropdown(
            value = minute,
            range = 0..59,
            suffix = "分",
            onValueChange = { newMinute -> onTotalMinutesChange(hour * 60 + newMinute) }
        )
    }
}

@Composable
private fun TimePartDropdown(
    value: Int,
    range: IntRange,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .width(72.dp)
                .height(40.dp)
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 16.sp
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 260.dp)
        ) {
            range.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text("${option.toString().padStart(2, '0')} $suffix")
                    },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun LimitConfigCard(
    limitMode: LimitMode,
    dailyMinutes: String,
    onDailyMinutesChange: (String) -> Unit,
    startMinutes: Int,
    endMinutes: Int,
    onStartMinutesChange: (Int) -> Unit,
    onEndMinutesChange: (Int) -> Unit,
    compact: Boolean = false,
    bonusContent: @Composable () -> Unit = {}
) {
    val contentPadding = if (compact) 10.dp else 12.dp
    val itemSpacing = if (compact) 6.dp else 10.dp
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            Text(
                text = "限制参数",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (limitMode != LimitMode.WINDOW_ONLY) {
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "每日分钟",
                            fontSize = 14.sp,
                            color = Color(0xFF455A64),
                            modifier = Modifier.width(72.dp)
                        )
                        OutlinedTextField(
                            value = dailyMinutes,
                            onValueChange = onDailyMinutesChange,
                            label = { Text("分钟") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = dailyMinutes,
                        onValueChange = onDailyMinutesChange,
                        label = { Text("每日可用分钟") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                bonusContent()
            }
            if (limitMode != LimitMode.DURATION_ONLY) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "禁用时段",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TimeWindowSelector(
                        startMinutes = startMinutes,
                        endMinutes = endMinutes,
                        onStartMinutesChange = onStartMinutesChange,
                        onEndMinutesChange = onEndMinutesChange
                    )
                }
            }
        }
    }
}

/**
 * 添加规则对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    title: String = "添加应用规则",
    confirmText: String = "确定",
    initialRule: AppRule? = null,
    initialUsedSeconds: Long = 0L,
    initialBonusSeconds: Long = 0L,
    allowAppSelection: Boolean = true,
    onDismiss: () -> Unit,
    onGrantTodayBonus: ((packageName: String, minutes: Int) -> Unit)? = null,
    onConfirm: (packageName: String, appName: String, ruleType: RuleType, limitMode: LimitMode, minutes: Int, timeWindows: String) -> Unit
) {
    val initialTimeRange = remember(initialRule) {
        TimeWindowCodec.parseRange(initialRule?.blockedTimeWindows.orEmpty())
    }
    val presetApp = remember(initialRule) {
        initialRule?.let {
            AppScanner.AppInfo(
                packageName = it.packageName,
                appName = it.appName,
                icon = null,
                isSystemApp = false
            )
        }
    }
    var selectedApp by remember(initialRule) { mutableStateOf<AppScanner.AppInfo?>(presetApp) }
    var selectedRuleType by remember(initialRule) { mutableStateOf(initialRule?.ruleType ?: RuleType.LIMIT) }
    var selectedLimitMode by remember(initialRule) { mutableStateOf(initialRule?.limitMode ?: LimitMode.BOTH) }
    var dailyMinutes by remember(initialRule) {
        mutableStateOf(
            if (initialRule != null) initialRule.dailyAllowedMinutes.toString() else "30"
        )
    }
    var blockedStartMinutes by remember(initialRule) { mutableStateOf(initialTimeRange.first) }
    var blockedEndMinutes by remember(initialRule) { mutableStateOf(initialTimeRange.second) }
    var bonusMinutesInput by remember(initialRule) { mutableStateOf("30") }
    var displayedBonusSeconds by remember(initialRule, initialBonusSeconds) { mutableStateOf(initialBonusSeconds) }
    var bonusMessage by remember(initialRule) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var limitModeExpanded by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedButton(
                    onClick = { if (allowAppSelection) showAppSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = allowAppSelection
                ) {
                    if (selectedApp != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            selectedApp?.icon?.let { icon ->
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedApp?.appName ?: "",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = selectedApp?.packageName ?: "",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        Text("点击选择应用")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 规则类型选择
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedRuleType) {
                            RuleType.ALLOW -> "放行"
                            RuleType.BLOCK -> "永久禁用"
                            RuleType.LIMIT -> "限时/限时段"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("规则类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        RuleType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (type) {
                                        RuleType.ALLOW -> "放行"
                                        RuleType.BLOCK -> "永久禁用"
                                        RuleType.LIMIT -> "限时/限时段"
                                    })
                                },
                                onClick = {
                                    selectedRuleType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // 根据规则类型显示不同选项
                if (selectedRuleType == RuleType.LIMIT) {
                    ExposedDropdownMenuBox(
                        expanded = limitModeExpanded,
                        onExpandedChange = { limitModeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (selectedLimitMode) {
                                LimitMode.BOTH -> "限时长+限时段"
                                LimitMode.DURATION_ONLY -> "仅限时长"
                                LimitMode.WINDOW_ONLY -> "仅限时段"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("限时模式") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = limitModeExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = limitModeExpanded,
                            onDismissRequest = { limitModeExpanded = false }
                        ) {
                            LimitMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                LimitMode.BOTH -> "限时长+限时段"
                                                LimitMode.DURATION_ONLY -> "仅限时长"
                                                LimitMode.WINDOW_ONLY -> "仅限时段"
                                            }
                                        )
                                    },
                                    onClick = {
                                        selectedLimitMode = mode
                                        limitModeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LimitConfigCard(
                        limitMode = selectedLimitMode,
                        dailyMinutes = dailyMinutes,
                        onDailyMinutesChange = { dailyMinutes = it },
                        startMinutes = blockedStartMinutes,
                        endMinutes = blockedEndMinutes,
                        onStartMinutesChange = { blockedStartMinutes = it },
                        onEndMinutesChange = { blockedEndMinutes = it },
                        bonusContent = {
                            if (initialRule != null && onGrantTodayBonus != null && selectedApp != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "今日临时奖励",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                    Text(
                                        text = "今日已用：${RuleUsageFormatter.formatDuration(initialUsedSeconds)}；已奖励：${RuleUsageFormatter.formatDuration(displayedBonusSeconds)}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = bonusMinutesInput,
                                            onValueChange = { bonusMinutesInput = it },
                                            label = { Text("奖励分钟") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                val minutes = bonusMinutesInput.toIntOrNull() ?: 0
                                                if (minutes > 0) {
                                                    onGrantTodayBonus(selectedApp!!.packageName, minutes)
                                                    displayedBonusSeconds += minutes * 60L
                                                    bonusMessage = "已为今天奖励 ${minutes} 分钟"
                                                } else {
                                                    bonusMessage = "请输入大于 0 的分钟数"
                                                }
                                            }
                                        ) {
                                            Text("奖励")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                onGrantTodayBonus(selectedApp!!.packageName, 30)
                                                displayedBonusSeconds += 30 * 60L
                                                bonusMessage = "已为今天奖励 30 分钟"
                                            }
                                        ) {
                                            Text("+30")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onGrantTodayBonus(selectedApp!!.packageName, 60)
                                                displayedBonusSeconds += 60 * 60L
                                                bonusMessage = "已为今天奖励 60 分钟"
                                            }
                                        ) {
                                            Text("+60")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val secondsToOffset = kotlin.math.max(0L, initialUsedSeconds - displayedBonusSeconds)
                                            val minutesToGrant = ((secondsToOffset + 59L) / 60L).toInt()
                                            if (minutesToGrant > 0) {
                                                onGrantTodayBonus(selectedApp!!.packageName, minutesToGrant)
                                                displayedBonusSeconds += minutesToGrant * 60L
                                                bonusMessage = "已清零今日已用（奖励 $minutesToGrant 分钟）"
                                            } else {
                                                bonusMessage = "今日已用已被奖励抵扣完"
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("清零今日已用")
                                    }
                                    Text(
                                        text = "只对今天生效，不修改上面的每日限制额度。",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    if (bonusMessage.isNotEmpty()) {
                                        Text(
                                            text = bonusMessage,
                                            fontSize = 12.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = dailyMinutes.toIntOrNull() ?: 0
                    onConfirm(
                        selectedApp?.packageName ?: "",
                        selectedApp?.appName ?: "",
                        selectedRuleType,
                        selectedLimitMode,
                        if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.WINDOW_ONLY) minutes else 0,
                        if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.DURATION_ONLY) {
                            TimeWindowCodec.formatRange(blockedStartMinutes, blockedEndMinutes)
                        } else {
                            ""
                        }
                    )
                },
                enabled = selectedApp != null
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 应用选择对话框
    if (showAppSelector && allowAppSelection) {
        AppSelectorDialog(
            onDismiss = { showAppSelector = false },
            onAppSelected = { appInfo ->
                selectedApp = appInfo
                showAppSelector = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRuleDialog(
    configuredPackages: Set<String>,
    configuredRules: Map<String, AppRule>,
    onDismiss: () -> Unit,
    onConfirm: (
        selectedApps: List<AppScanner.AppInfo>,
        ruleType: RuleType,
        limitMode: LimitMode,
        minutes: Int,
        timeWindows: String,
        allowReconfigure: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val defaultBatchTimeRange = remember { TimeWindowCodec.parseRange("") }

    var apps by remember { mutableStateOf<List<AppScanner.AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppScanner.AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var allowReconfigure by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedApps by remember { mutableStateOf<Map<String, AppScanner.AppInfo>>(emptyMap()) }
    var selectedRuleType by remember { mutableStateOf(RuleType.ALLOW) }
    var selectedLimitMode by remember { mutableStateOf(LimitMode.BOTH) }
    var dailyMinutes by remember { mutableStateOf("30") }
    var blockedStartMinutes by remember { mutableStateOf(defaultBatchTimeRange.first) }
    var blockedEndMinutes by remember { mutableStateOf(defaultBatchTimeRange.second) }
    var useGridView by remember { mutableStateOf(true) }
    var selectingAppsPage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = AppScanner.getInstalledApps(context, includeSystemApps = true).filterNot {
            WhitelistManager.isInWhitelist(it.packageName)
        }
        isLoading = false
    }

    LaunchedEffect(searchQuery, showSystemApps, allowReconfigure, apps, configuredPackages) {
        val visibleBySystemFlag = if (showSystemApps) {
            apps
        } else {
            apps.filterNot { it.isSystemApp }
        }
        val searchedApps = if (searchQuery.isBlank()) {
            visibleBySystemFlag
        } else {
            val q = searchQuery.trim().lowercase()
            visibleBySystemFlag.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        filteredApps = if (allowReconfigure) {
            searchedApps
        } else {
            searchedApps.filterNot { configuredPackages.contains(it.packageName) }
        }
        selectedApps = selectedApps.filterKeys { key ->
            filteredApps.any { it.packageName == key }
        }
    }

    val selectedCount = selectedApps.size
    val visibleBySystemFlagCount = if (showSystemApps) apps.size else apps.count { !it.isSystemApp }
    val totalCandidateCount = if (allowReconfigure) {
        visibleBySystemFlagCount
    } else {
        apps.count { candidate ->
            (showSystemApps || !candidate.isSystemApp) && !configuredPackages.contains(candidate.packageName)
        }
    }
    val filteredOutConfiguredCount = if (allowReconfigure) {
        0
    } else {
        apps.count { candidate ->
            (showSystemApps || !candidate.isSystemApp) && configuredPackages.contains(candidate.packageName)
        }
    }

    fun applyBatchSelection() {
        val minutes = dailyMinutes.toIntOrNull() ?: 0
        onConfirm(
            selectedApps.values.toList(),
            selectedRuleType,
            selectedLimitMode,
            if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.WINDOW_ONLY) minutes else 0,
            if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.DURATION_ONLY) {
                TimeWindowCodec.formatRange(blockedStartMinutes, blockedEndMinutes)
            } else {
                ""
            },
            allowReconfigure
        )
    }

    val ruleTypeLabel = when (selectedRuleType) {
        RuleType.ALLOW -> "白名单模式"
        RuleType.BLOCK -> "黑名单模式"
        RuleType.LIMIT -> "限时/限段模式"
    }
    val limitModeLabel = when (selectedLimitMode) {
        LimitMode.BOTH -> "时长+时段"
        LimitMode.DURATION_ONLY -> "仅限时长"
        LimitMode.WINDOW_ONLY -> "仅限时段"
    }
    val ruleSummary = if (selectedRuleType == RuleType.LIMIT) {
        val durationPart = if (selectedLimitMode != LimitMode.WINDOW_ONLY) "每日 $dailyMinutes 分钟" else ""
        val windowPart = if (selectedLimitMode != LimitMode.DURATION_ONLY) {
            "${TimeWindowCodec.format(blockedStartMinutes)}-${TimeWindowCodec.format(blockedEndMinutes)} 禁用"
        } else {
            ""
        }
        listOf(ruleTypeLabel, limitModeLabel, durationPart, windowPart)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    } else {
        ruleTypeLabel
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (selectingAppsPage) "选择要批量配置的应用" else "批量配置应用规则",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectingAppsPage) "第 2 步 / 2：选择应用" else "第 1 步 / 2：设置规则",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (!selectingAppsPage) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "选择规则类型",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        BatchRuleTypeChoiceCard(
                            title = "白名单模式",
                            description = "学习、电话、地图等必须可用的应用，不受限制。",
                            selected = selectedRuleType == RuleType.ALLOW,
                            onClick = { selectedRuleType = RuleType.ALLOW }
                        )
                        BatchRuleTypeChoiceCard(
                            title = "黑名单模式",
                            description = "明确不允许使用的应用，一直拦截直到家长修改。",
                            selected = selectedRuleType == RuleType.BLOCK,
                            onClick = { selectedRuleType = RuleType.BLOCK }
                        )
                        BatchRuleTypeChoiceCard(
                            title = "限时/限段模式",
                            description = "游戏、视频、娱乐应用，按每日分钟或时间段管理。",
                            selected = selectedRuleType == RuleType.LIMIT,
                            onClick = { selectedRuleType = RuleType.LIMIT }
                        )

                        if (selectedRuleType == RuleType.LIMIT) {
                            Text(
                                text = "选择限时模式",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            BatchLimitModeChoiceCard(
                                title = "时长 + 时段",
                                description = "既限制每天能玩多久，也限制夜间或上课时间不能打开。",
                                selected = selectedLimitMode == LimitMode.BOTH,
                                onClick = { selectedLimitMode = LimitMode.BOTH }
                            )
                            BatchLimitModeChoiceCard(
                                title = "仅限时长",
                                description = "只控制每天累计使用分钟，不限制具体使用时段。",
                                selected = selectedLimitMode == LimitMode.DURATION_ONLY,
                                onClick = { selectedLimitMode = LimitMode.DURATION_ONLY }
                            )
                            BatchLimitModeChoiceCard(
                                title = "仅限时段",
                                description = "只控制某些时间不能使用，不统计每天累计分钟。",
                                selected = selectedLimitMode == LimitMode.WINDOW_ONLY,
                                onClick = { selectedLimitMode = LimitMode.WINDOW_ONLY }
                            )

                            LimitConfigCard(
                                limitMode = selectedLimitMode,
                                dailyMinutes = dailyMinutes,
                                onDailyMinutesChange = { dailyMinutes = it },
                                startMinutes = blockedStartMinutes,
                                endMinutes = blockedEndMinutes,
                                onStartMinutesChange = { blockedStartMinutes = it },
                                onEndMinutesChange = { blockedEndMinutes = it },
                                compact = false
                            )
                        }

                        BatchRuleDescriptionCard(
                            ruleType = selectedRuleType,
                            limitMode = selectedLimitMode
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { selectingAppsPage = true }) {
                            Text("下一步：选择应用")
                        }
                    }
                } else {
                    Text(
                        text = "规则：$ruleSummary",
                        fontSize = 13.sp,
                        color = Color(0xFF455A64)
                    )
                    Text(
                        text = "已选择 $selectedCount 个应用；可配置 $totalCandidateCount 个，已过滤已配置 $filteredOutConfiguredCount 个",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索应用") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = showSystemApps,
                                onCheckedChange = { showSystemApps = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("系统应用", fontSize = 13.sp)
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = allowReconfigure,
                                onCheckedChange = { allowReconfigure = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("覆盖已配置", fontSize = 13.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { useGridView = false },
                            modifier = Modifier.weight(1f),
                            colors = if (!useGridView) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("列表")
                        }
                        Button(
                            onClick = { useGridView = true },
                            modifier = Modifier.weight(1f),
                            colors = if (useGridView) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("图标")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("加载中...", color = Color.Gray)
                        }
                    } else if (filteredApps.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("未找到应用", color = Color.Gray)
                        }
                    } else if (!useGridView) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps) { appInfo ->
                                val existingRule = configuredRules[appInfo.packageName]
                                val isSameRuleConfigured = allowReconfigure && existingRule?.let { rule ->
                                    when (selectedRuleType) {
                                        RuleType.ALLOW -> rule.ruleType == RuleType.ALLOW
                                        RuleType.BLOCK -> rule.ruleType == RuleType.BLOCK
                                        RuleType.LIMIT -> rule.ruleType == RuleType.LIMIT && rule.limitMode == selectedLimitMode
                                    }
                                } == true
                                BatchAppListItem(
                                    appInfo = appInfo,
                                    checked = selectedApps.containsKey(appInfo.packageName),
                                    configured = allowReconfigure && existingRule != null,
                                    sameRuleConfigured = isSameRuleConfigured,
                                    onCheckedChange = { checked ->
                                        selectedApps = if (checked) {
                                            selectedApps + (appInfo.packageName to appInfo)
                                        } else {
                                            selectedApps - appInfo.packageName
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredApps.size) { index ->
                                val appInfo = filteredApps[index]
                                val existingRule = configuredRules[appInfo.packageName]
                                val isSameRuleConfigured = allowReconfigure && existingRule?.let { rule ->
                                    when (selectedRuleType) {
                                        RuleType.ALLOW -> rule.ruleType == RuleType.ALLOW
                                        RuleType.BLOCK -> rule.ruleType == RuleType.BLOCK
                                        RuleType.LIMIT -> rule.ruleType == RuleType.LIMIT && rule.limitMode == selectedLimitMode
                                    }
                                } == true
                                BatchAppGridItem(
                                    appInfo = appInfo,
                                    checked = selectedApps.containsKey(appInfo.packageName),
                                    configured = allowReconfigure && existingRule != null,
                                    sameRuleConfigured = isSameRuleConfigured,
                                    onCheckedChange = { checked ->
                                        selectedApps = if (checked) {
                                            selectedApps + (appInfo.packageName to appInfo)
                                        } else {
                                            selectedApps - appInfo.packageName
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { selectingAppsPage = false }) {
                            Text("上一步")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { applyBatchSelection() },
                            enabled = selectedApps.isNotEmpty()
                        ) {
                            Text("批量应用")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRuleTypeChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    BatchChoiceCard(
        title = title,
        description = description,
        selected = selected,
        onClick = onClick,
        titleSize = 18
    )
}

@Composable
private fun BatchLimitModeChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    BatchChoiceCard(
        title = title,
        description = description,
        selected = selected,
        onClick = onClick,
        titleSize = 16
    )
}

@Composable
private fun BatchChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    titleSize: Int
) {
    val containerColor = if (selected) Color(0xFFEDE7F6) else Color(0xFFFFFFFF)
    val titleColor = if (selected) Color(0xFF5E35B1) else Color(0xFF263238)
    val borderColor = if (selected) Color(0xFF6D4CBB) else Color(0xFFD8DEE9)
    val borderWidth = if (selected) 2.dp else 1.dp
    val statusText = if (selected) "已选中" else "点选"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color(0xFF607D8B)
                )
            }
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
        }
    }
}

@Composable
private fun BatchRuleDescriptionCard(
    ruleType: RuleType,
    limitMode: LimitMode
) {
    val title = when (ruleType) {
        RuleType.ALLOW -> "白名单模式说明"
        RuleType.BLOCK -> "黑名单模式说明"
        RuleType.LIMIT -> "限时/限段模式说明"
    }
    val lead = when (ruleType) {
        RuleType.ALLOW -> "适合学习软件、电话短信、地图导航等需要长期可用的应用。"
        RuleType.BLOCK -> "适合游戏中心、短视频入口、浏览器下载器等暂时不希望孩子打开的应用。"
        RuleType.LIMIT -> when (limitMode) {
            LimitMode.BOTH -> "同时控制每天可用总时长和不可使用的时间段，适合大多数娱乐应用。"
            LimitMode.DURATION_ONLY -> "只限制每天累计使用多久，不限制具体在哪个时间段使用。"
            LimitMode.WINDOW_ONLY -> "只限制某些时间段不能使用，不统计每天累计时长。"
        }
    }
    val points = when (ruleType) {
        RuleType.ALLOW -> listOf(
            "被放行的应用不会被每日时长、禁用时段或全局锁机规则拦截。",
            "建议只给确实必要的应用放行，避免孩子通过放行应用绕开管控。",
            "以后仍可在已配置规则中单独修改或删除。"
        )
        RuleType.BLOCK -> listOf(
            "被禁用的应用会一直被拦截，直到家长重新改为放行或限时。",
            "适合处理明确不允许使用的应用，规则简单、效果直接。",
            "如果只是想每天少玩一会儿，建议选择限时规则。"
        )
        RuleType.LIMIT -> listOf(
            "每日分钟用于控制当天累计可玩多久。",
            "禁用时段用于控制某段时间完全不能打开，例如夜间或上课时间。",
            "下一步选择应用后，这套规则会一次性应用到所有选中的应用。"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = lead,
                fontSize = 14.sp,
                color = Color(0xFF455A64)
            )
            points.forEach { point ->
                Text(
                    text = "· $point",
                    fontSize = 13.sp,
                    color = Color(0xFF607D8B)
                )
            }
        }
    }
}

@Composable
fun BatchAppListItem(
    appInfo: AppScanner.AppInfo,
    checked: Boolean,
    configured: Boolean,
    sameRuleConfigured: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val containerColor = when {
        checked && sameRuleConfigured -> Color(0xFFFFEBEE)
        checked -> Color(0xFFE3F2FD)
        sameRuleConfigured -> Color(0xFFE8F5E9)
        configured -> Color(0xFFFFF8E1)
        else -> Color.White
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )

            Spacer(modifier = Modifier.width(8.dp))

            appInfo.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = appInfo.packageName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (checked && sameRuleConfigured) {
                    Text(
                        text = "将取消配置",
                        fontSize = 11.sp,
                        color = Color(0xFFD32F2F)
                    )
                } else if (sameRuleConfigured) {
                    Text(
                        text = "已配同规则",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32)
                    )
                } else if (configured) {
                    Text(
                        text = "已配其他规则",
                        fontSize = 11.sp,
                        color = Color(0xFFF57F17)
                    )
                }
            }
        }
    }
}

@Composable
fun BatchAppGridItem(
    appInfo: AppScanner.AppInfo,
    checked: Boolean,
    configured: Boolean,
    sameRuleConfigured: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val containerColor = when {
        checked && sameRuleConfigured -> Color(0xFFFFEBEE)
        checked -> Color(0xFFE3F2FD)
        sameRuleConfigured -> Color(0xFFE8F5E9)
        configured -> Color(0xFFFFF8E1)
        else -> Color.White
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appInfo.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = appInfo.appName,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (checked && sameRuleConfigured) {
                Text(
                    text = "将取消配置",
                    fontSize = 10.sp,
                    color = Color(0xFFD32F2F)
                )
            } else if (sameRuleConfigured) {
                Text(
                    text = "已配同规则",
                    fontSize = 10.sp,
                    color = Color(0xFF2E7D32)
                )
            } else if (configured) {
                Text(
                    text = "已配其他规则",
                    fontSize = 10.sp,
                    color = Color(0xFFF57F17)
                )
            }
        }
    }
}

@Composable
fun BatchApplyResultDialog(
    result: AppRuleRepository.BatchApplyResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量配置结果") },
        text = {
            Column {
                Text("新增或更新 ${result.successCount} 个应用")
                Text("取消配置 ${result.removedCount} 个应用")
                if (result.skippedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("跳过 ${result.skippedItems.size} 个应用")
                    Spacer(modifier = Modifier.height(4.dp))
                    result.skippedItems.take(8).forEach { item ->
                        val reasonText = when (item.reason) {
                            AppRuleRepository.BatchSkipReason.EMPTY_PACKAGE -> "包名为空"
                            AppRuleRepository.BatchSkipReason.SYSTEM_WHITELIST -> "系统白名单"
                            AppRuleRepository.BatchSkipReason.ALREADY_CONFIGURED -> "已配置规则"
                            AppRuleRepository.BatchSkipReason.DUPLICATE_REQUEST -> "批量请求重复"
                        }
                        Text(
                            text = "${item.appName.ifBlank { item.packageName }}：$reasonText",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    if (result.skippedItems.size > 8) {
                        Text(
                            text = "其余 ${result.skippedItems.size - 8} 条已省略",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
