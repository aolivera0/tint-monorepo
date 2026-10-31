package com.tint.app.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

enum class PlaybackStatus { PLAYING, PAUSED }

enum class CorrectionStrategy { RATE, SEEK }

/** Estado maestro de reproducción tal como lo emite sync-core (el servidor es la fuente de verdad). */
data class SyncState(
    val status: PlaybackStatus,
    val positionMs: Long,
    /** Unix ms (reloj del servidor) del ancla `positionMs`. */
    val updatedAtMs: Long,
    val rate: Double,
    val version: Long,
    /** Unix ms (reloj del servidor) en que se envió el mensaje. */
    val serverTimeMs: Long,
    /** Vídeo actual de la sala (`mediaId` de identity-api); vacío si no hay. */
    val mediaId: String,
    /** Volumen maestro 0..1. */
    val volume: Double,
    /** Calidad maestra: `auto`, `source`, `720p` o `360p`. */
    val quality: String,
)

/** Estado maestro + instante local (reloj monótono) en que llegó. */
data class SyncSnapshot(val state: SyncState, val receivedAtMs: Long)

sealed interface ServerMessage {
    data class State(val state: SyncState, val online: Boolean?, val canControl: Boolean?) : ServerMessage

    /** `PLAY`/`PAUSE`/`SEEK`/`RATE`/`LOAD`/`VOLUME`/`QUALITY` difundidos a toda la sala. */
    data class Broadcast(val type: String, val state: SyncState, val eventId: String?) : ServerMessage

    data class Heartbeat(val serverTimeMs: Long, val rttMs: Long?, val driftMs: Long?) : ServerMessage

    data class Correction(
        val targetMs: Long,
        val strategy: CorrectionStrategy,
        /** Negativo = cliente atrasado; positivo = adelantado. */
        val driftMs: Long,
        val version: Long,
        val status: PlaybackStatus?,
        val rate: Double?,
        val mediaId: String?,
        val volume: Double?,
        val quality: String?,
    ) : ServerMessage

    data class HostStatus(val online: Boolean) : ServerMessage

    data class Control(val canControl: Boolean) : ServerMessage

    data object Ack : ServerMessage

    data class Error(val code: String) : ServerMessage
}

/**
 * Protocolo del Canal Sync v2 (`/ws/sync`), versión JSON. Espejo de `apps/web/src/lib/syncProtocol.ts`;
 * el contrato está en `services/sync-core/AGENTS.md`.
 */
object SyncProtocol {
    /** Cierre del servidor: token revocado (no reconectar). */
    const val CLOSE_REVOKED = 4001

    /** Cierre del servidor: token caducado (hay que volver a unirse). */
    const val CLOSE_EXPIRED = 4003

    private const val BACKOFF_BASE_MS = 500L
    private const val BACKOFF_MAX_MS = 10_000L
    private val COMMANDS = setOf("PLAY", "PAUSE", "SEEK", "RATE", "LOAD", "VOLUME", "QUALITY")
    private val QUALITIES = setOf("auto", "source", "720p", "360p")

    /** Parsea un frame del servidor; null si no es un mensaje conocido o le faltan campos. */
    fun parse(raw: String): ServerMessage? {
        val obj = try {
            Json.parseToJsonElement(raw) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        return when (val type = obj.string("type")) {
            "STATE" -> state(obj)?.let { ServerMessage.State(it, obj.bool("online"), obj.bool("can_control")) }
            in COMMANDS -> state(obj)?.let { ServerMessage.Broadcast(type!!, it, obj.string("event_id")) }
            "HEARTBEAT" -> obj.long("server_time_ms")?.let {
                ServerMessage.Heartbeat(it, obj.long("rtt_ms"), obj.long("drift_ms"))
            }
            "SYNC_CORRECTION" -> correction(obj)
            "HOST_STATUS" -> obj.bool("online")?.let { ServerMessage.HostStatus(it) }
            "CONTROL" -> obj.bool("can_control")?.let { ServerMessage.Control(it) }
            "ACK" -> ServerMessage.Ack
            "ERROR" -> obj.string("code")?.let { ServerMessage.Error(it) }
            else -> null
        }
    }

    private fun state(o: JsonObject): SyncState? {
        return SyncState(
            status = status(o.string("status")) ?: return null,
            positionMs = o.long("position_ms") ?: return null,
            updatedAtMs = o.long("updated_at_ms") ?: return null,
            rate = o.double("rate") ?: return null,
            version = o.long("version") ?: return null,
            serverTimeMs = o.long("server_time_ms") ?: return null,
            mediaId = o.string("media_id").orEmpty(),
            volume = normalizeVolume(o.double("volume")),
            quality = normalizeQuality(o.string("quality")),
        )
    }

    private fun correction(o: JsonObject): ServerMessage.Correction? {
        return ServerMessage.Correction(
            targetMs = o.long("target_ms") ?: return null,
            strategy = when (o.string("strategy")) {
                "rate" -> CorrectionStrategy.RATE
                "seek" -> CorrectionStrategy.SEEK
                else -> return null
            },
            driftMs = o.long("drift_ms") ?: return null,
            version = o.long("version") ?: return null,
            status = status(o.string("status")),
            rate = o.double("rate"),
            mediaId = o.string("media_id"),
            volume = o.double("volume")?.let { normalizeVolume(it) },
            quality = o.string("quality")?.let { normalizeQuality(it) },
        )
    }

    private fun status(value: String?): PlaybackStatus? = when (value) {
        "playing" -> PlaybackStatus.PLAYING
        "paused" -> PlaybackStatus.PAUSED
        else -> null
    }

    /** Volumen acotado a [0, 1]; 1 si falta o no es un número finito. */
    private fun normalizeVolume(value: Double?): Double =
        if (value == null || !value.isFinite()) 1.0 else value.coerceIn(0.0, 1.0)

    private fun normalizeQuality(value: String?): String = if (value in QUALITIES) value!! else "auto"

    /** `SYNC_CHECK`: la respuesta a cada `HEARTBEAT`, con el eco del reloj del servidor. */
    fun syncCheck(
        roomId: String,
        positionMs: Long,
        echoServerTs: Long,
        occurredAt: String,
        version: Long?,
        eventId: String,
    ): String = buildJsonObject {
        put("event_id", eventId)
        put("type", "SYNC_CHECK")
        put("room_id", roomId)
        put("position_ms", positionMs.coerceAtLeast(0))
        put("occurred_at", occurredAt)
        put("echo_server_ts", echoServerTs)
        if (version != null) put("version", version)
    }.toString()

    /** Backoff exponencial de reconexión: 500 ms, 1 s, 2 s… hasta 10 s. */
    fun backoffDelay(attempt: Int): Long =
        if (attempt >= 5) BACKOFF_MAX_MS else minOf(BACKOFF_MAX_MS, BACKOFF_BASE_MS shl attempt.coerceAtLeast(0))

    /** Estima el reloj del servidor ahora a partir del snapshot y el reloj monótono local. */
    fun estimateServerNow(snapshot: SyncSnapshot, nowMs: Long): Long =
        snapshot.state.serverTimeMs + (nowMs - snapshot.receivedAtMs)

    /** Posición maestra esperada (ms) para el instante de servidor `serverNowMs`. */
    fun expectedPosition(state: SyncState, serverNowMs: Long): Long {
        if (state.status != PlaybackStatus.PLAYING) return state.positionMs
        val elapsed = (serverNowMs - state.updatedAtMs).coerceAtLeast(0)
        return state.positionMs + (elapsed * state.rate).toLong()
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.double(key: String): Double? = primitive(key)?.takeIf { !it.isString }?.doubleOrNull

    private fun JsonObject.long(key: String): Long? = double(key)?.takeIf { it.isFinite() }?.toLong()

    private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.takeIf { !it.isString }?.booleanOrNull
}
