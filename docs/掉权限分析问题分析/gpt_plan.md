# KidsPhoneGuard 无障碍掉权限问题分析与变通方案（GPT）

## 1. 本阶段分析范围与结论边界

本报告基于以下信息形成：

- 项目源码静态梳理（无障碍、前台守护、权限检测、恢复引导链路）
- 历史日志文件分析（`adb_extracted_logs_utf8.txt`）
- 实机 USB 连接后的实时只读查询（`adb` 命令）

本阶段**未修改任何代码**，仅进行分析与证据整理。

---

## 2. 核心结论（先给结论）

1. 在“普通应用 + 非 Device Owner”前提下，无法保证 100% 阻止系统关闭无障碍。  
2. 现有证据已能确认：掉权时主要表现为**系统设置层关闭**（`accessibility_enabled=0`、`enabled_accessibility_services` 清空或缺失目标服务），而不是仅服务线程异常。  
3. 当前代码已具备较完整的“检测 + 取证 + 引导恢复”机制，但“自动恢复能力”仍受权限边界限制。  
4. 最可落地突破路线是：**ADB 一次性授权 + 应用内自动回写恢复**（`WRITE_SECURE_SETTINGS`），再叠加纯应用兜底策略。  

---

## 3. 代码现状梳理（与掉权直接相关）

### 3.1 权限状态判定链路

- `PermissionManager.isAccessibilityServiceEnabled()` 同时检查：
  - `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`
  - `AccessibilityManager.getEnabledAccessibilityServiceList(...)`
- 位置：`app/src/main/java/com/kidsphoneguard/utils/PermissionManager.kt`

这说明项目并非单一来源判断，检测逻辑是相对稳健的。

### 3.2 守护与取证链路

- `GuardForegroundService` 具备：
  - 每 10 秒健康刷新（`health_snapshot`）
  - 每 5 秒恢复检查（`accessibility_recovery_check`）
  - `Settings.Secure` 观察器（监听 `ACCESSIBILITY_ENABLED` 与 `ENABLED_ACCESSIBILITY_SERVICES`）
  - 取证日志（`accessibility_forensics`、`process_tree_forensics`）
- 位置：`app/src/main/java/com/kidsphoneguard/service/GuardForegroundService.kt`

### 3.3 无障碍服务生命周期信号

- `GuardAccessibilityService` 已记录：
  - `onCreate/onServiceConnected/onUnbind/onDestroy/onInterrupt/onRebind`
  - 最新生命周期信号共享（`latestLifecycleSignal`）
- 位置：`app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

### 3.4 UI 层监测

- `MainActivity` 权限引导页每秒刷新权限状态，能及时感知降级。
- 位置：`app/src/main/java/com/kidsphoneguard/ui/MainActivity.kt`

---

## 4. 日志与实机查询证据

## 4.1 历史日志证据（已发生掉权样本）

在历史样本中可见多次：

- `accessibility_recovery_check enabled=false|running=false|...|recover=true`
- `accessibility_forensics ... ae=0 ... enabledServices=`

可解释为：

- 掉权时系统无障碍总开关已关闭（`ae=0`）
- 已启用服务列表丢失目标服务
- 前台守护循环仍在跑，但核心能力失效（degraded）

### 4.2 当前设备实时状态（本次分析时）

实时 `adb` 查询结果显示：

- `settings get secure accessibility_enabled` = `1`
- `settings get secure enabled_accessibility_services` 包含 `com.kidsphoneguard/.service.GuardAccessibilityService`
- `dumpsys accessibility` 可见 `Enabled services` 含本服务
- `GuardForegroundService` 健康日志持续 `degraded=false`

即：**当前时刻状态正常，问题是间歇触发**。

### 4.3 辅助状态

- `appops` 中 `BIND_ACCESSIBILITY_SERVICE` 近期存在 `allow` 记录  
- `deviceidle whitelist` 中可见 `com.kidsphoneguard`  

说明并非简单“应用完全被系统杀死”，更符合“某时刻系统策略改写无障碍设置”的模型。

---

## 5. 根因判断（当前可判断与不可判断）

### 5.1 可判断

1. 掉权真实存在，并且直接打断无障碍核心链路。  
2. 掉权时通常是系统设置态异常（总开关/服务列表）。  
3. 现有应用层守护只能“发现问题并引导恢复”，不能无条件越过系统权限边界。  

### 5.2 暂不可判断

1. 具体由哪个系统组件/策略在何时执行了改写（缺少 UID 级“写入者”铁证）。  
2. 是否与某个固定触发场景强绑定（锁屏、省电策略切换、特定系统任务）。  

---

## 6. 变通方案评估（按可落地性排序）

### 方案 A：ADB 一次性授权 `WRITE_SECURE_SETTINGS` + 自动恢复（推荐）

**思路**  
首次通过电脑授予权限，应用检测到 `ae=0` 后自动回写：

- `ENABLED_ACCESSIBILITY_SERVICES`
- `ACCESSIBILITY_ENABLED=1`

**优点**

- 最接近“无感恢复”
- 不依赖 Device Owner
- 对当前自用/家校场景可落地

**代价**

- 每台设备首次都要 ADB 授权
- 需要在产品层面告知家长“初始化步骤”

### 方案 B：纯普通应用兜底（无 ADB）

**思路**  
掉权后进入强提醒/强遮罩，阻止继续使用并引导手动恢复权限。

**优点**

- 不需要电脑，不涉及高危系统授权

**缺点**

- 不是自动恢复，体验有明显打断

### 方案 C：高风险系统组件处理（不建议默认路径）

不建议作为标准方案。可复制性差，风险与维护成本高，不适合作为常规产品路径。

---

## 7. 建议的工程策略（分层）

1. **主路径**：方案 A（ADB 授权自动恢复）  
2. **兜底路径**：方案 B（强提示/强遮罩引导手动恢复）  
3. **运维路径**：持续增强掉权前后取证，形成“触发场景画像”  

这三层组合后，能够把“不可控掉权”转为“可观测、可恢复、可兜底”。

---

## 8. 下一阶段建议（准备进入代码改造前）

在下一次复现窗口，建议先执行一轮标准化抓取：

```powershell
adb logcat -c
adb logcat -v time -s GuardForegroundService:W GuardAccessibilityService:D AccessibilityManagerService:D ActivityManager:I AndroidRuntime:E
```

复现后立即补充快照：

```powershell
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility
adb shell cmd appops get com.kidsphoneguard
```

判读重点：

- `accessibility_enabled` 是否由 `1 -> 0`
- `enabled_accessibility_services` 是否丢失本服务
- 同时窗 `AccessibilityManagerService/ActivityManager` 是否出现可归因事件

---

## 9. 本报告对应的项目决策建议

如果目标是“普通应用尽可能稳定”而非“必须零掉权”：

- 建议立项执行：**ADB 授权自动恢复 + 纯应用兜底**
- 同时保留你当前已有的取证机制，继续积累系统侧证据

这样做不是“完美消灭系统策略”，但能在现实权限边界内获得最优可用性与可维护性。
