# Phase 7 Closeout Summary

Source plan: `docs/plans/2026-05-24-phase-7-service-composition-runtime-cleanup-plan.md`

This document records the Phase 7 implementation state after completing the remaining code work in the current workspace.

## 1. Scope

Phase 7 targeted the overall decoupling plan's Task 8: reduce `GuardAccessibilityService` to an Android lifecycle entrypoint and composition root.

This phase did not attempt to fix:

- `ISSUE-004` WeChat Finder recognition.
- `ISSUE-005` Huawei/Honor power-save device validation.
- protected settings keyword or speed tuning.
- normal app blocking policy changes.

## 2. Delivered Code Artifacts

### New main code

- `app/src/main/java/com/kidsphoneguard/service/accessibility/AssistantOverlayRoutingSupport.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/SelfAppEventHandler.kt`
- `app/src/main/java/com/kidsphoneguard/service/accessibility/ServiceRuntimeSupport.kt`
- `app/src/main/java/com/kidsphoneguard/service/block/LockDecisionEngineProvider.kt`

### Modified main code

- `app/src/main/java/com/kidsphoneguard/service/GuardAccessibilityService.kt`
- `app/src/main/java/com/kidsphoneguard/service/block/AppBlockCoordinator.kt`

### New tests

- `app/src/test/java/com/kidsphoneguard/service/accessibility/AssistantOverlayRoutingSupportTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/SelfAppEventHandlerTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/accessibility/ServiceRuntimeSupportTest.kt`
- `app/src/test/java/com/kidsphoneguard/service/block/LockDecisionEngineProviderTest.kt`

## 3. Structural Outcome

Confirmed code-level outcomes:

- Assistant/gameassistant package mapping and follow-up scheduling moved to `AssistantOverlayRoutingSupport`.
- Assistant follow-up still reuses the router-owned `EventRoutingState`.
- Self-app route cleanup moved to `SelfAppEventHandler`.
- Service runtime responsibilities moved to `ServiceRuntimeSupport`:
  - event signal throttling
  - accessibility settings snapshot logging
  - heartbeat scheduling
  - protected window sweep scheduling
  - internal block receiver ownership
- `LockDecisionEngine` initialization and access moved to `LockDecisionEngineProvider`.
- `AppBlockCoordinator` no longer depends on a service-owned `LockDecisionEngine` field.
- `GuardAccessibilityService` keeps lifecycle callbacks, composition wiring, and the thin `latestLifecycleSignal` write callback.

## 4. Boundary Decisions Preserved

- `latestLifecycleSignal` remains composition-root-owned through `GuardAccessibilityService.publishLifecycleSignal(...)`.
- `ServiceRuntimeSupport` only receives the lifecycle signal callback and does not own global lifecycle signal state.
- Feature guards such as `ProtectedSurfaceGuard` and `WeChatFinderGuard` still receive the lifecycle signal callback directly from the service composition root, not through `ServiceRuntimeSupport`.
- `blockAppReceiver` registration remains attached to the delayed `SCHEDULER_OWNER_SERVICE_INIT` / `initialize` / `100L` initialization path via `initializeService()`.
- `initializeService()` is now a thin wrapper that calls `LockDecisionEngineProvider.initialize()` and `ServiceRuntimeSupport.registerBlockReceiverForServiceInit()`.

## 5. Automated Verification

The following checks were run and passed:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kidsphoneguard.service.accessibility.ServiceRuntimeSupportTest" --tests "com.kidsphoneguard.service.accessibility.AssistantOverlayRoutingSupportTest" --tests "com.kidsphoneguard.service.accessibility.SelfAppEventHandlerTest" --tests "com.kidsphoneguard.service.block.LockDecisionEngineProviderTest"
```

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest :app:lint
```

Result:

- `BUILD SUCCESSFUL`

## 6. Device Verification Status

Device regression validation has not been performed in this implementation pass.

Before Phase 7 is treated as fully device-closed, verify:

- 本应用打开通过。
- 普通拦截通过。
- 白名单通过。
- 应用商店拦截通过，且 `com.xiaomi.market` 不出现 overlay 卡死回归。
- 应用卸载拦截通过。
- 使用情况访问拦截通过。
- 悬浮窗权限拦截通过。
- 无障碍权限设置记录结果，不在本阶段混修。
- 微信视频号入口/详情记录结果，不在本阶段混修 `ISSUE-004`。
- 华为/荣耀省电模式如有设备则验证；无设备则保留 `ISSUE-005`。

## 7. Issue Ledger

`issues_list.md` was not changed during this Phase 7 implementation pass.

No new Phase 7-specific issue was identified by automated tests or lint.

## 8. Closeout Position

Phase 7 is code-complete and automated-check-complete in the current workspace.

It should be considered fully closed only after the device regression checklist in this document is confirmed.

