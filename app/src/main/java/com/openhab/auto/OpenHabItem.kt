package com.openhab.auto

import org.json.JSONObject

/** A command an item accepts, as advertised by openHAB's commandDescription. */
data class CommandOption(val command: String, val label: String)

data class OpenHabItem(
    val name: String,
    val label: String,
    val type: String,
    var state: String,
    // Selectable commands from the item's commandDescription, if any.
    val commandOptions: List<CommandOption> = emptyList(),
) {
    // ON for switches, or any percentage/dimmer item whose level is above zero.
    // For a rollershutter this reads as "closed" (position > 0).
    val isOn: Boolean get() = state == "ON" || (state.toDoubleOrNull() ?: 0.0) > 0.0

    val isRollershutter: Boolean get() = type.startsWith("Rollershutter")

    // Dimmer items take a 0..100 percentage, so they get a slider on the phone.
    val isDimmer: Boolean get() = type.startsWith("Dimmer")

    // Current dimmer level clamped to 0..100; non-numeric states read as 0.
    val level: Int get() = (state.toDoubleOrNull() ?: 0.0).toInt().coerceIn(0, 100)

    // String items are display-only here: there's no meaningful ON/OFF toggle.
    val isString: Boolean get() = type.startsWith("String")

    // Number items (including dimensioned types like Number:Temperature) report a
    // sensor reading. There's nothing to toggle, so they're display-only too.
    val isNumber: Boolean get() = type.startsWith("Number")

    // Items shown as a value with no toggle action.
    val isReadOnly: Boolean get() = isString || isNumber

    // Items that advertise command options open a list to pick from instead of
    // toggling. Takes precedence over the read-only treatment above.
    val isSelectable: Boolean get() = commandOptions.isNotEmpty()

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
                commandOptions = parseCommandOptions(json),
            )
        }

        // commandDescription.commandOptions is a list of { command, label }; the
        // label is optional and falls back to the command itself.
        private fun parseCommandOptions(json: JSONObject): List<CommandOption> {
            val options = json.optJSONObject("commandDescription")
                ?.optJSONArray("commandOptions")
                ?: return emptyList()

            return (0 until options.length()).map {
                val option = options.getJSONObject(it)
                val command = option.getString("command")
                CommandOption(command, option.optString("label", command))
            }
        }
    }
}
