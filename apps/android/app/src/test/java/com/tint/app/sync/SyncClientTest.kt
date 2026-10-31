package com.tint.app.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncClientTest {
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
    private var clock = 1_000L
    private var localPosition: Long? = 4_321L
    private var ids = 0
    private lateinit var client: SyncClient

    @Before
    fun setUp() {
        client = SyncClient(
            roomId = "r1",
            token = { token },
            urlFor = { room, t -> "ws://h/ws/sync?room_id=$room&token=$t" },
            sockets = SyncSocketFactory { url, listener -> FakeSocket(url, listener).also { sockets += it } },
            scope = CoroutineScope(Dispatchers.Unconfined),
            nowMs = { clock },
            localPositionMs = { localPosition },
            sleep = { sleeps += it },
            newId = { "id-${ids++}" },
            wallClock = { "2026-10-31T12:00:00Z" },
        )
    }

    private fun stateFrame(version: Int, status: String = "playing", position: Long = 5000, media: String = "m1", type: String = "STATE", extra: String = "") =
        """{"type":"$type","status":"$status","position_ms":$position,"updated_at_ms":1000,"rate":1,"version":$version,
        "server_time_ms":2000,"media_id":"$media"$extra}"""

    private fun open(): FakeSocket {
        client.start()
        return sockets.last().also { it.listener.onOpen() }
    }

    @Test
    fun `conecta con el token del invitado`() {
        client.start()

        assertEquals(listOf("ws://h/ws/sync?room_id=r1&token=guest-jwt"), sockets.map { it.url })
        assertFalse(client.state.value.connected)
        sockets[0].listener.onOpen()
        assertTrue(client.state.value.connected)
    }

    @Test
    fun `sin token no conecta ni reintenta`() {
        token = null

        client.start()

        assertEquals(0, sockets.size)
        assertEquals("NO_TOKEN", client.state.value.lastError)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `dos start seguidos abren un solo socket`() {
        client.start()
        client.start()

        assertEquals(1, sockets.size)
    }

    @Test
    fun `el STATE inicial fija snapshot, anfitrion y permisos`() {
        val socket = open()
        clock = 1_234

        socket.listener.onMessage(stateFrame(7, extra = ""","online":true,"can_control":false,"volume":0.3,"quality":"360p""""))

        val ui = client.state.value
        assertEquals(7L, ui.snapshot!!.state.version)
        assertEquals(1_234L, ui.snapshot!!.receivedAtMs)
        assertEquals(true, ui.hostOnline)
        assertFalse(ui.canControl)
        assertEquals(0.3, ui.volume, 0.0)
        assertEquals("360p", ui.quality)
    }

    @Test
    fun `responde cada HEARTBEAT con un SYNC_CHECK`() {
        val socket = open()
        socket.listener.onMessage(stateFrame(7))

        socket.listener.onMessage("""{"type":"HEARTBEAT","server_time_ms":9000,"rtt_ms":30}""")

        val check = Json.parseToJsonElement(socket.sent.single()).jsonObject
        assertEquals("SYNC_CHECK", check["type"]!!.jsonPrimitive.content)
        assertEquals("9000", check["echo_server_ts"]!!.jsonPrimitive.content)
        assertEquals("4321", check["position_ms"]!!.jsonPrimitive.content)
        assertEquals("7", check["version"]!!.jsonPrimitive.content)
        assertEquals("id-0", check["event_id"]!!.jsonPrimitive.content)
        assertEquals(30L, client.state.value.rttMs)
    }

    @Test
    fun `antes del primer STATE el SYNC_CHECK no lleva version y usa la posicion esperada`() {
        val socket = open()
        localPosition = null

        socket.listener.onMessage("""{"type":"HEARTBEAT","server_time_ms":9000}""")

        val check = Json.parseToJsonElement(socket.sent.single()).jsonObject
        assertFalse(check.containsKey("version"))
        assertEquals("0", check["position_ms"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sin posicion local usa la posicion maestra esperada`() {
        val socket = open()
        clock = 1_000
        socket.listener.onMessage(stateFrame(1, position = 5000)) // ancla en updated_at 1000, server_time 2000
        localPosition = null
        clock = 1_500 // 500 ms despues: servidor ~2500, 1500 ms desde el ancla

        socket.listener.onMessage("""{"type":"HEARTBEAT","server_time_ms":2500}""")

        val check = Json.parseToJsonElement(socket.sent.single()).jsonObject
        assertEquals("6500", check["position_ms"]!!.jsonPrimitive.content)
    }

    @Test
    fun `un STATE con version menor en la misma conexion no pisa el estado`() {
        val socket = open()
        socket.listener.onMessage(stateFrame(9))

        socket.listener.onMessage(stateFrame(4, status = "paused"))

        assertEquals(9L, client.state.value.snapshot!!.state.version)
        assertEquals(PlaybackStatus.PLAYING, client.state.value.snapshot!!.state.status)
    }

    @Test
    fun `el primer STATE de una conexion nueva siempre aplica aunque la version sea menor`() {
        val first = open()
        first.listener.onMessage(stateFrame(9))
        first.listener.onClosed(1006)

        val second = sockets.last()
        second.listener.onOpen()
        second.listener.onMessage(stateFrame(2, status = "paused"))

        assertEquals(2L, client.state.value.snapshot!!.state.version)
    }

    @Test
    fun `los broadcasts viejos se descartan y los nuevos se aplican`() {
        val socket = open()
        socket.listener.onMessage(stateFrame(5))

        socket.listener.onMessage(stateFrame(5, type = "PAUSE", status = "paused"))
        socket.listener.onMessage(stateFrame(4, type = "PAUSE", status = "paused"))
        assertEquals(PlaybackStatus.PLAYING, client.state.value.snapshot!!.state.status)

        socket.listener.onMessage(stateFrame(6, type = "PAUSE", status = "paused", position = 8000))
        assertEquals(PlaybackStatus.PAUSED, client.state.value.snapshot!!.state.status)
        assertEquals(6L, client.state.value.snapshot!!.state.version)
        assertEquals(8000L, client.state.value.snapshot!!.state.positionMs)
    }

    @Test
    fun `una correccion expone el objetivo con un numero de secuencia creciente`() {
        val socket = open()
        socket.listener.onMessage(stateFrame(3))

        socket.listener.onMessage("""{"type":"SYNC_CORRECTION","target_ms":9000,"strategy":"seek","drift_ms":-700,"version":3}""")
        socket.listener.onMessage("""{"type":"SYNC_CORRECTION","target_ms":9100,"strategy":"rate","drift_ms":600,"version":3,"rate":1}""")

        val c = client.state.value.correction!!
        assertEquals(2, c.seq)
        assertEquals(9100L, c.targetMs)
        assertEquals(CorrectionStrategy.RATE, c.strategy)
        assertEquals(1.0, c.rate!!, 0.0)
        assertEquals(600L, client.state.value.driftMs)
    }

    @Test
    fun `una correccion con version menor se ignora`() {
        val socket = open()
        socket.listener.onMessage(stateFrame(8))

        socket.listener.onMessage("""{"type":"SYNC_CORRECTION","target_ms":1,"strategy":"seek","drift_ms":-900,"version":2}""")

        assertNull(client.state.value.correction)
    }

    @Test
    fun `una correccion con otro estado reconcilia un broadcast perdido`() {
        val socket = open()
        clock = 1_000
        socket.listener.onMessage(stateFrame(3, status = "playing"))
        clock = 1_200

        socket.listener.onMessage(
            """{"type":"SYNC_CORRECTION","target_ms":7000,"strategy":"seek","drift_ms":-900,"version":4,
            "status":"paused","rate":1,"media_id":"m1"}""",
        )

        val s = client.state.value.snapshot!!
        assertEquals(PlaybackStatus.PAUSED, s.state.status)
        assertEquals(4L, s.state.version)
        assertEquals(7000L, s.state.positionMs)
        assertEquals(1_200L, s.receivedAtMs)
    }

    @Test
    fun `HOST_STATUS CONTROL y ERROR actualizan el estado y ACK limpia el error`() {
        val socket = open()

        socket.listener.onMessage("""{"type":"HOST_STATUS","online":true}""")
        socket.listener.onMessage("""{"type":"CONTROL","can_control":true}""")
        socket.listener.onMessage("""{"type":"ERROR","code":"RATE_LIMITED"}""")
        assertEquals(true, client.state.value.hostOnline)
        assertTrue(client.state.value.canControl)
        assertEquals("RATE_LIMITED", client.state.value.lastError)

        socket.listener.onMessage("""{"type":"ACK","ref_event_id":"e","version":1}""")
        assertNull(client.state.value.lastError)
    }

    @Test
    fun `un mensaje que no se entiende no rompe nada`() {
        val socket = open()

        socket.listener.onMessage("basura")

        assertTrue(client.state.value.connected)
        assertNull(client.state.value.snapshot)
    }

    @Test
    fun `cierre 4001 marca revocado y no reconecta`() {
        val socket = open()

        socket.listener.onClosed(SyncProtocol.CLOSE_REVOKED)

        assertTrue(client.state.value.revoked)
        assertFalse(client.state.value.connected)
        assertEquals(1, sockets.size)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `cierre 4003 marca expirado y no reconecta`() {
        val socket = open()

        socket.listener.onClosed(SyncProtocol.CLOSE_EXPIRED)

        assertTrue(client.state.value.expired)
        assertEquals(1, sockets.size)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `un corte reconecta con backoff y pide el token otra vez`() {
        val first = open()
        first.listener.onClosed(1006)
        sockets.last().listener.onFailure()
        sockets.last().listener.onFailure()

        assertEquals(listOf(500L, 1000L, 2000L), sleeps)
        assertEquals(4, sockets.size)
        assertEquals(3, client.state.value.reconnectAttempt)
    }

    @Test
    fun `el backoff se reinicia al recibir un STATE`() {
        open().listener.onClosed(1006)
        val second = sockets.last()
        second.listener.onOpen()
        second.listener.onMessage(stateFrame(1))
        assertEquals(0, client.state.value.reconnectAttempt)

        second.listener.onClosed(1006)

        assertEquals(listOf(500L, 500L), sleeps)
    }

    @Test
    fun `si ya no hay token deja de reintentar`() {
        open().listener.onClosed(1006)
        token = null
        sockets.last().listener.onFailure()

        assertEquals("NO_TOKEN", client.state.value.lastError)
        assertEquals(2, sockets.size)
    }

    @Test
    fun `stop cierra el socket y no reconecta`() {
        val socket = open()

        client.stop()
        socket.listener.onClosed(1000)

        assertEquals(1000, socket.closedWith)
        assertFalse(client.state.value.connected)
        assertEquals(1, sockets.size)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `los mensajes de un socket viejo se ignoran tras reconectar`() {
        val old = open()
        client.stop()
        client.start()
        val fresh = sockets.last()
        fresh.listener.onOpen()

        old.listener.onMessage(stateFrame(50))
        old.listener.onClosed(1006)

        assertNull(client.state.value.snapshot)
        assertTrue(client.state.value.connected)
        assertEquals(emptyList<Long>(), sleeps)
        assertNotNull(fresh)
    }

    @Test
    fun `start tras stop vuelve a conectar y conserva el ultimo estado`() {
        val socket = open()
        socket.listener.onMessage(stateFrame(4))
        client.stop()

        client.start()

        assertEquals(2, sockets.size)
        assertEquals(4L, client.state.value.snapshot!!.state.version)
    }
}
