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
import com.kidsphoneguard.utils.SettingsManager
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
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var permissionStatus by remember { mutableStateOf(PermissionManager.checkAllPermissions(context)) }
    var protectionDegraded by remember { mutableStateOf(isProtectionDegraded(context)) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var hasPasswordConfigured by remember { mutableStateOf(passwordManager.hasPasswordConfigured()) }
    var brandSetupConfirmed by remember { mutableStateOf(settingsManager.isBrandSetupConfirmed()) }
    val scrollState = rememberScrollState()
    val deviceSetupGuide = remember { DeviceSetupGuide.current() }
    val wizardSteps = listOf(
        SetupWizardStep(
            id = SetupStepId.OVERLAY,
            title = "开启悬浮窗权限",
            reason = "用于在受限应用上显示拦截遮罩。",
            done = permissionStatus[PermissionManager.PermissionType.OVERLAY] == true,
            primaryText = "去开启悬浮窗"
        ),
        SetupWizardStep(
            id = SetupStepId.USAGE_STATS,
            title = "开启使用情况访问",
            reason = "用于计算每个应用今天已经使用了多久。",
            done = permissionStatus[PermissionManager.PermissionType.USAGE_STATS] == true,
            primaryText = "去开启使用情况访问"
        ),
        SetupWizardStep(
            id = SetupStepId.BATTERY,
            title = "允许忽略电池优化",
            reason = "用于降低系统清理后台导致守护失效的概率。",
            done = permissionStatus[PermissionManager.PermissionType.BATTERY_OPTIMIZATION] == true,
            primaryText = "去设置电池优化"
        ),
        SetupWizardStep(
            id = SetupStepId.ACCESSIBILITY,
            title = "开启无障碍服务",
            reason = "这是核心权限，用于识别前台应用并执行拦截。",
            done = permissionStatus[PermissionManager.PermissionType.ACCESSIBILITY] == true,
            primaryText = "去开启无障碍服务"
        ),
        SetupWizardStep(
            id = SetupStepId.BRAND_SETUP,
            title = "确认${deviceSetupGuide.brandName}后台保活设置",
            reason = "这一步多数系统不允许 App 自动读取，需要家长进入系统设置确认。",
            done = brandSetupConfirmed,
            primaryText = "打开设置入口"
        ),
        SetupWizardStep(
            id = SetupStepId.PASSWORD,
            title = "设置家长密码",
            reason = "用于保护家长配置页，避免儿童随意修改规则。",
            done = hasPasswordConfigured,
            primaryText = "去设置家长密码"
        )
    )
    val completedStepCount = wizardSteps.count { it.done }
    val activeStep = wizardSteps.firstOrNull { !it.done }

    // 定期检查权限状态
    LaunchedEffect(Unit) {
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
            text = "儿童手机守护",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
        )

        Text(
            text = "按向导一步一步完成配置，中途离开后会自动接着未完成步骤继续。",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        WizardProgressCard(
            completedCount = completedStepCount,
            totalCount = wizardSteps.size,
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

        if (activeStep != null) {
            SetupWizardStepCard(
                stepNumber = completedStepCount + 1,
                totalCount = wizardSteps.size,
                step = activeStep,
                guide = deviceSetupGuide,
                onPrimaryClick = {
                    when (activeStep.id) {
                        SetupStepId.OVERLAY -> PermissionManager.requestOverlayPermission(context)
                        SetupStepId.USAGE_STATS -> PermissionManager.requestUsageStatsPermission(context)
                        SetupStepId.BATTERY -> PermissionManager.requestIgnoreBatteryOptimizations(context)
                        SetupStepId.ACCESSIBILITY -> PermissionManager.requestAccessibilityPermission(context)
                        SetupStepId.BRAND_SETUP -> openBrandSetupEntry(context)
                        SetupStepId.PASSWORD -> context.startActivity(Intent(context, PasswordSettingsActivity::class.java))
                    }
                },
                onConfirmManualStep = {
                    settingsManager.setBrandSetupConfirmed(true)
                    brandSetupConfirmed = true
                },
                onRecheck = {
                    permissionStatus = PermissionManager.checkAllPermissions(context)
                    protectionDegraded = isProtectionDegraded(context)
                    hasPasswordConfigured = passwordManager.hasPasswordConfigured()
                    brandSetupConfirmed = settingsManager.isBrandSetupConfirmed()
                }
            )
        } else {
            SetupFinishedCard(
                onEnterConfig = {
                    showPasswordDialog = true
                },
                onResetBrandStep = {
                    settingsManager.setBrandSetupConfirmed(false)
                    brandSetupConfirmed = false
                    Toast.makeText(context, "已重置品牌后台确认步骤", Toast.LENGTH_SHORT).show()
                }
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

private fun openBrandSetupEntry(context: android.content.Context) {
    val opened = PermissionManager.requestHuaweiProtectionGuide(context)
    if (!opened) {
        openCurrentAppDetails(context)
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
private fun WizardProgressCard(
    completedCount: Int,
    totalCount: Int,
    protectionDegraded: Boolean
) {
    val progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()
    val allDone = completedCount >= totalCount
    val containerColor = when {
        protectionDegraded -> Color(0xFFFFEBEE)
        allDone -> Color(0xFFE8F5E9)
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
                text = "配置向导进度",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "已完成 $completedCount / $totalCount 步",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = when {
                    protectionDegraded -> "守护状态异常，请按当前步骤恢复。"
                    allDone -> "基础配置已完成，可以进入家长配置添加规则。"
                    else -> "请先完成当前步骤，返回本页后会自动进入下一步。"
                },
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SetupWizardStepCard(
    stepNumber: Int,
    totalCount: Int,
    step: SetupWizardStep,
    guide: DeviceSetupGuide,
    onPrimaryClick: () -> Unit,
    onConfirmManualStep: () -> Unit,
    onRecheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "第 $stepNumber / $totalCount 步",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = step.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = step.reason,
                fontSize = 15.sp
            )
            Text(
                text = "完成方式：点击下面按钮进入系统页面，按提示完成后返回本页。",
                fontSize = 14.sp,
                color = Color.Gray
            )
            if (step.id == SetupStepId.BRAND_SETUP) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "已识别：${guide.brandName}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        guide.steps.forEachIndexed { index, guideStep ->
                            Text(
                                text = "${index + 1}. $guideStep",
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
                    }
                }
            }
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(step.primaryText, fontSize = 17.sp)
            }
            if (step.id == SetupStepId.BRAND_SETUP) {
                OutlinedButton(
                    onClick = onConfirmManualStep,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("我已按上面步骤完成设置")
                }
            }
            OutlinedButton(
                onClick = onRecheck,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("我已完成，重新检查")
            }
        }
    }
}

@Composable
private fun SetupFinishedCard(
    onEnterConfig: () -> Unit,
    onResetBrandStep: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "基础配置已完成",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "下一步建议先添加 1 个常用娱乐应用规则，然后亲自打开该应用做一次拦截测试。",
                fontSize = 15.sp
            )
            Button(
                onClick = onEnterConfig,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("进入家长配置", fontSize = 17.sp)
            }
            TextButton(onClick = onResetBrandStep) {
                Text("重新确认品牌后台设置")
            }
        }
    }
}

private enum class SetupStepId {
    OVERLAY,
    USAGE_STATS,
    BATTERY,
    ACCESSIBILITY,
    BRAND_SETUP,
    PASSWORD
}

private data class SetupWizardStep(
    val id: SetupStepId,
    val title: String,
    val reason: String,
    val done: Boolean,
    val primaryText: String
)

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
