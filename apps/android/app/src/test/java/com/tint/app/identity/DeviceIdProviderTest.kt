package com.tint.app.identity

import com.tint.app.store.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceIdProviderTest {
    @Test
    fun `genera un uuid la primera vez y lo guarda`() {
        val store = MemoryStore()
        val provider = DeviceIdProvider(store) { "11111111-1111-4111-8111-111111111111" }

        assertEquals("11111111-1111-4111-8111-111111111111", provider.get())
        assertEquals("11111111-1111-4111-8111-111111111111", store.data[DeviceIdProvider.KEY])
    }

    @Test
    fun `reutiliza el uuid guardado`() {
        val stored = "22222222-2222-4222-8222-222222222222"
        val provider = DeviceIdProvider(MemoryStore(mapOf(DeviceIdProvider.KEY to stored))) {
            error("no debe generar otro")
        }

        assertEquals(stored, provider.get())
    }

    @Test
    fun `reemplaza un valor guardado que no es un uuid`() {
        val store = MemoryStore(mapOf(DeviceIdProvider.KEY to "no-es-uuid"))
        val provider = DeviceIdProvider(store) { "33333333-3333-4333-8333-333333333333" }

        assertEquals("33333333-3333-4333-8333-333333333333", provider.get())
        assertNotEquals("no-es-uuid", store.data[DeviceIdProvider.KEY])
    }

    @Test
    fun `es estable entre llamadas`() {
        var n = 0
        val provider = DeviceIdProvider(MemoryStore()) { "44444444-4444-4444-8444-44444444444${n++}" }

        assertEquals(provider.get(), provider.get())
    }
}
