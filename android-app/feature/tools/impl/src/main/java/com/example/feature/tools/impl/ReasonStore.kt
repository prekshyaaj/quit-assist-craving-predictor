package com.example.feature.tools.impl

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ReasonType { TEXT, IMAGE, AUDIO }

/** One "why I quit" reason. For IMAGE / AUDIO, [path] is a file inside app storage. */
data class Reason(
    val id: Long,
    val type: ReasonType,
    val text: String = "",
    val path: String = "",
)

/**
 * Stores the user's reasons on the phone only: the list in SharedPreferences (JSON),
 * photos and voice notes as files in the app's private storage (files/why_i_quit/).
 */
class ReasonStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val dir: File = File(appContext.filesDir, "why_i_quit").apply { mkdirs() }

    /** Newest first. */
    fun load(): List<Reason> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reason(
                    id = o.getLong("id"),
                    type = ReasonType.valueOf(o.getString("type")),
                    text = o.optString("text"),
                    path = o.optString("path"),
                )
            }.sortedByDescending { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(reasons: List<Reason>) {
        val arr = JSONArray()
        reasons.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("type", r.type.name)
                    .put("text", r.text)
                    .put("path", r.path),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Copies a picked photo into app storage (the picker's Uri doesn't last). */
    fun copyImage(uri: Uri, id: Long): String? = try {
        val out = File(dir, "img_$id.jpg")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        out.absolutePath
    } catch (_: Exception) {
        null
    }

    fun newAudioFile(id: Long): File = File(dir, "audio_$id.m4a")

    fun deleteFile(reason: Reason) {
        if (reason.path.isNotEmpty()) File(reason.path).delete()
    }

    private companion object {
        const val PREFS = "why_i_quit"
        const val KEY = "reasons"
    }
}
