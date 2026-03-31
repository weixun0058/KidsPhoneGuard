# KidsPhoneGuard 无障碍掉权问题 — 商业化可行方案

## 核心约束

> **本应用要商业化，不能要求用户执行任何 ADB 命令。**
> 所有方案必须在"普通应用 + 普通用户 + 不插电脑"的前提下可行。

---

## 第一部分：三份 AI 报告对比与筛选

### 各方案在商业化约束下的存活情况

| 方案 | 来源 | 需要 ADB？ | 商业化可行？ | 说明 |
|------|------|-----------|-------------|------|
| WRITE_SECURE_SETTINGS 自动恢复 | Claude/Gemini/GPT | ✅ 需要 | ❌ 不可行 | 技术验证通过，但需用户插电脑执行一次 ADB |
| 卸载 powergenie | Gemini 路线A | ✅ 需要 | ❌ 不可行 | 过于激进，且需 ADB |
| Shizuku 集成 | Claude | ✅ 需要 | ❌ 不可行 | 需安装额外应用+ADB启动Shizuku |
| 多进程隔离 | Claude | ❌ | ⚠️ 部分可行 | 对进程被杀有效，对设置层关闭无效 |
| **isAccessibilityTool 声明** | Claude | ❌ | ✅ **可行** | 1行XML，减少被系统禁用概率 |
| **强遮罩/锁屏压制** | Gemini 路线C / GPT 方案B | ❌ | ✅ **可行** | 纯应用方案，强制引导用户恢复 |
| **华为专项设置引导** | Claude/GPT | ❌ | ✅ **可行** | 首次使用时引导用户完成厂商设置 |
| **取证增强+场景画像** | GPT | ❌ | ✅ **可行** | 持续积累证据，优化策略 |

### 三份报告的共识点

1. **根因一致**：掉权是华为 `iaware`/`PowerGenie` 在系统设置层关闭无障碍，不是进程被杀
2. **进程活着**：前台服务一直在运行（procImp=125），进程没有被杀
3. **电池优化无关**：`ignoreBattery=true` 但仍然掉权，说明是独立的辅助功能治理机制
4. **isAccessibilityTool**：当前缺失，应立即添加

### 三份报告中值得吸收的独特观点

| 来源 | 独特观点 | 价值 |
|------|----------|------|
| **Gemini** | 路线C "暴政弹窗压制"——掉权后变砖,逼用户恢复 | 最核心的商业化兜底方案。只有这个方案完全不需要ADB |
| **GPT** | 分层策略思路："可观测→可恢复→可兜底" | 架构思维清晰。不追求"消灭问题"，而是"管理问题" |
| **GPT** | 持续取证形成"触发场景画像"| 长期主义，通过数据积累找到精确的触发模式 |
| **Claude** | ADB写入实验已验证可行 | 虽商业化不直接用，但**可作为售后远程支援工具** |

---

## 第二部分：商业化可行的分层方案

### 整体架构

```
第一层：预防 — 减少系统关闭无障碍的概率
第二层：检测 — 第一时间感知掉权
第三层：兜底 — 掉权后仍能保持基本管控
第四层：恢复 — 高效引导用户重新开启
```

---

### 第一层：预防（减少触发概率）

#### 1.1 添加 isAccessibilityTool 属性 【P0 - 立即实施】

```xml
<service
    android:name=".service.GuardAccessibilityService"
    android:exported="true"
    android:isAccessibilityTool="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
```

**原理**：Android 12+ 对 `isAccessibilityTool=true` 的服务更宽容，系统层面减少自动禁用。当前 dumpsys 确认该值为 `false`。

**成本**：1行XML修改，零风险。

#### 1.2 首次使用华为专项设置向导 【P0 - 立即实施】

在 `MainActivity` 首次运行时（或检测到华为设备时），展示**分步引导页面**：

1. **应用启动管理** → 关闭"自动管理" → 开启允许自启动/关联启动/后台活动
2. **电池优化** → 设为"不优化"
3. **后台锁定** → 在最近任务中下拉锁定应用
4. **（如有）辅助功能安全设置** → 允许受限制的设置

每步配截图引导，用户完成后标记为"已配置"。

**原理**：这是所有商业化的后台保活应用（如 AppBlock、青少年守护等）的标准做法。

#### 1.3 accessibility_service_config.xml 优化 【P1】

考虑精简监听的事件类型，减少系统认为"该服务资源消耗过大"的可能：

```xml
<!-- 当前：已经比较精简了 -->
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged"
```

当前配置已比较合理，暂不需要修改。

---

### 第二层：检测（已基本完善，可小优化）

当前代码已具备：
- ContentObserver 监听设置变化（实时）
- 5秒恢复检查循环
- 10秒健康快照
- 心跳机制（4秒）

**小优化建议**：

#### 2.1 消除无效的引导弹出

当前在 `interactive=false`（屏幕关闭）时仍会尝试打开无障碍设置页，这是无效操作。应增加判断：

```kotlin
// 屏幕关闭时不要弹设置页，避免无效操作
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
if (!powerManager.isInteractive) {
    Log.d(TAG, "Screen off, skip accessibility guide")
    return  // 等屏幕亮了再引导
}
```

---

### 第三层：兜底（核心商业化方案）⭐⭐⭐

这是 Gemini 路线C 的思路，也是商业化约束下最关键的能力。

#### 3.1 掉权后的强制锁定遮罩 【P0 - 核心】

**原理**：当无障碍掉权后，虽然无法拦截具体应用，但**悬浮窗权限 (SYSTEM_ALERT_WINDOW) 是独立的，不受无障碍影响**。利用已有的悬浮窗能力，在掉权期间显示一个**全屏、置顶、无法绕过**的锁定界面。

**行为设计**：

```
检测到 ae=false（无障碍掉权）
    ↓
等待屏幕亮起（不在锁屏状态弹出）
    ↓
显示全屏锁定覆盖层：
  - TYPE_APPLICATION_OVERLAY，最高层级
  - 全屏覆盖，拦截所有触摸事件
  - 显示内容：
    "🔒 家长保护功能需要重新启用"
    "为了保护手机使用安全，请按以下步骤操作："
    [一键跳转无障碍设置] 按钮
  - 底部小字："如需帮助，请联系家长"
  - 无返回键、无Home键可逃逸（窗口拦截 BACK 和 HOME 键行为）
```

**关键实现要点**：

```kotlin
// 在 GuardForegroundService 中
private fun showDegradedLockScreen() {
    if (!PermissionManager.canDrawOverlays(this)) return
    if (!powerManager.isInteractive) return  // 等屏幕亮
    
    // 显示全屏不可取消的遮罩
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )
    // ... 显示锁定UI，只有"去设置"一个可点击按钮
}
```

**为什么这个方案比"啥都不做等用户发现"好得多**：
- 孩子无法绕过全屏遮罩继续使用手机
- 相当于把手机"锁住"了，效果接近无障碍拦截
- 家长或孩子只需点一下按钮就能跳转设置页恢复

#### 3.2 利用 UsageStatsManager 做降级监控 【P1】

当无障碍掉权后，`UsageTrackingManager` 仍然在运行。可以利用它做**降级模式**的应用监控：

```kotlin
// 降级模式：无障碍掉权时，通过 UsageStats 检测前台应用
// 如果发现被禁应用在前台，立即显示锁定覆盖层
private fun degradedModeCheck() {
    if (isProtectionDegraded && canDrawOverlays) {
        val foregroundApp = resolveRecentForegroundPackage(System.currentTimeMillis())
        val decision = lockDecisionEngine.getBlockDecision(foregroundApp)
        if (decision.shouldBlock) {
            showDegradedBlockOverlay(foregroundApp, decision.appName)
        }
    }
}
```

虽然没有无障碍快，但3秒轮询 + 悬浮窗覆盖，仍能形成有效的约束力。

---

### 第四层：恢复引导（优化用户体验）

#### 4.1 智能恢复引导时机 【P1】

不要在屏幕关闭时弹引导，应该在以下时机触发：

1. **屏幕亮起时** — 注册 `ACTION_SCREEN_ON` 广播
2. **用户打开本应用时** — `MainActivity.onResume()`
3. **用户解锁手机后** — `ACTION_USER_PRESENT`

#### 4.2 一键直达无障碍开关 【P0】

当前代码已有 `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` 直跳，这是正确的做法。确保在锁定遮罩上的按钮也使用此方式。

#### 4.3 恢复成功自动解除锁定 【P0】

ContentObserver 检测到 `accessibility_enabled` 变回 1 时，立即：
1. 移除锁定遮罩
2. 显示"✅ 保护已恢复"的短暂提示
3. 恢复正常监控

---

## 第三部分：关于 ADB 方案的定位调整

虽然商业化不能要求用户执行 ADB，但 ADB 方案仍有价值：

1. **作为"高级售后工具"**：当家长反馈问题频繁时，客服可远程指导连电脑执行一次
2. **作为"企业版/学校版"功能**：学校/企业批量部署时，IT管理员可统一执行
3. **在 Manifest 中仍然声明 WRITE_SECURE_SETTINGS**：不声明不花钱，声明了至少为高级场景留了口子

---

## 第四部分：实施优先级

| 优先级 | 改动 | 文件 | 工作量 | 效果 |
|--------|------|------|--------|------|
| **P0** | 添加 `isAccessibilityTool="true"` | AndroidManifest.xml | 1行 | 减少系统自动禁用 |
| **P0** | 声明 `WRITE_SECURE_SETTINGS`（预留） | AndroidManifest.xml | 1行 | 为高级场景留口子 |
| **P0** | 掉权后全屏锁定遮罩 | 新建 `DegradedLockManager.kt` + 修改 `GuardForegroundService.kt` | 中等 | **核心兜底能力** |
| **P0** | 锁定遮罩上的一键恢复按钮 | 同上 | 包含在上面 | 快速恢复体验 |
| **P0** | 恢复后自动解除锁定 | `GuardForegroundService.kt` | 少量 | 闭环体验 |
| **P1** | 屏幕关闭时不弹引导 | `GuardForegroundService.kt` | 几行 | 避免无效操作 |
| **P1** | 降级模式（UsageStats+覆盖层拦截） | `GuardForegroundService.kt` / `UsageTrackingManager.kt` | 中等 | 掉权期间仍有约束力 |
| **P1** | 华为首次使用引导向导 | `MainActivity.kt` + 新建引导页 | 较多 | 减少触发频率 |
| **P2** | WRITE_SECURE_SETTINGS 自动恢复逻辑 | `PermissionManager.kt` + `GuardForegroundService.kt` | 中等 | 仅对已授权设备生效 |

---

## Open Questions

1. **锁定遮罩的严格程度**：是完全不可退出（只有恢复权限才能解除），还是提供密码解锁？如果孩子主动去关闭了无障碍，家长不在身边，是否允许孩子通过某种方式临时解锁？
2. **降级模式是否实施**：掉权期间利用 UsageStats + 悬浮窗做降级拦截，这个功能是否需要？它能提供"有延迟但仍有效"的管控。
3. **华为引导向导的优先级**：需要制作截图和分步UI，工作量较大，是否放在第一批改动中？
4. **是否确认可以开始代码修改？**
