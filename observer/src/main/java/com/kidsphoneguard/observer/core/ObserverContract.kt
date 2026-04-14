package com.kidsphoneguard.observer.core

object ObserverContract {
    const val targetPackageName = "com.kidsphoneguard"
    const val targetAccessibilityService = "com.kidsphoneguard/com.kidsphoneguard.service.GuardAccessibilityService"
    const val observerWatchdogAction = "com.kidsphoneguard.observer.action.OBSERVER_WATCHDOG"
    const val guardStatusAction = "com.kidsphoneguard.observer.action.GUARD_STATUS"
    const val receiveGuardStatusPermission = "com.kidsphoneguard.observer.permission.RECEIVE_GUARD_STATUS"
    const val diagnosticsPrefsName = "observer_diagnostics"
    const val statePrefsName = "observer_state"
    const val keyLastPersistAt = "last_persist_at"
    const val keyLastPersistEvent = "last_persist_event"
    const val keyLatestSummary = "latest_summary"
    const val keyLatestEventAt = "latest_event_at"
    const val keyLatestSource = "latest_source"
    const val keyLastMainHeartbeatAt = "last_main_heartbeat_at"
    const val keyLastMainHeartbeatSource = "last_main_heartbeat_source"
    const val keyLastBridgeSummary = "last_bridge_summary"
    const val observerIntervalMs = 10_000L
    const val observerWatchdogIntervalMs = 10 * 60 * 1000L
    const val mainHeartbeatTimeoutMs = 45_000L
}
