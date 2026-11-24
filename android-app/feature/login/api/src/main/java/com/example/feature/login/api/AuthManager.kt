package com.example.feature.login.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS_NAME = "auth_prefs"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_USER_EMAIL = "user_email"
private const val KEY_USER_ID = "user_id"

private const val SERVER_URL = "https://chatbot-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io"
private const val API_KEY = "327498c766af73190d71289528726d00fc6aec3e5db74db7"

data class AuthUser(
    val id: String,
    val email: String,
    val accessToken: String,
)

object AuthManager {

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        val token = getToken(context)
        return !token.isNullOrEmpty()
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun getUserEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    fun getUserId(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_ID, null)
    }

    fun signOut(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun saveSession(context: Context, user: AuthUser) {
        getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, user.accessToken)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_ID, user.id)
            .apply()
    }

    suspend fun signIn(context: Context, email: String, pass: String): AuthUser =
        authenticate(context, "/auth/login", email, pass, "Authentication failed")

    suspend fun signUp(context: Context, email: String, pass: String): AuthUser =
        authenticate(context, "/auth/register", email, pass, "Registration failed")

    /** Logs in or registers through our server, which talks to Supabase for us. */
    private suspend fun authenticate(
        context: Context,
        path: String,
        email: String,
        pass: String,
        failureMessage: String,
    ): AuthUser = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("password", pass.trim())
        }

        val connection = URL("$SERVER_URL$path").openConnection() as HttpURLConnection
        val json = try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-API-Key", API_KEY)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = runCatching { JSONObject(error).optString("detail") }.getOrNull()
                throw IOException(detail?.ifEmpty { null } ?: "$failureMessage ($code)")
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }

        val userObj = json.optJSONObject("user")
        val user = AuthUser(
            id = userObj?.optString("id").orEmpty(),
            email = userObj?.optString("email")?.ifEmpty { null } ?: email,
            accessToken = json.optString("access_token"),
        )
        saveSession(context, user)
        user
    }
}
