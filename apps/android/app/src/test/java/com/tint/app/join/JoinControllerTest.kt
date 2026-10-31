package com.tint.app.join

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class JoinControllerTest {
    private class FakeInvites : RoomInvites {
        var result: ClaimResult = ClaimResult.Joined(JoinedRoom("r1", "Peli", "Ana"))
        var gate: CompletableDeferred<Unit>? = null
        val claims = mutableListOf<Pair<String, String>>()

        override suspend fun claim(code: String, nick: String): ClaimResult {
            claims += code to nick
            gate?.await()
            return result
        }
    }

    private fun controller(invites: FakeInvites) = JoinController(invites, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `empieza sin hacer nada`() {
        assertEquals(JoinState.Idle, controller(FakeInvites()).state.value)
    }

    @Test
    fun `entra a la sala con un codigo valido`() {
        val invites = FakeInvites()
        val c = controller(invites)

        c.submit("https://tint.jaucor.online/invite/wVRbfj_3dEnd", "  Ana ")

        assertEquals(JoinState.Joined(JoinedRoom("r1", "Peli", "Ana")), c.state.value)
        assertEquals(listOf("wVRbfj_3dEnd" to "Ana"), invites.claims)
    }

    @Test
    fun `pasa por uniendose`() = runBlocking {
        val invites = FakeInvites().apply { gate = CompletableDeferred() }
        val c = controller(invites)

        c.submit("abcdef12", "Ana")
        assertEquals(JoinState.Joining, c.state.value)

        invites.gate!!.complete(Unit)
        assertEquals(JoinState.Joined(JoinedRoom("r1", "Peli", "Ana")), c.state.value)
    }

    @Test
    fun `un codigo que no parece valido no llama al servidor`() {
        val invites = FakeInvites()
        val c = controller(invites)

        c.submit("hola mundo", "Ana")

        assertEquals(
            JoinState.Error("Ese link no parece válido. Revisa que incluya /invite/ o el código."),
            c.state.value,
        )
        assertEquals(0, invites.claims.size)
    }

    @Test
    fun `el nick debe tener entre 2 y 24 caracteres`() {
        val invites = FakeInvites()
        val c = controller(invites)

        c.submit("abcdef12", " A ")
        assertEquals(JoinState.Error("El nick debe tener entre 2 y 24 caracteres."), c.state.value)

        c.submit("abcdef12", "x".repeat(25))
        assertEquals(JoinState.Error("El nick debe tener entre 2 y 24 caracteres."), c.state.value)
        assertEquals(0, invites.claims.size)
    }

    @Test
    fun `un codigo rechazado muestra el error y se puede reintentar`() {
        val invites = FakeInvites().apply {
            result = ClaimResult.Failure("Esta invitación fue revocada por el host.")
        }
        val c = controller(invites)

        c.submit("abcdef12", "Ana")
        assertEquals(JoinState.Error("Esta invitación fue revocada por el host."), c.state.value)

        invites.result = ClaimResult.Joined(JoinedRoom("r2", "Otra", "Ana"))
        c.submit("zzzzzz99", "Ana")
        assertEquals(JoinState.Joined(JoinedRoom("r2", "Otra", "Ana")), c.state.value)
    }

    @Test
    fun `ignora un segundo envio mientras se une`() = runBlocking {
        val invites = FakeInvites().apply { gate = CompletableDeferred() }
        val c = controller(invites)

        c.submit("abcdef12", "Ana")
        c.submit("abcdef12", "Ana")
        invites.gate!!.complete(Unit)

        assertEquals(1, invites.claims.size)
    }

    @Test
    fun `salir de la sala vuelve al inicio`() {
        val c = controller(FakeInvites())
        c.submit("abcdef12", "Ana")

        c.leave()

        assertEquals(JoinState.Idle, c.state.value)
    }
}
