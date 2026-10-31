package com.tint.app.sync

import kotlin.math.abs

/** Lo que la sincronía necesita de un reproductor (ExoPlayer en la app, un falso en las pruebas). */
interface PlayerPort {
    val positionMs: Long
    val durationMs: Long?

    /** Si debería estar reproduciendo (no pausado), aunque esté cargando datos. */
    val isPlaying: Boolean
    val isBuffering: Boolean
    val isEnded: Boolean

    fun play()

    fun pause()

    fun seekTo(ms: Long)

    fun setSpeed(speed: Float)

    fun setVolume(volume: Float)
}

/**
 * Gobierna un reproductor con el estado maestro (espejo de `SyncedPlayer.tsx` y `lib/drift.ts`): aplica
 * velocidad, volumen y play/pausa, y solo salta si el desfase supera 250 ms reproduciendo (50 ms en pausa).
 * Las correcciones del servidor se aplican con [onCorrection]; las de velocidad se deshacen en [tick].
 */
class SyncedPlayback(private val port: PlayerPort, private val nowMs: () -> Long) {
    private class RateCorrection(val startedAtMs: Long, val roomRate: Double)

    private var last: SyncSnapshot? = null
    private var rateCorrection: RateCorrection? = null

    /** Aplica un estado maestro nuevo (STATE o broadcast) o re-aplica el último al volver de segundo plano. */
    fun apply(snapshot: SyncSnapshot) {
        last = snapshot
        rateCorrection = null
        val state = snapshot.state
        port.setSpeed(state.rate.toFloat())
        port.setVolume(state.volume.toFloat())

        val playing = state.status == PlaybackStatus.PLAYING
        val expected = expectedNow(snapshot)
        val tolerance = if (playing) SEEK_TOLERANCE_PLAYING_MS else SEEK_TOLERANCE_PAUSED_MS
        if (abs(port.positionMs - expected) > tolerance) port.seekTo(expected)

        if (playing && !port.isPlaying) port.play() else if (!playing && port.isPlaying) port.pause()
    }

    /** `roomRate` es la velocidad de la sala; la de corrección es relativa a ella (a 2× se acelera a 2.1). */
    fun onCorrection(correction: SyncCorrection, roomRate: Double) {
        when (correction.strategy) {
            CorrectionStrategy.SEEK -> if (!shouldSkipSeek(correction.targetMs)) port.seekTo(correction.targetMs)
            CorrectionStrategy.RATE -> {
                val base = if (roomRate.isFinite() && roomRate > 0) roomRate else 1.0
                val factor = when {
                    correction.driftMs < 0 -> RATE_BEHIND
                    correction.driftMs > 0 -> RATE_AHEAD
                    else -> 1.0
                }
                port.setSpeed((base * factor).toFloat())
                rateCorrection = RateCorrection(nowMs(), base)
            }
        }
    }

    /** Devuelve la velocidad de la sala cuando el desfase ya es < 100 ms o pasaron 5 s de corrección. */
    fun tick() {
        val correction = rateCorrection ?: return
        val elapsed = nowMs() - correction.startedAtMs
        val expected = last?.let { expectedNow(it) }
        val done = elapsed >= RATE_MAX_DURATION_MS ||
            (expected != null && abs(port.positionMs - expected) < RATE_RESTORE_TOLERANCE_MS)
        if (done) {
            port.setSpeed(correction.roomRate.toFloat())
            rateCorrection = null
        }
    }

    /** Desfase del reproductor frente a la sala (negativo = atrasado), o null si aún no hay estado. */
    fun driftMs(): Long? = last?.let { port.positionMs - expectedNow(it) }

    private fun expectedNow(snapshot: SyncSnapshot): Long {
        val serverNow = SyncProtocol.estimateServerNow(snapshot, nowMs())
        return SyncProtocol.expectedPosition(snapshot.state, serverNow).coerceAtLeast(0)
    }

    /**
     * Un seek de corrección no aporta si el vídeo está en buffering reproduciendo (otro seek solo vaciaría
     * el búfer), si terminó y el objetivo está en o tras el final, o si ya está a menos de 250 ms.
     */
    private fun shouldSkipSeek(targetMs: Long): Boolean {
        if (port.isPlaying && port.isBuffering) return true
        val duration = port.durationMs
        if (port.isEnded && duration != null && targetMs >= duration) return true
        return abs(port.positionMs - targetMs) < SEEK_SKIP_NEAR_MS
    }

    private companion object {
        const val SEEK_TOLERANCE_PLAYING_MS = 250L
        const val SEEK_TOLERANCE_PAUSED_MS = 50L
        const val SEEK_SKIP_NEAR_MS = 250L
        const val RATE_RESTORE_TOLERANCE_MS = 100L
        const val RATE_MAX_DURATION_MS = 5_000L
        const val RATE_BEHIND = 1.05
        const val RATE_AHEAD = 0.95
    }
}

/**
 * Posición del reproductor legible desde cualquier hilo. ExoPlayer solo se puede consultar en el hilo
 * principal, pero el `SYNC_CHECK` se responde en el hilo del socket: el hilo principal publica aquí la
 * posición y quien la lee la extrapola con el tiempo transcurrido si el vídeo avanza.
 */
class PositionTracker(private val nowMs: () -> Long) {
    @Volatile
    private var sample: Sample? = null

    private class Sample(val positionMs: Long, val atMs: Long, val speed: Double)

    /** `speed` es 0 si el vídeo no avanza (pausado, en buffering o sin cargar). */
    fun update(positionMs: Long, speed: Double) {
        sample = Sample(positionMs, nowMs(), speed)
    }

    /** Posición estimada ahora, o null si el reproductor aún no publicó nada. */
    fun current(): Long? {
        val s = sample ?: return null
        return s.positionMs + ((nowMs() - s.atMs) * s.speed).toLong()
    }

    fun clear() {
        sample = null
    }
}
