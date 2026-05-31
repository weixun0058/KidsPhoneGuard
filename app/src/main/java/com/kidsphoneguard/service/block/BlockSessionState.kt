package com.kidsphoneguard.service.block

/**
 * 持有共享的 block/overlay session 状态。
 * 输入：无；输出：供 BlockSessionController 独占管理的状态容器。
 */
data class BlockSessionState(
    var lastBlockedPackage: String = "",
    var lastBlockTime: Long = 0L,
    var blockHoldUntil: Long = 0L,
    var pendingBlockPackage: String = "",
    var lastOverlayPackage: String = "",
    var lastOverlayShowTime: Long = 0L
)
