package com.openhab.auto

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("openhab_settings", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString("openhab_url", "") ?: ""
        set(value) = prefs.edit().putString("openhab_url", value).apply()

    var token: String
        get() = prefs.getString("openhab_token", "") ?: ""
        set(value) = prefs.edit().putString("openhab_token", value).apply()

    var sitemap: String
        get() = prefs.getString("openhab_sitemap", "") ?: ""
        set(value) = prefs.edit().putString("openhab_sitemap", value).apply()

    // When true, connect through the myopenHAB cloud instead of a local server.
    var useRemote: Boolean
        get() = prefs.getBoolean("use_remote", false)
        set(value) = prefs.edit().putBoolean("use_remote", value).apply()

    // When true, use offline sample data instead of any server (for previews/review).
    var demoMode: Boolean
        get() = prefs.getBoolean("demo_mode", false)
        set(value) = prefs.edit().putBoolean("demo_mode", value).apply()

    // When true, accept self-signed/untrusted TLS certs for the local server.
    var allowSelfSigned: Boolean
        get() = prefs.getBoolean("allow_self_signed", false)
        set(value) = prefs.edit().putBoolean("allow_self_signed", value).apply()

    var email: String
        get() = prefs.getString("openhab_email", "") ?: ""
        set(value) = prefs.edit().putString("openhab_email", value).apply()

    var password: String
        get() = prefs.getString("openhab_password", "") ?: ""
        set(value) = prefs.edit().putString("openhab_password", value).apply()

    // When true, Local mode authenticates with username + password (HTTP Basic)
    // instead of an API token (Bearer). The server must have "Allow Basic
    // Authentication" enabled for this to work.
    var localBasicAuth: Boolean
        get() = prefs.getBoolean("local_basic_auth", false)
        set(value) = prefs.edit().putBoolean("local_basic_auth", value).apply()

    var localUsername: String
        get() = prefs.getString("local_username", "") ?: ""
        set(value) = prefs.edit().putString("local_username", value).apply()

    var localPassword: String
        get() = prefs.getString("local_password", "") ?: ""
        set(value) = prefs.edit().putString("local_password", value).apply()

    // Connection parameters resolved for the active mode.
    val effectiveUrl: String
        get() = if (useRemote) OpenHabService.REMOTE_URL else url
    // For Local Basic auth the username/password pair is used; for Local token
    // auth the token is the "username" with a blank password (sent as Bearer).
    val effectiveUsername: String
        get() = when {
            useRemote -> email
            localBasicAuth -> localUsername
            else -> token
        }
    val effectivePassword: String
        get() = when {
            useRemote -> password
            localBasicAuth -> localPassword
            else -> ""
        }

    val isConfigured: Boolean
        get() = demoMode || (sitemap.isNotBlank() && (if (useRemote) email.isNotBlank() else url.isNotBlank()))

    // The item source for the active mode. Self-signed certs are accepted only
    // for the local server; the myopenHAB cloud uses a normally trusted cert.
    fun buildSource(): OpenHabSource =
        when {
            demoMode -> DemoItemSource
            else -> OpenHabService(
                effectiveUrl,
                effectiveUsername,
                effectivePassword,
                allowSelfSigned = !useRemote && allowSelfSigned,
            )
        }

    fun save(
        demoMode: Boolean,
        useRemote: Boolean,
        url: String,
        token: String,
        email: String,
        password: String,
        sitemap: String,
        allowSelfSigned: Boolean,
        localBasicAuth: Boolean,
        localUsername: String,
        localPassword: String,
    ) {
        this.demoMode = demoMode
        this.useRemote = useRemote
        this.url = url
        this.token = token
        this.email = email
        this.password = password
        this.sitemap = sitemap
        this.allowSelfSigned = allowSelfSigned
        this.localBasicAuth = localBasicAuth
        this.localUsername = localUsername
        this.localPassword = localPassword
    }
}
