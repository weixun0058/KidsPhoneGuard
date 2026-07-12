package com.kidsphoneguard.ui.config

import java.util.Locale

object TimeWindowCodec {
    private const val DAY_MINUTES = 24 * 60
    private const val DEFAULT_START = 22 * 60
    private const val DEFAULT_END = 7 * 60

    fun normalize(minutes: Int): Int = ((minutes % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES

    fun parseRange(windows: String): Pair<Int, Int> {
        val range = windows.split(",").map(String::trim).firstOrNull { '-' in it }
            ?: return DEFAULT_START to DEFAULT_END
        val parts = range.split("-")
        if (parts.size != 2) return DEFAULT_START to DEFAULT_END
        return (parseTime(parts[0].trim()) ?: DEFAULT_START) to
            (parseTime(parts[1].trim()) ?: DEFAULT_END)
    }

    fun format(minutes: Int): String {
        val normalized = normalize(minutes)
        return String.format(Locale.US, "%02d:%02d", normalized / 60, normalized % 60)
    }

    fun formatRange(startMinutes: Int, endMinutes: Int): String =
        "${format(startMinutes)}-${format(endMinutes)}"

    private fun parseTime(value: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }
}
