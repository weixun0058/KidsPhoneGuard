package com.kidsphoneguard.observer.core

data class ObserverSnapshot(
    val source: String,
    val eventAt: Long,
    val accessibilityGlobalEnabled: Boolean,
    val targetServiceListed: Boolean,
    val targetProcessRunning: Boolean,
    val targetPackageInstalled: Boolean,
    val targetPackageEnabled: Boolean,
    val usageAccessGranted: Boolean,
    val mainHeartbeatFresh: Boolean,
    val mainHeartbeatAgeMs: Long,
    val inferredMainStopped: Boolean,
    val mainBridgeSummary: String
)
