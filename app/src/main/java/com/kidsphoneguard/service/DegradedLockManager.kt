package com.kidsphoneguard.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kidsphoneguard.utils.PasswordManager
import com.kidsphoneguard.utils.PermissionManager
import com.kidsphoneguard.utils.SettingsManager

/**
 * 降级锁定管理器
 *
 * 当无障碍服务被系统关闭、未绑定或失联时，利用悬浮窗权限（SYSTEM_ALERT_WINDOW）
 * 显示全屏不可绕过的锁定遮罩，强制引导用户恢复无障碍权限。
 *
 * 技术支点：悬浮窗权限与无障碍权限彼此独立，系统关闭无障碍不影响悬浮窗。
 *
 * 行为：
 * - 检测到设置未启用、服务未绑定或心跳失联且屏幕亮起 → 显示全屏锁定遮罩
 * - 遮罩提供"一键恢复"按钮跳转无障碍设置
 * - 遮罩提供家长密码解锁（合规需求）
 * - 检测到服务实际恢复运行 → 自动解除锁定
 */
object DegradedLockManager {

    private const val TAG = "DegradedLockManager"
    private const val PASSWORD_INPUT_TAG = "degraded_password_input"

    @Volatile
    private var lockView: View? = null

    @Volatile
    private var isLocked = false

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 显示降级锁定遮罩
     * 仅在屏幕亮起且有悬浮窗权限时生效
     */
    fun showLockScreen(context: Context) {
        if (isLocked) {
            Log.d(TAG, "lock_already_showing")
            return
        }
        if (SettingsManager.getInstance(context).isSetupSettingsAccessAllowed()) {
            Log.d(TAG, "skip_lock_screen: setup settings access allowed")
            dismissLockScreen(context)
            return
        }
        if (!PermissionManager.canDrawOverlays(context)) {
            Log.w(TAG, "cannot_show_lock: no overlay permission")
            return
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "screen_off_defer_lock")
            return
        }

        handler.post {
            try {
                doShowLockScreen(context)
            } catch (e: Exception) {
                Log.e(TAG, "show_lock_failed: ${e.message}", e)
            }
        }
    }

    /**
     * 解除锁定遮罩
     */
    fun dismissLockScreen(context: Context) {
        if (!isLocked) return

        handler.post {
            try {
                doRemoveLockView(context)
                Log.w(TAG, "lock_dismissed: accessibility restored")
            } catch (e: Exception) {
                Log.e(TAG, "dismiss_lock_failed: ${e.message}", e)
            }
        }
    }

    /**
     * 是否正在显示锁定遮罩
     */
    fun isLockShowing(): Boolean = isLocked

    /**
     * 当屏幕亮起时调用，如果需要锁定则显示
     */
    fun onScreenOn(context: Context) {
        if (!isLocked && shouldLock(context)) {
            showLockScreen(context)
        } else if (isLocked && !shouldLock(context)) {
            dismissLockScreen(context)
        }
    }

    /**
     * 判断是否需要锁定
     */
    fun shouldLock(context: Context): Boolean {
        return !PermissionManager.isAccessibilityServiceOperational(context) &&
            !SettingsManager.getInstance(context).isSetupSettingsAccessAllowed()
    }

    // ===== 内部实现 =====

    private fun doShowLockScreen(context: Context) {
        if (isLocked) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val appContext = context.applicationContext

        val view = buildLockView(appContext)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE 不加 → 让 EditText 可以获得焦点输入密码
            // FLAG_LAYOUT_IN_SCREEN → 覆盖状态栏
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE

        try {
            windowManager.addView(view, params)
            lockView = view
            isLocked = true
            Log.w(TAG, "lock_screen_shown")
            focusPasswordInput(appContext, view)
        } catch (e: Exception) {
            Log.e(TAG, "addView_failed: ${e.message}", e)
        }
    }

    private fun doRemoveLockView(context: Context) {
        val view = lockView ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "removeView_failed: ${e.message}", e)
        } finally {
            lockView = null
            isLocked = false
        }
    }

    private fun buildLockView(context: Context): View {
        val density = context.resources.displayMetrics.density

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E8222222")) // 深色半透明
            setPadding(dp(40, density), dp(60, density), dp(40, density), dp(60, density))
            // 拦截返回键
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ ->
                keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                    keyCode == android.view.KeyEvent.KEYCODE_HOME
            }
        }

        // 锁定图标
        val iconText = TextView(context).apply {
            text = "🔒"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(iconText, marginParams(density, bottom = 16))

        // 标题
        val titleText = TextView(context).apply {
            text = "设备保护功能需要重新启用"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rootLayout.addView(titleText, marginParams(density, bottom = 12))

        // 说明
        val descText = TextView(context).apply {
            text = "系统关闭了安全保护组件，手机使用功能已暂停。\n请按下方提示恢复，或联系家长处理。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
            setLineSpacing(dp(4, density).toFloat(), 1f)
        }
        rootLayout.addView(descText, marginParams(density, bottom = 32))

        // 一键恢复按钮
        val restoreButton = Button(context).apply {
            text = "👉 点击恢复保护功能"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            setPadding(dp(24, density), dp(14, density), dp(24, density), dp(14, density))
            setOnClickListener {
                try {
                    // 先尝试 WRITE_SECURE_SETTINGS 静默恢复
                    if (
                        AccessibilitySettingsRecovery.tryRestore(
                            context,
                            source = "degraded_lock_button"
                        ) == AccessibilitySettingsRecovery.Result.RESTORED
                    ) {
                        Toast.makeText(context, "✅ 保护功能已自动恢复", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // 降级：跳转无障碍设置页
                    PermissionManager.requestAccessibilityPermission(
                        context,
                        forceOpenWhenEnabled = false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "restore_button_failed: ${e.message}", e)
                }
            }
        }
        rootLayout.addView(restoreButton, marginParams(density, bottom = 24))

        // 分隔线
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#444444"))
        }
        rootLayout.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1, density)
        ).apply {
            bottomMargin = dp(20, density)
        })

        // 家长密码区域标题
        val passwordLabel = TextView(context).apply {
            text = "家长临时解锁"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        rootLayout.addView(passwordLabel, marginParams(density, bottom = 8))

        // 密码输入
        val passwordInput = EditText(context).apply {
            tag = PASSWORD_INPUT_TAG
            hint = "请输入家长密码"
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(dp(16, density), dp(12, density), dp(16, density), dp(12, density))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                requestFocus()
                showKeyboard(context, this)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showKeyboard(context, this)
                }
            }
        }
        rootLayout.addView(passwordInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12, density)
        })

        // 密码解锁按钮
        val unlockButton = Button(context).apply {
            text = "验证密码并临时解锁"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            setOnClickListener {
                val input = passwordInput.text.toString()
                val pm = PasswordManager.getInstance(context)
                if (!pm.hasPasswordConfigured()) {
                    Toast.makeText(context, "尚未设置家长密码，请先在主界面设置", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (pm.verifyPassword(input)) {
                    Log.w(TAG, "parent_password_unlock")
                    doRemoveLockView(context)
                    Toast.makeText(context, "已临时解锁，请尽快恢复保护功能", Toast.LENGTH_LONG).show()
                } else {
                    passwordInput.setText("")
                    Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootLayout.addView(unlockButton, marginParams(density, bottom = 20))

        // 底部提示
        val footerText = TextView(context).apply {
            text = "如需帮助，请联系家长"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
        }
        rootLayout.addView(footerText)

        return rootLayout
    }

    private fun focusPasswordInput(context: Context, rootView: View) {
        val input = rootView.findViewWithTag<EditText>(PASSWORD_INPUT_TAG) ?: return
        handler.postDelayed({
            try {
                input.requestFocus()
                showKeyboard(context, input)
                Log.d(TAG, "password_input_focus_requested")
            } catch (e: Exception) {
                Log.e(TAG, "password_input_focus_failed: ${e.message}", e)
            }
        }, 250)
    }

    private fun showKeyboard(context: Context, input: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    // ===== 工具方法 =====

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()

    private fun marginParams(density: Float, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(bottom, density)
        }
    }
}
