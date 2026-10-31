package com.tint.app.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/**
 * Lee claims del payload de un JWT **sin verificar la firma**. Solo sirve para decidir qué mostrar en la
 * pantalla (por ejemplo, si mi `sub` está en la lista de silenciados): quien decide es siempre el servidor.
 */
object TokenClaims {
    /** El `sub` del token (p. ej. `guest:<uuid>`), o null si el token no tiene la forma esperada. */
    fun subject(token: String?): String? {
        val payload = token?.split('.')?.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            val sub = (Json.parseToJsonElement(json) as? JsonObject)?.get("sub") as? JsonPrimitive
            sub?.takeIf { it.isString }?.content
        } catch (e: Exception) {
            null
        }
    }
}
