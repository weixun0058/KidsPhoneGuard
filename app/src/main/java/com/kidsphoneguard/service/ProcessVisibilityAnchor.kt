package com.kidsphoneguard.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 亮屏期间保留一个不可交互的极小应用悬浮窗，使系统将守护进程视为带可见悬浮窗的进程。
 * 它不承担拦截 UI，也不接收触摸；息屏时由调用方移除。
 */
internal class ProcessVisibilityAnchor(
    context: Context,
    private val reportState: (String) -> Unit
) {
    companion object {
        private const val WINDOW_TITLE = "KidsPhoneGuardProcessAnchor"
        private const val WINDOW_ALPHA = 0.01f
    }

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var anchorView: View? = null
    private var lastStateDigest = ""

    fun update(shouldShow: Boolean, source: String) {
        if (shouldShow) {
            show(source)
        } else {
            hide(source)
        }
    }

    fun isShowing(): Boolean = anchorView?.isAttachedToWindow == true

    private fun show(source: String) {
        if (isShowing()) {
            return
        }
        anchorView = null
        if (!Settings.canDrawOverlays(appContext)) {
            report("state=unavailable|source=$source|reason=overlay_permission_missing")
            return
        }

        val view = View(appContext).apply {
            setBackgroundColor(Color.BLACK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = WINDOW_ALPHA
            title = WINDOW_TITLE
        }

        try {
            windowManager.addView(view, params)
            anchorView = view
            report("state=shown|source=$source")
        } catch (e: Exception) {
            anchorView = null
            report("state=failed|source=$source|reason=${e.message.orEmpty()}")
        }
    }

    private fun hide(source: String) {
        val view = anchorView ?: return
        anchorView = null
        try {
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
            report("state=hidden|source=$source")
        } catch (e: Exception) {
            report("state=remove_failed|source=$source|reason=${e.message.orEmpty()}")
        }
    }

    private fun report(digest: String) {
        if (digest == lastStateDigest) {
            return
        }
        lastStateDigest = digest
        reportState(digest)
    }
}
