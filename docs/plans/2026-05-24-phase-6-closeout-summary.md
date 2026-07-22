# Phase 6 Closeout Summary

> **Acceptance reset (2026-07-21):** This is a historical implementation and automated-check record only. It does not establish final acceptance or close any mapped ledger issue. Current status is governed by `docs/ISSUES.md`; user manual acceptance is required. The explicit user product decision for ISS-008 remains in force.

> **Product-scope update (2026-07-21):** The Huawei/Honor validation gap and `ISSUE-005` `Deferred` status recorded below are historical Phase 6 facts, not current required work. The mapped ledger item `ISS-008` is now `WONTFIX` because the system “健康使用手机” capability overlaps the product requirement. Existing compatibility code remains, but it is not evidence of verified Huawei/Honor support.

Source plan: `docs/plans/2026-05-24-phase-6-oem-special-guard-extraction-plan.md`

Date: `2026-05-25`

## 1. Purpose

This document is the persistent closeout record for Phase 6.

It exists to answer the following without relying on chat history:

- What Phase 6 was intended to extract.
- What code was actually moved.
- What was verified.
- What remained intentionally out of scope or still open.

## 2. Phase Goal

Phase 6 extracted WeChat Finder handling and Huawei/Honor power-save handling out of `GuardAccessibilityService` while preserving the boundaries established in earlier phases.

The intended outcome was:

- `AccessibilityEventRouter` still owns event classification and route order.
- `WeChatFinderGuard` owns WeChat Finder detection/action behavior.
- `HuaweiPowerSaveHandler` owns power-save detection/action behavior.
- `ProtectedSurfaceGuard` keeps protected-surface policy ownership and only consumes a narrow visible power-save callback.
- `GuardAccessibilityService` becomes thinner and keeps Android entrypoint responsibilities plus explicitly out-of-scope logic such as assistant/gameassistant follow-up.

## 3. Delivered Code Artifacts

### New main code

- `app/src/main/java/com/kidsphoneguard/service/guard/WeChatFinderGuard.kt`
- `app/src/main/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandler.kt`

### Main modified code

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`

### New tests

- `app/src/test/java/com/kidsphoneguard/service/guard/WeChatFinderGuardTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/guard/oem/HuaweiPowerSaveHandlerTest.kt`

## 4. Structural Outcome

Phase 6 structural goals are considered delivered.

Confirmed outcomes:

- `WeChatFinderGuard` now owns:
  - WeChat Finder shape matching
  - WeChat Finder block orchestration
  - Finder overlay auto-release scheduling
- `HuaweiPowerSaveHandler` now owns:
  - power-save activity detection
  - visible power-save signal detection
  - node click traversal
  - burst gesture compensation
  - `lastPowerSaveExitAttemptTime`
- `GuardAccessibilityService` no longer contains the Phase 6 WeChat Finder and power-save main method bodies listed in the source plan.
- `AccessibilityEventRouter` still owns:
  - event type classification
  - window route order
  - interaction route order
  - `GuardActionResult` stop/continue semantics
- `ProtectedSurfaceGuard` still owns protected-surface policy and only depends on a narrow `exitVisiblePowerSaveModeIfNeeded(...)` callback.
- assistant/gameassistant follow-up remains service-owned by explicit Phase 6 scope decision and is not counted as a failure of this phase.

## 5. Verification Performed

### Automated verification

The following checks were run and passed during Phase 6 implementation/closeout:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lint
```

Additional targeted verification also passed:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest --tests "com.kidsphoneguard.service.guard.WeChatFinderGuardTest" --tests "com.kidsphoneguard.service.guard.oem.HuaweiPowerSaveHandlerTest" --tests "com.kidsphoneguard.service.accessibility.AccessibilityEventRouterTest"
```

Editor diagnostics were checked on touched files and no new diagnostics were introduced.

### Device verification

The latest Phase 6 device regression round produced the following results:

- 本应用打开通过。
- 普通拦截通过。
- 白名单通过。
- 应用商店通过。
- 应用卸载通过。
- 使用情况访问通过。
- 悬浮窗权限通过。
- 华为/荣耀在本轮未验证（历史回归结果；当前范围见文首 2026-07-21 更新）。
- 视频号入口不通过。
- 视频号详情不通过。

Interpretation:

- The normal-block baseline did not regress in confirmed device retest.
- No new Xiaomi market regression was reported in this round.
- WeChat Finder still fails, but this remained consistent with the pre-existing `ISSUE-004` problem shape.

## 6. Issues And Scope Decisions

### 6.1 WeChat Finder remains open

Phase 6 extracted the WeChat Finder logic into `WeChatFinderGuard`, but device retest still reported:

- 视频号入口不通过
- 视频号详情不通过

Fresh logs captured during closeout did **not** show `wechat_finder_block` or related hit markers.

Current interpretation:

- this is not evidence that Phase 6 broke the execution chain
- it remains consistent with the long-standing narrow Finder recognition problem already tracked as `ISSUE-004`

Therefore:

- `ISSUE-004` remains open
- it is not treated as a new Phase 6 regression blocker

### 6.2 Historical Phase 6 status: Huawei/Honor power-save was partially unverified

Phase 6 moved the power-save handling code into `HuaweiPowerSaveHandler`, but this round did not include Huawei/Honor device validation.

Interpretation at Phase 6 closeout:

- structural extraction is complete
- Xiaomi-side baseline did not reveal a new regression
- under the then-current scope, Huawei/Honor behavior would still have required real-device validation; the 2026-07-21 product decision removed this from current required scope

This was consistent with the historical `ISSUE-005` status at Phase 6 closeout. It is superseded for current planning by `ISS-008 = WONTFIX`.

## 7. Remaining Non-Blocking Risks

At Phase 6 closeout, the following were recorded as known non-blocking risks or deferred items:

- `ISSUE-004` remains `Open`:
  - WeChat Finder still fails
  - fresh logs suggest the old detection shape still misses
- `ISSUE-005` was `Deferred` at that time:
  - Huawei/Honor device validation was not available in this round; the mapped `ISS-008` was later changed to `WONTFIX` on 2026-07-21
- assistant/gameassistant follow-up remains in `GuardAccessibilityService` by explicit scope decision
- `HuaweiPowerSaveHandler` still uses existing Android APIs that emit deprecation warnings around `AccessibilityNodeInfo.recycle()` / `obtain(...)`, but this did not block compilation or lint

## 8. What Phase 6 Does Not Claim

Phase 6 closeout does not mean:

- WeChat Finder behavior is fully fixed
- Huawei/Honor power-save behavior is fully device-verified
- assistant/gameassistant follow-up was extracted
- unrelated sensitive-action false positives were resolved

Those remain outside this phase unless later proven to be a direct Phase 6 regression.

## 9. Issue Ledger Impact

`issues_list.md` was updated during Phase 6 closeout to reflect the latest understanding of `ISSUE-004`:

- the code ownership moved to `WeChatFinderGuard`
- fresh logs were captured during Phase 6 closeout
- the issue still appears to be old recognition coverage, not a newly confirmed Phase 6 regression

No new Phase 6-specific regression issue was added in this closeout.

## 10. Final Conclusion

Phase 6 is considered closed on the following basis:

- the planned structural extraction was completed
- `GuardAccessibilityService` was further reduced
- router ownership boundaries were preserved
- automated checks passed
- the normal-block baseline passed device retest
- the remaining WeChat Finder failure stayed within the explicitly allowed `ISSUE-004` open-item boundary defined by the Phase 6 plan

In one sentence:

> At Phase 6 closeout, the OEM/special guard extraction was complete and the pre-existing Finder and Huawei/Honor validation gaps were recorded as open; their later dispositions are governed by the current issue ledger.
