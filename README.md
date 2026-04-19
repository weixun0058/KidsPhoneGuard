# 儿童手机守护 App - MVP 阶段

根据第一阶段开发文档实现的纯本地Android防沉迷应用。

## 项目结构

```
app/src/main/java/com/kidsphoneguard/
├── data/
│   ├── db/                    # Room 数据库
│   │   ├── AppDatabase.kt     # 数据库实例
│   │   ├── AppRuleDao.kt      # 应用规则DAO
│   │   └── DailyUsageDao.kt   # 每日使用时长DAO
│   ├── model/                 # 数据模型
│   │   ├── AppRule.kt         # 应用规则实体
│   │   └── DailyUsage.kt      # 每日使用统计实体
│   └── repository/            # 数据仓库
│       ├── AppRuleRepository.kt
│       └── DailyUsageRepository.kt
├── receiver/
│   └── ScreenStateReceiver.kt # 屏幕状态广播接收器
├── service/
│   ├── GuardAccessibilityService.kt  # 核心无障碍服务（监控引擎）
│   ├── GuardForegroundService.kt     # 前台服务（保活）
│   ├── OverlayService.kt             # 悬浮窗拦截服务
│   └── UsageTrackingManager.kt       # 使用时长统计管理器
├── ui/
│   ├── MainActivity.kt        # 主界面（权限引导页）
│   └── ConfigActivity.kt      # 配置界面（家长配置页）
├── utils/
│   └── PermissionManager.kt   # 权限管理工具
└── KidsPhoneGuardApp.kt       # Application入口
```

## 核心功能

### 1. 权限引导页 (MainActivity)
- 引导用户开启4大核心权限：
  - 悬浮窗权限 (SYSTEM_ALERT_WINDOW)
  - 无障碍服务 (BIND_ACCESSIBILITY_SERVICE)
  - 使用统计权限 (PACKAGE_USAGE_STATS)
  - 忽略电池优化 (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)

### 2. 家长配置页 (ConfigActivity)
- 添加应用管控规则
- 支持3种规则类型：
  - **放行 (ALLOW)**：不受限制
  - **永久禁用 (BLOCK)**：完全禁止打开
  - **限时/限时段 (LIMIT)**：按时长或时段限制
- 全局锁机功能

### 3. 核心监控引擎 (GuardAccessibilityService)
- 通过无障碍服务实时监控前台应用
- 毫秒级响应应用切换事件
- 根据数据库规则执行拦截/放行决策
- 防卸载保护：检测系统设置中的敏感操作

### 4. 拦截覆盖层 (OverlayService)
- 全屏红色覆盖层，阻止儿童继续使用受限应用
- 提供"返回桌面"按钮
- 通过无障碍服务执行返回桌面操作

### 5. 使用时长统计 (UsageTrackingManager)
- 基于 UsageStatsManager 轮询统计
- 每3秒更新一次使用时长
- 自动写入 Room 数据库
- 达到限制时触发拦截

### 6. 保活机制
- 前台服务 + 常驻通知
- 屏幕亮灭广播控制统计启停
- 开机自启动

## 架构特点

### 低耦合设计
- **单向数据流**：配置UI → 数据库 → 管控引擎
- 管控引擎只读取数据库，不直接修改规则
- 使用 Kotlin Flow 实现响应式数据更新

### 数据驱动
- 所有管控逻辑基于本地数据库状态
- 规则变更自动生效，无需重启服务
- 为第二阶段网络模块接入预留接口

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose
- **数据库**：Room + Kotlin Coroutines Flow
- **架构**：MVVM
- **最低SDK**：26 (Android 8.0)
- **目标SDK**：34 (Android 14)

## Lint/TypeCheck 命令

- Android Lint: `.\gradlew.bat lint`
- 编译检查: `.\gradlew.bat build`
- 完整检查: `.\gradlew.bat check`

## 使用说明

1. 安装应用后，打开主界面
2. 按顺序开启4个必要权限
3. 进入"家长配置"页面
4. 添加需要管控的应用规则
5. 测试：打开被限制的应用，验证拦截功能

## PC 侧自动取证器

- 目的：当 `com.kidsphoneguard` 的无障碍状态从正常变为异常时，PC 侧脚本会自动导出一整包证据，不需要人工盯守手机。
- 脚本位置：`scripts/pc_forensics_watch.py`
- 默认输出目录：`forensics/pc-watch/`
- 运行环境：Windows、Python 3、`adb` 可用、手机通过 USB 保持在线

### 启动命令

```powershell
python -S .\scripts\pc_forensics_watch.py --poll-seconds 30
```

### 常用参数

- 指定设备：`python -S .\scripts\pc_forensics_watch.py --serial <device-serial>`
- 启动前清空 logcat：`python -S .\scripts\pc_forensics_watch.py --clear-logcat-on-start`
- 异常时额外抓取 bugreport：`python -S .\scripts\pc_forensics_watch.py --bugreport-on-incident`
- 修改输出目录：`python -S .\scripts\pc_forensics_watch.py --output-dir D:\guard-forensics`
- 先跑 1 次验证：`python -S .\scripts\pc_forensics_watch.py --max-polls 1`
- 调整 incident 附带的时间线窗口：`python -S .\scripts\pc_forensics_watch.py --incident-context-minutes 30`

### 输出内容

- `timeline.jsonl`：持续追加的状态时间线
- `latest_state.json`：当前最近一次轻量快照
- `session_config.json`：本次监控会话配置
- `incidents/<timestamp>-<reason>/`：异常发生时自动导出的证据包
- `incidents/<timestamp>-<reason>/recent_timeline_context.json`：事发前最近一段时间的精简时间线，默认 30 分钟

### 如何判断脚本工作正常

- 正常状态下，终端会持续打印 `health=healthy`
- 如果掉权限或服务条目变化，终端会打印 `Captured evidence bundle: ...`
- 异常目录里会包含：
  - `metadata.json`
  - `dumpsys_accessibility.txt`
  - `dumpsys_package_main.txt`
  - `secure_settings.txt`
  - `logcat_main_system_events.txt`

## 注意事项

1. **无障碍服务**：安装/更新后需要重新开启
2. **电量优化**：建议将应用加入电池优化白名单
3. **包名获取**：部分Android 11+设备可能需要特殊处理

## 开发里程碑

- [x] M1：基础框架与权限页
- [x] M2：本地配置UI与数据流连通
- [x] M3：无障碍监控与屏幕广播
- [x] M4：拦截引擎与UI层开发
- [x] M5：时长统计与自我保护
