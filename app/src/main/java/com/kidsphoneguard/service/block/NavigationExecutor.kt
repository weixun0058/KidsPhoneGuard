package com.kidsphoneguard.service.block

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class NavigationExecutor(
    private val service: AccessibilityService,
    private val logTag: String
) {

    private var cachedScreenWidth: Int = 0
    private var cachedScreenHeight: Int = 0

    /**
     * 执行一个全局导航动作。
     * 输入：Android 全局动作常量；输出：动作是否被系统成功接收。
     */
    fun performGlobalAction(action: Int): Boolean {
        return try {
            val result = service.performGlobalAction(action)
            Log.w(logTag, "navigation_executor_global_action action=$action result=$result")
            result
        } catch (e: Exception) {
            Log.e(logTag, "navigation_executor_global_action_failed action=$action reason=${e.message}", e)
            false
        }
    }

    /**
     * 分发一个无障碍手势。
     * 输入：已构建的手势描述与可选结果回调；输出：手势请求是否被系统接受。
     * 注意：返回 true 只表示请求已入队，最终完成或取消必须以 callback 为准。
     */
    fun dispatchGesture(
        gesture: GestureDescription,
        callback: AccessibilityService.GestureResultCallback? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        return try {
            service.dispatchGesture(gesture, callback, null)
        } catch (e: Exception) {
            Log.e(logTag, "navigation_executor_dispatch_gesture_failed reason=${e.message}", e)
            false
        }
    }

    /**
     * 获取屏幕宽度。
     * 输入：无；输出：屏幕宽度像素值，失败时返回 0。
     */
    fun getPhysicalScreenWidth(): Int {
        ensurePhysicalScreenSize()
        return cachedScreenWidth
    }

    /**
     * 获取包含状态栏和导航栏在内的物理屏幕高度。
     * 输入：无；输出：物理屏幕高度像素值，失败时返回 0。
     */
    fun getPhysicalScreenHeight(): Int {
        ensurePhysicalScreenSize()
        return cachedScreenHeight
    }

    /**
     * 小窗系统标题栏使用物理屏幕坐标；resources.displayMetrics 可能只返回扣除系统栏后的应用区域。
     */
    private fun ensurePhysicalScreenSize() {
        if (cachedScreenWidth > 0 && cachedScreenHeight > 0) {
            return
        }
        try {
            val windowManager =
                service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.maximumWindowMetrics.bounds
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            }
            cachedScreenWidth = bounds.width()
            cachedScreenHeight = bounds.height()
            Log.d(
                logTag,
                "navigation_executor_physical_screen " +
                    "width=$cachedScreenWidth height=$cachedScreenHeight"
            )
        } catch (e: Exception) {
            Log.e(logTag, "navigation_executor_physical_screen_failed: ${e.message}", e)
            cachedScreenWidth = 0
            cachedScreenHeight = 0
        }
    }
}
