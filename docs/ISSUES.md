# 拉钩守护 · 问题与任务台账（ISSUES Ledger）

> 建立日期：2026-07-09
> 来源：`docs/项目综合评价_2026-07-09.md`（评价报告）+ 各 Phase closeout + 实时勘察
> 性质：本文件是项目的**唯一问题台账**，取代已删除的 `issues_list.md`。所有待修问题、技术债、收尾回归、产品决策均在此登记、跟踪、归档。

---

## 0. 规范与约定（先读这一节）

### 0.1 ID 规则
- 格式 `ISS-NNN`（三位数字，从 001 起，**稳定不复用**）。
- 旧文档里的 `ISSUE-004`（微信视频号）/ `ISSUE-005`（华为省电）是历史编号，已在对应条目"关联"字段标注，不与本台账 `ISS-NNN` 混淆。

### 0.2 优先级
| 级别 | 含义 |
|------|------|
| **P0** | 发布前必修。不修则产品不可发布/不可信。 |
| **P1** | 高。抗绕过核心杠杆、数据/状态一致性、关键收尾。 |
| **P2** | 中。健壮性与工程化，有 workaround，可排期。 |
| **P3** | 低。可维护性、代码卫生、认知成本。 |

### 0.3 严重性
| 级别 | 含义 |
|------|------|
| **阻塞** | 阻挡发布或核心功能不可用。 |
| **严重** | 数据丢失 / 安全绕过 / 核心体验破坏；不修则不可信。 |
| **中等** | 影响健壮性或可维护性，有规避手段。 |
| **轻微** | 代码卫生 / 认知成本，不影响功能。 |

### 0.4 状态
`OPEN`（未开始）/ `IN_PROGRESS`（进行中）/ `BLOCKED`（阻塞，须注明原因）/ `DONE`（已完成）/ `WONTFIX`（决定不做，须注明决策）
> 已完成/归档条目**保留在原位**，不物理删除，以保可追溯。

### 0.5 类型
`缺陷` / `技术债` / `增强` / `收尾回归` / `产品决策` / `规范`

### 0.6 编辑规则（可插入 / 可编辑 / 可删除）
- **新增**：取下一个 `ISS-NNN` → 在"§1 总览表"加一行 → 在对应优先级小节加详情块。
- **改状态/信息**：改详情块字段，并在该条"变更记录"**追加一行**（日期 + 动作 + 说明）；**不删除历史**。
- **删除**：原则上不物理删；确需移除时标 `WONTFIX` 并写明决策，保留条目。
- **顺序**：详情块按优先级 P0→P3 分组；同优先级内按 ID 升序。

### 0.7 修改纪律（来自评价报告 §7 第 6 条）
任何代码改动，须**先在本台账登记或引用条目**，再动手；逐条改、逐条验证、逐条更新状态，并同步更新 `README.md` / `AGENTS.md` / 评价报告相关条目，避免文档与代码再次脱节。

### 0.8 排序约束（来自评价报告 §7 第 1、5 条）
- **先补"可信"再扩"功能"**：P0/P1 未清前，不开始网络/云端模块（抗绕过与数据持久化是远程管控的地基）。
- **真机异常优先于新功能**：ISS-021（小米无障碍“假启用”）是当前最高性价比收尾，优先于任何新功能。

---

## 1. 总览表（按优先级 → ID）

| ID | 优先级 | 严重性 | 状态 | 类型 | 标题 |
|------|------|------|------|------|------|
| ISS-001 | P0 | 严重 | DONE | 缺陷 | 系统时间篡改绕过 |
| ISS-002 | P1 | 严重 | IN_PROGRESS | 增强/收尾 | 设置守卫内容策略覆盖（关键词/ROM + BLOCK_ACTION 调优） |
| ISS-003 | P1 | 严重 | DONE | 缺陷 | 破坏性数据库迁移（fallbackToDestructiveMigration） |
| ISS-004 | P1 | 严重 | DONE | 技术债 | 全局锁双源 |
| ISS-005 | P1 | 严重 | DONE | 技术债 | 遮罩静态状态竞态 |
| ISS-006 | P1 | 严重 | DONE | 收尾回归 | Phase 7 设备回归验证 |
| ISS-007 | P2 | 中等 | DONE | 收尾回归 | 微信视频号软干预（原 ISSUE-004） |
| ISS-008 | P2 | 中等 | BLOCKED | 收尾回归 | 华为/荣耀省电模式真机验证（原 ISSUE-005） |
| ISS-009 | P2 | 中等 | IN_PROGRESS | 技术债 | UI 巨型文件拆分（含抽 ViewModel） |
| ISS-010 | P2 | 中等 | DONE | 技术债 | 无效 force-stop 路径清理 |
| ISS-011 | P2 | 中等 | DONE | 增强 | 输入法豁免改运行时发现（完整性，非安全） |
| ISS-012 | P2 | 中等 | DONE | 技术债 | 取证日志增长（timeline.jsonl 无上限） |
| ISS-013 | P2 | 中等 | DONE | 技术债 | 密码明文分支淘汰计划 |
| ISS-014 | P2 | 轻微 | WONTFIX | 技术债 | WRITE_SECURE_SETTINGS 预留项收口 |
| ISS-015 | P3 | 中等 | IN_PROGRESS | 技术债 | 测试覆盖断层 |
| ISS-016 | P3 | 轻微 | OPEN | 技术债 | 无依赖注入框架 |
| ISS-017 | P3 | 轻微 | DONE | 增强 | 轮询优化（UsageStats 3s） |
| ISS-018 | P3 | 轻微 | DONE | 技术债 | 系统受保护表面分类器拆分 |
| ISS-019 | P3 | 轻微 | DONE | 规范 | 重建 issue 台账（本文件） |
| ISS-020 | P2 | 中等 | OPEN | 产品决策 | 防卸载产品决策（是否接受儿童可卸载现状） |
| ISS-021 | P1 | 严重 | IN_PROGRESS | 兼容性缺陷 | 小米无障碍“设置已启用但服务未绑定” |

> **当前未收尾**：ISS-002、ISS-008（BLOCKED）、ISS-009、ISS-015、ISS-016、ISS-020、ISS-021。详见各自详情。

---

## 2. P0 — 发布前必修

### ISS-001 · 系统时间篡改绕过
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P0 / 严重 |
| 类型/状态 | 缺陷 / DONE |
| 关联代码 | `LockDecisionEngine`（`LocalTime.now()` / `LocalDate.now()`）；新增 `TrustedTimeProvider` |
| 来源 | 评价报告 §6 P0#1 |
| 负责人 | — |

**问题**：时段禁用与每日限额重置均依赖设备系统时间。儿童改系统时间/日期即可绕过限额与禁用时段。
**建议修法**：引入网络时间校验（NTP/网络回包时间），或对系统时间倒拨做异常取证与冻结累计时长。需注意无网时的降级策略。
**实际修法（2026-07-10，2026-07-11 复审修正）**：采用**纯本地可信时间方案**（不引入网络，符合 §0.8 排序约束 P0/P1 未清前不开始网络模块）：
- 新增 `utils/TrustedTimeProvider`：以 `SystemClock.elapsedRealtime()`（单调递增）为增量基准，持久化"上次已知系统时间"为锚点；
- `checkpoint()` 在 `GuardForegroundService.onCreate` 与保活循环（~10s）调用，比较墙钟增量与 `elapsedRealtime()` 增量；两者偏差超过 60 秒才识别为前拨或倒拨，正常跨午夜不会误报；
- 检测到篡改：写 `accessibility_forensics.log` 取证 + 设置 tamper 标志 + **冻结"今日日期"**（限额累计不清零）；
- `LockDecisionEngine`：篡改期时段规则（WINDOW_ONLY/BOTH）**直接短路为 TIME_WINDOW_BLOCKED**（反向激励：改时间反而被拦）；纯时长应用累计已冻结不受影响；
- `DailyUsageRepository.getTodayDate()` 改用 `TrustedTimeProvider.trustedToday()`；
- `PasswordManager.verifyPassword()` 成功后调用 `clearTamperFlag()`（家长在场操作即解除冻结）。
**已知限制**：设备重启会使 `elapsedRealtime()` 归零；重启后仍能识别倒拨，但无法可靠区分“关机期间正常过夜”和“关机后前拨时间”。后续若引入网络模块可叠加 NTP 校验作为增强。
**变更记录**：
- 2026-07-09 创建（来自评价报告）。
- 2026-07-10 实现纯本地可信时间方案，状态 OPEN → DONE。
- 2026-07-11 复审发现“跨午夜且 elapsed < 23h”会误报；改为增量偏差检测，并新增正常跨午夜、前拨、倒拨、重启边界单测。

---

## 3. P1 — 高优先级

### ISS-002 · 设置守卫内容策略覆盖（攻防核心）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P1 / 严重 |
| 类型/状态 | 增强·收尾回归 / IN_PROGRESS |
| 关联代码 | `ProtectedSettingsPolicy`、`BrandSettingsRules`、`ProtectedSurfaceGuard` |
| 来源 | 评价报告 §6 P1#2 / §8.4；原"保护设置关键词与响应速度调优"ISSUE |
| 负责人 | — |

**问题**：防"小孩进设置关权限/开省电破防"的真正杠杆是逐页内容判定（`ALLOW/OBSERVE/BLOCK_ACTION/BLOCK_PAGE`），需持续补关键词与 ROM 覆盖。当前覆盖不全、粒度待调、响应速度待优化。
**建议修法**：按 MIUI/EMUI/ColorOS/OneUI 等逐 ROM 补 `targetAppKeywords`/`riskyCapabilityKeywords`/`riskyActionKeywords`/`guardianDisruptive*`；调 `BLOCK_ACTION`（只拦危险点击、页面留着）与 `BLOCK_PAGE` 粒度；优化判定响应速度。注意：**设置前缀匹配（`SystemSurfaceClassifier.isSettingsSurface`）是正确的拦截触发器，保持/加宽，不要改精确匹配**（见 ISS-018）。
**变更记录**：
- 2026-07-09 创建（进行中，持续调优）。
- 2026-07-11 将 `ProtectedSettingsPolicy` 的 Android 状态读取与纯决策逻辑拆分为 `ProtectedSettingsDecisionEngine`；新增 JVM 决策矩阵，锁定全局解锁、设置向导、守护省电、Huawei 页面、SystemUI 瞬态页及目标应用四级决策的既有优先级。未凭猜测新增厂商关键词/包名，ROM 真机覆盖和响应速度验证仍待 ISS-006/ISS-008 回归。

### ISS-003 · 破坏性数据库迁移
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P1 / 严重 |
| 类型/状态 | 缺陷 / DONE |
| 关联代码 | `AppDatabase`（`MIGRATION_1_2`、`addMigrations`、`exportSchema=true`） |
| 来源 | 评价报告 §6 P1#3 / §8.3 |
| 负责人 | — |

**问题**：schema 变更会清空全部规则与使用记录；v1→v2 已静默清空过一次用户数据（实锤，提交 `9e536a4`）。
**建议修法**：删 `fallbackToDestructiveMigration()`，加 `addMigrations(MIGRATION_1_2)`（`ALTER TABLE app_rules ADD COLUMN limitMode INTEGER NOT NULL DEFAULT 0`）；开 `exportSchema=true` 并配 `room.schemaDirectory`；补 `MigrationTestHelper` 的 androidTest。详见报告 §8.3。
**实际修法（2026-07-10）**：
- `AppDatabase`：删除 `fallbackToDestructiveMigration()`；新增 `MIGRATION_1_2`（`ALTER TABLE app_rules ADD COLUMN limitMode INTEGER NOT NULL DEFAULT 0`）并通过 `addMigrations(MIGRATION_1_2)` 注册；`exportSchema` 改为 `true`；
- `app/build.gradle.kts`：新增 `ksp { arg("room.schemaLocation", "${projectDir}/schemas") }` 配套 schema 导出；新增 `androidTestImplementation("androidx.room:room-testing:2.6.1")`；
- `app/src/androidTest/.../AppDatabaseMigrationTest`：验证 MIGRATION_1_2 在 v1 表结构上执行后 `limitMode` 列存在且默认值为 0（BOTH）。因 v1 schema 此前未导出，采用手动建 v1 表 + 执行迁移 SQL 的方式；完整 MigrationTestHelper 校验留待 ISS-015 补齐历史 schema JSON 后升级。
**变更记录**：
- 2026-07-09 创建（已发生实锤，非未来隐患）。
- 2026-07-10 实现显式迁移，移除 fallbackToDestructiveMigration，状态 OPEN → DONE。

### ISS-004 · 全局锁双源
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P1 / 严重 |
| 类型/状态 | 技术债 / DONE |
| 关联代码 | `LockDecisionEngine`、`AppRuleDao`、`AppRuleRepository`、`AppRule.isGlobalLocked` |
| 来源 | 评价报告 §6 P1#4 |
| 负责人 | — |

**问题**：全局锁存在两个来源，引擎同时判断两者，可能出现不一致。
**建议修法**：统一为单一真相源（建议以 `SettingsManager` 为准，`AppRule.isGlobalLocked` 退役或改为派生值），并补单测覆盖。
**实际修法（2026-07-10）**：统一以 `SettingsManager.isGlobalLockEnabled()` 为单一真相源：
- `LockDecisionEngine`：移除 `|| (rule?.isGlobalLocked == true)` 判断，只判 `settingsManager.isGlobalLockEnabled()`；
- 清理死代码：删除 `AppRuleDao.updateGlobalLock` / `setGlobalLockForAll` 与 `AppRuleRepository.updateGlobalLock` / `setGlobalLockForAll`（均无调用方）；同步更新 `AppRuleRepositoryTest` 的 FakeAppRuleDao；
- `AppRule.isGlobalLocked` 字段保留（避免破坏性 DB 迁移），注释标记退役，不再作为拦截判断依据；
- 单测：引擎当前为 private 构造 + Android 依赖，难直接单测；统一后的全局锁判定单测随 ISS-015（引擎可测化）补齐。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 统一为 SettingsManager 单一真相源，清理死代码，状态 OPEN → DONE。

### ISS-005 · 遮罩静态状态竞态
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P1 / 严重 |
| 类型/状态 | 技术债 / DONE |
| 关联代码 | `OverlayCoordinator`、`OverlayService`、`UsageTrackingManager`、`GuardAccessibilityService` |
| 来源 | 评价报告 §6 P1#5 |
| 负责人 | — |

**问题**：遮罩显隐状态多入口读写，虽有 `synchronized` 与 `OverlayCoordinator` 收口，但"谁有权改状态"未集中，存在闪烁/漏隐藏竞态窗口。
**建议修法**：集中到单一 `OverlayCoordinator` 读写入口，移除外部对 `OverlayService.showOverlay/hideOverlay` 的直接调用。
**实际修法（2026-07-10）**：将 `OverlayService` 的所有外部读写收口到 `OverlayCoordinator`：
- `OverlayCoordinator` 新增读入口 `isShowing()` / `currentBlockedPackage()`（委托 `OverlayService`）；
- `UsageTrackingManager`：遮罩写调用从直接 `OverlayService.showOverlay` 改为 `OverlayCoordinator.showOverlay`（持有协调器实例，start/stop 跟踪生命周期）；
- `GuardAccessibilityService`：4 处读 lambda（`OverlayService.isOverlayShowing` / `getCurrentBlockedPackage`）全部改为 `overlayCoordinator.isShowing()` / `currentBlockedPackage()`；
- 收口后，全项目仅 `OverlayCoordinator` 直接调用 `OverlayService`，外部一律经协调器，"谁有权改状态"集中。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 完成 OverlayService 读写收口到 OverlayCoordinator，状态 OPEN → DONE。

### ISS-006 · Phase 7 设备回归验证
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P1 / 严重 |
| 类型/状态 | 收尾回归 / DONE |
| 关联代码 | 全链路 |
| 来源 | `docs/plans/2026-05-24-phase-7-closeout-summary.md` 回归清单 |
| 负责人 | — |

**问题**：Phase 7 代码+单测通过，但真机回归清单（普通拦截/白名单/应用商店/卸载/使用情况/悬浮窗/无障碍等）尚未在真机验证。
**建议修法**：按 closeout 清单逐项真机验证，结果回填至此条"变更记录"；失败项另开新 ISS。**优先于任何新功能**（见 §0.8）。
**变更记录**：
- 2026-07-09 创建（进行中，清单待执行）。
- 2026-07-11 · Redmi 23049RAD8C（Xiaomi / Android 15）：已验证规则模式下 `com.easybrain.jigsaw.puzzles` 的“永久禁用”规则。该 Unity 应用冷启动只提供 `TYPE_WINDOW_CONTENT_CHANGED`，旧路由不会进入普通策略检查；新增“新包名内容事件”兜底后，真机日志确认 `APP_BLOCKED`，并执行 BACK / HOME 返回桌面。新增路由状态单测，`AccessibilityEventRouterTest` 7/7 通过。
- 2026-07-12 · Redmi 23049RAD8C（Xiaomi / Android 15）完成适用清单：本应用与无障碍服务真实绑定、普通应用（Jigsaw Puzzles）拦截、系统桌面白名单过渡、`com.xiaomi.market` 应用商店拦截、Jigsaw 卸载入口、使用情况访问页、悬浮窗全局列表均已真机验证。应用商店最初被页面观察路径抢占，已改为安装器/市场优先走普通锁定决策；悬浮窗页原漏掉小米“显示在其他应用的上层”全局列表，已在非设置向导状态整页拦截。相关 JVM 测试与 debug 构建均通过，状态 IN_PROGRESS → DONE。
- 微信视频号具体入口未在本轮进入，保持 ISS-007；华为/荣耀设备不在本轮范围，保持 ISS-008（BLOCKED）。

---

## 4. P2 — 中优先级

### ISS-007 · 微信视频号软干预（原 ISSUE-004）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 收尾回归 / DONE |
| 关联代码 | `WeChatFinderGuard` |
| 来源 | Phase closeout；历史 ISSUE-004 |
| 负责人 | — |

**问题**：原实现依赖无障碍事件类名并立即遮罩/返回，微信实际播放时常只发内容变化事件，导致识别不稳定且会过度干预。
**实际修法**：不阻断微信，不显示遮罩；通过 `UsageStatsManager` 的微信 `ACTIVITY_RESUMED` 记录确认 `com.tencent.mm.plugin.finder.*` 为前台，2 秒后再次确认仍在 Finder 才执行一次 BACK。活动记录采用首次 90 秒回看、后续增量查询，避免高频无障碍事件反复扫描长时间历史；普通聊天、朋友圈和小程序不以内容刷新频率作为触发条件。
**变更记录**：
- 2026-07-09 创建（进行中）。
- 2026-07-12 · Redmi 23049RAD8C（Xiaomi / Android 15）真机回归通过：视频号可在约 2～3 秒内软返回，微信其他功能不显示遮罩且不被整体拦截。用户确认“基本达到预期”；识别依据为 Finder 前台 Activity，而非逐帧媒体播放状态，微信未来改动 Activity 结构时另开兼容性 ISSUE。状态 IN_PROGRESS → DONE。

### ISS-008 · 华为/荣耀省电模式真机验证（原 ISSUE-005）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 收尾回归 / BLOCKED（缺真机） |
| 关联代码 | `oem/HuaweiPowerSaveHandler`、`ProtectedSettingsPolicy`（省电关键词） |
| 来源 | Phase closeout；历史 ISSUE-005 |
| 负责人 | — |

**问题**：华为/荣耀省电模式处理代码已写，缺真机验证。
**建议修法**：取得华为/荣耀真机后回归；无设备则保留现状并在变更记录注明。
**变更记录**：
- 2026-07-09 创建（BLOCKED：缺真机）。

### ISS-009 · UI 巨型文件拆分（含抽 ViewModel）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / IN_PROGRESS |
| 关联代码 | `ConfigActivity.kt`（基线 2428 行，当前 2110 行）、`MainActivity.kt`（1313 行） |
| 来源 | 评价报告 §6 P2#6 / §7 第 3 条 |
| 负责人 | — |

**问题**：单文件堆叠 20+ Composable，维护与合并冲突困难。
**建议修法**：按功能域拆分为多文件；**拆分时顺手补 ViewModel + StateFlow**，为后续网络模块的远程状态注入留接口（来自报告 §7 第 3 条）。
**变更记录**：
- 2026-07-09 创建（合并了"拆 UI 同时抽 ViewModel"建议）。
- 2026-07-12 启动第一阶段：新增 `ui/config/ConfigViewModel` + `ConfigUiState`，由 `StateFlow` 统一观察规则、当天用量和临时奖励，并将新增/修改/删除/批量应用/临时加时的仓库写入从 `ConfigScreen` 收口到 ViewModel；弹窗开关和视图模式仍是 Compose 瞬态状态。`ConfigActivity` 由 2428 行降至 2371 行，余下展示组件与 `MainActivity` 尚未拆分，故状态 OPEN → IN_PROGRESS，不能收尾。
- 2026-07-12 第二阶段：将应用选择、搜索及列表/图标展示迁至 `ui/config/AppSelectorComponents.kt`，`AddRuleDialog` 保持原有调用签名与选择结果不变；`ConfigActivity` 进一步降至 2110 行。批量规则、单规则编辑和 `MainActivity` 尚待拆分，状态维持 IN_PROGRESS。
- 2026-07-12 第三阶段（规则展示预处理）：将限时规则的已用/剩余/临时奖励文案提取为 `ui/config/RuleUsageFormatter`，规则卡片和编辑对话框改为调用该纯 Kotlin 组件；新增 3 个 JVM 用例覆盖时长限制、仅时段限制和非限时规则。规则卡片、编辑器与批量配置的 Compose 文件拆分仍待后续阶段，状态维持 IN_PROGRESS。
- 2026-07-12 第四阶段（编辑器预处理）：将时段的解析、格式化和分钟归一化提取为 `ui/config/TimeWindowCodec`，单规则与批量规则共用同一编解码入口；新增 JVM 用例覆盖首段选择、错误回退和跨日归一化。当前 `ConfigActivity` 为 2028 行；规则卡片、编辑器、批量配置与 `MainActivity` 的 Compose 文件拆分仍未完成，状态维持 IN_PROGRESS。

### ISS-010 · 无效 force-stop 路径清理
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / DONE |
| 关联代码 | `AppBlockCoordinator.tryForceStopApp` |
| 来源 | 评价报告 §6 P2#7 / §8.1 |
| 负责人 | — |

**问题**：反射+shell 在非系统/非 root 下恒失败，是死代码；引入崩溃/ANR 风险与虚假安全感。
**建议修法**：删除反射与 shell 两段，拦截明确只依赖"HOME/BACK + 红屏遮罩"前台压制；补分支单测。详见报告 §8.1。
**实际修法（2026-07-10）**：
- `AppBlockCoordinator.tryForceStopApp`：删除反射 `ActivityManager.forceStopPackage` 与 `Runtime.exec("am force-stop")` 两段死代码（非系统/非 root 恒 SecurityException/失败）；
- 删除仅服务于反射分支的 `forceStopPermissionDenied` 标志字段；
- 保留合法范围内的 `appTasks.finishAndRemoveTask`（Lollipop+）与 `killBackgroundProcesses`（需 `KILL_BACKGROUND_PROCESSES` 权限，已声明）；`Process.killProcess`（<O）保留作低版本兼容；
- 拦截明确只依赖 HOME/BACK + 红屏遮罩前台压制；分支单测随 ISS-015 补（`tryForceStopApp` 为 private 且依赖 Android `ActivityManager`，需可测化改造）。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 移除反射与 shell 死代码，清理 forceStopPermissionDenied，状态 OPEN → DONE。

### ISS-011 · 输入法豁免改运行时发现（完整性，非安全）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 增强 / DONE |
| 关联代码 | `WhitelistManager.isInWhitelist`、`GuardForegroundService`（输入法缓存刷新） |
| 来源 | 评价报告 §6 P2#8 / §8.4 |
| 负责人 | — |

**问题**：硬编码只覆盖少数键盘，漏掉三星/OPPO/vivo/魅族/荣耀自带键盘，导致这些手机打不出字（连家长密码都输不了）。
**建议修法**：运行时 `InputMethodManager.inputMethodList` 取已装输入法精确包名，带缓存 + `PACKAGE_ADDED/REMOVED` 刷新；替换两个前缀判断。**定性为完整性修复，不是防 spoofing**（普通儿童造不出冒牌包名）。
**实际修法（2026-07-10，2026-07-11 复审修正）**：
- `WhitelistManager`：新增 `inputMethodPackages` 缓存 + `refreshInputMethodCache(context)`（用 `InputMethodManager.inputMethodList` 取已装输入法精确包名）；`isInWhitelist` 仅查询缓存，不再在热路径或 JVM 单测中访问 Android 单例；
- `KidsPhoneGuardApp.onCreate`：进程启动即初始化缓存；`GuardForegroundService` 在启动与包变更（ADDED/REMOVED/CHANGED/REPLACED/RESTARTED）时刷新；
- 运行时缓存已就绪时仅精确匹配；旧 AOSP/Gboard 前缀仅在首次初始化/刷新失败的降级阶段生效。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 实现运行时输入法发现 + 缓存刷新，状态 OPEN → DONE。
- 2026-07-11 复审发现懒加载异常路径调用 Android `Log`，使 4 个 JVM 单测失败；改为缓存初始化与纯查询分离，并补精确匹配/降级边界单测。

### ISS-012 · 取证日志增长
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / DONE |
| 关联代码 | PC 侧 `scripts/pc_forensics_watch.py` 的 `timeline.jsonl` |
| 来源 | 评价报告 §6 P2#9 |
| 负责人 | — |

**问题**：`accessibility_forensics.log` 有 2MB 轮转，但 `timeline.jsonl` 持续追加无上限，长期膨胀。
**建议修法**：加滚动/容量上限（如按大小或天数截断、保留最近 N 份）。
**实际修法（2026-07-10）**：`scripts/pc_forensics_watch.py` 的 `append_timeline` 增加按大小轮转：
- 新增常量 `TIMELINE_MAX_BYTES = 5MB`（与设备端 `accessibility_forensics.log` 的 2MB 轮转量级一致）；
- 每次 append 前检查 `timeline.jsonl` 大小，超过阈值时滚动为 `timeline.prev.jsonl`（删除旧 prev，仅保留最近一份历史）；
- 轮转失败不中断追加（`OSError` 吞掉，取证优先）。
**已知限制**：轮转后 `timeline.prev.jsonl` 不参与 `read_recent_timeline_entries` 的 incident window 读取（5MB 通常覆盖足够长时间窗口，权衡取舍）。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 实现按大小轮转，状态 OPEN → DONE。

### ISS-013 · 密码明文分支淘汰计划
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / DONE |
| 关联代码 | `PasswordManager`（`KEY_LEGACY_PASSWORD` 明文读取 + 一次性迁移分支） |
| 来源 | 评价报告 §6 P2#10 |
| 负责人 | — |

**问题**：已主推 PBKDF2，但明文读取分支仍在，增加本地读取攻击面。
**建议修法**：设定版本节点，强制迁移并移除 `KEY_LEGACY_PASSWORD` 明文分支。
**实际修法（2026-07-10，2026-07-11 复审修正）**：
- `PasswordManager.verifyPassword`：移除 `KEY_LEGACY_PASSWORD` 明文读取与一次性迁移分支，只支持 PBKDF2 hash 验证；
- `PasswordManager.hasPasswordConfigured`：仅 PBKDF2 hash 算已配置；但应用初始化会在任何 UI 判断前完成旧格式迁移；
- `PasswordManager.migrateLegacyPasswordIfNeeded()`：已有 hash 时只删除冗余明文；仅存明文时先写入 PBKDF2 hash、成功后再删除明文；迁移异常时保留明文并记录异常，下一次启动可重试；
- `KEY_LEGACY_PASSWORD` 常量保留仅用于迁移识别与 `resetToDefault` 清理，不再用于运行时验证。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 移除明文验证分支 + 启动清理残留明文，状态 OPEN → DONE。
- 2026-07-11 复审发现“仅存明文直接删除”会让儿童有机会重设家长密码；改为先哈希迁移再删除，并新增迁移决策单测。

### ISS-014 · WRITE_SECURE_SETTINGS 预留项收口
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 轻微 |
| 类型/状态 | 技术债 / WONTFIX（经核实有实现，决定保留声明） |
| 关联代码 | `AndroidManifest.xml`（`WRITE_SECURE_SETTINGS` 声明）、`DegradedLockManager.tryProgrammaticRecovery` |
| 来源 | 评价报告 §6 P2#11 |
| 负责人 | — |

**问题**：声明该权限用于"ADB 授予后程序化恢复无障碍"，但无对应实现代码，无用权限可能引发审核质疑。
**建议修法**：要么补实现，要么移除声明。
**核实与决策（2026-07-10）**：原台账"无对应实现代码"的判断**有误**。实际存在完整实现：
- `DegradedLockManager.tryProgrammaticRecovery(context)`：检查 `WRITE_SECURE_SETTINGS` 是否已授予（ADB 授予场景），若已授予则通过 `Settings.Secure.putString`/`putInt` 程序化恢复无障碍服务；
- 该方法被降级锁屏的"恢复按钮"调用（`DegradedLockManager` 恢复按钮 onClickListener，先尝试静默恢复，失败再跳转无障碍设置页）。
- 即权限声明有对应实现，属于"高级用户/企业版经 ADB 授予后可用"的合理预留，非无用权限。
**决策**：保留 `WRITE_SECURE_SETTINGS` 声明（WONTFIX），不移除。同时更正 README/AGENTS 中"预留"措辞为"已实现"。
**变更记录**：
- 2026-07-09 创建（基于"无实现"判断）。
- 2026-07-10 经核实 DegradedLockManager 已有实现，决定保留声明，状态 OPEN → WONTFIX。

### ISS-020 · 防卸载产品决策
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 产品决策 / OPEN |
| 关联代码 | （Phase 8 已废弃并移除运行时防卸载代码） |
| 来源 | 评价报告 §7 第 4 条 |
| 负责人 | — |

**问题**：当前**无防卸载能力**（Phase 8 有意降级）。需确认是否接受"儿童可卸载 App"这一现状。
**建议修法（决策）**：若接受 → 标 `WONTFIX` 并注明；若不接受 → 规划替代方案（如设备所有者/Device Policy Manager），**新建独立 ISS** 跟踪实现（属尚未开始功能，本台账暂不纳入实现条目）。
**变更记录**：
- 2026-07-09 创建（决策待定）。

### ISS-021 · 小米无障碍“设置已启用但服务未绑定”
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P1 / 严重 |
| 类型/状态 | 兼容性缺陷 / IN_PROGRESS |
| 关联代码 | `GuardForegroundService`、`PermissionManager`、`DegradedLockManager`、`AccessibilityOperationalState` |
| 来源 | Redmi 23049RAD8C（Xiaomi / Android 15）Phase 7 真机回归 |

**问题**：小米系统曾将 `com.kidsphoneguard/.service.GuardAccessibilityService` 保留在 `enabled_accessibility_services` 中，设置界面也显示已启用，但 `dumpsys accessibility` 的 Bound services 没有本服务；此时不会收到任何无障碍事件。
**已修复代码**：将“实际可用”统一定义为“设置已启用 + 服务运行 + 心跳未过期”。前台守护、权限状态与降级锁均改用该判定；失联状态会记录 `accessibility_operational_loss` 并进入降级保护，恢复实际运行后才解除。`AccessibilityOperationalStateTest` 覆盖未绑定、心跳过期和正常运行三种形态。
**待完成验证**：需等待该 MIUI 异常再次自然复现，以确认前台守护在“设置仍勾选、服务未绑定”时实际展示降级锁并能引导恢复；不能以已重新绑定的正常状态替代该验证。

---

## 5. P3 — 低优先级

### ISS-015 · 测试覆盖断层
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 中等 |
| 类型/状态 | 技术债 / IN_PROGRESS |
| 关联代码 | `LockDecisionEngine`、`PasswordManager`、`DailyUsageRepository`、`GuardForegroundService`、全部 UI |
| 来源 | 评价报告 §6 P3#12 |
| 负责人 | — |

**问题**：14 个单测覆盖协调器/守卫/路由，但引擎、密码、仓库、前台服务、UI 无测试。
**建议修法**：引擎与密码优先补测；拆 UI 时补 ViewModel 测试。
**本次进展（2026-07-10）**：
- `LockDecisionEngine.isInBlockedTimeWindow` 提取为 `companion internal` 纯逻辑方法（接收 `timeWindows` + `now` 参数），实例方法委托；
- 新增 `LockDecisionEngineTimeWindowTest`（14 个用例）：覆盖常规窗口/跨午夜窗口/全天窗口/多窗口/边界/非法格式/空字符串。
- 2026-07-11：`ProtectedSettingsDecisionEngineTest` 新增 9 个用例，覆盖 `ALLOW`、`OBSERVE`、`BLOCK_PAGE`、`BLOCK_ACTION` 与守护省电、全局解锁、设置向导、Huawei/SystemUI 优先级；`testDebugUnitTest` 已通过（9/9，零失败）。
**待后续**：
- `PasswordManager`、`DailyUsageRepository`、`SettingsManager` 等依赖 `SharedPreferences`/`Context`，纯 JUnit 难测，需引入 Robolectric 或随 ISS-016（DI/可测化）补；
- `GuardForegroundService` 与 UI 测试随 ISS-009（UI 拆分 + ViewModel）补。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 补 LockDecisionEngine 时段判定 14 个单测，状态 OPEN → IN_PROGRESS。

### ISS-016 · 无依赖注入框架
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 轻微 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `GuardAccessibilityService`（组合根）、`AppBlockCoordinator`（~30 构造参数） |
| 来源 | 评价报告 §6 P3#13 |
| 负责人 | — |

**问题**：手动装配在协作者 ~30 参数规模下已脆弱。
**建议修法**：引入 Hilt/Koin，或至少构造配置数据类收敛参数。
**变更记录**：
- 2026-07-09 创建。

### ISS-017 · 轮询优化
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 轻微 |
| 类型/状态 | 增强 / DONE |
| 关联代码 | `UsageTrackingManager`（3 秒轮询） |
| 来源 | 评价报告 §6 P3#15 |
| 负责人 | — |

**问题**：3 秒持续轮询耗电。
**建议修法**：夜间/熄屏降频，或向事件驱动演进。
**实际修法（2026-07-10）**：熄屏降频（渐进式，未做事件驱动大改）：
- `UsageTrackingManager` 新增 `SCREEN_OFF_POLLING_INTERVAL = 10s`；
- 轮询循环按 `isScreenInteractive` 选间隔：亮屏 3s、熄屏 10s；
- 熄屏时 `trackUsage` 仍 early return（reset state），但 delay 拉长到 10s，节电约 3 倍；
- 心跳仍每 10s touch（`usageHeartbeatTimeoutMs = 20s`），不触发健康误报。
**后续**：完全事件驱动（基于 `UsageEvents` 回调替代轮询）属较大改造，留后续。
**变更记录**：
- 2026-07-09 创建。
- 2026-07-10 熄屏降频到 10s，状态 OPEN → DONE。

### ISS-018 · 系统受保护表面分类器拆分
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 轻微 |
| 类型/状态 | 技术债 / DONE |
| 关联代码 | `WhitelistManager`（仅豁免白名单）；`SystemSurfaceClassifier`（设置/安装器/应用市场分类） |
| 来源 | 评价报告 §6 P3#16 / §8.4 §8.5 |
| 负责人 | — |

**问题**：四个"家族前缀"方法零调用方；设置、安装器、应用市场分类器住在 `WhitelistManager` 中，曾导致维护者把“拦截表面”误读为“白名单放行”。
**实际修法（2026-07-11）**：删除 `isLauncher`/`isPhoneApp`/`isMessagingApp`/`isCommunicationApp` 与未使用集合；新增 `SystemSurfaceClassifier`，迁移 `isSettingsSurface`、`isInstallerOrMarketSurface`、`isPackageInstallerSurface`、`isAppMarketSurface` 及所有调用方；补子包命中、substring spoof 拒绝、安装器/市场分离单测。**设置前缀匹配逻辑保持不变（见 ISS-002），仅完成命名/归属清理。**
**变更记录**：
- 2026-07-09 创建。
- 2026-07-11 完成死代码删除与分类器拆分，状态 OPEN → DONE。

### ISS-019 · 重建 issue 台账（本文件）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 轻微 |
| 类型/状态 | 规范 / DONE |
| 关联代码 | `docs/ISSUES.md` |
| 来源 | 评价报告 §6 P3#14 / §7 第 2 条 |
| 负责人 | — |

**问题**：旧 `issues_list.md` 已删除，issue 状态散落各 phase 文档。
**建议修法**：以本文件为唯一台账，按 §0 规范维护。
**变更记录**：
- 2026-07-09 台账建立（初始 20 条）。

---

## 6. 暂不纳入本台账（尚未开始，仅备案）

> 以下为"尚未开始"的功能模块，按用户要求暂不纳入 active 台账。需启动时，为每个新建 ISS 并排期；启动前受 §0.8 排序约束（P0/P1 未清前不开始）。

- **第二阶段网络/云端模块**：家长远程管控、跨设备同步、远程下发规则——当前无任何网络代码。
- **防卸载功能开发**：Phase 8 已废弃；是否重启取决于 ISS-020 决策。
- **多子女 / 多 profile 支持**：数据模型无 profile 维度。
- **统计报表 / 可视化**：有原始数据（DailyUsage 30 天清理），无报表 UI。

---

*本台账基于 2026-07-09 代码快照与评价报告建立。新增/修改请遵循 §0 规范，保持可追溯。*
