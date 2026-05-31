# Phase 5 App Block Coordinator Extraction Implementation Plan

Source plan: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`, especially Task 6: `Extract AppBlockCoordinator`.

**Goal:** Extract the normal app blocking workflow out of `GuardAccessibilityService` while preserving Phase 3 router behavior and Phase 4 protected-surface release timing.

**Architecture:** Phase 5 introduces `AppBlockCoordinator` as the router adapter target for normal app policy checks and block execution. `AccessibilityEventRouter` continues to own event order and `GuardActionResult` flow control. `AppBlockCoordinator` owns normal app block decision execution, deferred block actions, foreground visibility checks, and normal overlay release checks, while shared overlay/block state remains in `BlockSessionController`.

**Tech Stack:** Kotlin, Android AccessibilityService, coroutines, `LockDecisionEngine`, `GuardActionResult`, `WindowInspectorSnapshotApi`, `NavigationExecutor`, `GuardActionScheduler`, `BlockSessionController`, `ProtectedSurfaceGuard`.

---

## 1. Naming And Source

This plan intentionally follows the existing phase document naming convention:

- Phase 1: `docs/plans/2026-05-24-phase-1-seam-extraction-plan.md`
- Phase 2: `docs/plans/2026-05-24-phase-2-sensitive-action-extraction-plan.md`
- Phase 3: `docs/plans/2026-05-24-phase-3-accessibility-event-router-plan.md`
- Phase 4: `docs/plans/2026-05-24-phase-4-protected-surface-guard-extraction-plan.md`
- Phase 4 closeout: `docs/plans/2026-05-24-phase-4-closeout-summary.md`

This document is the detailed Phase 5 execution plan for the overall plan's Task 6. It is not a replacement for the overall decoupling plan.

## 2. Current State

Phase 4 is considered closed by `docs/plans/2026-05-24-phase-4-closeout-summary.md`.

The following app-blocking logic still lives in `GuardAccessibilityService` and is the main Phase 5 extraction target:

- `launchNormalPolicyCheck(...)`
- `checkPolicyAndExecute(...)`
- `enforceBlock(...)`
- `handleBlockHold(...)`
- `handleWhitelistWindowEvent(...)`
- `tryFallbackNavigation(...)`
- `scheduleDeferredBlockAction(...)`
- `canExecuteDeferredBlockAction(...)`
- `cancelPendingBlockActions(...)`
- `scheduleOverlayReleaseCheck(...)`
- `hideOverlay(...)` adapter usage for normal release
- `tryForceStopApp(...)`
- `isTargetPackageActive(...)`
- `isPackageVisibleInInteractiveWindows(...)`
- `getRecentTopPackageName(...)`
- normal app blocking constants such as block cooldown, hold duration, overlay stability window, overlay reshow cooldown, and release-check delays
- normal app blocking state that is not already in `BlockSessionController`, such as `forceStopPermissionDenied`

## 3. Scope

Phase 5 should extract:

- Normal app policy decision execution.
- Normal blocked-app overlay display orchestration.
- Normal deferred force-stop and fallback navigation scheduling.
- Normal overlay release checks.
- Normal app foreground/window visibility helpers.
- Whitelist transition behavior currently coupled to normal overlay release.
- Block hold checks that are part of normal route gating.

Phase 5 should not extract:

- `WeChatFinderGuard`.
- Huawei/Honor power-save handling.
- New sensitive-action behavior.
- New protected settings rules.
- Broad fixes for known behavior issues in `issues_list.md`, unless the issue is introduced by Phase 5 itself.
- Service lifecycle and Android callback ownership.

## 4. Target Files

Create:

- `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- `app/src/test/java/com/kidsphoneguard/service/block/AppBlockCoordinatorTest.kt`

Modify:

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt` only if adapter names or signatures must be narrowed.
- `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt` only if router adapter wiring changes.

Do not modify:

- `SensitiveActionGuard.kt`, unless a compile-only signature adjustment is unavoidable.
- `ProtectedSurfaceGuard.kt`, unless Phase 5 needs a narrow release-check adapter method to preserve current behavior.
- `SystemSurfaceGuard.kt`.

## 5. Boundaries

### 5.1 `AppBlockCoordinator`

Owns:

- Calling `LockDecisionEngine.getBlockDecision(...)`.
- Translating block decisions into logs and block execution.
- Normal `enforceBlock(...)` workflow.
- Normal app deferred force-stop actions.
- Normal app fallback navigation.
- Normal app overlay release checks.
- Normal app foreground/window visibility checks.
- Whitelist transition handling if it remains coupled to current overlay release behavior.
- `forceStopPermissionDenied` and any future normal-block-only state.

Does not own:

- Sensitive action detection or escape bursts.
- Protected settings policy evaluation.
- Protected window scanning.
- System panel collapse.
- WeChat Finder special-case behavior.
- Huawei/Honor power-save behavior.
- Android service lifecycle.
- Raw shared overlay/block session state.

Allowed dependencies:

- `LockDecisionEngine` or a narrow `getBlockDecision(packageName)` callback.
- `BlockSessionController`.
- `GuardActionScheduler`.
- `NavigationExecutor`.
- `WindowInspectorSnapshotApi`.
- `ActivityManager`, if force-stop behavior stays implementation-owned by the coordinator.
- Small callbacks for service-only behavior that cannot yet move safely.

Disallowed dependencies:

- Direct ownership of `lastBlockedPackage`, `lastBlockTime`, `blockHoldUntil`, `pendingBlockPackage`, `lastOverlayPackage`, or `lastOverlayShowTime`.
- Direct ownership of protected-surface state.
- Direct routing-order decisions that belong to `AccessibilityEventRouter`.
- Long-lived `AccessibilityNodeInfo` references.

### 5.2 App-Block State Decision

Phase 5 will not create `AppBlockState.kt`.

Current app-block-only state is limited to:

- `forceStopPermissionDenied`

Move this field into `AppBlockCoordinator` as a private field. This is a deliberate Phase 5 decision to avoid creating a state holder for a single field. If later phases add more app-block-only cooldowns, policy caches, or execution state, introduce `AppBlockState` then.

Do not put shared overlay/block session fields into `AppBlockCoordinator` or a future `AppBlockState`; they remain owned by `BlockSessionController`.

### 5.3 Protected Surface Release Boundary

Current normal `enforceBlock(...)` has a protected-surface edge path:

- If the blocked package is a protected surface and overlay needs to be shown, protected release checks must start after `showOverlay(...)` has actually been posted/executed.
- If the package is a protected surface and overlay is already visible for that package, protected release checks may start only when overlay is showing and the current blocked package matches.

Phase 5 must preserve this exact timing.

`AppBlockCoordinator` must not reimplement protected-surface policy. Use a narrow callback boundary instead, for example:

```kotlin
data class ProtectedSurfaceCallbacks(
    val isProtectedSystemSurface: (String) -> Boolean,
    val scheduleProtectedReleaseCheck: (String) -> Unit
)
```

The callback may delegate to `ProtectedSurfaceGuard`, but the coordinator must only use it for the release-check timing bridge described above.

### 5.4 Router Boundary

`AccessibilityEventRouter` continues to own:

- Event type classification.
- Window route order.
- Interaction route order.
- `GuardActionResult` stop/continue semantics.

Phase 5 should replace service adapters with coordinator adapters, not move route ordering into `AppBlockCoordinator`.

## 6. GuardActionResult Semantics

Phase 5 should keep router-level result semantics stable:

- `Continue`: no route consumption; router continues.
- `Consumed`: route stops; may or may not have side effects.
- `ScheduleFollowUp`: delayed work or coroutine work has been scheduled; route stops.
- `Blocked`: use only when a block was actually requested/executed through `BlockSessionController` semantics.

Expected Phase 5 mapping:

- `launchNormalPolicyCheck(...)` should remain `ScheduleFollowUp(reason = "normal_policy_check")` unless the router contract is deliberately changed.
- `handleBlockHold(...)` should remain `Consumed(reason = "block_hold", hasSideEffect = false)` when hit.
- `handleWhitelistWindowEvent(...)` should remain `Consumed(reason = "whitelist_transition", ...)` when hit.
- `enforceBlock(...)` may internally be represented as `Blocked(...)`, but do not force `Blocked` into the router path unless the adapter actually returns that result.

## 7. Accessibility Ownership

Phase 5 should prefer `WindowInspectorSnapshotApi` for active package and interactive window reads.

If live `AccessibilityNodeInfo` access becomes necessary:

- Keep it inside a short-lived method.
- Recycle the node in the same method that obtains it.
- Do not store it in `AppBlockCoordinator` state.
- Do not pass it into scheduler callbacks.

Normal app blocking should not add new live node traversal unless required to preserve current behavior.

## 8. Temporary Callback Boundary

Phase 5 intentionally migrates callers before some callees. Temporary callbacks are allowed only to keep each task small and reversible. They must be explicit constructor dependencies or small callback data classes; `AppBlockCoordinator` must not reach into `GuardAccessibilityService` private methods implicitly.

Temporary callbacks are allowed as follows:

### 8.1 Task 3 Temporary Callbacks

When moving `handleBlockHold(...)` and `handleWhitelistWindowEvent(...)`, these service-backed callbacks may exist temporarily:

- `isTargetPackageActive(packageName)`
- `cancelPendingBlockActions(reason)`
- `hideOverlay()`

These callbacks exist only because visibility helpers and pending-block cleanup move later. They must be removed or replaced by coordinator-owned methods by the end of Task 7.

### 8.2 Task 4 Temporary Callbacks

When moving `launchNormalPolicyCheck(...)` and `checkPolicyAndExecute(...)`, these service-backed callbacks may exist temporarily:

- `ensureLockDecisionEngineInitialized()`
- `enforceBlock(packageName, appName)`

If `LockDecisionEngine` initialization is still service-owned during Task 4, expose it as a narrow callback or inject a narrow decision provider. Do not make `AppBlockCoordinator` own service lifecycle initialization.

The temporary `enforceBlock(...)` callback must be removed in Task 5.

### 8.3 Task 5 Temporary Callbacks

When moving `enforceBlock(...)`, these service-backed callbacks may exist temporarily:

- `scheduleDeferredBlockAction(targetPackage, delayMs, actionLabel, action)`
- `scheduleOverlayReleaseCheck(packageName)`
- `tryFallbackNavigation(packageName)`
- `tryForceStopApp(packageName)`

These callbacks must be removed in Task 6 when deferred actions, fallback navigation, force-stop behavior, and overlay release checks move into `AppBlockCoordinator`.

### 8.4 Temporary Callback Exit Rule

Phase 5 is not complete while the normal block route still depends on service-owned temporary callbacks listed above.

Acceptable service callbacks at Phase 5 completion are only narrow Android/platform access callbacks that cannot reasonably move yet, such as service lifecycle hooks or framework objects deliberately injected into the coordinator.

## 9. Implementation Tasks

### Task 1: Baseline Audit

Files:

- Read: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Read: `docs/plans/2026-05-24-phase-4-closeout-summary.md`
- Read: `issues_list.md`

Steps:

1. Record the current normal blocking method list and constants.
2. Confirm `ISSUE-007` still says protected release checks must only start after overlay is visible or confirmed visible.
3. Confirm `ISSUE-011` is not part of Phase 5 unless a Phase 5 regression touches it.
4. Run automated checks before changing code.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Build succeeds.
- Any failure is investigated before extraction begins.

### Task 2: Create `AppBlockCoordinator` Shell

Files:

- Create: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/block/AppBlockCoordinatorTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Create a coordinator with constructor dependencies but minimal behavior.
2. Add static or injectable pure helpers for block decision mapping where practical.
3. Add tests for any pure helper introduced in this task.
4. Wire the coordinator in `GuardAccessibilityService` without moving main behavior yet.

Initial public surface should be narrow:

```kotlin
class AppBlockCoordinator(
    ...
) {
    fun launchNormalPolicyCheck(packageName: String): GuardActionResult

    fun handleBlockHold(packageName: String, currentTime: Long): GuardActionResult

    fun handleWhitelistWindowEvent(packageName: String, currentTime: Long): GuardActionResult

    fun cancelPendingBlockActions(reason: String)
}
```

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected:

- No behavior is moved yet, or moved behavior is delegated.
- Router output remains unchanged.

### Task 3: Move Block Hold And Whitelist Transition

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/block/AppBlockCoordinatorTest.kt`

Steps:

1. Move `handleBlockHold(...)` into `AppBlockCoordinator`.
2. Move `handleWhitelistWindowEvent(...)` into `AppBlockCoordinator`.
3. Preserve log markers:
   - `应用 ... 在白名单中，跳过锁定`
   - `白名单过渡界面 ... 出现`
4. Keep `hideOverlay()` behavior behind `BlockSessionController` / callback boundary.
5. Update router adapters to call coordinator methods.
6. Use the Task 3 temporary callbacks only for methods that move in later tasks; do not pull Task 5 or Task 6 implementation into this task.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Device regression targets:

- 本应用打开通过。
- 白名单成功。
- 普通拦截仍成功.

### Task 4: Move Policy Check Entry

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Move `launchNormalPolicyCheck(...)` into `AppBlockCoordinator`.
2. Move `checkPolicyAndExecute(...)` into `AppBlockCoordinator`.
3. Keep coroutine ownership explicit:
   - Either pass a narrow `launch` callback from service.
   - Or inject a scope owned by service.
4. Keep `LockDecisionEngine` initialization behavior equivalent.
5. Preserve log markers:
   - `检查应用 ... 决策结果`
   - `全局锁开启`
   - `应用被永久禁用`
   - `应用使用时长已达限制`
   - `应用在禁用时段内`
   - `保持遮蔽层稳定窗口`
6. If `enforceBlock(...)` has not moved yet, call it only through the Task 4 temporary callback.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected:

- Router still receives `ScheduleFollowUp(reason = "normal_policy_check")`.
- Normal policy check remains asynchronous as before.

### Task 5: Move `enforceBlock(...)`

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Move `enforceBlock(...)` into `AppBlockCoordinator`.
2. Preserve current block order:
   - cancel pending block actions
   - detect protected surface
   - duplicate-overlay skip for non-protected packages
   - protected surface reinforce log
   - block cooldown check
   - record block session
   - decide whether to show overlay
   - show overlay through `BlockSessionController`
   - for protected surfaces, schedule protected release check only after show overlay
   - perform immediate BACK or HOME
   - schedule force-stop and fallback navigation
   - schedule overlay release check unless protected surface + newly shown overlay path already scheduled it
3. Preserve current timings:
   - block cooldown: `5000L`
   - block hold duration: `700L`
   - overlay reshow cooldown: `6000L`
   - deferred force-stop: `120L`, `360L`, `700L`
   - fallback navigation: `650L`, `1200L`
   - Huawei extra fallback: `420L`
4. Keep protected release callback boundary narrow.
5. Do not move protected-surface policy into `AppBlockCoordinator`.
6. Use the Task 5 temporary callbacks for deferred actions and release checks until Task 6 moves those methods.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Device regression targets:

- 普通拦截成功。
- 应用商店拦截成功，且 `com.xiaomi.market` 不出现遮蔽层卡死回归。
- 应用卸载仍能拦截，即使速度偏慢仍先保持行为等价。

### Task 6: Move Deferred Actions And Release Checks

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Move `scheduleDeferredBlockAction(...)`.
2. Move `canExecuteDeferredBlockAction(...)`.
3. Move `cancelPendingBlockActions(...)`.
4. Move normal `scheduleOverlayReleaseCheck(...)`.
5. Move `tryFallbackNavigation(...)`.
6. Move `tryForceStopApp(...)` and `forceStopPermissionDenied`.
7. Ensure all delayed work uses existing `GuardActionScheduler` owner keys.
8. Keep overlay release owner/key semantics stable:
   - `SCHEDULER_OWNER_PENDING_BLOCK`
   - `SCHEDULER_OWNER_OVERLAY_RELEASE`
9. Remove the Task 5 temporary callbacks after these methods move.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- No direct feature-owned runnable list is introduced.
- `onDestroy()` still cancels scheduler-owned delayed actions through existing scheduler cleanup.

### Task 7: Move Foreground Visibility Helpers

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

Steps:

1. Move `isTargetPackageActive(...)`.
2. Move `isPackageVisibleInInteractiveWindows(...)`.
3. Move `getRecentTopPackageName(...)`.
4. Prefer `WindowInspectorSnapshotApi.activePackageName()` and `interactiveWindowSnapshots()`.
5. Keep service callbacks only where Android framework access cannot be cleanly injected.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected:

- `GuardAccessibilityService` no longer owns normal block visibility logic.
- Protected and sensitive guards do not depend on `AppBlockCoordinator` internals.

### Task 8: Router Adapter Cleanup

Files:

- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt` only if needed.
- Test: `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`

Steps:

1. Update `createAccessibilityEventRouterAdapters()` so normal block callbacks point to `AppBlockCoordinator`.
2. Keep adapter names stable unless the current names become misleading.
3. Do not change router route order.
4. Confirm unsupported event handling remains `GuardActionResult.Continue`.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Router tests still pass.
- Window route order remains unchanged.
- Interaction route order remains unchanged.

### Task 9: Closeout And Device Regression

Files:

- Modify: `issues_list.md` only if Phase 5 reveals a new issue or changes an existing issue's status.
- Create after implementation: `docs/plans/2026-05-24-phase-5-closeout-summary.md`

Automated verification:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Required device regression:

- 本应用打开通过。
- 普通拦截成功。
- 白名单成功。
- 应用商店拦截成功，且 `com.xiaomi.market` 不出现遮蔽层卡死回归。
- 应用卸载仍能拦截。
- 使用情况访问仍能拦截。
- 悬浮窗权限仍能拦截。

Watch-only, not Phase 5 blockers unless behavior regresses from current baseline:

- 应用卸载速度偏慢。
- `ISSUE-011` MIUI 应用列表页误判。
- WeChat Finder 拦截失败。
- Honor/Huawei power-save behavior, unless directly touched.

## 10. Completion Criteria

Phase 5 is complete when:

- `AppBlockCoordinator.kt` exists and owns normal app blocking workflow.
- `createAccessibilityEventRouterAdapters()` normal block callbacks point to `AppBlockCoordinator`, including:
  - `ensureLockDecisionEngineInitialized`
  - `handleBlockHold`
  - `handleWhitelistWindowEvent`
  - `launchNormalPolicyCheck`
- `GuardAccessibilityService` no longer contains `launchNormalPolicyCheck(...)`, `checkPolicyAndExecute(...)`, `enforceBlock(...)`, `handleBlockHold(...)`, or `handleWhitelistWindowEvent(...)`.
- `GuardAccessibilityService` no longer owns normal deferred block scheduling.
- `GuardAccessibilityService` no longer owns normal overlay release checks.
- `GuardAccessibilityService` no longer owns `tryForceStopApp(...)`, `tryFallbackNavigation(...)`, or `forceStopPermissionDenied`.
- `GuardAccessibilityService` no longer owns normal app visibility helper logic unless a service-only callback is explicitly justified.
- The temporary callbacks listed in Section 8 are removed, except for explicitly justified Android/platform access callbacks.
- Router route order is unchanged.
- `BlockSessionController` remains the shared owner of block session state.
- `GuardActionScheduler` remains the only delayed-action scheduler.
- Protected-surface release-check timing remains equivalent to Phase 4 closeout.
- `forceStopPermissionDenied` is a private `AppBlockCoordinator` field; no `AppBlockState.kt` is required in Phase 5.
- Automated checks pass.
- Required device regression scenarios are recorded.

## 11. Rollback Plan

Keep Phase 5 changes in small commits or small patch chunks:

1. Shell creation.
2. Block hold / whitelist move.
3. Policy check move.
4. `enforceBlock(...)` move.
5. Deferred actions / release checks move.
6. Visibility helpers and adapter cleanup.

If a device regression appears, revert only the most recent chunk and keep earlier verified chunks. Do not mix bug-rule changes into the extraction patch unless the regression is directly caused by the extraction.

## 12. Notes For Implementation

- Do not fix `ISSUE-011` inside Phase 5.
- Do not extract WeChat Finder inside Phase 5.
- Do not extract power-save handling inside Phase 5.
- Do not rename Phase 3 router adapters casually.
- Do not introduce a second overlay/block state holder.
- Do not reintroduce direct `OverlayService.showOverlay(...)` calls outside `OverlayCoordinator` / `BlockSessionController`.
- Preserve current logs, delays, cooldowns, and scheduler owner keys unless a specific failing test or device log requires a change.
