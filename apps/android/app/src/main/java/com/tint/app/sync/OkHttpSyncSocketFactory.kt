package com.tint.app.sync

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** [SyncSocketFactory] sobre el WebSocket de OkHttp; capa fina, se prueba en el dispositivo. */
class OkHttpSyncSocketFactory(http: OkHttpClient) : SyncSocketFactory {
    // Sin tiempo de lectura (el servidor manda un HEARTBEAT por segundo) y con ping para detectar cortes.
    private val client = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    override fun open(url: String, listener: SyncSocketListener): SyncSocket {
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

                override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)

                // El servidor cierra con un código (4001, 4003…): se contesta y `onClosed` avisa una sola vez.
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(code)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure()
            },
        )
        return object : SyncSocket {
            override fun send(text: String): Boolean = ws.send(text)

            override fun close(code: Int, reason: String) {
                ws.close(code, reason)
            }
        }
    }
}
