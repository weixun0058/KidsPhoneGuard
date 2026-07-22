# Phase 7 Service Composition And Runtime Cleanup Implementation Plan

> **Product-scope update (2026-07-21):** This is a historical implementation plan. Statements below that `ISSUE-005` still requires Huawei/Honor device validation reflect the Phase 7 state at the time and have been superseded. The mapped ledger item `ISS-008` is now `WONTFIX`; device validation is outside the current required scope. Retained compatibility code does not constitute a verified-support claim.

Source plan: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`, especially Task 8: `Reduce GuardAccessibilityService to composition only`.

**Goal:** Reduce `GuardAccessibilityService` to an Android lifecycle entrypoint and composition root by moving the remaining routing-support, runtime-support, and service-local helper logic into focused collaborators.

**Architecture:** Phase 7 does not introduce new blocking rules and did not fix then-unresolved business issues such as WeChat Finder recognition or Huawei/Honor device validation. It finishes the structural cleanup left after Phase 6 by extracting assistant overlay compensation, runtime heartbeat/snapshot/receiver support, self-app event cleanup, and app-block engine initialization ownership behind narrow collaborators. `AccessibilityEventRouter` keeps event order ownership, while `GuardAccessibilityService` keeps only Android callbacks, object wiring, and externally visible service-health getters.

**Tech Stack:** Kotlin, Android `AccessibilityService`, `BroadcastReceiver`, `Handler`, coroutines, existing `AccessibilityEventRouter`, `EventRoutingState`, `GuardActionResult`, `GuardActionScheduler`, `WindowInspectorSnapshotApi`, `AppBlockCoordinator`, `BlockSessionController`, `ProtectedSurfaceGuard`.

---

## 1. Naming And Source

This document follows the existing phase naming convention:

- Phase 1: `docs/plans/2026-05-24-phase-1-seam-extraction-plan.md`
- Phase 2: `docs/plans/2026-05-24-phase-2-sensitive-action-extraction-plan.md`
- Phase 3: `docs/plans/2026-05-24-phase-3-accessibility-event-router-plan.md`
- Phase 4: `docs/plans/2026-05-24-phase-4-protected-surface-guard-extraction-plan.md`
- Phase 5: `docs/plans/2026-05-24-phase-5-app-block-coordinator-extraction-plan.md`
- Phase 6: `docs/plans/2026-05-24-phase-6-oem-special-guard-extraction-plan.md`
- Phase 7: `docs/plans/2026-05-24-phase-7-service-composition-runtime-cleanup-plan.md`

This plan is the detailed execution plan for the overall plan's Task 8. It is not a replacement for the overall decoupling plan.

## 2. Current State After Phase 6

Phase 6 has extracted:

- `WeChatFinderGuard`
- `HuaweiPowerSaveHandler`

The following logic still lives in `GuardAccessibilityService` and is the Phase 7 cleanup target:

- assistant/gameassistant overlay compensation:
  - `assistantPackages`
  - `scheduleAssistantFollowUpChecks(...)`
  - assistant package mapping inside `resolvePolicyPackage(...)`
- self-app route cleanup:
  - `handleSelfAppWindowEvent(...)`
- runtime support:
  - `lastEventSignalTimestamp`
  - `publishEventSignalIfNeeded(...)`
  - `publishLifecycleSignal(...)`
  - `logAccessibilitySettingsSnapshot(...)`
  - `heartbeatRunnable`
  - `protectedWindowSweepRunnable`
  - `blockAppReceiver`
- app-block engine initialization bridge:
  - `lockDecisionEngine`
  - `ensureLockDecisionEngineInitialized(...)`
  - direct `LockDecisionEngine.getInstance(...)` initialization in `initializeService()`
- minor pass-through wrappers:
  - `collapseVisibleSystemPanelIfNeeded(...)`
  - `sweepProtectedInteractiveWindows(...)`

The following should remain in `GuardAccessibilityService` after Phase 7:

- Android service callbacks:
  - `onCreate()`
  - `onServiceConnected()`
  - `onAccessibilityEvent(...)`
  - `onInterrupt()`
  - `onUnbind(...)`
  - `onRebind(...)`
  - `onDestroy()`
- composition/wiring of guards, coordinators, router, scheduler, and support objects.
- externally visible service-health API in the companion object:
  - `isServiceRunning()`
  - `getLatestLifecycleSignal()`

## 3. Scope

Phase 7 should extract:

- Assistant overlay package mapping and follow-up scheduling into a focused routing-support component.
- Runtime heartbeat, event-signal throttling, accessibility snapshot logging, protected sweep scheduling, and internal block broadcast registration into runtime support.
- Self-app event cleanup into a focused route handler.
- `LockDecisionEngine` initialization ownership out of `GuardAccessibilityService` and into app-block support.
- Remaining pass-through wrappers where they no longer add service-level value.

Phase 7 should not:

- Change event route order.
- Change debounce values, follow-up delays, cooldown values, block hold timings, or scheduler owner keys.
- Fix `ISSUE-004` WeChat Finder recognition.
- Fix `ISSUE-005` Huawei/Honor behavior before real device logs exist.
- Change protected-settings keyword rules.
- Change normal app blocking policy behavior.
- Rework `OverlayService` internals.
- Add broad OEM abstraction that mixes unrelated special cases.

## 4. Target Files

Create:

- `app/src/main/java/com/kidsphoneguard/service/accessibility/AssistantOverlayRoutingSupport.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/SelfAppEventHandler.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/ServiceRuntimeSupport.kt`
- `app/src/main/java/com/kidsphoneguard/service/block/LockDecisionEngineProvider.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/AssistantOverlayRoutingSupportTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/SelfAppEventHandlerTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/ServiceRuntimeSupportTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/block/LockDecisionEngineProviderTest.kt`

Modify:

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`, only if adapter signatures need a narrow replacement for assistant routing support.
- `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`, to consume `LockDecisionEngineProvider` or equivalent narrow provider instead of service-owned engine callbacks.
- `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`, only if router adapter signatures change.
- `app/src/test/java/com/kidsphoneguard/service/block/AppBlockCoordinatorTest.kt`, if app-block engine provider wiring changes constructor shape.

Do not modify:

- `SensitiveActionGuard.kt`
- `ProtectedSurfaceGuard.kt`, unless runtime sweep callback wiring needs a compile-only adjustment.
- `SystemSurfaceGuard.kt`, unless a pass-through wrapper removal requires a compile-only adjustment.
- `WeChatFinderGuard.kt`
- `HuaweiPowerSaveHandler.kt`
- protected settings rule files.

## 5. Component Boundaries

### 5.1 `AssistantOverlayRoutingSupport`

Owns:

- assistant package set:
  - `com.huawei.gameassistant`
  - `com.hihonor.gameassistant`
- assistant event package remapping currently inside `resolvePolicyPackage(...)`.
- assistant follow-up scheduling currently inside `scheduleAssistantFollowUpChecks(...)`.
- assistant follow-up delay values:
  - `120L`
  - `320L`
  - `680L`
- scheduler owner:
  - `SCHEDULER_OWNER_ASSISTANT_FOLLOW_UP`
- route result:
  - `GuardActionResult.ScheduleFollowUp(reason = "assistant_follow_up")`
- existing log marker:
  - `助手覆盖场景补偿检测: ...`

Does not own:

- normal app policy decision logic.
- overlay state.
- protected-surface state.
- WeChat Finder logic.
- Huawei power-save logic.
- router route order.

Dependencies should be narrow:

- `GuardActionScheduler`
- `WindowInspectorSnapshotApi`
- `EventRoutingState`
- callbacks for:
  - `getRecentTopPackageName()`
  - `checkPolicyAndExecute(packageName)`
  - `isSelfApp(packageName)`
  - `isInWhitelist(packageName)`
  - async launch / coroutine execution

Implementation constraint:

- Do not let this class directly own `CoroutineScope` unless the constructor clearly documents cancellation ownership.
- Prefer a callback such as `launchPolicyCheck(packageName: String)` implemented by the service composition root or by a small runtime callback wrapper.
- Do not duplicate router debounce state. It must receive and update the same `EventRoutingState` instance used by `AccessibilityEventRouter`.

### 5.2 `SelfAppEventHandler`

Owns:

- self-app route cleanup currently inside `handleSelfAppWindowEvent(...)`.
- `GuardActionResult.Consumed(reason = "self_app_event", ...)` semantics.
- existing protected overlay preservation behavior:
  - keep protected overlay if current overlay blocked package is protected.
  - keep protected pending package if pending package is protected.
- existing log marker:
  - `self_app_event_keep_protected_overlay ...`

Does not own:

- whitelist database/policy.
- protected-surface rules.
- overlay implementation.
- normal block policy execution.

Dependencies should be callbacks:

- `isSelfApp(packageName)`
- `isOverlayShowing()`
- `readCurrentBlockedPackage()`
- `pendingBlockPackage()`
- `isProtectedSystemSurface(packageName)`
- `cancelPendingBlockActions(reason)`
- `clearLastBlockedPackage()`
- `hideOverlay()`

This keeps self-app cleanup as a route adapter, not as part of normal app blocking.

### 5.3 `ServiceRuntimeSupport`

Owns:

- event lifecycle signal throttling:
  - `lastEventSignalTimestamp`
  - `publishEventSignalIfNeeded(event)`
- accessibility settings snapshot logging:
  - `accessibility_service_snapshot`
- heartbeat scheduling:
  - existing heartbeat interval `4000L`
  - scheduler owner `SCHEDULER_OWNER_HEARTBEAT`
- protected interactive window sweep scheduling:
  - `protectedWindowSweepIntervalMs`
  - scheduler owner `SCHEDULER_OWNER_PROTECTED_SWEEP`
  - log marker `protected_window_sweep_failed`
- internal block broadcast registration/unregistration around `BroadcastPermissionHelper.ACTION_BLOCK_APP`

Does not own:

- protected-surface sweep logic.
- app blocking policy.
- `OverlayService` state.
- route order.
- Android service lifecycle decisions.
- global lifecycle signal ownership:
  - `latestLifecycleSignal` must remain owned by the composition root or by a very thin `LifecycleSignalSink`.
  - `ServiceRuntimeSupport` may call a lifecycle-signal callback, but it must not become the global signal owner.
  - feature guards such as `ProtectedSurfaceGuard` and `WeChatFinderGuard` must not depend on `ServiceRuntimeSupport` to publish lifecycle signals.

Dependencies should be narrow:

- `Context` or `ContentResolver` for settings snapshot.
- `GuardActionScheduler`.
- callbacks for:
  - `touchHeartbeat()`
  - `clearHeartbeat()`
  - `setRunning(Boolean)`
  - `publishLifecycleSignal(String)`
  - `sweepProtectedInteractiveWindows(source)`
  - `handleBlockBroadcast(packageName)`
- `BroadcastReceiver` ownership may live inside `ServiceRuntimeSupport`, but registration must still use `BroadcastPermissionHelper`.

Important boundary:

- `ServiceRuntimeSupport` may schedule `periodic_window_sweep`, but it must not evaluate protected settings or know protected-surface package rules.
- It should call the provided sweep callback only.
- `ServiceRuntimeSupport` may own the block receiver object and registration helper, but the registration timing must stay attached to the delayed `SCHEDULER_OWNER_SERVICE_INIT` / `initialize` / `100L` path unless a separate behavior-change note and device verification are added.
- If a `LifecycleSignalSink` is created, keep it intentionally tiny: write `latestLifecycleSignal`, read it for the companion getter, and expose a callback. Do not add routing, overlay, block, or runtime scheduling logic to it.

### 5.4 `LockDecisionEngineProvider`

Owns:

- lazy initialization of `LockDecisionEngine`.
- delayed initialization used during service startup.
- `ensureInitialized()` behavior.
- `getBlockDecision(packageName)` delegation.

Does not own:

- app block workflow.
- overlay behavior.
- router state.

Expected usage:

- `AppBlockCoordinator` should depend on this provider or equivalent narrow interface instead of service-owned `lockDecisionEngine` and `ensureLockDecisionEngineInitialized(...)` callbacks.
- `GuardAccessibilityService.initializeService()` should call a provider method, not assign `lockDecisionEngine` directly.

This is the last meaningful service-owned app-block state left after Phase 5.

## 6. GuardActionResult Semantics

Phase 7 must preserve existing result semantics:

- Assistant follow-up:
  - `ScheduleFollowUp(reason = "assistant_follow_up")`
  - stop routing.
- Self-app event:
  - `Consumed(reason = "self_app_event", hasSideEffect = false)` when preserving protected overlay.
  - `Consumed(reason = "self_app_event", hasSideEffect = true)` when canceling pending block actions or hiding overlay.
  - `Continue` when package is not self app.
- Unsupported event types:
  - still `Continue`.
- Runtime support methods:
  - should not invent new router results unless they are explicitly route adapters.

Do not introduce `Blocked` in Phase 7 unless an actual block session request is performed through `BlockSessionController`. Most Phase 7 work is routing/runtime cleanup and should not need `Blocked`.

## 7. Scheduler Owner And Timing Preservation

Do not change these values during Phase 7:

- Assistant follow-up:
  - owner: `SCHEDULER_OWNER_ASSISTANT_FOLLOW_UP`
  - keys: `delay_120`, `delay_320`, `delay_680`
  - delays: `120L`, `320L`, `680L`
- Heartbeat:
  - owner: `SCHEDULER_OWNER_HEARTBEAT`
  - key: `tick`
  - delay: `4000L`
- Protected sweep:
  - owner: `SCHEDULER_OWNER_PROTECTED_SWEEP`
  - key: `tick`
  - delay: current `protectedWindowSweepIntervalMs`
- Service init:
  - owner: `SCHEDULER_OWNER_SERVICE_INIT`
  - key: `initialize`
  - delay: `100L`

If a task must move a scheduled action, move owner/key/delay together with it.

## 8. Temporary Callback Boundaries

Phase 7 moves callers and callees in multiple steps. To keep tasks reversible, these temporary callbacks are allowed:

### Task 2 assistant support callbacks

`AssistantOverlayRoutingSupport` may temporarily call back into service or existing collaborators for:

- `appBlockCoordinator.getRecentTopPackageName()`
- `appBlockCoordinator.checkPolicyAndExecute(packageName)`
- `WhitelistManager.isSelfApp(packageName)`
- `WhitelistManager.isInWhitelist(packageName)`
- `serviceScope.launch { ... }`

Exit condition:

- After Task 2, `GuardAccessibilityService` should no longer contain `assistantPackages`, `scheduleAssistantFollowUpChecks(...)`, or assistant mapping logic inside `resolvePolicyPackage(...)`.

### Task 3 runtime support callbacks

`ServiceRuntimeSupport` may call explicit callbacks for:

- `GuardHealthState.touchAccessibilityHeartbeat(context)`
- `GuardHealthState.clearAccessibilityHeartbeat(context)`
- `protectedSurfaceGuard.sweepProtectedInteractiveWindows(source)`
- `appBlockCoordinator.checkPolicyAndExecute(packageName)`
- service companion running/lifecycle updates.
- lifecycle signal publishing through the composition root or a tiny `LifecycleSignalSink`.

Exit condition:

- Runtime support must not call feature guards directly except through named callbacks supplied by the service composition root.
- Runtime support must not own `latestLifecycleSignal`; it only invokes the supplied lifecycle signal callback.
- Block receiver registration must still be invoked from the delayed service initialization path, not moved directly into `onCreate()` or `onServiceConnected()`.

### Task 4 self-app callbacks

`SelfAppEventHandler` may call explicit callbacks for:

- `protectedSurfaceGuard.isProtectedSystemSurface(packageName)`
- `appBlockCoordinator.cancelPendingBlockActions(reason)`
- `appBlockCoordinator.hideOverlay()`
- `blockSessionController.clearLastBlockedPackage()`

Exit condition:

- `GuardAccessibilityService` should not retain the self-app cleanup method body.

### Task 5 app-block provider callbacks

`AppBlockCoordinator` may temporarily accept both old callbacks and new provider during migration.

Exit condition:

- `GuardAccessibilityService` should no longer have a `lockDecisionEngine` field or `ensureLockDecisionEngineInitialized(...)` method.

## 9. Task Breakdown

### Task 1: Freeze Current Residual Service Responsibilities

**Files:**

- Read: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Read: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
- Read: `docs/plans/2026-05-24-phase-6-closeout-summary.md`

**Steps:**

1. Record the current remaining service-owned methods and fields listed in this plan.
2. Confirm the current route order in `AccessibilityEventRouter` remains:
   - power-save
   - system panel
   - sensitive action
   - protected settings / protected surface
   - self app
   - WeChat Finder
   - normal app policy
3. Confirm interaction events still only run:
   - system panel
   - sensitive action
   - protected settings / protected surface
4. Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Build successful.

Do not edit code in Task 1.

### Task 2: Extract `AssistantOverlayRoutingSupport`

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/accessibility/AssistantOverlayRoutingSupport.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/accessibility/AssistantOverlayRoutingSupportTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`, only if adapter names need to be narrowed.
- Modify: `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`, only if adapter names change.

**Steps:**

1. Create a shell class with constructor dependencies only.
2. Add pure tests for:
   - `isAssistantPackage("com.huawei.gameassistant") == true`
   - `isAssistantPackage("com.hihonor.gameassistant") == true`
   - unrelated package returns false.
3. Move assistant package set into the new class.
4. Move assistant package remapping from `resolvePolicyPackage(...)` into a method such as:

```kotlin
fun resolvePolicyPackage(eventPackageName: String): String
```

5. Preserve existing fallback order:
   - active package from `WindowInspectorSnapshotApi.activePackageName()`
   - fallback package from `AppBlockCoordinator.getRecentTopPackageName()`
6. Preserve existing self-app exclusion before remapping.
7. Move assistant follow-up scheduling into:

```kotlin
fun scheduleFollowUpChecks(routingState: EventRoutingState): GuardActionResult
```

8. Preserve:
   - delays
   - scheduler owner/key
   - whitelist/self-app filtering
   - debounce state reuse
   - log marker
   - `ScheduleFollowUp(reason = "assistant_follow_up")`
9. Wire `AccessibilityEventRouter.Adapters` to the new support object.
10. Remove service-owned:
   - `assistantPackages`
   - `scheduleAssistantFollowUpChecks(...)`
   - assistant-specific body inside `resolvePolicyPackage(...)`
11. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.accessibility.AssistantOverlayRoutingSupportTest" --tests "com.kidsphoneguard.service.accessibility.AccessibilityEventRouterTest"
```

12. Run full check:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Assistant follow-up still schedules the same delayed checks.
- Router state is reused, not duplicated.
- No route order change.

### Task 3: Extract `ServiceRuntimeSupport`

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/accessibility/ServiceRuntimeSupport.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/accessibility/ServiceRuntimeSupportTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Steps:**

1. Create a shell class that accepts:
   - `logTag`
   - `GuardActionScheduler`
   - current time provider for tests
   - content resolver / settings reader capability
   - heartbeat callbacks
   - lifecycle signal callback
   - protected sweep callback
   - block broadcast callback
2. Move `lastEventSignalTimestamp` into `ServiceRuntimeSupport`.
3. Move `publishEventSignalIfNeeded(event)` into runtime support.
4. Keep the same 2000 ms event signal throttle.
5. Move `logAccessibilitySettingsSnapshot(source)` into runtime support.
6. Preserve marker:

```text
accessibility_service_snapshot
```

7. Move heartbeat scheduling into runtime support:
   - keep owner/key/delay.
   - keep `GuardHealthState.touchAccessibilityHeartbeat(...)` behavior via callback or context-bound method.
8. Move protected sweep scheduling into runtime support:
   - runtime owns scheduling only.
   - protected sweep evaluation remains in `ProtectedSurfaceGuard`.
9. Move `blockAppReceiver` registration/unregistration into runtime support:
   - keep `BroadcastPermissionHelper.registerInternalBroadcastReceiver(...)`.
   - keep `BroadcastPermissionHelper.unregisterReceiver(...)`.
   - keep action `BroadcastPermissionHelper.ACTION_BLOCK_APP`.
   - preserve exception log marker for registration failure.
   - keep the current registration timing: registration is still triggered by the delayed `initializeService()` path scheduled under `SCHEDULER_OWNER_SERVICE_INIT`, key `initialize`, delay `100L`.
   - do not register the receiver immediately in `onCreate()` or `onServiceConnected()` unless the change is explicitly documented and separately device-verified.
10. Modify service lifecycle callbacks to call runtime support methods.
11. Remove service-owned:
   - `lastEventSignalTimestamp`
   - `heartbeatRunnable`
   - `protectedWindowSweepRunnable`
   - `blockAppReceiver`
   - `publishEventSignalIfNeeded(...)`
   - `logAccessibilitySettingsSnapshot(...)`
12. Keep `latestLifecycleSignal` publishing as a composition-root callback or a tiny `LifecycleSignalSink`.
    - Do not move lifecycle signal ownership into `ServiceRuntimeSupport`.
    - Do not make `ProtectedSurfaceGuard`, `WeChatFinderGuard`, or other feature modules depend on `ServiceRuntimeSupport` to publish lifecycle signals.
13. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.accessibility.ServiceRuntimeSupportTest"
```

14. Run full check:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Heartbeat still starts on service create / service connected.
- Heartbeat still clears on interrupt / unbind / destroy.
- Protected window sweep still schedules periodically.
- Internal block broadcast still routes to app-block policy check.

### Task 4: Extract `SelfAppEventHandler`

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/accessibility/SelfAppEventHandler.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/accessibility/SelfAppEventHandlerTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`, only if adapter naming changes.

**Steps:**

1. Create a shell class with callback dependencies only.
2. Write tests for:
   - non-self package returns `Continue`.
   - self package with protected overlay returns `Consumed(reason = "self_app_event", hasSideEffect = false)`.
   - self package with pending block clears pending/cancels actions and returns `Consumed(..., hasSideEffect = true)`.
   - self package with visible non-protected overlay hides overlay and returns `Consumed(..., hasSideEffect = true)`.
3. Move method body from `handleSelfAppWindowEvent(...)`.
4. Keep protected overlay preservation logic unchanged.
5. Wire router adapter to `selfAppEventHandler::handle`.
6. Remove service-owned `handleSelfAppWindowEvent(...)`.
7. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.accessibility.SelfAppEventHandlerTest" --tests "com.kidsphoneguard.service.accessibility.AccessibilityEventRouterTest"
```

8. Run full check:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- 本应用打开仍通过。
- Protected overlay is not accidentally cleared by self-app events.

### Task 5: Move `LockDecisionEngine` Ownership Out Of The Service

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/block/LockDecisionEngineProvider.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/block/LockDecisionEngineProviderTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/test/java/com/kidsphoneguard/service/block/AppBlockCoordinatorTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Steps:**

1. Create a provider that owns:
   - `LockDecisionEngine.getInstance(context)`
   - lazy initialization
   - `ensureInitialized()`
   - `getBlockDecision(packageName)`
2. Preserve logs:
   - `LockDecisionEngine 初始化成功`
   - `LockDecisionEngine 初始化失败`
   - `LockDecisionEngine 延迟初始化成功`
   - `LockDecisionEngine 延迟初始化失败`
3. Wire `AppBlockCoordinator` to use the provider rather than service-owned callbacks.
4. Update `initializeService()` to call provider initialization instead of assigning a service field.
5. Remove service-owned:
   - `lockDecisionEngine`
   - `ensureLockDecisionEngineInitialized(...)`
6. Keep `AppBlockCoordinator.ensureLockDecisionEngineInitializedAsResult()` result semantics unchanged.
7. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.block.LockDecisionEngineProviderTest" --tests "com.kidsphoneguard.service.block.AppBlockCoordinatorTest"
```

8. Run full check:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- Normal app block still initializes policy engine before policy checks.
- Service no longer owns app-block engine state.

### Task 6: Remove Remaining Pass-Through Wrappers And Tighten Composition

**Files:**

- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify tests only if constructor wiring changes.

**Steps:**

1. Remove pass-through wrappers if no longer needed:
   - `collapseVisibleSystemPanelIfNeeded(...)`
   - `sweepProtectedInteractiveWindows(...)`
2. Wire callers directly to existing collaborators or runtime support callbacks.
3. Keep `GuardAccessibilityService` object creation readable:
   - router
   - runtime support
   - assistant routing support
   - self-app handler
   - block coordinator
   - protected/system/sensitive/WeChat/OEM guards
4. Do not reorder lazy properties in a way that creates initialization cycles.
5. Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Expected:

- No behavior change.
- `GuardAccessibilityService` is smaller and mostly composition plus Android callbacks.

### Task 7: Phase 7 Closeout And Device Regression

**Files:**

- Create after implementation: `docs/plans/2026-05-24-phase-7-closeout-summary.md`
- Modify: `issues_list.md` only if device validation changes known issue status or adds new evidence.

**Steps:**

1. Run full automated checks:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

2. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.accessibility.AssistantOverlayRoutingSupportTest" --tests "com.kidsphoneguard.service.accessibility.ServiceRuntimeSupportTest" --tests "com.kidsphoneguard.service.accessibility.SelfAppEventHandlerTest" --tests "com.kidsphoneguard.service.block.LockDecisionEngineProviderTest" --tests "com.kidsphoneguard.service.accessibility.AccessibilityEventRouterTest" --tests "com.kidsphoneguard.service.block.AppBlockCoordinatorTest"
```

3. Device regression targets:

- 本应用打开通过。
- 普通拦截通过。
- 白名单通过。
- 应用商店拦截通过，且 `com.xiaomi.market` 不出现 overlay 卡死回归。
- 应用卸载拦截通过。
- 使用情况访问拦截通过。
- 悬浮窗权限拦截通过。
- 无障碍权限设置记录结果，不在本阶段混修。
- 微信视频号入口/详情记录结果，不在本阶段混修 `ISSUE-004`。
- Phase 7 当时的可选回归项：华为/荣耀省电模式如有设备则验证；无设备则保留 `ISSUE-005`。（该状态已由文首 2026-07-21 产品决策取代。）

4. Closeout summary must state:

- Which service-owned methods/fields were removed.
- Which methods intentionally remain in service and why.
- Automated check results.
- Device regression results.
- Whether `issues_list.md` changed.

## 10. Completion Criteria

Phase 7 is complete only when all of the following are true:

- `GuardAccessibilityService` no longer contains:
  - `assistantPackages`
  - `scheduleAssistantFollowUpChecks(...)`
  - assistant mapping body inside `resolvePolicyPackage(...)`
  - `handleSelfAppWindowEvent(...)`
  - `lastEventSignalTimestamp`
  - `heartbeatRunnable`
  - `protectedWindowSweepRunnable`
  - `blockAppReceiver`
  - `publishEventSignalIfNeeded(...)`
  - `logAccessibilitySettingsSnapshot(...)`
  - `lockDecisionEngine`
  - `ensureLockDecisionEngineInitialized(...)`
- `AccessibilityEventRouter` still owns route order.
- Assistant follow-up reuses the same `EventRoutingState`; there is no duplicate debounce state.
- Runtime support schedules heartbeat and protected sweep through `GuardActionScheduler`.
- Self-app cleanup does not clear protected overlay/pending protected surface state.
- `AppBlockCoordinator` no longer depends on service-owned `LockDecisionEngine` state.
- `initializeService()` is deleted or reduced to a thin wrapper that only calls provider/runtime-support initialization methods.
  - It must no longer directly contain receiver registration logic.
  - It must no longer directly assign or initialize `LockDecisionEngine`.
  - It must no longer contain broad initialization exception/log orchestration beyond wrapping the thin initialization call.
- `GuardAccessibilityService.onAccessibilityEvent(...)` still only:
  - touches runtime health/event signal.
  - forwards event to router.
  - catches/logs route exceptions.
- Android lifecycle callbacks remain readable and thin.
- Full automated checks pass:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

- Phase 7 closeout summary exists:
  - `docs/plans/2026-05-24-phase-7-closeout-summary.md`

## 11. Historical Non-Goals And Explicit Open Items

At Phase 7 closeout, these issues were not to be closed merely because the structural phase was complete:

- `ISSUE-004`: WeChat Finder still needs fresh recognition logs and possibly expanded detection.
- `ISSUE-005`: Huawei/Honor power-save still needed real-device validation under the scope at that time. The mapped `ISS-008` was later changed to `WONTFIX` on 2026-07-21.
- Slow protected settings interception should remain in the issue ledger unless this phase produces new timing evidence.

Phase 7 is a structural cleanup phase. If a device regression appears, fix only the regression caused by this phase. Do not mix in broad rule changes.

## 12. Suggested Implementation Order

Recommended order:

1. Baseline and residual inventory.
2. Assistant overlay routing support.
3. Runtime support.
4. Self-app event handler.
5. Lock decision engine provider.
6. Pass-through wrapper cleanup.
7. Closeout and device regression.

This order keeps route behavior stable before moving lifecycle and engine ownership. Each task should compile and test independently before continuing.

## 13. Review Checklist

Before implementation, reviewer should confirm:

- Is it acceptable for Phase 7 to extract assistant/gameassistant follow-up now?
- Should `LockDecisionEngineProvider` be created as its own class, or should `AppBlockCoordinator` own the engine directly?
- Should `ServiceRuntimeSupport` own broadcast receiver registration now, or should receiver extraction be delayed if it feels too runtime-heavy?
- Are the completion criteria strict enough to call the service composition-only?

During implementation, reviewer should check:

- No route order changes.
- No scheduler owner/key/delay changes.
- No duplicate debounce state.
- No feature guard directly calls another feature guard's internal helper.
- `GuardAccessibilityService` does not regain business method bodies through convenience wrappers.
