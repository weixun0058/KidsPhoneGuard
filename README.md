# 拉钩守护（KidsPhoneGuard）

纯本地运行的 Android 儿童防沉迷 / 家长管控应用。所有管控逻辑基于设备本地数据库，无云端依赖。

- 应用显示名：**拉钩守护**（副标题：儿童防沉迷家长管理）
- 包名：`com.kidsphoneguard`
- 最低 SDK：26（Android 8.0）／目标 SDK：34（Android 14）

## 项目结构

```
app/src/main/java/com/kidsphoneguard/
├── KidsPhoneGuardApp.kt              # Application 入口：初始化数据库、仓库、通知通道
├── data/
│   ├── db/                          # Room 数据库
│   │   ├── AppDatabase.kt
│   │   ├── AppRuleDao.kt
│   │   └── DailyUsageDao.kt
│   ├── model/
│   │   ├── AppRule.kt               # 应用规则实体（RuleType / LimitMode）
│   │   └── DailyUsage.kt            # 每日使用时长实体
│   └── repository/
│       ├── AppRuleRepository.kt
│       └── DailyUsageRepository.kt
├── engine/                          # 决策引擎（纯逻辑，只读数据）
│   ├── LockDecisionEngine.kt        # 拦截决策单例
│   ├── BlockDecision.kt             # 决策结果
│   ├── BlockReason.kt               # 拦截原因
│   └── settingsprotection/          # 系统设置页保护策略
│       ├── BrandSettingsRules.kt
│       ├── ProtectedSettingsDecision.kt
│       ├── ProtectedSettingsPolicy.kt
│       └── SettingsPageSnapshot.kt
├── service/
│   ├── GuardAccessibilityService.kt # 核心监控引擎（组合根，仅生命周期 + 装配）
│   ├── GuardForegroundService.kt    # 前台保活 + 看门狗 + 无障碍掉权检测 + 降级锁屏 + 取证
│   ├── OverlayService.kt            # 全屏拦截遮罩服务
│   ├── DegradedLockManager.kt       # 无障碍掉权时的降级全屏锁定遮罩
│   ├── GuardHealthState.kt          # 无障碍/统计心跳状态（SharedPreferences）
│   ├── UsageTrackingManager.kt      # 使用时长轮询统计（无障碍失效时的补充拦截）
│   ├── AppBlockerService.kt         # 【已废弃空壳】逻辑已并入无障碍服务
│   ├── accessibility/               # 无障碍事件路由与运行时支持
│   │   ├── AccessibilityEventRouter.kt
│   │   ├── AssistantOverlayRoutingSupport.kt
│   │   ├── SelfAppEventHandler.kt
│   │   ├── ServiceRuntimeSupport.kt
│   │   ├── EventRoutingState.kt
│   │   ├── GuardActionResult.kt
│   │   └── WindowInspectorSnapshotApi.kt
│   ├── block/                       # 拦截执行链
│   │   ├── AppBlockCoordinator.kt   # 拦截协调中枢
│   │   ├── BlockSessionController.kt
│   │   ├── BlockSessionState.kt
│   │   ├── GuardActionScheduler.kt  # 动作调度（按 owner/key 去重）
│   │   ├── NavigationExecutor.kt    # BACK/HOME 等导航动作
│   │   ├── OverlayCoordinator.kt    # 遮罩显隐协调
│   │   └── LockDecisionEngineProvider.kt
│   └── guard/                       # 自我保护守卫
│       ├── ProtectedSurfaceGuard.kt # 受保护界面（设置/权限页）守卫
│       ├── ProtectedSurfaceState.kt
│       ├── SystemSurfaceGuard.kt    # 系统面板（通知栏/控制中心）守卫
│       ├── WeChatFinderGuard.kt     # 微信视频号入口守卫
│       └── oem/
│           └── HuaweiPowerSaveHandler.kt # 华为/荣耀省电模式处理
├── receiver/
│   └── ScreenStateReceiver.kt       # 屏幕亮灭 / 开机 / 更新 / 看门狗广播接收器
├── ui/
│   ├── MainActivity.kt              # 权限引导向导（含 SetupWizardActivity 类）
│   ├── ConfigActivity.kt            # 家长配置页
│   ├── PasswordSettingsActivity.kt  # 密码设置页
│   └── components/
│       └── PasswordDialog.kt
└── utils/
    ├── SettingsManager.kt           # 全局设置（全局锁/解锁、品牌确认、临时设置放行等）
    ├── PasswordManager.kt           # 家长密码（PBKDF2 加盐哈希）
    ├── TrustedTimeProvider.kt       # 可信时间（防系统时间篡改绕过限额/时段，ISS-001）
    ├── WhitelistManager.kt          # 系统白名单 / 设置 / 安装器识别
    ├── PermissionManager.kt         # 权限状态检查
    ├── AppScanner.kt                # 已安装应用扫描
    ├── TemporaryBonusManager.kt     # 临时时长奖励
    └── BroadcastPermissionHelper.kt # 内部广播（signature 权限保护）
```

> 注：`SetupWizardActivity` 未单独成文件，其类定义位于 `ui/MainActivity.kt` 内，AndroidManifest 中作为独立 Activity 注册。

## 核心功能

### 1. 权限引导向导（MainActivity / SetupWizardActivity）
向导式逐步引导开启核心权限：
- 家长密码设置
- 悬浮窗权限（`SYSTEM_ALERT_WINDOW`）
- 使用情况访问（`PACKAGE_USAGE_STATS`）
- 忽略电池优化（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
- 品牌后台保活设置确认（按厂商给出对应引导）
- 无障碍服务（`BIND_ACCESSIBILITY_SERVICE`）

### 2. 家长配置页（ConfigActivity）
- 按应用添加管控规则，支持 3 种规则类型：
  - **放行（ALLOW）**：不受限制
  - **永久禁用（BLOCK）**：完全禁止打开
  - **限时/限时段（LIMIT）**：按时长（`dailyAllowedMinutes`）和/或时段（`blockedTimeWindows`）限制，由 `LimitMode` 控制校验哪一种
- 全局一键锁机 / 全局解锁
- 进入配置页需家长密码

### 3. 核心监控引擎（GuardAccessibilityService）
经过 Phase 1–8 模块化重构后，`GuardAccessibilityService` 本身**只承担 Android 生命周期入口与组合根**职责，实际逻辑分散到 `accessibility/`、`block/`、`guard/` 子包中：
- `AccessibilityEventRouter` 统一分发窗口状态变化事件（500ms 去抖）
- `AppBlockCoordinator` 读取 `LockDecisionEngine` 决策并执行拦截
- 各 `guard/` 守卫负责系统设置页、系统面板、微信视频号、华为省电模式等自我保护场景

### 4. 拦截遮罩（OverlayService）
- 全屏半透明遮罩（`TYPE_APPLICATION_OVERLAY`）阻止继续使用受限应用
- 拦截所有触摸事件，显示被拦截应用名称
- 显隐状态由静态字段管理，`OverlayCoordinator` 协调（注意竞态，见下）

### 5. 使用时长统计（UsageTrackingManager）
- 基于 `UsageStatsManager` 每 3 秒轮询前台应用并累加时长
- 写入 Room 数据库；达到限制时，若无障碍在线则发内部广播拦截，否则直接拉起遮罩
- 屏幕熄灭时暂停累加；作为无障碍失效时的**补充**统计与兜底拦截

### 6. 保活与自我保护
- `GuardForegroundService`：常驻通知前台服务 + WakeLock + AlarmManager 看门狗（默认 10 分钟）+ keep-alive 循环
- 监听无障碍设置变化（ContentObserver），**掉权时通过 `DegradedLockManager` 显示不可绕过的降级锁屏**，权限恢复后自动解除
- 设备内取证：关键事件写入 `getExternalFilesDir("forensics")/accessibility_forensics.log`
- `ScreenStateReceiver`：开机自启、亮灭屏、应用更新、看门狗广播

## 架构特点

### 单向数据流
```
ConfigActivity / SetupWizard (UI)
        ↓ 写
AppRuleRepository / SettingsManager → Room / SharedPreferences
        ↓ 读
LockDecisionEngine（只读，不写库）
        ↓
GuardAccessibilityService → AppBlockCoordinator
        ↓
OverlayService / NavigationExecutor（遮罩 + BACK/HOME）
```
管控引擎只读取存储，规则变更自动生效，无需重启服务。

### 数据驱动 + 模块化
- 所有管控逻辑基于本地存储状态
- 无障碍服务经拆分为 router / block / guard 三族协作者，便于单元测试（`app/src/test` 下含 13 个测试文件）

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose（Material3）
- 数据库：Room + Kotlin Coroutines Flow
- 架构：MVVM + 单向数据流
- 构建：Gradle KTS + KSP（Room 注解处理）

## 安全现状

- **家长密码**：PBKDF2-HMAC-SHA256，随机 16 字节盐，120,000 次迭代，256bit（`PasswordManager`）。ISS-013 已淘汰明文密码分支，启动时清理残留明文。
- **系统时间篡改防护**：`TrustedTimeProvider` 基于 `SystemClock.elapsedRealtime()`（单调时钟）+ 持久化锚点检测时间倒拨/前拨跨午夜；篡改时冻结每日限额累计（不清零）、时段规则短路为拦截，并写取证日志；家长验证密码后解除冻结（ISS-001）。
- **内部广播**：`ACTION_BLOCK_APP` 等由 signature 级自定义权限 `com.kidsphoneguard.permission.INTERNAL_GUARD_BROADCAST` 保护，外部应用无法触发。

## 已知待办 / 技术债

- **全局锁双源**：✅ 已修复（ISS-004）。`LockDecisionEngine` 统一以 `SettingsManager.isGlobalLockEnabled()` 为单一真相源，`AppRule.isGlobalLocked` 字段保留但退役，死代码已清理。
- **白名单前缀匹配**：`WhitelistManager` 使用 `startsWith("$family.")` 匹配子包，存在子包名伪装绕过风险，建议改精确匹配或受控前缀。
- **遮罩状态竞态**：✅ 已修复（ISS-005）。`OverlayService` 的读写全部收口到 `OverlayCoordinator`，外部不再直接调用。
- **轮询开销**：✅ 已优化（ISS-017）。`UsageTrackingManager` 熄屏降频到 10s（亮屏 3s），节电约 3 倍且保心跳。
- 未决问题：ISSUE-004（微信视频号识别）、ISSUE-005（华为/荣耀省电模式真机验证）、保护设置关键词与响应速度调优。
- Phase 7 代码与单测通过，但**设备回归验证尚未完成**（清单见 `docs/plans/2026-05-24-phase-7-closeout-summary.md`）。

## 构建与调试命令

```bash
# 构建
.\gradlew.bat build

# Lint
.\gradlew.bat lint

# 单元测试
.\gradlew.bat testDebugUnitTest

# 安装 Debug 包
.\gradlew.bat installDebug

# 查看日志
adb logcat -s KidsPhoneGuard:D GuardAccessibilityService:D GuardForegroundService:D
```

## 权限清单

| 权限 | 用途 |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | 拦截遮罩 / 降级锁屏 |
| `BIND_ACCESSIBILITY_SERVICE` | 核心监控 |
| `PACKAGE_USAGE_STATS` | 使用时长统计 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 保活 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 前台保活服务 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `POST_NOTIFICATIONS` | 常驻通知 |
| `KILL_BACKGROUND_PROCESSES` | 强停受限应用 |
| `WAKE_LOCK` | 保活 |
| `WRITE_SECURE_SETTINGS` | ADB 授予后程序化恢复无障碍（`DegradedLockManager.tryProgrammaticRecovery`，高级/企业版） |
| `INTERNAL_GUARD_BROADCAST`（自定义，signature） | 内部广播保护 |

## PC 侧自动取证器

当 `com.kidsphoneguard` 的无障碍状态从正常变为异常时，PC 侧脚本自动导出证据包，无需人工盯守。

- 脚本：`scripts/pc_forensics_watch.py`
- 默认输出：`forensics/pc-watch/`
- 环境：Windows、Python 3、`adb` 可用、手机 USB 在线

```powershell
python -S .\scripts\pc_forensics_watch.py --poll-seconds 30
```

常用参数：

- 指定设备：`--serial <device-serial>`
- 启动前清空 logcat：`--clear-logcat-on-start`
- 异常时抓 bugreport：`--bugreport-on-incident`
- 修改输出目录：`--output-dir D:\guard-forensics`
- 先验证一次：`--max-polls 1`
- 时间线窗口：`--incident-context-minutes 30`

输出内容：

- `timeline.jsonl`：持续追加的状态时间线（ISS-012：按 5MB 大小轮转，保留 `timeline.prev.jsonl` 最近一份历史）
- `latest_state.json`：最近一次轻量快照
- `session_config.json`：本次监控会话配置
- `incidents/<timestamp>-<reason>/`：异常时导出的证据包（含 `metadata.json`、`dumpsys_accessibility.txt`、`dumpsys_package_main.txt`、`secure_settings.txt`、`logcat_main_system_events.txt` 及事发前精简时间线）

正常运行时终端持续打印 `health=healthy`；掉权或服务条目变化时打印 `Captured evidence bundle: ...`。

## MIUI / 小米设备兼容

MIUI 对后台服务限制严格，需额外设置：

1. 电池优化设为「无限制」
2. 安全中心开启自启动
3. 在最近任务锁定应用
4. 完整授予悬浮窗权限
5. 无障碍服务可能需要定期重新开启

详见 `小米手机应用拦截失效问题解决方案.md`（如存在）。

## 使用说明

1. 安装后打开主界面，按向导逐步开启权限
2. 进入「家长配置」添加管控规则
3. 打开被限制应用验证拦截效果

## 开发里程碑

- [x] M1：基础框架与权限页
- [x] M2：本地配置 UI 与数据流连通
- [x] M3：无障碍监控与屏幕广播
- [x] M4：拦截引擎与 UI 层
- [x] M5：时长统计与自我保护
- [x] 重构：GuardAccessibilityService 模块化拆分（Phase 1–8，见 `docs/plans/`）
