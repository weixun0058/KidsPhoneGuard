# KidsPhoneGuard 问题清单

本文档是长期维护的问题记录本，用于记录重构、测试、设备验证过程中发现的问题、复发原因、处理办法和验证结果。

使用原则：

- 不把所有问题都混进当前解耦阶段修复。
- 明确区分“重构引入的回归”和“解耦前已存在的遗留问题”。
- 每个问题都保留复现条件、日志 marker、代码位置、处理状态，避免问题复发时重新全量梳理代码。
- 运行时问题先看日志，再改代码。
- 每次修复后补充“修复方式”和“验证结果”，不要只改状态。

状态说明：

- `Open`：已确认存在，尚未修复。
- `Investigating`：现象明确，但原因还需要日志或设备验证。
- `Deferred`：已记录，暂不在当前解耦阶段处理。
- `Fixed`：已修复，并有验证结果。
- `Watch`：当前通过，但属于易复发问题，需要后续回归关注。

优先级说明：

- `P0`：阻塞核心拦截或会导致重构正确性无法判断。
- `P1`：重要行为失败或高概率影响真实使用。
- `P2`：体验、速度、品牌兼容或低频路径问题。
- `P3`：代码质量、可维护性、测试覆盖补强。

## 当前未解决问题

### ISSUE-001 无障碍权限设置页拦截失败

- 状态：`Open`
- 优先级：`P1`
- 所属模块：Protected Settings / Settings Protection
- 发现阶段：Phase 3 设备验证后
- 现象：
  - 进入受保护设置页中的“无障碍权限设置”时，未能成功拦截。
  - 同组验证中，“应用卸载”和“使用情况访问”可以拦截，说明 protected settings 总入口不是完全失效。
- 当前判断：
  - 更像是 `ProtectedSettingsPolicy` 的页面信号识别不足，而不是 `AccessibilityEventRouter` 未分发。
  - 可能没有稳定读到本应用名称、无障碍关键词，或系统设置页的文本分布在 event source / active root / interactive windows 之外。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
    - `handleProtectedSettingsPolicyIfCandidate(...)`
    - `buildSettingsPageSnapshot(...)`
    - `collectCandidateWindowNodeSignals(...)`
  - `app/src/main/java/com/kidsphoneguard/engine/settingsprotection/ProtectedSettingsPolicy.kt`
    - `evaluate(...)`
  - `app/src/main/java/com/kidsphoneguard/engine/settingsprotection/BrandSettingsRules.kt`
    - `riskyCapabilityKeywords`
    - `targetAppKeywords`
- 需要抓取的日志 marker：
  - `protected_settings_decision`
  - `settings_snapshot_event_source_failed`
  - `settings_snapshot_root_failed`
  - `settings_snapshot_windows_failed`
  - `protected_surface_fast_suppress`
- 建议下一步：
  - 先抓无障碍设置页的 `protected_settings_decision` 日志，确认 reason 是 `candidate_settings_without_target`、`target_app_settings_without_risk_keyword`，还是完全没有进入 policy。
  - 若完全没有进入 policy，优先查包名和窗口包识别。
  - 若进入 policy 但未命中，补充最小关键词或 snapshot 信号，不要把规则扩成万能文本扫描。
- 当前处理策略：
  - 暂不混入 Phase 3 解耦收尾。
  - 等 Protected Settings 进入独立模块或专项修复时处理。
- 验证要求：
  - 小米设备进入无障碍设置页必须触发拦截。
  - 不能破坏“本应用打开通过”和“应用商店拦截成功”。

### ISSUE-002 使用情况访问页拦截成功但速度慢

- 状态：`Investigating`
- 优先级：`P1`
- 所属模块：Protected Settings / Snapshot / Action Timing
- 发现阶段：Phase 3 设备验证后
- 现象：
  - 进入“使用情况访问”设置页时最终能拦截，但速度明显偏慢。
- 当前判断：
  - 可能是 snapshot 采集范围过大、窗口扫描延迟、策略需要等到后续事件才命中，或执行拦截动作排队较晚。
  - 不能直接假设是 router 顺序问题，因为应用卸载和普通拦截都已经通过。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
    - `buildSettingsPageSnapshot(...)`
    - `collectCandidateWindowNodeSignals(...)`
    - `suppressProtectedSystemSurface(...)`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
    - `routeWindowEvent(...)`
    - `routeInteractionEvent(...)`
- 需要抓取的日志 marker：
  - `protected_settings_decision`
  - `protected_surface_fast_suppress`
  - `publishLifecycleSignal`
  - `settings_snapshot_*`
- 建议下一步：
  - 在不改变行为的前提下，先补充耗时日志：
    - snapshot 构建耗时
    - policy evaluate 耗时
    - suppress 执行动作耗时
    - 从首次 event 到 block 的间隔
  - 根据日志决定是收紧 snapshot，还是提前识别 usage access 页面。
- 当前处理策略：
  - 记录为性能/时序问题。
  - 不建议和路由解耦混修。
- 验证要求：
  - 进入使用情况访问页后应快速拦截。
  - 不能引入 `com.xiaomi.market` 卡死回归。

### ISSUE-003 全普通权限访问拦截成功但速度慢

- 状态：`Investigating`
- 优先级：`P1`
- 所属模块：Protected Settings / Permission Controller
- 发现阶段：Phase 3 设备验证后
- 现象：
  - 进入全普通权限访问相关页面时最终能拦截，但速度明显偏慢。
- 当前判断：
  - 与 ISSUE-002 类似，可能是 snapshot 或策略命中时机偏晚。
  - 也可能是 Android 权限控制器页面包名、文本、目标应用名的组合不稳定。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/engine/settingsprotection/BrandSettingsRules.kt`
    - `protectedSettingPackages`
    - `riskyCapabilityKeywords`
    - `riskyActionKeywords`
  - `app/src/main/java/com/kidsphoneguard/engine/settingsprotection/ProtectedSettingsPolicy.kt`
    - `evaluate(...)`
  - `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
    - `buildSettingsPageSnapshot(...)`
- 需要抓取的日志 marker：
  - `protected_settings_decision`
  - `protected_surface_fast_suppress`
  - `settings_snapshot_*`
- 建议下一步：
  - 和 ISSUE-002 一起做耗时拆解。
  - 但修复时分开提交，避免“权限页”和“使用情况访问页”互相影响。
- 当前处理策略：
  - 暂不作为 Phase 3 阻塞项。
- 验证要求：
  - 权限页快速拦截。
  - 白名单、本应用打开、普通拦截保持通过。

### ISSUE-004 微信视频号拦截失败

- 状态：`Open`
- 优先级：`P1`
- 所属模块：WeChat Finder
- 发现阶段：Phase 3 设备验证后
- 最近复核阶段：Phase 6 closeout
- 现象：
  - 微信视频号未能成功拦截。
- 当前判断：
  - 现有识别规则仍然过窄，只依赖 `packageName == com.tencent.mm` 且 className 匹配 `com.tencent.mm.plugin.finder.*UI`。
  - Phase 6 已把 WeChat Finder 逻辑迁入 `WeChatFinderGuard`，但设备复测中“视频号入口 / 视频号详情页”仍不通过，且新日志里没有看到 `wechat_finder_block` 命中 marker。
  - 这更像是旧识别规则仍未覆盖新版微信入口/详情页，而不是 Phase 6 抽离后执行链路损坏。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/guard/WeChatFinderGuard.kt`
    - `handle(...)`
    - `shouldBlockCurrentFinderShape(...)`
    - `blockWeChatFinder(...)`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
    - `routeWindowEvent(...)`
- 需要抓取的日志 marker：
  - `wechat_finder`
  - `wechat_finder_block`
  - `wechat_finder_block_skip_cooldown`
- 建议下一步：
  - 先记录进入视频号时的：
    - event type
    - packageName
    - className
    - event text
    - contentDescription
    - active root / window text signal
  - 对照 Phase 6 复测日志，确认是否仍然完全没有 `wechat_finder_block` / `wechat_finder_block_skip_cooldown`，再决定是否扩展 Finder 识别信号。
  - 不要只凭猜测扩大 className 白名单；应基于新的 `com.tencent.mm` 页面日志决定是补类名、补文本信号，还是引入更稳的 snapshot 判断。
- 当前处理策略：
  - 不作为 Phase 6 阻塞项。
  - 继续作为独立的 WeChat Finder 专项问题处理。
- 验证要求：
  - 视频号入口、视频号详情页、返回微信聊天页都要验证。
  - 不能误拦普通微信聊天、支付、通讯录等核心路径。

### ISSUE-005 荣耀省电模式尚未设备验证

- 状态：`Deferred`
- 优先级：`P2`
- 所属模块：OEM Power Save / Protected Settings
- 发现阶段：Phase 3 后续测试计划
- 现象：
  - 小米上暂未发现省电模式问题。
  - 荣耀设备尚未测试。
- 当前判断：
  - 不应从小米结果推断荣耀结果。
  - 荣耀可能涉及 `com.huawei.systemmanager`、`com.huawei.android.launcher`、`com.huawei.controlcenter` 等不同窗口来源。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/engine/settingsprotection/BrandSettingsRules.kt`
    - `HuaweiSettingsRules`
  - `app/src/main/java/com/kidsphoneguard/engine/settingsprotection/ProtectedSettingsPolicy.kt`
    - `isHuaweiPowerModePage(...)`
  - `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`
    - `handle(...)`
    - `exitVisiblePowerSaveModeIfNeeded(...)`
- 需要抓取的日志 marker：
  - `power_save`
  - `protected_settings_decision`
  - `protected_surface_fast_suppress`
- 建议下一步：
  - 在荣耀设备上单独验证，不和小米问题混在一个补丁里处理。
- 当前处理策略：
  - 等荣耀设备测试后更新。
- 验证要求：
  - 开启/进入省电、超级省电、后台限制相关页面时行为符合预期。

### ISSUE-011 MIUI 应用列表页被误判为敏感卸载场景

- 状态：`Fixed`
- 优先级：`P1`
- 所属模块：Sensitive Action / MIUI SecurityCenter / App List
- 发现阶段：Phase 4 设备验证后
- 修复阶段：Phase 8 (2026-05-26)
- 发现日期：2026-05-24
- 设备/系统：
  - 小米设备
- 现象：
  - 进入“应用设置”或 MIUI 安全中心应用列表时，只要列表滑到“拉钩守护”的位置，就会被立即返回退出。
  - 用户无法继续进入“权限管理”等普通浏览页面。
  - 现象发生时动作非常快，更像敏感动作即时逃离，而不是 protected settings 的普通压制流程。
- 复现步骤：
  1. 打开 MIUI 的“应用设置”或应用列表页。
  2. 向下滚动，直到列表中出现“拉钩守护”。
  3. 观察页面立即被 `BACK/HOME` 逃离，无法继续浏览。
- 当前判断：
  - 这不是 Phase 4 解耦重构引入的回归。
  - Phase 4 主要改动 `ProtectedSurfaceGuard` / `SystemSurfaceGuard`，本问题对应的触发链路来自 `SensitiveActionGuard`，该模块不在本次解耦实施范围内。
  - 设备日志显示 `protected_settings_decision` 对 `com.android.settings` 仅为 `OBSERVE`，没有看到 protected settings 的 `BLOCK`。
  - 实际触发拦截的是 `SensitiveActionGuard`：
    - `sensitive_action_detected package=com.miui.securitycenter`
    - `sensitive_action_block package=com.miui.securitycenter`
    - `sensitive_action_escape action=1 delay=0 handled=true`
  - 更具体地说，MIUI 应用列表页同时出现“卸载”和“拉钩守护”文本，导致 `nodeMatch=true` 且 `targetAppMatch=true`，从而被误当成敏感卸载场景。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
    - `handle(...)`
    - `buildSensitiveActionSnapshot(...)`
    - `blockSensitiveAction(...)`
    - `containsSensitiveActionNodeText(...)`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
    - `routeWindowEvent(...)`
    - `routeInteractionEvent(...)`
- 需要抓取的日志 marker：
  - `sensitive_action_detected`
  - `sensitive_action_block`
  - `sensitive_action_escape`
  - `sensitive_action_skip_cooldown`
  - `protected_settings_decision`
- 修复方式：
  - 将 `SettingsSensitiveDetector.shouldBypassSettingsSelfFalsePositiveForPhase1Test()` 的包名硬编码判断改为依赖 `snapshot.isSettingsSource`。
  - 保留 `signalMatch` 和 `isDialogLike` 的判定，确保真正的卸载确认页和弹窗仍能被拦截。
  - 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/guard/SettingsSensitiveDetector.kt`
    - `shouldBypass(...)`
    - `shouldBypassSettingsSelfFalsePositiveForPhase1Test(...)`
- 验证结果：
  - 单元测试通过：`SettingsSensitiveDetectorTest` 覆盖 settings 来源判断。
  - 需要设备验证：MIUI 应用列表页可正常浏览；真正卸载确认仍能及时拦截。
- 回归风险：
  - 如果 `WhitelistManager.isSettings()` 范围扩大，可能影响其他场景的敏感动作检测。
  - 需要确认 `com.xiaomi.market` 不出现卡死回归。
- 后续处理策略：
  - 设备验证后关闭本 issue。

## 已修复但需要防复发的问题

### ISSUE-006 本应用打开后被自我拦截

- 状态：`Watch`
- 优先级：`P1`
- 所属模块：Self App Handling / App Blocking
- 发现阶段：Phase 1 seam extraction
- 现象：
  - 解耦过程中出现本应用自身打不开，打开后被拦截的问题。
- 当前处理：
  - 已通过临时开关/自我拦截 bypass 处理。
  - 设备验证结果：本应用打开通过。
- 复发风险：
  - 后续如果调整 self-app handling、白名单、protected settings、normal policy 顺序，可能再次复发。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
    - self app handling adapter
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
    - window route order
- 回归验证：
  - 每次改动 router、whitelist、normal policy 后，都要验证本应用可以正常打开。
- 备注：
  - 该问题是重构中暴露的行为风险，不能简单删除 bypass。

### ISSUE-007 com.xiaomi.market 拦截卡死

- 状态：`Watch`
- 优先级：`P1`
- 所属模块：Sensitive Action / Protected Settings / Xiaomi Market
- 发现阶段：Phase 2 sensitive action extraction
- 现象：
  - `com.xiaomi.market` 中曾出现拦截卡死问题。
- 当前处理：
  - 已在 Phase 2 修复过一次。
  - Phase 4 期间在 protected surface 提取后短暂复发过一次，表现为遮蔽层出现后无法自动释放。
  - 最新修复方式：将 `ProtectedSurfaceGuard` 的 `scheduleProtectedOverlayReleaseCheck(...)` 改为在实际发起 `showOverlay(...)` 之后再启动，避免主线程延迟显示遮蔽层时提前耗尽 release window。
  - 最新设备验证结果：应用商店拦截成功，遮蔽层出现后可自动释放，`com.xiaomi.market` 不出现卡死回归。
  - 后续 P2 收口补丁已把这条边缘路径从代码上收掉：普通 `enforceBlock(...)` 路径中的 protected surface 现在会在 `handler.post { showOverlay(...) }` 之后再启动 protected release check；`GuardAccessibilityService.scheduleOverlayReleaseCheck(...)` 对 protected surface 也只会在 overlay 已经显示且当前 blocked package 命中时才启动。
  - 这条收口补丁已经完成设备复测并通过；当前仍保留 `Watch` 状态，是因为该问题跨 Sensitive Action / Protected Surface 两条链路，且对调度时序较敏感，后续改动时仍需重点回归。
- 复发风险：
  - 后续如果扩大 sensitive action snapshot、launcher source 判断、installer/market 处理，可能再次误判。
  - 后续如果再次调整 protected surface 的 overlay 调度时序，也可能重新引入“显示后不释放”的问题。
  - “protected surface release check 只能在实际 `showOverlay(...)` 之后启动，或在 overlay 已显示时启动”现在已经作为统一约束落到代码中，后续改动需保持。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
  - `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuard.kt`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApi.kt`
  - `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- 回归验证：
  - 小米应用商店进入目标路径时能拦截，且遮蔽层出现后能自动释放，不能卡死。
  - 普通应用市场浏览不能被 sensitive action 误判。

### ISSUE-008 launcher 敏感动作范围曾被扩大

- 状态：`Watch`
- 优先级：`P1`
- 所属模块：Sensitive Action / Launcher Detection
- 发现阶段：Phase 2 收口
- 现象：
  - 解耦过程中曾把 launcher source 抽象成统一分支，导致原本更窄的 MIUI/Xiaomi 限定被扩大。
- 当前处理：
  - 已恢复到 MIUI/Xiaomi 限定范围。
  - 已收紧 launcher-specific snapshot 读取。
- 复发风险：
  - 后续重构 source classification 或 snapshot API 时，可能再次把平台特例混成通用 launcher 逻辑。
- 关键代码位置：
  - `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApi.kt`
- 回归验证：
  - MIUI 桌面敏感动作仍能拦截。
  - 非 MIUI/Xiaomi launcher 不应套用 MIUI/Xiaomi 特例。

## 结构性观察项

### ISSUE-009 Protected Settings snapshot 可能承担过多读取责任

- 状态：`Deferred`
- 优先级：`P2`
- 所属模块：Protected Settings / Snapshot
- 现象：
  - 当前 `buildSettingsPageSnapshot(...)` 会读取 event source、candidate windows、active root。
  - 这提高了识别能力，但也可能导致慢拦截和维护复杂度上升。
- 当前判断：
  - 这是“先统一，再收紧”的中间态。
  - 不应急着抽象成万能 Accessibility 读取中心。
- 建议下一步：
  - 在 Protected Settings 拆分时，定义最小 `ProtectedSettingsSnapshot` 或 snapshot builder。
  - 针对无障碍、使用情况访问、权限页分别确认最小需要信号。
- 验证要求：
  - 收紧读取范围后，不能降低当前已通过场景。

### ISSUE-010 AccessibilityEventRouter 测试覆盖仍偏骨架

- 状态：`Deferred`
- 优先级：`P3`
- 所属模块：Accessibility Event Router / Tests
- 现象：
  - Phase 3 已有 router 结构和基础测试，但测试重点更偏 `GuardActionResult` 路由停止语义。
  - 对真实 event type 分发、window route order、interaction route order 的覆盖仍有限。
- 当前判断：
  - 对 Phase 3 验收不是阻塞项。
  - 但后续拆 Protected Settings、WeChat Finder、normal policy 前，应补路由顺序测试或 adapter 级测试。
  - 当前 `ProtectedSurfaceStateTest`、`ProtectedSurfaceGuardTest`、`SystemSurfaceGuardTest` 主要覆盖了状态节流、纯 helper 和 `GuardActionResult` 映射；system panel cooldown 状态、release check 时序、router adapter 集成仍主要依赖设备验证。
  - 考虑到 Android node/window 行为不适合在 JVM 单测里完整模拟，这一状态当前可接受，但后续改动这些区域时应优先补 adapter 级测试。
- 建议下一步：
  - 增加不依赖真实 Android node 的 router adapter 顺序测试。
  - 明确验证：
    - unsupported event 返回 `Continue`
    - window route 顺序保持不变
    - interaction route 不进入 self app / WeChat Finder / normal policy
    - assistant follow-up 复用同一套 debounce state

## 当前已通过的回归场景

以下场景在最近设备验证中通过，后续涉及 router、policy、sensitive action、whitelist 时需要重复验证：

- 本应用打开通过。
- 应用商店拦截成功，且 `com.xiaomi.market` 不出现遮蔽层卡死回归。
- 普通拦截成功。
- 白名单成功。
- 应用卸载拦截成功，但速度偏慢，需后续关注。
- 使用情况访问仍能拦截。
- 悬浮窗权限能拦截。
- 小米省电模式暂未发现问题。

## 推荐日志命令

清空旧日志：

```powershell
adb logcat -c
```

观察核心日志：

```powershell
adb logcat -v time -s GuardAccessibilityService:D ProtectedSettingsPolicy:D OverlayService:D KidsPhoneGuard:D
```

重点 marker：

```text
protected_settings_decision
protected_surface_fast_suppress
settings_snapshot_event_source_failed
settings_snapshot_root_failed
settings_snapshot_windows_failed
wechat_finder
wechat_finder_block
wechat_finder_block_skip_cooldown
power_save
```

## 新问题记录模板

复制以下模板追加到对应章节：

```markdown
### ISSUE-XXX 问题标题

- 状态：`Open`
- 优先级：`P1`
- 所属模块：
- 发现阶段：
- 发现日期：
- 设备/系统：
- 现象：
  -
- 复现步骤：
  1.
  2.
  3.
- 当前判断：
  -
- 关键代码位置：
  -
- 需要抓取的日志 marker：
  -
- 修复方式：
  -
- 验证结果：
  -
- 回归风险：
  -
- 后续处理策略：
  -
```
