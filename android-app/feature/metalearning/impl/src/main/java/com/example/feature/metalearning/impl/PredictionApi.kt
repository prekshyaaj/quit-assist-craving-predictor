package com.example.feature.metalearning.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Static answers from the questionnaire (NHIS codes). */
internal data class DemoProfile(
    val age: Int?,
    val sex: Int?,
    val cigarettesPerDay: Double?,
    val employmentStatus: Int?,
)

/** What the phone's sensors would report; entered by hand for the demo. */
internal data class LiveContext(
    val hour: Int,
    val minute: Int,
    val location: String,
    /** null = no smartwatch reading. */
    val heartRateBpm: Int?,
    val isSleeping: Boolean,
    val minutesSinceLastCigarette: Int,
) {
    val timeText: String get() = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun summary(): String = buildString {
        append(timeText).append(" · ").append(location)
        append(" · ").append(if (isSleeping) "asleep" else heartRateBpm?.let { "$it bpm" } ?: "no watch")
        append(" · last cig ").append(minutesSinceLastCigarette).append(" min")
    }
}

internal data class PredictionResult(
    val probability: Double,
    val threshold: Double,
    val willSmoke: Boolean,
    /** "saved_model" or "cold_start" */
    val mode: String,
    val modelUpdatedAt: String?,
)

internal data class AdaptResult(
    val rowsUsed: Int,
    val updatedAt: String,
    /** Resting HR (bpm) the server worked out from day 1; null if not returned. */
    val restingHr: Double?,
)

/** One model's scores on the user's days after day 1 (rates are 0-1). */
internal data class EvalMetrics(
    val caught: Int,
    val events: Int,
    val falseAlarms: Int,
    val recall: Double,
    val precision: Double,
    val macroF1: Double,
    val threshold: Double,
)

/** GET /evaluate: cold-start vs this user's saved personal model on their own data. */
internal data class EvaluationResult(
    val testSlots: Int,
    val smokingEvents: Int,
    val cold: EvalMetrics,
    /** null until the user has run Adapt. */
    val personalised: EvalMetrics?,
    val modelUpdatedAt: String?,
)

internal class PredictionApiException(
    val statusCode: Int,
    val detail: String,
) : IOException("Prediction API error ($statusCode): $detail")

/** Client for deploy/server.py in few_shot_meta_learning. */
internal class PredictionApi(private val token: String) {

    /** General (meta-learned) model, no personal data. No login needed. */
    suspend fun predictColdStart(profile: DemoProfile, live: LiveContext, restingHr: Double): PredictionResult =
        parsePrediction(post("/predict_coldstart", predictBody(profile, live, restingHr)))

    /**
     * The server reads this user's day 1 from Supabase sensor_data, personalises the model and
     * saves it in public.users.personal_model.
     */
    suspend fun adapt(): AdaptResult {
        val json = JSONObject(post("/adapt", JSONObject()))
        return AdaptResult(
            rowsUsed = json.optInt("history_rows_used"),
            updatedAt = json.optString("personal_model_updated_at"),
            restingHr = if (json.has("resting_hr") && !json.isNull("resting_hr")) json.getDouble("resting_hr") else null,
        )
    }

    /** Deletes this user's saved personal model, so Adapt can be shown again from scratch. */
    suspend fun deletePersonalModel() {
        send("DELETE", "/personal_model", null)
    }

    /** Uses this user's saved personal model. 404 = /adapt hasn't been run for this user. */
    suspend fun predictAdapted(profile: DemoProfile, live: LiveContext, restingHr: Double?): PredictionResult =
        parsePrediction(post("/predict_adapted", predictBody(profile, live, restingHr)))

    /**
     * Scores this user's sensor_data after day 1 with both models (recall, precision, macro F1).
     * Personalised is null if Adapt hasn't been run. Nothing is saved.
     */
    suspend fun evaluate(): EvaluationResult {
        val json = JSONObject(send("GET", "/evaluate", null))
        fun metrics(o: JSONObject) = EvalMetrics(
            caught = o.optInt("smoking_caught"),
            events = o.optInt("smoking_events"),
            falseAlarms = o.optInt("false_alarms"),
            recall = o.optDouble("recall"),
            precision = o.optDouble("precision"),
            macroF1 = o.optDouble("macro_f1"),
            threshold = o.optDouble("threshold", 0.35),
        )
        return EvaluationResult(
            testSlots = json.optInt("test_slots"),
            smokingEvents = json.optInt("smoking_events"),
            cold = metrics(json.getJSONObject("cold_start")),
            personalised = json.optJSONObject("personalised")?.let { metrics(it) },
            modelUpdatedAt = if (json.isNull("personal_model_updated_at")) null
            else json.optString("personal_model_updated_at"),
        )
    }

    private fun predictBody(profile: DemoProfile, live: LiveContext, restingHr: Double?): JSONObject {
        // No watch: send the resting HR (delta 0). Asleep without a reading: resting - 15, like the training data.
        val resting = restingHr ?: PredictionConfig.DEFAULT_RESTING_HR
        val hr = live.heartRateBpm?.toDouble() ?: if (live.isSleeping) resting - 15.0 else resting
        val current = JSONObject().apply {
            put("time", live.hour + live.minute / 60.0)
            put("age", profile.age)
            put("sex", profile.sex)
            put("cigs_per_day", profile.cigarettesPerDay)
            put("emp_stat", profile.employmentStatus)
            put("is_sleeping", if (live.isSleeping) 1 else 0)
            put("location", live.location)
            put("tsls", live.minutesSinceLastCigarette)
            put("hr", hr)
        }
        return JSONObject().apply {
            put("current", current)
            if (restingHr != null) put("resting_hr", restingHr)
        }
    }

    private fun parsePrediction(text: String): PredictionResult {
        val json = JSONObject(text)
        return PredictionResult(
            probability = json.getDouble("probability"),
            threshold = json.optDouble("threshold", 0.35),
            willSmoke = json.optBoolean("will_smoke"),
            mode = json.optString("mode"),
            modelUpdatedAt = if (json.isNull("personal_model_updated_at")) null
            else json.optString("personal_model_updated_at"),
        )
    }

    private suspend fun post(path: String, body: JSONObject): String = send("POST", path, body)

    private suspend fun send(method: String, path: String, body: JSONObject?): String = withContext(Dispatchers.IO) {
        val connection = URL("${PredictionConfig.BASE_URL}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000 // the server sleeps when idle; first request can take 5-10 s
            connection.setRequestProperty("Content-Type", "application/json")
            if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (PredictionConfig.API_KEY.isNotEmpty()) connection.setRequestProperty("X-API-Key", PredictionConfig.API_KEY)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = runCatching { JSONObject(error).get("detail").toString() }.getOrNull()
                throw PredictionApiException(code, detail ?: error)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
