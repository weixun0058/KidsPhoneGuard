package com.kidsphoneguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.core.graphics.drawable.toBitmap
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.data.repository.AppRuleRepository
import com.kidsphoneguard.utils.AppScanner
import com.kidsphoneguard.utils.SettingsManager
import com.kidsphoneguard.utils.WhitelistManager
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
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as KidsPhoneGuardApp

    var appRules by remember { mutableStateOf<List<AppRule>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AppRule?>(null) }
    var batchApplyResult by remember { mutableStateOf<AppRuleRepository.BatchApplyResult?>(null) }
    var useRuleGridView by remember { mutableStateOf(false) }
    var longPressRule by remember { mutableStateOf<AppRule?>(null) }

    // 加载规则列表
    LaunchedEffect(Unit) {
        app.appRuleRepository.getAllRules().collect { rules ->
            appRules = rules
        }
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
                    Text(if (useRuleGridView) "列表模式" else "图标模式")
                }
            }

            if (!useRuleGridView) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appRules) { rule ->
                        RuleCard(
                            rule = rule,
                            onEdit = {
                                editingRule = rule
                            },
                            onDelete = {
                                scope.launch {
                                    app.appRuleRepository.deleteRule(rule.packageName)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appRules.size) { index ->
                        val rule = appRules[index]
                        RuleGridCard(
                            rule = rule,
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
                scope.launch {
                    val rule = AppRule(
                        packageName = packageName,
                        appName = appName,
                        ruleType = ruleType,
                        limitMode = if (ruleType == RuleType.LIMIT) limitMode else LimitMode.BOTH,
                        dailyAllowedMinutes = if (ruleType == RuleType.LIMIT) minutes else 0,
                        blockedTimeWindows = if (ruleType == RuleType.LIMIT) timeWindows else "",
                        isGlobalLocked = false
                    )
                    app.appRuleRepository.saveRule(rule)
                }
                showAddDialog = false
            }
        )
    }

    if (editingRule != null) {
        AddRuleDialog(
            title = "修改应用规则",
            confirmText = "保存",
            initialRule = editingRule,
            allowAppSelection = false,
            onDismiss = { editingRule = null },
            onConfirm = { packageName, appName, ruleType, limitMode, minutes, timeWindows ->
                scope.launch {
                    val originalRule = editingRule
                    val rule = AppRule(
                        packageName = packageName,
                        appName = appName,
                        ruleType = ruleType,
                        limitMode = if (ruleType == RuleType.LIMIT) limitMode else LimitMode.BOTH,
                        dailyAllowedMinutes = if (ruleType == RuleType.LIMIT) minutes else 0,
                        blockedTimeWindows = if (ruleType == RuleType.LIMIT) timeWindows else "",
                        isGlobalLocked = originalRule?.isGlobalLocked ?: false
                    )
                    app.appRuleRepository.saveRule(rule)
                }
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
                scope.launch {
                    val selectedPackageSet = selectedApps.map { it.packageName }.toSet()
                    val toRemovePackages = if (allowReconfigure) {
                        appRules.filter { rule ->
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
                        .filterNot { toRemovePackages.contains(it.packageName) }
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
                    batchApplyResult = app.appRuleRepository.applyBatchRules(
                        inputs = inputs,
                        allowReconfigure = allowReconfigure
                    ).copy(removedCount = toRemovePackages.toSet().size)
                }
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
                        scope.launch {
                            app.appRuleRepository.deleteRule(targetRule.packageName)
                        }
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
                            text = "不拦截",
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
                    isUnlocked -> "当前：全局解锁"
                    isLocked -> "当前：全局锁机"
                    else -> "当前：规则模式"
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * 规则卡片
 */
@Composable
fun RuleCard(rule: AppRule, onEdit: () -> Unit, onDelete: () -> Unit) {
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
    allowAppSelection: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String, appName: String, ruleType: RuleType, limitMode: LimitMode, minutes: Int, timeWindows: String) -> Unit
) {
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
    var timeWindows by remember(initialRule) {
        mutableStateOf(
            if (initialRule != null) initialRule.blockedTimeWindows else "22:00-07:00"
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var limitModeExpanded by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
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

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedLimitMode != LimitMode.WINDOW_ONLY) {
                        OutlinedTextField(
                            value = dailyMinutes,
                            onValueChange = { dailyMinutes = it },
                            label = { Text("每日限制（分钟）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (selectedLimitMode == LimitMode.BOTH) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (selectedLimitMode != LimitMode.DURATION_ONLY) {
                        OutlinedTextField(
                            value = timeWindows,
                            onValueChange = { timeWindows = it },
                            label = { Text("禁用时段（如：22:00-07:00）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                        if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.DURATION_ONLY) timeWindows else ""
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
    var timeWindows by remember { mutableStateOf("22:00-07:00") }
    var useGridView by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var limitModeExpanded by remember { mutableStateOf(false) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量配置应用规则") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp)
            ) {
                Text(
                    text = "已选择 $selectedCount 个应用",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "可配置应用 $totalCandidateCount 个，已过滤已配置 $filteredOutConfiguredCount 个",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索应用") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                        androidx.compose.material3.Switch(
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
                        androidx.compose.material3.Switch(
                            checked = allowReconfigure,
                            onCheckedChange = { allowReconfigure = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("覆盖已配置", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedRuleType) {
                            RuleType.ALLOW -> "放行（白名单）"
                            RuleType.BLOCK -> "永久禁用（黑名单）"
                            RuleType.LIMIT -> "限时/限时段"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("批量规则类型") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
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
                                    Text(
                                        when (type) {
                                            RuleType.ALLOW -> "放行（白名单）"
                                            RuleType.BLOCK -> "永久禁用（黑名单）"
                                            RuleType.LIMIT -> "限时/限时段"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedRuleType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedRuleType == RuleType.LIMIT) {
                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedLimitMode != LimitMode.WINDOW_ONLY) {
                            OutlinedTextField(
                                value = dailyMinutes,
                                onValueChange = { dailyMinutes = it },
                                label = { Text("分钟") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (selectedLimitMode != LimitMode.DURATION_ONLY) {
                            OutlinedTextField(
                                value = timeWindows,
                                onValueChange = { timeWindows = it },
                                label = { Text("时段") },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                            .weight(1f)
                            .heightIn(min = 280.dp),
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
                            .weight(1f)
                            .heightIn(min = 280.dp),
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = dailyMinutes.toIntOrNull() ?: 0
                    onConfirm(
                        selectedApps.values.toList(),
                        selectedRuleType,
                        selectedLimitMode,
                        if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.WINDOW_ONLY) minutes else 0,
                        if (selectedRuleType == RuleType.LIMIT && selectedLimitMode != LimitMode.DURATION_ONLY) timeWindows else "",
                        allowReconfigure
                    )
                },
                enabled = selectedApps.isNotEmpty()
            ) {
                Text("批量应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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

/**
 * 应用选择对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(
    onDismiss: () -> Unit,
    onAppSelected: (AppScanner.AppInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppScanner.AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppScanner.AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showSystemApps by remember { mutableStateOf(false) }

    // 加载应用列表
    LaunchedEffect(Unit) {
        scope.launch {
            apps = AppScanner.getInstalledApps(context, includeSystemApps = false)
            filteredApps = apps
            isLoading = false
        }
    }

    // 搜索过滤
    LaunchedEffect(searchQuery, showSystemApps) {
        scope.launch {
            if (searchQuery.isBlank()) {
                filteredApps = if (showSystemApps) {
                    AppScanner.getInstalledApps(context, includeSystemApps = true)
                } else {
                    apps
                }
            } else {
                filteredApps = AppScanner.searchApps(context, searchQuery, showSystemApps)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索应用") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 显示系统应用开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Switch(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("显示系统应用")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用列表
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("加载中...", color = Color.Gray)
                    }
                } else if (filteredApps.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("未找到应用", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps) { appInfo ->
                            AppListItem(
                                appInfo = appInfo,
                                onClick = { onAppSelected(appInfo) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 应用列表项
 */
@Composable
fun AppListItem(
    appInfo: AppScanner.AppInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标
            appInfo.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 应用信息
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
                if (appInfo.isSystemApp) {
                    Text(
                        text = "系统应用",
                        fontSize = 10.sp,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}
