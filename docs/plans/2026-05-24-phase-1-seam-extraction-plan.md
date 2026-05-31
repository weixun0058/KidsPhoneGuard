# Phase 1 Seam Extraction Plan

> **Status note as of 2026-05-31:** this plan is historical context. Scheduler, navigation, snapshot, and block-session boundaries remain relevant; `NodeActionSession` and later sensitive-action extraction references are obsolete after uninstall-protection removal.

Source plan: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`

## 1. Goal

Phase 1 creates the seams needed for later `GuardAccessibilityService` decomposition without moving business rules yet.

This phase must preserve current behavior. It should introduce focused wrappers and state holders, then keep `GuardAccessibilityService` calling the same logic through those seams.

## 2. Scope

Create only these seam components:

- `GuardActionResult`
- `WindowInspectorSnapshotApi`
- `NodeActionSession`
- `NavigationExecutor`
- `GuardActionScheduler`
- `OverlayCoordinator`
- `BlockSessionController`
- `BlockSessionState`

Do not extract these modules in Phase 1:

- `SensitiveActionGuard`
- `ProtectedSurfaceGuard`
- `SystemSurfaceGuard`
- `AppBlockCoordinator`
- OEM-specific handlers

## 3. Non-Negotiable Constraints

- Do not change event order.
- Do not change cooldown values, delay arrays, or burst timings.
- Do not rename existing runtime log markers unless this plan explicitly says so.
- Do not create a second scheduler. `GuardActionScheduler` is the only scheduler seam.
- Do not create `DeferredActionScheduler.kt`; that name is retired and must not coexist with `GuardActionScheduler.kt`.
- Use `BlockSessionController` as the final name. Do not introduce `OverlayBlockController`.
- Keep feature modules away from `OverlayCoordinator`; feature code should depend on `BlockSessionController`.

## 4. Target Files

```text
app/src/main/java/com/kidsphoneguard/service/accessibility/
  GuardActionResult.kt
  WindowInspectorSnapshotApi.kt
  NodeActionSession.kt

app/src/main/java/com/kidsphoneguard/service/block/
  NavigationExecutor.kt
  GuardActionScheduler.kt
  OverlayCoordinator.kt
  BlockSessionController.kt
  BlockSessionState.kt

app/src/main/java/com/kidsphoneguard/service/
  GuardAccessibilityService.kt
```

## 5. Component Contracts

### 5.1 `GuardActionResult`

Purpose: make routing decisions explicit before moving routing out of `GuardAccessibilityService`.

Required semantics:

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

Rules:

- `Continue`: router must call the next step.
- `Consumed`: router must stop; `hasSideEffect` states whether anything was executed.
- `ScheduleFollowUp`: router must stop because delayed work has been scheduled.
- `Blocked`: router must stop because blocking/suppression was executed or requested through `BlockSessionController`.

### 5.2 `WindowInspectorSnapshotApi`

Purpose: provide read-only accessibility/window state for routing, matching, and tests.

It may expose immutable data such as:

- Active package name
- Recent top package name
- Interactive window package summaries
- Event signal snapshots
- Root/window text snapshots

Rules:

- Do not expose raw `AccessibilityNodeInfo`.
- Own all `AccessibilityNodeInfo` recycling internally.
- Keep snapshot objects small and purpose-specific.

### 5.3 `NodeActionSession`

Purpose: support short-lived live-node actions that snapshots cannot perform, such as clicking cancel buttons.

Rules:

- Raw `AccessibilityNodeInfo` may appear only inside bounded callback/session APIs.
- The session owns cleanup of nodes it obtains unless the method name explicitly transfers ownership.
- Handlers must not store nodes outside the session callback.
- Click-oriented flows should depend on `NodeActionSession`, not `WindowInspectorSnapshotApi`.

### 5.4 `NavigationExecutor`

Purpose: centralize navigation/global action execution and logging.

It wraps:

- `performGlobalAction(GLOBAL_ACTION_BACK)`
- `performGlobalAction(GLOBAL_ACTION_HOME)`
- `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`
- Gesture dispatch needed by OEM flows

Rules:

- Keep current action order and delay timing in callers during Phase 1.
- Preserve existing log markers where behavior is still owned by `GuardAccessibilityService`.
- Log exceptions consistently.

### 5.5 `GuardActionScheduler`

Purpose: replace scattered direct `handler.postDelayed` ownership with a single cancelable scheduler seam.

Required capabilities:

- `schedule(owner: String, key: String, delayMs: Long, action: () -> Unit)`
- `cancelOwner(owner: String)`
- `cancelKey(owner: String, key: String)`
- `cancelTargetPackage(packageName: String)`
- `cancelAll()`

Rules:

- `onDestroy()` must call `cancelAll()`.
- Existing delay values must be preserved.
- Existing `Runnable` lists should be migrated only after the scheduler exists.
- Do not create `DeferredActionScheduler`.

### 5.6 `OverlayCoordinator`

Purpose: own only technical calls to `OverlayService`.

Allowed responsibilities:

- Call `OverlayService.showOverlay(...)`
- Call `OverlayService.hideOverlay(...)`
- Wrap those calls with try/catch and technical logging

Forbidden responsibilities:

- Do not store or mutate `lastBlockedPackage`.
- Do not store or mutate `blockHoldUntil`.
- Do not store or mutate `pendingBlockPackage`.
- Do not store or mutate `lastOverlayPackage`.
- Do not store or mutate `lastOverlayShowTime`.
- Do not decide whether an overlay should be held, re-shown, or released.

### 5.7 `BlockSessionState`

Purpose: hold shared block/overlay session state in one place.

Fields:

- `lastBlockedPackage`
- `lastBlockTime`
- `blockHoldUntil`
- `pendingBlockPackage`
- `lastOverlayPackage`
- `lastOverlayShowTime`

Rules:

- This state is owned only by `BlockSessionController`.
- No feature module should mutate these fields directly.

### 5.8 `BlockSessionController`

Purpose: own block-session state, overlay lifecycle policy, and release-check orchestration.

Allowed responsibilities:

- Decide whether to show, hold, re-show, or release overlay.
- Record active blocked package and block timestamps.
- Set and clear pending block package.
- Schedule overlay release checks through `GuardActionScheduler`.
- Call `OverlayCoordinator` for actual overlay show/hide.

Forbidden responsibilities:

- Do not directly perform app policy evaluation.
- Do not inspect raw accessibility nodes.
- Do not duplicate normal app-blocking decision logic.
- Do not expose mutable `BlockSessionState` to feature modules.

## 6. Implementation Order

### Step 1: Add `GuardActionResult`

- Create `GuardActionResult.kt`.
- Add minimal unit tests for result semantics if test setup is available.
- Do not wire it into routing yet unless the change is mechanically trivial.

### Step 2: Add `NavigationExecutor`

- Wrap global action execution.
- Keep current call sites functionally unchanged.
- Preserve exception logging.

### Step 3: Add `GuardActionScheduler`

- Wrap the existing main-thread `Handler`.
- Add owner/key tracking.
- Wire `onDestroy()` to `cancelAll()`.
- Do not migrate every delayed call in this step unless each migration is behavior-preserving.

### Step 4: Add `WindowInspectorSnapshotApi`

- Add read-only snapshot helpers around `rootInActiveWindow`, `windows`, and recent-top-package lookup.
- Keep raw node ownership internal.
- Start with helper methods needed by current code; do not broaden the API speculatively.

### Step 5: Add `NodeActionSession`

- Add short-lived APIs for live-node click flows.
- Migrate no click logic yet unless ownership stays identical and obvious.
- Document recycle ownership in method names or KDoc.

### Step 6: Add `OverlayCoordinator`

- Move only direct `OverlayService.showOverlay(...)` and `OverlayService.hideOverlay(...)` technical calls behind this wrapper.
- Do not move overlay policy or block-session state.

### Step 7: Add `BlockSessionState` and `BlockSessionController`

- Move shared state ownership into `BlockSessionState`.
- Move policy-level overlay lifecycle decisions into `BlockSessionController` only when each call site can preserve timing and log behavior.
- Keep feature code calling existing service methods if moving a policy decision would change behavior.

### Step 8: Wire seams conservatively

- Replace direct calls with seam calls only where the wrapper is behavior-equivalent.
- Keep `GuardAccessibilityService` as the behavior owner for Phase 1.
- Record any call site deliberately left unmigrated.

## 7. Validation

Run after each meaningful step:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

Run before Phase 1 is considered complete:

```powershell
.\gradlew.bat :app:lint
```

Runtime validation, when a device is available:

```powershell
adb logcat -c
adb logcat -s GuardAccessibilityService:D GuardAccessibilityService:W
```

Required validation notes:

- Methods changed or wrapped
- State moved
- Constructor dependencies added
- Delayed actions migrated to scheduler
- Overlay call sites migrated to coordinator/controller
- Any runtime flow not validated on device

## 8. Completion Criteria

Phase 1 is complete only when:

- `DeferredActionScheduler.kt` does not exist.
- `OverlayBlockController.kt` does not exist.
- `BlockSessionController.kt` and `BlockSessionState.kt` are the only block-session owner files.
- `OverlayCoordinator` contains no block-session policy.
- `GuardActionScheduler` is the only delayed-action scheduler seam.
- Raw node access is split between snapshot reads and `NodeActionSession`.
- `GuardAccessibilityService` behavior remains unchanged except for delegating through seams.
- Compile, unit tests, and lint have been run or explicitly documented as blocked.

## 9. Next Phase Boundary

Do not start extracting `SensitiveActionGuard` until Phase 1 is complete.

Phase 2 may begin with sensitive-action extraction only after:

- Router result semantics are available.
- Scheduler cancellation works.
- Navigation execution is centralized.
- Live-node click ownership is handled by `NodeActionSession`.
- Overlay/block-session state is owned by `BlockSessionController`.
