package com.kidsphoneguard.service.block

import android.content.Context
import android.util.Log
import com.kidsphoneguard.service.OverlayService

/**
 * 封装 OverlayService 的技术调用，不持有任何 block session 策略状态。
 * 输入：Context 与日志标签；输出：安全的遮罩显示/隐藏技术动作。
 */
class OverlayCoordinator(
    private val context: Context,
    private val logTag: String
) {

    /**
     * 显示遮罩层。
     * 输入：目标包名与展示名称；输出：无。
     */
    fun showOverlay(packageName: String, appName: String) {
        try {
            OverlayService.showOverlay(context, packageName, appName)
        } catch (e: Exception) {
            Log.e(logTag, "overlay_coordinator_show_failed package=$packageName reason=${e.message}", e)
        }
    }

    /**
     * 隐藏遮罩层。
     * 输入：无；输出：无。
     */
    fun hideOverlay() {
        try {
            OverlayService.hideOverlay(context)
        } catch (e: Exception) {
            Log.e(logTag, "overlay_coordinator_hide_failed reason=${e.message}", e)
        }
    }
}
