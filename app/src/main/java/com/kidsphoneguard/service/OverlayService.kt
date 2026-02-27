package com.kidsphoneguard.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.kidsphoneguard.R

/**
 * 悬浮窗服务
 * 用于显示拦截覆盖层，阻止儿童继续使用受限应用
 * HarmonyOS终极版：防止全面屏手势绕过
 */
class OverlayService : Service() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        private var overlayView: View? = null
        private var windowManager: WindowManager? = null
        private var isShowing = false
        private var currentPackageName: String = ""
        private var showCount = 0

        fun showOverlay(context: Context, packageName: String, appName: String) {
            currentPackageName = packageName
            showCount++
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                action = "ACTION_SHOW_OVERLAY"
                putExtra("show_count", showCount)
            }
            context.startService(intent)
        }

        fun hideOverlay(context: Context) {
            currentPackageName = ""
            val intent = Intent(context, OverlayService::class.java).apply {
                action = "ACTION_HIDE_OVERLAY"
            }
            context.startService(intent)
        }

        fun isOverlayShowing(): Boolean = isShowing && overlayView != null && overlayView!!.isAttachedToWindow

        fun getCurrentBlockedPackage(): String = currentPackageName
    }

    private lateinit var homeBroadcastReceiver: BroadcastReceiver
    private val handler = Handler(Looper.getMainLooper())
    private var lastShowCount = 0

    // 超激进的保活机制
    private val aggressiveKeepAlive = object : Runnable {
        override fun run() {
            if (currentPackageName.isNotEmpty()) {
                if (!isOverlayShowing()) {
                    showOverlay(currentPackageName, "")
                }
            }
            handler.postDelayed(this, 50)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        homeBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "ACTION_GO_HOME") {
                    hideOverlay()
                    currentPackageName = ""
                }
            }
        }
        registerReceiver(homeBroadcastReceiver, IntentFilter("ACTION_GO_HOME"))
        handler.post(aggressiveKeepAlive)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(homeBroadcastReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        handler.removeCallbacks(aggressiveKeepAlive)
        hideOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_HIDE_OVERLAY" -> {
                hideOverlay()
                currentPackageName = ""
            }
            "ACTION_SHOW_OVERLAY" -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                val count = intent.getIntExtra("show_count", 0)

                if (count > lastShowCount || packageName == currentPackageName) {
                    lastShowCount = count
                    showOverlay(packageName, appName)
                }
            }
        }
        return START_STICKY
    }

    private fun showOverlay(packageName: String, appName: String) {
        if (isShowing && overlayView != null && overlayView!!.isAttachedToWindow) {
            return
        }

        forceHideOverlay()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            // 终极flags组合 - 防止所有手势
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_block, null)

            val messageText = overlayView?.findViewById<TextView>(R.id.overlay_message)
            messageText?.text = getString(R.string.overlay_message)

            val homeButton = overlayView?.findViewById<Button>(R.id.btn_home)
            homeButton?.setOnClickListener {
                sendBroadcast(Intent("ACTION_GO_HOME"))
            }

            // 拦截所有触摸事件 - 包括手势
            overlayView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP -> {
                        // 消费所有触摸事件
                        true
                    }
                    else -> true
                }
            }

            // 拦截按键事件
            overlayView?.setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_HOME,
                    KeyEvent.KEYCODE_APP_SWITCH -> true
                    else -> false
                }
            }

            // 设置点击监听防止穿透
            overlayView?.setOnClickListener { }

            overlayView?.isFocusableInTouchMode = true
            overlayView?.requestFocus()

            windowManager?.addView(overlayView, layoutParams)
            isShowing = true
            currentPackageName = packageName

            handler.postDelayed({
                if (overlayView == null || !overlayView!!.isAttachedToWindow) {
                    showOverlay(packageName, appName)
                }
            }, 100)

        } catch (e: Exception) {
            e.printStackTrace()
            handler.postDelayed({
                if (currentPackageName == packageName) {
                    showOverlay(packageName, appName)
                }
            }, 100)
        }
    }

    private fun forceHideOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeViewImmediate(overlayView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            windowManager = null
            isShowing = false
        }
    }

    private fun hideOverlay() {
        forceHideOverlay()
    }
}
