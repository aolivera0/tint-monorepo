package com.tint.app.net

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenClaimsTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return "${enc.encodeToString("""{"alg":"HS256"}""".toByteArray())}.${enc.encodeToString(payload.toByteArray())}.firma"
    }

    @Test
    fun `lee el sub del payload`() {
        assertEquals("guest:abc-123", TokenClaims.subject(jwt("""{"sub":"guest:abc-123","room_id":"r1"}""")))
    }

    @Test
    fun `tolera payloads con y sin relleno base64`() {
        // Longitudes que dan 0, 1 y 2 caracteres de relleno.
        for (extra in listOf("", "x", "xx")) {
            assertEquals("s$extra", TokenClaims.subject(jwt("""{"sub":"s$extra"}""")))
        }
    }

    @Test
    fun `devuelve null si no hay sub o el token esta roto`() {
        assertNull(TokenClaims.subject(jwt("""{"room_id":"r1"}""")))
        assertNull(TokenClaims.subject(jwt("""{"sub":123}""")))
        assertNull(TokenClaims.subject("no-es-un-jwt"))
        assertNull(TokenClaims.subject("a.%%%.c"))
        assertNull(TokenClaims.subject("a.bm8gZXMganNvbg.c"))
        assertNull(TokenClaims.subject(null))
        assertNull(TokenClaims.subject(""))
    }
}
