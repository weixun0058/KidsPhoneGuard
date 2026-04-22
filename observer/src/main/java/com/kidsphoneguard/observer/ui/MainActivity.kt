package com.kidsphoneguard.observer.ui

import android.content.ActivityNotFoundException
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.kidsphoneguard.observer.core.ObserverLogStore
import com.kidsphoneguard.observer.service.ObserverForegroundService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ObserverScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ObserverForegroundService.start(this, "activity_resume")
    }
}

@Composable
private fun ObserverScreen() {
    val context = LocalContext.current
    var latestSummary by remember { mutableStateOf(ObserverLogStore.readLatestSummary(context)) }
    var latestEventAt by remember { mutableLongStateOf(ObserverLogStore.readLatestEventAt(context)) }
    var lastHeartbeatAt by remember { mutableLongStateOf(ObserverLogStore.readLastHeartbeatAt(context)) }
    var lastHeartbeatSource by remember { mutableStateOf(ObserverLogStore.readLastHeartbeatSource(context)) }
    var bridgeSummary by remember { mutableStateOf(ObserverLogStore.readLastBridgeSummary(context)) }
    var isIgnoringBattery by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var usageAccessGranted by remember { mutableStateOf(context.hasUsageAccess()) }
    var lastSettingAction by remember { mutableStateOf("尚未执行设置操作") }
    var manualGuide by remember { mutableStateOf("如果某个设置页无法自动打开，请按按钮提示从系统设置中手动进入。") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            latestSummary = ObserverLogStore.readLatestSummary(context)
            latestEventAt = ObserverLogStore.readLatestEventAt(context)
            lastHeartbeatAt = ObserverLogStore.readLastHeartbeatAt(context)
            lastHeartbeatSource = ObserverLogStore.readLastHeartbeatSource(context)
            bridgeSummary = ObserverLogStore.readLastBridgeSummary(context)
            isIgnoringBattery = context.isIgnoringBatteryOptimizations()
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            usageAccessGranted = context.hasUsageAccess()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "守护旁路观察员", fontSize = 26.sp)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "最新旁路快照")
                Text(text = latestSummary)
                Text(text = "latestEventAt=$latestEventAt")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "最近主应用桥接心跳")
                Text(text = "lastHeartbeatAt=$lastHeartbeatAt")
                Text(text = "lastHeartbeatSource=$lastHeartbeatSource")
                Text(text = bridgeSummary)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "后台保活配置")
                Text(text = "使用情况访问：${if (usageAccessGranted) "已允许" else "未允许"}")
                Text(text = "电池优化忽略：${if (isIgnoringBattery) "已允许" else "未允许"}")
                Text(text = "通知权限：${if (notificationsEnabled) "已允许" else "未允许"}")
                Text(text = "自启动/后台运行：系统未开放读取状态，请进入系统页后手动确认已允许。")
                Text(text = "最近一次操作：$lastSettingAction")
                Text(text = "操作提示：$manualGuide")
            }
        }
        Button(
            onClick = { ObserverForegroundService.start(context, "manual_refresh") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("立即刷新旁路快照")
        }
        Button(
            onClick = {
                lastSettingAction = context.openUsageAccessSettings()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (usageAccessGranted) "使用情况访问：已允许" else "使用情况访问：去开启")
        }
        Button(
            onClick = {
                lastSettingAction = context.requestIgnoreBatteryOptimizations()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isIgnoringBattery) "忽略电池优化：已允许" else "忽略电池优化：去开启")
        }
        Button(
            onClick = {
                lastSettingAction = context.openNotificationSettings()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (notificationsEnabled) "通知权限：已允许" else "通知权限：去开启")
        }
        Button(
            onClick = {
                lastSettingAction = context.openHuaweiStartupSettings()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("自启动/应用启动管理：去手动确认")
        }
        Button(
            onClick = {
                val guide = "请进入：系统设置 > 应用 > 应用管理 > 守护旁路观察员 > 电池/耗电管理，确认不受限制并允许后台运行。也可以从手机管家 > 应用启动管理中确认允许后台活动。"
                manualGuide = guide
                lastSettingAction = context.showAndLogSettingResult("已显示后台运行/电池管理手动路径。")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("后台运行/电池管理：显示手动路径")
        }
        Button(
            onClick = {
                lastSettingAction = context.openHomeForRecentTaskLockGuide()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("最近任务锁定：查看操作提示")
        }
        Button(
            onClick = {
                lastSettingAction = context.openAppDetailsSettings()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开观察员应用详情")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun Context.openUsageAccessSettings(): String {
    return startActivityWithMessage(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        success = "已打开使用情况访问页，请找到守护旁路观察员并允许。",
        failure = "无法打开使用情况访问页，已尝试打开应用详情。"
    ) {
        openAppDetailsSettings()
    }
}

private fun Context.requestIgnoreBatteryOptimizations(): String {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    return if (startActivitySafely(requestIntent, showToastOnFailure = false)) {
        showAndLogSettingResult("已打开忽略电池优化请求页，请选择允许。")
    } else {
        startActivityWithMessage(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            success = "已打开电池优化设置页，请找到守护旁路观察员并设为不优化。",
            failure = "无法打开电池优化设置页，已尝试打开应用详情。"
        ) {
            openAppDetailsSettings()
        }
    }
}

private fun Context.openNotificationSettings(): String {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    }
    return startActivityWithMessage(
        intent,
        success = "已打开通知设置页，请确认通知已允许。",
        failure = "无法打开通知设置页，已尝试打开应用详情。"
    ) {
        openAppDetailsSettings()
    }
}

private fun Context.openHuaweiStartupSettings(): String {
    val candidates = listOfNotNull(
        packageManager.getLaunchIntentForPackage("com.huawei.systemmanager"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        },
        Intent(Settings.ACTION_SETTINGS)
    )
    return if (startFirstAvailable(candidates)) {
        showAndLogSettingResult("已打开系统管理入口，请进入应用启动管理，允许自启动、关联启动、后台活动。")
    } else {
        showAndLogSettingResult("无法打开系统管理入口，请从系统设置手动搜索“应用启动管理”。")
    }
}

private fun Context.openHomeForRecentTaskLockGuide(): String {
    val message = "请手动打开最近任务列表，找到观察员，下拉或点锁图标锁定。应用无法读取锁定状态。"
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    return if (startActivitySafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))) {
        showAndLogSettingResult(message)
    } else {
        showAndLogSettingResult("无法自动回到桌面，请手动打开最近任务并锁定观察员。")
    }
}

private fun Context.openAppDetailsSettings(): String {
    return startActivityWithMessage(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        },
        success = "已打开应用详情页，请检查权限、电池、通知和后台运行。",
        failure = "无法打开应用详情页，请从系统设置中手动查找守护旁路观察员。"
    )
}

private fun Context.startFirstAvailable(intents: List<Intent>): Boolean {
    return intents.any { startActivitySafely(it, showToastOnFailure = false) }
}

private fun Context.startActivitySafely(intent: Intent, showToastOnFailure: Boolean = true): Boolean {
    return try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: ActivityNotFoundException) {
        if (showToastOnFailure) {
            Toast.makeText(this, "系统没有提供对应设置页。", Toast.LENGTH_LONG).show()
        }
        false
    } catch (error: SecurityException) {
        if (showToastOnFailure) {
            Toast.makeText(this, "系统限制打开该设置页，请从系统设置中手动进入。", Toast.LENGTH_LONG).show()
        }
        false
    } catch (error: RuntimeException) {
        if (showToastOnFailure) {
            Toast.makeText(this, "系统拒绝打开该设置页，请从系统设置中手动进入。", Toast.LENGTH_LONG).show()
        }
        false
    }
}

private fun Context.startActivityWithMessage(
    intent: Intent,
    success: String,
    failure: String,
    fallback: (() -> Unit)? = null
): String {
    return if (startActivitySafely(intent, showToastOnFailure = false)) {
        showAndLogSettingResult(success)
    } else {
        fallback?.invoke()
        showAndLogSettingResult(failure)
    }
}

private fun Context.showAndLogSettingResult(message: String): String {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    ObserverLogStore.appendLine(this, "observer_setting_action", message)
    return message
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.hasUsageAccess(): Boolean {
    return runCatching {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val appInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}
