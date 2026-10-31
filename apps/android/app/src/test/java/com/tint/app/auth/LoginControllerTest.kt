package com.tint.app.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginControllerTest {
    private class FakeAuth(var signedIn: Boolean = false) : SessionAuth {
        var next: SignInResult = SignInResult.Success
        var gate: CompletableDeferred<Unit>? = null
        var signOuts = 0

        override fun isSignedIn() = signedIn

        override suspend fun signInWithGoogle(): SignInResult {
            gate?.await()
            if (next == SignInResult.Success) signedIn = true
            return next
        }

        override suspend fun signOut() {
            signOuts++
            signedIn = false
        }
    }

    private fun controller(auth: FakeAuth) = LoginController(auth, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `arranca en sesion cerrada sin token`() {
        assertEquals(LoginState.SignedOut, controller(FakeAuth(signedIn = false)).state.value)
    }

    @Test
    fun `arranca con sesion si ya hay token de host`() {
        assertEquals(LoginState.SignedIn, controller(FakeAuth(signedIn = true)).state.value)
    }

    @Test
    fun `inicia sesion pasando por cargando`() = runBlocking {
        val auth = FakeAuth().apply { gate = CompletableDeferred() }
        val c = controller(auth)

        c.signIn()
        assertEquals(LoginState.Loading, c.state.value)

        auth.gate!!.complete(Unit)
        assertEquals(LoginState.SignedIn, c.state.value)
    }

    @Test
    fun `un fallo muestra el mensaje y se puede reintentar`() {
        val auth = FakeAuth().apply { next = SignInResult.Failure("Cancelaste el inicio de sesión.") }
        val c = controller(auth)

        c.signIn()
        assertEquals(LoginState.Error("Cancelaste el inicio de sesión."), c.state.value)

        auth.next = SignInResult.Success
        c.signIn()
        assertEquals(LoginState.SignedIn, c.state.value)
    }

    @Test
    fun `ignora un segundo toque mientras carga`() = runBlocking {
        val auth = FakeAuth().apply { gate = CompletableDeferred() }
        var calls = 0
        val counting = object : SessionAuth by auth {
            override suspend fun signInWithGoogle(): SignInResult {
                calls++
                return auth.signInWithGoogle()
            }
        }
        val c = LoginController(counting, CoroutineScope(Dispatchers.Unconfined))

        c.signIn()
        c.signIn()
        auth.gate!!.complete(Unit)

        assertEquals(1, calls)
    }

    @Test
    fun `cerrar sesion vuelve a la pantalla de acceso`() {
        val auth = FakeAuth(signedIn = true)
        val c = controller(auth)

        c.signOut()

        assertEquals(LoginState.SignedOut, c.state.value)
        assertEquals(1, auth.signOuts)
    }

    @Test
    fun `si la sesion de host caduca vuelve al acceso`() {
        val c = controller(FakeAuth(signedIn = true))

        c.onSessionExpired()

        assertEquals(
            LoginState.Error("Tu sesión caducó. Inicia sesión de nuevo."),
            c.state.value,
        )
    }
}
