# Phase 8 Sensitive Action Internal Decoupling Implementation Plan

> **Status: Obsolete as of 2026-05-31.**
> The sensitive-action/uninstall-protection feature has been removed from runtime code. Do not execute this plan or recreate `SensitiveActionGuard`, `SensitiveActionExecutor`, confirmation-dialog interception, launcher-menu interruption, or related state classes.

Source plan: `docs/plans/2026-05-24-guard-accessibility-service-decoupling-plan.md`, especially the remaining `SensitiveActionState` target and the follow-up need to split `SensitiveActionGuard` by state, detection, confirmation handling, and execution.

**Goal:** Finish the remaining sensitive-action structural closeout and then split `SensitiveActionGuard` internally so source detection, confirmation handling, execution, and state management can evolve independently.

**Architecture:** Phase 8 does not move logic back into `GuardAccessibilityService`. It starts with the low-risk final closeout from the original decoupling plan, then decomposes the sensitive-action subsystem behind the existing `SensitiveActionGuard` route adapter. Each task preserves current behavior first; issue-list behavior fixes are only introduced in explicitly marked issue tasks after the structural boundary is stable.

**Tech Stack:** Kotlin, Android `AccessibilityEvent`, `AccessibilityNodeInfo`, existing `SensitiveActionGuard`, `GuardActionResult`, `GuardActionScheduler`, `NavigationExecutor`, `NodeActionSession`, `WindowInspectorSnapshotApi`.

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
- Phase 8: `docs/plans/2026-05-24-phase-8-sensitive-action-internal-decoupling-plan.md`

Phase 8 is not a rewrite of the original service-level decoupling plan. It is the next layer after that plan: internal decoupling inside the sensitive-action subsystem.

## 2. Current Understanding

The previous phases successfully moved sensitive-action behavior out of `GuardAccessibilityService`.

Current `SensitiveActionGuard` still mixes these responsibilities:

- state management:
  - `lastSensitiveActionBlockTime`
  - `miuiLauncherIconMenuBlockUntil`
- source classification:
  - MIUI launcher
  - Android settings
  - installer / market
- action-stage detection:
  - page-level destructive signals
  - launcher shortcut/menu signals
  - uninstall confirmation dialog signals
  - dialog-like signals
- live node action:
  - search cancel buttons
  - click cancel
  - find clickable ancestor
- navigation/scheduling execution:
  - fast back burst
  - escape burst
  - `sensitive_escape` scheduler owner cancellation
- compatibility bypasses:
  - launcher self false-positive bypass
  - settings self false-positive bypass
  - installer/market node-only skip

This is not a failure of Phase 2. Phase 2 intentionally extracted the sensitive-action subsystem as a whole from the main service. Phase 8 now splits that subsystem internally.

## 3. Relation To `issues_list.md`

The following known behavior issues are already tracked in `issues_list.md` and must not be silently mixed into structural tasks:

- `ISSUE-011`: MIUI application list page is misdetected as a sensitive uninstall scenario.
- `ISSUE-008`: launcher sensitive-action scope was previously widened and must remain narrow.

Phase 8 should treat issue-listed behavior as follows:

- structural tasks may move code without changing behavior.
- behavior fixes for `ISSUE-011` must be explicitly marked as issue work.
- launcher detector extraction must preserve current behavior first.
- any later `ISSUE-011` fix must be log-driven and device-verified.

`SensitiveActionState` itself is not listed in `issues_list.md`; it is a remaining structural target from the overall decoupling plan and should be completed first.

## 4. Target End State

After Phase 8, `SensitiveActionGuard` should become an orchestrator:

- build or receive a source/detection snapshot.
- ask source detectors for detection results.
- ask confirmation handler for confirmation-stage semantics.
- ask executor to perform cancel/back/home actions.
- update state through `SensitiveActionState`.
- return `GuardActionResult`.

The intended files are:

```text
app/src/main/java/com/kidsphoneguard/service/guard/
  SensitiveActionGuard.kt
  SensitiveActionState.kt
  SensitiveActionExecutor.kt
  SensitiveActionSnapshot.kt
  SensitiveActionSnapshotBuilder.kt
  SensitiveConfirmDialogHandler.kt
  SettingsSensitiveDetector.kt
  InstallerMarketSensitiveDetector.kt
  LauncherSensitiveDetector.kt
  MiuiLauncherSensitiveHandler.kt
```

The exact file split can be adjusted while implementing, but the responsibilities must stay separate.

## 5. Non-Goals

Phase 8 should not:

- move sensitive-action logic back into `GuardAccessibilityService`.
- change router order.
- change scheduler owner/key/delay values unless explicitly stated in an issue task.
- fix protected settings pages.
- fix WeChat Finder.
- fix Huawei/Honor power-save.
- broaden MIUI launcher detection without fresh logs.
- delete the temporary Phase 1 self-false-positive bypass unless a separate tested decision is made.

## 6. Shared Semantics To Preserve

Preserve these route results:

- no sensitive action: `GuardActionResult.Continue`
- cooldown hit: `GuardActionResult.Consumed(reason = "sensitive_action_cooldown", hasSideEffect = false)`
- sensitive escape scheduled: `GuardActionResult.ScheduleFollowUp(reason = "sensitive_action_escape")`

Preserve scheduler owner and keys:

- owner: `sensitive_escape`
- fast back key: `fast_back`
- normal escape key: `escape`

Preserve timing:

- sensitive action cooldown: `120L`
- confirm dialog cooldown: `20L`
- MIUI launcher icon-menu window: `300L`
- fast back burst delays:
  - `0L`, `12L`, `30L`, `60L`
- escape burst delays:
  - `0L`, `16L`, `45L`, `85L`, `140L`, `240L`

Preserve log markers:

- `sensitive_action_skip_global_unlock`
- `sensitive_action_detected`
- `sensitive_action_skip_installer_market_node_only`
- `sensitive_action_skip_cooldown`
- `sensitive_action_block`
- `sensitive_action_cancel_clicked`
- `sensitive_action_escape`
- `sensitive_action_escape_failed`
- `launcher_uninstall_block_on_target_icon`
- `launcher_uninstall_confirm_detected`
- `phase1_test_bypass_launcher_self_false_positive`
- `phase1_test_bypass_settings_self_false_positive`

## 7. Task Breakdown

### Task 1: Extract `SensitiveActionState`

**Purpose:** Complete the remaining structural closeout from the original decoupling plan without changing behavior.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionState.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/SensitiveActionStateTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Steps:**

1. Create `SensitiveActionState`.
2. Move these fields into it:
   - `lastSensitiveActionBlockTime`
   - `miuiLauncherIconMenuBlockUntil`
3. Add methods or properties for:
   - reading and updating last block time.
   - marking MIUI launcher icon-menu blocking until `now + 300L`.
   - checking whether current time is inside launcher icon-menu window.
4. Keep cooldown values as guard/decision-layer configuration, not mutable state:
   - `sensitiveActionCooldownMs = 120L`
   - `sensitiveDialogActionCooldownMs = 20L`
   - state methods should accept `now` and the relevant `cooldownMs` as parameters when they need to evaluate elapsed time.
5. Inject the state into `SensitiveActionGuard` with a default instance:

```kotlin
private val state: SensitiveActionState = SensitiveActionState()
```

6. Replace direct field reads/writes in `SensitiveActionGuard` with state calls.
7. Add unit tests for:
   - cooldown elapsed calculation.
   - state update after block.
   - launcher icon-menu window check.
8. Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.guard.SensitiveActionStateTest" --tests "com.kidsphoneguard.service.guard.SensitiveActionGuardTest"
```

9. Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

**Must not change:**

- cooldown values.
- launcher window length.
- log markers.
- route result semantics.

### Task 2: Extract `SensitiveActionExecutor`

**Purpose:** Separate execution side effects from detection/orchestration.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionExecutor.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/SensitiveActionExecutorTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Moves from `SensitiveActionGuard`:**

- `cancelPendingActions()`
- `tryClickSensitiveCancel(...)`
- `findClickableAncestor(...)`
- `runSensitiveActionFastBackBurst()`
- `runSensitiveActionEscapeBurst()`
- `performSensitiveEscapeAction(...)`

**Dependencies:**

- `logTag`
- `NavigationExecutor`
- `GuardActionScheduler`
- `NodeActionSession`
- `readRootInActiveWindowForAction`
- `readEventSourceForAction`
- cancel keywords
- scheduler owner/key constants

**Steps:**

1. Create executor shell and move constants required only for execution.
2. Preserve `AccessibilityNodeInfo` ownership exactly:
   - raw nodes are used only inside `NodeActionSession` callbacks.
   - clickable ancestors returned by `findClickableAncestor(...)` are recycled by the caller.
3. Move cancel click behavior first.
4. Move burst scheduling second.
5. Keep `SensitiveActionGuard.cancelPendingActions()` as a thin delegate while external callers such as `GuardAccessibilityService` lifecycle cleanup or broadcast cleanup still hold a `SensitiveActionGuard` reference. Do not force those callers to depend on `SensitiveActionExecutor` in Phase 8. If a later phase changes the composition root to hold the executor directly, migrate the call sites then.
6. Do not extract a shared `NodeActionUtils` in this phase. Keep Huawei/Honor power-save's duplicate clickable-ancestor helper in `HuaweiPowerSaveHandler` and move only the sensitive-action implementation into `SensitiveActionExecutor`. Revisit a shared helper only if a third caller appears or duplication becomes behaviorally risky.
7. Add tests for pure scheduling intent. Prefer a small pure model such as a burst sequence/action plan that can be tested without Android `Handler`. Do not introduce a fake `GuardActionScheduler` abstraction unless implementation needs it. Android node traversal can stay covered by integration/device verification.
8. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.guard.SensitiveActionExecutorTest" --tests "com.kidsphoneguard.service.guard.SensitiveActionGuardTest"
```

9. Run full checks.

**Must not change:**

- fast back burst sequence.
- escape burst sequence.
- scheduler owner/key.
- cancel button keywords.

### Task 3: Extract Shared Detection Snapshot And Builder

**Purpose:** Stop passing a large private boolean bundle through unrelated methods and make detector input explicit without letting every detector read raw `AccessibilityEvent` / `WindowInspectorSnapshotApi` independently.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionSnapshot.kt`
- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionSnapshotBuilder.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`
- Test: `app/src/test/java/com/kidsphoneguard/service/guard/SensitiveActionGuardTest.kt`

**Move / define:**

- current `SensitiveActionSnapshot`
- source type flags:
  - launcher
  - settings
  - installer / market
- action-stage flags:
  - signal match
  - node match
  - target app match
  - confirm dialog
  - dialog-like
  - source keyword match
  - long click
  - MIUI target icon probe
- snapshot-building helpers:
  - `buildEventSignal(...)`
  - `containsSensitiveActionNodeText(...)`
  - `eventSourceLooksLikeLauncherIcon(...)`
  - `eventSourceSelfContainsKeyword(...)`

**Steps:**

1. Create immutable model file as `SensitiveActionSnapshot.kt`.
2. Create `SensitiveActionSnapshotBuilder` as the only component in this subsystem that directly combines event text, event source snapshots, active-root snapshots, and `WindowInspectorSnapshotApi` helper calls.
3. Keep field names close to current names to reduce migration risk.
4. Preserve `isMiuiLauncherSource` in the snapshot. Later confirmation handling should use `snapshot.isMiuiLauncherSource && snapshot.isConfirmDialog` to distinguish MIUI launcher confirmation from settings / market confirmation.
5. Inject or create the builder from `SensitiveActionGuard`:

```kotlin
private val snapshotBuilder: SensitiveActionSnapshotBuilder = SensitiveActionSnapshotBuilder(...)
```

6. `SensitiveActionGuard.handle(...)` calls `snapshotBuilder.build(event, packageName)` once, then passes the resulting `SensitiveActionSnapshot` to detectors and handlers.
7. Change `SensitiveActionGuard` to use the new model type and builder.
8. Do not change how snapshot fields are computed yet.
9. Do not turn `WindowInspectorSnapshotApi` into a generic accessibility reading center; add only the minimum snapshot reads required by current sensitive-action detection.
10. Run existing `SensitiveActionGuardTest`.

**Must not change:**

- detection behavior.
- text signal construction.
- source matching logic.
- event source ownership: detectors consume the snapshot; they do not call `event.source` directly.

### Task 4: Extract `SensitiveConfirmDialogHandler`

**Purpose:** Separate confirmation-stage detection and action choice from source detection.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveConfirmDialogHandler.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/SensitiveConfirmDialogHandlerTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Moves / owns:**

- `isLauncherUninstallConfirmEvent(...)`
- `isDialogLikeEvent(...)`
- confirmation-stage decision:
  - MIUI launcher confirm uses cancel click + fast back burst.
  - non-launcher confirm uses cancel click + escape burst.

**Does not own:**

- source classification.
- installer/market source skip.
- settings source bypass.
- raw node traversal implementation, which belongs to `SensitiveActionExecutor`.

**Steps:**

1. Move pure confirm/dialog helpers first.
2. Add unit tests that preserve current string behavior.
3. Add a small decision method that receives all context needed for mode choice without reaching back into the guard:

```kotlin
fun decide(
    packageName: String,
    snapshot: SensitiveActionSnapshot,
    state: SensitiveActionState,
    now: Long
): SensitiveConfirmDecision
```

4. The decision can contain an action mode, for example:

```kotlin
enum class SensitiveExecutionMode {
    EscapeBurst,
    ConfirmFastBack,
    ConfirmEscape
}
```

5. `SensitiveConfirmDialogHandler` may use `packageName`, `snapshot.isConfirmDialog`, `snapshot.isDialogLike`, and `state.isInLauncherIconMenuWindow(now)` to choose between MIUI launcher fast-back and generic escape behavior.
6. The handler should prefer `snapshot.isMiuiLauncherSource` over a separate raw `isXiaomiFamilyDevice` parameter. Device/package specificity belongs in snapshot construction and launcher source classification, not in confirmation execution policy.
7. `SensitiveActionGuard` maps detection + confirm handler output to executor calls. The handler should decide mode, not perform navigation or schedule work.
8. Run targeted tests and full checks.

**Must not change:**

- confirmed dialog string rules.
- cancel-click-before-burst behavior.
- route results.

### Task 5: Extract `InstallerMarketSensitiveDetector`

**Purpose:** Separate installer/market source behavior from generic sensitive action detection.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/InstallerMarketSensitiveDetector.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/InstallerMarketSensitiveDetectorTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Moves / owns:**

- installer/market source interpretation.
- `shouldSkipInstallerOrMarketNodeOnlyMatch(...)`.

**Steps:**

1. Move the pure skip helper first.
2. Preserve current node-only false-positive skip behavior.
3. Keep behavior around `com.xiaomi.market` stable.
4. Add tests for:
   - node-only installer/market match is skipped.
   - confirm dialog in installer/market is not skipped.
   - settings source is not treated as installer/market skip.
5. Run targeted tests and full checks.

**Must not change:**

- current `com.xiaomi.market` no-card-stuck behavior.
- current confirm dialog behavior.

### Task 6: Extract `SettingsSensitiveDetector`

**Purpose:** Separate settings-source sensitive action behavior and temporary self false-positive bypass.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/SettingsSensitiveDetector.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/SettingsSensitiveDetectorTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Moves / owns:**

- settings source classification decision.
- `shouldBypassSettingsSelfFalsePositiveForPhase1Test(...)`.

**Steps:**

1. Move the pure bypass helper without changing the default bypass setting.
2. Preserve and migrate the existing `TEMP_PHASE1_BYPASS_SELF_SENSITIVE_FALSE_POSITIVES = true` constant. Do not replace it with a new hard-coded `true` inside `SettingsSensitiveDetector` or `LauncherSensitiveDetector`.
3. Add tests for:
   - exact bypass shape.
   - no bypass for dialog-like text.
   - no bypass when signal match exists.
4. Run targeted tests and full checks.

**Must not change:**

- temporary bypass default.
- settings source behavior.
- protected settings behavior.

### Task 7: Extract `LauncherSensitiveDetector` Shell

**Purpose:** Move launcher source classification into a named component without fixing MIUI app-list behavior yet.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/LauncherSensitiveDetector.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/LauncherSensitiveDetectorTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Moves / owns:**

- launcher source classification.
- `isMiuiLauncherSensitiveSource(...)`.
- `shouldBypassLauncherSelfFalsePositiveForPhase1Test(...)`.
- launcher source recognition and launcher self false-positive bypass.

**Does not own:**

- raw event-source traversal. `eventSourceLooksLikeLauncherIcon(...)` and `eventSourceSelfContainsKeyword(...)` belong to `SensitiveActionSnapshotBuilder` after Task 3.
- execution decisions for icon probe / long click / confirm stages. Those belong to `MiuiLauncherSensitiveHandler` after Task 8.

**Important issue boundary:**

- Do not fix `ISSUE-011` in this task.
- Do not broaden or narrow launcher rules in this task.
- This task is structural only.

**Steps:**

1. Move pure launcher helpers.
2. Keep MIUI/Xiaomi scope exactly the same.
3. Keep Phase 1 launcher bypass default unchanged.
4. Add tests copied from current `SensitiveActionGuardTest`.
5. Run targeted tests and full checks.

### Task 8: Extract `MiuiLauncherSensitiveHandler`

**Purpose:** Isolate MIUI launcher event-stage handling so `ISSUE-011` can later be fixed in one place.

**Files:**

- Create: `app/src/main/java/com/kidsphoneguard/service/guard/MiuiLauncherSensitiveHandler.kt`
- Create: `app/src/test/java/com/kidsphoneguard/service/guard/MiuiLauncherSensitiveHandlerTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionGuard.kt`

**Moves / owns:**

- current `handleLauncherSensitiveAction(...)` body.
- launcher event-stage decision:
  - target icon probe.
  - long click / shortcut menu.
  - launcher uninstall confirm.
- MIUI launcher icon menu state write:
  - through `SensitiveActionState`, not local fields.
- launcher-specific log markers:
  - `launcher_uninstall_block_on_target_icon`
  - `launcher_uninstall_confirm_detected`

**Issue boundary:**

- This task should first preserve current behavior.
- `ISSUE-011` fix should be a separate follow-up task after this extraction, unless fresh logs are available and the user explicitly approves combining them.

**Steps:**

1. Move handler body with no rule changes.
2. Make handler return an explicit intermediate decision, not a final `GuardActionResult`, for example:

```kotlin
sealed class MiuiLauncherSensitiveDecision {
    data object Continue : MiuiLauncherSensitiveDecision()
    data class Block(
        val executionMode: SensitiveExecutionMode,
        val markIconMenuWindow: Boolean
    ) : MiuiLauncherSensitiveDecision()
}
```

3. `SensitiveActionGuard` maps this decision to state updates, executor calls, and final `GuardActionResult`.
4. If the decision is `Block(markIconMenuWindow = true)`, `SensitiveActionGuard` updates `SensitiveActionState` before executing the action.
5. The handler should not directly call `NavigationExecutor`, `GuardActionScheduler`, or `NodeActionSession`.
6. Keep executor calls outside the handler if possible; the handler should decide mode, not execute navigation.
7. Run tests and full checks.
8. Device verify:
   - MIUI desktop long-press / uninstall chain still blocks.
   - MIUI app list behavior is recorded, but not fixed unless this task is explicitly expanded.

### Task 9: Issue-Gated `ISSUE-011` Fix

**Purpose:** Fix MIUI application list false positive only after launcher behavior has a clear handler boundary.

**Files:**

- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/MiuiLauncherSensitiveHandler.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SettingsSensitiveDetector.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/InstallerMarketSensitiveDetector.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionSnapshot.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/service/guard/SensitiveActionSnapshotBuilder.kt`
- Modify tests under `app/src/test/java/com/kidsphoneguard/service/guard/`
- Update: `issues_list.md`

**Required evidence before code changes:**

- fresh logcat while browsing MIUI app list.
- fresh logcat for real MIUI launcher uninstall menu / confirm dialog.
- event type, package name, class name, event text, content description, source signal.

**Fix direction:**

- Do not simply remove destructive keywords.
- Do not disable MIUI launcher protection.
- Treat the known `ISSUE-011` root cause as a non-launcher false positive until fresh logs prove otherwise:
  - MIUI application list page under `com.miui.securitycenter` can expose both destructive text such as `卸载` and target app text such as `拉钩守护`.
  - this can make `nodeMatch=true` and `targetAppMatch=true` even when `isMiuiLauncherSource=false`.
  - the failure may therefore sit in the settings / installer-market branch, not only in launcher handling.
- First confirm how `com.miui.securitycenter` is classified by `WhitelistManager` in the current build.
- Current code classifies `com.miui.securitycenter` as settings source, not installer / market source. Treat `SettingsSensitiveDetector` as the primary expected fix point unless fresh code/log evidence changes this.
- If it is treated as settings source, consider a settings-list browsing signal that does not fire when there is a real dialog-like or confirm-dialog stage.
- For settings source, the likely rule is: when `isDialogLike=false` and `isConfirmDialog=false`, do not block only because `nodeMatch=true` and `targetAppMatch=true`; require `signalMatch=true` or another explicit action-stage signal.
- If it is treated as installer/market source, verify whether the existing node-only skip is insufficient and tighten that branch with the smallest rule.
- The main distinction should be page stage, not package name alone: real uninstall confirmation has dialog/confirm characteristics; app-list browsing usually does not.
- Distinguish app-list page signal from actual launcher icon/menu/confirm stage.
- Keep real uninstall confirm and launcher shortcut menu blocked.

**Verification:**

- MIUI app list can be browsed without immediate forced exit.
- MIUI desktop uninstall chain still blocks.
- self app opens.
- application market block has no overlay stuck regression.

### Task 10: Final SensitiveAction Internal Closeout

**Files:**

- Create: `docs/plans/2026-05-24-phase-8-closeout-summary.md`
- Modify: `issues_list.md` only if `ISSUE-011` or related findings change.

**Steps:**

1. Run targeted tests:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.guard.SensitiveActionStateTest" --tests "com.kidsphoneguard.service.guard.SensitiveActionExecutorTest" --tests "com.kidsphoneguard.service.guard.SensitiveConfirmDialogHandlerTest" --tests "com.kidsphoneguard.service.guard.InstallerMarketSensitiveDetectorTest" --tests "com.kidsphoneguard.service.guard.SettingsSensitiveDetectorTest" --tests "com.kidsphoneguard.service.guard.LauncherSensitiveDetectorTest" --tests "com.kidsphoneguard.service.guard.MiuiLauncherSensitiveHandlerTest" --tests "com.kidsphoneguard.service.guard.SensitiveActionGuardTest"
```

2. Run full checks:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

3. Device regression:

- 本应用打开通过。
- MIUI 桌面长按 / 卸载菜单仍被拦截。
- 真正卸载确认框仍被拦截。
- 应用商店 / 安装器危险路径仍被拦截。
- `com.xiaomi.market` 不出现 overlay 卡死回归。
- MIUI 应用列表页按 `ISSUE-011` 当前状态记录。

4. Closeout summary must state:

- which internal components were extracted.
- whether `ISSUE-011` was only isolated or also fixed.
- automated check results.
- device verification results.

## 8. Suggested Execution Order

Recommended order:

1. `SensitiveActionState`
2. `SensitiveActionExecutor`
3. shared detection snapshot and builder
4. `SensitiveConfirmDialogHandler`
5. `InstallerMarketSensitiveDetector`
6. `SettingsSensitiveDetector`
7. `LauncherSensitiveDetector`
8. `MiuiLauncherSensitiveHandler`
9. `ISSUE-011` fix only after logs
10. closeout

This order keeps the safest structural cleanup first and delays MIUI behavior changes until the relevant logic is isolated.

## 9. Completion Criteria

Phase 8 is complete when:

- `SensitiveActionGuard` no longer directly owns mutable state fields.
- `SensitiveActionGuard` no longer directly performs cancel-click node traversal.
- `SensitiveActionGuard` no longer directly schedules fast back / escape bursts.
- `SensitiveActionGuard.cancelPendingActions()` is either still a documented thin delegate for service lifecycle callers, or all callers have been explicitly migrated in a later composition change.
- snapshot construction lives in `SensitiveActionSnapshotBuilder`, and detectors consume `SensitiveActionSnapshot` instead of reading raw event/source nodes directly.
- confirmation dialog helpers live outside the guard.
- installer/market skip logic lives outside the guard.
- settings bypass logic lives outside the guard.
- launcher source classification lives outside the guard.
- MIUI launcher event-stage handling is isolated, or explicitly deferred with a documented reason tied to `ISSUE-011`.
- `GuardAccessibilityService` still injects only the top-level `SensitiveActionGuard` or a clearly named factory. It must not grow new direct dependencies on every sensitive-action subcomponent.
- all automated checks pass.
- device regression is recorded.

## 10. Constructor And Composition Rule

Phase 8 will introduce several internal collaborators. Keep dependency management conservative:

- Prefer default internal collaborators inside `SensitiveActionGuard` while preserving explicit constructor injection for existing Phase 1 seams.
- If the constructor becomes hard to read, introduce a small factory such as `SensitiveActionGuard.create(...)` or an internal collaborator holder.
- Do not make `GuardAccessibilityService` manually wire every detector/handler/executor unless there is a concrete test or lifecycle reason.
- The service should continue to see sensitive action as one route adapter, not a graph of low-level sensitive-action parts.

## 11. Review Checkpoints

Before implementing Task 7 or Task 8, review:

- whether current logs are enough to safely isolate MIUI launcher behavior.
- whether `ISSUE-011` should remain deferred or be fixed in the same branch.
- whether moving launcher code before fixing false positives would make debugging easier or harder.

If there is uncertainty, stop after Task 6. At that point state, execution, confirm dialog handling, installer/market detection, and settings detection can already be cleaner without touching the highest-risk MIUI branch.

Stopping after Task 6 is only a structural safety stop. If work stops there:

- do not attempt to fix `ISSUE-011` in the same partial phase.
- record that `MiuiLauncherSensitiveHandler` has not yet been extracted.
- delay `ISSUE-011` changes until Task 8 has established the launcher event-stage boundary, unless fresh evidence proves the fix is entirely inside settings detection and the user explicitly approves a narrower issue branch.
