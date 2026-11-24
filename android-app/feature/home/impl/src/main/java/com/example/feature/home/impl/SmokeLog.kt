package com.example.feature.home.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.LocalDate

/**
 * Cigarettes smoked per day, stored on this phone (SharedPreferences).
 * Filled by the Home "I smoked" button; past days can be corrected from the My week card.
 *
 * Tracking starts the first day the app is opened. A tracked day with no log counts as smoke-free.
 * Values are Compose state, so the Home screen and the top bar update by themselves.
 */
object SmokeLog {
    private const val PREFS = "smoke_log"
    private const val KEY_COUNTS = "counts"      // {"2026-11-19": 3, ...}
    private const val KEY_START = "start_date"   // first tracked day

    private var prefs: SharedPreferences? = null

    var counts by mutableStateOf<Map<LocalDate, Int>>(emptyMap())
        private set
    var startDate by mutableStateOf(LocalDate.now())
        private set

    /** Safe to call many times; loads once. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        startDate = p.getString(KEY_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now().also { p.edit().putString(KEY_START, it.toString()).apply() }
        counts = runCatching {
            val o = JSONObject(p.getString(KEY_COUNTS, "{}") ?: "{}")
            o.keys().asSequence().associate { LocalDate.parse(it) to o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    fun countOn(date: LocalDate): Int = counts[date] ?: 0

    /** "I smoked": +1 for today. Returns today's new count. */
    fun logCigarette(today: LocalDate = LocalDate.now()): Int {
        setCount(today, countOn(today) + 1)
        return countOn(today)
    }

    /** Correct a past day (or today). Editing a day before tracking started moves the start back. */
    fun setCount(date: LocalDate, count: Int) {
        if (date.isAfter(LocalDate.now())) return
        counts = counts + (date to count.coerceAtLeast(0))
        if (date.isBefore(startDate)) startDate = date
        save()
    }

    fun isTracked(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
        !date.isBefore(startDate) && !date.isAfter(today)

    /** Consecutive smoke-free days up to today (today counts while it is still at 0). */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var d = today
        var n = 0
        while (isTracked(d, today) && countOn(d) == 0) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** One coin for every finished (before today) smoke-free day since tracking started. */
    fun soberCoins(today: LocalDate = LocalDate.now()): Int {
        var d = startDate
        var n = 0
        while (d.isBefore(today)) {
            if (countOn(d) == 0) n++
            d = d.plusDays(1)
        }
        return n
    }

    private fun save() {
        val o = JSONObject()
        counts.forEach { (date, c) -> o.put(date.toString(), c) }
        prefs?.edit()
            ?.putString(KEY_COUNTS, o.toString())
            ?.putString(KEY_START, startDate.toString())
            ?.apply()
    }
}
