package com.kidsphoneguard.service.accessibility

import android.util.Log

/**
 * 统一承接助手覆盖场景的包名映射与补偿调度。
 * 输入：事件包名、router 共享状态与最小化回调；输出：归一化包名与 follow-up 路由结果。
 */
class AssistantOverlayRoutingSupport(
    private val logTag: String,
    private val debounceIntervalMs: Long,
    private val scheduleFollowUpAction: (key: String, delayMs: Long, action: () -> Unit) -> Unit,
    private val readActivePackageName: () -> String,
    private val getRecentTopPackageName: () -> String?,
    private val isSelfApp: (String) -> Boolean,
    private val isInWhitelist: (String) -> Boolean,
    private val launchPolicyCheck: (String) -> Unit,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val logDebug: (String) -> Unit = { message -> Log.d(logTag, message) }
) {

    companion object {
        private val ASSISTANT_PACKAGES = setOf(
            "com.huawei.gameassistant",
            "com.hihonor.gameassistant"
        )
        private val FOLLOW_UP_DELAYS_MS = longArrayOf(120L, 320L, 680L)
    }

    /**
     * 判断给定包名是否属于助手覆盖场景包。
     * 输入：包名；输出：是否命中助手包集合。
     */
    fun isAssistantPackage(packageName: String): Boolean {
        return packageName in ASSISTANT_PACKAGES
    }

    /**
     * 将助手事件包名映射为更可信的活动包名。
     * 输入：事件包名；输出：原包名或映射后的策略包名。
     */
    fun resolvePolicyPackage(eventPackageName: String): String {
        if (!isAssistantPackage(eventPackageName)) {
            return eventPackageName
        }

        val activePackageName = readActivePackageName()
        val fallbackPackageName = getRecentTopPackageName().orEmpty()
        val candidatePackageName = if (activePackageName.isNotEmpty()) {
            activePackageName
        } else {
            fallbackPackageName
        }

        if (candidatePackageName.isNotEmpty() &&
            candidatePackageName != eventPackageName &&
            !isSelfApp(candidatePackageName)
        ) {
            logDebug("事件包名 $eventPackageName 映射为活动窗口包名 $candidatePackageName")
            return candidatePackageName
        }

        return eventPackageName
    }

    /**
     * 为助手覆盖场景安排补偿检测。
     * 输入：router 共享状态；输出：统一的 follow-up 路由结果。
     */
    fun scheduleFollowUpChecks(routingState: EventRoutingState): GuardActionResult {
        FOLLOW_UP_DELAYS_MS.forEach { delayMillis ->
            scheduleFollowUpAction("delay_$delayMillis", delayMillis) {
                val candidatePackage = resolveCandidatePackage()
                if (candidatePackage.isEmpty() ||
                    isAssistantPackage(candidatePackage) ||
                    isSelfApp(candidatePackage) ||
                    isInWhitelist(candidatePackage)
                ) {
                    return@scheduleFollowUpAction
                }

                val now = nowProvider()
                if (routingState.shouldDebounce(candidatePackage, now, debounceIntervalMs)) {
                    return@scheduleFollowUpAction
                }

                routingState.markHandled(candidatePackage, now)
                logDebug("助手覆盖场景补偿检测: $candidatePackage")
                launchPolicyCheck(candidatePackage)
            }
        }
        return GuardActionResult.ScheduleFollowUp(reason = "assistant_follow_up")
    }

    /**
     * 汇总一次助手补偿检测的候选包名。
     * 输入：无；输出：活动包名优先、最近顶部包名兜底的候选包名。
     */
    private fun resolveCandidatePackage(): String {
        val activePackageName = readActivePackageName()
        if (activePackageName.isNotEmpty()) {
            return activePackageName
        }
        return getRecentTopPackageName().orEmpty()
    }
}
