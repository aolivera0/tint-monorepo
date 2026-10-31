package com.tint.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncedPlaybackTest {
    private class FakePort : PlayerPort {
        override var positionMs = 0L
        override var durationMs: Long? = 7_000_000L
        override var isPlaying = false
        override var isBuffering = false
        override var isEnded = false
        val seeks = mutableListOf<Long>()
        val speeds = mutableListOf<Float>()
        val volumes = mutableListOf<Float>()
        var plays = 0
        var pauses = 0

        override fun play() {
            plays++
            isPlaying = true
        }

        override fun pause() {
            pauses++
            isPlaying = false
        }

        override fun seekTo(ms: Long) {
            seeks += ms
            positionMs = ms
        }

        override fun setSpeed(speed: Float) {
            speeds += speed
        }

        override fun setVolume(volume: Float) {
            volumes += volume
        }
    }

    private val port = FakePort()
    private var clock = 10_000L
    private lateinit var playback: SyncedPlayback

    @Before
    fun setUp() {
        playback = SyncedPlayback(port) { clock }
    }

    /** Estado maestro: ancla en `position` a `updated_at = serverTime`, recibido ahora (clock). */
    private fun snapshot(
        status: PlaybackStatus = PlaybackStatus.PLAYING,
        position: Long = 60_000,
        rate: Double = 1.0,
        volume: Double = 1.0,
    ) = SyncSnapshot(
        SyncState(status, position, updatedAtMs = 50_000, rate = rate, version = 1, serverTimeMs = 50_000, mediaId = "m", volume = volume, quality = "auto"),
        receivedAtMs = clock,
    )

    @Test
    fun `reproduciendo y en hora no salta y arranca la reproduccion`() {
        port.positionMs = 60_000

        playback.apply(snapshot())

        assertEquals(emptyList<Long>(), port.seeks)
        assertEquals(1, port.plays)
        assertEquals(listOf(1.0f), port.speeds)
    }

    @Test
    fun `reproduciendo con mas de 250 ms de desfase salta a la posicion esperada`() {
        port.positionMs = 59_000

        playback.apply(snapshot())

        assertEquals(listOf(60_000L), port.seeks)
    }

    @Test
    fun `reproduciendo con menos de 250 ms de desfase no salta`() {
        port.positionMs = 60_200

        playback.apply(snapshot())

        assertEquals(emptyList<Long>(), port.seeks)
    }

    @Test
    fun `en pausa tolera solo 50 ms`() {
        port.isPlaying = true
        port.positionMs = 60_100

        playback.apply(snapshot(status = PlaybackStatus.PAUSED))

        assertEquals(listOf(60_000L), port.seeks)
        assertEquals(1, port.pauses)
        assertEquals(0, port.plays)
    }

    @Test
    fun `no repite play ni pausa si ya esta en ese estado`() {
        port.isPlaying = true
        port.positionMs = 60_000
        playback.apply(snapshot())
        assertEquals(0, port.plays)

        port.isPlaying = false
        playback.apply(snapshot(status = PlaybackStatus.PAUSED, position = 60_000).copy())
        assertEquals(0, port.pauses)
    }

    @Test
    fun `aplica la velocidad y el volumen maestros`() {
        port.positionMs = 60_000

        playback.apply(snapshot(rate = 1.5, volume = 0.25))

        assertEquals(listOf(1.5f), port.speeds)
        assertEquals(listOf(0.25f), port.volumes)
    }

    @Test
    fun `la posicion esperada incluye el tiempo desde que llego el estado`() {
        // El estado llego hace 2 s: 60 s + 2 s a velocidad 1.
        val snap = snapshot().copy(receivedAtMs = clock - 2_000)
        port.positionMs = 0

        playback.apply(snap)

        assertEquals(listOf(62_000L), port.seeks)
    }

    @Test
    fun `al volver del segundo plano salta a donde va la sala`() {
        val snap = snapshot()
        port.positionMs = 60_000
        playback.apply(snap)
        port.seeks.clear()
        // La app estuvo 30 s en segundo plano con el reproductor pausado en 60 s.
        clock += 30_000
        port.isPlaying = false

        playback.apply(snap)

        assertEquals(listOf(90_000L), port.seeks)
        assertTrue(port.isPlaying)
    }

    @Test
    fun `una correccion seek salta al objetivo`() {
        port.positionMs = 10_000

        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.SEEK, -50_000, 1, null), roomRate = 1.0)

        assertEquals(listOf(60_000L), port.seeks)
    }

    @Test
    fun `una correccion seek se omite si esta en buffering reproduciendo`() {
        port.isPlaying = true
        port.isBuffering = true
        port.positionMs = 10_000

        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.SEEK, -50_000, 1, null), 1.0)

        assertEquals(emptyList<Long>(), port.seeks)
    }

    @Test
    fun `una correccion seek se omite si ya esta a menos de 250 ms`() {
        port.positionMs = 59_900

        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.SEEK, -100, 1, null), 1.0)

        assertEquals(emptyList<Long>(), port.seeks)
    }

    @Test
    fun `una correccion seek al final del video terminado se omite`() {
        port.isEnded = true
        port.durationMs = 7_000_000L
        port.positionMs = 6_999_000L

        playback.onCorrection(SyncCorrection(1, 7_100_000, CorrectionStrategy.SEEK, -100_000, 1, null), 1.0)

        assertEquals(emptyList<Long>(), port.seeks)
    }

    @Test
    fun `una correccion por velocidad acelera o frena respecto a la velocidad de la sala`() {
        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.RATE, -600, 1, null), roomRate = 2.0)
        playback.onCorrection(SyncCorrection(2, 60_000, CorrectionStrategy.RATE, 600, 1, null), roomRate = 2.0)
        playback.onCorrection(SyncCorrection(3, 60_000, CorrectionStrategy.RATE, 0, 1, null), roomRate = 2.0)

        assertEquals(listOf(2.1f, 1.9f, 2.0f), port.speeds)
    }

    @Test
    fun `tick restaura la velocidad cuando el desfase baja de 100 ms`() {
        playback.apply(snapshot().also { port.positionMs = 60_000 })
        port.speeds.clear()
        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.RATE, -600, 1, null), 1.0)
        port.positionMs = 60_050 // casi en hora respecto a la posicion esperada (60_000)

        playback.tick()

        assertEquals(listOf(1.05f, 1.0f), port.speeds)
    }

    @Test
    fun `tick restaura la velocidad tras 5 segundos aunque siga el desfase`() {
        playback.apply(snapshot().also { port.positionMs = 60_000 })
        port.speeds.clear()
        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.RATE, -600, 1, null), 1.0)
        port.positionMs = 0
        playback.tick()
        assertEquals(listOf(1.05f), port.speeds)

        clock += 5_000
        playback.tick()

        assertEquals(listOf(1.05f, 1.0f), port.speeds)
    }

    @Test
    fun `tick sin correccion activa no hace nada`() {
        playback.tick()

        assertEquals(emptyList<Float>(), port.speeds)
    }

    @Test
    fun `un estado nuevo cancela una correccion por velocidad en curso`() {
        port.positionMs = 60_000
        playback.apply(snapshot())
        playback.onCorrection(SyncCorrection(1, 60_000, CorrectionStrategy.RATE, -600, 1, null), 1.0)
        port.speeds.clear()

        playback.apply(snapshot())
        clock += 6_000
        playback.tick()

        assertEquals(listOf(1.0f), port.speeds)
    }

    @Test
    fun `sin estado todavia no hay desfase`() {
        assertEquals(null, playback.driftMs())
    }

    @Test
    fun `el desfase es la posicion local menos la esperada`() {
        port.positionMs = 60_000
        playback.apply(snapshot())

        port.positionMs = 59_700
        assertEquals(-300L, playback.driftMs())

        // 2 s despues, la sala sigue avanzando y el reproductor se quedo quieto.
        clock += 2_000
        assertEquals(-2_300L, playback.driftMs())
    }

    @Test
    fun `en pausa el desfase no depende del tiempo`() {
        port.positionMs = 60_000
        playback.apply(snapshot(status = PlaybackStatus.PAUSED))

        clock += 5_000
        port.positionMs = 60_040

        assertEquals(40L, playback.driftMs())
    }
}
