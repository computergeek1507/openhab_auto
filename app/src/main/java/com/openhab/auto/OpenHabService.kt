package com.openhab.auto

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class OpenHabService(
    private val baseUrl: String,
    private val username: String,
    private val password: String = "",
) : OpenHabSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val normalizedUrl: String
        get() = baseUrl.trimEnd('/')

    // Basic auth covers both cases: a local openHAB API token (username = token,
    // blank password) and a myopenHAB cloud login (username = email, password).
    private fun addAuth(builder: Request.Builder): Request.Builder {
        if (username.isNotBlank()) {
            val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            builder.header("Authorization", "Basic $credentials")
        }
        return builder
    }

    companion object {
        const val REMOTE_URL = "https://home.myopenhab.org"
    }

    override fun getGroupItems(groupName: String): List<OpenHabItem> {
        val request = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/items/$groupName")
                .header("Accept", "application/json")
        ).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch group: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        val members = json.optJSONArray("members") ?: return emptyList()

        return (0 until members.length())
            .map { OpenHabItem.fromJson(members.getJSONObject(it)) }
    }

    override fun toggleItem(itemName: String): String {
        val getRequest = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/items/$itemName")
                .header("Accept", "application/json")
        ).build()

        val getResponse = client.newCall(getRequest).execute()
        if (!getResponse.isSuccessful) {
            throw Exception("Failed to read item: ${getResponse.code}")
        }

        val json = JSONObject(getResponse.body!!.string())
        val type = json.optString("type", "")
        val currentState = json.optString("state", "OFF")
        // Treat dimmers/percentage items (state is a number > 0) as "on" too,
        // so toggling them actually turns them off instead of re-sending ON.
        val isOn = currentState == "ON" || (currentState.toDoubleOrNull() ?: 0.0) > 0.0

        // Rollershutters take UP/DOWN (not ON/OFF); position is 0 (open) .. 100 (closed).
        // The returned value is the optimistic new state used to update the tile until
        // the next refresh reports the real position.
        val (command, newState) = if (type.startsWith("Rollershutter")) {
            if (isOn) "UP" to "0" else "DOWN" to "100"
        } else {
            if (isOn) "OFF" to "OFF" else "ON" to "ON"
        }

        val postRequest = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/items/$itemName")
                .post(command.toRequestBody("text/plain".toMediaType()))
        ).build()

        val postResponse = client.newCall(postRequest).execute()
        if (!postResponse.isSuccessful && postResponse.code != 202) {
            throw Exception("Failed to send command: ${postResponse.code}")
        }

        return newState
    }

    override fun testConnection(): String {
        return try {
            val request = addAuth(
                Request.Builder()
                    .url("$normalizedUrl/rest/items?limit=1")
                    .header("Accept", "application/json")
            ).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                "OK"
            } else {
                "HTTP ${response.code}"
            }
        } catch (e: Exception) {
            e.message ?: "Unknown error"
        }
    }
}
