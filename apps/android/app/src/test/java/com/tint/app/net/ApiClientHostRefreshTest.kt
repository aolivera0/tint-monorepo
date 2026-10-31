package com.tint.app.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Renovación del ID token de Firebase del host (dura ~1 h), igual que `apps/web/src/lib/api.ts`. */
class ApiClientHostRefreshTest {
    @Serializable
    private data class Room(val id: String)

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private var expired = 0
    private val asked = mutableListOf<Boolean>()
    private var provided: String? = "fresh"
    private var providerFails = false

    private fun client() = ApiClient(
        baseUrl = server.url("/api/v1").toString().removeSuffix("/"),
        http = OkHttpClient(),
        tokens = tokens,
        onHostSessionExpired = { expired++ },
        hostTokenProvider = { force ->
            asked += force
            if (providerFails) error("firebase caído")
            provided
        },
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore()
        expired = 0
        asked.clear()
        provided = "fresh"
        providerFails = false
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `usa el token del proveedor y lo guarda`() = runBlocking {
        tokens.hostToken = "viejo"
        server.enqueue(json("""{"id":"r1"}"""))

        client().get<Room>("/rooms/r1")

        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
        assertEquals("fresh", tokens.hostToken)
        assertEquals(listOf(false), asked)
    }

    @Test
    fun `sin sesion de host no consulta al proveedor ni la revive`() = runBlocking {
        tokens.guestToken = "guest"
        server.enqueue(json("""{"id":"r1"}"""))

        client().get<Room>("/rooms/r1")

        assertEquals("Bearer guest", server.takeRequest().getHeader("Authorization"))
        assertNull(tokens.hostToken)
        assertEquals(emptyList<Boolean>(), asked)
    }

    @Test
    fun `si el proveedor falla sigue con el token guardado`() = runBlocking {
        tokens.hostToken = "guardado"
        providerFails = true
        server.enqueue(json("""{"id":"r1"}"""))

        client().get<Room>("/rooms/r1")

        assertEquals("Bearer guardado", server.takeRequest().getHeader("Authorization"))
        assertEquals("guardado", tokens.hostToken)
    }

    @Test
    fun `si el proveedor no devuelve token sigue con el guardado`() = runBlocking {
        tokens.hostToken = "guardado"
        provided = null
        server.enqueue(json("""{"id":"r1"}"""))

        client().get<Room>("/rooms/r1")

        assertEquals("Bearer guardado", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `un 401 fuerza la renovacion y reintenta una vez`() = runBlocking {
        tokens.hostToken = "viejo"
        provided = "nuevo"
        server.enqueue(json("""{"error":"UNAUTHORIZED"}""", code = 401))
        server.enqueue(json("""{"id":"r1"}"""))

        val room = client().get<Room>("/rooms/r1")

        assertEquals("r1", room.id)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer nuevo", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer nuevo", server.takeRequest().getHeader("Authorization"))
        assertEquals(listOf(false, true), asked)
        assertEquals("nuevo", tokens.hostToken)
        assertEquals(0, expired)
    }

    @Test
    fun `un 401 repetido borra la sesion y avisa`() = runBlocking {
        tokens.hostToken = "viejo"
        server.enqueue(json("""{"error":"UNAUTHORIZED"}""", code = 401))
        server.enqueue(json("""{"error":"UNAUTHORIZED"}""", code = 401))

        try {
            client().get<Room>("/rooms/r1")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(401, e.status)
        }

        assertNull(tokens.hostToken)
        assertEquals(1, expired)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `un 401 sin poder renovar borra la sesion sin reintentar`() = runBlocking {
        tokens.hostToken = "viejo"
        provided = "viejo"
        server.enqueue(json("""{"error":"UNAUTHORIZED"}""", code = 401))
        providerFails = false
        val c = client()
        provided = null

        try {
            c.get<Room>("/rooms/r1")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(401, e.status)
        }

        assertNull(tokens.hostToken)
        assertEquals(1, expired)
        assertEquals(1, server.requestCount)
    }
}
