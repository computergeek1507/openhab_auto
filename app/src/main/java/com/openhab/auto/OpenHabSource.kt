package com.openhab.auto

/**
 * A source of openHAB items. Implemented by [OpenHabService] (live network) and
 * [DemoItemSource] (offline sample data used for store screenshots and review).
 */
interface OpenHabSource {
    fun getGroupItems(groupName: String): List<OpenHabItem>
    fun toggleItem(itemName: String): String
    fun sendCommand(itemName: String, command: String)
    fun testConnection(): String
}

/**
 * Offline demo data. Requires no server or credentials, so reviewers (and the
 * Play Store screenshots) can see the app working. Toggles update in memory and
 * persist for the session, exercising switches, a dimmer, a rollershutter and a
 * read-only string item.
 */
object DemoItemSource : OpenHabSource {
    private val items = mutableListOf(
        OpenHabItem("DemoLiving", "Living Room Lights", "Switch", "ON"),
        OpenHabItem("DemoKitchen", "Kitchen Lights", "Dimmer", "60"),
        OpenHabItem("DemoPorch", "Front Porch Light", "Switch", "OFF"),
        OpenHabItem("DemoGarage", "Garage Door", "Rollershutter", "100"),
        OpenHabItem("DemoFan", "Bedroom Fan", "Switch", "OFF"),
        OpenHabItem("DemoThermostat", "Thermostat", "String", "21°C"),
        OpenHabItem("DemoOutdoorTemp", "Outdoor Temperature", "Number:Temperature", "14.5 °C"),
        OpenHabItem(
            "DemoHvacMode", "HVAC Mode", "String", "Auto",
            commandOptions = listOf(
                CommandOption("Off", "Off"),
                CommandOption("Heat", "Heat"),
                CommandOption("Cool", "Cool"),
                CommandOption("Auto", "Auto"),
            ),
        ),
    )

    override fun getGroupItems(groupName: String): List<OpenHabItem> =
        items.map { it.copy() }

    override fun toggleItem(itemName: String): String {
        val item = items.find { it.name == itemName } ?: return "OFF"
        val newState = when {
            item.isRollershutter -> if (item.isOn) "0" else "100"
            else -> if (item.isOn) "OFF" else "ON"
        }
        item.state = newState
        return newState
    }

    override fun sendCommand(itemName: String, command: String) {
        items.find { it.name == itemName }?.state = command
    }

    override fun testConnection(): String = "OK"
}
