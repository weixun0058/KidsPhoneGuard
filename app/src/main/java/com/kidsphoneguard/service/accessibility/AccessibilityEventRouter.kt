package com.kidsphoneguard.service.accessibility

import android.view.accessibility.AccessibilityEvent
import com.kidsphoneguard.service.guard.SensitiveActionGuard

/**
 * 显式编码无障碍事件类型分发与两条主路由顺序。
 * 输入：Android 无障碍事件与 service 适配器；输出：统一的 `GuardActionResult` 路由结果。
 */
class AccessibilityEventRouter(
    private val logTag: String,
    private val sensitiveActionGuard: SensitiveActionGuard,
    private val adapters: Adapters,
    private val state: EventRoutingState = EventRoutingState()
) {
    /**
     * 汇总单次事件路由所需的归一化上下文。
     * 输入：事件与路由归一化结果；输出：供后续 router 步骤读取的不可变上下文。
     */
    data class RouteContext(
        val event: AccessibilityEvent,
        val eventPackageName: String,
        val source: String,
        val resolvedEventPackage: String,
        val protectedWindowPackage: String?,
        val packageName: String
    )

    /**
     * 暴露 service 现有业务能力给 router 调用。
     * 输入：路由上下文参数；输出：各分支统一适配后的 `GuardActionResult`。
     */
    data class Adapters(
        val resolvePolicyPackage: (String) -> String,
        val shouldSweepProtectedWindows: (AccessibilityEvent, String) -> Boolean,
        val findProtectedInteractiveWindowPackage: (String) -> String?,
        val isAssistantPackage: (String) -> Boolean,
        val scheduleAssistantFollowUpChecks: (EventRoutingState) -> GuardActionResult,
        val exitPowerSaveModeIfNeeded: (AccessibilityEvent, String) -> GuardActionResult,
        val collapseSystemPanelIfNeeded: (AccessibilityEvent, String, String) -> GuardActionResult,
        val handleProtectedSettingsPolicyIfCandidate: (AccessibilityEvent, String, String) -> GuardActionResult,
        val handleSelfAppWindowEvent: (String) -> GuardActionResult,
        val handleWeChatFinder: (AccessibilityEvent, String) -> GuardActionResult,
        val ensureLockDecisionEngineInitialized: () -> GuardActionResult,
        val handleBlockHold: (String, Long) -> GuardActionResult,
        val debounceIntervalMs: Long,
        val handleWhitelistWindowEvent: (String, Long) -> GuardActionResult,
        val launchNormalPolicyCheck: (String) -> GuardActionResult
    )

    /**
     * 对外统一处理单个无障碍事件。
     * 输入：原始 `AccessibilityEvent`；输出：当前事件在 router 中的最终处理结果。
     */
    fun route(event: AccessibilityEvent): GuardActionResult {
        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> routeWindowEvent(event)

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> routeInteractionEvent(event)

            else -> GuardActionResult.Continue
        }
    }

    /**
     * 按既定顺序处理 window family 事件。
     * 输入：window 类无障碍事件；输出：window 路由链的最终处理结果。
     */
    private fun routeWindowEvent(event: AccessibilityEvent): GuardActionResult {
        val context = buildRouteContext(event, "window_event") ?: return GuardActionResult.Continue
        if (adapters.isAssistantPackage(context.eventPackageName) && context.protectedWindowPackage == null) {
            return adapters.scheduleAssistantFollowUpChecks(state)
        }

        val currentTime = System.currentTimeMillis()
        return runRouteSteps(
            { adapters.exitPowerSaveModeIfNeeded(context.event, context.source) },
            { adapters.collapseSystemPanelIfNeeded(context.event, context.packageName, context.source) },
            { sensitiveActionGuard.handle(context.event, context.packageName) },
            { adapters.handleProtectedSettingsPolicyIfCandidate(context.event, context.packageName, context.source) },
            { adapters.handleSelfAppWindowEvent(context.packageName) },
            { adapters.handleWeChatFinder(context.event, context.packageName) },
            { adapters.ensureLockDecisionEngineInitialized() },
            { adapters.handleBlockHold(context.packageName, currentTime) },
            { handleDebounce(context.packageName, currentTime) },
            { adapters.handleWhitelistWindowEvent(context.packageName, currentTime) },
            {
                state.updateCurrentPackage(context.packageName)
                adapters.launchNormalPolicyCheck(context.packageName)
            }
        )
    }

    /**
     * 按既定顺序处理 interaction family 事件。
     * 输入：交互类无障碍事件；输出：interaction 路由链的最终处理结果。
     */
    private fun routeInteractionEvent(event: AccessibilityEvent): GuardActionResult {
        val context = buildRouteContext(event, "interactive_event") ?: return GuardActionResult.Continue
        return runRouteSteps(
            { adapters.collapseSystemPanelIfNeeded(context.event, context.packageName, context.source) },
            { sensitiveActionGuard.handle(context.event, context.packageName) },
            { adapters.handleProtectedSettingsPolicyIfCandidate(context.event, context.packageName, context.source) }
        )
    }

    /**
     * 构建当前事件的归一化路由上下文。
     * 输入：原始事件与日志前缀；输出：完整上下文，若包名为空则返回 `null`。
     */
    private fun buildRouteContext(event: AccessibilityEvent, sourcePrefix: String): RouteContext? {
        val eventPackageName = event.packageName?.toString().orEmpty()
        if (eventPackageName.isEmpty()) {
            return null
        }

        val source = "$sourcePrefix:${event.eventType}:$eventPackageName"
        val resolvedEventPackage = adapters.resolvePolicyPackage(eventPackageName)
        val protectedWindowPackage = if (adapters.shouldSweepProtectedWindows(event, resolvedEventPackage)) {
            adapters.findProtectedInteractiveWindowPackage(source)
        } else {
            null
        }

        return RouteContext(
            event = event,
            eventPackageName = eventPackageName,
            source = source,
            resolvedEventPackage = resolvedEventPackage,
            protectedWindowPackage = protectedWindowPackage,
            packageName = protectedWindowPackage ?: resolvedEventPackage
        )
    }

    /**
     * 在 router 内执行 window 路由自己的去抖逻辑。
     * 输入：包名与当前时间；输出：是否因去抖而消费当前路由。
     */
    private fun handleDebounce(packageName: String, currentTime: Long): GuardActionResult {
        if (state.shouldDebounce(packageName, currentTime, adapters.debounceIntervalMs)) {
            return GuardActionResult.Consumed(
                reason = "debounce",
                hasSideEffect = false
            )
        }
        state.markHandled(packageName, currentTime)
        return GuardActionResult.Continue
    }

    companion object {
        /**
         * 顺序执行一组路由步骤，直到某一步停止路由。
         * 输入：按顺序执行的步骤列表；输出：第一个停止路由的结果，或 `Continue`。
         */
        internal fun runRouteSteps(vararg steps: () -> GuardActionResult): GuardActionResult {
            steps.forEach { step ->
                val result = step()
                if (!result.continueRouting) {
                    return result
                }
            }
            return GuardActionResult.Continue
        }
    }
}
