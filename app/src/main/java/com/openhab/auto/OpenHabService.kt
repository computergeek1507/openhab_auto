package com.openhab.auto

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OpenHabService(
    private val baseUrl: String,
    private val username: String,
    private val password: String = "",
    // When true, accept any TLS certificate (for local servers with a
    // self-signed cert). Disables cert and hostname validation — only safe
    // on a trusted network, so it is off by default and opt-in via settings.
    private val allowSelfSigned: Boolean = false,
) : OpenHabSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .apply { if (allowSelfSigned) trustAllCertificates() }
        .build()

    private val normalizedUrl: String
        get() = baseUrl.trimEnd('/')

    // A local API token (blank password) is sent as a Bearer token — openHAB
    // disables Basic auth by default, so Basic would 401. The myopenHAB cloud
    // login (username = email, password) uses Basic auth.
    private fun addAuth(builder: Request.Builder): Request.Builder {
        if (username.isNotBlank()) {
            if (password.isBlank()) {
                builder.header("Authorization", "Bearer $username")
            } else {
                val credentials =
                    Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                builder.header("Authorization", "Basic $credentials")
            }
        }
        return builder
    }

    // Configure the builder to trust any server certificate. Used only when the
    // user explicitly enables "Allow self-signed certificates".
    private fun OkHttpClient.Builder.trustAllCertificates() {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        sslSocketFactory(sslContext.socketFactory, trustManager)
        hostnameVerifier { _, _ -> true }
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

        postCommand(itemName, command)
        return newState
    }

    override fun sendCommand(itemName: String, command: String) {
        postCommand(itemName, command)
    }

    private fun postCommand(itemName: String, command: String) {
        val postRequest = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/items/$itemName")
                .post(command.toRequestBody("text/plain".toMediaType()))
        ).build()

        val postResponse = client.newCall(postRequest).execute()
        if (!postResponse.isSuccessful && postResponse.code != 202) {
            throw Exception("Failed to send command: ${postResponse.code}")
        }
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
