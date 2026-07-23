package com.kidsphoneguard.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * 家长特权页面的前台授权边界。
 *
 * 密码验证只授权当前这一次前台访问；页面一旦被 Home、最近任务、其他 Activity
 * 或系统设置覆盖，就立即移出任务栈。再次打开应用时只能回到主页面重新验证密码。
 */
abstract class ParentProtectedActivity : ComponentActivity() {

    private var hasEnteredForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "parent_access_created activity=${javaClass.simpleName}")
    }

    override fun onResume() {
        super.onResume()
        hasEnteredForeground = true
        Log.d(TAG, "parent_access_foreground activity=${javaClass.simpleName}")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        relockAndFinish(source = "user_leave_hint")
    }

    override fun onPause() {
        super.onPause()
        relockAndFinish(source = "pause")
    }

    private fun relockAndFinish(source: String) {
        if (!shouldRelockParentAccess(
                hasEnteredForeground = hasEnteredForeground,
                isFinishing = isFinishing
            )
        ) {
            return
        }
        hasEnteredForeground = false
        Log.w(
            TAG,
            "parent_access_relocked activity=${javaClass.simpleName} source=$source"
        )
        finish()
    }

    companion object {
        private const val TAG = "ParentProtectedActivity"

        internal fun shouldRelockParentAccess(
            hasEnteredForeground: Boolean,
            isFinishing: Boolean
        ): Boolean = hasEnteredForeground && !isFinishing
    }
}
