package com.kidsphoneguard.service.guard

import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.service.accessibility.GuardActionResult

/**
 * 保留空实现以维持现有事件路由结构稳定。
 */
class SensitiveActionGuard {

    @Suppress("UNUSED_PARAMETER")
    fun handle(event: AccessibilityEvent, packageName: String): GuardActionResult {
        return GuardActionResult.Continue
    }

    fun cancelPendingActions() {
        // No actions are scheduled by this guard.
    }
}
