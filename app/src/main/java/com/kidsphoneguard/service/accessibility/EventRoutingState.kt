package com.kidsphoneguard.service.accessibility

/**
 * 保存无障碍事件路由过程中需要共享的轻量状态。
 * 输入：路由步骤写入的包名与时间；输出：供 router 与补偿调度共用的去抖状态。
 */
data class EventRoutingState(
    var currentPackageName: String = "",
    var lastHandledPackage: String = "",
    var lastHandledTime: Long = 0L,
    var lastObservedPackage: String = ""
) {
    /**
     * 判断当前包名是否仍处于去抖窗口内。
     * 输入：包名、当前时间、去抖间隔；输出：`true` 表示应停止后续路由。
     */
    fun shouldDebounce(packageName: String, now: Long, debounceIntervalMs: Long): Boolean {
        return packageName == lastHandledPackage && (now - lastHandledTime) < debounceIntervalMs
    }

    /**
     * 记录最近一次通过去抖检查的包名与时间。
     * 输入：包名与当前时间；输出：无，内部状态被更新。
     */
    fun markHandled(packageName: String, now: Long) {
        lastHandledPackage = packageName
        lastHandledTime = now
    }

    /**
     * 更新当前正在进入常规策略检查的包名。
     * 输入：已归一化的目标包名；输出：无，内部状态被更新。
     */
    fun updateCurrentPackage(packageName: String) {
        currentPackageName = packageName
    }

    /**
     * 记录已经由窗口事件处理过的包名。
     * 输入：窗口事件归一化后的包名；输出：无，后续同包内容事件无需再走兜底策略。
     */
    fun markWindowPackageObserved(packageName: String) {
        lastObservedPackage = packageName
    }

    /**
     * 判断内容变化事件是否代表一个尚未由窗口事件处理过的新包名。
     * 输入：内容事件归一化后的包名；输出：`true` 时应执行一次普通策略兜底检查。
     */
    fun shouldRunContentPolicyFallback(packageName: String): Boolean {
        if (packageName == lastObservedPackage) {
            return false
        }
        lastObservedPackage = packageName
        return true
    }
}
