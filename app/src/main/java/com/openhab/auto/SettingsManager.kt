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

    // User-defined display order, stored as item names. openHAB item names are
    // restricted to letters/digits/underscore, so a newline delimiter is safe.
    var itemOrder: List<String>
        get() = prefs.getString("item_order", "")
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = prefs.edit().putString("item_order", value.joinToString("\n")).apply()

    // Reorder freshly fetched items to match the saved order. Items not in the
    // saved order (newly added) keep their original relative order and are
    // appended after the known ones; sortedBy is stable, so this holds.
    fun applyItemOrder(items: List<OpenHabItem>): List<OpenHabItem> {
        val rank = itemOrder.withIndex().associate { (i, name) -> name to i }
        if (rank.isEmpty()) return items
        return items.sortedBy { rank[it.name] ?: Int.MAX_VALUE }
    }

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
        get() = demoMode || (group.isNotBlank() && (if (useRemote) email.isNotBlank() else url.isNotBlank()))

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
        group: String,
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
        this.group = group
        this.allowSelfSigned = allowSelfSigned
        this.localBasicAuth = localBasicAuth
        this.localUsername = localUsername
        this.localPassword = localPassword
    }
}
