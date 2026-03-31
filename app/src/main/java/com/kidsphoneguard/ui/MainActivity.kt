package com.kidsphoneguard.ui

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidsphoneguard.service.GuardAccessibilityService
import com.kidsphoneguard.service.GuardHealthState
import com.kidsphoneguard.service.GuardForegroundService
import com.kidsphoneguard.service.UsageTrackingManager
import com.kidsphoneguard.utils.PasswordManager
import com.kidsphoneguard.utils.PermissionManager
import kotlinx.coroutines.delay

/**
 * 主Activity - 权限引导页
 * 引导用户开启所有必要的权限
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGuideScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 启动前台服务
        GuardForegroundService.start(this)
    }
}

/**
 * 权限引导界面
 */
@Composable
fun PermissionGuideScreen() {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager.getInstance(context) }
    var permissionStatus by remember { mutableStateOf(PermissionManager.checkAllPermissions(context)) }
    var protectionDegraded by remember { mutableStateOf(isProtectionDegraded(context)) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val requiredPermissions = listOf(
        PermissionManager.PermissionType.OVERLAY,
        PermissionManager.PermissionType.USAGE_STATS,
        PermissionManager.PermissionType.BATTERY_OPTIMIZATION,
        PermissionManager.PermissionType.ACCESSIBILITY
    )
    val allRequiredGranted = requiredPermissions.all { permissionStatus[it] == true }
    val pendingRequiredCount = requiredPermissions.count { permissionStatus[it] != true }

    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            permissionStatus = PermissionManager.checkAllPermissions(context)
            protectionDegraded = isProtectionDegraded(context)
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
            text = "儿童手机守护",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
        )

        Text(
            text = "请完成以下设置以启用保护功能",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (protectionDegraded) {
            Text(
                text = "当前守护状态异常，请优先恢复无障碍与使用统计权限",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // 悬浮窗权限
        PermissionCard(
            title = "悬浮窗权限",
            description = "用于显示拦截覆盖层",
            isGranted = permissionStatus[PermissionManager.PermissionType.OVERLAY] ?: false,
            onClick = { PermissionManager.requestOverlayPermission(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 使用统计权限
        PermissionCard(
            title = "使用统计权限",
            description = "用于计算应用使用时长",
            isGranted = permissionStatus[PermissionManager.PermissionType.USAGE_STATS] ?: false,
            onClick = { PermissionManager.requestUsageStatsPermission(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 电池优化
        PermissionCard(
            title = "忽略电池优化",
            description = "防止应用被系统杀死",
            isGranted = permissionStatus[PermissionManager.PermissionType.BATTERY_OPTIMIZATION] ?: false,
            onClick = { PermissionManager.requestIgnoreBatteryOptimizations(context) }
        )

        // 无障碍服务权限（放在最后）
        PermissionCard(
            title = "无障碍服务",
            description = "用于监控应用切换和防卸载（核心权限）",
            isGranted = permissionStatus[PermissionManager.PermissionType.ACCESSIBILITY] ?: false,
            onClick = {
                if (!PermissionManager.canShowAccessibilityGuide(context)) {
                    Toast.makeText(context, "请先完成当前设置，再次尝试", Toast.LENGTH_SHORT).show()
                    return@PermissionCard
                }
                PermissionManager.requestAccessibilityPermission(context)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 进入配置页面按钮
        Button(
            onClick = {
                showPasswordDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("进入家长配置", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                context.startActivity(Intent(context, PasswordSettingsActivity::class.java))
            }
        ) {
            Text("修改密码")
        }

        if (!allRequiredGranted) {
            Text(
                text = "仍有 $pendingRequiredCount 项核心权限未完成，可进入家长配置后继续补齐",
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // 密码验证对话框
    if (showPasswordDialog) {
        PasswordVerificationFlow(
            passwordManager = passwordManager,
            onVerified = {
                // 密码验证成功，进入配置页面
                showPasswordDialog = false
                context.startActivity(Intent(context, ConfigActivity::class.java))
            },
            onDismiss = {
                // 取消或验证失败
                showPasswordDialog = false
            }
        )
    }
}

private fun isProtectionDegraded(context: android.content.Context): Boolean {
    val now = System.currentTimeMillis()
    val accessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(context)
    val usagePermissionGranted = UsageTrackingManager.hasUsageStatsPermission(context)
    val accessibilityHeartbeat = GuardHealthState.getAccessibilityHeartbeat(context)
    val usageHeartbeat = GuardHealthState.getUsageHeartbeat(context)
    val accessibilityStale = accessibilityEnabled &&
        (!GuardAccessibilityService.isServiceRunning() ||
            accessibilityHeartbeat == 0L ||
            now - accessibilityHeartbeat > 15000L)
    val usageStale = usagePermissionGranted &&
        (!UsageTrackingManager.isTrackingActive() ||
            usageHeartbeat == 0L ||
            now - usageHeartbeat > 20000L)
    return !accessibilityEnabled || !usagePermissionGranted || accessibilityStale || usageStale
}

@Composable
fun PasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    errorMessage: String? = null
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请输入密码") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "进入家长配置需要验证密码",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun PasswordVerificationFlow(
    passwordManager: PasswordManager,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (showDialog) {
        PasswordDialog(
            onDismiss = {
                showDialog = false
                onDismiss()
            },
            onConfirm = { inputPassword ->
                if (passwordManager.verifyPassword(inputPassword)) {
                    showDialog = false
                    onVerified()
                } else {
                    errorMessage = "密码错误，请重试"
                }
            },
            errorMessage = errorMessage
        )
    }
}

/**
 * 权限卡片组件
 */
@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (isGranted) {
                Text(
                    text = "已开启",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Button(onClick = onClick) {
                    Text("去开启")
                }
            }
        }
    }
}

/**
 * 文本按钮组件
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ),
        elevation = null
    ) {
        content()
    }
}
