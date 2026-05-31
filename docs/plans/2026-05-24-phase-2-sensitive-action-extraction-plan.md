# Phase 2 Sensitive Action Extraction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract the sensitive-action detection and interruption workflow out of `GuardAccessibilityService` into a focused `SensitiveActionGuard` without changing current runtime behavior.

**Architecture:** Keep `GuardAccessibilityService` as the Android entrypoint and event router, but move sensitive-action classification, launcher-specific heuristics, destructive dialog interruption, and related timing state into a dedicated guard that depends on Phase 1 seams. The new guard should return `GuardActionResult` so the service keeps explicit routing semantics instead of splitting detection and execution into loosely-coupled boolean/unit calls. The existing temporary Phase 1 self-false-positive bypass is present in the real code and must be migrated unchanged as an explicit legacy/testing hook owned by the new guard.

**Tech Stack:** Kotlin, Android AccessibilityService, existing `GuardActionResult`, `GuardActionScheduler`, `NavigationExecutor`, `NodeActionSession`, `WindowInspectorSnapshotApi`. `BlockSessionController` remains the owner of overlay/block-session state and should not be injected into `SensitiveActionGuard` during this phase.

---

## 1. Scope

Phase 2 extracts only the sensitive-action subsystem.

Create or modify only these areas:

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Optionally create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionState.kt`
- Optionally create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionDecision.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Modify if needed: `app/src/main/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApi.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/guard/SensitiveActionGuardTest.kt`
- Test if needed: `app/src/test/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApiTest.kt`

Do not extract these in Phase 2:

- `ProtectedSurfaceGuard`
- `SystemSurfaceGuard`
- `AppBlockCoordinator`
- Huawei power-save handlers
- WeChat Finder special-case logic

## 2. Non-Negotiable Constraints

- Do not change event routing order in `GuardAccessibilityService`.
- Do not change delay arrays or cooldown values during the extraction.
- Do not remove existing runtime log markers unless the plan explicitly calls for ownership transfer.
- Keep the existing temporary Phase 1 self-false-positive bypass behavior during extraction so testing remains unblocked. The real code currently has `TEMP_PHASE1_BYPASS_SELF_SENSITIVE_FALSE_POSITIVES`, `shouldBypassLauncherSelfFalsePositiveForPhase1Test()`, and `shouldBypassSettingsSelfFalsePositiveForPhase1Test()`. Phase 2 must migrate this bypass, must keep it enabled by default, and must not delete or invert it.
- Do not let `SensitiveActionGuard` own overlay/block session state already moved into `BlockSessionController`.
- Do not bypass `NavigationExecutor`, `GuardActionScheduler`, `NodeActionSession`, or `WindowInspectorSnapshotApi`; Phase 2 must build on those seams rather than reintroduce direct service access everywhere.
- `SensitiveActionGuard` must expose `GuardActionResult` for routing. Do not use a final API shaped as `shouldBlock(): Boolean` plus `block(): Unit`.
- Do not force use of every `GuardActionResult` variant. Phase 2 sensitive-action handling is expected to use `Continue`, `Consumed(hasSideEffect = false)`, and `ScheduleFollowUp`; `Blocked` should remain unused unless a real block/session request is made through `BlockSessionController`.
- Both current sensitive-action entrypoints must be migrated: the `handleWindowEvent()` path and the `handlePotentialProtectedInteraction()` path.
- Snapshot-style detection must go through `WindowInspectorSnapshotApi`. Live `AccessibilityNodeInfo` access is allowed only inside bounded `NodeActionSession` callbacks for actions that require live nodes, such as clicking Cancel.
- Extend `WindowInspectorSnapshotApi` only with the minimum text/signal snapshot methods needed for sensitive-action extraction. Do not turn it into a general-purpose Accessibility reading center.
- Keep `SCHEDULER_OWNER_SENSITIVE_ESCAPE` and the existing sensitive-action scheduler keys unchanged so cancellation semantics remain identical.
- Prefer one new guard file first; do not prematurely split into `LauncherSensitiveHandler`, `SettingsSensitiveHandler`, and `ConfirmDialogHandler` unless Phase 2 becomes too large to review safely.

## 3. Target Behavior After Phase 2

After Phase 2:

- `GuardAccessibilityService` still receives events and decides when to invoke sensitive-action handling.
- `SensitiveActionGuard` owns:
  - sensitive-action detection
  - Xiaomi launcher uninstall heuristics
  - destructive keyword matching
  - confirm-dialog cancellation flow
  - sensitive-action cooldown and launcher timing state
  - sensitive-action burst scheduling through `GuardActionScheduler`
- `SensitiveActionGuard` does not own overlay state, block-session state, normal app blocking, protected surface handling, system panel handling, WeChat Finder handling, or Huawei power-save handling.
- `GuardAccessibilityService` no longer directly contains the sensitive-action helper chain, except for temporary forwarding methods while an individual migration task is in progress.
- The service calls the guard at both existing sensitive-action positions and stops routing when the returned `GuardActionResult.continueRouting` is `false`.
- The temporary Phase 1 bypass is still present, still enabled by default, and owned by `SensitiveActionGuard`.

## 4. Current Code To Extract

Primary extraction targets currently live in:

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

The first extraction boundary should include these functions:

- `shouldBlockSensitiveAction()`
- `shouldBlockLauncherSensitiveAction()`
- `shouldBypassLauncherSelfFalsePositiveForPhase1Test()`
- `shouldBypassSettingsSelfFalsePositiveForPhase1Test()`
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

State that should move with the guard when the owning helper moves:

- `lastSensitiveActionBlockTime`
- `miuiLauncherIconMenuBlockUntil`
- `sensitiveActionCooldownMs`
- `sensitiveDialogActionCooldownMs`
- `destructiveActionKeywords`
- `launcherUninstallConfirmKeywords`
- `targetAppKeywords`
- `sensitiveCancelKeywords`
- `TEMP_PHASE1_BYPASS_SELF_SENSITIVE_FALSE_POSITIVES`

State that should stay outside the guard:

- block session / overlay state in `BlockSessionController`
- normal app blocking cooldown state
- protected-surface state
- service lifecycle and router state

## 5. Design Notes

### 5.1 Guard Shape

Start with one file:

- `SensitiveActionGuard.kt`

The constructor should depend on already-extracted seams instead of raw access patterns spread across the service:

- `logTag: String`
- `isXiaomiFamilyDevice: Boolean`
- `isGlobalUnlockEnabled: () -> Boolean`
- `NavigationExecutor`
- `GuardActionScheduler`
- `NodeActionSession`
- `WindowInspectorSnapshotApi`

Allow carefully scoped live-node callbacks only for operations that cannot be implemented from immutable snapshots, for example:

- `readRootInActiveWindowForAction: () -> AccessibilityNodeInfo?`
- `readEventSourceForAction: (AccessibilityEvent) -> AccessibilityNodeInfo?`

Do not pass the full `GuardAccessibilityService` instance into the guard if a smaller dependency shape can work. Do not use root/source callbacks as a general detection API; detection reads should prefer `WindowInspectorSnapshotApi`.

### 5.2 Service Boundary

`GuardAccessibilityService` should keep only a thin forwarding boundary at both current call sites:

```kotlin
when (sensitiveActionGuard.handle(event, packageName)) {
    GuardActionResult.Continue -> Unit
    else -> return
}
```

The service may temporarily keep adapter helpers during migration, but Phase 2 should end with the sensitive-action rule chain no longer implemented inline in the service.

`GuardActionResult` mapping for this phase:

- `Continue`: no sensitive-action match or global unlock allows routing to continue. It must have no side effect.
- `Consumed`: sensitive-action handling intentionally stops the route without scheduling or blocking. Use this for cooldown skips if the current behavior stops routing.
- `ScheduleFollowUp`: escape/cancel work has been scheduled through `GuardActionScheduler`; route stops.
- `Blocked`: not expected for normal Phase 2 sensitive-action handling. Use it only if this phase requests a real block/session through `BlockSessionController`. Do not return `Blocked` just to exercise all result variants.

### 5.3 Sensitive-Action Snapshot

`SensitiveActionGuard.handle(...)` should build a small intermediate value before branching into bypass, cooldown, and execution selection. Name it `SensitiveActionSnapshot` or use an equivalent private data class in `SensitiveActionGuard`; it does not have to be a standalone file.

The snapshot should keep detection state explicit and reviewable, for example:

- `textSignal`
- `signalMatch`
- `targetAppMatch`
- `isLauncherSource`
- `isLauncherIconLike`
- `isConfirmDialog`
- `isDialogLike`
- `sourceKeywordMatches`

Rules:

- Build the snapshot from immutable strings and booleans only.
- Do not store `AccessibilityNodeInfo` in the snapshot.
- Do not let `handle()` grow into a long chain of repeated raw boolean expressions when a named snapshot field would make the behavior easier to test.

### 5.4 Temporary Bypass Ownership

The temporary Phase 1 bypass is now part of the sensitive-action subsystem and should move with it.

Rules:

- Keep behavior unchanged and enabled by default during Phase 2.
- Move the bypass decision methods into `SensitiveActionGuard`.
- Rename only if the new name still makes its temporary nature explicit.
- Do not silently drop this bypass in Phase 2; removing it should be a separate follow-up step after the new guard is validated.

## 6. Task Plan

### Task 1: Introduce `SensitiveActionGuard` shell

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Add the guard class with constructor dependencies only**

Create the file with:

- class declaration
- constructor dependencies
- placeholder public methods:
  - `handle(event: AccessibilityEvent, packageName: String): GuardActionResult`
  - `cancelPendingActions()`

**Step 2: Wire the guard into the service without changing behavior**

- Instantiate the guard in `GuardAccessibilityService`
- Keep existing inline service logic untouched for now
- Do not route real execution through the new guard yet
- Do not pass `BlockSessionController`, `OverlayCoordinator`, or the full `GuardAccessibilityService` into the guard

**Step 3: Compile**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: PASS

### Task 2: Move pure signal detection and related constants

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/guard/SensitiveActionGuardTest.kt`

**Step 1: Move pure helper methods**

Move these methods into the guard first because they do not need live nodes:

- `buildEventSignal()`
- `isLauncherShortcutMenuEvent()`
- `isLauncherUninstallConfirmEvent()`
- `isDialogLikeEvent()`
- `shouldBypassLauncherSelfFalsePositiveForPhase1Test()`
- `shouldBypassSettingsSelfFalsePositiveForPhase1Test()`

Move only the constants those helpers require:

- Do not change keyword contents
- Do not change the temporary bypass flag value
- Keep the temporary bypass default enabled
- Preserve log marker strings exactly, including `phase1_test_bypass_launcher_self_false_positive` and `phase1_test_bypass_settings_self_false_positive`

**Step 2: Add focused JVM tests for pure helper logic**

Write tests for:

- launcher confirm signal detection
- dialog-like signal detection
- temporary bypass classification behavior

If Android `AccessibilityEvent` construction makes direct tests awkward, extract package-private pure string functions in `SensitiveActionGuard` rather than testing through Android framework objects.

**Step 3: Run focused tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.guard.SensitiveActionGuardTest"
```

Expected: PASS

**Step 4: Compile**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: PASS

### Task 3: Add immutable snapshot support for sensitive-action detection

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApi.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Test if needed: `app/src/test/java/com/kidsphoneguard/service/accessibility/WindowInspectorSnapshotApiTest.kt`

**Step 1: Add a read-only text/signal snapshot API if current Phase 1 API is insufficient**

Current `containsSensitiveActionNodeText()` does detection by reading `event.source` and `rootInActiveWindow` directly. Do not move that raw node pattern into `SensitiveActionGuard`.

Instead, add the smallest bounded immutable API needed by the current sensitive-action flow. Prefer explicit replacement methods such as:

- `eventSourceSignal(event: AccessibilityEvent): String`
- `eventSourceMatchesKeywords(event: AccessibilityEvent, keywords: Set<String>): Boolean`
- `activeRootMatchesKeywords(keywords: Set<String>): Boolean`
- optionally, a combined helper only if it removes real duplication without broadening the API beyond sensitive-action detection

Implementation rules:

- Keep these APIs narrow and named around the current sensitive-action migration need. Do not add a broad tree walker, generic node query facade, or catch-all Accessibility reader in this phase.
- The snapshot API obtains and recycles any nodes it reads.
- `SensitiveActionGuard` receives only immutable values or boolean snapshot results.
- No handler or delayed lambda may capture an `AccessibilityNodeInfo`.

**Step 2: Replace service-side raw detection adapters**

Before moving the detection entrypoint, adapt the existing service helpers to use the new `WindowInspectorSnapshotApi` reads while preserving behavior and log markers.

**Step 3: Compile and run focused tests**

Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 4: Move snapshot-based detection helpers

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Move detection helpers that now use snapshots**

Move these methods into the guard:

- `eventSourceLooksLikeLauncherIcon()`
- `eventSourceSelfContainsKeyword()`
- `containsSensitiveActionNodeText()`

These methods should no longer take or return `AccessibilityNodeInfo`. They should delegate to immutable snapshot APIs or operate on immutable strings/booleans.

**Step 2: Introduce `SensitiveActionSnapshot` or equivalent**

Add a private data class or equivalent intermediate value in `SensitiveActionGuard` to hold the detection facts used by `handle(...)`. Keep it local to the guard unless tests or later phases need a separate file.

The snapshot should include only immutable fields such as `textSignal`, `signalMatch`, `targetAppMatch`, `isLauncherSource`, `isLauncherIconLike`, `isConfirmDialog`, `isDialogLike`, and `sourceKeywordMatches`.

**Step 3: Preserve log behavior**

- Keep `launcher_icon_source_check_failed`
- Keep `sensitive_source_self_check_failed`
- Keep `sensitive_node_search_failed`
- Keep `sensitive_action_node_match`

If a log moves from the service to the guard or snapshot API, keep the marker text unchanged.

**Step 4: Compile**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: PASS

### Task 5: Move live-node cancel-click actions

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Move only click/action-oriented live-node helpers**

Move:

- `tryClickSensitiveCancel()`
- `findClickableAncestor()`

Ownership rules:

- Use `NodeActionSession.withUniqueRoots()` and `NodeActionSession.withNodesByText()` for all root/source and text-node access.
- Use scoped root/source callbacks only inside this immediate action path.
- Do not store live nodes on guard state.
- Do not pass live nodes to `GuardActionScheduler`.

**Step 2: Compile**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: PASS

### Task 6: Move detection entrypoints and return `GuardActionResult`

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Move detection orchestration**

Move:

- `shouldBlockSensitiveAction()`
- `shouldBlockLauncherSensitiveAction()`
- `isGlobalUnlockEnabledForSensitiveAction()`

Rename the public orchestration API to `handle(event, packageName): GuardActionResult`. At this point execution can still delegate back to service adapters if needed, but the route result must already be explicit.

**Step 2: Change both service call sites to honor the result**

Replace both existing sensitive-action blocks in `handleWindowEvent()` and `handlePotentialProtectedInteraction()` with:

```kotlin
when (sensitiveActionGuard.handle(event, packageName)) {
    GuardActionResult.Continue -> Unit
    else -> return
}
```

Do not change the surrounding order. Sensitive-action handling remains after power-save/system-panel handling and before protected-settings handling.

**Step 3: Result mapping**

- No sensitive source, no match, or global unlock: `GuardActionResult.Continue`
- Bypass hit: `GuardActionResult.Continue`
- Cooldown skip that currently returns from the service path: `GuardActionResult.Consumed(reason = "sensitive_action_cooldown", hasSideEffect = false)`
- Escape burst scheduled or immediate+delayed escape executed: `GuardActionResult.ScheduleFollowUp(reason = "sensitive_action_escape")`
- `Blocked` is not expected in normal Phase 2 sensitive-action handling. Do not return `Blocked` unless a real block/session request is made through `BlockSessionController`.

**Step 4: Compile and run tests**

Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 7: Move interruption execution and sensitive-action state

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- Optionally create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionState.kt`

**Step 1: Move execution methods**

Move:

- `blockSensitiveAction()`
- `runSensitiveActionFastBackBurst()`
- `runSensitiveActionEscapeBurst()`
- `cancelSensitiveEscapeActions()`
- `performSensitiveEscapeAction()`

Move the state that execution owns:

- `lastSensitiveActionBlockTime`
- `miuiLauncherIconMenuBlockUntil`
- `sensitiveActionCooldownMs`
- `sensitiveDialogActionCooldownMs`

**Step 2: Keep scheduler and navigation behavior identical**

- Same delay arrays
- Same scheduler owner: `SCHEDULER_OWNER_SENSITIVE_ESCAPE`
- Same scheduler keys: `fast_back` and `escape`
- Same runtime logs
- Same cooldown values
- Same launcher menu hold duration
- All navigation calls continue through `NavigationExecutor`
- All delayed actions continue through `GuardActionScheduler`

**Step 3: Route service cleanup through the guard**

In `onDestroy()` and any other relevant cleanup path, call:

- `sensitiveActionGuard.cancelPendingActions()`

**Step 4: Compile and run tests**

Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 8: Remove extracted sensitive-action code from the service

**Files:**
- Modify: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Remove now-dead helper methods and fields from the service**

Delete only the sensitive-action code that is now owned by the guard.

Do not delete:

- service router logic
- protected-surface logic
- normal app block logic
- system panel logic
- WeChat Finder logic
- Huawei power-save logic

**Step 2: Re-run compiler and tests**

Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS

### Task 9: Runtime verification and phase handoff

**Files:**
- Modify if needed: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Modify if needed: `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

**Step 1: Run lint**

Run:

```powershell
.\gradlew.bat :app:lint
```

Expected: PASS or explicitly documented known pre-existing warnings

**Step 2: Runtime validation**

Run:

```powershell
adb logcat -c
adb logcat -s GuardAccessibilityService:D GuardAccessibilityService:W
```

Validate:

- app still opens with the temporary bypass enabled
- existing sensitive-action logs still appear in the same scenarios
- no new crash or scheduler runaway behavior
- both `handleWindowEvent()` and `handlePotentialProtectedInteraction()` still stop routing in the same sensitive-action scenarios
- no `AccessibilityNodeInfo` is captured by scheduled callbacks

**Step 3: Record what still remains legacy**

Document if these remain intentionally unchanged after Phase 2:

- repeated fallback navigation behavior
- temporary Phase 1 bypass still present
- known MIUI / Settings false-positive heuristics still imperfect

## 7. Validation Checklist

After each meaningful task:

```powershell
.\gradlew.bat compileDebugKotlin
```

After Tasks 2, 3, 6, 7, and 8:

```powershell
.\gradlew.bat testDebugUnitTest
```

Before calling Phase 2 complete:

```powershell
.\gradlew.bat :app:lint
```

Runtime validation notes must record:

- methods moved into `SensitiveActionGuard`
- state moved out of the service
- any remaining direct service access still required
- both service entrypoints migrated to `GuardActionResult`
- whether snapshot-style detection now goes through `WindowInspectorSnapshotApi`
- whether `WindowInspectorSnapshotApi` changes stayed limited to sensitive-action needs
- whether live-node operations are limited to `NodeActionSession` callbacks
- whether `SCHEDULER_OWNER_SENSITIVE_ESCAPE`, `fast_back`, and `escape` remained unchanged
- whether the temporary bypass remains enabled by default
- whether self-false-positive behavior was runtime-checked again

## 8. Completion Criteria

Phase 2 is complete only when:

- `GuardAccessibilityService` no longer implements the sensitive-action helper chain inline
- both sensitive-action call sites route through `SensitiveActionGuard.handle(...)`
- `SensitiveActionGuard.handle(...)` returns `GuardActionResult`
- normal Phase 2 sensitive-action handling does not force `GuardActionResult.Blocked`
- `SensitiveActionGuard` builds a `SensitiveActionSnapshot` or equivalent explicit immutable detection value before complex branching
- `SensitiveActionGuard` owns sensitive-action state and execution
- snapshot-style detection goes through `WindowInspectorSnapshotApi`
- `WindowInspectorSnapshotApi` was not expanded into a broad generic Accessibility reader
- sensitive-action burst scheduling still goes through `GuardActionScheduler`
- `SCHEDULER_OWNER_SENSITIVE_ESCAPE`, `fast_back`, and `escape` still preserve existing cancellation semantics
- live-node cancellation logic still goes through `NodeActionSession`
- no `AccessibilityNodeInfo` is stored in guard fields or scheduled callbacks
- compile, unit tests, and lint have been run or explicitly documented as blocked
- runtime validation has been attempted with the temporary bypass still enabled

## 9. Out Of Scope Follow-Ups

These are not Phase 2 goals and should be handled later:

- remove the temporary Phase 1 bypass
- redesign Xiaomi launcher uninstall detection behavior
- redesign settings-page destructive-action heuristics
- optimize delayed fallback/home behavior after normal app blocking
- extract `ProtectedSurfaceGuard` or `AppBlockCoordinator`
