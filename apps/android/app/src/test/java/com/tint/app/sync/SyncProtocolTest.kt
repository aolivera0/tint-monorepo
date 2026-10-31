package com.tint.app.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {
    private fun state(extra: String = "", type: String = "STATE") =
        """{"type":"$type","status":"playing","position_ms":5000,"updated_at_ms":1000,"rate":1.5,
        "version":7,"server_time_ms":2000$extra}"""

    @Test
    fun `parsea un STATE completo`() {
        val msg = SyncProtocol.parse(
            state(""","media_id":"m1","volume":0.4,"quality":"720p","online":true,"can_control":false"""),
        )

        assertEquals(
            ServerMessage.State(
                state = SyncState(PlaybackStatus.PLAYING, 5000, 1000, 1.5, 7, 2000, "m1", 0.4, "720p"),
                online = true,
                canControl = false,
            ),
            msg,
        )
    }

    @Test
    fun `volumen y calidad ausentes valen 1 y auto`() {
        val msg = SyncProtocol.parse(state()) as ServerMessage.State

        assertEquals(1.0, msg.state.volume, 0.0)
        assertEquals("auto", msg.state.quality)
        assertEquals("", msg.state.mediaId)
        assertNull(msg.online)
        assertNull(msg.canControl)
    }

    @Test
    fun `acota el volumen a 0 y 1 y descarta calidades desconocidas`() {
        val loud = SyncProtocol.parse(state(""","volume":5,"quality":"8k"""")) as ServerMessage.State
        val quiet = SyncProtocol.parse(state(""","volume":-2""")) as ServerMessage.State

        assertEquals(1.0, loud.state.volume, 0.0)
        assertEquals("auto", loud.state.quality)
        assertEquals(0.0, quiet.state.volume, 0.0)
    }

    @Test
    fun `parsea los broadcasts de comandos`() {
        for (type in listOf("PLAY", "PAUSE", "SEEK", "RATE", "LOAD", "VOLUME", "QUALITY")) {
            val msg = SyncProtocol.parse(state(""","event_id":"e1"""", type)) as ServerMessage.Broadcast
            assertEquals(type, msg.type)
            assertEquals("e1", msg.eventId)
            assertEquals(7, msg.state.version)
        }
    }

    @Test
    fun `parsea HEARTBEAT con y sin rtt y drift`() {
        assertEquals(
            ServerMessage.Heartbeat(9000, 40, -120),
            SyncProtocol.parse("""{"type":"HEARTBEAT","server_time_ms":9000,"rtt_ms":40,"drift_ms":-120}"""),
        )
        assertEquals(
            ServerMessage.Heartbeat(9000, null, null),
            SyncProtocol.parse("""{"type":"HEARTBEAT","server_time_ms":9000}"""),
        )
    }

    @Test
    fun `parsea SYNC_CORRECTION con campos opcionales`() {
        val full = SyncProtocol.parse(
            """{"type":"SYNC_CORRECTION","target_ms":12000,"strategy":"seek","drift_ms":-900,"version":3,
            "status":"paused","rate":2,"media_id":"m2","volume":0.5,"quality":"360p"}""",
        )
        assertEquals(
            ServerMessage.Correction(12000, CorrectionStrategy.SEEK, -900, 3, PlaybackStatus.PAUSED, 2.0, "m2", 0.5, "360p"),
            full,
        )
        val minimal = SyncProtocol.parse(
            """{"type":"SYNC_CORRECTION","target_ms":1,"strategy":"rate","drift_ms":600,"version":1}""",
        )
        assertEquals(
            ServerMessage.Correction(1, CorrectionStrategy.RATE, 600, 1, null, null, null, null, null),
            minimal,
        )
    }

    @Test
    fun `parsea los mensajes simples`() {
        assertEquals(ServerMessage.HostStatus(false), SyncProtocol.parse("""{"type":"HOST_STATUS","online":false}"""))
        assertEquals(ServerMessage.Control(true), SyncProtocol.parse("""{"type":"CONTROL","can_control":true}"""))
        assertEquals(ServerMessage.Error("FORBIDDEN"), SyncProtocol.parse("""{"type":"ERROR","code":"FORBIDDEN"}"""))
        assertEquals(ServerMessage.Ack, SyncProtocol.parse("""{"type":"ACK","ref_event_id":"e","version":2}"""))
    }

    @Test
    fun `ignora lo desconocido o malformado`() {
        assertNull(SyncProtocol.parse("no es json"))
        assertNull(SyncProtocol.parse("[1,2]"))
        assertNull(SyncProtocol.parse("""{"type":"INVENTADO"}"""))
        assertNull(SyncProtocol.parse("""{"sin":"tipo"}"""))
        assertNull(SyncProtocol.parse("""{"type":"STATE","position_ms":1}"""))
        assertNull(SyncProtocol.parse("""{"type":"STATE","status":"raro","position_ms":1,"updated_at_ms":1,"rate":1,"version":1,"server_time_ms":1}"""))
        assertNull(SyncProtocol.parse("""{"type":"HEARTBEAT"}"""))
        assertNull(SyncProtocol.parse("""{"type":"SYNC_CORRECTION","target_ms":1,"strategy":"otra","drift_ms":1,"version":1}"""))
        assertNull(SyncProtocol.parse("""{"type":"HOST_STATUS"}"""))
    }

    @Test
    fun `SYNC_CHECK lleva el eco, la posicion y la version`() {
        val json = Json.parseToJsonElement(
            SyncProtocol.syncCheck("r1", 4200, 9000, "2026-10-31T12:00:00Z", 7, "ev-1"),
        ).jsonObject

        assertEquals("SYNC_CHECK", json["type"]!!.jsonPrimitive.content)
        assertEquals("ev-1", json["event_id"]!!.jsonPrimitive.content)
        assertEquals("r1", json["room_id"]!!.jsonPrimitive.content)
        assertEquals("4200", json["position_ms"]!!.jsonPrimitive.content)
        assertEquals("9000", json["echo_server_ts"]!!.jsonPrimitive.content)
        assertEquals("2026-10-31T12:00:00Z", json["occurred_at"]!!.jsonPrimitive.content)
        assertEquals("7", json["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SYNC_CHECK omite la version antes del primer STATE y no manda posiciones negativas`() {
        val json = Json.parseToJsonElement(
            SyncProtocol.syncCheck("r1", -50, 1, "2026-10-31T12:00:00Z", null, "ev"),
        ).jsonObject

        assertFalse(json.containsKey("version"))
        assertEquals("0", json["position_ms"]!!.jsonPrimitive.content)
    }

    @Test
    fun `backoff exponencial de 500 ms con tope de 10 s`() {
        assertEquals(listOf(500L, 1000L, 2000L, 4000L, 8000L, 10_000L, 10_000L), (0..6).map { SyncProtocol.backoffDelay(it) })
        assertEquals(10_000L, SyncProtocol.backoffDelay(500))
    }

    @Test
    fun `la posicion esperada avanza solo si reproduce y respeta la velocidad`() {
        val playing = SyncState(PlaybackStatus.PLAYING, 5000, 1000, 2.0, 1, 1000, "m", 1.0, "auto")
        val paused = playing.copy(status = PlaybackStatus.PAUSED)

        assertEquals(5000 + 500 * 2, SyncProtocol.expectedPosition(playing, 1500))
        assertEquals(5000, SyncProtocol.expectedPosition(playing, 900)) // reloj anterior al ancla
        assertEquals(5000, SyncProtocol.expectedPosition(paused, 9999))
    }

    @Test
    fun `el reloj del servidor se estima con el reloj local monotono`() {
        val snap = SyncSnapshot(SyncState(PlaybackStatus.PAUSED, 0, 0, 1.0, 1, 10_000, "m", 1.0, "auto"), receivedAtMs = 400)

        assertEquals(10_250, SyncProtocol.estimateServerNow(snap, 650))
    }

    @Test
    fun `los codigos de cierre terminales son los del servidor`() {
        assertTrue(SyncProtocol.CLOSE_REVOKED == 4001 && SyncProtocol.CLOSE_EXPIRED == 4003)
    }
}
