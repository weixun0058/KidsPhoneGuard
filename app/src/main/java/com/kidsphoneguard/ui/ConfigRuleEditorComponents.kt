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
internal fun LimitConfigCard(
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
    onAdjustTodayMinutes: ((packageName: String, minutes: Int) -> Unit)? = null,
    onResetTodayUsage: ((packageName: String) -> Unit)? = null,
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
                            if (initialRule != null &&
                                onAdjustTodayMinutes != null &&
                                onResetTodayUsage != null &&
                                selectedApp != null
                            ) {
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
                                        text = "今日时间调整",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                    Text(
                                        text = "今日已用：${RuleUsageFormatter.formatDuration(initialUsedSeconds)}；已调整：${RuleUsageFormatter.formatSignedDuration(displayedBonusSeconds)}",
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
                                            onValueChange = { value ->
                                                if (value.isEmpty() ||
                                                    value == "-" ||
                                                    value.matches(Regex("-?\\d+"))
                                                ) {
                                                    bonusMinutesInput = value
                                                }
                                            },
                                            label = { Text("调整分钟（负数为惩罚）") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                val minutes = bonusMinutesInput.toIntOrNull() ?: 0
                                                if (minutes != 0) {
                                                    onAdjustTodayMinutes(selectedApp!!.packageName, minutes)
                                                    displayedBonusSeconds += minutes * 60L
                                                    bonusMessage = if (minutes > 0) {
                                                        "已为今天奖励 $minutes 分钟"
                                                    } else {
                                                        "已为今天扣减 ${-minutes.toLong()} 分钟"
                                                    }
                                                } else {
                                                    bonusMessage = "请输入非 0 分钟数，负数表示扣减"
                                                }
                                            }
                                        ) {
                                            Text("应用")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(-60, -30, 30, 60).forEach { minutes ->
                                            OutlinedButton(
                                                onClick = {
                                                    onAdjustTodayMinutes(
                                                        selectedApp!!.packageName,
                                                        minutes
                                                    )
                                                    displayedBonusSeconds += minutes * 60L
                                                    bonusMessage = if (minutes > 0) {
                                                        "已为今天奖励 $minutes 分钟"
                                                    } else {
                                                        "已为今天扣减 ${-minutes} 分钟"
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(if (minutes > 0) "+$minutes" else "$minutes")
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onResetTodayUsage(selectedApp!!.packageName)
                                            displayedBonusSeconds = 0L
                                            bonusMessage = "已将今日已用清零"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("清零今日已用")
                                    }
                                    Text(
                                        text = "正数奖励、负数惩罚，只对今天生效；调整后总额度最低为 0。",
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
