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
internal fun SetupMaintenanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager.getInstance(context) }
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var permissionStatus by remember { mutableStateOf(PermissionManager.checkAllPermissions(context)) }
    var hasPasswordConfigured by remember { mutableStateOf(passwordManager.hasPasswordConfigured()) }
    var brandSetupConfirmed by remember { mutableStateOf(settingsManager.isBrandSetupConfirmed()) }
    val scrollState = rememberScrollState()
    val deviceSetupGuide = remember { DeviceSetupGuide.current() }

    fun refreshStatus(showFeedback: Boolean = false) {
        val latestPermissions = PermissionManager.checkAllPermissions(context)
        val latestPasswordConfigured = passwordManager.hasPasswordConfigured()
        val latestBrandSetupConfirmed = settingsManager.isBrandSetupConfirmed()
        permissionStatus = latestPermissions
        hasPasswordConfigured = latestPasswordConfigured
        brandSetupConfirmed = latestBrandSetupConfirmed

        if (showFeedback) {
            val completedCount = listOf(
                latestPasswordConfigured,
                latestPermissions[PermissionManager.PermissionType.OVERLAY] == true,
                latestPermissions[PermissionManager.PermissionType.USAGE_STATS] == true,
                latestPermissions[PermissionManager.PermissionType.BATTERY_OPTIMIZATION] == true,
                latestBrandSetupConfirmed,
                latestPermissions[PermissionManager.PermissionType.ACCESSIBILITY] == true
            ).count { it }
            android.util.Log.d(
                "SetupMaintenance",
                "manual permission refresh completed=$completedCount total=6"
            )
            Toast.makeText(
                context,
                "检查完成：已配置 $completedCount / 6 项",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openStep(stepId: SetupStepId) {
        if (stepId != SetupStepId.PASSWORD) {
            settingsManager.allowSetupSettingsAccess(SETUP_SETTINGS_ACCESS_ALLOWANCE_MS)
        }
        when (stepId) {
            SetupStepId.PASSWORD -> context.startActivity(Intent(context, PasswordSettingsActivity::class.java))
            SetupStepId.OVERLAY -> PermissionManager.requestOverlayPermission(context)
            SetupStepId.USAGE_STATS -> PermissionManager.requestUsageStatsPermission(context)
            SetupStepId.BATTERY -> PermissionManager.requestIgnoreBatteryOptimizations(context)
            SetupStepId.BRAND_SETUP -> openBrandSetupEntry(context)
            SetupStepId.ACCESSIBILITY -> PermissionManager.requestAccessibilityPermission(
                context = context,
                forceOpenWhenEnabled = true
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            refreshStatus()
        }
    }

    val maintenanceItems = listOf(
        SetupMaintenanceItem(
            id = SetupStepId.PASSWORD,
            title = "家长密码",
            description = "用于进入家长配置、临时解锁和修改规则。",
            done = hasPasswordConfigured,
            actionText = if (hasPasswordConfigured) "修改密码" else "设置密码"
        ),
        SetupMaintenanceItem(
            id = SetupStepId.OVERLAY,
            title = "悬浮窗权限",
            description = "用于显示应用时间已到的拦截遮罩。",
            done = permissionStatus[PermissionManager.PermissionType.OVERLAY] == true,
            actionText = "打开悬浮窗设置"
        ),
        SetupMaintenanceItem(
            id = SetupStepId.USAGE_STATS,
            title = "使用情况访问",
            description = "用于统计每日使用时长和判断限时规则。",
            done = permissionStatus[PermissionManager.PermissionType.USAGE_STATS] == true,
            actionText = "打开使用情况访问"
        ),
        SetupMaintenanceItem(
            id = SetupStepId.BATTERY,
            title = "电池优化与后台运行",
            description = "用于降低黑屏、后台或省电策略导致守护失效的概率。",
            done = permissionStatus[PermissionManager.PermissionType.BATTERY_OPTIMIZATION] == true,
            actionText = "打开电池设置"
        ),
        SetupMaintenanceItem(
            id = SetupStepId.BRAND_SETUP,
            title = "${deviceSetupGuide.brandName} 后台保活",
            description = "确认自启动、关联启动、后台活动和最近任务锁定等品牌专有设置。",
            done = brandSetupConfirmed,
            actionText = "打开品牌设置入口"
        ),
        SetupMaintenanceItem(
            id = SetupStepId.ACCESSIBILITY,
            title = "无障碍服务",
            description = "核心守护权限。建议最后确认，开启后会开始拦截受限入口。",
            done = permissionStatus[PermissionManager.PermissionType.ACCESSIBILITY] == true,
            actionText = "打开无障碍设置"
        )
    )
    val completedCount = maintenanceItems.count { it.done }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "配置向导",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "这里是家长配置内的维护面板。绿色表示已配置，红色表示还需要处理；每一项都可以重新打开系统入口复查。",
            fontSize = 15.sp,
            color = Color.Gray
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "已完成 $completedCount / ${maintenanceItems.size} 项",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "如果只是复查某一项，可以直接点击对应卡片，不需要重新走首次安装流程。",
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            }
        }

        maintenanceItems.forEach { item ->
            SetupMaintenanceCard(
                item = item,
                guide = deviceSetupGuide,
                onOpen = { openStep(item.id) },
                onRefresh = { refreshStatus(showFeedback = true) },
                onConfirmBrandSetup = {
                    settingsManager.setBrandSetupConfirmed(true)
                    brandSetupConfirmed = true
                    Toast.makeText(context, "已标记品牌后台设置完成", Toast.LENGTH_SHORT).show()
                }
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回家长配置", fontSize = 17.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SetupMaintenanceCard(
    item: SetupMaintenanceItem,
    guide: DeviceSetupGuide,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onConfirmBrandSetup: () -> Unit
) {
    val containerColor = if (item.done) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val statusColor = if (item.done) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    val statusText = if (item.done) "已配置" else "待配置"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Text(
                text = item.description,
                fontSize = 14.sp,
                color = Color(0xFF475569)
            )

            if (item.id == SetupStepId.BRAND_SETUP) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        guide.steps.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                fontSize = 13.sp
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(item.actionText)
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重新检查")
                }
            }

            if (item.id == SetupStepId.BRAND_SETUP) {
                OutlinedButton(
                    onClick = onConfirmBrandSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("我已完成品牌后台设置")
                }
            }
        }
    }
}

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
    val appName = stringResource(R.string.app_name)
    val appSubtitle = stringResource(R.string.app_subtitle)
    val wizardSteps = listOf(
        SetupWizardStep(
            id = SetupStepId.PASSWORD,
            title = "设置家长密码",
            reason = "先设置家长密码，后续进入配置页或临时解锁时都需要它。",
            instructions = listOf(
                "点击“去设置家长密码”。",
                "如果是第一次设置，只需要输入新密码和确认密码。",
                "建议使用家长容易记住、孩子不容易猜到的 6 位以上数字。"
            ),
            done = hasPasswordConfigured,
            primaryText = "去设置家长密码"
        ),
        SetupWizardStep(
            id = SetupStepId.OVERLAY,
            title = "开启悬浮窗权限",
            reason = "用于在受限应用上显示拦截遮罩。",
            instructions = listOf(
                "进入系统页面后，找到“$appName”。",
                "开启“允许显示在其他应用上层”“悬浮窗”或类似名称的开关。",
                "如果系统提示可能遮挡其他应用，这是安卓对悬浮窗权限的通用风险提示，本应用仅用于显示拦截遮罩。"
            ),
            done = permissionStatus[PermissionManager.PermissionType.OVERLAY] == true,
            primaryText = "去开启悬浮窗"
        ),
        SetupWizardStep(
            id = SetupStepId.USAGE_STATS,
            title = "开启使用情况访问",
            reason = "用于计算每个应用今天已经使用了多久。",
            instructions = listOf(
                "进入“使用情况访问权限”页面后，找到“$appName”。",
                "打开允许访问使用情况的开关。",
                "系统可能提示可查看应用使用记录，这是计时和每日限制所必需的权限。"
            ),
            done = permissionStatus[PermissionManager.PermissionType.USAGE_STATS] == true,
            primaryText = "去开启使用情况访问"
        ),
        SetupWizardStep(
            id = SetupStepId.BATTERY,
            title = "允许忽略电池优化",
            reason = "用于降低系统清理后台导致守护失效的概率。",
            instructions = listOf(
                "进入电池优化页面后，选择允许本应用不受电池优化限制。",
                "如果页面显示“允许后台运行”“不限制”“无限制”，请选择最宽松的选项。",
                "系统可能提示会增加耗电，这是为了让守护服务在黑屏和后台时继续工作。"
            ),
            done = permissionStatus[PermissionManager.PermissionType.BATTERY_OPTIMIZATION] == true,
            primaryText = "去设置电池优化"
        ),
        SetupWizardStep(
            id = SetupStepId.BRAND_SETUP,
            title = "确认${deviceSetupGuide.brandName}后台保活设置",
            reason = "这一步多数系统不允许 App 自动读取，需要家长进入系统设置确认。",
            instructions = listOf(
                "按下面的品牌步骤逐项检查。",
                "重点确认自启动、关联启动、后台活动、电池无限制和最近任务锁定。",
                "如果系统提示后台运行风险，这是手机厂商对长期后台服务的通用提示；不完成这一步，守护可能在数小时后失效。"
            ),
            done = brandSetupConfirmed,
            primaryText = "打开设置入口"
        ),
        SetupWizardStep(
            id = SetupStepId.ACCESSIBILITY,
            title = "开启无障碍服务",
            reason = "这是最后一步，也是核心权限，用于识别前台应用并执行拦截。",
            instructions = listOf(
                "进入无障碍设置后，找到“$appName”。",
                "打开服务开关，并在系统弹窗中选择允许或确定。",
                "系统会提示该权限可能读取屏幕内容、执行点击等高危能力，这是 Android 对无障碍服务的统一警告；本应用在本机用于识别前台应用和执行拦截，不需要云端上传。",
                "开启后请返回本应用，确认向导显示基础配置已完成。"
            ),
            done = permissionStatus[PermissionManager.PermissionType.ACCESSIBILITY] == true,
            primaryText = "去开启无障碍服务"
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
            modifier = Modifier.padding(bottom = 8.dp)
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
                    if (activeStep.id != SetupStepId.PASSWORD) {
                        settingsManager.allowSetupSettingsAccess(SETUP_SETTINGS_ACCESS_ALLOWANCE_MS)
                    }
                    when (activeStep.id) {
                        SetupStepId.OVERLAY -> PermissionManager.requestOverlayPermission(context)
                        SetupStepId.USAGE_STATS -> PermissionManager.requestUsageStatsPermission(context)
                        SetupStepId.BATTERY -> PermissionManager.requestIgnoreBatteryOptimizations(context)
                        SetupStepId.ACCESSIBILITY -> PermissionManager.requestAccessibilityPermission(context)
                        SetupStepId.BRAND_SETUP -> {
                            openBrandSetupEntry(context)
                        }
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
        flags = PermissionManager.protectedSettingsLaunchFlags()
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = PermissionManager.protectedSettingsLaunchFlags()
            })
        }
}

internal fun isProtectionDegraded(context: android.content.Context): Boolean {
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
                text = "完成方式：点击下面按钮进入系统页面，按下面说明完成后返回本页。",
                fontSize = 14.sp,
                color = Color.Gray
            )
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
                        text = "具体操作",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    step.instructions.forEachIndexed { index, instruction ->
                        Text(
                            text = "${index + 1}. $instruction",
                            fontSize = 14.sp
                        )
                    }
                }
            }
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
private fun SetupWizardReturnCard(onBack: () -> Unit) {
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
                text = "当前是从家长配置进入的配置向导。需要继续调整应用规则时，直接返回家长配置即可，不需要再次输入密码。",
                fontSize = 15.sp
            )
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("返回家长配置", fontSize = 17.sp)
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
            Text(
                text = "如需做系统维护，请先进入家长配置开启“全局解锁”，再到系统设置中操作。",
                fontSize = 14.sp,
                color = Color(0xFF5D4037)
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
    PASSWORD,
    OVERLAY,
    USAGE_STATS,
    BATTERY,
    BRAND_SETUP,
    ACCESSIBILITY
}

private data class SetupWizardStep(
    val id: SetupStepId,
    val title: String,
    val reason: String,
    val instructions: List<String>,
    val done: Boolean,
    val primaryText: String
)

private data class SetupMaintenanceItem(
    val id: SetupStepId,
    val title: String,
    val description: String,
    val done: Boolean,
    val actionText: String
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
                        "先打开一次拉钩守护，再进入最近任务列表锁定本应用（必须看到锁标记）"
                    ),
                    note = "华为/荣耀会在长时间息屏后批量停止未锁定应用；最近任务锁定与后台活动设置都必须完成。"
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
