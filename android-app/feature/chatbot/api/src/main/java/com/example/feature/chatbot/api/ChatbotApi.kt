package com.example.feature.chatbot.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private fun getBaseUrl(): String = "https://chatbot-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io"

private const val API_KEY = "327498c766af73190d71289528726d00fc6aec3e5db74db7"

data class ChatResponse(
    val sessionId: String,
    val reply: String,
)

data class ChatSession(
    val id: String,
    val status: String,
    val startedAt: String,
    val endedAt: String?,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
)

class ChatbotApiException(
    val statusCode: Int,
    val detail: String,
) : IOException("Chatbot API Error ($statusCode): $detail")

object ChatbotApi {

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = request("/health")
            JSONObject(text).optString("status") == "ok"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendMessage(
        token: String,
        message: String,
        sessionId: String? = null,
    ): ChatResponse = withContext(Dispatchers.IO) {
        val reqJson = JSONObject().apply {
            put("message", message)
            if (!sessionId.isNullOrEmpty()) put("session_id", sessionId)
        }
        val resJson = JSONObject(request("/chat", "POST", token, reqJson.toString()))
        ChatResponse(
            sessionId = resJson.getString("session_id"),
            reply = resJson.getString("reply"),
        )
    }

    suspend fun getSessions(token: String): List<ChatSession> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("/sessions", "GET", token))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatSession(
                id = item.getString("id"),
                status = item.getString("status"),
                startedAt = item.getString("started_at"),
                endedAt = if (item.isNull("ended_at")) null else item.getString("ended_at"),
            )
        }
    }

    suspend fun getMessages(token: String, sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("/sessions/$sessionId/messages", "GET", token))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatMessage(
                id = item.getString("id"),
                role = item.getString("role"),
                content = item.getString("content"),
                createdAt = item.getString("created_at"),
            )
        }
    }

    suspend fun endSession(token: String, sessionId: String): ChatSession = withContext(Dispatchers.IO) {
        val item = JSONObject(request("/sessions/$sessionId/end", "POST", token, "{}"))
        ChatSession(
            id = item.getString("id"),
            status = item.getString("status"),
            startedAt = item.getString("started_at"),
            endedAt = if (item.isNull("ended_at")) null else item.getString("ended_at"),
        )
    }

    suspend fun deleteSession(token: String, sessionId: String): Unit = withContext(Dispatchers.IO) {
        request("/sessions/$sessionId", "DELETE", token)
    }

    private fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: String? = null,
    ): String {
        val connection = URL("${getBaseUrl()}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (API_KEY.isNotEmpty()) {
                connection.setRequestProperty("X-API-Key", API_KEY)
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                return if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    ""
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
            } else {
                handleAndThrowError(connection)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun handleAndThrowError(connection: HttpURLConnection): Nothing {
        val statusCode = connection.responseCode
        val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        val detail = try {
            val json = JSONObject(errorText)
            if (json.has("detail")) {
                json.get("detail").toString()
            } else {
                errorText
            }
        } catch (_: Exception) {
            errorText.ifEmpty { "HTTP $statusCode error" }
        }
        throw ChatbotApiException(statusCode, detail)
    }
}
