package com.tint.app.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

interface SyncSocket {
    fun send(text: String): Boolean

    fun close(code: Int, reason: String)
}

interface SyncSocketListener {
    fun onOpen()

    fun onMessage(text: String)

    /** El servidor (o la red) cerró el socket con este código. */
    fun onClosed(code: Int)

    fun onFailure()
}

fun interface SyncSocketFactory {
    fun open(url: String, listener: SyncSocketListener): SyncSocket
}

/** Lo que la sala necesita de la sincronía (se sustituye en las pruebas). */
interface SyncSession {
    val state: StateFlow<SyncUiState>

    fun start()

    fun stop()
}

/** Corrección ordenada por sync-core; `seq` crece con cada una para poder reaccionar a cada llegada. */
data class SyncCorrection(
    val seq: Int,
    val targetMs: Long,
    val strategy: CorrectionStrategy,
    val driftMs: Long,
    val version: Long,
    /** Velocidad de la sala en esa versión, si el servidor la envía. */
    val rate: Double?,
)

data class SyncUiState(
    val connected: Boolean = false,
    val snapshot: SyncSnapshot? = null,
    val hostOnline: Boolean? = null,
    val canControl: Boolean = false,
    val correction: SyncCorrection? = null,
    /** Cierre 4001: el host retiró al invitado; no se reconecta. */
    val revoked: Boolean = false,
    /** Cierre 4003: el token caducó; hay que volver a unirse. */
    val expired: Boolean = false,
    val volume: Double = 1.0,
    val quality: String = "auto",
    val reconnectAttempt: Int = 0,
    val rttMs: Long? = null,
    val driftMs: Long? = null,
    /** Último error (`ERROR` del servidor o `NO_TOKEN`). */
    val lastError: String? = null,
)

/**
 * Cliente del Canal Sync como participante. Solo refleja el estado maestro (espejo de
 * `apps/web/src/hooks/useSyncSocket.ts`): descarta broadcasts por versión, responde cada
 * `HEARTBEAT` con un `SYNC_CHECK` y expone las correcciones que decide el servidor.
 * Reconecta con backoff exponencial salvo ante `4001`/`4003`.
 *
 * `localPositionMs` se llama desde el hilo del socket: debe poder leerse desde cualquier hilo.
 */
class SyncClient(
    private val roomId: String,
    token: () -> String?,
    urlFor: (roomId: String, token: String) -> String,
    sockets: SyncSocketFactory,
    scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val localPositionMs: () -> Long?,
    sleep: suspend (Long) -> Unit = { delay(it) },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val wallClock: () -> String = { Instant.now().toString() },
) : SyncSession {
    private val lock = Any()
    private val _state = MutableStateFlow(SyncUiState())
    override val state: StateFlow<SyncUiState> = _state

    private var awaitingFirstState = true
    private var correctionSeq = 0

    private val connection = ResilientSocket(
        token = token,
        urlFor = { urlFor(roomId, it) },
        sockets = sockets,
        scope = scope,
        sleep = sleep,
        listener = object : ResilientSocket.Listener {
            override fun onConnecting() {
                synchronized(lock) { awaitingFirstState = true }
            }

            override fun onNoToken() {
                _state.update { it.copy(connected = false, lastError = "NO_TOKEN") }
            }

            override fun onOpen() {
                _state.update { it.copy(connected = true) }
            }

            override fun onMessage(text: String) = handleMessage(text)

            override fun onClosed(code: Int, reconnectAttempt: Int?) {
                _state.update {
                    it.copy(
                        connected = false,
                        revoked = it.revoked || code == SyncProtocol.CLOSE_REVOKED,
                        expired = it.expired || code == SyncProtocol.CLOSE_EXPIRED,
                        reconnectAttempt = reconnectAttempt ?: it.reconnectAttempt,
                    )
                }
            }
        },
    )

    override fun start() = connection.start()

    override fun stop() {
        connection.stop()
        _state.update { it.copy(connected = false) }
    }

    private fun handleMessage(text: String) {
        val msg = SyncProtocol.parse(text) ?: return
        when (msg) {
            is ServerMessage.State -> onState(msg)
            is ServerMessage.Broadcast -> {
                val cur = _state.value.snapshot
                if (cur != null && msg.state.version <= cur.state.version) return
                accept(msg.state)
            }
            is ServerMessage.Control -> _state.update { it.copy(canControl = msg.canControl) }
            is ServerMessage.Heartbeat -> onHeartbeat(msg)
            is ServerMessage.Correction -> onCorrection(msg)
            is ServerMessage.HostStatus -> _state.update { it.copy(hostOnline = msg.online) }
            is ServerMessage.Ack -> _state.update { it.copy(lastError = null) }
            is ServerMessage.Error -> _state.update { it.copy(lastError = msg.code) }
        }
    }

    private fun onState(msg: ServerMessage.State) {
        val cur = _state.value.snapshot
        val first = synchronized(lock) { awaitingFirstState }
        // El primer STATE de cada conexión siempre aplica (la sala pudo reiniciarse con una versión menor);
        // los siguientes con versión menor llegaron tarde.
        if (!first && cur != null && msg.state.version < cur.state.version) return
        synchronized(lock) { awaitingFirstState = false }
        connection.resetBackoff()
        accept(msg.state)
        _state.update {
            it.copy(
                reconnectAttempt = 0,
                hostOnline = msg.online ?: it.hostOnline,
                canControl = msg.canControl ?: it.canControl,
            )
        }
    }

    private fun accept(state: SyncState) {
        val snapshot = SyncSnapshot(state, nowMs())
        _state.update { it.copy(snapshot = snapshot, volume = state.volume, quality = state.quality) }
    }

    private fun onHeartbeat(msg: ServerMessage.Heartbeat) {
        if (msg.rttMs != null) {
            // v2 omite drift_ms cuando vale 0.
            _state.update { it.copy(rttMs = msg.rttMs, driftMs = msg.driftMs ?: 0) }
        }
        val snapshot = _state.value.snapshot
        val position = localPositionMs()
            ?: snapshot?.let { SyncProtocol.expectedPosition(it.state, SyncProtocol.estimateServerNow(it, nowMs())) }
            ?: 0
        val check = SyncProtocol.syncCheck(roomId, position, msg.serverTimeMs, wallClock(), snapshot?.state?.version, newId())
        connection.send(check)
    }

    private fun onCorrection(msg: ServerMessage.Correction) {
        val prev = _state.value.snapshot
        if (prev != null && msg.version < prev.state.version) return
        val seq = synchronized(lock) { ++correctionSeq }
        _state.update {
            it.copy(
                driftMs = msg.driftMs,
                volume = msg.volume ?: it.volume,
                quality = msg.quality ?: it.quality,
                correction = SyncCorrection(seq, msg.targetMs, msg.strategy, msg.driftMs, msg.version, msg.rate),
            )
        }
        reconcile(msg)
    }

    /**
     * Una corrección con `status` y `rate` describe el estado de la sala en su `version`: si difiere del
     * aplicado (se perdió un PAUSE o un RATE), se adopta, con el ancla en `target_ms` "ahora".
     */
    private fun reconcile(msg: ServerMessage.Correction) {
        val prev = _state.value.snapshot ?: return
        val status = msg.status ?: return
        val rate = msg.rate ?: return
        val mediaId = msg.mediaId ?: prev.state.mediaId
        val differs = msg.version > prev.state.version || status != prev.state.status ||
            rate != prev.state.rate || mediaId != prev.state.mediaId
        if (!differs) return
        val now = nowMs()
        val serverNow = SyncProtocol.estimateServerNow(prev, now)
        val state = prev.state.copy(
            status = status,
            rate = rate,
            mediaId = mediaId,
            version = msg.version,
            positionMs = msg.targetMs,
            updatedAtMs = serverNow,
            serverTimeMs = serverNow,
            volume = msg.volume ?: prev.state.volume,
            quality = msg.quality ?: prev.state.quality,
        )
        _state.update { it.copy(snapshot = SyncSnapshot(state, now)) }
    }
}
