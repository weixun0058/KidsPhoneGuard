package com.kidsphoneguard.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.InputFilter
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kidsphoneguard.R
import com.kidsphoneguard.utils.PasswordManager
import com.kidsphoneguard.utils.PermissionManager
import com.kidsphoneguard.utils.RecoveryCodeManager
import com.kidsphoneguard.utils.RecoveryVerificationResult
import com.kidsphoneguard.utils.SettingsManager
import com.kidsphoneguard.utils.TrustedTimeProvider

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

    /**
     * Latest requested visibility. Show/dismiss calls can arrive from overlapping health and
     * recovery checks; the main-thread runnable must obey the newest request, not its queue order.
     */
    @Volatile
    private var desiredLocked = false

    @Volatile
    private var desiredBlockedPackageName = ""

    @Volatile
    private var currentBlockedPackageName = ""

    @Volatile
    private var parentTemporaryUnlockUntilElapsedRealtime = 0L

    private val exitToHomeStateLock = Any()
    private var exitToHomeBlockedPackageName = ""
    private var exitToHomeExpiresAtElapsedRealtime = 0L

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 显示降级锁定遮罩
     * 仅在屏幕亮起且有悬浮窗权限时生效
     */
    fun showLockScreen(context: Context, blockedPackageName: String) {
        if (SettingsManager.getInstance(context).isGlobalUnlockEnabled()) {
            desiredLocked = false
            dismissLockScreen(context, reason = "global_unlock_active")
            OverlayService.suppressForGlobalUnlock()
            Log.d(TAG, "lock_suppressed_by_global_unlock")
            return
        }
        if (isParentTemporaryUnlockActive()) {
            desiredLocked = false
            dismissLockScreen(context, reason = "parent_temporary_unlock_active")
            OverlayService.suppressForParentTemporaryUnlock()
            Log.d(TAG, "lock_suppressed_by_parent_temporary_unlock")
            return
        }
        val exitDecision = evaluateExitToHome(
            observedPackageName = blockedPackageName,
            safeDestination = false
        )
        if (exitDecision == DegradedExitToHomeDecision.SUPPRESS_TRANSITION_PENDING) {
            desiredLocked = false
            dismissLockScreen(context, reason = "exit_to_home_transition_pending")
            OverlayService.suppressForExitToHome()
            Log.d(
                TAG,
                "lock_suppressed_by_exit_to_home package=$blockedPackageName"
            )
            return
        }
        desiredLocked = true
        desiredBlockedPackageName = blockedPackageName
        currentBlockedPackageName = blockedPackageName
        if (isLocked) {
            OverlayService.suppressForDegradedLock()
            Log.d(TAG, "lock_already_showing package=$blockedPackageName")
            return
        }
        if (SettingsManager.getInstance(context).isSetupSettingsAccessAllowed()) {
            Log.d(TAG, "skip_lock_screen: setup settings access allowed")
            dismissLockScreen(context, reason = "setup_settings_access_allowed")
            return
        }
        if (!PermissionManager.canDrawOverlays(context)) {
            Log.w(TAG, "cannot_show_lock: no overlay permission")
            desiredLocked = false
            return
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "screen_off_defer_lock")
            desiredLocked = false
            return
        }

        // Degraded protection owns the screen while accessibility is unavailable. Remove any
        // normal app-block overlay before adding this interactive recovery surface.
        OverlayService.suppressForDegradedLock()
        handler.post {
            if (!desiredLocked) {
                Log.d(TAG, "lock_show_cancelled: newer dismiss request")
                return@post
            }
            try {
                if (SettingsManager.getInstance(context).isSetupSettingsAccessAllowed()) {
                    desiredLocked = false
                    Log.d(TAG, "lock_show_cancelled: setup settings access allowed")
                    return@post
                }
                if (!PermissionManager.canDrawOverlays(context)) {
                    desiredLocked = false
                    Log.w(TAG, "lock_show_cancelled: no overlay permission")
                    return@post
                }
                val latestPowerManager =
                    context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!latestPowerManager.isInteractive) {
                    desiredLocked = false
                    Log.d(TAG, "lock_show_cancelled: screen off")
                    return@post
                }
                OverlayService.suppressForDegradedLock()
                doShowLockScreen(context, desiredBlockedPackageName)
            } catch (e: Exception) {
                Log.e(TAG, "show_lock_failed: ${e.message}", e)
            }
        }
    }

    /**
     * 解除锁定遮罩
     */
    fun dismissLockScreen(context: Context, reason: String = "unspecified") {
        desiredLocked = false
        desiredBlockedPackageName = ""
        handler.post {
            if (desiredLocked) {
                Log.d(TAG, "lock_dismiss_cancelled: newer show request reason=$reason")
                return@post
            }
            if (!isLocked) {
                currentBlockedPackageName = ""
                return@post
            }
            try {
                doRemoveLockView(context)
                Log.w(TAG, "lock_dismissed reason=$reason")
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
     * 普通应用遮蔽层的互斥判断入口。包含尚未执行 addView 的待显示状态，避免两个
     * TYPE_APPLICATION_OVERLAY 请求在同一主线程队列中交错后同时出现。
     */
    fun isLockRequestedOrShowing(): Boolean = desiredLocked || isLocked

    fun isParentTemporaryUnlockActive(
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val until = parentTemporaryUnlockUntilElapsedRealtime
        val active = DegradedTemporaryUnlockPolicy.isActive(
            untilElapsedRealtime = until,
            nowElapsedRealtime = nowElapsedRealtime
        )
        if (!active && until != 0L) {
            parentTemporaryUnlockUntilElapsedRealtime = 0L
            Log.w(TAG, "parent_temporary_unlock_expired")
        }
        return active
    }

    fun clearParentTemporaryUnlock(reason: String) {
        val wasActive = parentTemporaryUnlockUntilElapsedRealtime != 0L
        parentTemporaryUnlockUntilElapsedRealtime = 0L
        if (wasActive) {
            Log.w(TAG, "parent_temporary_unlock_cleared reason=$reason")
        }
    }

    fun isExitToHomeInProgress(
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ): Boolean = synchronized(exitToHomeStateLock) {
        if (
            exitToHomeBlockedPackageName.isBlank() ||
            nowElapsedRealtime >= exitToHomeExpiresAtElapsedRealtime
        ) {
            exitToHomeBlockedPackageName = ""
            exitToHomeExpiresAtElapsedRealtime = 0L
            false
        } else {
            true
        }
    }

    fun evaluateExitToHome(
        observedPackageName: String,
        safeDestination: Boolean,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ): DegradedExitToHomeDecision {
        val decision = synchronized(exitToHomeStateLock) {
            val active = exitToHomeBlockedPackageName.isNotBlank()
            val result = DegradedExitToHomePolicy.evaluate(
                active = active,
                blockedPackageName = exitToHomeBlockedPackageName,
                observedPackageName = observedPackageName,
                safeDestination = safeDestination,
                expiresAtElapsedRealtime = exitToHomeExpiresAtElapsedRealtime,
                nowElapsedRealtime = nowElapsedRealtime
            )
            if (
                result == DegradedExitToHomeDecision.SAFE_DESTINATION_REACHED ||
                result == DegradedExitToHomeDecision.CANCELLED_BY_OTHER_FOREGROUND ||
                result == DegradedExitToHomeDecision.EXPIRED
            ) {
                exitToHomeBlockedPackageName = ""
                exitToHomeExpiresAtElapsedRealtime = 0L
            }
            result
        }
        if (
            decision != DegradedExitToHomeDecision.NONE &&
            decision != DegradedExitToHomeDecision.SUPPRESS_TRANSITION_PENDING
        ) {
            Log.d(
                TAG,
                "exit_to_home_state decision=$decision observed=$observedPackageName"
            )
        }
        return decision
    }

    fun clearExitToHome(reason: String) {
        val wasActive = synchronized(exitToHomeStateLock) {
            val active = exitToHomeBlockedPackageName.isNotBlank()
            exitToHomeBlockedPackageName = ""
            exitToHomeExpiresAtElapsedRealtime = 0L
            active
        }
        if (wasActive) {
            Log.d(TAG, "exit_to_home_state_cleared reason=$reason")
        }
    }

    // ===== 内部实现 =====

    private fun doShowLockScreen(context: Context, blockedPackageName: String) {
        if (isLocked) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val appContext = context.applicationContext

        currentBlockedPackageName = blockedPackageName
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
        // Do not force the IME to stay visible. On MIUI/HyperOS, a password editor hosted by an
        // application-overlay window can otherwise make the secure IME and the user's regular
        // IME repeatedly replace each other, causing focus churn and input-dispatch ANRs.
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        try {
            windowManager.addView(view, params)
            lockView = view
            isLocked = true
            Log.w(TAG, "lock_screen_shown package=$blockedPackageName")
        } catch (e: Exception) {
            currentBlockedPackageName = ""
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
            currentBlockedPackageName = ""
        }
    }

    private fun buildLockView(context: Context): View {
        val density = context.resources.displayMetrics.density

        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#E8222222"))
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ ->
                keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME
            }
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40, density), dp(60, density), dp(40, density), dp(60, density))
            // 拦截返回键
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ ->
                keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME
            }
        }
        scrollView.addView(
            rootLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

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

        val exitRestrictedSurfaceButton = Button(context).apply {
            text = "退出受限应用并返回桌面"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#EF6C00"))
            setPadding(dp(24, density), dp(14, density), dp(24, density), dp(14, density))
            setOnClickListener {
                val blockedPackageName = currentBlockedPackageName.ifBlank {
                    desiredBlockedPackageName
                }
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                }
                try {
                    beginExitToHome(blockedPackageName)
                    desiredLocked = false
                    desiredBlockedPackageName = ""
                    context.startActivity(homeIntent)
                    OverlayService.suppressForExitToHome()
                    doRemoveLockView(context)
                    Log.w(
                        TAG,
                        "exit_to_home_requested blockedPackage=$blockedPackageName"
                    )
                    Toast.makeText(
                        context,
                        "已退出受限内容；再次进入时会重新拦截",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    clearExitToHome(reason = "home_launch_failed")
                    Log.e(TAG, "exit_to_home_failed: ${e.message}", e)
                    Toast.makeText(context, "暂时无法返回桌面，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootLayout.addView(
            exitRestrictedSurfaceButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8, density)
            }
        )

        val exitRestrictedSurfaceHint = TextView(context).apply {
            text = "无需密码；再次进入受限应用或受限页面会重新拦截"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
        }
        rootLayout.addView(
            exitRestrictedSurfaceHint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20, density)
            }
        )

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
            // Keep EditorInfo as a plain numeric field so MIUI does not force its security IME
            // onto a TYPE_APPLICATION_OVERLAY token. Masking remains local to the EditText.
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            transformationMethod = PasswordTransformationMethod.getInstance()
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
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
                Log.d(TAG, "password_input_focus_changed hasFocus=$hasFocus")
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

        // 验证通过后才显示解锁方式，避免密码动作直接隐式选择某一种授权范围。
        val unlockButton = Button(context).apply {
            text = "验证密码并选择解锁方式"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
        }
        rootLayout.addView(unlockButton, marginParams(density, bottom = 16))

        val recoveryButton = Button(context).apply {
            text = "忘记密码 / 联系客服恢复"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#455A64"))
        }
        rootLayout.addView(recoveryButton, marginParams(density, bottom = 16))

        val choicePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val choiceHint = TextView(context).apply {
            text = "请选择本次家长授权范围"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
        }
        choicePanel.addView(
            choiceHint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12, density)
            }
        )

        val globalUnlockButton = Button(context).apply {
            text = "1. 全局解锁模式\n持续到家长手动关闭"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            setOnClickListener {
                SettingsManager.getInstance(context).apply {
                    setGlobalUnlock(true)
                    setGlobalLock(false)
                }
                clearParentTemporaryUnlock(reason = "global_unlock_selected")
                desiredLocked = false
                Log.w(TAG, "parent_unlock_mode_selected mode=global")
                OverlayService.suppressForGlobalUnlock()
                doRemoveLockView(context)
                Toast.makeText(
                    context,
                    "已进入全局解锁模式；请在家长配置中手动关闭",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        choicePanel.addView(
            globalUnlockButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12, density)
            }
        )

        val temporaryUnlockButton = Button(context).apply {
            text = "2. 临时解锁 5 分钟\n熄屏或恢复无障碍后提前结束"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#388E3C"))
            setOnClickListener {
                val now = SystemClock.elapsedRealtime()
                parentTemporaryUnlockUntilElapsedRealtime =
                    DegradedTemporaryUnlockPolicy.expiresAt(now)
                desiredLocked = false
                Log.w(
                    TAG,
                    "parent_unlock_mode_selected mode=temporary durationMs=" +
                        DegradedTemporaryUnlockPolicy.DURATION_MS
                )
                OverlayService.suppressForParentTemporaryUnlock()
                doRemoveLockView(context)
                Toast.makeText(
                    context,
                    "已临时解锁 5 分钟；熄屏或恢复无障碍后会提前结束",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        choicePanel.addView(
            temporaryUnlockButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(
            choicePanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20, density)
            }
        )

        val recoverySnapshot = RecoveryCodeManager.snapshot(context)
        val recoveryPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        recoveryPanel.addView(
            TextView(context).apply {
                text = "请把设备号和计算日期报给客服"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#BBBBBB"))
                gravity = Gravity.CENTER
            },
            marginParams(density, bottom = 8)
        )
        recoveryPanel.addView(
            TextView(context).apply {
                text = context.getString(
                    R.string.recovery_support_info,
                    recoverySnapshot.displayRecoveryId,
                    recoverySnapshot.recoveryDate
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            },
            marginParams(density, bottom = 12)
        )

        fun recoveryNumberInput(hintText: String, maxLength: Int): EditText =
            EditText(context).apply {
                hint = hintText
                setHintTextColor(Color.parseColor("#777777"))
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                transformationMethod = PasswordTransformationMethod.getInstance()
                filters = arrayOf(InputFilter.LengthFilter(maxLength))
                setSingleLine(true)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#333333"))
                setPadding(
                    dp(16, density),
                    dp(12, density),
                    dp(16, density),
                    dp(12, density)
                )
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener {
                    requestFocus()
                    showKeyboard(context, this)
                }
            }

        val recoveryCodeInput = recoveryNumberInput("客服提供的 8 位恢复码", 8)
        val recoveryNewPasswordInput = recoveryNumberInput("新家长密码（至少 6 位）", 32)
        val recoveryConfirmPasswordInput = recoveryNumberInput("再次输入新家长密码", 32)
        recoveryPanel.addView(
            recoveryCodeInput,
            marginParams(density, bottom = 10)
        )
        recoveryPanel.addView(
            recoveryNewPasswordInput,
            marginParams(density, bottom = 10)
        )
        recoveryPanel.addView(
            recoveryConfirmPasswordInput,
            marginParams(density, bottom = 12)
        )

        val submitRecoveryButton = Button(context).apply {
            text = "验证恢复码并重设密码"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
        }
        recoveryPanel.addView(
            submitRecoveryButton,
            marginParams(density, bottom = 8)
        )
        val cancelRecoveryButton = Button(context).apply {
            text = "返回密码验证"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
        }
        recoveryPanel.addView(cancelRecoveryButton)
        rootLayout.addView(
            recoveryPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20, density)
            }
        )

        recoveryButton.setOnClickListener {
            hideKeyboard(context, passwordInput)
            passwordInput.visibility = View.GONE
            unlockButton.visibility = View.GONE
            recoveryButton.visibility = View.GONE
            choicePanel.visibility = View.GONE
            passwordLabel.text = "联系客服恢复家长密码"
            recoveryPanel.visibility = View.VISIBLE
            recoveryCodeInput.requestFocus()
            showKeyboard(context, recoveryCodeInput)
            recoveryPanel.post {
                scrollView.smoothScrollTo(0, recoveryPanel.bottom)
            }
        }

        cancelRecoveryButton.setOnClickListener {
            hideKeyboard(context, recoveryCodeInput)
            recoveryPanel.visibility = View.GONE
            passwordLabel.text = "家长临时解锁"
            passwordInput.visibility = View.VISIBLE
            unlockButton.visibility = View.VISIBLE
            recoveryButton.visibility = View.VISIBLE
            passwordInput.requestFocus()
            showKeyboard(context, passwordInput)
        }

        submitRecoveryButton.setOnClickListener {
            val recoveryCode = recoveryCodeInput.text.toString()
            val newPassword = recoveryNewPasswordInput.text.toString()
            val confirmedPassword = recoveryConfirmPasswordInput.text.toString()
            when {
                recoveryCode.length != 8 -> {
                    Toast.makeText(context, "请输入完整的 8 位恢复码", Toast.LENGTH_SHORT).show()
                }
                newPassword.length < 6 -> {
                    Toast.makeText(context, "新密码至少需要 6 位数字", Toast.LENGTH_SHORT).show()
                }
                newPassword != confirmedPassword -> {
                    Toast.makeText(context, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    when (
                        val result = RecoveryCodeManager.verify(
                            context = context,
                            enteredCode = recoveryCode,
                            recoverySnapshot = recoverySnapshot
                        )
                    ) {
                        RecoveryVerificationResult.Success -> {
                            try {
                                PasswordManager.getInstance(context).setPassword(newPassword)
                                TrustedTimeProvider.clearTamperFlag(context)
                                Log.w(TAG, "parent_password_reset_by_support_code")
                                hideKeyboard(context, recoveryCodeInput)
                                recoveryPanel.visibility = View.GONE
                                recoveryButton.visibility = View.GONE
                                passwordLabel.text = "选择解锁方式"
                                choicePanel.visibility = View.VISIBLE
                                Toast.makeText(
                                    context,
                                    "家长密码已重设，请选择解锁方式",
                                    Toast.LENGTH_LONG
                                ).show()
                                choicePanel.post {
                                    scrollView.smoothScrollTo(0, choicePanel.bottom)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "parent_password_reset_failed", e)
                                Toast.makeText(
                                    context,
                                    "新密码保存失败，请重试",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        is RecoveryVerificationResult.RateLimited -> {
                            Toast.makeText(
                                context,
                                "尝试次数过多，请 ${result.retryAfterSeconds} 秒后再试",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is RecoveryVerificationResult.Rejected -> {
                            val message = if (result.retryAfterSeconds > 0L) {
                                "恢复码错误，请 ${result.retryAfterSeconds} 秒后再试"
                            } else {
                                "恢复码错误，还可尝试 ${result.remainingAttempts} 次"
                            }
                            recoveryCodeInput.setText("")
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        val submitParentPassword = submit@{
            val input = passwordInput.text.toString()
            val passwordManager = PasswordManager.getInstance(context)
            if (!passwordManager.hasPasswordConfigured()) {
                Log.w(TAG, "parent_password_submit_rejected reason=not_configured")
                Toast.makeText(
                    context,
                    "尚未设置家长密码，请先在主界面设置",
                    Toast.LENGTH_SHORT
                ).show()
                return@submit
            }
            if (passwordManager.verifyPassword(input)) {
                Log.w(TAG, "parent_password_verified showing_unlock_choices")
                hideKeyboard(context, passwordInput)
                passwordInput.visibility = View.GONE
                unlockButton.visibility = View.GONE
                recoveryButton.visibility = View.GONE
                recoveryPanel.visibility = View.GONE
                passwordLabel.text = "选择解锁方式"
                choicePanel.visibility = View.VISIBLE
                choicePanel.post {
                    scrollView.smoothScrollTo(0, choicePanel.bottom)
                }
            } else {
                Log.w(TAG, "parent_password_submit_rejected reason=verification_failed")
                passwordInput.setText("")
                passwordInput.requestFocus()
                showKeyboard(context, passwordInput)
                Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show()
            }
        }
        passwordInput.setOnEditorActionListener { _, actionId, event ->
            val submittedFromIme = actionId == EditorInfo.IME_ACTION_DONE
            val submittedFromEnter =
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_UP
            if (submittedFromIme || submittedFromEnter) {
                submitParentPassword()
                true
            } else {
                false
            }
        }

        unlockButton.setOnClickListener {
            submitParentPassword()
        }

        // 底部提示
        val footerText = TextView(context).apply {
            text = "如需帮助，请联系家长"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
        }
        rootLayout.addView(footerText)

        return scrollView
    }

    private fun beginExitToHome(blockedPackageName: String) {
        val normalizedPackageName = blockedPackageName.trim().substringBefore(':')
        synchronized(exitToHomeStateLock) {
            exitToHomeBlockedPackageName = normalizedPackageName.ifBlank { "unknown" }
            exitToHomeExpiresAtElapsedRealtime =
                SystemClock.elapsedRealtime() + DegradedExitToHomePolicy.MAX_TRANSITION_MS
        }
    }

    private fun showKeyboard(context: Context, input: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val accepted = imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        Log.d(
            TAG,
            "keyboard_show_requested accepted=$accepted tag=${input.tag} " +
                "hasFocus=${input.hasFocus()} inputType=${input.inputType}"
        )
    }

    private fun hideKeyboard(context: Context, input: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
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
