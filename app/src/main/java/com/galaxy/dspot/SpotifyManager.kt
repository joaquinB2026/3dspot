package com.galaxy.dspot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

class SpotifyManager(private val context: Context) {

    companion object {
        const val CLIENT_ID = "SPOTIFY_CLIENT_ID_PLACEHOLDER"
        const val REDIRECT_URI = "3dspot://callback"
        const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private streaming user-library-read"
        const val AUTH_URL = "https://accounts.spotify.com/authorize"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val API_BASE = "https://api.spotify.com/v1"
    }

    private val client = OkHttpClient()
    var accessToken: String? = null
    private var codeVerifier: String? = null

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun getAuthIntent(): Intent {
        codeVerifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(codeVerifier!!)
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun handleCallback(uri: Uri, onResult: (Boolean) -> Unit) {
        val code = uri.getQueryParameter("code")
        if (code == null || codeVerifier == null) { onResult(false); return }
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("code_verifier", codeVerifier!!)
            .build()
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return onResult(false)
                try {
                    accessToken = JSONObject(json).optString("access_token").takeIf { it.isNotEmpty() }
                    onResult(accessToken != null)
                } catch (e: Exception) { onResult(false) }
            }
        })
    }

    fun getCurrentTrack(callback: (JSONObject?) -> Unit) {
        val token = accessToken ?: return callback(null)
        get("$API_BASE/me/player/currently-playing", token) { body ->
            if (body.isNullOrEmpty()) callback(null)
            else try { callback(JSONObject(body)) } catch (e: Exception) { callback(null) }
        }
    }

    fun getPlaylists(callback: (List<JSONObject>) -> Unit) {
        val token = accessToken ?: return callback(emptyList())
        get("$API_BASE/me/playlists?limit=20", token) { body ->
            if (body == null) return@get callback(emptyList())
            try {
                val items = JSONObject(body).getJSONArray("items")
                callback((0 until items.length()).map { items.getJSONObject(it) })
            } catch (e: Exception) { callback(emptyList()) }
        }
    }

    fun searchTracks(query: String, callback: (List<JSONObject>) -> Unit) {
        val token = accessToken ?: return callback(emptyList())
        get("$API_BASE/search?q=${Uri.encode(query)}&type=track&limit=10", token) { body ->
            if (body == null) return@get callback(emptyList())
            try {
                val items = JSONObject(body).getJSONObject("tracks").getJSONArray("items")
                callback((0 until items.length()).map { items.getJSONObject(it) })
            } catch (e: Exception) { callback(emptyList()) }
        }
    }

    fun playPause(isPlaying: Boolean, callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val url = if (isPlaying) "$API_BASE/me/player/pause" else "$API_BASE/me/player/play"
        val body = "".toRequestBody("application/json".toMediaType())
        put(url, body, token, callback)
    }

    fun skipNext(callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$API_BASE/me/player/next").post(body)
            .addHeader("Authorization", "Bearer $token").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }

    fun skipPrevious(callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$API_BASE/me/player/previous").post(body)
            .addHeader("Authorization", "Bearer $token").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }

    fun playTrack(uri: String, callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val body = """{"uris":["$uri"]}""".toRequestBody("application/json".toMediaType())
        put("$API_BASE/me/player/play", body, token, callback)
    }

    fun playPlaylist(contextUri: String, callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val body = """{"context_uri":"$contextUri"}""".toRequestBody("application/json".toMediaType())
        put("$API_BASE/me/player/play", body, token, callback)
    }

    private fun get(url: String, token: String, callback: (String?) -> Unit) {
        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) { callback(response.body?.string()) }
        })
    }

    private fun put(url: String, body: RequestBody, token: String, callback: (Boolean) -> Unit) {
        val request = Request.Builder().url(url).put(body).addHeader("Authorization", "Bearer $token").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }
}
