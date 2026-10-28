package com.openhab.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateChangeParserTest {

    // Build an /rest/events message the way openHAB sends it: the payload is a
    // JSON object serialized into a string field.
    private fun event(topic: String, type: String, value: String): String {
        val payload = org.json.JSONObject()
            .put("type", "OnOff")
            .put("value", value)
            .put("oldType", "OnOff")
            .put("oldValue", "OFF")
            .toString()
        return org.json.JSONObject()
            .put("topic", topic)
            .put("payload", payload)
            .put("type", type)
            .toString()
    }

    @Test
    fun itemStateChanged_returnsNameAndValue() {
        val data = event("openhab/items/Porch_Light/statechanged", "ItemStateChangedEvent", "ON")
        assertEquals("Porch_Light" to "ON", OpenHabService.parseStateChange(data))
    }

    @Test
    fun quantityValue_keepsUnit() {
        val data = event("openhab/items/OutdoorTemp/statechanged", "ItemStateChangedEvent", "14.5 °C")
        assertEquals("OutdoorTemp" to "14.5 °C", OpenHabService.parseStateChange(data))
    }

    @Test
    fun groupStateChanged_returnsGroupName() {
        val data = event(
            "openhab/items/gLights/Porch_Light/statechanged",
            "GroupItemStateChangedEvent",
            "ON",
        )
        assertEquals("gLights" to "ON", OpenHabService.parseStateChange(data))
    }

    @Test
    fun aliveKeepalive_isIgnored() {
        assertNull(OpenHabService.parseStateChange("""{"type":"ALIVE","interval":10}"""))
    }

    @Test
    fun otherEventTypes_areIgnored() {
        val data = event("openhab/items/Porch_Light/command", "ItemCommandEvent", "ON")
        assertNull(OpenHabService.parseStateChange(data))
    }

    @Test
    fun malformedData_isIgnored() {
        assertNull(OpenHabService.parseStateChange("not json"))
        assertNull(
            OpenHabService.parseStateChange(
                """{"topic":"openhab/items/X/statechanged","payload":"oops","type":"ItemStateChangedEvent"}"""
            )
        )
    }
}
