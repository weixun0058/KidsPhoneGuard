package com.kidsphoneguard.service.guard

/**
 * 保存 protected surface 相关的节流、签名与最近处理状态。
 * 输入：签名、包名、时间戳与冷却时间；输出：是否允许当前 protected surface 分支继续执行。
 */
data class ProtectedSurfaceState(
    var lastProtectedWindowLogTime: Long = 0L,
    var lastProtectedWindowSignature: String = "",
    var lastProtectedSettingsDecisionLogTime: Long = 0L,
    var lastProtectedSettingsDecisionSignature: String = "",
    var lastProtectedWindowSweepPackage: String = "",
    var lastProtectedWindowSweepTime: Long = 0L,
    var lastProtectedSurfaceSuppressPackage: String = "",
    var lastProtectedSurfaceSuppressTime: Long = 0L
) {
    /**
     * 判断当前 protected window 快照是否应继续输出日志。
     * 输入：新的签名、当前时间与日志冷却时间；输出：`true` 表示应记录并更新状态。
     */
    fun shouldLogProtectedWindow(signature: String, now: Long, cooldownMs: Long): Boolean {
        if (signature == lastProtectedWindowSignature &&
            (now - lastProtectedWindowLogTime) < cooldownMs
        ) {
            return false
        }
        lastProtectedWindowSignature = signature
        lastProtectedWindowLogTime = now
        return true
    }

    /**
     * 判断当前 protected settings 决策是否应继续输出日志。
     * 输入：新的签名、当前时间与日志冷却时间；输出：`true` 表示应记录并更新状态。
     */
    fun shouldLogProtectedSettingsDecision(signature: String, now: Long, cooldownMs: Long): Boolean {
        if (signature == lastProtectedSettingsDecisionSignature &&
            (now - lastProtectedSettingsDecisionLogTime) < cooldownMs
        ) {
            return false
        }
        lastProtectedSettingsDecisionSignature = signature
        lastProtectedSettingsDecisionLogTime = now
        return true
    }

    /**
     * 判断 protected window sweep 是否应继续处理当前包名。
     * 输入：包名、当前时间与 sweep 冷却时间；输出：`true` 表示应更新状态并继续处理。
     */
    fun shouldProcessProtectedWindowSweep(packageName: String, now: Long, cooldownMs: Long): Boolean {
        if (packageName == lastProtectedWindowSweepPackage &&
            (now - lastProtectedWindowSweepTime) < cooldownMs
        ) {
            return false
        }
        lastProtectedWindowSweepPackage = packageName
        lastProtectedWindowSweepTime = now
        return true
    }

    /**
     * 判断 protected surface suppression 是否应继续执行。
     * 输入：包名、当前时间与 suppression 冷却时间；输出：`true` 表示应更新状态并继续压制。
     */
    fun shouldSuppressProtectedSurface(packageName: String, now: Long, cooldownMs: Long): Boolean {
        if (packageName == lastProtectedSurfaceSuppressPackage &&
            (now - lastProtectedSurfaceSuppressTime) < cooldownMs
        ) {
            return false
        }
        lastProtectedSurfaceSuppressPackage = packageName
        lastProtectedSurfaceSuppressTime = now
        return true
    }
}
