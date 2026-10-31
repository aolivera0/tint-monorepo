package com.tint.app.sync

import com.tint.app.net.ApiClient
import com.tint.app.net.InMemoryTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var repo: PlaybackRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore().also { it.guestToken = "guest-jwt" }
        val api = ApiClient(server.url("/api/v1").toString().removeSuffix("/"), OkHttpClient(), tokens)
        repo = PlaybackRepository(api, tokens)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `pide la reproduccion con el token de invitado aunque haya sesion de host`() = runBlocking {
        tokens.hostToken = "host"
        server.enqueue(
            json(
                """{"mediaId":"m1","url":"https://minio/x.mp4","expiresAt":"2026-10-31T13:00:00Z","status":"ready",
                "ambilightUrl":"https://a","qualities":[{"id":"source","label":"Original","url":"https://q"}],"nuevo":1}""",
            ),
        )

        val result = repo.get("r1")

        assertEquals(
            PlaybackResult.Loaded(RoomPlayback("m1", "https://minio/x.mp4", "2026-10-31T13:00:00Z", "ready")),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/api/v1/rooms/r1/playback", request.path)
        assertEquals("Bearer guest-jwt", request.getHeader("Authorization"))
    }

    @Test
    fun `una sala sin video llega con mediaId nulo`() = runBlocking {
        server.enqueue(json("""{"mediaId":null,"url":null,"expiresAt":null,"status":null}"""))

        assertEquals(PlaybackResult.Loaded(RoomPlayback(null, null, null, null)), repo.get("r1"))
    }

    @Test
    fun `un 401 o 403 significa invitacion revocada y borra el token`() = runBlocking {
        server.enqueue(json("""{"error":"x"}""", code = 401))
        assertEquals(PlaybackResult.Revoked, repo.get("r1"))
        assertNull(tokens.guestToken)

        tokens.guestToken = "otra"
        server.enqueue(json("""{"error":"x"}""", code = 403))
        assertEquals(PlaybackResult.Revoked, repo.get("r1"))
        assertNull(tokens.guestToken)
    }

    @Test
    fun `una sala eliminada se explica`() = runBlocking {
        server.enqueue(json("""{"error":"x"}""", code = 404))
        server.enqueue(json("""{"error":"x"}""", code = 410))

        assertEquals(PlaybackResult.Failure("La sala ya no está disponible."), repo.get("r1"))
        assertEquals(PlaybackResult.Failure("La sala ya no está disponible."), repo.get("r1"))
    }

    @Test
    fun `sin red y otros errores tienen su mensaje`() = runBlocking {
        server.enqueue(json("""{"error":"boom"}""", code = 500))
        assertEquals(PlaybackResult.Failure("No se pudo cargar el vídeo"), repo.get("r1"))

        server.shutdown()
        assertEquals(
            PlaybackResult.Failure("No se pudo conectar con el servidor. Revisa tu conexión."),
            repo.get("r1"),
        )
    }
}
