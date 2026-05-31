# Phase 6 OEM And Special Guard Extraction Implementation Plan

Source plan: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`, especially Task 7: `Extract OEM and special-case handlers`.

**Goal:** Extract WeChat Finder and Huawei/Honor power-save handling out of `GuardAccessibilityService` while preserving Phase 3 router ownership, Phase 4 protected-surface boundaries, and Phase 5 normal block ownership.

**Architecture:** Phase 6 introduces focused special-case handlers behind router adapters. `WeChatFinderGuard` owns the narrow WeChat Finder route and block action. `HuaweiPowerSaveHandler` owns OEM power-save exit detection and execution. `GuardAccessibilityService` remains the Android entrypoint and composition root; it should not keep WeChat Finder or power-save business method bodies after this phase. Assistant/gameassistant follow-up compensation is explicitly out of scope for Phase 6 and may remain service-owned until a later routing-support phase.

**Tech Stack:** Kotlin, Android AccessibilityService, AccessibilityEvent, AccessibilityNodeInfo, GestureDescription, existing `GuardActionResult`, `NavigationExecutor`, `GuardActionScheduler`, `BlockSessionController`, `AppBlockCoordinator`, `ProtectedSurfaceGuard`.

---

## 1. Naming And Source

This plan intentionally follows the existing phase document naming convention:

- Phase 1: `docs/plans/2026-05-24-phase-1-seam-extraction-plan.md`
- Phase 2: `docs/plans/2026-05-24-phase-2-sensitive-action-extraction-plan.md`
- Phase 3: `docs/plans/2026-05-24-phase-3-accessibility-event-router-plan.md`
- Phase 4: `docs/plans/2026-05-24-phase-4-protected-surface-guard-extraction-plan.md`
- Phase 5: `docs/plans/2026-05-24-phase-5-app-block-coordinator-extraction-plan.md`
- Phase 6: `docs/plans/2026-05-24-phase-6-oem-special-guard-extraction-plan.md`

This document is the detailed Phase 6 execution plan for the overall plan's Task 7. It is not a replacement for the overall decoupling plan.

## 2. Current State

Phase 5 extracted normal app blocking into `AppBlockCoordinator`.

The following special-case logic still lives in `GuardAccessibilityService` and is the Phase 6 extraction target:

- `handleWeChatFinder(...)`
- `shouldBlockWeChatFinder(...)`
- `blockWeChatFinder(...)`
- `exitPowerSaveModeIfNeeded(...)`
- `exitVisiblePowerSaveModeIfNeeded(...)`
- `triggerPowerSaveExit(...)`
- `clickPowerSaveExitNode(...)`
- `clickPowerSaveExitNodeInTree(...)`
- `schedulePowerSaveExitBurst(...)`
- `tapPowerSaveExitArea(...)`
- `collectPowerSaveExitSignal(...)`
- `collectPowerSaveNodeSignals(...)`
- `containsPowerSaveExitSignal(...)`
- `isPowerSaveLauncherPackage(...)`
- `findClickableAncestor(...)`, if no other service-owned caller remains after power-save extraction
- special-case constants/state:
  - `WECHAT_PACKAGE`
  - `WECHAT_FINDER_SURFACE`
  - `WECHAT_FINDER_APP_NAME`
  - `SCHEDULER_OWNER_WECHAT_FINDER`
  - `SCHEDULER_OWNER_POWER_SAVE`
  - `lastPowerSaveExitAttemptTime`
  - `powerSaveExitAttemptCooldownMs`
  - `powerSaveExitPackages`
  - `powerSaveExitActivitySignals`
  - `powerSaveExitKeywords`

The following special-case logic also remains in `GuardAccessibilityService`, but is not a Phase 6 extraction target:

- `assistantPackages`
- `scheduleAssistantFollowUpChecks(...)`
- assistant package mapping inside `resolvePolicyPackage(...)`
- `SCHEDULER_OWNER_ASSISTANT_FOLLOW_UP`

This assistant/gameassistant compensation path exists to handle Huawei/Honor assistant overlay routing. Phase 6 will not move it. The closeout summary must state that this logic remains in `GuardAccessibilityService` and is not counted against Phase 6 completion.

## 3. Scope

Phase 6 should extract:

- WeChat Finder special-case route and action behavior.
- Huawei/Honor power-save exit detection and execution behavior.
- Power-save live-node traversal and gesture burst scheduling.
- Special-case-only state into the owning handler, not into `GuardAccessibilityService`.
- Minimal diagnostics needed to preserve issue traceability.

Phase 6 should not extract:

- `SensitiveActionGuard` rule fixes.
- `ISSUE-011` MIUI app list false positive fix.
- assistant/gameassistant follow-up compensation.
- Broad WeChat Finder detection expansion without fresh logs.
- Protected settings policy changes.
- Normal app blocking changes.
- Service lifecycle or health reporting.

Known issues may guide diagnostics, but should not expand this phase into a broad behavior-fix phase.

## 4. Target Files

Create:

- `app/src/main/java/com/kidsphoneguard/service/guard/WeChatFinderGuard.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/WeChatFinderGuardTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandlerTest.kt`

Modify:

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuard.kt` only if its `exitVisiblePowerSaveModeIfNeeded` callback wiring needs a narrow type/signature adjustment.
- `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt` only if the existing adapter signature needs a narrow rename or diagnostic parameter.
- `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt` only if router adapter wiring changes.

Do not modify:

- `SensitiveActionGuard.kt`, unless a compile-only signature adjustment is unavoidable.
- `AppBlockCoordinator.kt`, unless a narrow callback surface must be exposed for WeChat Finder cleanup.
- Protected settings rule files, unless a compile-only import/package move requires it.

## 5. Handler Boundaries

### 5.1 `WeChatFinderGuard`

Owns:

- WeChat Finder route detection.
- WeChat Finder cooldown check.
- WeChat Finder block session recording.
- WeChat Finder overlay show request.
- WeChat Finder immediate navigation action.
- WeChat Finder delayed overlay auto-release.
- WeChat Finder log markers:
  - `wechat_finder_block`
  - `wechat_finder_block_skip_cooldown`
  - `wechat_finder_overlay_auto_release`

Does not own:

- Normal app policy decisions.
- Protected settings policy.
- Sensitive action keyword detection.
- WeChat general chat/payment/contact blocking rules.
- Broad WeChat Finder detection fixes unless backed by fresh logs and explicitly added to this phase.

Allowed dependencies:

- `BlockSessionController`
- `GuardActionScheduler`
- `NavigationExecutor`
- narrow setting callbacks:
  - `isWeChatFinderBlockEnabled`
  - `isGlobalUnlockEnabled`
- narrow cleanup callbacks:
  - `cancelPendingBlockActions(reason)`
  - `hideOverlay()`
- narrow overlay read callback:
  - `readCurrentBlockedPackage()`
- `postToMain { ... }` for overlay show if preserving current main-thread timing
- `publishLifecycleSignal(signal)` if preserving current lifecycle signal behavior

Disallowed dependencies:

- Direct normal app policy calls.
- Direct `OverlayService.showOverlay(...)`.
- Direct ownership of normal app block state.
- Direct ownership of protected-surface state.

### 5.2 `HuaweiPowerSaveHandler`

Owns:

- Power-save event detection.
- Visible power-save detection.
- Power-save exit cooldown state.
- Power-save node click search.
- Power-save gesture burst scheduling.
- Power-save signal collection.
- Power-save package recognition.
- Power-save log markers:
  - `power_save_exit_attempt`
  - `power_save_exit_click_node`
  - `power_save_exit_gesture`
  - existing failure markers such as `power_save_exit_root_failed`

Does not own:

- Protected settings policy evaluation.
- Normal app blocking.
- WeChat Finder behavior.
- System panel collapse.
- Broad Huawei/Honor protected settings rules.

Allowed dependencies:

- `NavigationExecutor`
- `GuardActionScheduler`
- root/window read callbacks:
  - `readRootInActiveWindow()`
  - `readWindows()`
- `isGlobalProtectedSurfaceUnlockAllowed()`
- display metrics provider or context/resource callback for gesture coordinates
- scheduler owner key for power-save actions

Disallowed dependencies:

- Direct router-order decisions.
- Long-lived `AccessibilityNodeInfo` storage.
- Direct `OverlayService` calls.
- Broad protected settings policy dependencies.

### 5.3 State Decision

Phase 6 will not create a separate `PowerSaveState.kt`.

Current power-save-only state is limited to:

- `lastPowerSaveExitAttemptTime`

Move this field into `HuaweiPowerSaveHandler` as a private field. If later phases add more OEM state, introduce a dedicated state holder then.

`WeChatFinderGuard` does not need a separate state holder because it uses `BlockSessionController` for cooldown/session checks and `GuardActionScheduler` for delayed release.

## 6. GuardActionResult Semantics

Phase 6 should keep router-level result semantics stable:

- `Continue`: no route consumption; router continues.
- `Consumed`: route stops; may or may not have side effects.
- `ScheduleFollowUp`: delayed work has been scheduled; route stops.
- `Blocked`: use only if a block was actually requested/executed through block-session semantics and the adapter returns that result intentionally.

Expected Phase 6 mapping:

- WeChat Finder no hit: `Continue`
- WeChat Finder hit: `Consumed(reason = "wechat_finder", hasSideEffect = true)`
- Power-save no hit: `Continue`
- Power-save hit: `Consumed(reason = "power_save_exit", hasSideEffect = true)`

Do not change route order in `AccessibilityEventRouter`.

## 7. AccessibilityNodeInfo Ownership

Power-save extraction touches live nodes. Ownership rules are strict:

- The method that obtains a node must recycle it.
- Event source nodes must not be stored in the handler.
- Active root nodes must be recycled before returning.
- Window root nodes must be recycled in the same loop iteration.
- Child nodes obtained by traversal must be recycled in the same loop iteration.
- No `AccessibilityNodeInfo` may be captured in scheduler callbacks.

If a node-derived value is needed later, store a string/boolean snapshot, not the node.

## 8. Temporary Callback Boundary

Temporary callbacks are allowed only to keep tasks small and reversible. They must be explicit constructor dependencies; handlers must not call service private methods implicitly.

### 8.1 WeChat Finder Callbacks

These narrow callbacks may remain after Phase 6 because they represent cross-module coordination, not service private implementation:

- `cancelPendingBlockActions(reason)` delegating to `AppBlockCoordinator`
- `hideOverlay()` delegating to `AppBlockCoordinator` or `BlockSessionController`
- `readCurrentBlockedPackage()`
- `postToMain { ... }`
- `publishLifecycleSignal(signal)`

They should be passed directly to `WeChatFinderGuard`; `WeChatFinderGuard` should not depend on the full `AppBlockCoordinator`.

### 8.2 Power-Save Platform Callbacks

These platform callbacks may remain after Phase 6 because they represent Android framework access:

- `readRootInActiveWindow()`
- `readWindows()`
- `displayMetricsProvider`
- `isGlobalProtectedSurfaceUnlockAllowed()`

The following service methods must not remain as power-save business callbacks after Phase 6:

- `triggerPowerSaveExit(...)`
- `clickPowerSaveExitNode(...)`
- `clickPowerSaveExitNodeInTree(...)`
- `schedulePowerSaveExitBurst(...)`
- `tapPowerSaveExitArea(...)`
- `collectPowerSaveExitSignal(...)`
- `collectPowerSaveNodeSignals(...)`
- `containsPowerSaveExitSignal(...)`
- `isPowerSaveLauncherPackage(...)`

## 9. Implementation Tasks

### Task 1: Baseline Audit

Files:

- Read: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Read: `issues_list.md`
- Read: `docs/plans/2026-05-24-phase-5-closeout-summary.md`

Steps:

1. Record current WeChat Finder method list, constants, log markers, and delay values.
2. Record current power-save method list, constants, log markers, and delay values.
3. Confirm `ISSUE-004` is known open and should not be "fixed by guessing".
4. Confirm `ISSUE-005` remains device-validation dependent.
5. Confirm `ISSUE-011` stays outside Phase 6.
6. Run automated checks before changing code.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Build succeeds.
- Any failure is investigated before extraction begins.

### Task 2: Create `WeChatFinderGuard` Shell

Files:

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/WeChatFinderGuard.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/WeChatFinderGuardTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Create `WeChatFinderGuard` with constructor dependencies only.
2. Add pure helper tests for current class-name matching.
3. Add handler-level tests for router result semantics.
4. Keep behavior delegated or equivalent at first.
5. Wire a `weChatFinderGuard` lazy property in `GuardAccessibilityService`.

Initial public surface:

```kotlin
class WeChatFinderGuard(
    ...
) {
    fun handle(event: AccessibilityEvent, packageName: String): GuardActionResult
}
```

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected:

- Router result remains `Continue` for non-WeChat packages.
- `WeChatFinderGuard.handle(...)` returns `Continue` for non-WeChat packages.
- `WeChatFinderGuard.handle(...)` returns `Consumed(reason = "wechat_finder", hasSideEffect = true)` for the current Finder class-name match when blocking is enabled and global unlock is disabled.
- Current Finder class-name matching remains unchanged.

### Task 3: Move WeChat Finder Detection And Action

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/WeChatFinderGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/guard/WeChatFinderGuardTest.kt`

Steps:

1. Move `shouldBlockWeChatFinder(...)` into `WeChatFinderGuard`.
2. Move `blockWeChatFinder(...)` into `WeChatFinderGuard`.
3. Preserve constants and timings:
   - WeChat package: `com.tencent.mm`
   - Finder surface: `com.tencent.mm:finder`
   - app name: `微信视频号`
   - cooldown: `1200L`
   - overlay auto-release delay: `1500L`
   - scheduler owner: `wechat_finder`
4. Preserve log markers:
   - `wechat_finder_block`
   - `wechat_finder_block_skip_cooldown`
   - `wechat_finder_overlay_auto_release`
5. Replace service router adapter `handleWeChatFinder` with `weChatFinderGuard::handle`.
6. Do not broaden className rules without fresh logs.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Device regression targets:

- Normal WeChat chat opens without false block.
- WeChat payment/contact/chat paths are not falsely blocked.
- If Finder still fails to block, record logs under `ISSUE-004`; do not silently broaden rules in this extraction patch.
- Phase 6 may still close with `ISSUE-004` open, as long as no regression is introduced and fresh logs are recorded when Finder still fails.

### Task 4: Create `HuaweiPowerSaveHandler` Shell

Files:

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandlerTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Create `HuaweiPowerSaveHandler` with constructor dependencies only.
2. Move pure helper tests for package recognition and signal matching.
3. Add handler-level tests for router result semantics.
4. Keep behavior delegated or equivalent at first.
5. Wire a `huaweiPowerSaveHandler` lazy property in `GuardAccessibilityService`.

Initial public surface:

```kotlin
class HuaweiPowerSaveHandler(
    ...
) {
    fun handle(event: AccessibilityEvent, source: String): GuardActionResult

    fun exitVisiblePowerSaveModeIfNeeded(source: String): Boolean
}
```

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected:

- `HuaweiPowerSaveHandler.handle(...)` returns `Continue` when package/class signals do not match.
- Router power-save adapter still returns `Consumed(reason = "power_save_exit", hasSideEffect = true)` when hit.
- `HuaweiPowerSaveHandler.handle(...)` returns `Consumed(reason = "power_save_exit", hasSideEffect = true)` for the current matching package/class signal when global protected-surface unlock is disabled.
- `HuaweiPowerSaveHandler.exitVisiblePowerSaveModeIfNeeded(...)` returns `false` when visible root/signal does not match.
- `HuaweiPowerSaveHandler.exitVisiblePowerSaveModeIfNeeded(...)` returns `true` when visible root/signal matches and an exit attempt is triggered.
- Visible power-save callback used by `ProtectedSurfaceGuard` still returns the same Boolean semantics.

### Task 5: Move Power-Save Detection And State

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandlerTest.kt`

Steps:

1. Move `exitPowerSaveModeIfNeeded(...)` logic into `HuaweiPowerSaveHandler.handle(...)`.
2. Move `exitVisiblePowerSaveModeIfNeeded(...)`.
3. Move `lastPowerSaveExitAttemptTime` into the handler as a private field.
4. Move constants:
   - `powerSaveExitAttemptCooldownMs`
   - `powerSaveExitPackages`
   - `powerSaveExitActivitySignals`
   - `powerSaveExitKeywords`
5. Preserve cooldown:
   - `220L`
6. Preserve current package and class matching.
7. Do not add Honor/Huawei-specific guesses beyond existing constants.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

### Task 6: Move Power-Save Live Node And Gesture Actions

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Move `triggerPowerSaveExit(...)`.
2. Move `clickPowerSaveExitNode(...)`.
3. Move `clickPowerSaveExitNodeInTree(...)`.
4. Move `schedulePowerSaveExitBurst(...)`.
5. Move `tapPowerSaveExitArea(...)`.
6. Move `collectPowerSaveExitSignal(...)`.
7. Move `collectPowerSaveNodeSignals(...)`.
8. Move `containsPowerSaveExitSignal(...)`.
9. Move `isPowerSaveLauncherPackage(...)`.
10. Move `findClickableAncestor(...)` if no non-power-save caller remains in service.
11. Preserve burst delays:
    - `0L`, `45L`, `120L`, `260L`, `520L`
12. Preserve tap coordinate ratio:
    - `width * 0.92f`
    - `height * 0.055f`
13. Preserve scheduler owner:
    - `power_save_exit`

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- No power-save business method body remains in `GuardAccessibilityService`.
- No `AccessibilityNodeInfo` is stored in handler state or scheduler callbacks.
- All moved node traversal still recycles child/root nodes in the same method.

### Task 7: Router And ProtectedSurface Wiring Cleanup

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuard.kt` only if needed.
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt` only if needed.

Steps:

1. Update `createAccessibilityEventRouterAdapters()` so:
   - WeChat Finder callback points to `weChatFinderGuard::handle`
   - power-save callback points to `huaweiPowerSaveHandler::handle`
2. Update `ProtectedSurfaceGuard` constructor wiring so visible power-save exit points to `huaweiPowerSaveHandler::exitVisiblePowerSaveModeIfNeeded`.
3. Keep router route order unchanged.
4. Keep unsupported event handling unchanged.
5. Remove service methods that only existed as Phase 6 temporary wrappers.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Router tests pass.
- Service no longer contains WeChat Finder or power-save business method bodies.

### Task 8: Diagnostics And Issue Ledger Review

Files:

- Modify: `issues_list.md` only if Phase 6 changes or adds evidence.

Steps:

1. If WeChat Finder still fails, add the new observed logs to `ISSUE-004`.
2. If Honor/Huawei power-save is tested, update `ISSUE-005`.
3. If no issue status changes, state that in Phase 6 closeout instead of editing `issues_list.md`.
4. Do not mark `ISSUE-004` fixed unless video号 entry, detail page, and return-to-chat flows were actually validated.
5. Do not mark `ISSUE-005` resolved without Honor/Huawei device evidence.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

### Task 9: Closeout And Device Regression

Files:

- Create after implementation: `docs/plans/2026-05-24-phase-6-closeout-summary.md`

Automated verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Required device regression:

- 本应用打开通过。
- 普通拦截通过。
- 白名单通过。
- 应用商店通过，且 `com.xiaomi.market` 不出现遮蔽层卡死回归。
- 应用卸载仍能拦截。
- 使用情况访问仍能拦截。
- 悬浮窗权限仍能拦截。
- 普通微信聊天、通讯录、支付不被误拦。

Special validation:

- WeChat Finder:
  - 视频号入口
  - 视频号详情页
  - 返回微信聊天页
- Huawei/Honor power-save:
  - 小米上不出现新回归
  - 荣耀/Huawei 设备如可用，验证省电/超级省电/后台限制页面

Watch-only, not Phase 6 blockers unless behavior regresses from current baseline:

- `ISSUE-011` MIUI 应用列表页误判。
- WeChat Finder 仍失败但日志显示仍是旧识别规则过窄。
- Honor/Huawei device remains unavailable for power-save validation.
- assistant/gameassistant follow-up remains service-owned by explicit Phase 6 scope decision.

## 10. Completion Criteria

Phase 6 is complete when:

- `WeChatFinderGuard.kt` exists and owns WeChat Finder detection/action behavior.
- `HuaweiPowerSaveHandler.kt` exists and owns power-save detection/action behavior.
- `GuardAccessibilityService` no longer contains:
  - `handleWeChatFinder(...)`
  - `shouldBlockWeChatFinder(...)`
  - `blockWeChatFinder(...)`
  - `exitPowerSaveModeIfNeeded(...)`
  - `exitVisiblePowerSaveModeIfNeeded(...)`
  - `triggerPowerSaveExit(...)`
  - `clickPowerSaveExitNode(...)`
  - `clickPowerSaveExitNodeInTree(...)`
  - `schedulePowerSaveExitBurst(...)`
  - `tapPowerSaveExitArea(...)`
  - `collectPowerSaveExitSignal(...)`
  - `collectPowerSaveNodeSignals(...)`
  - `containsPowerSaveExitSignal(...)`
  - `isPowerSaveLauncherPackage(...)`
- `lastPowerSaveExitAttemptTime` no longer lives in `GuardAccessibilityService`.
- Router route order is unchanged.
- `GuardActionScheduler` remains the only delayed-action scheduler for these handlers.
- `BlockSessionController` remains the owner of block session state.
- No handler owns long-lived `AccessibilityNodeInfo`.
- If assistant/gameassistant follow-up remains in `GuardAccessibilityService`, Phase 6 closeout summary explicitly says it is out of scope and not counted as a failure of this phase.
- Automated checks pass.
- Required device regression scenarios are recorded.
- Phase 6 closeout summary exists.

## 11. Rollback Plan

Keep Phase 6 changes in small patch chunks:

1. WeChat Finder shell.
2. WeChat Finder behavior move.
3. Huawei power-save shell.
4. Power-save detection/state move.
5. Power-save live-node/gesture move.
6. Router/protected-surface wiring cleanup.
7. Closeout summary and issue ledger update.

If a device regression appears, revert only the most recent chunk and keep earlier verified chunks. Do not mix ISSUE-011 or broad WeChat detection fixes into the extraction patch unless the regression is directly caused by Phase 6.

## 12. Notes For Implementation

- Do not fix `ISSUE-011` inside Phase 6.
- Do not extract assistant/gameassistant follow-up inside Phase 6.
- Do not broaden WeChat Finder detection without fresh logs.
- Phase 6 can close with `ISSUE-004` still open if behavior is not regressed and fresh logs are captured when the old Finder detection still misses.
- Do not infer Honor/Huawei behavior from Xiaomi-only tests.
- Preserve existing log markers, delays, cooldowns, and scheduler owner keys.
- Keep `WeChatFinderGuard` and `HuaweiPowerSaveHandler` separate; do not create a new mixed OEM/special-case god object.
- Prefer guard-local small helpers over cross-handler helper dependencies.
