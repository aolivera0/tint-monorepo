package com.tint.app.auth

import com.tint.app.net.ApiClient
import com.tint.app.net.InMemoryTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private class FakeGoogle(var result: () -> String = { "google-id-token" }) : GoogleIdTokenSource {
        var calls = 0
        override suspend fun getGoogleIdToken(): String {
            calls++
            return result()
        }
    }

    private class FakeFirebase(var exchange: (String) -> String = { "firebase-id-token" }) : FirebaseGateway {
        val exchanged = mutableListOf<String>()
        var signedOut = 0
        override suspend fun signInWithGoogle(googleIdToken: String): String {
            exchanged += googleIdToken
            return exchange(googleIdToken)
        }

        override suspend fun idToken(forceRefresh: Boolean): String? = null

        override fun signOut() {
            signedOut++
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var google: FakeGoogle
    private lateinit var firebase: FakeFirebase
    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore()
        google = FakeGoogle()
        firebase = FakeFirebase()
        val api = ApiClient(server.url("/api/v1").toString().removeSuffix("/"), OkHttpClient(), tokens)
        repo = AuthRepository(api, tokens, google, firebase)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun verifyOk() = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"user":{"id":"u1","nuevo":true},"isRegistered":true}""")

    @Test
    fun `sin token de host no hay sesion`() {
        assertFalse(repo.isSignedIn())
    }

    @Test
    fun `inicia sesion, guarda el token y verifica con el backend`() = runBlocking {
        tokens.guestToken = "invitado-viejo"
        server.enqueue(verifyOk())

        val result = repo.signInWithGoogle()

        assertEquals(SignInResult.Success, result)
        assertEquals(listOf("google-id-token"), firebase.exchanged)
        assertEquals("firebase-id-token", tokens.hostToken)
        assertNull(tokens.guestToken)
        assertTrue(repo.isSignedIn())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/verify", request.path)
        assertEquals("Bearer firebase-id-token", request.getHeader("Authorization"))
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun `cancelar explica que paso y permite reintentar`() = runBlocking {
        google.result = { throw GoogleSignInException.Cancelled() }

        val result = repo.signInWithGoogle()

        assertEquals(SignInResult.Failure("Cancelaste el inicio de sesión. Puedes intentarlo de nuevo."), result)
        assertNull(tokens.hostToken)
        assertEquals(0, server.requestCount)

        google.result = { "google-id-token" }
        server.enqueue(verifyOk())
        assertEquals(SignInResult.Success, repo.signInWithGoogle())
    }

    @Test
    fun `un fallo de Google se informa con su mensaje`() = runBlocking {
        google.result = { throw GoogleSignInException.Failed("Sin cuentas de Google en el dispositivo") }

        val result = repo.signInWithGoogle()

        assertEquals(SignInResult.Failure("Sin cuentas de Google en el dispositivo"), result)
        assertNull(tokens.hostToken)
    }

    @Test
    fun `un fallo al canjear en Firebase no deja sesion`() = runBlocking {
        firebase.exchange = { error("firebase rechazó el token") }

        val result = repo.signInWithGoogle()

        assertEquals(SignInResult.Failure("No se pudo iniciar sesión"), result)
        assertNull(tokens.hostToken)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `si el backend rechaza el token borra la sesion y cierra Firebase`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("""{"error":"UNAUTHORIZED"}"""),
        )

        val result = repo.signInWithGoogle()

        assertTrue(result is SignInResult.Failure)
        assertNull(tokens.hostToken)
        assertFalse(repo.isSignedIn())
        assertEquals(1, firebase.signedOut)
    }

    @Test
    fun `sin red el mensaje es comprensible`() = runBlocking {
        server.shutdown()

        val result = repo.signInWithGoogle()

        assertEquals(SignInResult.Failure("No se pudo conectar con el servidor. Revisa tu conexión."), result)
        assertNull(tokens.hostToken)
        assertEquals(1, firebase.signedOut)
    }

    @Test
    fun `cerrar sesion borra las credenciales y exige iniciar de nuevo`() = runBlocking {
        tokens.hostToken = "host"
        tokens.guestToken = "guest"

        repo.signOut()

        assertNull(tokens.hostToken)
        assertNull(tokens.guestToken)
        assertFalse(repo.isSignedIn())
        assertEquals(1, firebase.signedOut)
    }
}
