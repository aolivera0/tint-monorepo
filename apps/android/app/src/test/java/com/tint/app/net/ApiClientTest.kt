package com.tint.app.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    @Serializable
    private data class Room(val id: String, val title: String)

    @Serializable
    private data class CreateRoom(val title: String)

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private var expired = 0
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore()
        expired = 0
        client = ApiClient(
            baseUrl = server.url("/api/v1").toString().removeSuffix("/"),
            http = OkHttpClient(),
            tokens = tokens,
            onHostSessionExpired = { expired++ },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `usa las mismas rutas del gateway que la web`() = runBlocking {
        server.enqueue(json("""{"id":"r1","title":"Peli"}"""))

        client.get<Room>("/rooms/r1")

        assertEquals("/api/v1/rooms/r1", server.takeRequest().path)
    }

    @Test
    fun `ignora los campos desconocidos de la respuesta`() = runBlocking {
        server.enqueue(json("""{"id":"r1","title":"Peli","campoNuevo":{"a":1},"otro":true}"""))

        val room = client.get<Room>("/rooms/r1")

        assertEquals(Room("r1", "Peli"), room)
    }

    @Test
    fun `envia el cuerpo como json`() = runBlocking {
        server.enqueue(json("""{"id":"r2","title":"Nueva"}"""))

        client.post<CreateRoom, Room>("/rooms", CreateRoom("Nueva"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"title":"Nueva"}""", request.body.readUtf8())
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `auto prefiere el token de host`() = runBlocking {
        tokens.hostToken = "host-token"
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"id":"r1","title":"t"}"""))

        client.get<Room>("/rooms/r1")

        assertEquals("Bearer host-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `auto usa el token de invitado si no hay host`() = runBlocking {
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"id":"r1","title":"t"}"""))

        client.get<Room>("/rooms/r1")

        assertEquals("Bearer guest-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `guest ignora la sesion de host`() = runBlocking {
        tokens.hostToken = "host-token"
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"id":"r1","title":"t"}"""))

        client.get<Room>("/rooms/r1", ApiAuth.GUEST)

        assertEquals("Bearer guest-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `host sin sesion no envia credencial`() = runBlocking {
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"id":"r1","title":"t"}"""))

        client.get<Room>("/rooms/r1", ApiAuth.HOST)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `none no envia credencial aunque haya sesiones`() = runBlocking {
        tokens.hostToken = "host-token"
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"id":"r1","title":"t"}"""))

        client.get<Room>("/rooms/r1", ApiAuth.NONE)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `sin credenciales no envia Authorization`() = runBlocking {
        server.enqueue(json("""{"id":"r1","title":"t"}"""))

        client.get<Room>("/rooms/r1")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `un error trae el estado y el mensaje del servidor`() = runBlocking {
        server.enqueue(json("""{"error":"BANNED"}""", code = 403))

        try {
            client.get<Room>("/rooms/r1")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(403, e.status)
            assertEquals("BANNED", e.message)
        }
    }

    @Test
    fun `un error sin json usa un mensaje generico`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        try {
            client.get<Room>("/rooms/r1")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(502, e.status)
            assertEquals("API error 502", e.message)
        }
    }

    @Test
    fun `un 401 con sesion de host la borra y avisa`() = runBlocking {
        tokens.hostToken = "host-token"
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"error":"UNAUTHORIZED"}""", code = 401))

        try {
            client.get<Room>("/rooms")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(401, e.status)
        }

        assertNull(tokens.hostToken)
        assertEquals("guest-token", tokens.guestToken)
        assertEquals(1, expired)
    }

    @Test
    fun `un 401 con token de invitado no toca la sesion de host`() = runBlocking {
        tokens.guestToken = "guest-token"
        server.enqueue(json("""{"error":"UNAUTHORIZED"}""", code = 401))

        try {
            client.get<Room>("/rooms/r1")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(401, e.status)
        }

        assertEquals("guest-token", tokens.guestToken)
        assertEquals(0, expired)
    }

    @Test
    fun `un fallo de red se informa como ApiError con estado 0`() = runBlocking {
        server.shutdown()

        try {
            client.get<Room>("/rooms/r1")
            fail("debia lanzar ApiError")
        } catch (e: ApiError) {
            assertEquals(0, e.status)
            assertFalse(e.message.isNullOrBlank())
        }
    }
}
