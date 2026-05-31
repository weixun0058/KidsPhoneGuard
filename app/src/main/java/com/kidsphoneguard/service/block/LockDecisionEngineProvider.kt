package com.kidsphoneguard.service.block

import android.content.Context
import android.util.Log
import com.kidsphoneguard.engine.BlockDecision
import com.kidsphoneguard.engine.LockDecisionEngine

/**
 * 统一承接 LockDecisionEngine 的延迟初始化与查询入口。
 * 输入：Android context 与 engine loader；输出：初始化结果与 block decision 读取能力。
 */
class LockDecisionEngineProvider(
    private val logTag: String,
    private val context: Context?,
    private val engineLoader: (Context?) -> EngineAccess = { targetContext ->
        DefaultEngineAccess(LockDecisionEngine.getInstance(requireNotNull(targetContext)))
    },
    private val logDebug: (String) -> Unit = { message -> Log.d(logTag, message) },
    private val logWarn: (String) -> Unit = { message -> Log.w(logTag, message) },
    private val logError: (String, Throwable?) -> Unit = { message, throwable ->
        if (throwable != null) {
            Log.e(logTag, message, throwable)
        } else {
            Log.e(logTag, message)
        }
    }
) {

    /**
     * 对外暴露最小的锁决策读取能力，便于测试替身注入。
     * 输入：包名；输出：锁决策结果。
     */
    interface EngineAccess {
        suspend fun getBlockDecision(packageName: String): BlockDecision
    }

    /**
     * 将真实 `LockDecisionEngine` 适配为最小接口。
     * 输入：真实引擎实例；输出：统一的决策读取能力。
     */
    private class DefaultEngineAccess(
        private val engine: LockDecisionEngine
    ) : EngineAccess {
        override suspend fun getBlockDecision(packageName: String): BlockDecision {
            return engine.getBlockDecision(packageName)
        }
    }

    private var engineAccess: EngineAccess? = null

    /**
     * 在 service 初始化阶段执行一次前置初始化。
     * 输入：无；输出：初始化是否成功。
     */
    fun initialize(): Boolean {
        return try {
            if (engineAccess == null) {
                engineAccess = engineLoader(context)
            }
            logDebug("LockDecisionEngine 初始化成功")
            true
        } catch (e: Exception) {
            logError("LockDecisionEngine 初始化失败: ${e.message}", e)
            false
        }
    }

    /**
     * 在路由期间确保引擎已初始化。
     * 输入：无；输出：可用性检查结果。
     */
    fun ensureInitialized(): Boolean {
        if (engineAccess != null) {
            return true
        }
        return try {
            engineAccess = engineLoader(context)
            logWarn("LockDecisionEngine 延迟初始化成功")
            true
        } catch (e: Exception) {
            logError("LockDecisionEngine 延迟初始化失败: ${e.message}", e)
            false
        }
    }

    /**
     * 读取当前目标包名的 block decision。
     * 输入：包名；输出：引擎返回的 block decision。
     */
    suspend fun getBlockDecision(packageName: String): BlockDecision {
        return requireNotNull(engineAccess) {
            "LockDecisionEngineProvider accessed before initialization"
        }.getBlockDecision(packageName)
    }
}
