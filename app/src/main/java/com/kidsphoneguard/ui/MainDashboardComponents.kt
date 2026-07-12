package com.kidsphoneguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidsphoneguard.R
import com.kidsphoneguard.service.GuardAccessibilityService
import com.kidsphoneguard.service.GuardHealthState
import com.kidsphoneguard.service.GuardForegroundService
import com.kidsphoneguard.service.UsageTrackingManager
import com.kidsphoneguard.utils.PasswordManager
import com.kidsphoneguard.utils.PermissionManager
import com.kidsphoneguard.utils.SettingsManager
import kotlinx.coroutines.delay

private const val SETUP_SETTINGS_ACCESS_ALLOWANCE_MS = 10 * 60 * 1000L

@Composable
fun MainDashboardScreen() {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager.getInstance(context) }
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var permissionStatus by remember { mutableStateOf(PermissionManager.checkAllPermissions(context)) }
    var protectionDegraded by remember { mutableStateOf(isProtectionDegraded(context)) }
    var hasPasswordConfigured by remember { mutableStateOf(passwordManager.hasPasswordConfigured()) }
    var brandSetupConfirmed by remember { mutableStateOf(settingsManager.isBrandSetupConfirmed()) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val appName = stringResource(R.string.app_name)
    val appSubtitle = stringResource(R.string.app_subtitle)

    LaunchedEffect(Unit) {
        settingsManager.clearSetupSettingsAccess()
        while (true) {
            delay(1000)
            permissionStatus = PermissionManager.checkAllPermissions(context)
            protectionDegraded = isProtectionDegraded(context)
            hasPasswordConfigured = passwordManager.hasPasswordConfigured()
            brandSetupConfirmed = settingsManager.isBrandSetupConfirmed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = appName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
        )

        Text(
            text = appSubtitle,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MainProtectionStatusCard(
            permissionStatus = permissionStatus,
            protectionDegraded = protectionDegraded,
            brandSetupConfirmed = brandSetupConfirmed
        )

        Spacer(modifier = Modifier.height(16.dp))

        ParentConfigEntryCard(
            hasPasswordConfigured = hasPasswordConfigured,
            onEnterConfig = {
                if (hasPasswordConfigured) {
                    showPasswordDialog = true
                } else {
                    context.startActivity(Intent(context, PasswordSettingsActivity::class.java))
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showPasswordDialog) {
        PasswordVerificationFlow(
            passwordManager = passwordManager,
            onVerified = {
                showPasswordDialog = false
                context.startActivity(Intent(context, ConfigActivity::class.java))
            },
            onDismiss = {
                showPasswordDialog = false
            }
        )
    }
}

@Composable
private fun MainProtectionStatusCard(
    permissionStatus: Map<PermissionManager.PermissionType, Boolean>,
    protectionDegraded: Boolean,
    brandSetupConfirmed: Boolean
) {
    val containerColor = if (protectionDegraded) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (protectionDegraded) "守护状态需要家长检查" else "守护状态正常",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "主页面只显示状态，不再提供系统设置入口。需要调整权限、后台保活或规则时，请进入家长配置。",
                fontSize = 14.sp,
                color = Color(0xFF5D4037)
            )
            StatusLine("无障碍服务", permissionStatus[PermissionManager.PermissionType.ACCESSIBILITY] == true)
            StatusLine("使用情况访问", permissionStatus[PermissionManager.PermissionType.USAGE_STATS] == true)
            StatusLine("悬浮窗权限", permissionStatus[PermissionManager.PermissionType.OVERLAY] == true)
            StatusLine(
                "电池优化忽略",
                permissionStatus[PermissionManager.PermissionType.BATTERY_OPTIMIZATION] == true
            )
            StatusLine("品牌后台保活已确认", brandSetupConfirmed)
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    isOk: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp)
        Text(
            text = if (isOk) "正常" else "需家长处理",
            color = if (isOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ParentConfigEntryCard(
    hasPasswordConfigured: Boolean,
    onEnterConfig: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "家长配置",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (hasPasswordConfigured) {
                    "进入后可以配置应用规则、权限向导、后台保活、全局解锁等高风险操作。"
                } else {
                    "首次使用请先设置家长密码。设置完成后，再回到这里进入家长配置。"
                },
                fontSize = 15.sp
            )
            Button(
                onClick = onEnterConfig,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (hasPasswordConfigured) "输入家长密码进入" else "设置家长密码",
                    fontSize = 17.sp
                )
            }
        }
    }
}

/**
 * 权限引导界面
 */
