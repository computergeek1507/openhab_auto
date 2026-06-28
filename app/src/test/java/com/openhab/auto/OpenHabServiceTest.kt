package com.openhab.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

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
    fun passwordWithExclamationMark_roundTripsExactly() {
        // "!" is plain ASCII (0x21); Base64 carries it verbatim, so a password
        // containing "!" is encoded and decoded without alteration.
        val service = service("user", "p@ss!w0rd!")
        val credential = service.basicCredential(Charsets.UTF_8)
        // ASCII so both charsets agree, and the retry would be a no-op.
        assertEquals(credential, service.basicCredential(Charsets.ISO_8859_1))
        // The base64 payload must decode back to the exact "user:password" pair.
        val decoded = String(Base64.getDecoder().decode(credential.removePrefix("Basic ")))
        assertEquals("user:p@ss!w0rd!", decoded)
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
