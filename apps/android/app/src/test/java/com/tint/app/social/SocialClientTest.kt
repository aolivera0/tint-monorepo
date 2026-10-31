package com.tint.app.social

import com.tint.app.sync.SyncSocket
import com.tint.app.sync.SyncSocketFactory
import com.tint.app.sync.SyncSocketListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SocialClientTest {
    private class FakeSocket(val url: String, val listener: SyncSocketListener) : SyncSocket {
        val sent = mutableListOf<String>()
        var closedWith: Int? = null
        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun close(code: Int, reason: String) {
            closedWith = code
        }
    }

    private val sockets = mutableListOf<FakeSocket>()
    private val sleeps = mutableListOf<Long>()
    private var token: String? = "guest-jwt"
    private var clock = 10_000L
    private var ids = 0
    private lateinit var client: SocialClient

    @Before
    fun setUp() {
        client = SocialClient(
            roomId = "r1",
            token = { token },
            urlFor = { room, t -> "ws://h/ws/social?room_id=$room&token=$t" },
            sockets = SyncSocketFactory { url, listener -> FakeSocket(url, listener).also { sockets += it } },
            scope = CoroutineScope(Dispatchers.Unconfined),
            nowMs = { clock },
            selfUserId = { "guest:me" },
            sleep = { sleeps += it },
            newId = { "id-${ids++}" },
            wallClock = { "2026-10-31T12:00:00Z" },
        )
    }

    private fun open(): FakeSocket {
        client.start()
        return sockets.last().also { it.listener.onOpen() }
    }

    private fun chat(id: String, text: String = "hola", user: String = "guest:a", nick: String = "Ana", extra: String = "") =
        """{"event_id":"$id","type":"chat_message","room_id":"r1","user_id":"$user","nick":"$nick","text":"$text",
        "occurred_at":"x","received_at":"x"$extra}"""

    private fun settings(slow: Int, muted: String = "") =
        """{"type":"chat_settings","slow_mode_s":$slow,"muted_user_ids":[$muted]}"""

    private fun sentJson(socket: FakeSocket, index: Int = 0) = Json.parseToJsonElement(socket.sent[index]).jsonObject

    @Test
    fun `conecta con el token del invitado`() {
        client.start()

        assertEquals(listOf("ws://h/ws/social?room_id=r1&token=guest-jwt"), sockets.map { it.url })
        assertFalse(client.state.value.connected)
        sockets[0].listener.onOpen()
        assertTrue(client.state.value.connected)
    }

    @Test
    fun `sin token no conecta`() {
        token = null

        client.start()

        assertEquals(0, sockets.size)
    }

    @Test
    fun `el historial se muestra al entrar y se queda con los 50 ultimos`() {
        val socket = open()
        val many = (1..60).joinToString(",") { chat("h$it", "m$it") }

        socket.listener.onMessage("""{"type":"history","room_id":"r1","messages":[$many]}""")

        val ids = client.state.value.messages.map { it.eventId }
        assertEquals(50, ids.size)
        assertEquals("h11", ids.first())
        assertEquals("h60", ids.last())
    }

    @Test
    fun `un mensaje nuevo se agrega y un eco repetido no se duplica`() {
        val socket = open()
        socket.listener.onMessage("""{"type":"history","messages":[${chat("h1")}]}""")

        socket.listener.onMessage(chat("e1", "nuevo"))
        socket.listener.onMessage(chat("e1", "nuevo"))
        socket.listener.onMessage(chat("h1")) // ya estaba en el historial

        assertEquals(listOf("h1", "e1"), client.state.value.messages.map { it.eventId })
    }

    @Test
    fun `el chat nunca pasa de 50 mensajes`() {
        val socket = open()

        for (i in 1..70) socket.listener.onMessage(chat("m$i"))

        assertEquals(50, client.state.value.messages.size)
        assertEquals("m70", client.state.value.messages.last().eventId)
    }

    @Test
    fun `los avisos de presencia entran al chat`() {
        val socket = open()

        socket.listener.onMessage("""{"event_id":"n1","type":"notice","user_id":"u","nick":"Ana","kind":"joined","text":"Ana se unió"}""")

        assertEquals(ChatItem.Notice("n1", "Ana se unió"), client.state.value.messages.single())
    }

    @Test
    fun `enviar un mensaje manda chat_send con el texto recortado`() {
        val socket = open()

        val ok = client.send("  hola a todos  ")

        assertTrue(ok)
        val json = sentJson(socket)
        assertEquals("chat_send", json["type"]!!.jsonPrimitive.content)
        assertEquals("hola a todos", json["text"]!!.jsonPrimitive.content)
        assertEquals("r1", json["room_id"]!!.jsonPrimitive.content)
        assertEquals("id-0", json["event_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no envia vacio, demasiado largo ni sin conexion`() {
        assertFalse(client.send("hola")) // sin start

        val socket = open()
        assertFalse(client.send("   "))
        assertFalse(client.send("x".repeat(281)))
        assertTrue(client.send("x".repeat(280)))
        assertEquals(1, socket.sent.size)
    }

    @Test
    fun `el modo lento frena el segundo mensaje y lo explica`() {
        val socket = open()
        socket.listener.onMessage(settings(10))
        assertEquals(10, client.state.value.slowModeS)

        assertTrue(client.send("uno"))
        clock += 4_000
        val second = client.send("dos")

        assertFalse(second)
        assertEquals(1, socket.sent.size)
        assertEquals("El chat está en modo lento: espera un momento para volver a escribir.", client.state.value.error)
        assertEquals(10_000L + 10_000, client.state.value.cooldownUntilMs)

        clock += 6_000
        assertTrue(client.send("tres"))
    }

    @Test
    fun `sin modo lento se puede escribir seguido`() {
        val socket = open()
        socket.listener.onMessage(settings(0))

        assertTrue(client.send("uno"))
        assertTrue(client.send("dos"))
        assertNull(client.state.value.cooldownUntilMs)
    }

    @Test
    fun `si el host me silencia no puedo escribir y se me dice por que`() {
        val socket = open()

        socket.listener.onMessage(settings(0, "\"guest:other\",\"guest:me\""))

        assertTrue(client.state.value.muted)
        assertFalse(client.send("hola"))
        assertEquals(0, socket.sent.size)
        assertEquals("El host te silenció; puedes leer el chat.", client.state.value.error)

        socket.listener.onMessage(settings(0, "\"guest:other\""))
        assertFalse(client.state.value.muted)
        assertTrue(client.send("ya puedo"))
    }

    @Test
    fun `silenciar a otro no me afecta`() {
        val socket = open()

        socket.listener.onMessage(settings(0, "\"guest:other\""))

        assertFalse(client.state.value.muted)
        assertEquals(listOf("guest:other"), client.state.value.mutedUserIds)
    }

    @Test
    fun `los errores del servidor se muestran con un mensaje legible`() {
        val socket = open()

        socket.listener.onMessage("""{"type":"error","code":"MUTED"}""")
        assertEquals("El host te silenció; puedes leer el chat.", client.state.value.error)
        val first = client.state.value.errorSeq

        socket.listener.onMessage("""{"type":"error","code":"RATE_LIMITED"}""")
        assertEquals("Vas muy rápido: ve más despacio.", client.state.value.error)
        assertEquals(first + 1, client.state.value.errorSeq)
    }

    @Test
    fun `un SLOW_MODE del servidor arma la cuenta regresiva con su retry_after_ms`() {
        val socket = open()

        socket.listener.onMessage("""{"type":"error","code":"SLOW_MODE","retry_after_ms":4000}""")

        assertEquals(14_000L, client.state.value.cooldownUntilMs)
        assertFalse(client.send("pronto"))
    }

    @Test
    fun `enviar bien limpia el error anterior`() {
        val socket = open()
        socket.listener.onMessage("""{"type":"error","code":"RATE_LIMITED"}""")

        client.send("hola")

        assertNull(client.state.value.error)
    }

    @Test
    fun `reaccionar manda reaction_send con la posicion del video`() {
        val socket = open()

        assertTrue(client.sendReaction("😂", 61_234))

        val json = sentJson(socket)
        assertEquals("reaction_send", json["type"]!!.jsonPrimitive.content)
        assertEquals("😂", json["emoji"]!!.jsonPrimitive.content)
        assertEquals("61234", json["position_ms"]!!.jsonPrimitive.content)
    }

    @Test
    fun `solo se aceptan las reacciones del conjunto y no mas de 5 por segundo`() {
        val socket = open()

        assertFalse(client.sendReaction("🙂", 0))
        assertTrue(client.sendReaction("👏", 0))
        clock += 100
        assertFalse(client.sendReaction("👏", 0)) // < 200 ms
        clock += 100
        assertTrue(client.sendReaction("👏", 0))
        assertEquals(2, socket.sent.size)
    }

    @Test
    fun `las reacciones de la sala se guardan sin repetir y con tope`() {
        val socket = open()

        for (i in 1..30) {
            socket.listener.onMessage("""{"event_id":"x$i","type":"reaction","user_id":"u","nick":"Ana","emoji":"😂","position_ms":1}""")
        }
        socket.listener.onMessage("""{"event_id":"x30","type":"reaction","user_id":"u","nick":"Ana","emoji":"😂","position_ms":1}""")

        val ids = client.state.value.reactions.map { it.eventId }
        assertEquals(20, ids.size)
        assertEquals("x30", ids.last())
        assertEquals(1, ids.count { it == "x30" })
    }

    @Test
    fun `revocado y expirado cierran sin reconectar`() {
        val socket = open()
        socket.listener.onClosed(4001)
        assertTrue(client.state.value.revoked)

        client.stop()
        client.start()
        sockets.last().listener.onOpen()
        sockets.last().listener.onClosed(4003)
        assertTrue(client.state.value.expired)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `un corte reconecta y el historial nuevo reemplaza al viejo`() {
        val first = open()
        first.listener.onMessage("""{"type":"history","messages":[${chat("old")}]}""")
        first.listener.onClosed(1006)
        assertEquals(listOf(500L), sleeps)
        assertEquals(1, client.state.value.reconnectAttempt)

        val second = sockets.last()
        second.listener.onOpen()
        second.listener.onMessage("""{"type":"history","messages":[${chat("new")}]}""")

        assertEquals(listOf("new"), client.state.value.messages.map { it.eventId })
        assertEquals(0, client.state.value.reconnectAttempt)
    }

    @Test
    fun `stop cierra el socket`() {
        val socket = open()

        client.stop()

        assertEquals(1000, socket.closedWith)
        assertFalse(client.state.value.connected)
    }
}
