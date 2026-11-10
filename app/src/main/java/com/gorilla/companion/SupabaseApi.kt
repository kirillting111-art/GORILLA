package com.gorilla.companion

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SupabaseApi {
    const val PROJECT_URL = "https://rcjhhdwvoldkvjhazmrb.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_SviPfqviy2WJ4yT4SUgN3w_kpqDsx7_"

    data class Session(val accessToken: String, val refreshToken: String, val userId: String)
    data class Profile(val publicId: Long, val status: String, val role: String, val displayName: String = "")

    private fun request(path: String, method: String, token: String? = null, body: JSONObject? = null): String {
        val connection = (URL(PROJECT_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=representation")
            }
        }
        try {
            if (body != null) OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (connection.responseCode !in 200..299) {
                val message = try {
                    val json = JSONObject(response)
                    json.optString("msg", json.optString("message", json.optString("error_description", response)))
                } catch (_: Exception) { response }
                throw IllegalStateException(message.ifBlank { "HTTP ${connection.responseCode}" })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun signUp(email: String, password: String, name: String): Session? {
        val body = JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("name", name))
        val result = JSONObject(request("/auth/v1/signup", "POST", body = body))
        return sessionFrom(result)
    }

    fun signIn(email: String, password: String): Session {
        val result = JSONObject(request("/auth/v1/token?grant_type=password", "POST",
            body = JSONObject().put("email", email).put("password", password)))
        return sessionFrom(result) ?: throw IllegalStateException("Сервер не вернул сессию. Подтверди почту и попробуй войти снова.")
    }

    private fun sessionFrom(json: JSONObject): Session? {
        val access = json.optString("access_token")
        val user = json.optJSONObject("user") ?: return null
        val id = user.optString("id")
        if (access.isBlank() || id.isBlank()) return null
        return Session(access, json.optString("refresh_token"), id)
    }

    fun profile(token: String, userId: String): Profile {
        val path = "/rest/v1/gorilla_profiles?select=public_id,access_status,role,display_name&user_id=eq.$userId&limit=1"
        val array = JSONArray(request(path, "GET", token))
        if (array.length() == 0) throw IllegalStateException("Профиль ещё создаётся. Подожди немного и войди снова.")
        val row = array.getJSONObject(0)
        return Profile(row.getLong("public_id"), row.getString("access_status"), row.getString("role"), row.optString("display_name"))
    }

    fun redeemCode(token: String, code: String): Long {
        val result = request("/rest/v1/rpc/gorilla_redeem_access_code", "POST", token,
            JSONObject().put("code", code))
        return result.trim().toLong()
    }

    fun pendingAccounts(token: String): JSONArray =
        JSONArray(request("/rest/v1/rpc/gorilla_pending_accounts", "POST", token, JSONObject()))

    fun issueCode(token: String, userId: String): String =
        request("/rest/v1/rpc/gorilla_issue_access_code", "POST", token,
            JSONObject().put("target_user", userId)).trim().trim('"')

    fun submitVideo(token: String, prompt: String, duration: String, aspect: String): JSONObject =
        JSONObject(request("/functions/v1/video-api", "POST", token,
            JSONObject().put("action", "submit").put("prompt", prompt).put("duration", duration)
                .put("aspect_ratio", aspect).put("generate_audio", true)))

    fun videoStatus(token: String, requestId: String, result: Boolean = false): JSONObject =
        JSONObject(request("/functions/v1/video-api", "POST", token,
            JSONObject().put("action", if (result) "result" else "status").put("request_id", requestId)))
}
