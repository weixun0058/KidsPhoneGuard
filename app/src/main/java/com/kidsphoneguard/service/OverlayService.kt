package com.kidsphoneguard.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kidsphoneguard.R

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

        fun isOverlayShowing(): Boolean = isShowing && overlayView?.isAttachedToWindow == true

        fun getCurrentBlockedPackage(): String = currentPackageName
    }

    private val tag = "OverlayService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlayInternal(packageName: String, appName: String) {
        if (isShowing && overlayView?.isAttachedToWindow == true) {
            if (currentPackageName == packageName) return
            hideOverlayInternal()
        }
        val wm = windowManager ?: return

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
            setBackgroundColor(Color.parseColor("#CCFF0000"))
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

        val homeButton = Button(this).apply {
            text = getString(R.string.overlay_button_home)
            setOnClickListener {
                hideOverlayInternal()
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(homeIntent)
            }
        }
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (16 * resources.displayMetrics.density).toInt()
            gravity = Gravity.CENTER
        }
        content.addView(homeButton, buttonParams)

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

        overlayView = root
        try {
            wm.addView(root, layoutParams)
            isShowing = true
        } catch (e: Exception) {
            Log.e(tag, "显示覆盖层失败: ${e.message}", e)
            overlayView = null
            isShowing = false
        }
    }

    private fun hideOverlayInternal() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            Log.e(tag, "隐藏覆盖层失败: ${e.message}", e)
        } finally {
            overlayView = null
            isShowing = false
        }
    }
}
