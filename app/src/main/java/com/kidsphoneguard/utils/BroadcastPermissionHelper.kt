package com.kidsphoneguard.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

object BroadcastPermissionHelper {

    const val ACTION_BLOCK_APP = "com.kidsphoneguard.action.BLOCK_APP"
    private const val INTERNAL_BROADCAST_PERMISSION = "com.kidsphoneguard.permission.INTERNAL_GUARD_BROADCAST"

    private const val TAG = "BroadcastPermissionHelper"

    fun registerInternalBroadcastReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        action: String
    ) {
        val filter = IntentFilter(action)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    filter,
                    INTERNAL_BROADCAST_PERMISSION,
                    null,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, filter, INTERNAL_BROADCAST_PERMISSION, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册广播失败: ${e.message}", e)
        }
    }

    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "注销广播失败: ${e.message}")
        }
    }

    fun sendInternalBroadcast(context: Context, action: String, extras: Map<String, String> = emptyMap()): Boolean {
        return try {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
            }
            extras.forEach { (key, value) -> intent.putExtra(key, value) }
            context.sendBroadcast(intent, INTERNAL_BROADCAST_PERMISSION)
            true
        } catch (e: Exception) {
            Log.e(TAG, "发送广播失败: ${e.message}", e)
            false
        }
    }

    fun sendBlockAppBroadcast(context: Context, packageName: String): Boolean {
        return sendInternalBroadcast(context, ACTION_BLOCK_APP, mapOf("package_name" to packageName))
    }
}
