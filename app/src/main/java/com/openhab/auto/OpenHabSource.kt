package com.openhab.auto

/**
 * A source of openHAB items. Implemented by [OpenHabService] (live network) and
 * [DemoItemSource] (offline sample data used for store screenshots and review).
 */
interface OpenHabSource {
    fun getSitemaps(): List<SitemapInfo>
    fun getSitemapItems(sitemapName: String): List<OpenHabItem>
    fun toggleItem(itemName: String): String
    fun sendCommand(itemName: String, command: String)
    fun testConnection(): String

    /**
     * Open a live stream of item state changes. Returns a handle that closes the
     * stream, or null if this source doesn't support streaming (callers then keep
     * polling). The listener is called on a background thread.
     */
    fun subscribeStateChanges(listener: StateChangeListener): AutoCloseable? = null
}

/** Callbacks for [OpenHabSource.subscribeStateChanges]. */
interface StateChangeListener {
    /** The stream is connected; changes from here on will be delivered. */
    fun onConnected()

    /** An item's state changed on the server. */
    fun onStateChanged(itemName: String, state: String)

    /** The stream ended or failed. Not called after the handle is closed. */
    fun onDisconnected(error: Throwable?)
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

    override fun getSitemaps(): List<SitemapInfo> =
        listOf(SitemapInfo("demo", "Demo Sitemap"))

    override fun getSitemapItems(sitemapName: String): List<OpenHabItem> =
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
