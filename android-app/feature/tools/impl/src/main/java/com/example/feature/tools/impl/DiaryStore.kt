package com.example.feature.tools.impl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One diary entry. Ids are creation timestamps (ms). */
data class DiaryEntry(
    val id: Long,
    val title: String,
    val text: String,
    val updatedAt: Long,
)

/**
 * Stores diary entries on the phone only (SharedPreferences, as JSON).
 * No server, no login needed. Entries are lost if app data is cleared.
 */
class DiaryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** All entries, most recently edited first. */
    fun load(): List<DiaryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DiaryEntry(
                    id = o.getLong("id"),
                    title = o.optString("title"),
                    text = o.optString("text"),
                    updatedAt = o.optLong("updatedAt"),
                )
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<DiaryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("text", e.text)
                    .put("updatedAt", e.updatedAt),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val PREFS = "diary"
        const val KEY = "entries"
    }
}
