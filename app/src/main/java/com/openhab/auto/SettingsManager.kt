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

    var group: String
        get() = prefs.getString("openhab_group", "") ?: ""
        set(value) = prefs.edit().putString("openhab_group", value).apply()

    // When true, connect through the myopenHAB cloud instead of a local server.
    var useRemote: Boolean
        get() = prefs.getBoolean("use_remote", false)
        set(value) = prefs.edit().putBoolean("use_remote", value).apply()

    // When true, use offline sample data instead of any server (for previews/review).
    var demoMode: Boolean
        get() = prefs.getBoolean("demo_mode", false)
        set(value) = prefs.edit().putBoolean("demo_mode", value).apply()

    var email: String
        get() = prefs.getString("openhab_email", "") ?: ""
        set(value) = prefs.edit().putString("openhab_email", value).apply()

    var password: String
        get() = prefs.getString("openhab_password", "") ?: ""
        set(value) = prefs.edit().putString("openhab_password", value).apply()

    // Connection parameters resolved for the active mode.
    val effectiveUrl: String
        get() = if (useRemote) OpenHabService.REMOTE_URL else url
    val effectiveUsername: String
        get() = if (useRemote) email else token
    val effectivePassword: String
        get() = if (useRemote) password else ""

    val isConfigured: Boolean
        get() = demoMode || (group.isNotBlank() && (if (useRemote) email.isNotBlank() else url.isNotBlank()))

    // The item source for the active mode.
    fun buildSource(): OpenHabSource =
        when {
            demoMode -> DemoItemSource
            else -> OpenHabService(effectiveUrl, effectiveUsername, effectivePassword)
        }

    fun save(
        demoMode: Boolean,
        useRemote: Boolean,
        url: String,
        token: String,
        email: String,
        password: String,
        group: String,
    ) {
        this.demoMode = demoMode
        this.useRemote = useRemote
        this.url = url
        this.token = token
        this.email = email
        this.password = password
        this.group = group
    }
}
