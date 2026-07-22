# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

KidsPhoneGuard is a local Android application for parental control and screen time management for children. It runs entirely on-device with no cloud dependencies in the MVP phase.

**Core Architecture**: The app uses a data-driven control flow where UI configuration writes to a Room database, and the monitoring engine reads from the database to enforce rules. Services are designed to be keep-alive and resilient against being killed by the system.

## Build & Development Commands

```bash
# Build
.\gradlew.bat build

# Lint
.\gradlew.bat lint

# Clean and rebuild
.\gradlew.bat clean build

# Install debug APK
.\gradlew.bat installDebug

# View logs
adb logcat -s KidsPhoneGuard:D GuardAccessibilityService:D GuardForegroundService:D

# Unit tests
.\gradlew.bat testDebugUnitTest

# Monitor specific events
adb logcat -d | grep "关键词"
```

## Debugging & Troubleshooting Rules (CRITICAL)

**Rule 1: Always check logs before modifying code**

When investigating runtime issues (crashes, exceptions, performance issues), the order is:
1. Check device logs with `adb logcat`
2. Analyze error stacks and exception messages
3. Locate the issue based on logs
4. Fix the code
5. Verify the fix by retesting

**Forbidden actions:**
- ❌ Do not modify code based on guesses
- ❌ Do not make "trial" fixes without logs
- ❌ Do not ignore warnings and errors in logs

**Rule 2: Fix one issue at a time**

Each fix should target one specific problem. Verify the solution before moving to the next. Avoid modifying multiple places at once.

**Rule 3: Preserve log output**

Add logs for key operations using `Log.d/e/w`. Use class names as tags: `private const val TAG = "ClassName"`. Always log exceptions: `Log.e(TAG, "description", e)`.

**Rule 4: Verify before proceeding**

After a fix, ask the user to verify the result. Never assume a fix is successful. If it fails, go back to analyzing logs.

**Rule 5: Final acceptance belongs to the user**

Unit tests, instrumentation tests, emulators, ADB, logs, UI trees, and code inspection are development self-check or diagnostic evidence only. They must never be used by an agent to mark an issue `DONE`, claim closeout, or judge real-world latency/experience as acceptable. Only the user's explicit manual acceptance may close an issue. `WONTFIX` likewise requires an explicit user product decision. See `docs/ISSUES.md` §0.9.

## Core Architecture

### Service Architecture

After the Phase 1–8 modular refactor, `GuardAccessibilityService` is only the Android lifecycle entrypoint and **composition root**; business logic lives in `accessibility/`, `block/`, and `guard/` sub-packages.

1. **GuardAccessibilityService** - Lifecycle entrypoint + composition root
   - `onAccessibilityEvent` delegates to `AccessibilityEventRouter` (500ms debounce via `ServiceRuntimeSupport`)
   - Wires all coordinators in `service/block/` and guards in `service/guard/`
   - Does **not** itself execute blocking; delegates to `AppBlockCoordinator`

2. **service/accessibility/** - Event routing & runtime support
   - `AccessibilityEventRouter` - dispatches window events to handlers
   - `ServiceRuntimeSupport` - heartbeat, protected-window sweep, internal block receiver ownership
   - `AssistantOverlayRoutingSupport`, `SelfAppEventHandler`, `WindowInspectorSnapshotApi`, `EventRoutingState`

3. **service/block/** - Blocking execution chain
   - `AppBlockCoordinator` - core coordinator: policy check + execute block
   - `BlockSessionController` / `BlockSessionState` - block session lifecycle
   - `GuardActionScheduler` - debounced/deferred navigation actions, keyed by owner + key
   - `NavigationExecutor` - BACK / HOME / force-stop actions
   - `OverlayCoordinator` - overlay show/hide coordination
   - `LockDecisionEngineProvider` - engine init & access

4. **service/guard/** - Self-protection guards
   - `ProtectedSurfaceGuard` - blocks escaping into Settings / permission pages
   - `SystemSurfaceGuard` - collapses notification shade / control center
   - `WeChatFinderGuard` - blocks WeChat 视频号 entry
   - `oem/HuaweiPowerSaveHandler` - retained Huawei/Honor power-save compatibility code; device validation is outside the current required product scope (ISS-008 WONTFIX)

5. **OverlayService** - Blocking overlay window
   - Full-screen `TYPE_APPLICATION_OVERLAY`, touch-blocking, shows blocked app name
   - Static state guarded by `stateLock`; shown/hidden via `OverlayCoordinator` (race risk remains)

6. **UsageTrackingManager** - Usage time tracker (singleton object, not a Service)
   - Polls `UsageStatsManager` every 3 seconds
   - Writes accumulated time to Room; triggers blocking when limit exceeded
   - Works as fallback when accessibility service is down

7. **GuardForegroundService** - Keep-alive + watchdog + degraded lock + forensics
   - Foreground service with persistent notification + WakeLock + AlarmManager watchdog (~10 min)
   - Monitors accessibility setting via `ContentObserver`; on drop, shows **degraded full-screen lock** via `DegradedLockManager`; auto-dismisses on restore
   - On-device forensics logging to `getExternalFilesDir("forensics")/accessibility_forensics.log`

8. **DegradedLockManager** - Full-screen lock shown when accessibility is disabled (with one-tap recovery + parent-password unlock)
9. **GuardHealthState** - Heartbeat timestamps for accessibility / usage health
10. **AppBlockerService** - **DEPRECATED no-op stub** (`startService` does nothing, `onStartCommand` stops itself). Logic merged into `GuardAccessibilityService` / `AppBlockCoordinator`; kept only for manifest compatibility.

### Data Flow

```
ConfigActivity / SetupWizard (UI)
       ↓ writes
AppRuleRepository / SettingsManager → Room / SharedPreferences
       ↓ reads
LockDecisionEngine (reads only)
       ↓
GuardAccessibilityService → AppBlockCoordinator
       ↓
OverlayService / NavigationExecutor (overlay + BACK/HOME)

GuardForegroundService (keep-alive)
  ├─ watchdog + accessibility ContentObserver
  └─ on accessibility drop → DegradedLockManager (degraded full-screen lock)
```

**Key principle**: Monitoring engine only reads from database/storage, never writes. Rule changes automatically take effect without service restart.

### Data Models

- **AppRule**: Per-app control rules with types:
  - `RuleType.ALLOW` - No restrictions
  - `RuleType.BLOCK` - Completely blocked
  - `RuleType.LIMIT` - Time limits and/or time window restrictions (controlled by `LimitMode`: `BOTH` / `DURATION_ONLY` / `WINDOW_ONLY`)
  - Also carries `isGlobalLocked` flag (see dual-source issue below)
- **DailyUsage**: Daily usage time tracking per app
- **BlockReason**: Enum defining why blocking occurred (NONE / GLOBAL_LOCK / APP_BLOCKED / TIME_LIMIT_EXCEEDED / TIME_WINDOW_BLOCKED)

### Engine Layer

The `engine/` package contains core decision logic:

- **LockDecisionEngine**: Singleton that evaluates blocking decisions
  - Checks global lock status
  - Evaluates app-specific rules
  - Checks time windows and daily limits
- **BlockDecision**: Result containing `shouldBlock`, `reason`, and `appName`

## Known Issues & Technical Debt

> 通俗解读与修法细节（含"白名单/设置拦截"误诊澄清、输入法来龙去脉、破坏性迁移实锤等易混淆点）见 `docs/项目综合评价_2026-07-09.md` 第八节《重点问题通俗解读》。

### Security Concerns

1. **Broadcast security (implementation present; user acceptance not implied)**: `ACTION_BLOCK_APP` etc. are protected by the signature-level custom permission `com.kidsphoneguard.permission.INTERNAL_GUARD_BROADCAST` (see `BroadcastPermissionHelper` + AndroidManifest). Automated or code-level checks do not constitute final acceptance.

2. **Password storage (implementation present; ISS-013 pending user acceptance)**: `PasswordManager` uses PBKDF2-HMAC-SHA256 with a random 16-byte salt, 120,000 iterations, 256-bit output. No default password. Runtime legacy-plaintext verification is removed; at app startup, a legacy-only password is first migrated to PBKDF2 and only then removed.

3. ⚠️ **Whitelist prefix matching (RE-ASSESSED, not a bypass)**: Earlier this was flagged as a "spoofing bypass" (`com.android.settings.evil` etc.). Re-inspection shows that was a misdiagnosis:
   - The prefix match (`SystemSurfaceClassifier.isSettingsSurface`) is used as a **block trigger** (`LockDecisionEngine` returns `shouldBlock=true` for settings-family packages), NOT as an allow-list exemption. It is the mechanism that keeps kids out of Settings / OEM managers where they could disable permissions — tightening it to exact-match would *regress* protection (miss OEM manager variants).
   - The actual exemption path (`isInWhitelist`) is exact-match `SYSTEM_WHITELIST` plus only two input-method prefixes, which exist to avoid blocking keyboards. Package-name spoofing is not a realistic threat for this product's audience (ordinary children).
   - The real lever for the "kid breaks protection via Settings"攻防 is the content-based `ProtectedSettingsPolicy` (keyword coverage per OEM ROM + `BLOCK_ACTION`/`BLOCK_PAGE` granularity tuning), tracked as the open "保护设置关键词与响应速度调优" ISSUE.
   - ISS-018 implementation is present but pending user acceptance: dead `isLauncher`/`isPhoneApp`/`isMessagingApp`/`isCommunicationApp` methods were removed. Settings, installer, and market classification now lives in `SystemSurfaceClassifier`, separate from allow-list logic.
   - See `docs/项目综合评价_2026-07-09.md` §8.4 for the full plain-language explanation.

### Race Conditions

1. **Overlay state (implementation present; ISS-005 pending user acceptance)**: Static state read/write is centralized through `OverlayCoordinator`; external callers no longer touch `OverlayService` directly.

2. **Global lock dual source**: Global lock exists in both `SettingsManager` and `AppRule.isGlobalLocked`
   - Could lead to inconsistent state
   - Need single source of truth

### Performance Concerns

1. **Aggressive blocking**: Multiple BACK/HOME actions and repeated Overlay.show calls
   - Could cause UI jank
   - Should consolidate and optimize

2. **Polling (implementation present; ISS-017 pending user acceptance)**: `UsageTrackingManager` polls every 3s while screen on, 10s while screen off (down from 3s always); heartbeat is designed to remain within the 20s health timeout. Actual power and stability behavior require user acceptance. Full event-driven approach is deferred.

## MIUI/Xiaomi Device Compatibility

**Critical**: MIUI devices have strict background service restrictions. For proper functionality on Xiaomi devices:

1. Set battery optimization to "No restrictions"
2. Enable auto-start in Security Center
3. Lock the app in recent tasks
4. Enable overlay permissions fully
5. Accessibility service may need periodic re-enabling

See `小米手机应用拦截失效问题解决方案.md` for detailed MIUI-specific setup instructions.

## Code Conventions

### Kotlin
- Use `companion object` for singletons with `@Volatile` instance
- Launch coroutines with proper exception handling
- Use `suspend` functions for database operations
- Repository classes should expose Flow for reactive updates

### Logging
- Tag with class name: `private const val TAG = "ClassName"`
- Use appropriate levels: `Log.d` for debug, `Log.e` for errors with stacktrace
- Always catch and log exceptions, never let them propagate from services

### Resource Management
- Recycle `AccessibilityNodeInfo` objects
- Remove Handler callbacks when done
- Unregister broadcast receivers in onDestroy

## Development Priorities

**P0** (Critical): ISS-001 implementation is present but `PENDING_USER_ACCEPTANCE`. `TrustedTimeProvider` compares persisted wall-clock deltas with `SystemClock.elapsedRealtime()` deltas, freezes daily date, and short-circuits time-window rules on detected tamper. A forward edit after reboot remains a documented pure-local limitation. See `docs/ISSUES.md` ISS-001.
**P1** (High): ISS-003, ISS-004, and ISS-005 have implementations but are `PENDING_USER_ACCEPTANCE`; code checks and automated tests do not close them. ISS-002 remains `IN_PROGRESS` and the earlier 43 ms log observation is not user-experience evidence.
**P2** (Medium): ISS-009 through ISS-014 implementations/decisions are governed by the ledger; all except the user's explicit ISS-008 product decision are pending user acceptance where applicable. ISS-007 remains complete because the user explicitly confirmed it basically met expectations. ISS-008 remains `WONTFIX` by explicit user product decision.
**P3** (Low): ISS-015 through ISS-019 and ISS-022 are `PENDING_USER_ACCEPTANCE`; automated coverage and structural inspection are development evidence only.

> Note: "Exact/curated whitelist matching (remove `startsWith` bypass)" previously listed under P0 has been **retracted** as a misdiagnosis — see Known Issues #3 and `docs/项目综合评价_2026-07-09.md` §8.4.

## Permissions Required

The app requires these critical permissions:
- `SYSTEM_ALERT_WINDOW` - Overlay display + degraded lock screen
- `BIND_ACCESSIBILITY_SERVICE` - Core monitoring
- `PACKAGE_USAGE_STATS` - Usage time tracking
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Keep-alive
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` - Foreground keep-alive service
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot
- `POST_NOTIFICATIONS` / `WAKE_LOCK` / `KILL_BACKGROUND_PROCESSES` - Notification / keep-alive / force-stop
- `INTERNAL_GUARD_BROADCAST` (custom, signature-level) - Internal broadcast protection
- `WRITE_SECURE_SETTINGS` - ADB-granted programmatic accessibility recovery (`DegradedLockManager.tryProgrammaticRecovery`, advanced/enterprise)

Core permissions are guided step-by-step through the setup wizard in `MainActivity` / `SetupWizardActivity`.
