package com.example.feature.login.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val SERVER_URL = "https://chatbot-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io"
private const val API_KEY = "327498c766af73190d71289528726d00fc6aec3e5db74db7"

data class QuestionnaireData(
    val age: Int? = null,
    val sex: Int? = null,
    val cigarettesNow: Double? = null,
    val employmentStatus: Int? = null,
) {
    val isComplete: Boolean
        get() = age != null && sex != null && cigarettesNow != null && employmentStatus != null
}

class QuestionnaireApiException(
    val statusCode: Int,
    detail: String,
) : IOException("Questionnaire API error ($statusCode): $detail")

object QuestionnaireApi {

    suspend fun getQuestionnaire(token: String): QuestionnaireData =
        parse(request("GET", token))

    suspend fun saveQuestionnaire(token: String, data: QuestionnaireData): QuestionnaireData {
        val body = JSONObject().apply {
            data.age?.let { put("AGEP_A", it) }
            data.sex?.let { put("SEX_A", it) }
            data.cigarettesNow?.let { put("CIGNOW_A", it) }
            data.employmentStatus?.let { put("EMP_STAT", it) }
        }
        return parse(request("POST", token, body.toString()))
    }

    private fun parse(text: String): QuestionnaireData {
        val json = JSONObject(text)
        fun JSONObject.intOrNull(key: String) = if (isNull(key)) null else getInt(key)
        fun JSONObject.doubleOrNull(key: String) = if (isNull(key)) null else getDouble(key)
        return QuestionnaireData(
            age = json.intOrNull("AGEP_A"),
            sex = json.intOrNull("SEX_A"),
            cigarettesNow = json.doubleOrNull("CIGNOW_A"),
            employmentStatus = json.intOrNull("EMP_STAT"),
        )
    }

    private suspend fun request(method: String, token: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = URL("$SERVER_URL/questionnaire").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-API-Key", API_KEY)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw QuestionnaireApiException(code, detail)
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}
