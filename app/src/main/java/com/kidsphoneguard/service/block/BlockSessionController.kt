package com.kidsphoneguard.service.block

/**
 * 统一管理 block session 状态、遮罩生命周期策略入口与释放检查调度入口。
 * 输入：OverlayCoordinator、GuardActionScheduler 与 BlockSessionState；输出：只读查询与受控状态变更能力。
 */
class BlockSessionController(
    private val overlayCoordinator: OverlayCoordinator,
    private val scheduler: GuardActionScheduler,
    private val state: BlockSessionState = BlockSessionState()
) {

    /**
     * 读取最近一次被拦截的包名。
     * 输入：无；输出：最近一次被拦截的包名。
     */
    fun lastBlockedPackage(): String = state.lastBlockedPackage

    /**
     * 读取最近一次拦截时间戳。
     * 输入：无；输出：最近一次拦截时间戳。
     */
    fun lastBlockTime(): Long = state.lastBlockTime

    /**
     * 读取当前 hold 窗口结束时间。
     * 输入：无；输出：hold 截止时间戳。
     */
    fun blockHoldUntil(): Long = state.blockHoldUntil

    /**
     * 读取当前等待中的目标包名。
     * 输入：无；输出：待处理目标包名。
     */
    fun pendingBlockPackage(): String = state.pendingBlockPackage

    /**
     * 读取最近一次展示遮罩的包名。
     * 输入：无；输出：最近一次展示遮罩的包名。
     */
    fun lastOverlayPackage(): String = state.lastOverlayPackage

    /**
     * 读取最近一次展示遮罩的时间戳。
     * 输入：无；输出：最近一次展示遮罩时间。
     */
    fun lastOverlayShowTime(): Long = state.lastOverlayShowTime

    /**
     * 记录一次新的 block session。
     * 输入：目标包名、当前时间与 hold 时长；输出：无。
     */
    fun recordBlock(packageName: String, currentTime: Long, holdDurationMs: Long) {
        state.lastBlockedPackage = packageName
        state.lastBlockTime = currentTime
        state.blockHoldUntil = currentTime + holdDurationMs
        state.pendingBlockPackage = packageName
    }

    /**
     * 设置待处理目标包名。
     * 输入：目标包名；输出：无。
     */
    fun setPendingBlockPackage(packageName: String) {
        state.pendingBlockPackage = packageName
    }

    /**
     * 清空待处理目标包名。
     * 输入：无；输出：无。
     */
    fun clearPendingBlockPackage() {
        state.pendingBlockPackage = ""
    }

    /**
     * 判断指定包名是否仍处于遮罩重展示冷却期之外。
     * 输入：目标包名、当前时间与冷却时长；输出：是否应重新展示遮罩。
     */
    fun shouldReshowOverlay(packageName: String, currentTime: Long, cooldownMs: Long): Boolean {
        return !(state.lastOverlayPackage == packageName &&
            (currentTime - state.lastOverlayShowTime) < cooldownMs)
    }

    /**
     * 通过协调器显示遮罩，并更新遮罩展示状态。
     * 输入：目标包名、展示名称与展示时间；输出：无。
     */
    fun showOverlay(packageName: String, appName: String, shownAt: Long = System.currentTimeMillis()) {
        overlayCoordinator.showOverlay(packageName, appName)
        state.lastOverlayPackage = packageName
        state.lastOverlayShowTime = shownAt
    }

    /**
     * 通过协调器隐藏遮罩，但不额外重置 block session 业务状态。
     * 输入：无；输出：无。
     */
    fun hideOverlay() {
        overlayCoordinator.hideOverlay()
    }

    /**
     * 清空最近一次被拦截包名。
     * 输入：无；输出：无。
     */
    fun clearLastBlockedPackage() {
        state.lastBlockedPackage = ""
    }

    /**
     * 清空遮罩展示跟踪信息。
     * 输入：无；输出：无。
     */
    fun clearOverlayTracking() {
        state.lastOverlayPackage = ""
        state.lastOverlayShowTime = 0L
    }

    /**
     * 清空完整 block session 状态。
     * 输入：是否同时清空待处理目标；输出：无。
     */
    fun clearSession(clearPendingPackage: Boolean = true) {
        state.lastBlockedPackage = ""
        state.lastBlockTime = 0L
        state.blockHoldUntil = 0L
        state.lastOverlayPackage = ""
        state.lastOverlayShowTime = 0L
        if (clearPendingPackage) {
            state.pendingBlockPackage = ""
        }
    }

    /**
     * 安排一个与 overlay 生命周期相关的延迟检查。
     * 输入：owner、key、延迟与回调；输出：无。
     */
    fun scheduleReleaseCheck(owner: String, key: String, delayMs: Long, action: () -> Unit) {
        scheduler.schedule(owner, key, delayMs, action)
    }

    /**
     * 取消某个 owner 下的 overlay 生命周期检查。
     * 输入：owner 标识；输出：无。
     */
    fun cancelReleaseChecks(owner: String) {
        scheduler.cancelOwner(owner)
    }
}
