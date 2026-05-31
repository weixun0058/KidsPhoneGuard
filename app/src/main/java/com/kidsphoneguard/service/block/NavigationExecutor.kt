package com.kidsphoneguard.service.block

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Build
import android.util.Log

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
     * 输入：已构建的手势描述；输出：手势是否被系统接受。
     */
    fun dispatchGesture(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        return try {
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(logTag, "navigation_executor_dispatch_gesture_failed reason=${e.message}", e)
            false
        }
    }

    /**
     * 获取屏幕宽度。
     * 输入：无；输出：屏幕宽度像素值，失败时返回 0。
     */
    fun getScreenWidth(): Int {
        if (cachedScreenWidth <= 0) {
            cachedScreenWidth = try {
                service.resources.displayMetrics.widthPixels
            } catch (e: Exception) {
                Log.e(logTag, "navigation_executor_screen_width_failed: ${e.message}", e)
                0
            }
        }
        return cachedScreenWidth
    }

    /**
     * 获取屏幕高度。
     * 输入：无；输出：屏幕高度像素值，失败时返回 0。
     */
    fun getScreenHeight(): Int {
        if (cachedScreenHeight <= 0) {
            cachedScreenHeight = try {
                service.resources.displayMetrics.heightPixels
            } catch (e: Exception) {
                Log.e(logTag, "navigation_executor_screen_height_failed: ${e.message}", e)
                0
            }
        }
        return cachedScreenHeight
    }
}
