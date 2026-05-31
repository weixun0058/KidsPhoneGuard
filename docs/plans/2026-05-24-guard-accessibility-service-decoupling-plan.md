# GuardAccessibilityService Decoupling Implementation Plan

**Goal:** Decouple `GuardAccessibilityService` into smaller, testable modules so that sensitive-action protection, protected-surface suppression, and normal app blocking can evolve independently without repeated cross-impact.

**Architecture:** Keep `GuardAccessibilityService` as a thin Android service entrypoint and move business logic into focused coordinators and guards. Separate event routing, blocking workflow, sensitive-action protection, protected system-surface protection, and OEM-specific handlers, while preserving current runtime behavior during the migration.

**Tech Stack:** Kotlin, Android AccessibilityService, Handler/Looper, coroutines, existing `LockDecisionEngine`, `ProtectedSettingsPolicy`, `OverlayService`

---

## 0. Review Amendments

This plan was reviewed against the current `GuardAccessibilityService.kt` implementation. The direction is correct, but the original migration order was too close to "move methods into new files" and did not first isolate the shared state and delayed action machinery that currently creates most cross-impact.

Required changes before implementation:

- Build cross-cutting boundaries first: event result protocol, action scheduler, navigation executor, overlay/block session controller, and window/node access facade.
- Do not let `SensitiveActionGuard`, `ProtectedSurfaceGuard`, or `AppBlockCoordinator` directly own shared overlay state such as `lastBlockedPackage`, `lastBlockTime`, `blockHoldUntil`, `pendingBlockPackage`, `lastOverlayPackage`, and `lastOverlayShowTime`.
- Do not pass raw `AccessibilityNodeInfo` freely across modules. Prefer immutable snapshots; if a module obtains a node, its ownership and recycle responsibility must be explicit.
- Do not extract `SensitiveActionGuard` as the first code move. First add the platform and scheduling seams, then extract business logic through those seams.
- After every step, verify behavior with build checks, unit tests where possible, and log-order comparison against the pre-extraction baseline.

## 1. Background

`app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt` currently mixes too many responsibilities inside a single class:

- Accessibility service lifecycle and health heartbeat
- Accessibility event routing
- Normal app blocking and overlay coordination
- Sensitive-action protection such as uninstall, force-stop, and clear-data interruption
- Protected settings / installer / market page recognition and suppression
- System panel collapse logic
- Huawei power-save exit compatibility
- WeChat Finder special-case blocking
- Delayed action scheduling and overlay release coordination

This makes the class a de facto God Object. A local change for one problem, such as Xiaomi launcher uninstall interception, can easily influence overlay timing, protected-surface suppression, or normal app blocking behavior.

## 2. Current Responsibility Map

### 2.1 Service and runtime support

**Current functions**

- `onCreate()`
- `onServiceConnected()`
- `onDestroy()`
- `onUnbind()`
- `onRebind()`
- `onInterrupt()`
- `initializeService()`
- `publishEventSignalIfNeeded()`
- `publishLifecycleSignal()`
- `logAccessibilitySettingsSnapshot()`
- `heartbeatRunnable`
- `protectedWindowSweepRunnable`
- `blockAppReceiver`

**Current responsibility**

- Manage Android service lifecycle
- Initialize dependencies
- Publish runtime health and lifecycle signals
- Register the internal block broadcast

### 2.2 Event routing

**Current functions**

- `onAccessibilityEvent()`
- `handleWindowEvent()`
- `handlePotentialProtectedInteraction()`
- `resolvePolicyPackage()`
- `scheduleAssistantFollowUpChecks()`

**Current responsibility**

- Split window events vs interaction events
- Map OEM assistant overlays back to the real foreground package
- Decide which business chain should handle the current event

### 2.3 Normal app blocking workflow

**Current functions**

- `checkPolicyAndExecute()`
- `enforceBlock()`
- `tryFallbackNavigation()`
- `scheduleDeferredBlockAction()`
- `canExecuteDeferredBlockAction()`
- `cancelPendingBlockActions()`
- `scheduleOverlayReleaseCheck()`
- `scheduleProtectedOverlayReleaseCheck()`
- `hideOverlay()`
- `tryForceStopApp()`
- `isTargetPackageActive()`
- `isPackageVisibleInInteractiveWindows()`
- `getRecentTopPackageName()`

**Current responsibility**

- Ask `LockDecisionEngine` for the block decision
- Show or hide overlay
- Execute `BACK` / `HOME`
- Try to finish tasks and recover from failed exits
- Manage delayed block and overlay release actions

### 2.4 Sensitive-action protection

**Current functions**

- `shouldBlockSensitiveAction()`
- `shouldBlockLauncherSensitiveAction()`
- `eventSourceLooksLikeLauncherIcon()`
- `isLauncherShortcutMenuEvent()`
- `isLauncherUninstallConfirmEvent()`
- `isDialogLikeEvent()`
- `isGlobalUnlockEnabledForSensitiveAction()`
- `eventSourceSelfContainsKeyword()`
- `containsSensitiveActionNodeText()`
- `buildEventSignal()`
- `blockSensitiveAction()`
- `runSensitiveActionFastBackBurst()`
- `runSensitiveActionEscapeBurst()`
- `cancelSensitiveEscapeActions()`
- `performSensitiveEscapeAction()`
- `tryClickSensitiveCancel()`
- `findClickableAncestor()`

**Current responsibility**

- Detect uninstall / force-stop / clear-data related operations
- Handle Xiaomi launcher long-press and uninstall confirmation
- Search visible nodes for destructive keywords
- Try to cancel destructive dialogs and navigate away

### 2.5 Protected surface protection

**Current functions**

- `handleProtectedSettingsPolicyIfCandidate()`
- `shouldSweepProtectedWindows()`
- `buildSettingsPageSnapshot()`
- `isExplicitUserActionEvent()`
- `collectNodeSignals()`
- `collectCandidateWindowNodeSignals()`
- `isSameBasePackage()`
- `collectInteractiveWindowPackages()`
- `logProtectedSettingsDecision()`
- `releaseProtectedSettingsOverlayIfAllowed()`
- `sweepProtectedInteractiveWindows()`
- `suppressProtectedSystemSurface()`
- `isProtectedSystemSurface()`
- `isProtectedSurfaceSuppressionAllowed()`
- `performProtectedSurfaceNavigation()`
- `findProtectedInteractiveWindowPackage()`
- `forEachInteractiveWindow()`
- `logProtectedWindowSnapshot()`

**Current responsibility**

- Recognize settings, installer, market, and other protected system surfaces
- Build snapshots for `ProtectedSettingsPolicy`
- Suppress risky pages through overlay and navigation bursts

### 2.6 System surface and OEM-specific compatibility

**Current functions**

- `collapseSystemPanelIfNeeded()`
- `collapseVisibleSystemPanelIfNeeded()`
- `collapseSystemPanelWithSignal()`
- `performSystemPanelCollapseAction()`
- `isSystemPanelPackage()`
- `shouldInspectSystemPanel()`
- `buildSystemPanelSignal()`
- `buildVisibleSystemPanelSignal()`
- `collectSystemPanelWindowNodeSignals()`
- `appendSignal()`
- `exitPowerSaveModeIfNeeded()`
- `exitVisiblePowerSaveModeIfNeeded()`
- `triggerPowerSaveExit()`
- `clickPowerSaveExitNode()`
- `clickPowerSaveExitNodeInTree()`
- `schedulePowerSaveExitBurst()`
- `tapPowerSaveExitArea()`
- `collectPowerSaveExitSignal()`
- `containsPowerSaveExitSignal()`
- `isPowerSaveLauncherPackage()`
- `shouldBlockWeChatFinder()`
- `blockWeChatFinder()`

**Current responsibility**

- Suppress notification shade and OEM control centers
- Exit Huawei and Honor power-save launchers
- Apply WeChat Finder-specific guard behavior

## 3. Why The Current Design Is Strongly Coupled

The file is strongly coupled for four reasons:

1. **One service class acts as both router and executor**
   - `handleWindowEvent()` and `handlePotentialProtectedInteraction()` both route events and immediately execute domain behavior.
   - Ordering changes in one branch can alter unrelated behavior elsewhere.

2. **Multiple domains share mutable state**
   - Fields such as `lastBlockedPackage`, `pendingBlockPackage`, `blockHoldUntil`, `lastOverlayPackage`, `lastSensitiveActionBlockTime`, and `miuiLauncherIconMenuBlockUntil` are updated by different feature chains.
   - This makes timing bugs and accidental interactions much more likely.

3. **Action execution is duplicated across domains**
   - `performGlobalAction`, overlay show/hide, delayed retries, and window scanning are reassembled in several feature-specific paths.
   - Similar effects are implemented with slightly different local timing assumptions.

4. **Business rules and platform details are mixed**
   - Generic policy checks, Xiaomi launcher behavior, Huawei power-save behavior, and WeChat Finder special cases all live in the same class.
   - This makes the service harder to test and harder to reason about.

## 4. Recommended Target Architecture

```text
GuardAccessibilityService
|-- AccessibilityEventRouter
|   |-- routeWindowEvent()
|   `-- routeInteractionEvent()
|-- GuardRuntimeFacade
|   |-- WindowInspectorSnapshotApi
|   |-- NodeActionSession
|   |-- NavigationExecutor
|   |-- GuardActionScheduler
|   `-- BlockSessionController
|-- AppBlockCoordinator
|   `-- PolicyEvaluatorAdapter
|-- SensitiveActionGuard
|   |-- LauncherSensitiveHandler
|   |-- SettingsSensitiveHandler
|   `-- ConfirmDialogHandler
|-- ProtectedSurfaceGuard
|   |-- ProtectedSettingsSnapshotBuilder
|   |-- ProtectedWindowScanner
|   |-- ProtectedSurfaceSuppressor
|   `-- ProtectedSettingsDecisionLogger
|-- SystemSurfaceGuard
|   `-- SystemPanelHandler
|-- OemGuardHandlers
|   |-- HuaweiPowerSaveHandler
|   |-- WeChatFinderGuard
|   `-- MiuiLauncherSensitiveHandler
`-- ServiceRuntimeSupport
    |-- HealthHeartbeat
    |-- LifecycleSignalPublisher
    `-- AccessibilitySettingsLogger
```

## 5. Recommended Split Boundaries

### 5.1 Thin service entrypoint

Keep `GuardAccessibilityService` responsible only for:

- Android lifecycle
- Dependency creation and injection
- Accessibility event subscription
- Forwarding events into the router

It should stop containing feature-specific blocking logic.

### 5.2 Event router

Create `AccessibilityEventRouter` to:

- Normalize event package names
- Distinguish window events from interaction events
- Decide which guard or coordinator receives the event

It should not perform overlay, navigation, or keyword search itself.

### 5.3 Normal block workflow

Create `AppBlockCoordinator` to own:

- Calls into `LockDecisionEngine`
- Overlay show/hide timing
- Fallback navigation
- Deferred force-stop and delayed release actions

This module should represent the standard app-blocking pipeline only.

### 5.4 Sensitive action protection

Create `SensitiveActionGuard` to own:

- Launcher uninstall detection
- Settings uninstall / force-stop / clear-data detection
- Confirmation dialog cancellation
- Sensitive-action navigation escape timing

This is the most important early split because it is the current troubleshooting hotspot.

### 5.5 Protected surface protection

Create `ProtectedSurfaceGuard` to own:

- Protected settings snapshot building
- Window scanning for protected packages
- Policy evaluation against `ProtectedSettingsPolicy`
- Suppression of protected pages and installers

This keeps protected-page logic independent from generic app-blocking logic.

### 5.6 OEM compatibility handlers

Create OEM-specific handlers for:

- `HuaweiPowerSaveHandler`
- `WeChatFinderGuard`
- `MiuiLauncherSensitiveHandler`

These handlers should expose small, focused interfaces and should not mutate shared global state directly.

### 5.7 Required cross-cutting boundaries

Before moving feature logic into separate files, introduce these narrow boundaries. They are more important than the final directory names because they prevent the current coupling from being recreated in smaller classes.

#### 5.7.1 Router result protocol

Every guard/coordinator called by `AccessibilityEventRouter` should return a typed result instead of directly controlling router flow through ad hoc booleans:

```kotlin
sealed interface GuardActionResult {
    val continueRouting: Boolean
    val hasSideEffect: Boolean

    data object Continue : GuardActionResult {
        override val continueRouting = true
        override val hasSideEffect = false
    }

    data class Consumed(
        val reason: String,
        override val hasSideEffect: Boolean
    ) : GuardActionResult {
        override val continueRouting = false
    }

    data class ScheduleFollowUp(
        val reason: String
    ) : GuardActionResult {
        override val continueRouting = false
        override val hasSideEffect = true
    }

    data class Blocked(
        val packageName: String,
        val reason: String
    ) : GuardActionResult {
        override val continueRouting = false
        override val hasSideEffect = true
    }
}
```

The router owns the event order and early-return behavior. Individual guards should not call unrelated guards or reimplement the routing chain.

Result semantics:

- `Continue`: no meaningful side effect occurred and the router must call the next guard.
- `Consumed`: the current event was intentionally handled and the router must stop. It may or may not have a side effect; the result must state that explicitly through `hasSideEffect`.
- `ScheduleFollowUp`: delayed work was scheduled and the router must stop for the current event. If a future implementation truly needs "schedule and continue", it should return `Continue` only after proving that the scheduled work cannot conflict with downstream guards.
- `Blocked`: a blocking or suppression action has already been executed or requested. The router must stop. If overlay/block-session state is involved, it must already have gone through `BlockSessionController`.

#### 5.7.2 Overlay and block session control

Create one shared controller for overlay/block-session state before splitting business modules. Normal app blocking and protected-surface suppression both mutate the same conceptual state today, so these fields must not be copied into separate module-local states:

- `lastBlockedPackage`
- `lastBlockTime`
- `blockHoldUntil`
- `pendingBlockPackage`
- `lastOverlayPackage`
- `lastOverlayShowTime`

Use `BlockSessionController` for:

- Showing and hiding `OverlayService`
- Holding and releasing protected overlays
- Recording the active blocked package
- Scheduling overlay release checks
- Publishing block-related lifecycle signals

`AppBlockCoordinator`, `ProtectedSurfaceGuard`, `SensitiveActionGuard`, and special-case handlers should request block/navigation actions through this controller instead of writing the state directly.

Hard boundary between overlay classes:

- `OverlayCoordinator` owns only technical calls to `OverlayService`, such as `showOverlay(...)`, `hideOverlay(...)`, and exception/log wrapping around those calls.
- `BlockSessionController` owns block-session state, overlay lifecycle policy, release-check orchestration, and decisions such as whether to hold, re-show, or release an overlay.
- `OverlayCoordinator` must not store or mutate `lastBlockedPackage`, `blockHoldUntil`, `pendingBlockPackage`, `lastOverlayPackage`, or `lastOverlayShowTime`.
- `BlockSessionController` may call `OverlayCoordinator`, but `OverlayCoordinator` must not call back into `BlockSessionController`.

#### 5.7.3 Delayed action scheduling

Create `GuardActionScheduler` early. It should support:

- Scheduling delayed actions with an owner and key, for example `owner = "protected_surface"` and `key = packageName`
- Canceling all actions for an owner
- Canceling all actions for a target package
- Canceling everything in `onDestroy()`

This replaces scattered `handler.postDelayed` calls and prevents stale delayed actions from one feature chain from firing after another chain has taken over.

#### 5.7.4 Window snapshot and node action access

Create two access layers instead of one generic window helper:

`WindowInspectorSnapshotApi` centralizes read-only inspection:

- Reading `rootInActiveWindow`
- Iterating `windows`
- Building immutable snapshots of package names, class names, text, content descriptions, and window summaries

Use this API for routing, policy evaluation, keyword matching, and tests. It should not expose raw `AccessibilityNodeInfo`.

`NodeActionSession` is a short-lived action scope for cases that must operate on live nodes, such as clicking cancel buttons or OEM exit controls:

- It may expose raw `AccessibilityNodeInfo` only inside a bounded callback/session.
- The session owns cleanup of nodes it obtains unless the method name explicitly transfers ownership.
- Handlers must not store nodes beyond the callback/session.
- Any click-oriented handler should receive a `NodeActionSession`, not the general snapshot API.

This avoids two bad extremes: snapshots that cannot perform required clicks, and unrestricted node passing that recreates ownership bugs.

#### 5.7.5 Navigation executor

Create `NavigationExecutor` as the single wrapper around:

- `performGlobalAction(GLOBAL_ACTION_BACK)`
- `performGlobalAction(GLOBAL_ACTION_HOME)`
- `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`
- Gesture dispatch used by OEM compatibility flows

This keeps navigation logging, exception handling, and retry semantics consistent across feature modules.

## 6. Function-to-Module Mapping

### 6.1 `GuardAccessibilityService`

Keep:

- `onCreate()`
- `onServiceConnected()`
- `onDestroy()`
- `onUnbind()`
- `onRebind()`
- `onInterrupt()`
- `initializeService()`
- `onAccessibilityEvent()`

### 6.2 `AccessibilityEventRouter`

Move:

- `handleWindowEvent()`
- `handlePotentialProtectedInteraction()`
- `resolvePolicyPackage()`
- `scheduleAssistantFollowUpChecks()`

### 6.3 `AppBlockCoordinator`

Move:

- `checkPolicyAndExecute()`
- `enforceBlock()`
- `tryFallbackNavigation()`
- `scheduleDeferredBlockAction()`
- `canExecuteDeferredBlockAction()`
- `cancelPendingBlockActions()`
- `scheduleOverlayReleaseCheck()`
- `scheduleProtectedOverlayReleaseCheck()`
- `hideOverlay()`
- `tryForceStopApp()`
- `isTargetPackageActive()`
- `isPackageVisibleInInteractiveWindows()`
- `getRecentTopPackageName()`

### 6.4 `SensitiveActionGuard`

Move:

- `shouldBlockSensitiveAction()`
- `shouldBlockLauncherSensitiveAction()`
- `eventSourceLooksLikeLauncherIcon()`
- `isLauncherShortcutMenuEvent()`
- `isLauncherUninstallConfirmEvent()`
- `isDialogLikeEvent()`
- `isGlobalUnlockEnabledForSensitiveAction()`
- `eventSourceSelfContainsKeyword()`
- `containsSensitiveActionNodeText()`
- `buildEventSignal()`
- `blockSensitiveAction()`
- `runSensitiveActionFastBackBurst()`
- `runSensitiveActionEscapeBurst()`
- `cancelSensitiveEscapeActions()`
- `performSensitiveEscapeAction()`
- `tryClickSensitiveCancel()`
- `findClickableAncestor()`

### 6.5 `ProtectedSurfaceGuard`

Move:

- `handleProtectedSettingsPolicyIfCandidate()`
- `shouldSweepProtectedWindows()`
- `buildSettingsPageSnapshot()`
- `isExplicitUserActionEvent()`
- `collectNodeSignals()`
- `collectCandidateWindowNodeSignals()`
- `isSameBasePackage()`
- `collectInteractiveWindowPackages()`
- `logProtectedSettingsDecision()`
- `releaseProtectedSettingsOverlayIfAllowed()`
- `sweepProtectedInteractiveWindows()`
- `suppressProtectedSystemSurface()`
- `isProtectedSystemSurface()`
- `isProtectedSurfaceSuppressionAllowed()`
- `performProtectedSurfaceNavigation()`
- `findProtectedInteractiveWindowPackage()`
- `forEachInteractiveWindow()`
- `logProtectedWindowSnapshot()`

### 6.6 `SystemSurfaceGuard`

Move:

- `collapseSystemPanelIfNeeded()`
- `collapseVisibleSystemPanelIfNeeded()`
- `collapseSystemPanelWithSignal()`
- `performSystemPanelCollapseAction()`
- `isSystemPanelPackage()`
- `shouldInspectSystemPanel()`
- `buildSystemPanelSignal()`
- `buildVisibleSystemPanelSignal()`
- `collectSystemPanelWindowNodeSignals()`
- `appendSignal()`

### 6.7 `HuaweiPowerSaveHandler`

Move:

- `exitPowerSaveModeIfNeeded()`
- `exitVisiblePowerSaveModeIfNeeded()`
- `triggerPowerSaveExit()`
- `clickPowerSaveExitNode()`
- `clickPowerSaveExitNodeInTree()`
- `schedulePowerSaveExitBurst()`
- `tapPowerSaveExitArea()`
- `collectPowerSaveExitSignal()`
- `containsPowerSaveExitSignal()`
- `isPowerSaveLauncherPackage()`

### 6.8 `WeChatFinderGuard`

Move:

- `shouldBlockWeChatFinder()`
- `blockWeChatFinder()`

### 6.9 `ServiceRuntimeSupport`

Move or wrap:

- `publishEventSignalIfNeeded()`
- `publishLifecycleSignal()`
- `logAccessibilitySettingsSnapshot()`
- `heartbeatRunnable`
- `protectedWindowSweepRunnable`
- `blockAppReceiver`

## 7. State Migration Plan

### 7.1 Shared overlay/block session state

Create `BlockSessionState` owned only by `BlockSessionController`:

- `lastBlockedPackage`
- `lastBlockTime`
- `blockHoldUntil`
- `pendingBlockPackage`
- `lastOverlayPackage`
- `lastOverlayShowTime`

These fields are currently touched by both normal app blocking and protected-surface suppression. They must not be placed inside `AppBlockState` because that would force `ProtectedSurfaceGuard` to either duplicate state or reach back into app-block internals.

### 7.2 Router/debounce state

Create `EventRoutingState` owned by `AccessibilityEventRouter`:

- `currentPackageName`
- `lastHandledPackage`
- `lastHandledTime`
- `lastEventSignalTimestamp`

The router should own debounce decisions because debounce controls whether downstream guards are called at all.

### 7.3 Normal app blocking state

Create `AppBlockState` only for fields that are truly specific to standard app blocking:

- `forceStopPermissionDenied`

If later changes add app-block-only cooldowns or policy-result caches, put them here. Do not put overlay ownership or protected-surface state here.

### 7.4 Sensitive-action state

Create `SensitiveActionState` for:

- `lastSensitiveActionBlockTime`
- `miuiLauncherIconMenuBlockUntil`

Do not keep raw `Runnable` lists such as `sensitiveEscapeActions` in this state after `GuardActionScheduler` exists. Sensitive-action escape bursts should be scheduled under an owner key and canceled through the scheduler.

### 7.5 Protected-surface state

Create `ProtectedSurfaceState` for:

- `lastProtectedWindowLogTime`
- `lastProtectedWindowSignature`
- `lastProtectedSettingsDecisionLogTime`
- `lastProtectedSettingsDecisionSignature`
- `lastProtectedWindowSweepPackage`
- `lastProtectedWindowSweepTime`
- `lastProtectedSurfaceSuppressPackage`
- `lastProtectedSurfaceSuppressTime`

This state controls protected-surface logging and suppression cooldown only. It should not own overlay visibility or the active blocked package.

### 7.6 Runtime support state

Keep service lifecycle flags close to `GuardAccessibilityService` or `ServiceRuntimeSupport`:

- `isRunning`
- `latestLifecycleSignal`

These are externally observable service-health values and should not be mixed with feature-domain state.

The main service should not keep feature-state fields directly after their owning component exists.

## 8. Target Directory Layout

```text
app/src/main/java/com/kidsphoneguard/service/
  GuardAccessibilityService.kt

app/src/main/java/com/kidsphoneguard/service/accessibility/
  AccessibilityEventRouter.kt
  ServiceRuntimeSupport.kt
  GuardActionResult.kt
  WindowInspectorSnapshotApi.kt
  NodeActionSession.kt

app/src/main/java/com/kidsphoneguard/service/block/
  AppBlockCoordinator.kt
  OverlayCoordinator.kt
  BlockSessionController.kt
  BlockSessionState.kt
  DeferredActionScheduler.kt
  GuardActionScheduler.kt
  NavigationExecutor.kt

app/src/main/java/com/kidsphoneguard/service/guard/
  SensitiveActionGuard.kt
  SensitiveActionState.kt
  ProtectedSurfaceGuard.kt
  ProtectedSurfaceState.kt
  SystemSurfaceGuard.kt
  WeChatFinderGuard.kt

app/src/main/java/com/kidsphoneguard/service/guard/oem/
  HuaweiPowerSaveHandler.kt
  MiuiLauncherSensitiveHandler.kt
```

## 9. Migration Strategy

This migration should be phased. Do not rewrite the class in one pass.

### Task 1: Freeze behavior and capture a baseline

**Files:**
- Modify: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`
- Read only: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Treat the current service as baseline behavior**

- Do not change behavior during the first extraction step.
- Preserve the current ordering of:
  - power-save exit
  - system panel collapse
  - sensitive action blocking
  - protected settings handling
  - self-app special handling
  - WeChat Finder handling
  - normal policy check

**Step 2: Record the baseline evidence**

- Record the current handler order in this plan before moving code.
- Record the key log names that prove each route still fires.
- If device validation is available, capture logcat for the main flows before extraction.
- If device validation is not available, state that explicitly in the implementation note for the step.

### Task 2: Introduce platform seams without moving business logic

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Create as needed:
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/GuardActionResult.kt`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApi.kt`
  - `app/src/main/java/com/kidsphoneguard/service/accessibility/NodeActionSession.kt`
  - `app/src/main/java/com/kidsphoneguard/service/block/NavigationExecutor.kt`
  - `app/src/main/java/com/kidsphoneguard/service/block/GuardActionScheduler.kt`
  - `app/src/main/java/com/kidsphoneguard/service/block/OverlayCoordinator.kt`
  - `app/src/main/java/com/kidsphoneguard/service/block/BlockSessionController.kt`

**Step 1: Add wrapper interfaces/classes**

- Add wrappers for immutable window/root snapshots, short-lived node actions, global navigation actions, delayed scheduling, technical overlay calls, and overlay/block-session state.
- Keep method bodies delegated back to the existing service behavior at first.
- Do not change event order, cooldown values, burst timing, log names, or overlay release timing.
- Enforce the `OverlayCoordinator` / `BlockSessionController` boundary in constructor dependencies: feature modules may depend on `BlockSessionController`; only `BlockSessionController` should depend on `OverlayCoordinator`.

**Step 2: Move scheduling registration into `GuardActionScheduler`**

- Replace direct feature-owned runnable lists with scheduler owner keys only after the wrapper exists.
- Ensure `onDestroy()` cancels all scheduler-owned delayed actions.

### Task 3: Make routing explicit

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Create or modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`

**Step 1: Introduce `GuardActionResult` in the router**

- Convert `handleWindowEvent()` and `handlePotentialProtectedInteraction()` into a pipeline that preserves the current order.
- Each step returns `Continue`, `Consumed`, `ScheduleFollowUp`, or `Blocked`.
- The router, not the individual guards, owns early-return behavior.

**Step 2: Keep the business methods in the service temporarily**

- At this stage, the router may still call private service methods through temporary adapters.
- The goal is to make flow control explicit before moving logic.

### Task 4: Extract `SensitiveActionGuard`

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionState.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Move detection logic first**

- Move keyword matching, launcher-source checks, target-app checks, and confirm-dialog detection.
- Prefer immutable event/window snapshots where possible.
- Preserve existing log names.

**Step 2: Move action logic second**

- Move cancel-click and escape-burst behavior only after detection output is stable.
- Use `NavigationExecutor` and `GuardActionScheduler`; do not keep a module-local list of delayed `Runnable`s.

**Step 3: Move only sensitive-action state**

- Move `lastSensitiveActionBlockTime` and `miuiLauncherIconMenuBlockUntil`.
- Do not move shared overlay/block session fields.

### Task 5: Extract `ProtectedSurfaceGuard` and `SystemSurfaceGuard`

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuard.kt`
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceState.kt`
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SystemSurfaceGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Move snapshot and policy code**

- Move snapshot building and decision logging first.
- Use `WindowInspectorSnapshotApi` for root/window reads.
- Keep `AccessibilityNodeInfo` ownership local to the component that obtains it.

**Step 2: Move suppression code second**

- Move overlay suppression and navigation burst code after snapshot logic is stable.
- Suppression must call `BlockSessionController` and `NavigationExecutor` instead of writing shared block fields directly.
- Navigation bursts must use `GuardActionScheduler`.

**Step 3: Split system panel handling**

- Keep system panel collapse separate from settings-page suppression.
- System panel collapse should use `NavigationExecutor` for dismiss shade/back behavior.

### Task 6: Extract `AppBlockCoordinator`

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Move normal block decision execution**

- Move `checkPolicyAndExecute()` and `enforceBlock()`.
- Use `BlockSessionController` for overlay/session state.

**Step 2: Move deferred scheduling and overlay release**

- Move delayed force-stop, fallback navigation, and overlay release methods.
- Do not introduce a second scheduler; use `GuardActionScheduler`.

**Step 3: Move foreground visibility helpers**

- Move `isTargetPackageActive()`, `isPackageVisibleInInteractiveWindows()`, and `getRecentTopPackageName()`.
- Prefer `WindowInspectorSnapshotApi` for window visibility checks.

### Task 7: Extract OEM and special-case handlers

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/WeChatFinderGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Extract Huawei power-save logic**

- Move gesture-based and node-search-based exit behavior into its own handler.

**Step 2: Extract WeChat Finder guard**

- Keep this as a narrow special-case module.
- It should return `GuardActionResult` and use the shared navigation/overlay seams.

### Task 8: Reduce `GuardAccessibilityService` to composition only

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Instantiate modules**

- Construct router, coordinators, and guards in `initializeService()`.

**Step 2: Keep service methods thin**

- `onAccessibilityEvent()` should forward the event.
- Lifecycle methods should call runtime support helpers.

**Step 3: Remove leftover business logic from the service**

- The service should stop owning business-state fields directly whenever a state holder already exists.
- The service should still own Android lifecycle callbacks and externally visible service-health static fields.

## 10. Risks and Controls

### Risk 1: Behavior changes because event order changes

**Control**

- Preserve the existing handler order during each extraction.
- Do not reorder chains while extracting.
- Encode handler order in `AccessibilityEventRouter` and make each step return `GuardActionResult`.
- Add log-order comparison to every extraction checkpoint.

### Risk 2: Shared mutable state gets partially copied

**Control**

- Move state together with the behavior that owns it.
- Do not leave the same state represented in both service and module.
- Treat overlay/block-session state as shared cross-cutting state, not app-block-owned state.
- Route all overlay and active-block updates through `BlockSessionController`.

### Risk 3: Accessibility dependencies become awkward to access

**Control**

- Introduce small interfaces for:
  - root window access
  - interactive window iteration
  - global action execution
  - overlay show / hide
- Prefer immutable snapshots over raw `AccessibilityNodeInfo` references.
- Use `NodeActionSession` only for short-lived click/action flows that cannot be implemented from snapshots.
- If raw nodes are passed inside a session, document whether the caller or callee must recycle them.

### Risk 4: OEM-specific code leaks back into generic modules

**Control**

- Keep Huawei and Xiaomi logic in explicit handlers or sub-handlers.

### Risk 5: Stale delayed actions fire after ownership changes

**Control**

- Replace scattered `handler.postDelayed` calls with `GuardActionScheduler`.
- Schedule actions with owner/key metadata.
- Cancel owner-scoped work when a newer route consumes the event.
- Cancel all scheduler work in `onDestroy()`.

### Risk 6: The migration becomes file movement without real decoupling

**Control**

- Do not create a new class that directly depends on `Context`, `Handler`, `rootInActiveWindow`, `OverlayService`, `performGlobalAction`, and `SettingsManager` all at once.
- Move behavior only after the relevant facade exists.
- Keep module constructors small and reviewable.

## 11. Validation Strategy

After each extraction step, verify at least these flows:

- Normal blocked app still shows overlay and exits correctly
- Whitelisted app does not keep the wrong overlay
- Self app opens without being treated as blocked
- Protected settings / installer pages are still suppressed
- Xiaomi launcher uninstall chain still triggers the same logs as before the extraction
- WeChat Finder special-case still blocks when enabled
- Huawei power-save exit flow still triggers when applicable
- Delayed actions from a previous route are canceled when a newer route consumes the event
- Overlay release checks still release only after the target package is no longer active, except for protected-overlay force-release behavior

Recommended commands:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Recommended runtime validation:

```powershell
adb logcat -c
adb logcat -s GuardAccessibilityService:D GuardAccessibilityService:W
```

For each phase, record:

- Methods moved
- State moved
- New constructor dependencies
- Log names that should remain unchanged
- Any runtime flow that could not be validated on a device

Add JVM tests where logic can be separated from Android framework objects:

- `GuardActionResult` routing order
- Whitelist/self-app route behavior
- Sensitive-action keyword and target matching
- Protected-surface snapshot evaluation inputs
- Scheduler cancellation by owner/key

## 12. Recommended First Execution Scope

Do not start with the whole migration. The best first execution scope is:

1. Keep behavior unchanged
2. Add `GuardActionResult`
3. Add `GuardActionScheduler`
4. Add `NavigationExecutor`
5. Add `WindowInspectorSnapshotApi`
6. Add `NodeActionSession`
7. Add `OverlayCoordinator`
8. Add `BlockSessionController` / `BlockSessionState`
9. Keep `GuardAccessibilityService` calling the same existing methods through these seams

Only after this first scope compiles and the baseline logs still match should the implementation extract `SensitiveActionGuard`.
