package com.tint.app.net

import com.tint.app.store.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredTokenStoreTest {
    @Test
    fun `guarda y lee los tokens con las claves de la web`() {
        val store = MemoryStore()
        val tokens = StoredTokenStore(store)

        tokens.hostToken = "host"
        tokens.guestToken = "guest"

        assertEquals("host", store.data["tint_token"])
        assertEquals("guest", store.data["tint_guest_token"])
        assertEquals("host", tokens.hostToken)
        assertEquals("guest", tokens.guestToken)
    }

    @Test
    fun `asignar null borra el token`() {
        val store = MemoryStore(mapOf("tint_token" to "host", "tint_guest_token" to "guest"))
        val tokens = StoredTokenStore(store)

        tokens.hostToken = null
        tokens.guestToken = null

        assertNull(tokens.hostToken)
        assertNull(tokens.guestToken)
        assertEquals(emptyMap<String, String>(), store.data)
    }
}
