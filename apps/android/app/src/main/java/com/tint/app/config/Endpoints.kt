package com.tint.app.config

import java.net.URLEncoder

/**
 * URLs del backend. Son las mismas que usa la web (`apps/web/.env.example`): identity-api
 * por REST y los dos canales WebSocket de sync-core y social-core. Las bases se normalizan
 * sin barra final.
 */
class Endpoints(identityApiUrl: String, syncWsUrl: String, socialWsUrl: String) {
    val identityApiUrl: String = identityApiUrl.trimEnd('/')
    private val syncBase: String = syncWsUrl.trimEnd('/')
    private val socialBase: String = socialWsUrl.trimEnd('/')

    /** `GET /ws/sync?room_id=&token=` (el token va en la query, igual que en la web). */
    fun syncUrl(roomId: String, token: String): String = channelUrl(syncBase, roomId, token)

    /** `GET /ws/social?room_id=&token=`. */
    fun socialUrl(roomId: String, token: String): String = channelUrl(socialBase, roomId, token)

    private fun channelUrl(base: String, roomId: String, token: String): String =
        "$base?room_id=${encode(roomId)}&token=${encode(token)}"

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
