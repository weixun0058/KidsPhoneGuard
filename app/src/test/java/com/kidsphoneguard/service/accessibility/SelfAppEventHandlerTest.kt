package com.kidsphoneguard.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SelfAppEventHandlerTest {

    /**
     * 验证非 self-app 包名直接继续路由。
     * 输入：普通包名；输出：断言返回 `Continue`。
     */
    @Test
    fun nonSelfPackageReturnsContinue() {
        val handler = createHandler()

        val result = handler.handle("com.example.other")

        assertSame(GuardActionResult.Continue, result)
    }

    /**
     * 验证 protected overlay / pending 会被保留且不产生副作用。
     * 输入：self-app 包名与受保护 overlay 状态；输出：断言返回无副作用的 `Consumed`。
     */
    @Test
    fun protectedOverlayIsKept() {
        val handler = createHandler(
            isSelfApp = { true },
            isOverlayShowing = { true },
            readCurrentBlockedPackage = { "com.android.settings" },
            pendingBlockPackage = { "com.android.settings" },
            isProtectedSystemSurface = { packageName -> packageName == "com.android.settings" }
        )

        val result = handler.handle("com.kidsphoneguard")

        assertEquals(
            GuardActionResult.Consumed(reason = "self_app_event", hasSideEffect = false),
            result
        )
    }

    /**
     * 验证存在待执行 block 或非 protected overlay 时会执行清理副作用。
     * 输入：self-app 包名与普通 overlay/pending 状态；输出：断言清理回调被调用。
     */
    @Test
    fun pendingBlockAndOverlayAreCleared() {
        val operations = mutableListOf<String>()
        val handler = createHandler(
            isSelfApp = { true },
            isOverlayShowing = { true },
            readCurrentBlockedPackage = { "com.example.blocked" },
            pendingBlockPackage = { "com.example.pending" },
            cancelPendingBlockActions = { reason -> operations += "cancel:$reason" },
            clearLastBlockedPackage = { operations += "clear_last_blocked" },
            hideOverlay = { operations += "hide_overlay" }
        )

        val result = handler.handle("com.kidsphoneguard")

        assertEquals(
            GuardActionResult.Consumed(reason = "self_app_event", hasSideEffect = true),
            result
        )
        assertEquals(
            listOf(
                "cancel:self_app_event:com.kidsphoneguard",
                "clear_last_blocked",
                "hide_overlay"
            ),
            operations
        )
    }

    /**
     * 验证 self-app overlay 事件不会过早取消仍在前台的普通拦截目标。
     * 输入：self-app 包名与仍活跃的 pending block；输出：断言无副作用保留会话。
     */
    @Test
    fun activePendingBlockIsKept() {
        val operations = mutableListOf<String>()
        val handler = createHandler(
            isSelfApp = { true },
            isOverlayShowing = { true },
            readCurrentBlockedPackage = { "com.xiaomi.market" },
            pendingBlockPackage = { "com.xiaomi.market" },
            isTargetPackageActive = { packageName -> packageName == "com.xiaomi.market" },
            cancelPendingBlockActions = { reason -> operations += "cancel:$reason" },
            clearLastBlockedPackage = { operations += "clear_last_blocked" },
            hideOverlay = { operations += "hide_overlay" }
        )

        val result = handler.handle("com.kidsphoneguard")

        assertEquals(
            GuardActionResult.Consumed(reason = "self_app_event", hasSideEffect = false),
            result
        )
        assertEquals(emptyList<String>(), operations)
    }

    /**
     * 构建用于测试的 self-app event handler。
     * 输入：可覆盖的回调；输出：测试实例。
     */
    private fun createHandler(
        isSelfApp: (String) -> Boolean = { false },
        isOverlayShowing: () -> Boolean = { false },
        readCurrentBlockedPackage: () -> String = { "" },
        pendingBlockPackage: () -> String = { "" },
        isProtectedSystemSurface: (String) -> Boolean = { false },
        isTargetPackageActive: (String) -> Boolean = { false },
        cancelPendingBlockActions: (String) -> Unit = {},
        clearLastBlockedPackage: () -> Unit = {},
        hideOverlay: () -> Unit = {}
    ): SelfAppEventHandler {
        return SelfAppEventHandler(
            logTag = "SelfAppEventHandlerTest",
            isSelfApp = isSelfApp,
            isOverlayShowing = isOverlayShowing,
            readCurrentBlockedPackage = readCurrentBlockedPackage,
            pendingBlockPackage = pendingBlockPackage,
            isProtectedSystemSurface = isProtectedSystemSurface,
            isTargetPackageActive = isTargetPackageActive,
            cancelPendingBlockActions = cancelPendingBlockActions,
            clearLastBlockedPackage = clearLastBlockedPackage,
            hideOverlay = hideOverlay,
            logDebug = {}
        )
    }
}
