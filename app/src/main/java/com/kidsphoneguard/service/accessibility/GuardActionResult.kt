package com.kidsphoneguard.service.accessibility

/**
 * 表达路由步骤对当前无障碍事件的处理结果。
 * 输入：无；输出：是否继续路由以及是否已经产生副作用的统一协议。
 */
sealed interface GuardActionResult {
    val continueRouting: Boolean
    val hasSideEffect: Boolean

    data object Continue : GuardActionResult {
        override val continueRouting: Boolean = true
        override val hasSideEffect: Boolean = false
    }

    data class Consumed(
        val reason: String,
        override val hasSideEffect: Boolean
    ) : GuardActionResult {
        override val continueRouting: Boolean = false
    }

    data class ScheduleFollowUp(
        val reason: String
    ) : GuardActionResult {
        override val continueRouting: Boolean = false
        override val hasSideEffect: Boolean = true
    }

    data class Blocked(
        val packageName: String,
        val reason: String
    ) : GuardActionResult {
        override val continueRouting: Boolean = false
        override val hasSideEffect: Boolean = true
    }
}
