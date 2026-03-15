package com.kidsphoneguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kidsphoneguard.KidsPhoneGuardApp
import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.RuleType
import com.kidsphoneguard.utils.AppScanner
import com.kidsphoneguard.utils.SettingsManager
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
            // 全局锁机按钮
            GlobalLockButton()

            Spacer(modifier = Modifier.height(16.dp))

            // 添加规则按钮
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("添加应用规则")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 规则列表
            Text(
                text = "已配置规则 (${appRules.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appRules) { rule ->
                    RuleCard(
                        rule = rule,
                        onDelete = {
                            scope.launch {
                                app.appRuleRepository.deleteRule(rule.packageName)
                            }
                        }
                    )
                }
            }
        }
    }

    // 添加规则对话框
    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { packageName, appName, ruleType, minutes, timeWindows ->
                scope.launch {
                    val rule = AppRule(
                        packageName = packageName,
                        appName = appName,
                        ruleType = ruleType,
                        dailyAllowedMinutes = minutes,
                        blockedTimeWindows = timeWindows,
                        isGlobalLocked = false
                    )
                    app.appRuleRepository.saveRule(rule)
                }
                showAddDialog = false
            }
        )
    }
}

/**
 * 全局锁机按钮
 */
@Composable
fun GlobalLockButton() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }

    // 使用 remember 保存状态，每次重新组合时重新读取
    var refreshKey by remember { mutableStateOf(0) }
    var isLocked by remember(refreshKey) { mutableStateOf(settingsManager.isGlobalLockEnabled()) }

    // 使用 SideEffect 在每次组合完成后检查是否需要刷新
    androidx.compose.runtime.SideEffect {
        val currentState = settingsManager.isGlobalLockEnabled()
        if (currentState != isLocked) {
            isLocked = currentState
            android.util.Log.d("GlobalLock", "State refreshed to: $currentState")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isLocked) "全局锁机已开启" else "全局锁机已关闭",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) Color(0xFFD32F2F) else Color(0xFF388E3C)
                )
                Text(
                    text = if (isLocked) "所有应用都将被拦截" else "按各应用规则执行",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = {
                    val newState = !isLocked
                    isLocked = newState
                    settingsManager.setGlobalLock(newState)
                    refreshKey++ // 触发重新读取
                    android.util.Log.d("GlobalLock", "Global lock set to: $newState")
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (isLocked) Color(0xFFD32F2F) else Color(0xFF388E3C)
                )
            ) {
                Text(if (isLocked) "解锁" else "锁机")
            }
        }
    }
}

/**
 * 规则卡片
 */
@Composable
fun RuleCard(rule: AppRule, onDelete: () -> Unit) {
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

                TextButton(onClick = onDelete) {
                    Text("删除", color = Color.Red)
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

/**
 * 添加规则对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (packageName: String, appName: String, ruleType: RuleType, minutes: Int, timeWindows: String) -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppScanner.AppInfo?>(null) }
    var selectedRuleType by remember { mutableStateOf(RuleType.LIMIT) }
    var dailyMinutes by remember { mutableStateOf("30") }
    var timeWindows by remember { mutableStateOf("22:00-07:00") }
    var expanded by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加应用规则") },
        text = {
            Column {
                // 应用选择按钮
                OutlinedButton(
                    onClick = { showAppSelector = true },
                    modifier = Modifier.fillMaxWidth()
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
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dailyMinutes,
                        onValueChange = { dailyMinutes = it },
                        label = { Text("每日限制（分钟）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = timeWindows,
                        onValueChange = { timeWindows = it },
                        label = { Text("禁用时段（如：22:00-07:00）") },
                        modifier = Modifier.fillMaxWidth()
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
                        minutes,
                        timeWindows
                    )
                },
                enabled = selectedApp != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 应用选择对话框
    if (showAppSelector) {
        AppSelectorDialog(
            onDismiss = { showAppSelector = false },
            onAppSelected = { appInfo ->
                selectedApp = appInfo
                showAppSelector = false
            }
        )
    }
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
