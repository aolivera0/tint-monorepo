package com.tint.app.config

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointsTest {
    private val endpoints = Endpoints(
        identityApiUrl = "http://localhost:8081/api/v1",
        syncWsUrl = "ws://localhost:8083/ws/sync",
        socialWsUrl = "ws://localhost:8082/ws/social",
    )

    @Test
    fun `el canal sync lleva sala y token en la query`() {
        assertEquals(
            "ws://localhost:8083/ws/sync?room_id=abc&token=h.p.s",
            endpoints.syncUrl("abc", "h.p.s"),
        )
    }

    @Test
    fun `el canal social lleva sala y token en la query`() {
        assertEquals(
            "ws://localhost:8082/ws/social?room_id=abc&token=h.p.s",
            endpoints.socialUrl("abc", "h.p.s"),
        )
    }

    @Test
    fun `escapa los caracteres reservados de la query`() {
        assertEquals(
            "ws://localhost:8083/ws/sync?room_id=a%26b&token=x%3Dy",
            endpoints.syncUrl("a&b", "x=y"),
        )
    }

    @Test
    fun `quita la barra final de las bases`() {
        val trimmed = Endpoints("http://h/api/v1/", "ws://h/ws/sync/", "ws://h/ws/social/")
        assertEquals("http://h/api/v1", trimmed.identityApiUrl)
        assertEquals("ws://h/ws/sync?room_id=r&token=t", trimmed.syncUrl("r", "t"))
        assertEquals("ws://h/ws/social?room_id=r&token=t", trimmed.socialUrl("r", "t"))
    }
}
