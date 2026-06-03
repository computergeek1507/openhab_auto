package com.openhab.auto

import org.json.JSONObject

data class OpenHabItem(
    val name: String,
    val label: String,
    val type: String,
    var state: String,
) {
    // ON for switches, or any percentage/dimmer item whose level is above zero.
    // For a rollershutter this reads as "closed" (position > 0).
    val isOn: Boolean get() = state == "ON" || (state.toDoubleOrNull() ?: 0.0) > 0.0

    val isRollershutter: Boolean get() = type.startsWith("Rollershutter")

    // String items are display-only here: there's no meaningful ON/OFF toggle.
    val isString: Boolean get() = type.startsWith("String")

    // Human-readable value for read-only items; blank/uninitialized states show as a dash.
    val displayValue: String
        get() = if (state.isBlank() || state == "NULL" || state == "UNDEF") "—" else state

    companion object {
        fun fromJson(json: JSONObject): OpenHabItem {
            return OpenHabItem(
                name = json.getString("name"),
                label = json.optString("label", json.getString("name")),
                type = json.getString("type"),
                state = json.optString("state", "NULL"),
            )
        }
    }
}
