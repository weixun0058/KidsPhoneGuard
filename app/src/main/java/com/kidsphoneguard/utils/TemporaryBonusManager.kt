package com.kidsphoneguard.utils

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TemporaryBonusManager private constructor(context: Context) {

    companion object {
        private const val TAG = "TemporaryBonusManager"
        private const val PREF_NAME = "temporary_bonus_prefs"
        private const val KEY_PREFIX = "bonus|"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        @Volatile
        private var instance: TemporaryBonusManager? = null

        fun getInstance(context: Context): TemporaryBonusManager {
            return instance ?: synchronized(this) {
                instance ?: TemporaryBonusManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getTodayBonusSeconds(packageName: String): Long {
        cleanupOldBonuses()
        return prefs.getLong(todayKey(packageName), 0L).coerceAtLeast(0L)
    }

    fun getTodayBonusMap(packageNames: Collection<String>): Map<String, Long> {
        cleanupOldBonuses()
        return packageNames.associateWith { getTodayBonusSeconds(it) }
    }

    fun addTodayBonusMinutes(packageName: String, minutes: Int): Long {
        val bonusSeconds = (minutes.coerceAtLeast(0) * 60L)
        if (packageName.isBlank() || bonusSeconds <= 0L) {
            return getTodayBonusSeconds(packageName)
        }

        val key = todayKey(packageName)
        val updatedSeconds = (prefs.getLong(key, 0L).coerceAtLeast(0L) + bonusSeconds).coerceAtLeast(0L)
        prefs.edit().putLong(key, updatedSeconds).apply()
        Log.d(TAG, "add_today_bonus package=$packageName minutes=$minutes totalSeconds=$updatedSeconds")
        return updatedSeconds
    }

    fun clearTodayBonus(packageName: String) {
        if (packageName.isBlank()) {
            return
        }
        prefs.edit().remove(todayKey(packageName)).apply()
        Log.d(TAG, "clear_today_bonus package=$packageName")
    }

    private fun todayKey(packageName: String): String {
        return "$KEY_PREFIX${todayDate()}|$packageName"
    }

    private fun todayDate(): String {
        return LocalDate.now().format(DATE_FORMATTER)
    }

    private fun cleanupOldBonuses() {
        val todayPrefix = "$KEY_PREFIX${todayDate()}|"
        val oldKeys = prefs.all.keys.filter { key ->
            key.startsWith(KEY_PREFIX) && !key.startsWith(todayPrefix)
        }
        if (oldKeys.isEmpty()) {
            return
        }

        val editor = prefs.edit()
        oldKeys.forEach { editor.remove(it) }
        editor.apply()
    }
}
