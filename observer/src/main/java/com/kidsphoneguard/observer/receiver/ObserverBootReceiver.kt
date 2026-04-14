package com.kidsphoneguard.observer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kidsphoneguard.observer.core.ObserverContract
import com.kidsphoneguard.observer.core.ObserverLogStore
import com.kidsphoneguard.observer.service.ObserverForegroundService

class ObserverBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        val packageName = intent.data?.schemeSpecificPart.orEmpty()
        ObserverLogStore.appendLine(
            context,
            "observer_receiver_signal",
            "action=$action|package=$packageName"
        )
        if (packageName.isNotEmpty() && packageName != ObserverContract.targetPackageName) {
            return
        }
        ObserverForegroundService.start(context, "receiver:$action:$packageName")
    }
}
