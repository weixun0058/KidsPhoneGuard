package com.kidsphoneguard.service.guard

import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.kidsphoneguard.service.accessibility.GuardActionResult
import com.kidsphoneguard.service.block.BlockSessionController
import com.kidsphoneguard.service.block.GuardActionScheduler
import com.kidsphoneguard.service.block.NavigationExecutor

/**
 * 负责微信视频号特例路由检测、冷却控制、遮蔽层展示与自动释放。
 * 输入：微信事件、设置回调、block session 与调度能力；输出：统一的 `GuardActionResult` 与视频号压制动作。
 */
class WeChatFinderGuard(
    private val logTag: String,
    private val blockSessionController: BlockSessionController,
    private val guardActionScheduler: GuardActionScheduler,
    private val navigationExecutor: NavigationExecutor,
    private val isWeChatFinderBlockEnabled: () -> Boolean,
    private val isGlobalUnlockEnabled: () -> Boolean,
    private val cancelPendingBlockActions: (String) -> Unit,
    private val hideOverlay: () -> Unit,
    private val readCurrentBlockedPackage: () -> String,
    private val postToMain: ((() -> Unit) -> Unit),
    private val publishLifecycleSignal: (String) -> Unit,
    private val blockHoldDurationMs: Long,
    private val backAction: Int,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_FINDER_SURFACE = "com.tencent.mm:finder"
        private const val WECHAT_FINDER_APP_NAME = "微信视频号"
        private const val WECHAT_FINDER_COOLDOWN_MS = 1200L
        private const val WECHAT_FINDER_AUTO_RELEASE_DELAY_MS = 1500L
        private const val SCHEDULER_OWNER_WECHAT_FINDER = "wechat_finder"

        /**
         * 判断当前包名/类名是否命中微信视频号现有识别规则。
         * 输入：包名、类名、功能开关与全局解锁状态；输出：是否应按当前规则拦截。
         */
        internal fun shouldBlockCurrentFinderShape(
            packageName: String,
            className: String,
            blockEnabled: Boolean,
            globalUnlockEnabled: Boolean
        ): Boolean {
            if (packageName != WECHAT_PACKAGE || !blockEnabled || globalUnlockEnabled) {
                return false
            }
            return className.startsWith("com.tencent.mm.plugin.finder.") && className.endsWith("UI")
        }

        /**
         * 计算当前包名/类名组合在 router 中应返回的结果语义。
         * 输入：包名、类名、功能开关与全局解锁状态；输出：命中时返回视频号消费结果，否则继续。
         */
        internal fun resultForCurrentShape(
            packageName: String,
            className: String,
            blockEnabled: Boolean,
            globalUnlockEnabled: Boolean
        ): GuardActionResult {
            return if (shouldBlockCurrentFinderShape(packageName, className, blockEnabled, globalUnlockEnabled)) {
                GuardActionResult.Consumed(reason = "wechat_finder", hasSideEffect = true)
            } else {
                GuardActionResult.Continue
            }
        }
    }

    /**
     * 处理单次微信相关事件并返回 router 可消费结果。
     * 输入：原始事件与已归一化包名；输出：命中视频号时消费路由，否则继续。
     */
    fun handle(event: AccessibilityEvent, packageName: String): GuardActionResult {
        val className = event.className?.toString().orEmpty()
        return handle(packageName, className)
    }

    /**
     * 处理已提取包名/类名的微信视频号判断，便于在 JVM 测试中验证结果语义。
     * 输入：包名与类名；输出：命中时返回 `Consumed("wechat_finder")`，否则继续。
     */
    internal fun handle(packageName: String, className: String): GuardActionResult {
        val result = resultForCurrentShape(
            packageName = packageName,
            className = className,
            blockEnabled = isWeChatFinderBlockEnabled(),
            globalUnlockEnabled = isGlobalUnlockEnabled()
        )
        if (result == GuardActionResult.Continue) {
            return GuardActionResult.Continue
        }
        blockWeChatFinder(className)
        return result
    }

    /**
     * 执行一次微信视频号压制，保留现有冷却、遮蔽层和自动释放行为。
     * 输入：当前事件类名；输出：无，副作用通过 block session 与调度完成。
     */
    private fun blockWeChatFinder(className: String) {
        val currentTime = nowProvider()
        if (blockSessionController.lastBlockedPackage() == WECHAT_FINDER_SURFACE &&
            (currentTime - blockSessionController.lastBlockTime()) < WECHAT_FINDER_COOLDOWN_MS
        ) {
            Log.d(logTag, "wechat_finder_block_skip_cooldown class=$className")
            return
        }

        cancelPendingBlockActions("wechat_finder:$className")
        blockSessionController.recordBlock(WECHAT_FINDER_SURFACE, currentTime, blockHoldDurationMs)
        blockSessionController.setPendingBlockPackage(WECHAT_PACKAGE)
        publishLifecycleSignal("wechat_finder_block:$className")
        Log.w(logTag, "wechat_finder_block class=$className")

        postToMain {
            blockSessionController.showOverlay(
                packageName = WECHAT_FINDER_SURFACE,
                appName = WECHAT_FINDER_APP_NAME,
                shownAt = nowProvider()
            )
        }

        navigationExecutor.performGlobalAction(backAction)

        guardActionScheduler.schedule(
            owner = SCHEDULER_OWNER_WECHAT_FINDER,
            key = WECHAT_PACKAGE,
            delayMs = WECHAT_FINDER_AUTO_RELEASE_DELAY_MS
        ) {
            if (readCurrentBlockedPackage() == WECHAT_FINDER_SURFACE) {
                Log.d(logTag, "wechat_finder_overlay_auto_release")
                hideOverlay()
            }
            if (blockSessionController.pendingBlockPackage() == WECHAT_PACKAGE) {
                blockSessionController.clearPendingBlockPackage()
            }
        }
    }
}
