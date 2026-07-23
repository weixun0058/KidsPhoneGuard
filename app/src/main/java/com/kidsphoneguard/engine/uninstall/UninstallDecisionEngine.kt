package com.kidsphoneguard.engine.uninstall

/**
 * 卸载相关窗口表面的只读快照。
 * 输入：由 Android 壳（UninstallGuard）采集的窗口/事件文本；输出：供纯决策核心评估的不可变输入。
 */
internal data class UninstallSurfaceSnapshot(
    val packageName: String,
    val className: String,
    val pageText: String,
    val windowPackages: Set<String>,
    val clickedText: String,
    /**
     * 归因信号：最近几秒内用户长按过本应用图标（由 UninstallGuard 记录）。
     * MIUI 卸载确认界面可能不显示应用名，且遍历桌面整树取"拉钩守护"图标文本代价过高
     * （逐节点 IPC，数百节点即数秒），因此允许用近期长按归因替代页面内的应用标识。
     */
    val recentTargetAppLongPress: Boolean = false
)

/**
 * 卸载判定所需的运行时状态（家长逃生口）。
 * 输入：全局解锁与设置向导放行标志；输出：供纯决策核心读取的不可变输入。
 */
internal data class UninstallRuntimeState(
    val isGlobalUnlockEnabled: Boolean,
    val isSetupAccessAllowed: Boolean
)

enum class UninstallDecisionType {
    ALLOW,
    BLOCK_PAGE,
    BLOCK_ACTION
}

/**
 * 卸载拦截决策结果。
 * 输入：决策类型与原因；输出：带匹配关键词的取证信息。
 */
internal data class UninstallDecision(
    val type: UninstallDecisionType,
    val reason: String,
    val matchedTarget: String = "",
    val matchedUninstallKeywords: List<String> = emptyList()
)

/**
 * 卸载守卫规则值集合。
 * 输入：安装器家族、launcher 家族、卸载动作关键词与本应用标识关键词；输出：供引擎使用的不可变规则。
 */
internal data class UninstallGuardRules(
    val installerPackages: Set<String> = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller"
    ),
    val launcherPackages: Set<String> = setOf(
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.bbk.launcher2",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher"
    ),
    val uninstallActionKeywords: Set<String> = setOf(
        "卸载",
        "卸载应用",
        "uninstall",
        "remove app"
    ),
    val targetAppKeywords: Set<String> = setOf(
        "拉钩守护",
        "儿童手机守护",
        "KidsPhoneGuard",
        "com.kidsphoneguard"
    )
)

/**
 * 防卸载纯决策核心（JVM 可测，镜像 ProtectedSettingsDecisionEngine 模式）。
 *
 * 统一判定模型：任何界面同时满足"出现本应用标识"且"提供卸载入口或处于卸载确认流程" → 阻断。
 * Android 状态由 UninstallGuard 采集并以 [UninstallRuntimeState] 注入，因此全部规则可在 JVM 测试中验证。
 */
internal class UninstallDecisionEngine(rules: UninstallGuardRules = UninstallGuardRules()) {

    private val installerPackages = rules.installerPackages.map { normalizePackageName(it) }.toSet()
    private val launcherPackages = rules.launcherPackages.map { normalizePackageName(it) }.toSet()
    private val uninstallActionKeywords = rules.uninstallActionKeywords.toList()
    private val targetAppKeywords = rules.targetAppKeywords.toList()

    /**
     * 按固定优先级评估卸载威胁。
     * 输入：窗口表面快照与运行时状态；输出：ALLOW / BLOCK_PAGE / BLOCK_ACTION 决策。
     */
    fun evaluate(
        snapshot: UninstallSurfaceSnapshot,
        runtimeState: UninstallRuntimeState
    ): UninstallDecision {
        val ownedPackage = findOwnedPackage(snapshot)
        val pageSignal = normalizeText(
            listOf(
                snapshot.pageText,
                snapshot.className,
                snapshot.packageName,
                snapshot.windowPackages.joinToString(" ")
            ).joinToString(" ")
        )
        val clickedSignal = normalizeText(snapshot.clickedText)
        val pageUninstallKeywords = keywordMatches(pageSignal, uninstallActionKeywords)
        val clickedUninstallKeywords = keywordMatches(clickedSignal, uninstallActionKeywords)

        // 规则 1：既不属于安装器/launcher 家族，也没有任何卸载信号 → 不是候选。
        if (ownedPackage == null && pageUninstallKeywords.isEmpty() && clickedUninstallKeywords.isEmpty()) {
            return UninstallDecision(
                type = UninstallDecisionType.ALLOW,
                reason = "not_uninstall_candidate"
            )
        }
        // 规则 2：家长逃生口（全局解锁或设置向导放行）→ 整体放行。
        if (runtimeState.isGlobalUnlockEnabled) {
            return UninstallDecision(
                type = UninstallDecisionType.ALLOW,
                reason = "global_unlock_enabled"
            )
        }
        if (runtimeState.isSetupAccessAllowed) {
            return UninstallDecision(
                type = UninstallDecisionType.ALLOW,
                reason = "setup_access_allowed"
            )
        }

        val targetKeyword = firstKeywordMatch(pageSignal, targetAppKeywords)
        // 应用标识命中 = 页面内出现本应用标识，或最近几秒内长按过本应用图标（归因窗口）。
        val hasAppIdentity = targetKeyword.isNotEmpty() || snapshot.recentTargetAppLongPress

        // 规则 3：安装器家族窗口且页面同时含本应用标识（或长按归因）与卸载关键词 → 卸载确认弹窗，整页阻断。
        // 仅含本应用标识而无卸载信号的安装/更新确认页必须放行，否则家长无法正常安装/更新本应用
        // （2026-07-23 真机实证：pm install 的 InstallStaging 被误判拦截，安装流程被 HOME 杀掉导致挂起）。
        if (ownedPackage != null &&
            isInstallerPackage(ownedPackage) &&
            hasAppIdentity &&
            pageUninstallKeywords.isNotEmpty()
        ) {
            return UninstallDecision(
                type = UninstallDecisionType.BLOCK_PAGE,
                reason = "installer_uninstall_confirm_page",
                matchedTarget = targetKeyword.ifEmpty { "recent_long_press" },
                matchedUninstallKeywords = pageUninstallKeywords
            )
        }
        // 规则 4：点击文本含卸载关键词且页面含本应用标识（或长按归因）→ 只阻断该次点击。
        if (clickedUninstallKeywords.isNotEmpty() && hasAppIdentity) {
            return UninstallDecision(
                type = UninstallDecisionType.BLOCK_ACTION,
                reason = "uninstall_action_click",
                matchedTarget = targetKeyword.ifEmpty { "recent_long_press" },
                matchedUninstallKeywords = clickedUninstallKeywords
            )
        }
        // 规则 5：launcher 家族窗口且页面同时含本应用标识（或长按归因）与卸载关键词 → 卸载确认界面，整页阻断。
        if (ownedPackage != null &&
            isLauncherPackage(ownedPackage) &&
            hasAppIdentity &&
            pageUninstallKeywords.isNotEmpty()
        ) {
            return UninstallDecision(
                type = UninstallDecisionType.BLOCK_PAGE,
                reason = "launcher_uninstall_confirm_page",
                matchedTarget = targetKeyword.ifEmpty { "recent_long_press" },
                matchedUninstallKeywords = pageUninstallKeywords
            )
        }
        // 规则 6：其余情况放行。
        return UninstallDecision(
            type = UninstallDecisionType.ALLOW,
            reason = "no_uninstall_threat_detected"
        )
    }

    /**
     * 判断包名是否属于卸载守卫所有权的表面（安装器家族或 launcher 家族）。
     * 输入：任意包名；输出：是否归 UninstallGuard 单owner处理。
     */
    fun isOwnedPackage(packageName: String): Boolean {
        return isInstallerPackage(packageName) || isLauncherPackage(packageName)
    }

    fun isInstallerPackage(packageName: String): Boolean {
        return matchesFamily(packageName, installerPackages)
    }

    fun isLauncherPackage(packageName: String): Boolean {
        return matchesFamily(packageName, launcherPackages)
    }

    /**
     * 在快照中查找第一个属于卸载守卫所有权的包名。
     * 输入：窗口表面快照；输出：命中的归一化包名，未命中返回 null。
     */
    fun findOwnedPackage(snapshot: UninstallSurfaceSnapshot): String? {
        val packages = linkedSetOf<String>()
        packages.add(snapshot.packageName)
        packages.addAll(snapshot.windowPackages)
        return packages.firstOrNull { isOwnedPackage(it) }?.let { normalizePackageName(it) }
    }

    fun containsUninstallSignal(text: String): Boolean {
        val normalized = normalizeText(text)
        return uninstallActionKeywords.any { keyword ->
            normalized.contains(normalizeText(keyword))
        }
    }

    fun containsTargetAppSignal(text: String): Boolean {
        val normalized = normalizeText(text)
        return targetAppKeywords.any { keyword ->
            normalized.contains(normalizeText(keyword))
        }
    }

    fun describeRules(): String {
        return "installers=${installerPackages.size} launchers=${launcherPackages.size} " +
            "uninstallKeywords=${uninstallActionKeywords.size} targets=${targetAppKeywords.size}"
    }

    private fun matchesFamily(packageName: String, family: Set<String>): Boolean {
        val normalized = normalizePackageName(packageName)
        if (normalized.isEmpty()) {
            return false
        }
        return family.any { candidate ->
            normalized == candidate || normalized.startsWith("$candidate.")
        }
    }

    private fun firstKeywordMatch(signal: String, keywords: List<String>): String {
        return keywords.firstOrNull { keyword ->
            signal.contains(normalizeText(keyword))
        }.orEmpty()
    }

    private fun keywordMatches(signal: String, keywords: List<String>): List<String> {
        return keywords.filter { keyword ->
            signal.contains(normalizeText(keyword))
        }
    }

    private fun normalizeText(text: String): String = text.lowercase()

    private fun normalizePackageName(packageName: String): String {
        return packageName.trim().substringBefore(':').lowercase()
    }
}

/**
 * 卸载遮蔽层释放策略（纯函数，JVM 可测）。
 * 家长放行或卸载威胁消失即释放；不得以"目标包仍在前台"为持有依据——
 * launcher 被拦截后 HOME 的归宿仍是 launcher，前台条件永不收敛（2026-07-23 真机缺陷）。
 */
internal fun shouldReleaseUninstallOverlay(
    suppressionAllowed: Boolean,
    threatStillPresent: Boolean
): Boolean = suppressionAllowed || !threatStillPresent

/**
 * 卸载遮蔽层持有重排策略（纯函数，JVM 可测）。
 * 仅在最后一次检查且未超持有上限时重排，防止遮蔽层被无限持有。
 */
internal fun shouldRearmUninstallOverlayChecks(
    isFinalCheck: Boolean,
    cycle: Int,
    maxCycles: Int
): Boolean = isFinalCheck && cycle < maxCycles
