package com.kidsphoneguard.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kidsphoneguard.R

class OverlayService : Service() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        private val stateLock = Any()
        private var overlayView: View? = null
        private var windowManager: WindowManager? = null
        private var isShowing = false
        private var currentPackageName: String = ""
        private var showCount = 0

        fun showOverlay(context: Context, packageName: String, appName: String) {
            val currentShowCount = synchronized(stateLock) {
                currentPackageName = packageName
                showCount += 1
                showCount
            }
            Log.d("OverlayService", "请求显示覆盖层: $packageName, count=$currentShowCount")
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                action = "ACTION_SHOW_OVERLAY"
                putExtra("show_count", currentShowCount)
            }
            context.startService(intent)
        }

        fun hideOverlay(context: Context) {
            synchronized(stateLock) {
                currentPackageName = ""
            }
            Log.d("OverlayService", "请求隐藏覆盖层")
            val intent = Intent(context, OverlayService::class.java).apply {
                action = "ACTION_HIDE_OVERLAY"
            }
            context.startService(intent)
        }

        fun isOverlayShowing(): Boolean = synchronized(stateLock) {
            isShowing && overlayView?.isAttachedToWindow == true
        }

        fun getCurrentBlockedPackage(): String = synchronized(stateLock) {
            currentPackageName
        }
    }

    private val tag = "OverlayService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        synchronized(stateLock) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlayInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SHOW_OVERLAY" -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()
                showOverlayInternal(packageName, appName)
            }
            "ACTION_HIDE_OVERLAY" -> {
                hideOverlayInternal()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlayInternal(packageName: String, appName: String) {
        val shouldSkip = synchronized(stateLock) {
            isShowing && overlayView?.isAttachedToWindow == true && currentPackageName == packageName
        }
        if (shouldSkip) return

        hideOverlayInternal()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(tag, "悬浮窗权限未开启，无法显示拦截层")
            return
        }

        val wm = synchronized(stateLock) {
            windowManager
        } ?: return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#B3121212"))
            systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        val blocker = View(this).apply {
            setOnTouchListener { _, _ -> true }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = if (appName.isNotEmpty()) {
                "$appName\n${getString(R.string.overlay_message)}"
            } else {
                getString(R.string.overlay_message)
            }
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        content.addView(message)

        root.addView(
            blocker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(content, contentParams)

        try {
            wm.addView(root, layoutParams)
            synchronized(stateLock) {
                overlayView = root
                isShowing = true
                currentPackageName = packageName
            }
            Log.d(tag, "覆盖层已显示: $packageName")
        } catch (e: Exception) {
            Log.e(tag, "显示覆盖层失败: ${e.message}", e)
            synchronized(stateLock) {
                overlayView = null
                isShowing = false
                currentPackageName = ""
            }
        }
    }

    private fun hideOverlayInternal() {
        val (view, wm) = synchronized(stateLock) {
            val currentView = overlayView
            val currentWm = windowManager
            overlayView = null
            isShowing = false
            currentPackageName = ""
            Pair(currentView, currentWm)
        }
        if (view == null || wm == null) return
        try {
            wm.removeView(view)
            Log.d(tag, "覆盖层已隐藏")
        } catch (e: Exception) {
            Log.e(tag, "隐藏覆盖层失败: ${e.message}", e)
        }
    }
}
