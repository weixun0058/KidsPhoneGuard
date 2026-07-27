# 拉钩守护统一术语表与 UI 映射

> 文档性质：产品、开发、测试、客服和 AI 协作的统一命名约定
> 最后同步：2026-07-26
> 适用代码：`com.kidsphoneguard`

## 1. 使用规则

1. 讨论功能或缺陷时，优先使用本文的“标准术语”和稳定 ID。
2. 推荐表达格式：`界面 ID / 元素 ID + 现象 + 触发条件`。例如：
   `OVL-02 / PASSWORD-INPUT：无障碍关闭后打开受控应用，密码框点了没有键盘。`
3. “页面元素”指具有业务含义的可见区域、状态、输入框、按钮、开关、列表项和对话框。`Row`、`Column`、`Spacer`、纯装饰图标等布局实现不单独编号；瞬时 Toast 提示统一归入所属页面的“反馈”元素。
4. 系统设置页面不是本 App 的 UI；本文只约定 App 用来打开它们的“系统设置入口”。
5. 新增、删除或重命名用户可见页面、对话框、悬浮层或关键元素时，必须同步本文。
6. 标准术语与代码类名不必相同。对外沟通用标准术语；定位代码时使用“代码映射”列。

## 2. 最容易混淆的术语

| ID | 标准术语 | 曾用说法/别名 | 明确定义 | 代码映射 |
| --- | --- | --- | --- | --- |
| STATE-01 | 降级状态 | 守护降级、无障碍失效状态 | 无障碍服务没有实际运行，或关键监控健康状态异常。它是内部状态，不是一个页面。 | `GuardProtectionHealthEvaluator`；`GuardForegroundService.refreshProtectionHealthState()` |
| OVL-02 | 降级保护层 | 降级锁、降级锁屏、降级全屏锁、降级保护页面 | `STATE-01` 下覆盖受限界面的可交互全屏悬浮层，能恢复无障碍、输入家长密码或联系客服恢复密码。 | `GuardForegroundService.refreshDegradedLockVisibility()` → `DegradedLockManager.showLockScreen()` → `doShowLockScreen()` → `buildLockView()` |
| OVL-01 | 普通应用遮罩 | 拦截遮罩、红屏、时间到遮罩 | 无障碍正常时，受控应用命中规则后显示的触摸拦截层。它不是降级保护层，也没有密码输入。 | `AppBlockCoordinator.enforceBlock()` → `OverlayCoordinator.showOverlay()` → `OverlayService.showOverlay()` / `showOverlayInternal()` |
| MODE-01 | 全局解锁 | 维护模式、全局放行 | 家长维护手机时暂停全部守护拦截，直到家长手动关闭。开启时不会显示普通应用遮罩或降级保护层。 | `SettingsManager.setGlobalUnlock()` / `isGlobalUnlockEnabled()` |
| MODE-02 | 全局锁机 | 全局锁、全拦截 | 不看单应用规则，对非白名单应用执行全局拦截。它与“全局解锁”互斥。 | `SettingsManager.setGlobalLock()` / `isGlobalLockEnabled()`；`LockDecisionEngine.getBlockDecision()` |
| MODE-03 | 降级临时解锁 | 临时解锁 5 分钟 | 只在降级保护层内提供的短期放行；最长 5 分钟，熄屏或无障碍恢复时提前结束。 | `DegradedTemporaryUnlockPolicy`；`DegradedLockManager.isParentTemporaryUnlockActive()` |
| REC-01 | 家长密码 | 管理密码 | 进入家长配置、执行维护和从降级保护层授权时使用的本地密码。 | `PasswordManager.setPassword()` / `verifyPassword()` |
| REC-02 | 家长密码恢复 | 忘记密码、客服恢复 | 用恢复设备号、计算日期和当日恢复码重设家长密码。它不直接解锁手机。 | `PasswordRecoveryActivity`；`RecoveryCodeManager.verify()`；`PasswordManager.setPassword()` |
| REC-03 | 恢复设备号 | 设备 ID、客服设备号 | App 恢复页显示的稳定标识。当前优先使用 `ANDROID_ID`，不可用时生成本地备用 ID。它不是 IMEI，也不要求用户输入 `*#06#`。 | `RecoveryCodeManager.snapshot()`；`RecoveryCodeEngine.formatRecoveryId()` |
| REC-04 | 计算日期 | 当天日期、恢复日期 | 恢复页明确显示并参与算号的 `yyyy-MM-dd` 日期，应原样报给客服。 | `TrustedTimeProvider.trustedToday()`；`RecoverySnapshot.recoveryDate` |
| REC-05 | 当日恢复码 | 超级密码、客服密码、动态密码 | 客服根据“恢复设备号 + 计算日期”离线算出的 8 位数字。标准术语不叫“永久超级密码”，因为它随日期和设备变化。 | `RecoveryCodeEngine.generateCode()`；`RecoveryCodeManager.verify()` |
| REC-06 | 客服离线算号器 | 算号器 | Windows 端离线生成 `REC-05` 的工具。手机和客服电脑都不需要联网。 | `tools/打开恢复码算号器.bat`；`tools/recovery-code-calculator.ps1` |
| MODE-04 | 配置向导临时放行 | 设置入口放行 | 家长从配置向导进入系统权限页面时使用的临时维护授权，不是全局解锁。 | `SettingsManager.allowSetupSettingsAccess()` / `clearSetupSettingsAccess()` |

## 3. 守护功能术语

| ID | 标准术语 | 功能边界 | 主要组件/函数 |
| --- | --- | --- | --- |
| GUARD-01 | 无障碍监控入口 | 接收 Android 无障碍事件并组装各守卫；不直接承载全部业务。 | `GuardAccessibilityService`；`AccessibilityEventRouter.route()` |
| GUARD-02 | 应用规则决策 | 根据全局模式、单应用规则、每日用量和禁用时段决定是否拦截。 | `LockDecisionEngine.getBlockDecision()` |
| GUARD-03 | 应用拦截执行链 | 执行普通受控应用的遮罩、BACK/HOME 和延迟动作。 | `AppBlockCoordinator.launchNormalPolicyCheck()` / `enforceBlock()` |
| GUARD-04 | 前台守护与看门狗 | 保活、健康检查、通知、无障碍掉权监测、降级保护层调度和取证。 | `GuardForegroundService`；`refreshProtectionHealthState()` |
| GUARD-05 | 使用统计兜底 | 每隔约 3 秒读取使用统计、累计时长；无障碍失效时继续提供前台应用判断和拦截触发。 | `UsageTrackingManager.startTracking()` |
| GUARD-06 | 设置守卫 | 保护无障碍、悬浮窗、应用信息等可能破坏守护的系统设置页面。 | `ProtectedSurfaceGuard.handleProtectedSettingsPolicyIfCandidate()` / `sweepProtectedInteractiveWindows()` |
| GUARD-07 | 系统面板守卫 | 收起通知栏、控制中心等系统面板。 | `SystemSurfaceGuard.collapseSystemPanelIfNeeded()` |
| GUARD-08 | 防卸载守卫 | 识别安装器确认框和桌面卸载入口，阻止卸载本 App。 | `UninstallGuard.handleOwnedSurfaceEvent()` / `sweepOwnedSurfaces()`；`UninstallDecisionEngine.evaluate()` |
| GUARD-09 | 微信视频号守卫 | 只处理微信视频号入口，不等同于封禁整个微信。 | `WeChatFinderGuard.handle()` |
| GUARD-10 | 无障碍恢复 | 有 `WRITE_SECURE_SETTINGS` 时尝试程序化恢复，否则打开系统无障碍设置。 | `AccessibilitySettingsRecovery.tryRestore()`；`PermissionManager.requestAccessibilityPermission()` |
| GUARD-11 | 进程可见锚点 | 1×1、不可触摸、不可聚焦的保活辅助窗口；用户不应把它当成任何保护层。 | `ProcessVisibilityAnchor.update()` |
| GUARD-12 | 已废弃拦截服务 | Manifest 兼容用空壳，不参与实际拦截。 | `AppBlockerService` |

## 4. 规则术语

| ID | 标准术语 | UI 别名 | 数据值 | 含义 |
| --- | --- | --- | --- | --- |
| RULE-01 | 放行规则 | 白名单模式、放行 | `RuleType.ALLOW` | 该应用不受单应用时长/时段限制。 |
| RULE-02 | 永久禁用规则 | 黑名单模式、永久禁用 | `RuleType.BLOCK` | 该应用始终拦截，直到家长修改规则。 |
| RULE-03 | 限时/限段规则 | 限时模式 | `RuleType.LIMIT` | 按每日分钟、禁用时段或两者共同管理。 |
| LIMIT-01 | 时长 + 时段 | 限时长+限时段 | `LimitMode.BOTH` | 同时检查每日累计时长与禁用时段。 |
| LIMIT-02 | 仅限时长 | — | `LimitMode.DURATION_ONLY` | 只检查每日累计分钟。 |
| LIMIT-03 | 仅限时段 | — | `LimitMode.WINDOW_ONLY` | 只检查禁用时段。 |
| RULE-04 | 今日时间调整 | 奖励/惩罚时间 | `TemporaryBonusManager` | 正数增加今日额度，负数减少今日额度，只对当天生效。 |

## 5. 页面与运行时界面总表

| ID | 标准名称 | 运行时载体 | 根 UI/构建函数 | 进入条件 |
| --- | --- | --- | --- | --- |
| PAGE-01 | 首次配置向导 | `MainActivity` | `PermissionGuideScreen()` | 尚未设置家长密码 |
| PAGE-02 | 守护主页 | `MainActivity` | `MainDashboardScreen()` | 已设置家长密码 |
| PAGE-03 | 家长配置页 | `ConfigActivity` | `ConfigScreen()` | `DLG-01` 验证通过，或首次配置完成后进入 |
| PAGE-04 | 配置维护面板 | `SetupWizardActivity` | `SetupMaintenanceScreen()` | 从家长配置页点击“打开配置向导” |
| PAGE-05 | 密码设置页 | `PasswordSettingsActivity` | `PasswordSettingsScreen()` | 首次配置或家长修改密码 |
| PAGE-06 | 家长密码恢复页 | `PasswordRecoveryActivity` | `PasswordRecoveryScreen()` | 点击“忘记密码/忘记当前密码” |
| DLG-01 | 家长密码验证对话框 | Compose `AlertDialog` | `ui.PasswordComponents.PasswordVerificationFlow()` / `PasswordDialog()` | 从守护主页进入家长配置 |
| DLG-02 | 单应用规则编辑对话框 | Compose `AlertDialog` | `AddRuleDialog()` | 新增或修改一条规则 |
| DLG-03 | 应用选择对话框 | Compose `AlertDialog` | `AppSelectorDialog()` | `DLG-02` 中选择应用 |
| DLG-04 | 批量规则配置对话框 | Compose `Dialog` | `BatchRuleDialog()` | 家长配置页点击批量配置 |
| DLG-05 | 批量配置结果对话框 | Compose `AlertDialog` | `BatchApplyResultDialog()` | 批量操作完成 |
| DLG-06 | 图标规则操作对话框 | Compose `AlertDialog` | `ConfigScreen()` 内长按分支 | 长按图标模式中的规则卡 |
| OVL-01 | 普通应用遮罩 | `TYPE_APPLICATION_OVERLAY` | `OverlayService.showOverlayInternal()` | 应用命中普通规则且降级保护层未接管 |
| OVL-02 | 降级保护层 | `TYPE_APPLICATION_OVERLAY` | `DegradedLockManager.buildLockView()` | 处于降级状态且前台是应受限界面 |
| NOTICE-01 | 守护常驻通知 | Android 通知 | `GuardForegroundService.createNotification()` | 前台守护服务运行 |

### 5.1 PAGE-01 · 首次配置向导

| 元素 ID | 标准名称 | 可见内容/状态 | 代码映射 |
| --- | --- | --- | --- |
| PAGE-01/HEADER | 品牌标题区 | “拉钩守护”与副标题 | `PermissionGuideScreen()` |
| PAGE-01/INTRO | 向导说明 | 按步骤配置、离开后继续 | `PermissionGuideScreen()` |
| PAGE-01/DEGRADED-WARNING | 守护异常提示 | 无障碍或使用统计异常时显示 | `isProtectionDegraded()` |
| PAGE-01/PROGRESS | 配置进度卡 | 已完成 X / 6 步 | `WizardProgressCard()` |
| PAGE-01/STEP-CARD | 当前步骤卡 | 步骤号、标题、原因、操作说明 | `SetupWizardStepCard()` |
| PAGE-01/PRIMARY-ACTION | 当前步骤主按钮 | 打开对应设置或密码页 | `SetupWizardStepCard()` |
| PAGE-01/BRAND-DONE | 品牌设置完成按钮 | “我已按上面步骤完成设置” | `SetupWizardStepCard()` |
| PAGE-01/RECHECK | 重新检查按钮 | “我已完成，重新检查” | `SetupWizardStepCard()` |
| PAGE-01/FINISHED | 首次配置完成卡 | 完成说明与下一步建议 | `SetupFinishedCard()` |
| PAGE-01/ENTER-CONFIG | 进入家长配置按钮 | 完成后进入规则配置 | `SetupFinishedCard()` |
| PAGE-01/RESET-BRAND | 重新确认品牌设置 | 清除品牌设置完成标记 | `SetupFinishedCard()` |

首次配置步骤统一称为：

| 步骤 ID | 标准名称 | 主入口函数 |
| --- | --- | --- |
| STEP-01 | 设置家长密码 | `PasswordSettingsActivity` |
| STEP-02 | 开启悬浮窗权限 | `PermissionManager.requestOverlayPermission()` |
| STEP-03 | 开启使用情况访问 | `PermissionManager.requestUsageStatsPermission()` |
| STEP-04 | 允许忽略电池优化 | `PermissionManager.requestIgnoreBatteryOptimizations()` |
| STEP-05 | 品牌后台保活设置 | `openBrandSetupEntry()` |
| STEP-06 | 开启无障碍服务 | `PermissionManager.requestAccessibilityPermission()` |

### 5.2 PAGE-02 · 守护主页

| 元素 ID | 标准名称 | 可见内容/状态 | 代码映射 |
| --- | --- | --- | --- |
| PAGE-02/HEADER | 品牌标题区 | App 名称与副标题 | `MainDashboardScreen()` |
| PAGE-02/STATUS-CARD | 守护状态卡 | “守护状态正常”或“需要家长检查” | `MainProtectionStatusCard()` |
| PAGE-02/STATUS-ACCESSIBILITY | 无障碍状态行 | 正常/需家长处理 | `StatusLine()` |
| PAGE-02/STATUS-USAGE | 使用情况访问状态行 | 正常/需家长处理 | `StatusLine()` |
| PAGE-02/STATUS-OVERLAY | 悬浮窗权限状态行 | 正常/需家长处理 | `StatusLine()` |
| PAGE-02/STATUS-BATTERY | 电池优化忽略状态行 | 正常/需家长处理 | `StatusLine()` |
| PAGE-02/STATUS-BRAND | 品牌后台保活状态行 | 正常/需家长处理 | `StatusLine()` |
| PAGE-02/PARENT-CARD | 家长配置入口卡 | 配置范围说明 | `ParentConfigEntryCard()` |
| PAGE-02/ENTER-PARENT | 家长配置入口按钮 | “输入家长密码进入”或“设置家长密码” | `ParentConfigEntryCard()` |

### 5.3 PAGE-03 · 家长配置页

| 元素 ID | 标准名称 | 可见内容/状态 | 代码映射 |
| --- | --- | --- | --- |
| PAGE-03/TITLE | 页面标题 | “家长配置” | `ConfigScreen()` |
| PAGE-03/GLOBAL-MODES | 全局模式卡 | 全局解锁、全局锁机及当前说明 | `GlobalModeControlRow()` |
| PAGE-03/GLOBAL-UNLOCK | 全局解锁开关 | 系统维护时使用 | `SettingsManager.setGlobalUnlock()` |
| PAGE-03/GLOBAL-LOCK | 全局锁机开关 | 全拦截 | `SettingsManager.setGlobalLock()` |
| PAGE-03/OPEN-MAINTENANCE | 打开配置向导按钮 | 进入 `PAGE-04` | `ConfigScreen()` |
| PAGE-03/ADD-RULE | 添加应用规则按钮 | 打开 `DLG-02` | `ConfigScreen()` |
| PAGE-03/BATCH-RULE | 批量配置规则按钮 | 打开 `DLG-04` | `ConfigScreen()` |
| PAGE-03/RULE-COUNT | 已配置规则标题 | 显示规则数量 | `ConfigScreen()` |
| PAGE-03/VIEW-MODE | 规则视图切换 | 图标模式/详情模式 | `ConfigScreen()` |
| PAGE-03/WECHAT-FINDER | 微信视频号开关卡 | 只控制视频号 | `WeChatVideoControlCard()` |
| PAGE-03/RULE-LIST | 详情规则列表 | 规则、用量、修改、删除 | `ConfigRuleCard()` |
| PAGE-03/RULE-GRID | 图标规则网格 | 图标、摘要；长按打开 `DLG-06` | `ConfigRuleGridCard()` |

### 5.4 PAGE-04 · 配置维护面板

| 元素 ID | 标准名称 | 可见内容/状态 | 代码映射 |
| --- | --- | --- | --- |
| PAGE-04/TITLE | 面板标题 | “配置向导” | `SetupMaintenanceScreen()` |
| PAGE-04/SUMMARY | 配置汇总卡 | 已配置 X / 6 项 | `SetupMaintenanceScreen()` |
| PAGE-04/ITEM-PASSWORD | 家长密码维护项 | 设置/修改密码 | `SetupMaintenanceCard()` |
| PAGE-04/ITEM-OVERLAY | 悬浮窗权限维护项 | 打开悬浮窗设置 | `SetupMaintenanceCard()` |
| PAGE-04/ITEM-USAGE | 使用情况访问维护项 | 打开使用情况访问 | `SetupMaintenanceCard()` |
| PAGE-04/ITEM-BATTERY | 电池与后台维护项 | 打开电池设置 | `SetupMaintenanceCard()` |
| PAGE-04/ITEM-BRAND | 品牌后台保活维护项 | 打开品牌设置并人工确认 | `SetupMaintenanceCard()` |
| PAGE-04/ITEM-ACCESSIBILITY | 无障碍维护项 | 打开无障碍设置 | `SetupMaintenanceCard()` |
| PAGE-04/RECHECK | 单项重新检查按钮 | 刷新该项状态并反馈 | `SetupMaintenanceScreen.refreshStatus()` |
| PAGE-04/BACK | 返回家长配置按钮 | 关闭 `SetupWizardActivity` | `SetupMaintenanceScreen()` |

### 5.5 PAGE-05 · 密码设置页

| 元素 ID | 标准名称 | 显示条件/作用 | 代码映射 |
| --- | --- | --- | --- |
| PAGE-05/TITLE | 页面标题 | “密码设置” | `PasswordSettingsScreen()` |
| PAGE-05/INTRO | 密码设置说明 | 区分首次设置与修改密码 | `PasswordSettingsScreen()` |
| PAGE-05/CURRENT-PASSWORD | 当前密码输入框 | 已有密码时显示 | `PasswordManager.verifyPassword()` |
| PAGE-05/FORGOT | 忘记当前密码按钮 | 已有密码时显示；打开 `PAGE-06` | `PasswordSettingsScreen()` |
| PAGE-05/NEW-PASSWORD | 新密码输入框 | 至少 6 位数字 | `PasswordSettingsScreen()` |
| PAGE-05/CONFIRM-PASSWORD | 确认新密码输入框 | 必须与新密码一致 | `PasswordSettingsScreen()` |
| PAGE-05/FEEDBACK | 保存反馈区 | 错误或成功信息 | `PasswordSettingsScreen()` |
| PAGE-05/CANCEL | 取消按钮 | 关闭页面 | `PasswordSettingsScreen()` |
| PAGE-05/SAVE | 保存按钮 | 验证并写入 PBKDF2 密码 | `PasswordManager.setPassword()` |

### 5.6 PAGE-06 · 家长密码恢复页

| 元素 ID | 标准名称 | 作用 | 代码映射 |
| --- | --- | --- | --- |
| PAGE-06/TITLE | 页面标题 | “忘记家长密码” | `PasswordRecoveryScreen()` |
| PAGE-06/INSTRUCTION | 客服恢复说明 | 告知家长需要报设备号和日期 | `PasswordRecoveryScreen()` |
| PAGE-06/RECOVERY-ID | 恢复设备号 | 显示 `REC-03` | `RecoveryCodeManager.snapshot()` |
| PAGE-06/RECOVERY-DATE | 计算日期 | 显示 `REC-04` | `RecoveryCodeManager.snapshot()` |
| PAGE-06/COPY | 复制恢复信息按钮 | 复制设备号和日期 | `PasswordRecoveryScreen()` |
| PAGE-06/CODE | 当日恢复码输入框 | 8 位数字 | `RecoveryCodeManager.verify()` |
| PAGE-06/NEW-PASSWORD | 新家长密码输入框 | 至少 6 位数字 | `PasswordRecoveryScreen()` |
| PAGE-06/CONFIRM-PASSWORD | 确认新密码输入框 | 必须与新密码一致 | `validateRecoveryInput()` |
| PAGE-06/FEEDBACK | 恢复反馈区 | 格式错误、恢复码错误、冷却时间 | `PasswordRecoveryScreen()` |
| PAGE-06/CANCEL | 取消按钮 | 关闭页面 | `PasswordRecoveryScreen()` |
| PAGE-06/SUBMIT | 验证并重设密码按钮 | 校验恢复码并覆盖家长密码 | `RecoveryCodeManager.verify()` → `PasswordManager.setPassword()` |

### 5.7 DLG-01 · 家长密码验证对话框

| 元素 ID | 标准名称 | 作用 | 代码映射 |
| --- | --- | --- | --- |
| DLG-01/TITLE | 对话框标题 | “请输入密码” | `ui.PasswordComponents.PasswordDialog()` |
| DLG-01/PASSWORD | 密码输入框 | 验证家长密码 | `PasswordManager.verifyPassword()` |
| DLG-01/ERROR | 密码错误提示 | 验证失败时显示 | `PasswordDialog()` |
| DLG-01/CONFIRM | 确认按钮 | 验证并进入家长配置 | `PasswordVerificationFlow()` |
| DLG-01/FORGOT | 忘记密码按钮 | 打开 `PAGE-06` | `PasswordVerificationFlow()` |
| DLG-01/CANCEL | 取消按钮 | 关闭对话框 | `PasswordDialog()` |

> 注意：`ui/components/PasswordDialog.kt` 还有一套同名旧副本，但当前运行入口没有引用它，也没有“忘记密码”按钮。定位线上 UI 时，应修改 `ui/PasswordComponents.kt`，不要误改旧副本。

### 5.8 DLG-02 ～ DLG-06 · 规则对话框

| 界面/元素 ID | 标准名称 | 作用 | 代码映射 |
| --- | --- | --- | --- |
| DLG-02/APP | 应用选择入口 | 新建规则时打开 `DLG-03` | `AddRuleDialog()` |
| DLG-02/RULE-TYPE | 规则类型选择 | `RULE-01` / `RULE-02` / `RULE-03` | `AddRuleDialog()` |
| DLG-02/LIMIT-MODE | 限时模式选择 | `LIMIT-01` / `LIMIT-02` / `LIMIT-03` | `AddRuleDialog()` |
| DLG-02/DAILY-MINUTES | 每日分钟输入 | 时长模式使用 | `LimitConfigCard()` |
| DLG-02/BLOCK-WINDOW | 禁用时段选择 | 时段模式使用 | `TimeWindowSelector()` |
| DLG-02/TODAY-ADJUST | 今日时间调整区 | 修改规则时奖励/惩罚今日时间 | `AddRuleDialog()` |
| DLG-02/RESET-TODAY | 清零今日已用按钮 | 修改规则时可用 | `ConfigViewModel.resetTodayUsage()` |
| DLG-02/CONFIRM | 确定/保存按钮 | 保存规则 | `ConfigViewModel.saveRule()` |
| DLG-02/CANCEL | 取消按钮 | 关闭对话框 | `AddRuleDialog()` |
| DLG-03/SEARCH | 搜索应用输入框 | 按名称或包名筛选 | `AppSelectorDialog()` |
| DLG-03/SYSTEM | 显示系统应用开关 | 决定是否列出系统 App | `AppSelectorDialog()` |
| DLG-03/VIEW | 应用列表/图标切换 | 切换选择布局 | `AppSelectorDialog()` |
| DLG-03/APPS | 应用候选区 | 单击选择应用 | `AppListItem()` / `AppGridSelectItem()` |
| DLG-03/CANCEL | 取消按钮 | 关闭选择器 | `AppSelectorDialog()` |
| DLG-04/STEP-1 | 批量规则设置页 | 选择规则与限时参数 | `BatchRuleDialog()` |
| DLG-04/STEP-2 | 批量应用选择页 | 搜索、系统应用、覆盖已配置、列表/图标、复选 | `BatchRuleDialog()` |
| DLG-04/NEXT | 下一步按钮 | 从规则页进入应用页 | `BatchRuleDialog()` |
| DLG-04/BACK | 上一步按钮 | 返回规则页 | `BatchRuleDialog()` |
| DLG-04/APPLY | 批量应用按钮 | 执行批量配置 | `ConfigViewModel.applyBatchRules()` |
| DLG-05/SUMMARY | 批量结果摘要 | 新增/更新、取消配置、跳过数量 | `BatchApplyResultDialog()` |
| DLG-05/CLOSE | 知道了按钮 | 关闭结果 | `BatchApplyResultDialog()` |
| DLG-06/EDIT | 修改按钮 | 打开规则编辑 | `ConfigScreen()` |
| DLG-06/DELETE | 删除按钮 | 删除该规则 | `ConfigViewModel.deleteRule()` |

### 5.9 OVL-01 · 普通应用遮罩

| 元素 ID | 标准名称 | 作用 | 代码映射 |
| --- | --- | --- | --- |
| OVL-01/TOUCH-BLOCKER | 全屏触摸拦截区 | 吞掉所有触摸，防止继续操作受控应用 | `OverlayService.showOverlayInternal()` |
| OVL-01/MESSAGE | 拦截信息 | 显示应用名和“该应用的使用时间已到” | `OverlayService.resolveOverlayAppName()` |

当前普通应用遮罩没有密码框，也没有可点击的“返回桌面”按钮；返回动作由拦截执行链的导航逻辑负责。

### 5.10 OVL-02 · 降级保护层

| 元素 ID | 标准名称 | 显示条件/作用 | 代码映射 |
| --- | --- | --- | --- |
| OVL-02/TITLE | 降级保护标题 | “设备保护功能需要重新启用” | `DegradedLockManager.buildLockView()` |
| OVL-02/DESCRIPTION | 降级原因说明 | 提示保护组件关闭 | `buildLockView()` |
| OVL-02/RESTORE | 恢复保护按钮 | 先尝试程序化恢复，否则打开无障碍设置 | `AccessibilitySettingsRecovery.tryRestore()` |
| OVL-02/EXIT-HOME | 退出受限应用按钮 | 无密码返回桌面；再次进入仍会拦截 | `DegradedLockManager.beginExitToHome()` |
| OVL-02/EXIT-HINT | 返回桌面说明 | 说明退出不等于解除守护 | `buildLockView()` |
| OVL-02/PASSWORD-LABEL | 家长授权区标题 | 默认“家长临时解锁” | `buildLockView()` |
| OVL-02/PASSWORD-INPUT | 家长密码输入框 | 输入 `REC-01` | `PasswordManager.verifyPassword()` |
| OVL-02/VERIFY-PASSWORD | 验证密码按钮 | 验证后显示授权方式 | `buildLockView()` 内 `submitParentPassword` |
| OVL-02/FORGOT | 联系客服恢复按钮 | 切换到内嵌恢复面板 | `buildLockView()` |
| OVL-02/UNLOCK-CHOICES | 授权方式面板 | 密码验证成功后显示 | `buildLockView()` |
| OVL-02/GLOBAL-UNLOCK | 全局解锁按钮 | 开启 `MODE-01` | `SettingsManager.setGlobalUnlock()` |
| OVL-02/TEMP-UNLOCK | 临时解锁按钮 | 开启 `MODE-03` | `DegradedTemporaryUnlockPolicy.expiresAt()` |
| OVL-02/RECOVERY-INFO | 内嵌恢复信息 | 显示 `REC-03` 与 `REC-04` | `RecoveryCodeManager.snapshot()` |
| OVL-02/RECOVERY-CODE | 内嵌恢复码输入框 | 输入 `REC-05` | `RecoveryCodeManager.verify()` |
| OVL-02/RECOVERY-NEW | 内嵌新密码输入框 | 设置新家长密码 | `PasswordManager.setPassword()` |
| OVL-02/RECOVERY-CONFIRM | 内嵌确认密码输入框 | 与新密码一致 | `buildLockView()` |
| OVL-02/RECOVERY-SUBMIT | 内嵌恢复提交按钮 | 验证恢复码并重设密码 | `RecoveryCodeManager.verify()` |
| OVL-02/RECOVERY-BACK | 返回密码验证按钮 | 从恢复面板切回密码输入 | `buildLockView()` |
| OVL-02/FOOTER | 底部帮助文案 | “如需帮助，请联系家长” | `buildLockView()` |

### 5.11 NOTICE-01 · 守护常驻通知

| 元素 ID | 标准名称 | 作用 | 代码映射 |
| --- | --- | --- | --- |
| NOTICE-01/TITLE | 通知标题 | “拉钩守护运行中” | `R.string.notification_title` |
| NOTICE-01/CONTENT | 正常通知内容 | “正在保护孩子的手机使用” | `R.string.notification_content` |
| NOTICE-01/DEGRADED | 降级通知内容 | 提醒进入应用恢复关键权限 | `R.string.notification_content_degraded` |
| NOTICE-01/TAP | 通知点击入口 | 打开 `MainActivity` | `GuardForegroundService.createNotification()` |

## 6. 系统设置入口约定

以下都是 Android/厂商页面，不属于拉钩守护自身页面。报告问题时应说“从哪个 App 元素打开了哪个系统入口”。

| ID | 标准名称 | App 入口函数 |
| --- | --- | --- |
| OS-01 | 系统悬浮窗设置入口 | `PermissionManager.requestOverlayPermission()` |
| OS-02 | 系统使用情况访问入口 | `PermissionManager.requestUsageStatsPermission()` |
| OS-03 | 系统电池优化入口 | `PermissionManager.requestIgnoreBatteryOptimizations()` |
| OS-04 | 系统无障碍设置入口 | `PermissionManager.requestAccessibilityPermission()` |
| OS-05 | 品牌后台保活入口 | `openBrandSetupEntry()` / `PermissionManager.requestHuaweiProtectionGuide()` |
| OS-06 | 本应用系统详情入口 | `openCurrentAppDetails()` |

## 7. 家长密码恢复文档同步矩阵

| 文档 | 已同步内容 |
| --- | --- |
| `README.md` | 功能入口、离线恢复行为、代码结构与安全边界 |
| `AGENTS.md` | 面向 AI/开发者的 ISS-025 架构说明和真机验收结果 |
| `docs/ISSUES.md` · ISS-025 | 产品决策、实现范围、测试记录和用户验收通过状态 |
| `tools/恢复码算号器说明.md` | 客服实际操作、命令行方式、自检与同步要求 |
| 本文 | 统一标准术语、全部运行时 UI 页面/关键元素及代码映射 |

恢复机制的统一边界：

- 手机端和客服算号器都可完全离线运行。
- 当日恢复码只重设家长密码，不直接开启全局解锁、不直接关闭守护、不直接放行卸载。
- 客服必须使用恢复页显示的恢复设备号和计算日期，不自行替换成 IMEI、手机号或客服电脑日期。
- `RecoveryCodeEngine` 的算法、设备号规范化、日期格式和主密钥发生变化时，Android 端、算号器、固定测试向量和本文必须同时更新。

## 8. 推荐沟通示例

- `PAGE-06 / RECOVERY-ID 显示不完整，横屏时最后四位被截断。`
- `OVL-02 / PASSWORD-INPUT 在 Redmi 上点了以后键盘不断闪烁。`
- `PAGE-03 / GLOBAL-UNLOCK 已关闭，但打开受控应用没有出现 OVL-01。`
- `GUARD-06 在 OS-04 的无障碍详情页没有及时执行拦截。`
- `DLG-04 / STEP-2 勾选“覆盖已配置”后，RULE-01 被错误改成 RULE-03。`
