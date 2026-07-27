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

| 级别     | 含义                          |
| ------ | --------------------------- |
| **P0** | 发布前必修。不修则产品不可发布/不可信。        |
| **P1** | 高。抗绕过核心杠杆、数据/状态一致性、关键收尾。    |
| **P2** | 中。健壮性与工程化，有 workaround，可排期。 |
| **P3** | 低。可维护性、代码卫生、认知成本。           |

### 0.3 严重性

| 级别     | 含义                           |
| ------ | ---------------------------- |
| **阻塞** | 阻挡发布或核心功能不可用。                |
| **严重** | 数据丢失 / 安全绕过 / 核心体验破坏；不修则不可信。 |
| **中等** | 影响健壮性或可维护性，有规避手段。            |
| **轻微** | 代码卫生 / 认知成本，不影响功能。           |

### 0.4 状态

`OPEN`（未开始）/ `IN_PROGRESS`（进行中）/ `BLOCKED`（阻塞，须注明原因）/ `PENDING_USER_ACCEPTANCE`（实现或修复已存在，等待用户人工验收）/ `DONE`（用户已明确人工验收通过）/ `WONTFIX`（用户已明确决定不做，须注明决策）

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
- **真机异常优先于新功能**：ISS-021（小米无障碍“假启用”）已有实现和自动化记录，但须重新由用户人工验收；后续同类异常仍须先于新功能处理。

### 0.9 验收权限与 2026-07-21 状态重置

- 单元测试、仪器测试、模拟器、ADB、日志、UI 树和代码检查只属于**开发自检或排障证据**，不能单独作为最终验收、收尾或关单依据。
- 涉及真实行为、安全性、响应速度和使用体验的结论，必须由用户亲自操作并明确表示“通过”后，才能标记 `DONE`。响应速度是否可接受，以用户实际体验和用户确认的口径为准。
- `WONTFIX` 属于产品决策，只能由用户明确决定；代码检查或助手判断不能代替该决策。
- 2026-07-21 起，凡此前仅由助手判断、自动化、模拟器、ADB/UI 树或代码检查支持的 `DONE`/`WONTFIX`，其关单效力一律撤销，改为 `PENDING_USER_ACCEPTANCE`。历史测试记录保留，仅供开发与排障使用。
- 本次已重置 19 条：ISS-001、ISS-003、ISS-004、ISS-005、ISS-006、ISS-009、ISS-010、ISS-011、ISS-012、ISS-013、ISS-014、ISS-015、ISS-016、ISS-017、ISS-018、ISS-019、ISS-021、ISS-022、ISS-023。
- 例外仅有：ISS-007（用户已明确确认“基本达到预期”，保留 `DONE`）和 ISS-008（用户已明确调整产品范围，保留 `WONTFIX`）。

### 0.10 统一术语与 UI 标识

- `docs/统一术语表与UI映射.md` 是产品、开发、测试、客服和 AI 协作的唯一标准术语与 UI 清单。
- 新增 issue、排障记录和验收反馈优先引用其中的稳定 ID，例如 `OVL-02 / PASSWORD-INPUT`。
- 新增、删除或重命名用户可见页面、对话框、悬浮层、通知或有业务含义的 UI 元素时，必须在同一变更中同步术语表。

---

## 1. 总览表（按优先级 → ID）

| ID                                  | 优先级 | 严重性 | 状态          | 类型    | 标题                                       |
| ----------------------------------- | --- | --- | ----------- | ----- | ---------------------------------------- |
| ISS-001                             | P0  | 严重  | PENDING_USER_ACCEPTANCE | 缺陷    | 系统时间篡改绕过                                 |
| ISS-002 | P1  | 严重  | IN_PROGRESS | 增强/收尾 | 设置守卫内容策略覆盖（关键词/ROM + BLOCK_ACTION 调优）    |
| ISS-003                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 缺陷    | 破坏性数据库迁移（fallbackToDestructiveMigration） |
| ISS-004                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 技术债   | 全局锁双源                                    |
| ISS-005                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 技术债   | 遮罩静态状态竞态                                 |
| ISS-006                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 收尾回归  | Phase 7 设备回归验证                           |
| ISS-021                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 兼容性缺陷 | 小米无障碍“设置已启用但服务未绑定”                       |
| ISS-023                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 工程缺陷  | Gradle Wrapper 缺失导致仓库不可构建                |
| ISS-024                             | P1  | 严重  | IN_PROGRESS | 增强    | 防卸载能力从零重建（软件层，ISS-020 决策落地）            |
| ISS-025                             | P1  | 严重  | PENDING_USER_ACCEPTANCE | 增强    | 忘记家长密码后的离线客服恢复通道                       |
| ISS-007                             | P2  | 中等  | DONE        | 收尾回归  | 微信视频号软干预（原 ISSUE-004）                    |
| ISS-008                             | P2  | 中等  | WONTFIX     | 产品决策  | 华为/荣耀省电模式真机验证不纳入当前范围（原 ISSUE-005）          |
| ISS-009                             | P2  | 中等  | PENDING_USER_ACCEPTANCE | 技术债   | UI 巨型文件拆分（含抽 ViewModel）                  |
| ISS-010                             | P2  | 中等  | PENDING_USER_ACCEPTANCE | 技术债   | 无效 force-stop 路径清理                       |
| ISS-011                             | P2  | 中等  | PENDING_USER_ACCEPTANCE | 增强    | 输入法豁免改运行时发现（完整性，非安全）                     |
| ISS-012                             | P2  | 中等  | PENDING_USER_ACCEPTANCE | 技术债   | 取证日志增长（timeline.jsonl 无上限）               |
| ISS-013                             | P2  | 中等  | PENDING_USER_ACCEPTANCE | 技术债   | 密码明文分支淘汰计划                               |
| ISS-014                             | P2  | 轻微  | PENDING_USER_ACCEPTANCE | 技术债   | WRITE_SECURE_SETTINGS 预留项收口              |
| ISS-020                             | P2  | 中等  | DONE        | 产品决策  | 防卸载产品决策（不接受可卸载现状，由 ISS-024 从零重建）         |
| ISS-015                             | P3  | 中等  | PENDING_USER_ACCEPTANCE | 技术债   | 测试覆盖断层                                   |
| ISS-016                             | P3  | 轻微  | PENDING_USER_ACCEPTANCE | 技术债   | 无依赖注入框架                                  |
| ISS-017                             | P3  | 轻微  | PENDING_USER_ACCEPTANCE | 增强    | 轮询优化（UsageStats 3s）                      |
| ISS-018                             | P3  | 轻微  | PENDING_USER_ACCEPTANCE | 技术债   | 系统受保护表面分类器拆分                             |
| ISS-019                             | P3  | 轻微  | PENDING_USER_ACCEPTANCE | 规范    | 重建 issue 台账（本文件）                         |
| ISS-022                             | P3  | 轻微  | PENDING_USER_ACCEPTANCE | UX 缺陷 | 配置向导“重新检查”无可见反馈                          |

> **当前状态**：ISS-007 已获用户明确人工验收并保留 `DONE`；ISS-008 是用户明确作出的 `WONTFIX` 产品决策；ISS-020 经用户 2026-07-22 明确决策后关闭（实现转 ISS-024）。ISS-002、ISS-024 为活动项，其余标为 `PENDING_USER_ACCEPTANCE` 的条目均须由用户人工验收，自动化结果不再用于关单。

---

## 2. P0 — 发布前必修

### ISS-001 · 系统时间篡改绕过

| 字段      | 值                                                                                    |
| ------- | ------------------------------------------------------------------------------------ |
| 优先级/严重性 | P0 / 严重                                                                              |
| 类型/状态   | 缺陷 / PENDING_USER_ACCEPTANCE                                                         |
| 关联代码    | `LockDecisionEngine`（`LocalTime.now()` / `LocalDate.now()`）；新增 `TrustedTimeProvider` |
| 来源      | 评价报告 §6 P0#1                                                                         |
| 负责人     | —                                                                                    |

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
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。历史测试记录仅作开发与排障证据。

---

## 3. P1 — 高优先级

### ISS-002 · 设置守卫内容策略覆盖（攻防核心）

| 字段      | 值                                                                      |
| ------- | ---------------------------------------------------------------------- |
| 优先级/严重性 | P1 / 严重                                                                |
| 类型/状态   | 增强·收尾回归 / IN_PROGRESS                                                  |
| 关联代码    | `ProtectedSettingsPolicy`、`BrandSettingsRules`、`ProtectedSurfaceGuard` |
| 来源      | 评价报告 §6 P1#2 / §8.4；原"保护设置关键词与响应速度调优"ISSUE                             |
| 负责人     | —                                                                      |

**问题**：防"小孩进设置关权限/开省电破防"的真正杠杆是逐页内容判定（`ALLOW/OBSERVE/BLOCK_ACTION/BLOCK_PAGE`），需持续补关键词与 ROM 覆盖。当前覆盖不全、粒度待调、响应速度待优化。
**建议修法**：按 MIUI/EMUI/ColorOS/OneUI 等逐 ROM 补 `targetAppKeywords`/`riskyCapabilityKeywords`/`riskyActionKeywords`/`guardianDisruptive*`；调 `BLOCK_ACTION`（只拦危险点击、页面留着）与 `BLOCK_PAGE` 粒度；优化判定响应速度。注意：**设置前缀匹配（`SystemSurfaceClassifier.isSettingsSurface`）是正确的拦截触发器，保持/加宽，不要改精确匹配**（见 ISS-018）。
**变更记录**：

- 2026-07-09 创建（进行中，持续调优）。
- 2026-07-11 将 `ProtectedSettingsPolicy` 的 Android 状态读取与纯决策逻辑拆分为 `ProtectedSettingsDecisionEngine`；新增 JVM 决策矩阵，锁定全局解锁、设置向导、守护省电、Huawei 页面、SystemUI 瞬态页及目标应用四级决策的既有优先级。未凭猜测新增厂商关键词/包名，ROM 真机覆盖和响应速度验证当时仍待 ISS-006/ISS-008 回归。（历史状态；ISS-008 后于 2026-07-21 按产品决策转为 WONTFIX。）
- 2026-07-19 台账复核：ISS-006 已完成小米 Android 15 基线回归；当时华为/荣耀受 ISS-008 缺真机阻塞，OPPO/vivo/三星等 ROM 尚无实机证据。后续须先明确支持 ROM 矩阵与响应时延验收口径，再按真机日志补规则，状态维持 IN_PROGRESS。（历史状态；ISS-008 后于 2026-07-21 按产品决策转为 WONTFIX，不再阻塞 ISS-002。）
- 2026-07-20 Redmi Note 12 Turbo（Android 15 / HyperOS 3.0.5.0）实测设置守卫：系统“无障碍”根页和“已下载的应用”列表被判为 `OBSERVE`，可正常浏览；进入包含“拉钩守护/关闭”的目标服务页面后判为 `BLOCK_ACTION reason=target_app_risky_action`，同一事件时间戳完成 fast suppress，并在约 43 ms 后成功执行 HOME，随后延迟 BACK/HOME 任务继续压制，最终回到桌面。该结果确认小米样本上的页面粒度和响应速度符合预期；当时华为/荣耀仍由 ISS-008 阻塞，其他 ROM 的支持范围尚未决策，状态维持 IN_PROGRESS。（历史状态；ISS-008 后于 2026-07-21 按产品决策转为 WONTFIX，不再阻塞 ISS-002。）
- 2026-07-21 产品范围更新：华为/荣耀系统“健康使用手机”与本项目核心需求高度重合，ISS-008 不再作为当前必需项；ISS-002 的当前收尾范围不包含 ISS-008 所述省电模式真机验证。若未来重新要求华为/荣耀覆盖，应先明确支持范围，再基于当时设备日志另行重开或新建条目。
- 2026-07-21 验收证据纠正：上述“约 43 ms”仅是日志中的内部动作时间，不能代表用户实际感知，也不能证明防护有效。用户在荣耀真机人工复测后确认仍可退出无障碍且响应过慢，判定为严重问题。ISS-002 保持 IN_PROGRESS，后续只能由用户人工复测并明确确认后收尾。
- 2026-07-21 荣耀 OXF-AN10 现场定位：用户从系统设置点击“拉钩守护 已开启”后，策略先返回 `OBSERVE target_app_settings_without_risk_keyword`，约 2.27 秒后周期扫描才因“无障碍/辅助功能”返回 `BLOCK_PAGE` 并执行 HOME；该空窗足以继续操作，用户明确判定没有实际防护意义。另一次取证确认无障碍在系统设置点击后被关闭，随后悬浮窗也不可用，降级锁记录 `cannot_show_lock: no overlay permission`。候选修复改为在受保护设置中点击目标应用行时立即返回 `BLOCK_ACTION target_app_settings_entry_click`，不再等待下一页关键词；状态保持 IN_PROGRESS，必须由用户真机复测响应速度和两项权限是否仍可被关闭。
- 2026-07-22 荣耀 OXF-AN10 用户人工复测：普通入口的拦截速度已基本满足需求，但先后发现小窗与“最近任务/历史页面”绕过；最终从全面屏手势的最近任务恢复无障碍详情页并成功关闭服务，明确判定候选版失败。现场确认设置任务 `#492` 由本应用的 `ACTION_ACCESSIBILITY_SETTINGS` 启动，栈顶保留 `CleanSubSettings`；01:12:06 恢复设置页至 01:12:52 权限关闭之间约 46 秒没有 `protected_fast_suppress`，根因不是响应竞速，而是历史恢复页缺少能力关键词后被“仅目标应用名则 OBSERVE”规则长期放行。新候选实现改为：锁定状态下候选设置页只要明确出现“拉钩守护”即 `BLOCK_PAGE target_app_settings_page`；应用发起的权限/品牌设置 Intent 使用 `NEW_TASK | CLEAR_TASK | EXCLUDE_FROM_RECENTS`，重建外部设置任务并禁止留下最近任务卡片。定向 JVM 测试与 debug 构建通过仅属开发自检；ISS-002 保持 IN_PROGRESS，等待用户复测普通、小窗、最近任务三条路径。
- 2026-07-22 用户补充历史页面守卫曾表现为“偶尔触发、遮罩闪烁后失效”。源码复核确认两个确定性释放缺陷：`OBSERVE`（节点快照不完整）会调用 `releaseProtectedSettingsOverlayIfAllowed` 立即撤销刚建立的保护；专用释放检查在目标设置包仍可见时也会于 2.6 秒执行导航后无条件隐藏遮罩。候选修复将 `OBSERVE` 定义为不确定状态，不再释放已有保护；仅在目标确已离开或家长放行生效时隐藏遮罩，目标仍可见则持续强化导航并循环检查。相关决策、释放条件和任务 Intent 定向测试及 debug 构建通过仅属开发自检，仍等待用户真机人工验收。
- 2026-07-22 荣耀 OXF-AN10“最近任务/历史页面”人工复测：在全局解锁关闭状态下，用户先连续 5 次从最近任务恢复遗留的无障碍详情卡片，5 次均被立即弹走；对应日志均命中 `BLOCK_PAGE reason=target_app_settings_page`，从系统开始恢复设置页到开始 HOME 的记录间隔约为 108/90/80/65/84 ms。日志同时暴露设置页退出后仍有延迟 BACK/HOME 执行至第 3 秒、遮罩迟到显示到桌面再隐藏的问题，候选实现据此增加“后续导航执行前重新确认目标仍为活动/聚焦窗口”、同包新一轮压制覆盖旧调度，以及“遮罩实际显示前目标已离开则取消显示与剩余动作”；定向测试与 debug 构建通过仅属开发自检。覆盖安装后，用户关闭全局解锁再次人工复测并明确确认“功能完好”；断线前状态核对显示 `global_unlock_enabled=false`、无障碍服务仍启用且悬浮窗为 `allow`。因此仅将“最近任务/历史页面恢复无障碍详情”子路径记为用户人工通过；ISS-002 仍保持 IN_PROGRESS，普通入口、小窗及整体收尾仍以用户后续明确验收为准。

### ISS-003 · 破坏性数据库迁移

| 字段      | 值                                                                  |
| ------- | ------------------------------------------------------------------ |
| 优先级/严重性 | P1 / 严重                                                            |
| 类型/状态   | 缺陷 / PENDING_USER_ACCEPTANCE                                       |
| 关联代码    | `AppDatabase`（`MIGRATION_1_2`、`addMigrations`、`exportSchema=true`） |
| 来源      | 评价报告 §6 P1#3 / §8.3                                                |
| 负责人     | —                                                                  |

**问题**：schema 变更会清空全部规则与使用记录；v1→v2 已静默清空过一次用户数据（实锤，提交 `9e536a4`）。
**建议修法**：删 `fallbackToDestructiveMigration()`，加 `addMigrations(MIGRATION_1_2)`（`ALTER TABLE app_rules ADD COLUMN limitMode INTEGER NOT NULL DEFAULT 0`）；开 `exportSchema=true` 并配 `room.schemaDirectory`；补 `MigrationTestHelper` 的 androidTest。详见报告 §8.3。
**实际修法（2026-07-10）**：

- `AppDatabase`：删除 `fallbackToDestructiveMigration()`；新增 `MIGRATION_1_2`（`ALTER TABLE app_rules ADD COLUMN limitMode INTEGER NOT NULL DEFAULT 0`）并通过 `addMigrations(MIGRATION_1_2)` 注册；`exportSchema` 改为 `true`；
- `app/build.gradle.kts`：新增 `ksp { arg("room.schemaLocation", "${projectDir}/schemas") }` 配套 schema 导出；新增 `androidTestImplementation("androidx.room:room-testing:2.6.1")`；
- `app/src/androidTest/.../AppDatabaseMigrationTest`：初版采用手动建 v1 表验证 `limitMode` 默认值；2026-07-21 已从历史提交 `9e536a4^` 使用原 Room 2.6.1 编译器重新导出真实 v1 schema，并升级为 `MigrationTestHelper.runMigrationsAndValidate`，同时验证完整 v2 结构、`app_rules` 与 `daily_usage` 数据保留及 `limitMode=0`。
  **变更记录**：
- 2026-07-09 创建（已发生实锤，非未来隐患）。
- 2026-07-10 实现显式迁移，移除 fallbackToDestructiveMigration，状态 OPEN → DONE。
- 2026-07-21 补齐历史 v1 schema JSON 与完整 `MigrationTestHelper` 仪器测试；API 34 模拟器验证通过。
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与模拟器仪器测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。历史测试记录仅作开发与排障证据。

### ISS-004 · 全局锁双源

| 字段      | 值                                                                              |
| ------- | ------------------------------------------------------------------------------ |
| 优先级/严重性 | P1 / 严重                                                                        |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                                                  |
| 关联代码    | `LockDecisionEngine`、`AppRuleDao`、`AppRuleRepository`、`AppRule.isGlobalLocked` |
| 来源      | 评价报告 §6 P1#4                                                                   |
| 负责人     | —                                                                              |

**问题**：全局锁存在两个来源，引擎同时判断两者，可能出现不一致。
**建议修法**：统一为单一真相源（建议以 `SettingsManager` 为准，`AppRule.isGlobalLocked` 退役或改为派生值），并补单测覆盖。
**实际修法（2026-07-10）**：统一以 `SettingsManager.isGlobalLockEnabled()` 为单一真相源：

- `LockDecisionEngine`：移除 `|| (rule?.isGlobalLocked == true)` 判断，只判 `settingsManager.isGlobalLockEnabled()`；
- 清理死代码：删除 `AppRuleDao.updateGlobalLock` / `setGlobalLockForAll` 与 `AppRuleRepository.updateGlobalLock` / `setGlobalLockForAll`（均无调用方）；同步更新 `AppRuleRepositoryTest` 的 FakeAppRuleDao；
- `AppRule.isGlobalLocked` 字段保留（避免破坏性 DB 迁移），注释标记退役，不再作为拦截判断依据；
- 单测：初版引擎为 private 构造且直接依赖 Android；2026-07-19 已补内部依赖边界与 `LockDecisionEngineTest` 13 个用例，其中包含全局锁单一来源、规则优先级和篡改时段等核心矩阵。
  **变更记录**：
- 2026-07-09 创建。
- 2026-07-10 统一为 SettingsManager 单一真相源，清理死代码，状态 OPEN → DONE。
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-005 · 遮罩静态状态竞态

| 字段      | 值                                                                                        |
| ------- | ---------------------------------------------------------------------------------------- |
| 优先级/严重性 | P1 / 严重                                                                                  |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                                                            |
| 关联代码    | `OverlayCoordinator`、`OverlayService`、`UsageTrackingManager`、`GuardAccessibilityService` |
| 来源      | 评价报告 §6 P1#5                                                                             |
| 负责人     | —                                                                                        |

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
- 2026-07-21 验收口径重置：此前 `DONE` 由结构检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-006 · Phase 7 设备回归验证

| 字段      | 值                                                        |
| ------- | -------------------------------------------------------- |
| 优先级/严重性 | P1 / 严重                                                  |
| 类型/状态   | 收尾回归 / PENDING_USER_ACCEPTANCE                           |
| 关联代码    | 全链路                                                      |
| 来源      | `docs/plans/2026-05-24-phase-7-closeout-summary.md` 回归清单 |
| 负责人     | —                                                        |

**问题**：Phase 7 代码+单测通过，但真机回归清单（普通拦截/白名单/应用商店/卸载/使用情况/悬浮窗/无障碍等）尚未在真机验证。
**建议修法**：按 closeout 清单逐项真机验证，结果回填至此条"变更记录"；失败项另开新 ISS。**优先于任何新功能**（见 §0.8）。
**变更记录**：

- 2026-07-09 创建（进行中，清单待执行）。
- 2026-07-11 · Redmi 23049RAD8C（Xiaomi / Android 15）：已验证规则模式下 `com.easybrain.jigsaw.puzzles` 的“永久禁用”规则。该 Unity 应用冷启动只提供 `TYPE_WINDOW_CONTENT_CHANGED`，旧路由不会进入普通策略检查；新增“新包名内容事件”兜底后，真机日志确认 `APP_BLOCKED`，并执行 BACK / HOME 返回桌面。新增路由状态单测，`AccessibilityEventRouterTest` 7/7 通过。
- 2026-07-12 · Redmi 23049RAD8C（Xiaomi / Android 15）完成适用清单：本应用与无障碍服务真实绑定、普通应用（Jigsaw Puzzles）拦截、系统桌面白名单过渡、`com.xiaomi.market` 应用商店拦截、Jigsaw 卸载入口、使用情况访问页、悬浮窗全局列表均已真机验证。应用商店最初被页面观察路径抢占，已改为安装器/市场优先走普通锁定决策；悬浮窗页原漏掉小米“显示在其他应用的上层”全局列表，已在非设置向导状态整页拦截。相关 JVM 测试与 debug 构建均通过，状态 IN_PROGRESS → DONE。
- 微信视频号具体入口未在本轮进入，保持 ISS-007；华为/荣耀设备不在本轮范围，当时保持 ISS-008（BLOCKED）。（历史状态；ISS-007 已于 2026-07-12 完成，ISS-008 已于 2026-07-21 按产品决策转为 WONTFIX。）
- 2026-07-21 验收口径重置：此前真机步骤主要由助手通过 ADB、日志与 UI 树判断，未取得用户对整份清单的明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。历史记录仅作排障参考。

### ISS-021 · 小米无障碍“设置已启用但服务未绑定”

| 字段      | 值                                                                                                                                   |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 优先级/严重性 | P1 / 严重                                                                                                                             |
| 类型/状态   | 兼容性缺陷 / PENDING_USER_ACCEPTANCE                                                                                                     |
| 关联代码    | `GuardForegroundService`、`PermissionManager`、`DegradedLockManager`、`AccessibilityOperationalState`、`GuardProtectionHealthEvaluator` |
| 来源      | Redmi 23049RAD8C（Xiaomi / Android 15）Phase 7 真机回归                                                                                   |
| 负责人     | —                                                                                                                                   |

**问题**：小米系统曾将 `com.kidsphoneguard/.service.GuardAccessibilityService` 保留在 `enabled_accessibility_services` 中，设置界面也显示已启用，但 `dumpsys accessibility` 的 Bound services 没有本服务；此时不会收到任何无障碍事件。
**已有实现（未验收）**：将“实际可用”统一定义为“设置已启用 + 服务运行 + 心跳未过期”。前台守护、权限状态与降级锁均改用该判定；失联状态会记录 `accessibility_operational_loss` 并进入降级保护，恢复实际运行后才解除。`AccessibilityOperationalStateTest` 覆盖未绑定、心跳过期和正常运行三种形态。2026-07-20 进一步修改降级锁异步策略回调中的二次判定：回调返回后重新检查“实际可用”，不只检查系统设置开关；展示决策提取至 `GuardProtectionHealthEvaluator.shouldShowDegradedLock` 并补纯 JVM 测试。以上均不代表用户验收通过。
**历史自动化记录（验收效力已撤销）**：2026-07-20 曾在 Redmi Note 12 Turbo 上通过系统 UiAutomation 构造“设置启用但服务未绑定”状态，并观察日志、窗口和恢复流程。该记录只能证明测试场景下出现了相应信号，不能代表真实用户体验或最终防护效果，不再视为验收通过。
**变更记录**：

- 2026-07-19 台账复核：条目从 P2 章节移回 P1 章节，状态与验收条件不变；当前 ADB 无连接设备，不能完成真机收尾。
- 2026-07-19 Redmi Note 12 Turbo（23049RAD8C，Android 15 / OS3.0.5.0.VMRCNXM）复测：系统设置仍保留本服务且初始 `dumpsys accessibility` 明确为 Bound；在真机仪器测试安装/清理期间，日志连续捕获 `onUnbind → onDestroy`，同时 `enabled_accessibility_services` 仍含本服务，前台守护正确进入 `degraded=true`，随后系统 `onCreate → Service connected` 后自动恢复并解除降级状态。由于较长失联发生在息屏期（明确记录 `screen_off_skip_recovery_guide`），亮屏期失联仅约 1 秒，尚未满足“亮屏持续失联并实际展示降级锁/恢复引导”的最终验收条件，状态维持 IN_PROGRESS。
- 2026-07-20 解锁亮屏后再次捕获 MIUI 真实异常：`enabled_accessibility_services` 保持启用，但服务反复 `onUnbind → onDestroy → onCreate → Service connected`，前台守护记录 `accessibility_operational_loss settingsEnabled=true running=false`。日志与源码对照确认异步策略回调错误地改用 `isAccessibilityServiceEnabled` 二次判断，导致“设置已启用但实际未绑定”时撤销降级锁；已改为二次检查 `isAccessibilityServiceOperational`，并新增 3 个展示/恢复/策略放行测试。`GuardProtectionHealthEvaluatorTest` 11/11、完整 JVM 测试、Debug APK 构建和 lint 均通过；因真机既有包签名冲突，修复版尚不能覆盖安装，状态维持 IN_PROGRESS。
- 2026-07-20 家长确认旧包内均为可删除测试数据后，已卸载旧签名包并安装 13:37 构建的当前修复版 APK，签名冲突解除。小米应用信息页配置期间曾误触发卸载确认并导致新包再次被移除；因仍是空白初始化状态，随即重新安装同一 APK，当前 `firstInstallTime/lastUpdateTime` 均为 14:49。首次启动正常、未发现 FATAL/ANR；通知、使用情况访问、悬浮窗、电池优化白名单、小米自启动（`MIUIOP(10008)=allow`）和受限制设置均已通过可核验命令恢复，当前等待家长再次设置密码并完成无障碍确认，随后继续降级锁真机验收。状态维持 IN_PROGRESS。
- 2026-07-20 确定性真机验收：在不启用全局锁、不新增应用规则的前提下，通过持续 UiAutomation 会话稳定复现“设置启用但服务未绑定”；系统设置前台时确认降级锁全屏窗口、完整文案与密码输入焦点均已出现。结束会话后无障碍自动重新绑定，降级锁自动解除。验收条件全部满足，状态 IN_PROGRESS → DONE。
- 2026-07-21 验收口径重置：上述 UiAutomation、ADB、日志与窗口检查不构成用户人工验收；撤销“验收均通过”的关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。后续由用户在真实操作中判断是否有效、是否及时。
- 2026-07-26 Redmi Note 12 Turbo 现场发现降级锁密码框无法输入：窗口与 `InputMethodManager` 证据确认输入框已获得焦点和输入连接，但 `TYPE_NUMBER_VARIATION_PASSWORD` 触发 HyperOS 在小米安全输入法与普通输入法之间持续切换，最终产生 `Input dispatching timed out`。修复为保留本地圆点掩码、向系统声明普通数字输入，并移除 `SOFT_INPUT_STATE_ALWAYS_VISIBLE`；覆盖安装后真机日志显示 `inputType=2`、键盘请求成功、家长密码验证通过，且新安装进程没有新增输入超时。完整 233 项 JVM 测试与 Debug APK 构建通过；本记录仍属开发自检，状态保持 PENDING_USER_ACCEPTANCE，等待用户亲手确认输入体验。

### ISS-023 · Gradle Wrapper 缺失导致仓库不可构建

| 字段      | 值                                                      |
| ------- | ------------------------------------------------------ |
| 优先级/严重性 | P1 / 严重                                                |
| 类型/状态   | 工程缺陷 / PENDING_USER_ACCEPTANCE                         |
| 关联代码    | `gradlew`、`gradlew.bat`、`.gitignore`、`gradle/wrapper/` |
| 来源      | 2026-07-19 台账与构建链复核                                    |
| 负责人     | —                                                      |

**问题**：仓库已跟踪 `gradlew.bat`，脚本要求加载 `gradle/wrapper/gradle-wrapper.jar`，但 `.gitignore` 同时明确忽略 `gradle-wrapper.jar`、`gradle-wrapper.properties` 和全部 `*.jar`，且这两个标准 Wrapper 文件当前均不存在。执行 `.\gradlew.bat testDebugUnitTest` 直接报 `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`；仓库内残留的 `gradle/wrapper/gradle-8.4/` 也只包含启动脚本和许可文件，没有运行所需的 `lib/*.jar`，不能替代标准 Wrapper。结果是干净检出无法执行文档约定的构建、测试、lint 和 APK 生成，所有代码任务的可重复验收均被阻断。
**建议修法**：

- 按 Android Gradle Plugin 8.2.2 的官方兼容范围恢复标准 `gradle-wrapper.jar` 与 `gradle-wrapper.properties`，并将二者纳入 Git 跟踪；
- 调整 `.gitignore`，移除针对 Wrapper 文件的忽略规则，并确保全局 `*.jar` 规则不会再次排除 `gradle-wrapper.jar`；
- 标准 Wrapper 验证通过后，清理不完整且会误导维护者的 `gradle/wrapper/gradle-8.4/` 残留目录；
- 在干净检出环境依次验证 `.\gradlew.bat --version`、`.\gradlew.bat testDebugUnitTest`、`.\gradlew.bat assembleDebug` 与 `.\gradlew.bat lint`。
  **验收标准**：无需预装系统 Gradle、无需依赖未纳入仓库的本机文件，即可由 Wrapper 完成上述四项命令；Wrapper JAR 与 properties 均受版本控制；测试与构建结果回填本条变更记录。
  **变更记录**：
- 2026-07-19 创建；现场复现 Wrapper 主类缺失，状态记为 OPEN。该问题阻塞其他任务的可重复测试验收，应先修复再继续代码侧收尾。
- 2026-07-19 实现标准 Wrapper：按 AGP 8.2.2 官方兼容表选用 Gradle 8.2；由 Gradle 8.2 自身生成 `gradlew`、`gradlew.bat` 与 `gradle-wrapper.jar`，Wrapper JAR SHA-256 为官方公布的 `a8451eeda314d0568b5340498b36edf147a8f0d692c5ff58082d477abe9146e4`；`gradle-wrapper.properties` 固定官方 `gradle-8.2-bin.zip`，配置官方分发包 SHA-256 `38f66cd6eef217b4c35855bb11ea4e9fbc53594ccccb5fb82dfd317ef8c2c5a3`、120 秒网络超时和 URL 校验；修正 `.gitignore` 并删除不完整的 `gradle/wrapper/gradle-8.4/` 残留。`.\gradlew.bat --version` 已通过，确认 Gradle 8.2 + Microsoft OpenJDK 17.0.18。
- 2026-07-19 环境复核：本机已有 JDK 17、Android SDK Platform 34、Build-Tools 34.0.0 与 Platform-Tools/ADB，必需软件齐全；Android Studio/模拟器仅属可选。`testDebugUnitTest` 在项目配置阶段因 Google Maven `dl.google.com` TLS 握手被远端中断而失败，直接 `curl` 多次重试同样失败，尚未进入源码编译或测试执行；`assembleDebug`、`lint` 依赖同一未下载依赖，暂未重复执行。状态 OPEN → IN_PROGRESS；待网络或代理可稳定访问 Google Maven 后重跑三项命令并转 DONE。
- 2026-07-19 开启 TUN 后，通过 Karing 本地代理临时完成依赖下载；`.\gradlew.bat testDebugUnitTest --no-daemon`（25 个任务）、`.\gradlew.bat assembleDebug --no-daemon`（35 个任务）与 `.\gradlew.bat lint --no-daemon`（24 个任务）均 `BUILD SUCCESSFUL`，lint HTML 报告已生成。项目级代理参数已在验证后移除，不把本机端口写入仓库；移除后再以 `--offline` 复跑单元测试仍成功。四项 Wrapper 验收全部完成，状态 IN_PROGRESS → DONE。
- 2026-07-21 验收口径重置：此前 `DONE` 由助手执行构建命令并判断，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。命令结果保留为开发自检证据。

### ISS-024 · 防卸载能力从零重建（软件层）

| 字段      | 值                                                                                          |
| ------- | ------------------------------------------------------------------------------------------ |
| 优先级/严重性 | P1 / 严重                                                                                     |
| 类型/状态   | 增强 / IN_PROGRESS                                                                             |
| 关联代码    | 新建 `engine/uninstall/UninstallDecisionEngine`、`service/guard/UninstallGuard`（不恢复、不引用旧 Phase 8 代码） |
| 来源      | ISS-020 用户决策（2026-07-22）                                                                    |
| 负责人     | —                                                                                          |

**背景**：旧防卸载（SensitiveActionGuard 系）因多轮补丁式修改跑偏、不可纠正，已被整体删除（git「remove uninstall protection remnants」）。用户明确指示从零重新设计，**禁止修补或复活旧代码**；Device Owner / 恢复出厂方案已被用户否决（ISS-020）。
**设计原则（全新，2026-07-22）**：

- **纯决策核心 + 薄 Android 壳**：`UninstallDecisionEngine` 为纯 Kotlin、JVM 可测（镜像 `ProtectedSettingsDecisionEngine` 的成功模式）；`UninstallGuard` 只负责采集窗口快照、调用引擎、执行阻断。
- **统一判定模型**：任何界面同时满足"出现本应用标识（拉钩守护/儿童手机守护/KidsPhoneGuard/包名）"且"提供卸载入口或处于卸载确认流程" → 阻断。安装器家族（packageinstaller 系）含本应用标识的弹窗整页 `BLOCK_PAGE`；点击卸载类文本且页面含本应用标识 → `BLOCK_ACTION`；launcher 家族出现本应用卸载确认界面 → `BLOCK_PAGE`。
- **统一阻断执行**：复用现有"BACK/HOME + 红屏遮罩"路径（`GuardActionScheduler` / `OverlayCoordinator`），与 `ProtectedSurfaceGuard` 同一执行体系，不新建执行机制。
- **家长逃生口**：全局解锁（家长密码）与设置向导放行期间 UninstallGuard 整体放行，家长可正常卸载/维护。
- **明确不覆盖**（Device Owner 领域，用户已否决）：ADB `pm uninstall`、安全模式；桌面长按拖拽若确认框不含应用名，由安装器确认弹窗拦截兜底。
  **验收标准**：JVM 决策矩阵测试全过 + `testDebugUnitTest` / `assembleDebug` / `lint` 通过为开发自检；最终由用户在真机人工验证（设置应用信息页卸载、桌面长按卸载、安装器确认弹窗、家长解锁后可卸载）后方能 DONE（§0.9）。
  **变更记录**：
- 2026-07-22 创建（ISS-020 决策落地，用户明确指示立即启动，属 §0.8 排序约束的用户显式例外）。
- 2026-07-22 完成首版实现：新建 `engine/uninstall/UninstallDecisionEngine`（纯 Kotlin 决策核心，安装器 4 包 + 桌面 6 包 + 卸载关键词 4 组 + 本应用标识 4 组，六条判定矩阵）与 `service/guard/UninstallGuard`（薄壳，复用 `GuardActionScheduler` + BACK/HOME + 遮罩执行体系，含 480ms 节流周期扫描与桌面事件廉价预过滤）；`AccessibilityEventRouter` 中 UninstallGuard 提至最前，`ProtectedSurfaceGuard` 让出安装器/桌面家族表面所有权；`BrandSettingsRules.riskyActionKeywords` 增补"卸载/uninstall"覆盖设置应用信息页卸载按钮。开发自检：新增 `UninstallDecisionEngineTest` 12/12，全量 `testDebugUnitTest` 160 项零失败，`assembleDebug` 与 `lint` 均 BUILD SUCCESSFUL。AGENTS.md 已同步架构与优先级说明。状态维持 IN_PROGRESS：真机人工验收（设置卸载、桌面长按卸载、安装器确认弹窗、家长解锁后放行）待用户执行（§0.9）。
- 2026-07-23 用户真机验收：设置→应用管理卸载、桌面长按卸载两通道均被成功拦截（全局解锁关闭状态下验证通过）；同时暴露两个缺陷并已修复：①launcher 被拦截后 HOME 归宿仍是 launcher，复用"目标离开前台即释放"的释放检查导致遮蔽层无限 reinforce/hold 不消散 → 改为 UninstallGuard 自有决策驱动释放（威胁消失或家长放行即释放，硬上限 1 轮重排，删除与 ProtectedSurfaceGuard 释放检查的耦合）；②安装器规则 3 仅凭"本应用标识"即拦截，误杀 `pm install` 的 InstallStaging 安装确认页（日志实证 `target=KidsPhoneGuard uninstall=` 空），导致安装流程被 HOME 杀掉挂起 → 规则 3 增加卸载关键词必要条件，安装/更新确认页放行。新增 5 个用例（释放/重排策略 4 + 安装页回归 1，引擎测试共 17 项），`testDebugUnitTest`、`assembleDebug`、`lint` 全部 BUILD SUCCESSFUL。**产品行为记录（用户确认）**：保护生效（全局解锁关闭）状态下，通过 adb/商店安装或更新本应用会被拦截或中止；安装/更新本应用前需家长开启全局解锁，完成后可关闭。
- 2026-07-23 小米（MIUI, Redmi Note 12 Turbo）适配**失败**，用户中止本轮路线：为治理 MIUI 逐节点 IPC 风暴（日志实证单次全窗扫描 1.4~3.3 秒）所做的“不遍历 launcher 主窗口 + 长按事件文本归因”改动，未经真机验证即进入判定链路，导致卸载确认对话框**完全不被拦截**（卸载测试期间无任何 `uninstall_decision` 日志，身份识别来源被砍、长按归因假设不成立），拦截能力较荣耀基线净退步；普通应用遮罩二次显示问题亦未解决（日志实证 count=2、持续约 10 秒）。另发现独立嫌疑：应用内某轮询链路每约 3 秒触发 `ShortcutService` launcher 查询并伴随密集 GC。完整失败复盘与负面清单见 `docs/ISS-024_防卸载小米适配失败复盘_负面参考_2026-07-23.md`，日志存档 `docs/iss024_miui_logcat_20260723.txt`。**工作区含未提交的第二、三轮改动，建议回滚至 `ae5b31a`（荣耀验收基线）后按复盘报告 §6 顺序重新出发。** 状态维持 IN_PROGRESS。

### ISS-025 · 忘记家长密码后的离线客服恢复通道

| 字段      | 值                                                                 |
| ------- | ----------------------------------------------------------------- |
| 优先级/严重性 | P1 / 严重                                                           |
| 类型/状态   | 增强 / PENDING_USER_ACCEPTANCE                                         |
| 关联代码    | `PasswordRecoveryActivity`、`RecoveryCodeEngine`、客服离线算号器              |
| 来源      | 2026-07-25 用户发现“忘记密码 → 无法全局解锁 → 无法卸载/进入设置”的闭环；2026-07-26 确认产品方案 |
| 负责人     | —                                                                 |

**问题**：当前帮助文档声称忘记密码可卸载重装，但保护生效时卸载守卫和受保护设置入口都要求先用原密码进入全局解锁，形成家长无法自救的闭环。

**用户确认的产品边界（2026-07-26）**：

- 本应用只需防普通儿童，不把专业逆向、密钥提取或冒充家长联系客服纳入当前威胁模型；
- 不引入网络、账号、首次设备登记、二维码或每设备密钥配置；
- 家长密码恢复页（`PAGE-06`）即时显示恢复设备号（`REC-03`）和计算日期（`REC-04`），家长将二者报给客服；
- 客服使用离线算号器（`REC-06`），按“恢复设备号 + 计算日期”生成 8 位当日恢复码（`REC-05`）；
- 当日恢复码只允许重设家长密码，不直接关闭保护、全局解锁或放行卸载。

**验收标准**：

- 已设置家长密码时，主要密码入口可进入“忘记密码”恢复页；
- App 显示的恢复设备号、计算日期与离线算号器输入一致，同一输入产生相同 8 位当日恢复码；
- 错误恢复码不能修改旧密码；正确恢复码可直接设置新密码；
- 连续错误有轻量尝试限制，且不依赖手机联网；
- `testDebugUnitTest`、`assembleDebug`、`lint` 仅作为开发自检；最终状态须由用户真机操作确认后才能 `DONE`（§0.9）。

**变更记录**：

- 2026-07-26 用户确认采用轻量离线客服算号方案并要求同时实现算号器，状态记为 IN_PROGRESS。
- 2026-07-26 完成实现：新增 `RecoveryCodeEngine`（HMAC-SHA256 + HOTP 动态截断的 8 位码）、`RecoveryCodeManager`（`ANDROID_ID`/可信日期采集、5 次失败后冷却 60 秒）、`PasswordRecoveryActivity`，并在主要密码对话框、密码设置页和降级保护层（`OVL-02`）内接入恢复入口；正确恢复码直接覆盖写入新的 PBKDF2 家长密码，不开启全局解锁。新增 Windows 图形/命令行离线工具 `tools/打开恢复码算号器.bat`，工具启动时自检固定向量 `7D4A-92FC-381B-6E20 + 2026-07-26 → 19381938`，与 Android 单测一致。开发自检：新增 7 个 JVM 用例；完整 `testDebugUnitTest` 共 233 项、0 failures/0 errors，`assembleDebug` 与 `lint` 均 BUILD SUCCESSFUL（lint 0 errors、56 个既有 warnings，新增恢复文件无 lint 命中）。状态 IN_PROGRESS → PENDING_USER_ACCEPTANCE；仍需用户在真机验证普通密码入口和降级保护层两条恢复路径后才能 DONE（§0.9）。

---

## 4. P2 — 中优先级

### ISS-007 · 微信视频号软干预（原 ISSUE-004）

| 字段      | 值                           |
| ------- | --------------------------- |
| 优先级/严重性 | P2 / 中等                     |
| 类型/状态   | 收尾回归 / DONE                 |
| 关联代码    | `WeChatFinderGuard`         |
| 来源      | Phase closeout；历史 ISSUE-004 |
| 负责人     | —                           |

**问题**：原实现依赖无障碍事件类名并立即遮罩/返回，微信实际播放时常只发内容变化事件，导致识别不稳定且会过度干预。
**实际修法**：不阻断微信，不显示遮罩；通过 `UsageStatsManager` 的微信 `ACTIVITY_RESUMED` 记录确认 `com.tencent.mm.plugin.finder.*` 为前台，2 秒后再次确认仍在 Finder 才执行一次 BACK。活动记录采用首次 90 秒回看、后续增量查询，避免高频无障碍事件反复扫描长时间历史；普通聊天、朋友圈和小程序不以内容刷新频率作为触发条件。
**变更记录**：

- 2026-07-09 创建（进行中）。
- 2026-07-12 · Redmi 23049RAD8C（Xiaomi / Android 15）真机回归通过：视频号可在约 2～3 秒内软返回，微信其他功能不显示遮罩且不被整体拦截。用户确认“基本达到预期”；识别依据为 Finder 前台 Activity，而非逐帧媒体播放状态，微信未来改动 Activity 结构时另开兼容性 ISSUE。状态 IN_PROGRESS → DONE。

### ISS-008 · 华为/荣耀省电模式真机验证不纳入当前范围（原 ISSUE-005）

| 字段      | 值                                                             |
| ------- | ------------------------------------------------------------- |
| 优先级/严重性 | P2 / 中等                                                       |
| 类型/状态   | 产品决策 / WONTFIX                                                  |
| 关联代码    | `oem/HuaweiPowerSaveHandler`、`ProtectedSettingsPolicy`（省电关键词） |
| 来源      | Phase closeout；历史 ISSUE-005                                   |
| 负责人     | —                                                             |

**原需求**：华为/荣耀省电模式处理代码已写，但缺少对应真机验证。
**产品决策（2026-07-21）**：荣耀、华为系手机已内置“健康使用手机”，与本项目核心功能需求高度重合，因此不再把该机型省电模式真机验证作为当前必要工作，状态由 BLOCKED 转为 WONTFIX。
**保留边界**：现有 `HuaweiPowerSaveHandler`、相关策略和单元测试继续保留，避免无依据删除兼容代码；但保留代码不等于已完成真机验证，也不据此宣称华为/荣耀支持。
**重开条件**：若后续产品范围重新要求在华为/荣耀设备上提供独立于系统能力的覆盖，应基于当时设备、系统版本和日志重新评估，并重开本条目或新建兼容性条目。
**变更记录**：

- 2026-07-09 创建（BLOCKED：缺真机）。
- 2026-07-21 需求变更：系统“健康使用手机”与本项目需求高度重合，真机验证不再是当前必需项；状态 BLOCKED → WONTFIX。现有兼容代码保留，但不宣称已验证支持。

### ISS-009 · UI 巨型文件拆分（含抽 ViewModel）

| 字段      | 值                                                                                     |
| ------- | ------------------------------------------------------------------------------------- |
| 优先级/严重性 | P2 / 中等                                                                               |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                                                         |
| 关联代码    | `ConfigActivity.kt`（基线 2428 行，当前 351 行）、`MainActivity.kt`（基线 1313 行，当前 120 行）；各功能组件文件 |
| 来源      | 评价报告 §6 P2#6 / §7 第 3 条                                                               |
| 负责人     | —                                                                                     |

**问题**：单文件堆叠 20+ Composable，维护与合并冲突困难。
**建议修法**：按功能域拆分为多文件；**拆分时顺手补 ViewModel + StateFlow**，为后续网络模块的远程状态注入留接口（来自报告 §7 第 3 条）。
**变更记录**：

- 2026-07-09 创建（合并了"拆 UI 同时抽 ViewModel"建议）。
- 2026-07-12 启动第一阶段：新增 `ui/config/ConfigViewModel` + `ConfigUiState`，由 `StateFlow` 统一观察规则、当天用量和临时奖励，并将新增/修改/删除/批量应用/临时加时的仓库写入从 `ConfigScreen` 收口到 ViewModel；弹窗开关和视图模式仍是 Compose 瞬态状态。`ConfigActivity` 由 2428 行降至 2371 行，余下展示组件与 `MainActivity` 尚未拆分，故状态 OPEN → IN_PROGRESS，不能收尾。
- 2026-07-12 第二阶段：将应用选择、搜索及列表/图标展示迁至 `ui/config/AppSelectorComponents.kt`，`AddRuleDialog` 保持原有调用签名与选择结果不变；`ConfigActivity` 进一步降至 2110 行。批量规则、单规则编辑和 `MainActivity` 尚待拆分，状态维持 IN_PROGRESS。
- 2026-07-12 第三阶段（规则展示预处理）：将限时规则的已用/剩余/临时奖励文案提取为 `ui/config/RuleUsageFormatter`，规则卡片和编辑对话框改为调用该纯 Kotlin 组件；新增 3 个 JVM 用例覆盖时长限制、仅时段限制和非限时规则。规则卡片、编辑器与批量配置的 Compose 文件拆分仍待后续阶段，状态维持 IN_PROGRESS。
- 2026-07-12 第四阶段（编辑器预处理）：将时段的解析、格式化和分钟归一化提取为 `ui/config/TimeWindowCodec`，单规则与批量规则共用同一编解码入口；新增 JVM 用例覆盖首段选择、错误回退和跨日归一化。当前 `ConfigActivity` 为 2028 行；规则卡片、编辑器、批量配置与 `MainActivity` 的 Compose 文件拆分仍未完成，状态维持 IN_PROGRESS。
- 2026-07-12 完成代码级功能域拆分：`ConfigActivity` 仅保留 Activity 与 `ConfigScreen`（352 行），配置控制、规则展示、单规则编辑、批量配置分别迁至独立文件；`MainActivity` 仅保留两个 Activity 入口（121 行），主页、配置向导、密码流程分别迁至独立文件。`compileDebugKotlin`、完整 `testDebugUnitTest`、`assembleDebug` 均通过。当前 ADB 未检测到设备，按实施计划仍需完成一次家长主页、配置页、向导、单规则和批量规则真机冒烟验证后才能从 IN_PROGRESS 改为 DONE。
- 2026-07-19 台账复核：按当前文件重新计数为 `ConfigActivity` 351 行、`MainActivity` 120 行；ISS-023 已恢复构建链，实施计划要求的 `ConfigViewModelTest` 尚不存在。收尾条件为补齐 ViewModel 测试，再完成上述真机冒烟，状态维持 IN_PROGRESS。
- 2026-07-19 补齐 `ConfigViewModelTest`：ViewModel 保留生产端无参创建方式，同时通过内部 `ConfigDependencies` 提供轻量可测缝隙，不引入 DI 框架；新增 5 个 JVM 用例，覆盖规则/用量/临时奖励状态合并、非 LIMIT 规则字段归一化、删除规则、奖励刷新和批量重配时移除同组规则。定向测试 5/5、完整 `testDebugUnitTest`、`assembleDebug` 与 `lint` 均通过。代码与测试交付已完成，剩余收尾仅为既定真机冒烟，状态维持 IN_PROGRESS。
- 2026-07-19 小米真机冒烟启动：家长主页在 23049RAD8C（Android 15）成功启动并完整渲染，日志与 UI 树均确认“守护状态正常”及四项权限/后台保活状态展示；配置页、权限向导、单规则和批量规则仍需家长在机上完成系统解锁及应用密码验证后继续，状态维持 IN_PROGRESS。
- 2026-07-20 完成 Redmi Note 12 Turbo（23049RAD8C，Android 15 / OS3.0.5.0.VMRCNXM）真机冒烟：家长主页、家长配置、维护型配置向导、现有单规则编辑器和批量规则编辑器均成功渲染；单规则与批量编辑器均以“取消”退出，未保存或批量应用，退出前后已配置规则数保持 5；最近 3000 行设备日志未发现 `com.kidsphoneguard` 的 FATAL EXCEPTION 或 ANR。既定代码、测试与真机验收全部完成，状态 IN_PROGRESS → DONE。
- 2026-07-21 验收口径重置：上述 ADB/UI 树、日志和助手操作不构成用户人工验收；撤销“全部完成”的关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-010 · 无效 force-stop 路径清理

| 字段      | 值                                     |
| ------- | ------------------------------------- |
| 优先级/严重性 | P2 / 中等                               |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE         |
| 关联代码    | `AppBlockCoordinator.tryForceStopApp` |
| 来源      | 评价报告 §6 P2#7 / §8.1                   |
| 负责人     | —                                     |

**问题**：反射+shell 在非系统/非 root 下恒失败，是死代码；引入崩溃/ANR 风险与虚假安全感。
**建议修法**：删除反射与 shell 两段，拦截明确只依赖"HOME/BACK + 红屏遮罩"前台压制；补分支单测。详见报告 §8.1。
**实际修法（2026-07-10）**：

- `AppBlockCoordinator.tryForceStopApp`：删除反射 `ActivityManager.forceStopPackage` 与 `Runtime.exec("am force-stop")` 两段死代码（非系统/非 root 恒 SecurityException/失败）；
- 删除仅服务于反射分支的 `forceStopPermissionDenied` 标志字段；
- 保留合法范围内的 `appTasks.finishAndRemoveTask`（Lollipop+）与 `killBackgroundProcesses`（需 `KILL_BACKGROUND_PROCESSES` 权限，已声明）；`Process.killProcess`（<O）保留作低版本兼容；
- 拦截明确只依赖 HOME/BACK + 红屏遮罩前台压制。2026-07-21 历史自动化复核曾建议不把 `ActivityManager` 的 best-effort 任务/后台清理分支纳入 ISS-015；该建议不再具有验收范围决策效力，是否接受此边界由用户决定。
  **变更记录**：
- 2026-07-09 创建。
- 2026-07-10 移除反射与 shell 死代码，清理 forceStopPermissionDenied，状态 OPEN → DONE。
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-011 · 输入法豁免改运行时发现（完整性，非安全）

| 字段      | 值                                                                  |
| ------- | ------------------------------------------------------------------ |
| 优先级/严重性 | P2 / 中等                                                            |
| 类型/状态   | 增强 / PENDING_USER_ACCEPTANCE                                       |
| 关联代码    | `WhitelistManager.isInWhitelist`、`GuardForegroundService`（输入法缓存刷新） |
| 来源      | 评价报告 §6 P2#8 / §8.4                                                |
| 负责人     | —                                                                  |

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
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-012 · 取证日志增长

| 字段      | 值                                                       |
| ------- | ------------------------------------------------------- |
| 优先级/严重性 | P2 / 中等                                                 |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                           |
| 关联代码    | PC 侧 `scripts/pc_forensics_watch.py` 的 `timeline.jsonl` |
| 来源      | 评价报告 §6 P2#9                                            |
| 负责人     | —                                                       |

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
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-013 · 密码明文分支淘汰计划

| 字段      | 值                                                       |
| ------- | ------------------------------------------------------- |
| 优先级/严重性 | P2 / 中等                                                 |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                           |
| 关联代码    | `PasswordManager`（`KEY_LEGACY_PASSWORD` 明文读取 + 一次性迁移分支） |
| 来源      | 评价报告 §6 P2#10                                           |
| 负责人     | —                                                       |

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
- 2026-07-19 增加语义化密码存储、盐生成器、验证成功回调与日志边界，生产端仍以一次 SharedPreferences 编辑写入 PBKDF2 hash/salt/版本并删除明文，PBKDF2 参数不变；Base64 改用 API 26 可用的 Java 标准实现，与原 `NO_WRAP` 数据兼容。新增 8 个真实 PBKDF2 JVM 用例，覆盖设置/验证/随机盐、未知版本、损坏存储安全失败、迁移成功/失败保留明文/冗余清理及重置；定向与完整测试、Debug 构建、lint 均通过。
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-014 · WRITE_SECURE_SETTINGS 预留项收口

| 字段      | 值                                                                                               |
| ------- | ----------------------------------------------------------------------------------------------- |
| 优先级/严重性 | P2 / 轻微                                                                                         |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE（实现与保留建议待用户确认）                                                       |
| 关联代码    | `AndroidManifest.xml`（`WRITE_SECURE_SETTINGS` 声明）、`DegradedLockManager.tryProgrammaticRecovery` |
| 来源      | 评价报告 §6 P2#11                                                                                   |
| 负责人     | —                                                                                               |

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
- 2026-07-21 验收口径重置：此前 `WONTFIX` 是助手基于代码检查作出的判断，不是用户产品决策；撤销该决策效力，状态 WONTFIX → PENDING_USER_ACCEPTANCE。是否保留权限声明由用户最终确认。

### ISS-020 · 防卸载产品决策

| 字段      | 值                        |
| ------- | ------------------------ |
| 优先级/严重性 | P2 / 中等                  |
| 类型/状态   | 产品决策 / OPEN              |
| 关联代码    | （Phase 8 已废弃并移除运行时防卸载代码；实现由 ISS-024 从零重建） |
| 来源      | 评价报告 §7 第 4 条            |
| 负责人     | —                        |

**问题**：当前**无防卸载能力**（Phase 8 有意降级）。需确认是否接受"儿童可卸载 App"这一现状。
**建议修法（决策）**：若接受 → 标 `WONTFIX` 并注明；若不接受 → 规划替代方案（如设备所有者/Device Policy Manager），**新建独立 ISS** 跟踪实现（属尚未开始功能，本台账暂不纳入实现条目）。
**用户决策（2026-07-22，DONE 依据）**：用户明确**不接受**"儿童可卸载 App"现状；同时明确**否决** Device Owner / 恢复出厂类方案（无账号初始状态、ADB 配置、备份恢复成本均不可接受）。用户指出旧防卸载代码的失败根因是多轮补丁式修改跑偏、不可纠正，因此要求**抛弃旧机制与旧代码，从零重新设计软件层防卸载**。实现由 ISS-024（P1）跟踪，本条决策关闭。
**变更记录**：

- 2026-07-09 创建（决策待定）。
- 2026-07-22 用户明确决策：不接受可卸载现状；否决 Device Owner/重置方案；决定从零软件重建防卸载（新建 ISS-024 跟踪实现）。状态 OPEN → DONE。

---

## 5. P3 — 低优先级

### ISS-015 · 测试覆盖断层

| 字段      | 值                                                                                            |
| ------- | -------------------------------------------------------------------------------------------- |
| 优先级/严重性 | P3 / 中等                                                                                      |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                                                                |
| 关联代码    | `LockDecisionEngine`、`PasswordManager`、`DailyUsageRepository`、`GuardForegroundService`、全部 UI |
| 来源      | 评价报告 §6 P3#12                                                                                |
| 负责人     | —                                                                                            |

**问题（历史基线）**：评价基线仅有 14 个单测，测试覆盖曾集中在协调器/守卫/路由，引擎、密码、仓库、前台服务与 UI 存在明显断层。
**自动化资产记录（不构成人工验收，2026-07-21）**：当前共有 28 个 JVM 测试文件、3 个 androidTest 文件、143 个 `@Test` 方法（JVM 134、Android 仪器 9）。这些用例只构成开发自检基线，不证明真实设备行为、用户体验或最终功能验收通过。
**本次进展（2026-07-10）**：

- `LockDecisionEngine.isInBlockedTimeWindow` 提取为 `companion internal` 纯逻辑方法（接收 `timeWindows` + `now` 参数），实例方法委托；
- 新增 `LockDecisionEngineTimeWindowTest`（14 个用例）：覆盖常规窗口/跨午夜窗口/全天窗口/多窗口/边界/非法格式/空字符串。
- 2026-07-11：`ProtectedSettingsDecisionEngineTest` 新增 9 个用例，覆盖 `ALLOW`、`OBSERVE`、`BLOCK_PAGE`、`BLOCK_ACTION` 与守护省电、全局解锁、设置向导、Huawei/SystemUI 优先级；`testDebugUnitTest` 已通过（9/9，零失败）。
  **自动化分层记录（仅开发自检）**：
- L1 纯 JVM：`testDebugUnitTest` 通过，134 个测试覆盖核心决策、仓库、设置、密码、ViewModel 与服务健康状态；
- L2 构建静态检查：`assembleDebug`、`assembleDebugAndroidTest` 与 `lint` 通过；
- L3 Android 仪器层：在 API 34 / Google APIs x86_64 模拟器上 9/9 通过，包括 1 个完整 `MigrationTestHelper` 迁移测试、4 个 Compose 密码/权限交互测试，以及 4 个通知通道、Watchdog Alarm、真实降级悬浮窗、前台服务启停集成测试；
- 当前机器的 Gradle UTP 辅助插件下载受 `dl.google.com` Java TLS 握手影响，故测试 APK 在 Gradle 成功编译后使用同一 `AndroidJUnitRunner` 通过 ADB 直接执行；这是主机网络环境问题，不是测试失败；
- L4 厂商 ROM 行为不纳入 ISS-015，以各厂商兼容性/产品范围条目跟踪。自动化测试不宣称穷尽全部 UI 与 Android 系统行为；后续发现具体回归时新建独立 ISS。
  **变更记录**：
- 2026-07-09 创建。
- 2026-07-10 补 LockDecisionEngine 时段判定 14 个单测，状态 OPEN → IN_PROGRESS。
- 2026-07-19 复核当前测试资产为 22 个 JVM 测试文件、1 个 androidTest 文件、90 个测试方法；补记关键缺口与 ISS-023 前置阻塞，状态维持 IN_PROGRESS。
- 2026-07-19 新增 `ConfigViewModelTest` 5 个用例；当前测试资产更新为 23 个 JVM 测试文件、1 个 androidTest 文件、95 个测试方法。完整 JVM 测试、Debug 构建和 lint 通过；其余分层覆盖缺口仍在，状态维持 IN_PROGRESS。
- 2026-07-19 为 `DailyUsageRepository` 增加内部可信日期/真实日期来源，不改变生产端 `Context` 构造方式；新增 4 个 JVM 用例，覆盖可信日期、当天累计、当天 Flow 选择和 30 天清理边界。定向测试 4/4，完整 JVM 测试、Debug 构建和 lint 均通过；测试资产更新为 24 个 JVM 测试文件、1 个 androidTest 文件、99 个测试方法，状态维持 IN_PROGRESS。
- 2026-07-19 为 `SettingsManager` 增加内部存储/时钟缝隙，不改变生产端 `Context` 构造与单例入口；新增 4 个 JVM 用例，覆盖布尔设置默认值、独立读写、设置向导临时放行的严格过期边界和主动清除。定向测试 4/4，完整 JVM 测试、Debug 构建和 lint 均通过；测试资产更新为 25 个 JVM 测试文件、1 个 androidTest 文件、103 个测试方法，状态维持 IN_PROGRESS。
- 2026-07-19 补齐 `PasswordManager` 8 个完整行为用例，并将损坏的 Base64/hash 存储处理改为记录错误后安全返回 `false`；真实 PBKDF2 参数及迁移“先写 hash、成功后删明文”的安全顺序保持不变。定向测试 8/8，完整 JVM 测试、Debug 构建和 lint 均通过；测试资产更新为 26 个 JVM 测试文件、1 个 androidTest 文件、111 个测试方法，状态维持 IN_PROGRESS。
- 2026-07-19 将 `GuardForegroundService` 的健康快照和“降级变化/无障碍恢复”转换提取为纯 `GuardProtectionHealthEvaluator`，服务继续负责采集 Android 状态与执行日志、通知、锁屏和重启副作用；新增 8 个 JVM 矩阵用例，覆盖权限缺失、服务未绑定、心跳缺失/边界、Usage tracker 失活及恢复转换。定向测试 8/8，完整 JVM 测试、Debug 构建和 lint 均通过；测试资产更新为 27 个 JVM 测试文件、1 个 androidTest 文件、119 个测试方法，状态维持 IN_PROGRESS。
- 2026-07-19 为 `LockDecisionEngine` 增加内部依赖边界，生产单例和规则优先级保持不变；新增 13 个 JVM 用例，覆盖全局解锁、自己包、设置/市场、全局锁、空/ALLOW/BLOCK 规则、篡改时段、实时窗口、奖励时长边界及 `DURATION_ONLY`/`WINDOW_ONLY` 隔离。定向测试 13/13，完整 JVM 测试、Debug 构建和 lint 均通过；测试资产更新为 28 个 JVM 测试文件、1 个 androidTest 文件、132 个测试方法，状态维持 IN_PROGRESS。
- 2026-07-19 本机 L3 环境复核：Android SDK 中没有 `emulator.exe`、系统镜像或现成 AVD，因此当前无法在“不连接真机”的条件下执行仪器测试；如需继续 L3，必须先授权安装 Emulator + API 34 系统镜像（大体积下载），或连接设备。历史提交 `9e536a4^` 已确认可恢复 v1 实体结构，但完整 `MigrationTestHelper` 仍需在 Android 运行环境执行后才能宣称通过。
- 2026-07-19 在 Redmi Note 12 Turbo（23049RAD8C，Android 15）执行 `connectedDebugAndroidTest`：`AppDatabaseMigrationTest.migration_1_2_adds_limitMode_column_with_default_zero` 1/1 通过（0 failures / 0 errors）。因手机中既有 debug 包与当前本机 debug key 指纹不同，测试采用临时并行 applicationId 安装，运行后测试包和临时构建配置均已清理，未覆盖或清除现有应用数据。本次仅验证现有“手工 v1 建表 + MIGRATION_1_2”androidTest；完整 `MigrationTestHelper`、Compose UI 与 Service/通知/Alarm/降级锁仪器覆盖仍未完成，状态维持 IN_PROGRESS。
- 2026-07-20 为降级锁异步策略回调增加 3 个纯 JVM 用例，覆盖服务非实际可用时展示、恢复后撤销及策略放行；`GuardProtectionHealthEvaluatorTest` 更新为 11/11，完整 JVM 测试、Debug 构建和 lint 均通过。测试资产更新为 28 个 JVM 测试文件、1 个 androidTest 文件、135 个测试方法，状态维持 IN_PROGRESS。
- 2026-07-21 安装并启动 API 34 模拟器；补真实 v1 Room schema 与完整 `MigrationTestHelper` 校验、4 个 Compose 关键交互测试、4 个前台服务/通知/Alarm/降级锁集成测试。最终 JVM 134 项、Android 仪器 9/9、Debug/测试 APK 构建与 lint 均通过；状态 IN_PROGRESS → DONE。
- 2026-07-21 验收口径重置：自动化覆盖的增加是测试资产成果，但不能代替用户人工验收；撤销此前关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-016 · 无依赖注入框架

| 字段      | 值                                                                |
| ------- | ---------------------------------------------------------------- |
| 优先级/严重性 | P3 / 轻微                                                          |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                                    |
| 关联代码    | `GuardAccessibilityService`（组合根）、`AppBlockCoordinator`（~30 构造参数） |
| 来源      | 评价报告 §6 P3#13                                                    |
| 负责人     | —                                                                |

**问题**：手动装配在协作者 ~30 参数规模下已脆弱。
**建议修法**：引入 Hilt/Koin，或至少构造配置数据类收敛参数。
**实际修法（2026-07-19）**：采用台账允许的轻量方案，不额外引入 Hilt/Koin；将 `AppBlockCoordinator` 原 31 个平铺构造参数收敛为 `Dependencies`、`Callbacks`、`Config` 三组，`GuardAccessibilityService` 继续作为明确组合根，运行依赖、环境回调与策略常量的边界清晰分离。
**变更记录**：

- 2026-07-09 创建。
- 2026-07-19 完成三组构造配置收敛，协调器定向测试、完整 JVM 测试、Debug 构建和 lint 均通过；状态 OPEN → DONE。
- 2026-07-21 验收口径重置：此前 `DONE` 由代码结构检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-017 · 轮询优化

| 字段      | 值                             |
| ------- | ----------------------------- |
| 优先级/严重性 | P3 / 轻微                       |
| 类型/状态   | 增强 / PENDING_USER_ACCEPTANCE  |
| 关联代码    | `UsageTrackingManager`（3 秒轮询） |
| 来源      | 评价报告 §6 P3#15                 |
| 负责人     | —                             |

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
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与心跳推算支撑，未取得用户对耗电和稳定性的人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-018 · 系统受保护表面分类器拆分

| 字段      | 值                                                                   |
| ------- | ------------------------------------------------------------------- |
| 优先级/严重性 | P3 / 轻微                                                             |
| 类型/状态   | 技术债 / PENDING_USER_ACCEPTANCE                                       |
| 关联代码    | `WhitelistManager`（仅豁免白名单）；`SystemSurfaceClassifier`（设置/安装器/应用市场分类） |
| 来源      | 评价报告 §6 P3#16 / §8.4 §8.5                                           |
| 负责人     | —                                                                   |

**问题**：四个"家族前缀"方法零调用方；设置、安装器、应用市场分类器住在 `WhitelistManager` 中，曾导致维护者把“拦截表面”误读为“白名单放行”。
**实际修法（2026-07-11）**：删除 `isLauncher`/`isPhoneApp`/`isMessagingApp`/`isCommunicationApp` 与未使用集合；新增 `SystemSurfaceClassifier`，迁移 `isSettingsSurface`、`isInstallerOrMarketSurface`、`isPackageInstallerSurface`、`isAppMarketSurface` 及所有调用方；补子包命中、substring spoof 拒绝、安装器/市场分离单测。**设置前缀匹配逻辑保持不变（见 ISS-002），仅完成命名/归属清理。**
**变更记录**：

- 2026-07-09 创建。
- 2026-07-11 完成死代码删除与分类器拆分，状态 OPEN → DONE。
- 2026-07-21 验收口径重置：此前 `DONE` 由代码检查与自动化测试支撑，未取得用户明确人工验收；撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

### ISS-019 · 重建 issue 台账（本文件）

| 字段      | 值                        |
| ------- | ------------------------ |
| 优先级/严重性 | P3 / 轻微                  |
| 类型/状态   | 规范 / PENDING_USER_ACCEPTANCE |
| 关联代码    | `docs/ISSUES.md`、`docs/统一术语表与UI映射.md` |
| 来源      | 评价报告 §6 P3#14 / §7 第 2 条 |
| 负责人     | —                        |

**问题**：旧 `issues_list.md` 已删除，issue 状态散落各 phase 文档。
**建议修法**：以本文件为唯一台账，按 §0 规范维护。
**变更记录**：

- 2026-07-09 台账建立（初始 20 条）。
- 2026-07-21 验收口径重置：台账已建立不等于用户已验收其内容；状态 DONE → PENDING_USER_ACCEPTANCE，由用户审核后再决定是否关单。
- 2026-07-26 按用户要求建立统一术语与 UI 映射：覆盖全部运行时 Activity 页面、业务对话框、普通应用遮罩、降级保护层、常驻通知、规则/模式/恢复术语及其组件与函数映射；README、AGENTS 和本台账已建立统一入口。该文档仅统一沟通口径，不改变 ISS-019 的用户验收状态。

### ISS-022 · 配置向导“重新检查”无可见反馈

| 字段      | 值                                              |
| ------- | ---------------------------------------------- |
| 优先级/严重性 | P3 / 轻微                                        |
| 类型/状态   | UX 缺陷 / PENDING_USER_ACCEPTANCE                |
| 关联代码    | `SetupWizardComponents.SetupMaintenanceScreen` |
| 来源      | ISS-009 真机冒烟验证                                 |
| 负责人     | —                                              |

**问题**：维护面板每秒自动刷新一次；手动点击“重新检查”时若权限状态没有变化，界面无 Toast、文字或日志反馈，看起来像按钮失效。
**实际修法（2026-07-13）**：手动刷新后显示“检查完成：已配置 X / 6 项”Toast，并写 `SetupMaintenance` 调试日志；自动每秒刷新保持静默，避免重复打扰。
**变更记录**：

- 2026-07-13 真机确认属于既有交互反馈缺失，登记并完成修复。
- 2026-07-21 验收口径重置：用户曾报告看到了 Toast，但未明确作出“ISS-022 验收通过”的结论；按本次统一要求撤销关单效力，状态 DONE → PENDING_USER_ACCEPTANCE。

---

## 6. 暂不纳入本台账（尚未开始，仅备案）

> 以下为"尚未开始"的功能模块，按用户要求暂不纳入 active 台账。需启动时，为每个新建 ISS 并排期；启动前受 §0.8 排序约束（P0/P1 未清前不开始）。

- **第二阶段网络/云端模块**：家长远程管控、跨设备同步、远程下发规则——当前无任何网络代码。
- **防卸载功能开发**：已由 ISS-020 决策（2026-07-22）启动，见 ISS-024（P1，从零软件重建，不恢复旧代码）。
- **多子女 / 多 profile 支持**：数据模型无 profile 维度。
- **统计报表 / 可视化**：有原始数据（DailyUsage 30 天清理），无报表 UI。

---

*本台账基于 2026-07-09 代码快照与评价报告建立。新增/修改请遵循 §0 规范，保持可追溯。*
