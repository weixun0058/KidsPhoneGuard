# Phase 4 Protected Surface Guard Extraction Implementation Plan

Source plan: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`, especially Task 5: `Extract ProtectedSurfaceGuard and SystemSurfaceGuard`.

**Goal:** Extract protected settings, protected window scanning, and system-surface handling out of `GuardAccessibilityService` while preserving Phase 3 router behavior.

**Architecture:** Phase 4 builds on `AccessibilityEventRouter`. The router continues to own event order and flow control, while `ProtectedSurfaceGuard` and `SystemSurfaceGuard` become adapter targets for protected settings and system panel behavior. This phase is primarily structural; known behavior issues are logged and diagnosed, but broad rule fixes are deferred unless a migration regression is found.

**Tech Stack:** Kotlin, Android AccessibilityService, AccessibilityNodeInfo, existing `GuardActionResult`, `WindowInspectorSnapshotApi`, `NodeActionSession`, `NavigationExecutor`, `GuardActionScheduler`, `BlockSessionController`, `ProtectedSettingsPolicy`.

---

## 1. Naming And Source

This plan intentionally follows the existing phase document naming convention:

- Phase 1: `docs/plans/2026-05-24-phase-1-seam-extraction-plan.md`
- Phase 2: `docs/plans/2026-05-24-phase-2-sensitive-action-extraction-plan.md`
- Phase 3: `docs/plans/2026-05-24-phase-3-accessibility-event-router-plan.md`
- Phase 4: `docs/plans/2026-05-24-phase-4-protected-surface-guard-extraction-plan.md`

`issues_list.md` is the exception because it is a long-lived global issue ledger, not a phase plan.

This document is not a replacement for the overall decoupling plan. It is the detailed Phase 4 execution plan for the overall plan's Task 5. If this document and the source plan disagree, first update the source plan's terminology and boundaries, then keep this Phase 4 plan aligned with it.

## 2. Scope

Phase 4 should extract:

- Protected settings policy orchestration.
- Protected settings snapshot building.
- Protected interactive window scanning.
- Protected surface suppression and release checks.
- System panel collapse into a separate guard.
- Protected-surface-specific state into `ProtectedSurfaceState`.

Phase 4 should not extract:

- `AppBlockCoordinator`.
- `WeChatFinderGuard`.
- Huawei/Honor power-save handling.
- New sensitive-action behavior.
- New broad settings keyword fixes unless they are needed to preserve existing behavior.

Known issues tracked in `issues_list.md` should guide diagnostics, but should not expand Phase 4 into a general bug-fix phase.

## 3. Target Files

Create:

- `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuard.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceState.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/SystemSurfaceGuard.kt`

Modify:

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt` only if adapter wiring needs a narrow signature adjustment.

Test:

- `app/src/test/java/com/kidsphoneguard/service/guard/ProtectedSurfaceStateTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuardTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/SystemSurfaceGuardTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`

At minimum, tests must cover state ownership, router adapter order, and `GuardActionResult` return semantics for moved adapters. Android-only live node behavior may stay under device validation, but JVM-testable branch logic should not remain untested.

## 4. Phase 3 Boundary

Before starting Phase 4, keep this invariant:

- `AccessibilityEventRouter` remains the only place that classifies accessibility event types.
- Window route order remains encoded in `AccessibilityEventRouter`.
- Interaction route order remains encoded in `AccessibilityEventRouter`.
- `GuardAccessibilityService.onAccessibilityEvent()` remains a thin heartbeat/error-boundary/router-forwarding entrypoint.

Do not move route ordering into `ProtectedSurfaceGuard` or `SystemSurfaceGuard`.

## 5. Guard Boundaries

### 5.1 `ProtectedSurfaceGuard`

Owns:

- Protected settings candidate detection.
- Snapshot construction for `ProtectedSettingsPolicy`.
- Policy evaluation orchestration.
- Protected settings decision logging.
- Protected window scanning.
- Protected surface suppression decisions.
- Protected overlay release checks.

Does not own:

- App-block policy.
- Normal blocked-app decisions.
- WeChat Finder behavior.
- Sensitive action detection.
- System panel collapse.
- Raw shared overlay/block session state.

Allowed dependencies:

- `ProtectedSettingsPolicy`
- `WindowInspectorSnapshotApi`
- `NodeActionSession`
- `NavigationExecutor`
- `GuardActionScheduler`
- `BlockSessionController`
- Small callbacks for service-only operations that cannot yet move safely.

Disallowed direct dependencies:

- Direct `OverlayService` state mutation.
- Direct writes to shared block fields.
- Long-lived `AccessibilityNodeInfo` storage.
- A constructor that depends on `Context`, `Handler`, root/window access, overlay, navigation, settings, and scheduler all at once.

### 5.2 `SystemSurfaceGuard`

Owns:

- System panel package recognition.
- System panel signal collection.
- System panel collapse/dismiss behavior.

Does not own:

- Protected settings page suppression.
- Normal app blocking.
- WeChat Finder.
- Sensitive action detection.

System panel handling must stay separate from protected settings handling so this phase does not create another mixed-responsibility object.

## 6. GuardActionResult Semantics

Adapters called by `AccessibilityEventRouter` must continue to return `GuardActionResult`:

- `Continue`: no route consumption; router continues.
- `Consumed`: route stops; may or may not have side effects.
- `ScheduleFollowUp`: delayed work has been scheduled; route stops.
- `Blocked`: only use if the action actually requests/executes a block through `BlockSessionController` semantics.

For Phase 4, most protected-surface hits should remain `Consumed(reason = "protected_settings_policy", hasSideEffect = true)` unless the code is explicitly converted to a clearer `Blocked` contract. Do not use `Blocked` merely to use the enum.

## 7. AccessibilityNodeInfo Ownership

Rules:

- Snapshot reads should prefer `WindowInspectorSnapshotApi` where the Phase 1 seam already exists.
- Live node traversal can use `NodeActionSession` or a local acquire/use/recycle block.
- No guard may store `AccessibilityNodeInfo` across callbacks, handler posts, coroutine launches, or scheduler delays.
- The code that obtains a node is responsible for recycling it.
- If a child node is obtained during traversal, it must be recycled in the same loop iteration.

Phase 4 should reduce raw node handling in `GuardAccessibilityService`, not spread it into multiple new owners.

## 8. Shared Helper Ownership

`collectNodeSignals(...)` and `appendSignal(...)` are currently shared by protected settings snapshot code and system panel signal code. Phase 4 must not turn that shared helper usage into a cross-guard dependency.

Allowed options:

- Prefer separate guard-local implementations when the duplication is small. Phase 4 optimizes for lower coupling and safer extraction, not zero duplication.
- Extract a very small shared internal helper, such as `AccessibilitySignalCollector`, only if duplication becomes clearly larger than the coupling cost. The helper must have no policy, overlay, router, or guard dependencies.

Disallowed options:

- Do not make `ProtectedSurfaceGuard` depend on `SystemSurfaceGuard` helpers.
- Do not make `SystemSurfaceGuard` depend on `ProtectedSurfaceGuard` helpers.
- Do not place shared helpers in either guard if the other guard must call back into them.

Decide this before moving `collectNodeSignals(...)` or `appendSignal(...)`. The chosen option must preserve the existing node recycle ownership rules.

## 9. Implementation Tasks

### Task 1: Add `ProtectedSurfaceState`

Move protected-surface-only state out of `GuardAccessibilityService`:

- `lastProtectedWindowLogTime`
- `lastProtectedWindowSignature`
- `lastProtectedSettingsDecisionLogTime`
- `lastProtectedSettingsDecisionSignature`
- `lastProtectedWindowSweepPackage`
- `lastProtectedWindowSweepTime`
- `lastProtectedSurfaceSuppressPackage`
- `lastProtectedSurfaceSuppressTime`

Keep cooldown constants close to the guard or pass them explicitly. Do not put normal app-block state into `ProtectedSurfaceState`.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

### Task 2: Create `ProtectedSurfaceGuard` Shell

Create a shell class that receives dependencies but initially delegates through existing service callbacks if needed.

The shell should expose narrow methods matching current router adapter needs:

```kotlin
fun shouldSweepProtectedWindows(event: AccessibilityEvent?, packageName: String): Boolean

fun findProtectedInteractiveWindowPackage(source: String): String?

fun handleProtectedSettingsPolicyIfCandidate(
    event: AccessibilityEvent?,
    packageName: String,
    source: String
): GuardActionResult
```

At the end of this task, behavior may still be mostly delegated, but the dependency boundary should be visible.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

### Task 3: Move Snapshot And Policy Evaluation

Move pure or mostly pure protected settings logic first:

- `handleProtectedSettingsPolicyIfCandidate(...)`
- `shouldSweepProtectedWindows(...)`
- `buildSettingsPageSnapshot(...)`
- `isExplicitUserActionEvent(...)`
- `collectCandidateWindowNodeSignals(...)`
- `isSameBasePackage(...)`
- `collectInteractiveWindowPackages(...)`
- `logProtectedSettingsDecision(...)`

`collectNodeSignals(...)` may move in this task only after the shared helper ownership decision in Section 8 is applied. It must not become a `ProtectedSurfaceGuard` helper that `SystemSurfaceGuard` later depends on.

Temporary callback boundary:

- `handleProtectedSettingsPolicyIfCandidate(...)` currently calls `releaseProtectedSettingsOverlayIfAllowed(...)` for allow/observe decisions and `suppressProtectedSystemSurface(...)` for block decisions.
- Task 3 moves snapshot and policy orchestration before Task 5 moves suppression and release behavior.
- Therefore, while Task 3 is in progress, `ProtectedSurfaceGuard` must call release and suppress behavior through explicit temporary callbacks.
- Do not let `ProtectedSurfaceGuard` directly reach into service internals for these calls.
- Do not move suppression/release behavior early just to remove the callback; keep Task 5 as the suppression/release migration step.

Keep behavior, log markers, cooldowns, and decision reasons unchanged.

Do not fix `ISSUE-001`, `ISSUE-002`, or `ISSUE-003` in this task unless a direct migration bug appears. It is acceptable to add narrow timing logs if they do not alter behavior.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Device smoke checks:

- 本应用打开通过。
- 应用商店拦截成功。
- 应用卸载拦截成功。
- 使用情况访问仍能拦截。

### Task 4: Move Protected Window Scanning

Move:

- `findProtectedInteractiveWindowPackage(...)`
- `forEachInteractiveWindow(...)`
- `logProtectedWindowSnapshot(...)`
- `sweepProtectedInteractiveWindows(...)`

Keep these logs stable:

- `protected_window_snapshot`
- `protected_window_sweep`
- `protected_surface_fast_suppress`

The router should still call the same adapter-level concepts. Only the implementation target should move from service internals to `ProtectedSurfaceGuard`.

Temporary callback boundary:

- `sweepProtectedInteractiveWindows(...)` currently calls visible power-save exit and visible system panel collapse before suppressing protected windows.
- Phase 4 does not extract visible power-save handling.
- `SystemSurfaceGuard` is extracted later in Task 6.
- Therefore, while Task 4 is in progress, `ProtectedSurfaceGuard` may call visible power-save exit and visible system panel collapse through explicit temporary callbacks.
- These callbacks must be named as transitional dependencies and removed or narrowed once Task 6 finishes.
- Do not make `ProtectedSurfaceGuard` own power-save behavior or system panel collapse behavior.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

### Task 5: Move Suppression And Release Checks

Move:

- `suppressProtectedSystemSurface(...)`
- `isProtectedSystemSurface(...)`
- `isProtectedSurfaceSuppressionAllowed(...)`
- `performProtectedSurfaceNavigation(...)`
- `releaseProtectedSettingsOverlayIfAllowed(...)`
- `scheduleProtectedOverlayReleaseCheck(...)`

Requirements:

- Overlay/block state changes go through `BlockSessionController`.
- Show/hide technical calls remain behind `OverlayCoordinator` through existing controller behavior.
- Navigation goes through `NavigationExecutor`.
- Delayed work goes through `GuardActionScheduler`.
- Existing scheduler owner/key semantics must remain stable.
- Do not introduce the retired overlay-block controller name; the accepted name is `BlockSessionController`.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Device checks:

- 应用卸载拦截成功。
- 使用情况访问仍能拦截。
- 全普通权限访问仍能拦截。
- 本应用打开仍通过。
- `com.xiaomi.market` 不出现卡死回归。

### Task 6: Extract `SystemSurfaceGuard`

Move system panel behavior separately:

- `lastSystemPanelCollapseTime`, either into `SystemSurfaceGuard`-owned state or into a tiny `SystemSurfaceState`
- `collapseSystemPanelIfNeeded(...)`
- `collapseVisibleSystemPanelIfNeeded(...)`
- `collapseSystemPanelWithSignal(...)`
- `performSystemPanelCollapseAction(...)`
- `isSystemPanelPackage(...)`
- `shouldInspectSystemPanel(...)`
- `buildSystemPanelSignal(...)`
- `buildVisibleSystemPanelSignal(...)`
- `collectSystemPanelWindowNodeSignals(...)`

Return `GuardActionResult.Consumed(reason = "system_panel_collapse", hasSideEffect = true)` when the panel was collapsed. Return `Continue` otherwise.

Do not merge this into `ProtectedSurfaceGuard`.

`appendSignal(...)` and `collectNodeSignals(...)` must follow the shared helper ownership decision in Section 8. Do not move `appendSignal(...)` into `SystemSurfaceGuard` if `ProtectedSurfaceGuard` would still need it.

Verification:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

### Task 7: Router Adapter Cleanup

After the guards exist, clean `GuardAccessibilityService` adapter wiring:

- Router still receives adapter functions.
- Adapter functions delegate to `ProtectedSurfaceGuard` / `SystemSurfaceGuard`.
- `GuardAccessibilityService` should no longer contain the protected-surface method bodies moved above.
- Route order remains in `AccessibilityEventRouter`.

Search checks:

```powershell
rg -n "handleProtectedSettingsPolicyIfCandidate|buildSettingsPageSnapshot|findProtectedInteractiveWindowPackage|suppressProtectedSystemSurface|collapseSystemPanelIfNeeded" app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt
```

Expected:

- Only thin adapter wiring remains, or no matches if the service no longer needs wrappers.

### Task 8: Update Issue Ledger

Update `issues_list.md` after Phase 4:

- Mark any migration-caused regression as new issue.
- Keep `ISSUE-001`, `ISSUE-002`, and `ISSUE-003` open unless they were intentionally fixed and device-verified.
- Add any protected-surface timing evidence gathered during the phase.
- Do not close WeChat Finder issues in this phase.

## 10. Validation Matrix

Run after final Phase 4 implementation:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Manual/device validation:

- 本应用打开通过。
- 应用商店拦截成功。
- 普通拦截成功。
- 白名单成功。
- 应用卸载拦截成功。
- 使用情况访问仍能拦截。
- 全普通权限访问仍能拦截。
- 无障碍权限设置页记录足够日志，即使仍未修复。
- 小米省电模式保持现状。
- 微信视频号保持现状，不在本阶段混修。

## 11. Rollback Strategy

Each task should remain independently reversible:

- If `ProtectedSurfaceState` causes state drift, move the state fields back before continuing.
- If snapshot migration changes behavior, revert Task 3 without touching router.
- If suppression migration changes block behavior, revert Task 5 while keeping snapshot migration if stable.
- If `SystemSurfaceGuard` causes panel behavior regressions, revert Task 6 only.

Do not combine unrelated behavior fixes with extraction commits.

## 12. Completion Criteria

Phase 4 is complete only when:

- `ProtectedSurfaceGuard` exists and owns protected settings snapshot/policy/window/suppression orchestration.
- `ProtectedSurfaceState` owns protected-surface-specific cooldown/logging state.
- `SystemSurfaceGuard` exists and owns system panel collapse behavior.
- `lastSystemPanelCollapseTime` no longer lives in `GuardAccessibilityService`.
- Shared signal helpers do not create dependencies between `ProtectedSurfaceGuard` and `SystemSurfaceGuard`.
- `AccessibilityEventRouter` still owns event type classification and route order.
- `GuardAccessibilityService` no longer contains the moved protected-surface method bodies.
- `GuardAccessibilityService` no longer contains the moved system-surface method bodies.
- Existing log markers, cooldowns, scheduler owners/keys, and route order remain stable.
- No new direct overlay/block session state ownership is introduced.
- `BlockSessionController` remains the only block-session policy/state dependency for protected-surface suppression.
- The retired overlay-block controller name does not appear in the Phase 4 implementation.
- `compileDebugKotlin`, `testDebugUnitTest`, and `:app:lint` pass or any blockers are explicitly documented.
- Device regression results are recorded in `issues_list.md` or the phase notes.
