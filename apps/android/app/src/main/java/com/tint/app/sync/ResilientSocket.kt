package com.tint.app.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Conexión WebSocket que se reconecta sola (compartida por el Canal Sync y el Canal Social). Pide el
 * token en cada intento, reconecta con backoff exponencial y no reconecta ante `4001` (revocado) ni
 * `4003` (caducado). Ignora los mensajes y cierres de sockets viejos, y es segura entre hilos: los
 * callbacks de OkHttp llegan por su propio hilo.
 */
class ResilientSocket(
    private val token: () -> String?,
    private val urlFor: (token: String) -> String,
    private val sockets: SyncSocketFactory,
    private val scope: CoroutineScope,
    private val sleep: suspend (Long) -> Unit,
    private val listener: Listener,
) {
    interface Listener {
        /** Antes de abrir cada socket (también en las reconexiones). */
        fun onConnecting() {}

        /** No hay token: no se abre nada ni se reintenta. */
        fun onNoToken() {}

        fun onOpen() {}

        fun onMessage(text: String) {}

        /** El socket se cerró; `reconnectAttempt` es el número de reintento programado, o null si es terminal. */
        fun onClosed(code: Int, reconnectAttempt: Int?) {}
    }

    private val lock = Any()
    private var running = false
    private var generation = 0
    private var reconnectEpoch = 0
    private var socket: SyncSocket? = null
    private var attempt = 0

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            attempt = 0
        }
        connect()
    }

    fun stop() {
        val toClose = synchronized(lock) {
            running = false
            generation++
            reconnectEpoch++
            socket.also { socket = null }
        }
        toClose?.close(1000, "stop")
    }

    /** Envía un frame si hay un socket abierto. */
    fun send(text: String): Boolean {
        val target = synchronized(lock) { socket } ?: return false
        return target.send(text)
    }

    /** El servidor respondió con estado válido: el siguiente corte vuelve a empezar desde 500 ms. */
    fun resetBackoff() {
        synchronized(lock) { attempt = 0 }
    }

    private fun connect() {
        val gen = synchronized(lock) {
            if (!running) return
            ++generation
        }
        listener.onConnecting()
        val value = token()
        if (value == null) {
            listener.onNoToken()
            return
        }
        val opened = sockets.open(urlFor(value), listenerFor(gen))
        synchronized(lock) {
            if (gen == generation && running) socket = opened else opened.close(1000, "stale")
        }
    }

    private fun listenerFor(gen: Int) = object : SyncSocketListener {
        override fun onOpen() {
            if (current(gen)) listener.onOpen()
        }

        override fun onMessage(text: String) {
            if (current(gen)) listener.onMessage(text)
        }

        override fun onClosed(code: Int) = handleClosed(gen, code)

        override fun onFailure() = handleClosed(gen, 1006)
    }

    private fun current(gen: Int): Boolean = synchronized(lock) { gen == generation }

    private fun handleClosed(gen: Int, code: Int) {
        val reconnectIn: Long?
        val epoch: Int
        val attemptNow: Int
        synchronized(lock) {
            if (gen != generation) return
            socket = null
            epoch = reconnectEpoch
            val terminal = code == SyncProtocol.CLOSE_REVOKED || code == SyncProtocol.CLOSE_EXPIRED
            reconnectIn = if (terminal || !running) null else SyncProtocol.backoffDelay(attempt).also { attempt++ }
            attemptNow = attempt
        }
        listener.onClosed(code, if (reconnectIn != null) attemptNow else null)
        if (reconnectIn != null) {
            scope.launch {
                sleep(reconnectIn)
                val stillValid = synchronized(lock) { running && epoch == reconnectEpoch }
                if (stillValid) connect()
            }
        }
    }
}
