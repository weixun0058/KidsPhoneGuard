# Phase 3 Accessibility Event Router Implementation Plan

> **Status note as of 2026-05-31:** the router boundary remains relevant, but all references to `SensitiveActionGuard`, `NodeActionSession`, and sensitive-action route steps are obsolete after uninstall-protection removal.

**Goal:** Introduce an explicit `AccessibilityEventRouter` so `GuardAccessibilityService` becomes a thin Android entrypoint that forwards accessibility events into a centralized routing pipeline without changing current runtime behavior.

**Architecture:** Phase 3 promotes `GuardActionResult` from a local `SensitiveActionGuard` contract into the router-level flow-control protocol. The router owns event-type dispatch, branch order, and early-return behavior; existing business methods stay in `GuardAccessibilityService` behind temporary adapter callbacks until later phases extract `ProtectedSurfaceGuard`, `AppBlockCoordinator`, OEM handlers, and special-case guards.

**Tech Stack:** Kotlin, Android `AccessibilityService`, `AccessibilityEvent`, existing `GuardActionResult`, existing `SensitiveActionGuard`, existing Phase 1 seams (`NavigationExecutor`, `GuardActionScheduler`, `WindowInspectorSnapshotApi`, `NodeActionSession`, `BlockSessionController`)

---

## 1. Scope

Phase 3 makes event routing explicit. It does not extract the remaining feature modules.

Create or modify only these areas:

- Create: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
- Optionally create: `app/src/main/java/com/kidsphoneguard/service/accessibility/EventRoutingState.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`

Do not create or extract these in Phase 3:

- `ProtectedSurfaceGuard`
- `SystemSurfaceGuard`
- `AppBlockCoordinator`
- Huawei/Honor power-save handler files
- `WeChatFinderGuard`
- normal app policy coordinator files

## 2. Non-Negotiable Constraints

- Do not change the current event handling order.
- Do not change cooldowns, delay arrays, scheduler owners, scheduler keys, overlay hold durations, or log marker strings.
- Do not move overlay/block-session state out of `BlockSessionController`.
- Do not move sensitive-action logic back into `GuardAccessibilityService`; `SensitiveActionGuard.handle(...)` remains the sensitive-action step.
- Do not make `AccessibilityEventRouter` perform overlay, navigation, node search, keyword search, database reads, or policy evaluation itself.
- The router may call existing service methods only through explicit adapter callbacks. This is temporary and intentional.
- Each route step must return `GuardActionResult`; raw `Boolean` early-return control should be contained inside adapter methods and not leak into the router pipeline.
- Preserve the existing two event entry families:
  - window events: `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOWS_CHANGED`
  - interaction events: `TYPE_VIEW_CLICKED`, `TYPE_VIEW_LONG_CLICKED`, `TYPE_WINDOW_CONTENT_CHANGED`
- Keep lifecycle, heartbeat, and service health publishing in `GuardAccessibilityService` unless a later task explicitly moves them. Phase 3 is about event routing, not service lifecycle.

## 3. Target Behavior After Phase 3

After Phase 3:

- `GuardAccessibilityService.onAccessibilityEvent()` still performs heartbeat/error boundary work, then delegates event routing to `AccessibilityEventRouter`.
- `AccessibilityEventRouter` owns:
  - event type classification
  - window-event route order
  - interaction-event route order
  - route-local package normalization context
  - debounce/routing state that controls whether downstream steps run
  - `GuardActionResult` interpretation and early stop behavior
- Existing feature logic remains implemented in `GuardAccessibilityService` through adapter methods.
- `SensitiveActionGuard` remains a real extracted guard and is called as one router step.
- Later phases can replace individual adapter steps with real guard/coordinator classes without changing route order again.

## 4. Router Entry Points

`AccessibilityEventRouter` should expose one public entrypoint:

```kotlin
fun route(event: AccessibilityEvent): GuardActionResult
```

The router owns event type dispatch:

- `TYPE_WINDOW_STATE_CHANGED`
- `TYPE_WINDOWS_CHANGED`
  - route through `routeWindowEvent(event)`
- `TYPE_VIEW_CLICKED`
- `TYPE_VIEW_LONG_CLICKED`
- `TYPE_WINDOW_CONTENT_CHANGED`
  - route through `routeInteractionEvent(event)`
- all other event types
  - return `GuardActionResult.Continue`. The service currently does nothing for these events, so `Continue` is the closest behavior-preserving result.

`GuardAccessibilityService.onAccessibilityEvent()` should become roughly:

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent) {
    GuardHealthState.touchAccessibilityHeartbeat(this)
    publishEventSignalIfNeeded(event)
    try {
        accessibilityEventRouter.route(event)
    } catch (e: Exception) {
        Log.e(TAG, "处理无障碍事件时出错: ${e.message}", e)
    }
}
```

Do not move `GuardHealthState.touchAccessibilityHeartbeat(...)`, `publishEventSignalIfNeeded(...)`, or the top-level exception boundary in the first router task.

## 5. Route Order To Preserve

### 5.1 Window Event Route

Current `handleWindowEvent()` order must be preserved:

1. Read event package; empty/null package stops routing with no side effect.
2. Build source marker: `window_event:${event.eventType}:$eventPackageName`.
3. Resolve policy package.
4. Check protected-window sweep and possibly replace package with protected interactive window package.
5. If event package is an assistant package and no protected window was found, schedule assistant follow-up checks and stop.
6. Huawei/Honor power-save exit adapter.
7. System panel collapse adapter.
8. `SensitiveActionGuard.handle(event, packageName)`.
9. Protected settings policy adapter.
10. Self-app handling adapter.
11. WeChat Finder adapter.
12. LockDecisionEngine readiness check.
13. Block hold and debounce checks.
14. Whitelist transition handling.
15. Record current package.
16. Launch normal policy check (`checkPolicyAndExecute(packageName)`) and stop.

### 5.2 Interaction Event Route

Current `handlePotentialProtectedInteraction()` order must be preserved:

1. Read event package; empty package stops routing with no side effect.
2. Build source marker: `interactive_event:${event.eventType}:$eventPackageName`.
3. Resolve policy package.
4. Check protected-window sweep and possibly replace package with protected interactive window package.
5. System panel collapse adapter.
6. `SensitiveActionGuard.handle(event, packageName)`.
7. Protected settings policy adapter.
8. Stop.

Interaction events should not run self-app handling, WeChat Finder handling, or normal app policy in Phase 3 because current code does not.

## 6. Adapter Boundary

Because most business logic intentionally remains in `GuardAccessibilityService`, create an explicit adapter interface or callback holder for the router. Prefer one constructor dependency such as:

```kotlin
class AccessibilityEventRouter(
    private val logTag: String,
    private val sensitiveActionGuard: SensitiveActionGuard,
    private val adapters: Adapters,
    private val state: EventRoutingState = EventRoutingState()
)
```

Example adapter shape:

```kotlin
class Adapters(
    val resolvePolicyPackage: (String) -> String,
    val shouldSweepProtectedWindows: (AccessibilityEvent, String) -> Boolean,
    val findProtectedInteractiveWindowPackage: (String) -> String?,
    val isAssistantPackage: (String) -> Boolean,
    val scheduleAssistantFollowUpChecks: () -> GuardActionResult,
    val exitPowerSaveModeIfNeeded: (AccessibilityEvent, String) -> GuardActionResult,
    val collapseSystemPanelIfNeeded: (AccessibilityEvent, String, String) -> GuardActionResult,
    val handleProtectedSettingsPolicyIfCandidate: (AccessibilityEvent, String, String) -> GuardActionResult,
    val handleSelfAppWindowEvent: (String) -> GuardActionResult,
    val handleWeChatFinder: (AccessibilityEvent, String) -> GuardActionResult,
    val ensureLockDecisionEngineInitialized: () -> GuardActionResult,
    val readBlockedPackage: () -> String,
    val readBlockHoldUntil: () -> Long,
    val isWhitelistedNonPolicyPackage: (String) -> Boolean,
    val handleWhitelistWindowEvent: (String, Long) -> GuardActionResult,
    val launchNormalPolicyCheck: (String) -> GuardActionResult
)
```

This shape is illustrative. Keep the final adapter cohesive and reviewable, but do not hide route order inside a single `handleWindowEventAdapter(...)` callback. If a callback contains the old full route chain, Phase 3 has not achieved its purpose.

## 7. Existing Methods That Stay In Service

These methods should stay in `GuardAccessibilityService` during Phase 3, exposed only through adapter callbacks or small wrapper methods:

- `resolvePolicyPackage(...)`
- `shouldSweepProtectedWindows(...)`
- `findProtectedInteractiveWindowPackage(...)`
- `scheduleAssistantFollowUpChecks()`
- `exitPowerSaveModeIfNeeded(...)`
- `collapseSystemPanelIfNeeded(...)`
- `handleProtectedSettingsPolicyIfCandidate(...)`
- self-app overlay release/keep behavior currently inline in `handleWindowEvent()`
- `shouldBlockWeChatFinder(...)`
- `blockWeChatFinder(...)`
- `ensureLockDecisionEngineInitialized()`
- whitelist transition handling currently inline in `handleWindowEvent()`
- `checkPolicyAndExecute(...)`
- `isTargetPackageActive(...)`
- overlay release helpers
- protected-surface helpers
- power-save live-node helpers
- system-panel helpers

Add small private service wrapper methods where needed to convert existing boolean/unit behavior into `GuardActionResult`, but do not move their internals into new guard files in Phase 3.

## 8. Branches To Convert To `GuardActionResult`

### 8.1 Already Converted

- Sensitive action:
  - `SensitiveActionGuard.handle(event, packageName): GuardActionResult`

### 8.2 Convert Through Adapters In Phase 3

These branches should become explicit router steps returning `GuardActionResult`, while their implementation remains in service:

- assistant follow-up
  - current behavior: schedule follow-up and return
  - result: `GuardActionResult.ScheduleFollowUp(reason = "assistant_follow_up")`
- power-save exit
  - current behavior: if handled, return
  - result when handled: `GuardActionResult.Consumed(reason = "power_save_exit", hasSideEffect = true)`
- system panel collapse
  - current behavior: if handled, return
  - result when handled: `GuardActionResult.Consumed(reason = "system_panel_collapse", hasSideEffect = true)`
- protected settings policy
  - current behavior: if handled, return
  - result when handled: `GuardActionResult.Consumed(reason = "protected_settings_policy", hasSideEffect = true)`
- self-app window event
  - current behavior: may clear pending actions/hide overlay, then return
  - result: `GuardActionResult.Consumed(reason = "self_app_event", hasSideEffect = <true only if cleanup/hide happened>)`
- WeChat Finder
  - current behavior: if finder should block, call block and return
  - result after `blockWeChatFinder(...)`: `GuardActionResult.Consumed(reason = "wechat_finder", hasSideEffect = true)`
  - do not use `Blocked` for WeChat Finder in Phase 3 unless a later phase explicitly separates cooldown/no-op from a new block request
- lock engine unavailable
  - current behavior: return
  - result: `GuardActionResult.Consumed(reason = "lock_engine_unavailable", hasSideEffect = false)`
- block hold / debounce
  - current behavior: return
  - result: `GuardActionResult.Consumed(reason = "block_hold" | "debounce", hasSideEffect = false)`
- whitelist transition
  - current behavior: may hide overlay/cancel pending, then return
  - result: `GuardActionResult.Consumed(reason = "whitelist_transition", hasSideEffect = <true only if cleanup/hide happened>)`
- normal app policy launch
  - current behavior: launch coroutine and return
  - result: `GuardActionResult.ScheduleFollowUp(reason = "normal_policy_check")`

## 9. Event Routing State

Introduce `EventRoutingState` or keep an equivalent private state object inside `AccessibilityEventRouter`.

Move into router in Phase 3:

- `currentPackageName`
- `lastHandledPackage`
- `lastHandledTime`

Keep in service for now unless the implementation also moves `publishEventSignalIfNeeded(...)`:

- `lastEventSignalTimestamp`

Reason: debounce controls whether downstream guards are called, so it belongs to the router. Assistant follow-up checks must reuse this same routing state because current `scheduleAssistantFollowUpChecks()` reads and writes `lastHandledPackage` / `lastHandledTime`; do not split main-route debounce state and assistant-follow-up debounce state into two owners. Event-signal heartbeat publishing is lifecycle/diagnostic behavior and can remain in the service until a later cleanup.

`EventRoutingState` should expose small methods instead of open mutation where practical:

```kotlin
data class EventRoutingState(
    var currentPackageName: String = "",
    var lastHandledPackage: String = "",
    var lastHandledTime: Long = 0L
)
```

Optional helper:

```kotlin
fun shouldDebounce(packageName: String, now: Long, debounceIntervalMs: Long): Boolean
```

Do not move `blockSessionController` state into `EventRoutingState`.

## 10. Task Plan

### Task 1: Add Router Skeleton And Routing State

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
- Optionally create: `app/src/main/java/com/kidsphoneguard/service/accessibility/EventRoutingState.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`

**Step 1: Create `EventRoutingState`**

Add the state object with:

- `currentPackageName`
- `lastHandledPackage`
- `lastHandledTime`

Do not include overlay state or block-session state.

**Step 2: Create `AccessibilityEventRouter` shell**

Add:

- constructor dependencies
- `route(event: AccessibilityEvent): GuardActionResult`
- private `routeWindowEvent(event)`
- private `routeInteractionEvent(event)`

At this step, the router can return `Continue` for all event types. Do not wire it into the service yet.

**Step 3: Add focused JVM tests for pure routing flow helpers**

If direct Android `AccessibilityEvent` construction is awkward, test pure route-step execution helpers instead:

- `Continue` calls the next step
- `Consumed` stops the pipeline
- `ScheduleFollowUp` stops the pipeline
- `Blocked` stops the pipeline

**Step 4: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.accessibility.AccessibilityEventRouterTest"
```

Expected: PASS

**Step 5: Compile**

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: PASS

### Task 2: Route Event-Type Dispatch Through Router

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`

**Step 1: Add temporary entry adapters**

In `GuardAccessibilityService`, construct the router with temporary callbacks:

- window event callback calls existing `handleWindowEvent(event)`
- interaction event callback calls existing `handlePotentialProtectedInteraction(event)`

This task only moves event-type dispatch into the router. It does not yet move the window/interaction branch order.

This is a short transition step only. Task 3 must remove the whole-event `handleWindowEvent(event)` passthrough and encode the window route order inside `AccessibilityEventRouter`.

**Step 2: Replace `onAccessibilityEvent` dispatch**

Keep heartbeat, event-signal publishing, and try/catch in service, but replace the internal `when (event.eventType)` with:

```kotlin
accessibilityEventRouter.route(event)
```

**Step 3: Compile and run tests**

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 3: Move Window Route Order Into Router

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Create explicit window route context**

Add an immutable context for window routing:

```kotlin
data class WindowRouteContext(
    val event: AccessibilityEvent,
    val eventPackageName: String,
    val source: String,
    val resolvedEventPackage: String,
    val protectedWindowPackage: String?,
    val packageName: String
)
```

**Step 2: Move package normalization into router**

Router should perform:

- event package read
- source marker creation
- `resolvePolicyPackage(...)`
- protected-window sweep package override
- assistant package follow-up decision

All business reads remain through adapters.

**Step 3: Convert each window branch to a router step**

In this exact order:

1. assistant follow-up
2. power-save exit
3. system panel collapse
4. sensitive action
5. protected settings policy
6. self-app handling
7. WeChat Finder
8. lock engine readiness
9. block hold
10. debounce
11. whitelist transition
12. normal policy launch

Each step returns `GuardActionResult`.

**Step 4: Move debounce state**

Move `lastHandledPackage`, `lastHandledTime`, and `currentPackageName` into `EventRoutingState`.

Do not move `lastEventSignalTimestamp` in this task.

If `scheduleAssistantFollowUpChecks()` remains in `GuardAccessibilityService`, it must access debounce state through router-owned state callbacks or an explicit adapter boundary. It must not keep reading or writing service-owned `lastHandledPackage` / `lastHandledTime` after those fields move.

**Step 5: Compile and run tests**

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 4: Move Interaction Route Order Into Router

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Create explicit interaction route context**

Add:

```kotlin
data class InteractionRouteContext(
    val event: AccessibilityEvent,
    val eventPackageName: String,
    val source: String,
    val resolvedEventPackage: String,
    val protectedWindowPackage: String?,
    val packageName: String
)
```

If it duplicates `WindowRouteContext`, use one shared `RouteContext`.

**Step 2: Move interaction package normalization into router**

Router should perform:

- empty package check
- source marker creation
- `resolvePolicyPackage(...)`
- protected-window sweep package override

**Step 3: Convert interaction branches to router steps**

In this exact order:

1. system panel collapse
2. sensitive action
3. protected settings policy

No self-app, WeChat Finder, or normal app policy handling should be added to interaction events in Phase 3.

**Step 4: Compile and run tests**

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 5: Remove Old Service Route Methods

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`

**Step 1: Delete or shrink old route methods**

Remove old route-chain bodies from:

- `handleWindowEvent(...)`
- `handlePotentialProtectedInteraction(...)`

Acceptable end states:

- methods are deleted entirely, or
- methods remain only as one-line adapter wrappers during a transition task

They must not keep the old branch order duplicated in the service after Phase 3 completes.

**Step 2: Keep business methods in service**

Do not delete existing business methods that adapters still call.

**Step 3: Check for duplicated route order**

Search:

```powershell
rg -n "handleWindowEvent|handlePotentialProtectedInteraction|sensitiveActionGuard\\.handle|exitPowerSaveModeIfNeeded|collapseSystemPanelIfNeeded|handleProtectedSettingsPolicyIfCandidate" app/src/main/java/com/kidsphoneguard/service
```

Expected:

- `sensitiveActionGuard.handle(...)` appears in the router, not directly in service event route methods.
- old route methods do not contain a full branch chain.

**Step 4: Compile and run tests**

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 6: Router Verification And Handoff

**Files:**
- Modify if needed: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`
- Modify if needed: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Run lint**

```powershell
.\gradlew.bat :app:lint
```

Expected: PASS or documented pre-existing warnings.

**Step 2: Runtime verification**

Validate the same scenarios that protected Phase 2:

- MIUI launcher uninstall/confirm behavior
- opening KidsPhoneGuard itself
- `com.xiaomi.market`

Also validate at least one normal app policy route:

- blocked app still shows overlay
- whitelisted app still skips block
- overlay release behavior still works after leaving blocked app

If available, validate:

- protected settings route
- system panel collapse route
- WeChat Finder route when enabled
- Huawei/Honor power-save route on matching device

**Step 3: Record route-order notes**

Add a short note to the implementation handoff or commit message listing:

- window route order
- interaction route order
- which steps are still service adapters
- which steps are real extracted guards
- any runtime scenario not verified on device

## 11. Completion Criteria

Phase 3 is complete only when:

- `AccessibilityEventRouter` exists and is the only place that classifies accessibility event types.
- Window-event branch order is encoded in `AccessibilityEventRouter`.
- Interaction-event branch order is encoded in `AccessibilityEventRouter`.
- `GuardAccessibilityService.onAccessibilityEvent()` is reduced to heartbeat/event-signal publishing, exception boundary, and router forwarding.
- `SensitiveActionGuard.handle(...)` is called by the router, not directly by service route methods.
- All router steps return or are adapted into `GuardActionResult`.
- Unsupported/unhandled event types return `GuardActionResult.Continue`.
- `lastHandledPackage`, `lastHandledTime`, and `currentPackageName` are no longer directly read or written by the service's main route chain; they are owned by `AccessibilityEventRouter` / `EventRoutingState` or accessed through an explicit router-state adapter.
- Assistant follow-up debounce behavior uses the same router-owned `lastHandledPackage` / `lastHandledTime` state as the main route.
- Existing business logic remains behaviorally unchanged.
- No new feature guard/coordinator files were created for protected surface, system panel, app block, WeChat Finder, or power-save handling.
- `compileDebugKotlin`, `testDebugUnitTest`, and `:app:lint` pass or any blockers are explicitly documented.
- Runtime validation records the scenarios tested and any unavailable device-specific scenarios.

## 12. Out Of Scope Follow-Ups

Handle these after Phase 3:

- Extract `ProtectedSurfaceGuard`
- Extract `SystemSurfaceGuard`
- Extract `AppBlockCoordinator`
- Extract Huawei/Honor power-save handling
- Extract `WeChatFinderGuard`
- Move `publishEventSignalIfNeeded(...)` or `lastEventSignalTimestamp` if a later cleanup wants lifecycle diagnostics inside the router
- Replace temporary service adapters with real guard/coordinator dependencies
