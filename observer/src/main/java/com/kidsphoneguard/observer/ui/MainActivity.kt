package com.kidsphoneguard.observer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            latestSummary = ObserverLogStore.readLatestSummary(context)
            latestEventAt = ObserverLogStore.readLatestEventAt(context)
            lastHeartbeatAt = ObserverLogStore.readLastHeartbeatAt(context)
            lastHeartbeatSource = ObserverLogStore.readLastHeartbeatSource(context)
            bridgeSummary = ObserverLogStore.readLastBridgeSummary(context)
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
        Button(
            onClick = { ObserverForegroundService.start(context, "manual_refresh") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("立即刷新旁路快照")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("授予观察员使用情况访问")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("请求忽略电池优化")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开观察员应用详情")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
