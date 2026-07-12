package com.kidsphoneguard.service

/**
 * 无障碍设置中的“已启用”不等于服务实际可用：部分 ROM 可能保留设置项，
 * 但没有重新绑定 AccessibilityService。此处把可操作性判定集中为纯逻辑，
 * 供权限页、前台守护和降级锁使用。
 */
object AccessibilityOperationalState {

    fun isOperational(
        enabledInSettings: Boolean,
        serviceRunning: Boolean,
        heartbeatAt: Long,
        now: Long,
        heartbeatTimeoutMs: Long
    ): Boolean {
        return enabledInSettings &&
            serviceRunning &&
            heartbeatAt > 0L &&
            now - heartbeatAt <= heartbeatTimeoutMs
    }
}
