package com.tint.app.join

import com.tint.app.identity.DeviceIdProvider
import com.tint.app.net.ApiClient
import com.tint.app.net.InMemoryTokenStore
import com.tint.app.store.MemoryStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InviteRepositoryTest {
    private val deviceId = "11111111-1111-4111-8111-111111111111"
    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var repo: InviteRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore()
        val api = ApiClient(server.url("/api/v1").toString().removeSuffix("/"), OkHttpClient(), tokens)
        repo = InviteRepository(api, tokens, DeviceIdProvider(MemoryStore()) { deviceId })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `canjea el codigo con nick y deviceId y guarda el token de invitado`() = runBlocking {
        server.enqueue(json("""{"token":"guest-jwt","roomId":"r1","nick":"Ana","roomTitle":"Peli","extra":1}"""))

        val result = repo.claim("wVRbfj_3dEnd", "Ana")

        assertEquals(ClaimResult.Joined(JoinedRoom("r1", "Peli", "Ana")), result)
        assertEquals("guest-jwt", tokens.guestToken)
        val request = server.takeRequest()
        assertEquals("/api/v1/invitations/claim", request.path)
        assertEquals("""{"code":"wVRbfj_3dEnd","nick":"Ana","deviceId":"$deviceId"}""", request.body.readUtf8())
    }

    @Test
    fun `una sala sin titulo se acepta`() = runBlocking {
        server.enqueue(json("""{"token":"t","roomId":"r1","nick":"Ana","roomTitle":null}"""))

        val result = repo.claim("abcdef", "Ana")

        assertEquals(ClaimResult.Joined(JoinedRoom("r1", null, "Ana")), result)
    }

    @Test
    fun `no toca la sesion de host`() = runBlocking {
        tokens.hostToken = "host"
        server.enqueue(json("""{"token":"guest","roomId":"r1","nick":"Ana","roomTitle":"Peli"}"""))

        repo.claim("abcdef", "Ana")

        assertEquals("host", tokens.hostToken)
        assertEquals("guest", tokens.guestToken)
        // El canje es anónimo: no manda la credencial de host.
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `mensajes por estado, iguales que en la web`() = runBlocking {
        val cases = mapOf(
            404 to "Esta invitación no existe o la sala fue eliminada.",
            409 to "Esta invitación fue revocada por el host.",
            410 to "Esta invitación expiró o la sala fue eliminada.",
        )
        for ((status, message) in cases) {
            server.enqueue(json("""{"error":"x"}""", code = status))
            assertEquals(ClaimResult.Failure(message), repo.claim("abcdef", "Ana"))
        }
        assertNull(tokens.guestToken)
    }

    @Test
    fun `un bloqueo se dice claro`() = runBlocking {
        server.enqueue(json("""{"error":"BANNED"}""", code = 403))

        assertEquals(ClaimResult.Failure("El host te bloqueó en esta sala"), repo.claim("abcdef", "Ana"))
    }

    @Test
    fun `sin red el mensaje es comprensible`() = runBlocking {
        server.shutdown()

        assertEquals(
            ClaimResult.Failure("No se pudo conectar con el servidor. Revisa tu conexión."),
            repo.claim("abcdef", "Ana"),
        )
    }

    @Test
    fun `otros errores usan un mensaje generico`() = runBlocking {
        server.enqueue(json("""{"error":"boom"}""", code = 500))

        assertEquals(ClaimResult.Failure("No se pudo unir a la sala"), repo.claim("abcdef", "Ana"))
    }
}
