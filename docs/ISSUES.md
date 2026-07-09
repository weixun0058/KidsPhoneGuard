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
- **真机回归优先于新功能**：ISS-006（Phase 7 回归）是当前最高性价比收尾，优先于任何新功能。

---

## 1. 总览表（按优先级 → ID）

| ID | 优先级 | 严重性 | 状态 | 类型 | 标题 |
|------|------|------|------|------|------|
| ISS-001 | P0 | 严重 | DONE | 缺陷 | 系统时间篡改绕过 |
| ISS-002 | P1 | 严重 | IN_PROGRESS | 增强/收尾 | 设置守卫内容策略覆盖（关键词/ROM + BLOCK_ACTION 调优） |
| ISS-003 | P1 | 严重 | DONE | 缺陷 | 破坏性数据库迁移（fallbackToDestructiveMigration） |
| ISS-004 | P1 | 严重 | DONE | 技术债 | 全局锁双源 |
| ISS-005 | P1 | 严重 | DONE | 技术债 | 遮罩静态状态竞态 |
| ISS-006 | P1 | 严重 | IN_PROGRESS | 收尾回归 | Phase 7 设备回归验证 |
| ISS-007 | P2 | 中等 | IN_PROGRESS | 收尾回归 | 微信视频号识别准确性（原 ISSUE-004） |
| ISS-008 | P2 | 中等 | BLOCKED | 收尾回归 | 华为/荣耀省电模式真机验证（原 ISSUE-005） |
| ISS-009 | P2 | 中等 | OPEN | 技术债 | UI 巨型文件拆分（含抽 ViewModel） |
| ISS-010 | P2 | 中等 | OPEN | 技术债 | 无效 force-stop 路径清理 |
| ISS-011 | P2 | 中等 | OPEN | 增强 | 输入法豁免改运行时发现（完整性，非安全） |
| ISS-012 | P2 | 中等 | OPEN | 技术债 | 取证日志增长（timeline.jsonl 无上限） |
| ISS-013 | P2 | 中等 | OPEN | 技术债 | 密码明文分支淘汰计划 |
| ISS-014 | P2 | 轻微 | OPEN | 技术债 | WRITE_SECURE_SETTINGS 预留项收口 |
| ISS-015 | P3 | 中等 | OPEN | 技术债 | 测试覆盖断层 |
| ISS-016 | P3 | 轻微 | OPEN | 技术债 | 无依赖注入框架 |
| ISS-017 | P3 | 轻微 | OPEN | 增强 | 轮询优化（UsageStats 3s） |
| ISS-018 | P3 | 轻微 | OPEN | 技术债 | WhitelistManager 死代码与命名清理 |
| ISS-019 | P3 | 轻微 | DONE | 规范 | 重建 issue 台账（本文件） |
| ISS-020 | P2 | 中等 | OPEN | 产品决策 | 防卸载产品决策（是否接受儿童可卸载现状） |

> **当前进行中 / 未收尾**：ISS-002、ISS-006、ISS-007、ISS-008（BLOCKED）。详见各自详情。

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
**实际修法（2026-07-10）**：采用**纯本地可信时间方案**（不引入网络，符合 §0.8 排序约束 P0/P1 未清前不开始网络模块）：
- 新增 `utils/TrustedTimeProvider`：以 `SystemClock.elapsedRealtime()`（单调递增）为增量基准，持久化"上次已知系统时间"为锚点；
- `checkpoint()` 在 `GuardForegroundService.onCreate` 与保活循环（~10s）调用，检测**倒拨**（wallNow < lastWall − 60s 容忍）与**前拨跨午夜**（跨午夜但单调时钟增量 < 23h）；
- 检测到篡改：写 `accessibility_forensics.log` 取证 + 设置 tamper 标志 + **冻结"今日日期"**（限额累计不清零）；
- `LockDecisionEngine`：篡改期时段规则（WINDOW_ONLY/BOTH）**直接短路为 TIME_WINDOW_BLOCKED**（反向激励：改时间反而被拦）；纯时长应用累计已冻结不受影响；
- `DailyUsageRepository.getTodayDate()` 改用 `TrustedTimeProvider.trustedToday()`；
- `PasswordManager.verifyPassword()` 成功后调用 `clearTamperFlag()`（家长在场操作即解除冻结）。
**已知限制**：关机后改时间再开机无法被单调时钟验证；但启动时倒拨校验（wallNow < lastWall）可覆盖大部分场景。后续若引入网络模块可叠加 NTP 校验作为增强。
**变更记录**：
- 2026-07-09 创建（来自评价报告）。
- 2026-07-10 实现纯本地可信时间方案，状态 OPEN → DONE。

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
**建议修法**：按 MIUI/EMUI/ColorOS/OneUI 等逐 ROM 补 `targetAppKeywords`/`riskyCapabilityKeywords`/`riskyActionKeywords`/`guardianDisruptive*`；调 `BLOCK_ACTION`（只拦危险点击、页面留着）与 `BLOCK_PAGE` 粒度；优化判定响应速度。注意：**设置前缀匹配（`isSettings`）是正确的拦截触发器，保持/加宽，不要改精确匹配**（见 ISS-018 命名清理）。
**变更记录**：
- 2026-07-09 创建（进行中，持续调优）。

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
| 类型/状态 | 收尾回归 / IN_PROGRESS |
| 关联代码 | 全链路 |
| 来源 | `docs/plans/2026-05-24-phase-7-closeout-summary.md` 回归清单 |
| 负责人 | — |

**问题**：Phase 7 代码+单测通过，但真机回归清单（普通拦截/白名单/应用商店/卸载/使用情况/悬浮窗/无障碍等）尚未在真机验证。
**建议修法**：按 closeout 清单逐项真机验证，结果回填至此条"变更记录"；失败项另开新 ISS。**优先于任何新功能**（见 §0.8）。
**变更记录**：
- 2026-07-09 创建（进行中，清单待执行）。

---

## 4. P2 — 中优先级

### ISS-007 · 微信视频号识别准确性（原 ISSUE-004）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 收尾回归 / IN_PROGRESS |
| 关联代码 | `WeChatFinderGuard` |
| 来源 | Phase closeout；历史 ISSUE-004 |
| 负责人 | — |

**问题**：视频号拦截功能存在，但识别准确性待验证/调优。
**建议修法**：真机验证识别率，按需补入口关键词与冷却参数（`WECHAT_FINDER_COOLDOWN_MS` 等）。
**变更记录**：
- 2026-07-09 创建（进行中）。

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
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `ConfigActivity.kt`（2428 行）、`MainActivity.kt`（1313 行） |
| 来源 | 评价报告 §6 P2#6 / §7 第 3 条 |
| 负责人 | — |

**问题**：单文件堆叠 20+ Composable，维护与合并冲突困难。
**建议修法**：按功能域拆分为多文件；**拆分时顺手补 ViewModel + StateFlow**，为后续网络模块的远程状态注入留接口（来自报告 §7 第 3 条）。
**变更记录**：
- 2026-07-09 创建（合并了"拆 UI 同时抽 ViewModel"建议）。

### ISS-010 · 无效 force-stop 路径清理
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `AppBlockCoordinator.tryForceStopApp`（反射 `forceStopPackage` + `am force-stop` shell） |
| 来源 | 评价报告 §6 P2#7 / §8.1 |
| 负责人 | — |

**问题**：反射+shell 在非系统/非 root 下恒失败，是死代码；引入崩溃/ANR 风险与虚假安全感。
**建议修法**：删除反射与 shell 两段，拦截明确只依赖"HOME/BACK + 红屏遮罩"前台压制；补分支单测。详见报告 §8.1。
**变更记录**：
- 2026-07-09 创建。

### ISS-011 · 输入法豁免改运行时发现（完整性，非安全）
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 增强 / OPEN |
| 关联代码 | `WhitelistManager.isInWhitelist`（两个输入法前缀） |
| 来源 | 评价报告 §6 P2#8 / §8.4 |
| 负责人 | — |

**问题**：硬编码只覆盖少数键盘，漏掉三星/OPPO/vivo/魅族/荣耀自带键盘，导致这些手机打不出字（连家长密码都输不了）。
**建议修法**：运行时 `InputMethodManager.inputMethodList` 取已装输入法精确包名，带缓存 + `PACKAGE_ADDED/REMOVED` 刷新；替换两个前缀判断。**定性为完整性修复，不是防 spoofing**（普通儿童造不出冒牌包名）。
**变更记录**：
- 2026-07-09 创建。

### ISS-012 · 取证日志增长
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | PC 侧 `scripts/pc_forensics_watch.py` 的 `timeline.jsonl` |
| 来源 | 评价报告 §6 P2#9 |
| 负责人 | — |

**问题**：`accessibility_forensics.log` 有 2MB 轮转，但 `timeline.jsonl` 持续追加无上限，长期膨胀。
**建议修法**：加滚动/容量上限（如按大小或天数截断、保留最近 N 份）。
**变更记录**：
- 2026-07-09 创建。

### ISS-013 · 密码明文分支淘汰计划
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 中等 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `PasswordManager`（`KEY_LEGACY_PASSWORD` 明文读取 + 一次性迁移分支） |
| 来源 | 评价报告 §6 P2#10 |
| 负责人 | — |

**问题**：已主推 PBKDF2，但明文读取分支仍在，增加本地读取攻击面。
**建议修法**：设定版本节点，强制迁移并移除 `KEY_LEGACY_PASSWORD` 明文分支。
**变更记录**：
- 2026-07-09 创建。

### ISS-014 · WRITE_SECURE_SETTINGS 预留项收口
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P2 / 轻微 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `AndroidManifest.xml`（`WRITE_SECURE_SETTINGS` 声明） |
| 来源 | 评价报告 §6 P2#11 |
| 负责人 | — |

**问题**：声明该权限用于"ADB 授予后程序化恢复无障碍"，但无对应实现代码，无用权限可能引发审核质疑。
**建议修法**：要么补实现，要么移除声明。
**变更记录**：
- 2026-07-09 创建。

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

---

## 5. P3 — 低优先级

### ISS-015 · 测试覆盖断层
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 中等 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `LockDecisionEngine`、`PasswordManager`、`DailyUsageRepository`、`GuardForegroundService`、全部 UI |
| 来源 | 评价报告 §6 P3#12 |
| 负责人 | — |

**问题**：14 个单测覆盖协调器/守卫/路由，但引擎、密码、仓库、前台服务、UI 无测试。
**建议修法**：引擎与密码优先补测；拆 UI 时补 ViewModel 测试。
**变更记录**：
- 2026-07-09 创建。

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
| 类型/状态 | 增强 / OPEN |
| 关联代码 | `UsageTrackingManager`（3 秒轮询） |
| 来源 | 评价报告 §6 P3#15 |
| 负责人 | — |

**问题**：3 秒持续轮询耗电。
**建议修法**：夜间/熄屏降频，或向事件驱动演进。
**变更记录**：
- 2026-07-09 创建。

### ISS-018 · WhitelistManager 死代码与命名清理
| 字段 | 值 |
|------|------|
| 优先级/严重性 | P3 / 轻微 |
| 类型/状态 | 技术债 / OPEN |
| 关联代码 | `WhitelistManager`：`isLauncher`/`isPhoneApp`/`isMessagingApp`/`isCommunicationApp`（死代码）；`isSettings`/`isInstallerOrMarket`（命名误导） |
| 来源 | 评价报告 §6 P3#16 / §8.4 §8.5 |
| 负责人 | — |

**问题**：四个"家族前缀"方法零调用方；`isSettings` 等实为表面分类器却住在 `WhitelistManager`，命名是"把拦设置误读成白名单绕过"的根源。
**建议修法**：删除四个死方法；将 `isSettings`/`isInstallerOrMarket` 移出 `WhitelistManager`，或把类改名（如 `SystemSurfaceClassifier`）。**注意：`isSettings` 的前缀匹配逻辑保持不变（见 ISS-002），本条只做命名/归属清理。**
**变更记录**：
- 2026-07-09 创建。

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
