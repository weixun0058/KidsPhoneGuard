# Phase 4 Closeout Summary

> **Acceptance reset (2026-07-21):** This is a historical implementation and automated-check record only. It does not establish final acceptance or close any mapped ledger issue. Current status is governed by `docs/ISSUES.md`; user manual acceptance is required.

Source plan: `docs/plans/2026-05-24-phase-4-protected-surface-guard-extraction-plan.md`

Date: `2026-05-24`

## 1. Purpose

This document is the persistent closeout record for Phase 4.

It exists to answer four questions without depending on chat history:

- What Phase 4 was supposed to do.
- What code was actually changed.
- What was verified.
- What was intentionally left out of scope or deferred.

## 2. Phase Goal

Phase 4 extracted protected-surface-related behavior out of `GuardAccessibilityService` while preserving Phase 3 router ownership.

The intended outcome was:

- `AccessibilityEventRouter` continues to own event classification and route order.
- `ProtectedSurfaceGuard` owns protected settings / protected window / suppression orchestration.
- `SystemSurfaceGuard` owns system panel collapse behavior.
- `ProtectedSurfaceState` owns protected-surface-specific cooldown and logging state.
- `GuardAccessibilityService` becomes thinner and keeps only Android entrypoint, adapter wiring, and out-of-scope behavior.

## 3. Delivered Code Artifacts

### New main code

- `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceState.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuard.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/SystemSurfaceGuard.kt`

### Main modified code

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouter.kt`

### New or updated tests

- `app/src/test/java/com/kidsphoneguard/service/guard/ProtectedSurfaceStateTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/ProtectedSurfaceGuardTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/SystemSurfaceGuardTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/AccessibilityEventRouterTest.kt`

## 4. Structural Outcome

Phase 4 structural goals are considered delivered.

Confirmed outcomes:

- `ProtectedSurfaceGuard` now owns:
  - protected settings candidate handling
  - settings snapshot construction
  - protected settings decision orchestration
  - protected interactive window scanning
  - protected surface suppression
  - protected overlay release checks
- `SystemSurfaceGuard` now owns:
  - system panel package recognition
  - system panel signal collection
  - system panel collapse behavior
- `lastSystemPanelCollapseTime` no longer lives in `GuardAccessibilityService`.
- `GuardAccessibilityService` no longer contains the large protected-surface/system-surface method bodies that were moved in this phase.
- Guard-local `collectNodeSignals(...)` / `appendSignal(...)` implementations were kept separate, so the two guards do not depend on each other.
- `AccessibilityEventRouter` still owns:
  - event type classification
  - window route order
  - interaction route order
  - `GuardActionResult` stop/continue semantics

## 5. Verification Performed

### Automated verification

The following checks were run during Phase 4 closeout and passed:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Editor diagnostics were also checked on the touched files and no new diagnostics were introduced.

### Device verification

The following device scenarios were verified as passing during Phase 4 closeout:

- 本应用打开通过。
- 应用商店拦截成功。
- 普通拦截成功。
- 白名单成功。
- 应用卸载拦截成功。
- 使用情况访问仍能拦截。
- 悬浮窗权限能拦截。
- `com.xiaomi.market` 不出现遮蔽层卡死回归。

Observed but not treated as Phase 4 blockers:

- 应用卸载拦截成功，但速度偏慢。

## 6. Issues Encountered During Phase 4

### 6.1 `com.xiaomi.market` overlay release regression

During Phase 4, `com.xiaomi.market` briefly regressed:

- the overlay could appear
- but release checks were starting too early in one path
- which could lead to the overlay not auto-releasing

Fix applied in Phase 4:

- `ProtectedSurfaceGuard.suppressProtectedSystemSurface(...)` was changed so protected release checks start only after `showOverlay(...)` is actually posted.
- A follow-up P2 closeout patch also tightened the normal `enforceBlock(...)` path so protected-surface release checks are not started early there either.

Current status:

- device retest passed
- the issue remains `Watch` in `issues_list.md` because it is timing-sensitive and spans multiple chains

### 6.2 MIUI app list false positive

Another issue was discovered during Phase 4 validation:

- scrolling to `拉钩守护` inside MIUI app list / app settings could trigger immediate exit

This was investigated and classified as:

- **not a Phase 4 regression**
- caused by `SensitiveActionGuard`
- tracked separately in `issues_list.md` as `ISSUE-011`

It was intentionally not mixed into Phase 4 closeout.

## 7. Remaining Non-Blocking Risks

Phase 4 is closed with the following known non-blocking risks or deferred items:

- `ISSUE-007` remains `Watch`:
  - fixed and retested
  - still sensitive to timing/order changes
- `ISSUE-011` remains open:
  - MIUI app list false positive
  - not part of Phase 4 scope
- test coverage is still lighter than ideal for:
  - system panel cooldown state
  - release check timing
  - router adapter integration around Android node/window behavior

These are considered acceptable for Phase 4 closeout because the phase goal was structural decoupling, not broad behavior cleanup across unrelated modules.

## 8. What Phase 4 Does Not Claim

Phase 4 closeout does not mean:

- all settings-related issues are solved
- all permission pages are now correct
- `SensitiveActionGuard` false positives are solved
- WeChat Finder behavior is solved
- OEM-specific power-save behavior is universally verified

Those remain outside this phase unless explicitly tracked as a Phase 4 regression.

## 9. Final Conclusion

Phase 4 is considered closed on the following basis:

- structural extraction goals were completed
- router ownership boundaries were preserved
- automated checks passed
- core device scenarios passed
- the temporary `com.xiaomi.market` regression introduced during the phase was fixed and retested
- non-Phase-4 issues were identified, separated, and written to `issues_list.md`

In one sentence:

> Phase 4 achieved its decoupling goal, preserved the intended runtime routing boundaries, passed closeout verification, and left only explicitly documented out-of-scope or watch-list items behind.
