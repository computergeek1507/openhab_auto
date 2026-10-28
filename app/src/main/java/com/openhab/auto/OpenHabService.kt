package com.openhab.auto

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
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

    // Server-sent event streams stay open indefinitely, so they need a client
    // with no read timeout. newBuilder() keeps the TLS settings above.
    private val streamClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

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
                // Modern servers expect UTF-8; legacy ones may want ISO-8859-1,
                // which executeWithRetry falls back to on a 401.
                builder.header("Authorization", basicCredential(Charsets.UTF_8))
            }
        }
        return builder
    }

    // Build a Basic auth header value (`Basic <base64(user:pass)>`) using the
    // given charset to encode the credentials before Base64. Internal (not
    // private) so the unit test can verify the UTF-8 vs ISO-8859-1 encoding.
    internal fun basicCredential(charset: Charset): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(charset))

    // Execute a request, retrying a Basic-auth 401 once with ISO-8859-1
    // credentials. HTTP Basic auth historically used ISO-8859-1 and some servers
    // still decode it that way, so a non-ASCII password encoded as UTF-8 can be
    // wrongly rejected. Pure-ASCII passwords encode identically under both
    // charsets, so the retry is skipped for them.
    private fun executeWithRetry(request: Request): Response {
        val response = client.newCall(request).execute()
        if (response.code != 401 || username.isBlank() || password.isBlank()) {
            return response
        }
        val latin1 = basicCredential(Charsets.ISO_8859_1)
        if (latin1 == basicCredential(Charsets.UTF_8)) {
            return response
        }
        response.close()
        val retry = request.newBuilder().header("Authorization", latin1).build()
        return client.newCall(retry).execute()
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

        // Every item's statechanged event. Group items publish theirs under
        // openhab/items/{group}/{member}/statechanged, which this also matches.
        private const val STATE_TOPICS = "openhab/items/*/statechanged"

        /**
         * Parse one SSE message from /rest/events into (itemName, newState), or
         * null for anything that isn't an item state change (e.g. ALIVE keepalives).
         *
         * Messages look like:
         * {"topic":"openhab/items/Porch/statechanged",
         *  "payload":"{\"type\":\"OnOff\",\"value\":\"ON\",...}",
         *  "type":"ItemStateChangedEvent"}
         * For a group the topic is openhab/items/{group}/{member}/statechanged and
         * the value is the group's own new state, so the name is segment 2 in both.
         */
        internal fun parseStateChange(data: String): Pair<String, String>? {
            val event = try {
                JSONObject(data)
            } catch (_: Exception) {
                return null
            }
            val type = event.optString("type")
            if (type != "ItemStateChangedEvent" && type != "GroupItemStateChangedEvent") {
                return null
            }
            val segments = event.optString("topic").split('/')
            if (segments.size < 4 || segments[0] != "openhab" || segments[1] != "items") {
                return null
            }
            val payload = try {
                JSONObject(event.optString("payload"))
            } catch (_: Exception) {
                return null
            }
            if (!payload.has("value")) return null
            return segments[2] to payload.optString("value")
        }
    }

    override fun subscribeStateChanges(listener: StateChangeListener): AutoCloseable {
        val handle = StreamHandle(listener)
        handle.open(Charsets.UTF_8)
        return handle
    }

    // Wraps the EventSource so a Basic-auth 401 can be retried once with
    // ISO-8859-1 credentials (as executeWithRetry does for normal requests)
    // without the caller seeing a disconnect.
    private inner class StreamHandle(private val listener: StateChangeListener) : AutoCloseable {
        @Volatile private var closed = false
        @Volatile private var source: EventSource? = null

        fun open(charset: Charset) {
            val builder = Request.Builder()
                .url("$normalizedUrl/rest/events?topics=$STATE_TOPICS")
                .header("Accept", "text/event-stream")
            addAuth(builder)
            if (username.isNotBlank() && password.isNotBlank()) {
                builder.header("Authorization", basicCredential(charset))
            }
            source = EventSources.createFactory(streamClient)
                .newEventSource(builder.build(), Listener(charset))
        }

        override fun close() {
            closed = true
            source?.cancel()
        }

        private inner class Listener(private val charset: Charset) : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                if (!closed) listener.onConnected()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (closed) return
                parseStateChange(data)?.let { (name, state) -> listener.onStateChanged(name, state) }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!closed) listener.onDisconnected(null)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (closed) return
                val canRetryLatin1 = response?.code == 401 &&
                    charset == Charsets.UTF_8 &&
                    username.isNotBlank() && password.isNotBlank() &&
                    basicCredential(Charsets.ISO_8859_1) != basicCredential(Charsets.UTF_8)
                if (canRetryLatin1) {
                    response?.close()
                    open(Charsets.ISO_8859_1)
                    return
                }
                listener.onDisconnected(t ?: Exception("Event stream failed: HTTP ${response?.code}"))
            }
        }
    }

    override fun getSitemaps(): List<SitemapInfo> {
        val request = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/sitemaps")
                .header("Accept", "application/json")
        ).build()

        val response = executeWithRetry(request)
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch sitemaps: ${response.code}")
        }

        val json = JSONArray(response.body!!.string())
        return (0 until json.length()).map {
            val sitemap = json.getJSONObject(it)
            val name = sitemap.getString("name")
            SitemapInfo(name = name, label = sitemap.optString("label", name))
        }
    }

    // A sitemap's root page id equals its name, so its widget tree lives at
    // /rest/sitemaps/{name}/{name}.
    override fun getSitemapItems(sitemapName: String): List<OpenHabItem> {
        val request = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/sitemaps/$sitemapName/$sitemapName")
                .header("Accept", "application/json")
        ).build()

        val response = executeWithRetry(request)
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch sitemap: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        val items = mutableListOf<OpenHabItem>()
        collectItems(json.optJSONArray("widgets"), items)
        return items
    }

    // Sitemap pages nest widgets inside Frames and linked subpages; walk the
    // tree depth-first so items come out in the order the sitemap defines them.
    private fun collectItems(widgets: JSONArray?, out: MutableList<OpenHabItem>) {
        if (widgets == null) return
        for (i in 0 until widgets.length()) {
            val widget = widgets.getJSONObject(i)
            widget.optJSONObject("item")?.let { out.add(OpenHabItem.fromJson(it)) }
            collectItems(widget.optJSONArray("widgets"), out)
            widget.optJSONObject("linkedPage")?.optJSONArray("widgets")?.let {
                collectItems(it, out)
            }
        }
    }

    override fun toggleItem(itemName: String): String {
        val getRequest = addAuth(
            Request.Builder()
                .url("$normalizedUrl/rest/items/$itemName")
                .header("Accept", "application/json")
        ).build()

        val getResponse = executeWithRetry(getRequest)
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

        val postResponse = executeWithRetry(postRequest)
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
            val response = executeWithRetry(request)
            if (response.isSuccessful) {
                "OK"
            } else if (response.code == 401 && normalizedUrl == REMOTE_URL) {
                // myopenHAB rejected the Basic credentials — point at the likely
                // cause rather than a bare status code.
                "email or password rejected by myopenHAB"
            } else {
                "HTTP ${response.code}"
            }
        } catch (e: Exception) {
            e.message ?: "Unknown error"
        }
    }
}
