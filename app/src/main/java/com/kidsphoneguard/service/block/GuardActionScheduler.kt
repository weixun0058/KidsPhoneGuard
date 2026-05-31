package com.kidsphoneguard.service.block

import android.os.Handler

class GuardActionScheduler(
    private val handler: Handler
) {
    private class ScheduledAction(
        val owner: String,
        val key: String,
        val runnable: Runnable
    )

    private val actions = mutableListOf<ScheduledAction>()

    /**
     * 安排一个延迟动作，并记录所属 owner 与 key 以便后续取消。
     * 输入：owner、key、延迟毫秒数与执行逻辑；输出：无。
     */
    fun schedule(owner: String, key: String, delayMs: Long, action: () -> Unit) {
        lateinit var scheduledAction: ScheduledAction
        val runnable = Runnable {
            synchronized(actions) {
                actions.remove(scheduledAction)
            }
            action()
        }
        scheduledAction = ScheduledAction(owner = owner, key = key, runnable = runnable)
        synchronized(actions) {
            actions.add(scheduledAction)
        }
        handler.postDelayed(runnable, delayMs)
    }

    /**
     * 取消某个 owner 下的所有延迟动作。
     * 输入：owner 标识；输出：无。
     */
    fun cancelOwner(owner: String) {
        cancelMatching { it.owner == owner }
    }

    /**
     * 取消某个 owner 下指定 key 的所有延迟动作。
     * 输入：owner 与 key；输出：无。
     */
    fun cancelKey(owner: String, key: String) {
        cancelMatching { it.owner == owner && it.key == key }
    }

    /**
     * 取消以包名作为 key 注册的所有延迟动作。
     * 输入：目标包名；输出：无。
     */
    fun cancelTargetPackage(packageName: String) {
        cancelMatching { it.key == packageName }
    }

    /**
     * 取消当前调度器登记的全部延迟动作。
     * 输入：无；输出：无。
     */
    fun cancelAll() {
        cancelMatching { true }
    }

    /**
     * 按谓词批量取消调度动作。
     * 输入：匹配条件；输出：无。
     */
    private fun cancelMatching(predicate: (ScheduledAction) -> Boolean) {
        val matched = synchronized(actions) {
            actions.filter(predicate).also { toRemove ->
                actions.removeAll(toRemove.toSet())
            }
        }
        matched.forEach { handler.removeCallbacks(it.runnable) }
    }
}
