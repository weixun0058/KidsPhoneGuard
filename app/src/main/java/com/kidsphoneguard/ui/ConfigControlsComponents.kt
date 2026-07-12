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
