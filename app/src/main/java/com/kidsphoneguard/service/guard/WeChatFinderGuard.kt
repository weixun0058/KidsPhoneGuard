package com.kidsphoneguard.service.guard

import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.NavigationExecutor

data class WeChatForegroundActivity(
    val packageName: String,
    val className: String
)

/**
 * 视频号干预会话状态：同一时间仅允许一个延迟任务，任务完成后立即允许重新布防。
 * 不使用固定冷却时间，避免孩子快速重入时出现人为放行窗口。
 */
internal class WeChatFinderSessionState {
    private var nextSessionId = 0L
    private var activeSessionId = 0L

    fun tryArm(): Long? {
        if (activeSessionId != 0L) {
            return null
        }
        val sessionId = ++nextSessionId
        activeSessionId = sessionId
        return sessionId
    }

    fun complete(sessionId: Long): Boolean {
        if (activeSessionId != sessionId) {
            return false
        }
        activeSessionId = 0L
        return true
    }
}

/**
 * 微信视频号的软干预：不阻断微信，也不显示遮罩；确认进入 Finder 后，
 * 仅在持续停留一段时间时执行一次 BACK，降低连续刷视频的顺畅度。
 */
class WeChatFinderGuard(
    private val logTag: String,
    private val guardActionScheduler: GuardActionScheduler,
    private val navigationExecutor: NavigationExecutor,
    private val isWeChatFinderBlockEnabled: () -> Boolean,
    private val isGlobalUnlockEnabled: () -> Boolean,
    private val readRecentForegroundActivity: () -> WeChatForegroundActivity?,
    private val readActivePackageName: () -> String,
    private val publishLifecycleSignal: (String) -> Unit,
    private val backAction: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_FINDER_DWELL_MS = 2_000L
        private const val ACTIVITY_LOOKUP_COOLDOWN_MS = 500L
        private const val SCHEDULER_OWNER_WECHAT_FINDER = "wechat_finder"
        private const val SCHEDULER_KEY_SOFT_BACK = "soft_back"

        internal fun isFinderActivityClass(className: String): Boolean {
            return className.startsWith("com.tencent.mm.plugin.finder.") && className.endsWith("UI")
        }

    }

    private val sessionState = WeChatFinderSessionState()
    private var lastActivityLookupAt = 0L
    private var cachedRecentForegroundActivity: WeChatForegroundActivity? = null

    fun handle(event: AccessibilityEvent, packageName: String): GuardActionResult {
        val className = event.className?.toString().orEmpty()
        if (packageName != WECHAT_PACKAGE || !isWeChatFinderBlockEnabled() || isGlobalUnlockEnabled()) {
            return GuardActionResult.Continue
        }

        val finderActivityDetected = isFinderActivityClass(className) || isFinderForegroundByUsage()
        if (!finderActivityDetected) {
            return GuardActionResult.Continue
        }

        val source = "finder_activity:${className.ifEmpty { cachedRecentForegroundActivity?.className.orEmpty() }}"
        armFinderSession(source)
        return GuardActionResult.Continue
    }

    private fun isFinderForegroundByUsage(): Boolean {
        val now = nowProvider()
        if (now - lastActivityLookupAt >= ACTIVITY_LOOKUP_COOLDOWN_MS) {
            lastActivityLookupAt = now
            cachedRecentForegroundActivity = readRecentForegroundActivity()
        }
        val activePackageName = readActivePackageName()
        if (activePackageName.isNotEmpty() && activePackageName != WECHAT_PACKAGE) {
            return false
        }
        val activity = cachedRecentForegroundActivity ?: return false
        return activity.packageName == WECHAT_PACKAGE && isFinderActivityClass(activity.className)
    }

    private fun armFinderSession(className: String) {
        val sessionId = sessionState.tryArm() ?: return
        publishLifecycleSignal("wechat_finder_session_armed:$className")
        Log.d(logTag, "wechat_finder_session_armed class=$className dwellMs=$WECHAT_FINDER_DWELL_MS")
        guardActionScheduler.schedule(
            owner = SCHEDULER_OWNER_WECHAT_FINDER,
            key = SCHEDULER_KEY_SOFT_BACK,
            delayMs = WECHAT_FINDER_DWELL_MS
        ) {
            if (!sessionState.complete(sessionId)) {
                return@schedule
            }
            if (!isWeChatFinderBlockEnabled() || isGlobalUnlockEnabled()) {
                Log.d(logTag, "wechat_finder_soft_back_skip_disabled")
                return@schedule
            }
            if (!isFinderForegroundByUsage()) {
                Log.d(logTag, "wechat_finder_soft_back_skip_left_finder")
                return@schedule
            }
            publishLifecycleSignal("wechat_finder_soft_back:$className")
            Log.w(logTag, "wechat_finder_soft_back class=$className")
            navigationExecutor.performGlobalAction(backAction)
        }
    }

}
