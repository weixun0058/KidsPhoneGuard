package com.kidsphoneguard.service.accessibility

import android.util.Log

/**
 * 统一承接 self-app 事件对遮蔽层与待执行动作的收口。
 * 输入：归一化包名与最小化 service 回调；输出：统一的 self-app 路由结果。
 */
class SelfAppEventHandler(
    private val logTag: String,
    private val isSelfApp: (String) -> Boolean,
    private val isOverlayShowing: () -> Boolean,
    private val readCurrentBlockedPackage: () -> String,
    private val pendingBlockPackage: () -> String,
    private val isProtectedSystemSurface: (String) -> Boolean,
    private val cancelPendingBlockActions: (String) -> Unit,
    private val clearLastBlockedPackage: () -> Unit,
    private val hideOverlay: () -> Unit,
    private val logDebug: (String) -> Unit = { message -> Log.d(logTag, message) }
) {

    /**
     * 处理一次 self-app 事件。
     * 输入：归一化后的包名；输出：命中 self-app 时消费路由，否则继续。
     */
    fun handle(packageName: String): GuardActionResult {
        if (!isSelfApp(packageName)) {
            return GuardActionResult.Continue
        }

        val overlayBlockedPackage = readCurrentBlockedPackage()
        val pendingPackage = pendingBlockPackage()
        val keepProtectedOverlay = isOverlayShowing() &&
            isProtectedSystemSurface(overlayBlockedPackage)
        val keepProtectedPending = isProtectedSystemSurface(pendingPackage)
        if (keepProtectedOverlay || keepProtectedPending) {
            logDebug(
                "self_app_event_keep_protected_overlay blocked=$overlayBlockedPackage pending=$pendingPackage"
            )
            return GuardActionResult.Consumed(reason = "self_app_event", hasSideEffect = false)
        }

        val hadPendingBlock = pendingPackage.isNotEmpty()
        val hadOverlay = isOverlayShowing()
        if (hadPendingBlock) {
            cancelPendingBlockActions("self_app_event:$packageName")
        }
        if (hadOverlay) {
            clearLastBlockedPackage()
            hideOverlay()
        }

        return GuardActionResult.Consumed(
            reason = "self_app_event",
            hasSideEffect = hadPendingBlock || hadOverlay
        )
    }
}
