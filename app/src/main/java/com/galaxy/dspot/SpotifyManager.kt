package com.galaxy.dspot

import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SpotifyManager(private val context: Context) {

    companion object {
        const val CLIENT_ID = "SPOTIFY_CLIENT_ID_PLACEHOLDER"
        const val REDIRECT_URI = "3dspot://callback"
        const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private streaming"
        const val AUTH_URL = "https://accounts.spotify.com/authorize"
        const val API_BASE = "https://api.spotify.com/v1"
    }

    private val client = OkHttpClient()
    var accessToken: String? = null

    fun getAuthIntent(): Intent {
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun handleCallback(uri: Uri): Boolean {
        val fragment = uri.fragment ?: return false
        val params = fragment.split("&").associate {
            val (k, v) = it.split("=")
            k to v
        }
        accessToken = params["access_token"]
        return accessToken != null
    }

    fun getCurrentTrack(callback: (JSONObject?) -> Unit) {
        val token = accessToken ?: return callback(null)
        val request = Request.Builder()
            .url("$API_BASE/me/player/currently-playing")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body.isNullOrEmpty()) { callback(null); return }
                try { callback(JSONObject(body)) } catch (e: Exception) { callback(null) }
            }
        })
    }

    fun getPlaylists(callback: (List<JSONObject>) -> Unit) {
        val token = accessToken ?: return callback(emptyList())
        val request = Request.Builder()
            .url("$API_BASE/me/playlists?limit=20")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(emptyList()) }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return callback(emptyList())
                try {
                    val json = JSONObject(body)
                    val items = json.getJSONArray("items")
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until items.length()) list.add(items.getJSONObject(i))
                    callback(list)
                } catch (e: Exception) { callback(emptyList()) }
            }
        })
    }

    fun searchTracks(query: String, callback: (List<JSONObject>) -> Unit) {
        val token = accessToken ?: return callback(emptyList())
        val encoded = Uri.encode(query)
        val request = Request.Builder()
            .url("$API_BASE/search?q=$encoded&type=track&limit=10")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(emptyList()) }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return callback(emptyList())
                try {
                    val json = JSONObject(body)
                    val items = json.getJSONObject("tracks").getJSONArray("items")
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until items.length()) list.add(items.getJSONObject(i))
                    callback(list)
                } catch (e: Exception) { callback(emptyList()) }
            }
        })
    }

    fun playPause(isPlaying: Boolean, callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val url = if (isPlaying) "$API_BASE/me/player/pause" else "$API_BASE/me/player/play"
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url).put(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }

    fun skipNext(callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$API_BASE/me/player/next").post(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }

    fun skipPrevious(callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$API_BASE/me/player/previous").post(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }

    fun playTrack(uri: String, callback: (Boolean) -> Unit) {
        val token = accessToken ?: return callback(false)
        val jsonBody = """{"uris":["$uri"]}"""
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$API_BASE/me/player/play").put(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }
}
