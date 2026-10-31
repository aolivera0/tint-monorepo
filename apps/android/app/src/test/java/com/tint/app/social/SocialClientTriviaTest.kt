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

class SocialClientTriviaTest {
    private class FakeSocket(val listener: SyncSocketListener) : SyncSocket {
        val sent = mutableListOf<String>()
        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun close(code: Int, reason: String) = Unit
    }

    private val sockets = mutableListOf<FakeSocket>()
    private var clock = 50_000L
    private var ids = 0
    private lateinit var client: SocialClient
    private lateinit var socket: FakeSocket

    @Before
    fun setUp() {
        client = SocialClient(
            roomId = "r1",
            token = { "guest-jwt" },
            urlFor = { room, t -> "ws://h/ws/social?room_id=$room&token=$t" },
            sockets = SyncSocketFactory { _, listener -> FakeSocket(listener).also { sockets += it } },
            scope = CoroutineScope(Dispatchers.Unconfined),
            nowMs = { clock },
            selfUserId = { "guest:me" },
            sleep = { },
            newId = { "id-${ids++}" },
            wallClock = { "2026-10-31T12:00:00Z" },
        )
        client.start()
        socket = sockets.last().also { it.listener.onOpen() }
    }

    private fun question(id: String = "t1", opens: Long = 1_000_000, closes: Long = 1_020_000) =
        """{"event_id":"q-$id","type":"trivia_question","trivia_id":"$id","question":"¿Pregunta $id?",
        "options":["A","B","C"],"opens_at_ms":$opens,"closes_at_ms":$closes}"""

    private fun result(id: String = "t1", correct: Int = 1, myScore: String = ",\"my_score\":1500,\"my_total\":2500") =
        """{"event_id":"r-$id","type":"trivia_result","trivia_id":"$id","correct":$correct,
        "top":[{"user_id":"u1","nick":"Ana","score":1800}],"leaderboard":[{"user_id":"u1","nick":"Ana","score":2500}]$myScore}"""

    @Test
    fun `sin pregunta no hay trivia`() {
        assertNull(client.state.value.trivia)
        assertFalse(client.answerTrivia(0))
    }

    @Test
    fun `una pregunta lanzada aparece con su hora de llegada`() {
        socket.listener.onMessage(question())

        val t = client.state.value.trivia!!
        assertEquals("¿Pregunta t1?", t.question.question)
        assertEquals(50_000L, t.receivedAtMs)
        assertEquals(20_000L, t.windowMs)
        assertNull(t.myOption)
    }

    @Test
    fun `la cuenta regresiva usa la duracion de la ventana y el reloj local, no el del servidor`() {
        socket.listener.onMessage(question())
        val t = client.state.value.trivia!!

        assertEquals(20_000L, t.remainingMs(50_000))
        assertEquals(15_000L, t.remainingMs(55_000))
        assertEquals(0L, t.remainingMs(80_000))
    }

    @Test
    fun `responder manda trivia_answer con el indice y recuerda mi opcion`() {
        socket.listener.onMessage(question())

        assertTrue(client.answerTrivia(2))

        val json = Json.parseToJsonElement(socket.sent.single()).jsonObject
        assertEquals("trivia_answer", json["type"]!!.jsonPrimitive.content)
        assertEquals("t1", json["trivia_id"]!!.jsonPrimitive.content)
        assertEquals("2", json["option"]!!.jsonPrimitive.content)
        assertEquals(2, client.state.value.trivia!!.myOption)
    }

    @Test
    fun `solo se responde una vez`() {
        socket.listener.onMessage(question())
        client.answerTrivia(0)

        assertFalse(client.answerTrivia(1))

        assertEquals(1, socket.sent.size)
        assertEquals(0, client.state.value.trivia!!.myOption)
    }

    @Test
    fun `una opcion fuera de rango no se envia`() {
        socket.listener.onMessage(question())

        assertFalse(client.answerTrivia(-1))
        assertFalse(client.answerTrivia(3))
        assertEquals(0, socket.sent.size)
    }

    @Test
    fun `pasada la ventana no se envia y se explica`() {
        socket.listener.onMessage(question())
        clock += 21_000

        assertFalse(client.answerTrivia(0))

        assertEquals(0, socket.sent.size)
        assertEquals("Se acabó el tiempo para responder.", client.state.value.error)
    }

    @Test
    fun `el ack trae el puntaje y la opcion`() {
        socket.listener.onMessage(question())
        client.answerTrivia(1)

        socket.listener.onMessage("""{"type":"trivia_ack","trivia_id":"t1","option":1,"score":1450}""")

        assertEquals(TriviaAck("t1", 1, 1450), client.state.value.trivia!!.ack)
    }

    @Test
    fun `un ack de otra pregunta se ignora`() {
        socket.listener.onMessage(question("t1"))

        socket.listener.onMessage("""{"type":"trivia_ack","trivia_id":"otra","option":1,"score":10}""")

        assertNull(client.state.value.trivia!!.ack)
    }

    @Test
    fun `el resultado trae la correcta, mis puntos y el ranking`() {
        socket.listener.onMessage(question())
        client.answerTrivia(1)
        socket.listener.onMessage("""{"type":"trivia_ack","trivia_id":"t1","option":1,"score":1500}""")

        socket.listener.onMessage(result())

        val r = client.state.value.trivia!!.result!!
        assertEquals(1, r.correct)
        assertEquals(1500, r.myScore)
        assertEquals(2500, r.myTotal)
        assertEquals("Ana", r.top.single().nick)
        assertEquals(0L, client.state.value.trivia!!.remainingMs(clock))
    }

    @Test
    fun `si no respondi, el cierre llega con cero puntos`() {
        socket.listener.onMessage(question())

        socket.listener.onMessage(result(myScore = ",\"my_score\":0,\"my_total\":2500"))

        val t = client.state.value.trivia!!
        assertNull(t.myOption)
        assertEquals(0, t.result!!.myScore)
        assertFalse(client.answerTrivia(0)) // ya cerró
    }

    @Test
    fun `un resultado de una pregunta que no tengo se ignora`() {
        socket.listener.onMessage(question("t1"))

        socket.listener.onMessage(result("otra"))

        assertNull(client.state.value.trivia!!.result)
    }

    @Test
    fun `una pregunta nueva reemplaza a la anterior`() {
        socket.listener.onMessage(question("t1"))
        client.answerTrivia(0)
        clock += 30_000

        socket.listener.onMessage(question("t2"))

        val t = client.state.value.trivia!!
        assertEquals("t2", t.question.triviaId)
        assertNull(t.myOption)
        assertEquals(clock, t.receivedAtMs)
    }

    @Test
    fun `la misma pregunta reenviada al reconectar conserva mi respuesta y la cuenta regresiva`() {
        socket.listener.onMessage(question("t1"))
        client.answerTrivia(1)
        clock += 8_000

        socket.listener.onMessage(question("t1"))

        val t = client.state.value.trivia!!
        assertEquals(1, t.myOption)
        assertEquals(50_000L, t.receivedAtMs)
    }

    @Test
    fun `los errores de la trivia se muestran`() {
        socket.listener.onMessage("""{"type":"error","code":"ALREADY_ANSWERED"}""")
        assertEquals("Ya respondiste esta pregunta.", client.state.value.error)

        socket.listener.onMessage("""{"type":"error","code":"TRIVIA_CLOSED"}""")
        assertEquals("Se acabó el tiempo para responder.", client.state.value.error)
    }

    @Test
    fun `descartar la trivia la quita de la pantalla`() {
        socket.listener.onMessage(question())

        client.dismissTrivia()

        assertNull(client.state.value.trivia)
    }
}
