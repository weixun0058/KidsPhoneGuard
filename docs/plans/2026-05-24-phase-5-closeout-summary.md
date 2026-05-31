# Phase 5 Closeout Summary

Source plan: `docs/plans/2026-05-24-phase-5-app-block-coordinator-extraction-plan.md`

Date: `2026-05-24`

## 1. Purpose

This document is the persistent closeout record for Phase 5.

It exists to answer the following without relying on chat history:

- What Phase 5 was intended to extract.
- What code was actually moved.
- What was verified.
- What remained intentionally out of scope.

## 2. Phase Goal

Phase 5 extracted the normal app blocking workflow out of `GuardAccessibilityService` into `AppBlockCoordinator` while preserving:

- Phase 3 router ownership and route order.
- Phase 4 protected-surface release timing constraints.
- Existing `GuardActionResult` semantics at router boundaries.
- Existing scheduler owner/key behavior for delayed normal block actions.

The intended outcome was:

- `AccessibilityEventRouter` still owns event classification and route ordering.
- `AppBlockCoordinator` owns normal app block orchestration.
- `BlockSessionController` remains the shared owner of overlay/block session state.
- `ProtectedSurfaceGuard` remains the owner of protected-surface policy and release checks.
- `GuardAccessibilityService` becomes thinner and keeps Android entrypoint responsibilities plus out-of-scope logic.

## 3. Delivered Code Artifacts

### New main code

- `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`

### Main modified code

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

### New tests

- `app/src/test/java/com/kidsphoneguard/service/block/AppBlockCoordinatorTest.kt`

## 4. Structural Outcome

Phase 5 structural goals are considered delivered.

Confirmed outcomes:

- `AppBlockCoordinator` now owns:
  - `handleBlockHold(...)`
  - `handleWhitelistWindowEvent(...)`
  - `launchNormalPolicyCheck(...)`
  - `checkPolicyAndExecute(...)`
  - `enforceBlock(...)`
  - normal deferred block actions
  - normal overlay release checks
  - normal foreground/window visibility helpers
  - `tryForceStopApp(...)`
  - `forceStopPermissionDenied`
- `GuardAccessibilityService` no longer contains the main normal-block method bodies listed above.
- `createAccessibilityEventRouterAdapters()` now routes normal block callbacks through `AppBlockCoordinator`.
- `AppBlockCoordinator` keeps a narrow callback bridge for protected-surface release timing:
  - it can ask whether a package is a protected surface
  - it can request protected release checks
  - it does not own protected-surface policy
- `BlockSessionController` remains the shared owner of:
  - block session state
  - overlay tracking state
  - release-check scheduling entrypoints
- `AccessibilityEventRouter` still owns:
  - event type classification
  - window route order
  - interaction route order
  - `GuardActionResult` stop/continue semantics

## 5. Verification Performed

### Automated verification

The following checks were run and passed during Phase 5 implementation/closeout:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Editor diagnostics were also checked on the touched files and no new diagnostics were introduced.

### Device verification

The latest device regression round reported the core Phase 5 baseline scenarios as passing:

- 普通拦截通过。
- 白名单通过。
- 应用商店通过。
- 基本测试都通过了。

This means the Phase 5 extraction did not introduce an immediate regression in the normal block route or in the Xiaomi market regression path that was guarded in Phase 4.

## 6. Behavior Guarantees Preserved

Phase 5 specifically preserved the following behavior constraints:

- Router order remains unchanged.
- Normal policy checks remain asynchronous.
- Protected-surface release checks are still not started early:
  - protected release starts after overlay show on the protected path
  - the normal block path still defers to protected release timing when the target package is a protected surface
- Existing delayed normal block actions still use the same scheduler owner/key model:
  - `SCHEDULER_OWNER_PENDING_BLOCK`
  - `SCHEDULER_OWNER_OVERLAY_RELEASE`

## 7. Out Of Scope And Deferred Items

Phase 5 does not claim to solve:

- `ISSUE-011` MIUI app list false positive in `SensitiveActionGuard`
- WeChat Finder extraction or behavior fixes
- Huawei/Honor power-save extraction or new behavior changes
- Broad permission/settings recognition fixes unrelated to a Phase 5 regression

These remain outside the phase unless a direct Phase 5 regression is later proven.

## 8. Issue Ledger Impact

No new Phase 5 regression issue was recorded during this closeout.

`issues_list.md` was intentionally not updated in this closeout because:

- no new issue status changed
- no previously fixed issue regressed in the reported validation round
- no new Phase 5-specific issue needed to be added

If later device validation reveals a regression directly caused by `AppBlockCoordinator` extraction, it should be logged separately and not merged back into this closeout summary retroactively without evidence.

## 9. Final Conclusion

Phase 5 is considered closed on the following basis:

- the normal app blocking workflow was extracted into `AppBlockCoordinator`
- router ownership boundaries were preserved
- shared block/overlay state was not duplicated
- protected-surface release timing remained protected
- automated checks passed
- the reported device regression round passed the core baseline scenarios

In one sentence:

> Phase 5 completed the normal app blocking extraction, preserved the intended routing and timing boundaries, and passed the current baseline verification without introducing a confirmed new regression.
