package com.kidsphoneguard.observer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kidsphoneguard.observer.core.ObserverLogStore
import com.kidsphoneguard.observer.service.ObserverForegroundService

class MainAppSignalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val source = intent.getStringExtra("source").orEmpty().ifEmpty { "unknown" }
        val eventAt = intent.getLongExtra("eventAt", System.currentTimeMillis())
        val summary = listOf(
            "source=$source",
            "serviceEnabled=${intent.getBooleanExtra("serviceEnabled", false)}",
            "accessibilityEnabled=${intent.getBooleanExtra("accessibilityEnabled", false)}",
            "accessibilityRunning=${intent.getBooleanExtra("accessibilityRunning", false)}",
            "usagePermissionGranted=${intent.getBooleanExtra("usagePermissionGranted", false)}",
            "usageRunning=${intent.getBooleanExtra("usageRunning", false)}",
            "accessibilityMissing=${intent.getBooleanExtra("accessibilityMissing", false)}",
            "usageMissing=${intent.getBooleanExtra("usageMissing", false)}",
            "accessibilityStale=${intent.getBooleanExtra("accessibilityStale", false)}",
            "usageStale=${intent.getBooleanExtra("usageStale", false)}",
            "accessibilityHeartbeatAge=${intent.getLongExtra("accessibilityHeartbeatAge", -1L)}",
            "usageHeartbeatAge=${intent.getLongExtra("usageHeartbeatAge", -1L)}",
            "topPackage=${intent.getStringExtra("topPackage").orEmpty()}",
            "powerInteractive=${intent.getBooleanExtra("powerInteractive", false)}",
            "powerSave=${intent.getBooleanExtra("powerSave", false)}",
            "updateChanged=${intent.getBooleanExtra("updateChanged", false)}",
            "reasonSummary=${intent.getStringExtra("reasonSummary").orEmpty()}",
            "degraded=${intent.getBooleanExtra("degraded", false)}"
        ).joinToString("|")
        ObserverLogStore.persistMainBridgeHeartbeat(context, source, eventAt, summary)
        ObserverForegroundService.start(context, "main_bridge:$source")
    }
}
