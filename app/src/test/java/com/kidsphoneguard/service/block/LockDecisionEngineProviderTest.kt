package com.kidsphoneguard.service.block

import com.kidsphoneguard.engine.BlockDecision
import com.kidsphoneguard.engine.BlockReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockDecisionEngineProviderTest {

    /**
     * 验证 provider 仅在首次初始化时调用 loader，之后可直接读取 block decision。
     * 输入：计数型 loader 与固定决策；输出：断言 loader 次数与返回结果。
     */
    @Test
    fun ensureInitializedLoadsEngineOnce() = runBlocking {
        var loadCount = 0
        val decision = BlockDecision(
            shouldBlock = true,
            reason = BlockReason.APP_BLOCKED,
            appName = "示例应用"
        )
        val provider = LockDecisionEngineProvider(
            logTag = "LockDecisionEngineProviderTest",
            context = null,
            engineLoader = {
                loadCount += 1
                object : LockDecisionEngineProvider.EngineAccess {
                    override suspend fun getBlockDecision(packageName: String): BlockDecision {
                        return decision
                    }
                }
            },
            logDebug = {},
            logWarn = {},
            logError = { _, _ -> }
        )

        assertTrue(provider.ensureInitialized())
        assertTrue(provider.ensureInitialized())
        assertEquals(decision, provider.getBlockDecision("com.example.app"))
        assertEquals(1, loadCount)
    }

    /**
     * 验证前置初始化失败时会返回 false。
     * 输入：抛异常的 loader；输出：断言初始化失败。
     */
    @Test
    fun initializeReturnsFalseWhenLoaderFails() {
        val provider = LockDecisionEngineProvider(
            logTag = "LockDecisionEngineProviderTest",
            context = null,
            engineLoader = { throw IllegalStateException("boom") },
            logDebug = {},
            logWarn = {},
            logError = { _, _ -> }
        )

        assertFalse(provider.initialize())
    }
}
