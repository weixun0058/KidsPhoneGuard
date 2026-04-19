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
adb logcat -s KidsPhoneGuard:D GuardAccessibilityService:D AppBlockerService:D

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

## Core Architecture

### Service Architecture

The app relies on multiple cooperating services:

1. **GuardAccessibilityService** - Core monitoring engine
   - Listens for window state changes via Accessibility API
   - Debounces events (500ms interval) to avoid excessive checks
   - Triggers `LockDecisionEngine` to evaluate blocking decisions
   - Executes blocking actions (BACK, HOME, force-stop app)

2. **OverlayService** - Blocking UI layer
   - Shows full-screen red overlay to prevent app access
   - Uses `TYPE_APPLICATION_OVERLAY` window type
   - Provides "Return to Home" button
   - Managed by static state (be careful of race conditions)

3. **UsageTrackingManager** - Usage time tracker
   - Polls `UsageStatsManager` every 3 seconds
   - Updates Room database with accumulated time
   - Triggers blocking when time limits are exceeded
   - Works even when accessibility service is down

4. **GuardForegroundService** - Keep-alive service
   - Runs as foreground service with persistent notification
   - Helps prevent system from killing monitoring services

### Data Flow

```
ConfigActivity (UI)
       ↓
AppRuleRepository → Room Database
       ↓
LockDecisionEngine (reads DB)
       ↓
GuardAccessibilityService (monitors events)
       ↓
OverlayService (shows block screen)
```

**Key principle**: Monitoring engine only reads from database, never writes. Rule changes automatically take effect without service restart.

### Data Models

- **AppRule**: Per-app control rules with types:
  - `ALLOW` - No restrictions
  - `BLOCK` - Completely blocked
  - `LIMIT` - Time limits and time window restrictions
- **DailyUsage**: Daily usage time tracking per app
- **BlockReason**: Sealed class defining why blocking occurred

### Engine Layer

The `engine/` package contains core decision logic:

- **LockDecisionEngine**: Singleton that evaluates blocking decisions
  - Checks global lock status
  - Evaluates app-specific rules
  - Checks time windows and daily limits
- **BlockDecision**: Result containing `shouldBlock`, `reason`, and `appName`

## Known Issues & Technical Debt

### Security Concerns

1. **Broadcast security**: Custom broadcasts like `ACTION_BLOCK_APP` lack permission protection
   - Could be triggered by external apps
   - Consider using protected broadcasts or signed permissions

2. **Whitelist bypass risk**: Uses `contains()` for package matching
   - Could be bypassed with package name spoofing
   - Should use exact matching or controlled prefix matching

3. **Password storage**: Stored in plaintext with weak default
   - Should use salted hash
   - Remove default weak password

### Race Conditions

1. **Overlay state**: Static state modified from multiple service entry points
   - Can cause overlay flickering or missed hide operations
   - Consider centralizing overlay control

2. **Global lock dual source**: Global lock exists in both `SettingsManager` and `AppRule.isGlobalLocked`
   - Could lead to inconsistent state
   - Need single source of truth

### Performance Concerns

1. **Aggressive blocking**: Multiple BACK/HOME actions and repeated Overlay.show calls
   - Could cause UI jank
   - Should consolidate and optimize

2. **Polling**: UsageStats queried every 3 seconds continuously
   - Consider event-driven approach
   - Reduce frequency when possible

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

**P0** (Critical): Broadcast protection, exact whitelist matching, overlay close strategy
**P1** (High): Unified lock decision engine, single state source, dual-source global lock fix
**P2** (Medium): Polling optimization, performance profiling, log standardization

## Permissions Required

The app requires these critical permissions:
- `SYSTEM_ALERT_WINDOW` - Overlay display
- `BIND_ACCESSIBILITY_SERVICE` - Core monitoring
- `PACKAGE_USAGE_STATS` - Usage time tracking
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Keep-alive

All permissions are guided through MainActivity.
