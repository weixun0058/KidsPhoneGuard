package com.kidsphoneguard.observer.core

import android.content.Context
import java.io.File

object ObserverLogStore {
    private val fileLock = Any()
    private const val logDirName = "observer-forensics"
    private const val logFileName = "guard_observer.log"
    private const val backupLogFileName = "guard_observer.prev.log"
    private const val maxLogBytes = 2 * 1024 * 1024L

    fun appendLine(context: Context, event: String, payload: String) {
        synchronized(fileLock) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(ObserverContract.diagnosticsPrefsName, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val line = "$now|$event|$payload\n"
            val persistedToFile = resolveCandidateDirs(appContext)
                .map { rootDir -> File(rootDir, logFileName) }
                .any { logFile -> appendToFile(logFile, line) }
            prefs.edit()
                .putLong(ObserverContract.keyLastPersistAt, now)
                .putString(ObserverContract.keyLastPersistEvent, event)
                .putString("last_persist_storage", if (persistedToFile) "file" else "prefs_only")
                .apply()
        }
    }

    fun persistSnapshot(context: Context, snapshot: ObserverSnapshot) {
        val summary = buildSummary(snapshot)
        context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(ObserverContract.keyLatestSummary, summary)
            .putLong(ObserverContract.keyLatestEventAt, snapshot.eventAt)
            .putString(ObserverContract.keyLatestSource, snapshot.source)
            .apply()
        appendLine(context, "observer_snapshot", summary)
    }

    fun persistMainBridgeHeartbeat(
        context: Context,
        source: String,
        eventAt: Long,
        summary: String
    ) {
        context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .edit()
            .putLong(ObserverContract.keyLastMainHeartbeatAt, eventAt)
            .putString(ObserverContract.keyLastMainHeartbeatSource, source)
            .putString(ObserverContract.keyLastBridgeSummary, summary)
            .apply()
        appendLine(context, "main_guard_bridge", "source=$source|eventAt=$eventAt|$summary")
    }

    fun readLatestSummary(context: Context): String {
        return context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .getString(ObserverContract.keyLatestSummary, "暂无旁路监控快照")
            .orEmpty()
    }

    fun readLatestEventAt(context: Context): Long {
        return context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .getLong(ObserverContract.keyLatestEventAt, 0L)
    }

    fun readLastHeartbeatAt(context: Context): Long {
        return context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .getLong(ObserverContract.keyLastMainHeartbeatAt, 0L)
    }

    fun readLastHeartbeatSource(context: Context): String {
        return context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .getString(ObserverContract.keyLastMainHeartbeatSource, "none")
            .orEmpty()
    }

    fun readLastBridgeSummary(context: Context): String {
        return context.getSharedPreferences(ObserverContract.statePrefsName, Context.MODE_PRIVATE)
            .getString(ObserverContract.keyLastBridgeSummary, "暂无主应用桥接快照")
            .orEmpty()
    }

    fun readLastPersistStorage(context: Context): String {
        return context.getSharedPreferences(ObserverContract.diagnosticsPrefsName, Context.MODE_PRIVATE)
            .getString("last_persist_storage", "unknown")
            .orEmpty()
    }

    private fun buildSummary(snapshot: ObserverSnapshot): String {
        return listOf(
            "source=${snapshot.source}",
            "eventAt=${snapshot.eventAt}",
            "globalAe=${snapshot.accessibilityGlobalEnabled}",
            "serviceListed=${snapshot.targetServiceListed}",
            "processRunning=${snapshot.targetProcessRunning}",
            "pkgInstalled=${snapshot.targetPackageInstalled}",
            "pkgEnabled=${snapshot.targetPackageEnabled}",
            "usageAccess=${snapshot.usageAccessGranted}",
            "heartbeatFresh=${snapshot.mainHeartbeatFresh}",
            "heartbeatAgeMs=${snapshot.mainHeartbeatAgeMs}",
            "inferredStopped=${snapshot.inferredMainStopped}",
            "bridge=${snapshot.mainBridgeSummary}"
        ).joinToString("|")
    }

    private fun resolveCandidateDirs(context: Context): List<File> {
        val dirs = linkedSetOf<File>()
        dirs += File(context.filesDir, logDirName)
        context.getExternalFilesDir(logDirName)?.let { dirs += it }
        dirs.forEach { rootDir ->
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
        }
        return dirs.toList()
    }

    private fun appendToFile(logFile: File, line: String): Boolean {
        return runCatching {
            rotateIfNeeded(logFile)
            logFile.appendText(line)
            true
        }.getOrDefault(false)
    }

    private fun rotateIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() <= maxLogBytes) {
            return
        }
        val backupFile = File(logFile.parentFile, backupLogFileName)
        if (backupFile.exists()) {
            backupFile.delete()
        }
        logFile.renameTo(backupFile)
    }
}
