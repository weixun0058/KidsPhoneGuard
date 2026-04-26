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
    var hasPasswordConfigured by remember { mutableStateOf(passwordManager.hasPasswordConfigured()) }
    val scrollState = rememberScrollState()
    val deviceSetupGuide = remember { DeviceSetupGuide.current() }
    val requiredPermissions = listOf(
        PermissionManager.PermissionType.OVERLAY,
        PermissionManager.PermissionType.USAGE_STATS,
        PermissionManager.PermissionType.BATTERY_OPTIMIZATION,
        PermissionManager.PermissionType.ACCESSIBILITY
    )
    val grantedRequiredCount = requiredPermissions.count { permissionStatus[it] == true }
    val allRequiredGranted = requiredPermissions.all { permissionStatus[it] == true }
    val pendingRequiredCount = requiredPermissions.count { permissionStatus[it] != true }

    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            permissionStatus = PermissionManager.checkAllPermissions(context)
            protectionDegraded = isProtectionDegraded(context)
            hasPasswordConfigured = passwordManager.hasPasswordConfigured()
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
            text = "小范围测试前，请按步骤完成守护配置",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SetupProgressCard(
            grantedCount = grantedRequiredCount,
            totalCount = requiredPermissions.size,
            hasPasswordConfigured = hasPasswordConfigured,
            protectionDegraded = protectionDegraded
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (protectionDegraded) {
            Text(
                text = "当前守护状态异常，请优先恢复无障碍与使用统计权限",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        SectionTitle("第1步：开启核心权限")

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
                PermissionManager.requestAccessibilityPermission(context)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("第2步：确认手机品牌后台设置")

        BrandSetupGuideCard(
            guide = deviceSetupGuide,
            onOpenSettings = {
                val opened = PermissionManager.requestHuaweiProtectionGuide(context)
                if (!opened) {
                    openCurrentAppDetails(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("第3步：进入家长配置并做一次拦截测试")

        TestPreparationCard(
            allRequiredGranted = allRequiredGranted,
            hasPasswordConfigured = hasPasswordConfigured
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 进入配置页面按钮
        Button(
            onClick = {
                if (passwordManager.hasPasswordConfigured()) {
                    showPasswordDialog = true
                } else {
                    Toast.makeText(context, "请先设置家长密码", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(context, PasswordSettingsActivity::class.java))
                }
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
                text = "仍有 $pendingRequiredCount 项核心权限未完成。亲友测试前建议先补齐，否则测试结果可能失真。",
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

private fun openCurrentAppDetails(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun SetupProgressCard(
    grantedCount: Int,
    totalCount: Int,
    hasPasswordConfigured: Boolean,
    protectionDegraded: Boolean
) {
    val progress = if (totalCount == 0) 0f else grantedCount.toFloat() / totalCount.toFloat()
    val containerColor = when {
        protectionDegraded -> Color(0xFFFFEBEE)
        grantedCount == totalCount && hasPasswordConfigured -> Color(0xFFE8F5E9)
        else -> Color(0xFFEFF6FF)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "测试前配置进度",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "核心权限 $grantedCount/$totalCount；家长密码：${if (hasPasswordConfigured) "已设置" else "未设置"}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = when {
                    protectionDegraded -> "守护服务状态异常，请先按下方权限项恢复。"
                    grantedCount == totalCount && hasPasswordConfigured -> "基础配置已完成，可以进入家长配置添加规则并做拦截测试。"
                    else -> "按顺序完成下方步骤，完成后返回本页会自动刷新状态。"
                },
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun BrandSetupGuideCard(
    guide: DeviceSetupGuide,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "已识别：${guide.brandName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "这些后台设置多数无法由 App 直接读取，需要家长人工确认。",
                fontSize = 13.sp,
                color = Color.Gray
            )
            guide.steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    fontSize = 14.sp
                )
            }
            if (guide.note.isNotBlank()) {
                Text(
                    text = guide.note,
                    fontSize = 13.sp,
                    color = Color(0xFF5D4037)
                )
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开系统设置入口")
            }
        }
    }
}

@Composable
private fun TestPreparationCard(
    allRequiredGranted: Boolean,
    hasPasswordConfigured: Boolean
) {
    val lines = listOf(
        "添加 1 个常用娱乐应用规则，例如每天 30 或 60 分钟",
        "打开该应用，确认时间到后会出现拦截遮罩",
        "测试期间如失效，请记录机型、系统版本、失效时间和操作路径"
    )
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
                text = when {
                    !allRequiredGranted -> "先补齐核心权限，再开始配置规则。"
                    !hasPasswordConfigured -> "先设置家长密码，再开始配置规则。"
                    else -> "可以开始亲友测试前的第一条规则配置。"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            lines.forEachIndexed { index, line ->
                Text(
                    text = "${index + 1}. $line",
                    fontSize = 14.sp
                )
            }
        }
    }
}

private data class DeviceSetupGuide(
    val brandName: String,
    val steps: List<String>,
    val note: String = ""
) {
    companion object {
        fun current(): DeviceSetupGuide {
            val manufacturer = Build.MANUFACTURER.orEmpty()
            val brand = Build.BRAND.orEmpty()
            val identity = "$manufacturer $brand".lowercase()
            return when {
                identity.contains("huawei") || identity.contains("honor") -> DeviceSetupGuide(
                    brandName = "华为 / 荣耀",
                    steps = listOf(
                        "手机管家 > 应用启动管理，将本应用改为手动管理",
                        "允许自启动、关联启动、后台活动",
                        "电池设置为不受限制，并允许通知",
                        "最近任务列表中锁定本应用"
                    ),
                    note = "华为/荣耀的后台活动设置对长时间守护稳定性很关键。"
                )
                identity.contains("xiaomi") || identity.contains("redmi") -> DeviceSetupGuide(
                    brandName = "小米 / Redmi",
                    steps = listOf(
                        "手机管家 > 应用管理 > 权限，允许自启动",
                        "省电策略设置为无限制",
                        "允许后台弹出界面和悬浮窗",
                        "最近任务列表中下拉或点锁图标锁定本应用"
                    )
                )
                identity.contains("oppo") || identity.contains("oneplus") || identity.contains("realme") -> DeviceSetupGuide(
                    brandName = "OPPO / 一加 / realme",
                    steps = listOf(
                        "设置 > 应用 > 应用管理，确认允许自启动",
                        "电池/耗电管理中允许后台运行或设为不受限制",
                        "允许悬浮窗、通知和使用情况访问",
                        "最近任务列表中锁定本应用"
                    )
                )
                identity.contains("vivo") || identity.contains("iqoo") -> DeviceSetupGuide(
                    brandName = "vivo / iQOO",
                    steps = listOf(
                        "i管家或系统设置中允许自启动",
                        "电池管理中允许后台高耗电或后台运行",
                        "允许悬浮窗、通知和使用情况访问",
                        "最近任务列表中锁定本应用"
                    )
                )
                else -> DeviceSetupGuide(
                    brandName = if (brand.isNotBlank()) brand else "未知品牌",
                    steps = listOf(
                        "在系统设置中允许自启动或后台启动",
                        "电池管理中设置为不受限制",
                        "允许悬浮窗、通知和使用情况访问",
                        "最近任务列表中锁定本应用"
                    ),
                    note = "不同系统入口名称可能不同，可在设置中搜索“自启动”“电池优化”“后台”。"
                )
            }
        }
    }
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
