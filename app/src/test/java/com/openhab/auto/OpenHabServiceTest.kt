package com.openhab.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OpenHabServiceTest {

    private fun service(username: String, password: String) =
        OpenHabService(baseUrl = "https://example.org", username = username, password = password)

    @Test
    fun asciiPassword_encodesIdenticallyForBothCharsets() {
        val service = service("user", "pass")
        val utf8 = service.basicCredential(Charsets.UTF_8)
        val latin1 = service.basicCredential(Charsets.ISO_8859_1)
        // Pure-ASCII bytes are identical under both charsets, so the 401 retry is
        // a no-op (and executeWithRetry skips it).
        assertEquals(utf8, latin1)
        assertEquals("Basic dXNlcjpwYXNz", utf8)
    }

    @Test
    fun nonAsciiPassword_differsBetweenUtf8AndLatin1() {
        val service = service("user", "é")
        val utf8 = service.basicCredential(Charsets.UTF_8)
        val latin1 = service.basicCredential(Charsets.ISO_8859_1)
        // "é" is the two bytes C3 A9 in UTF-8 but a single byte E9 in ISO-8859-1,
        // so Base64("user:é") differs — exactly the case the 401 retry covers.
        assertNotEquals(utf8, latin1)
        assertEquals("Basic dXNlcjrDqQ==", utf8)
        assertEquals("Basic dXNlcjrp", latin1)
    }
}
