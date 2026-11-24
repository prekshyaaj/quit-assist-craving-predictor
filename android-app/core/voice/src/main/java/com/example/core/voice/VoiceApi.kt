package com.example.core.voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private fun getBaseUrl(): String = "https://voice-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io"

// Must match the server's API_KEY environment variable; leave empty if the server has none.
private const val API_KEY = "327498c766af73190d71289528726d00fc6aec3e5db74db7"

data class CravingResult(
    val text: String,
    val isCraving: Boolean,
    val score: Double,
    val match: String?,
)

object VoiceApi {

    suspend fun analyze(pcm: ByteArray): List<CravingResult> = withContext(Dispatchers.IO) {
        val connection = URL("${getBaseUrl()}/analyze").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(pcm.size)
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Content-Length", pcm.size.toString())
            if (API_KEY.isNotEmpty()) connection.setRequestProperty("X-API-Key", API_KEY)

            connection.outputStream.use { it.write(pcm) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned ${connection.responseCode}")
            }
            parseResults(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResults(json: String): List<CravingResult> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            CravingResult(
                text = item.getString("text"),
                isCraving = item.getBoolean("is_craving"),
                score = item.getDouble("score"),
                match = if (item.isNull("match")) null else item.getString("match"),
            )
        }
    }
}
