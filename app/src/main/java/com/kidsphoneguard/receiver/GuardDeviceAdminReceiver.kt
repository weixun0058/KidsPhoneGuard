package com.kidsphoneguard.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GuardDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "GuardDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.w(TAG, "device_admin_enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "device_admin_disabled")
    }
}
