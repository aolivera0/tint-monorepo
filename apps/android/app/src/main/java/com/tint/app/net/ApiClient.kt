package com.tint.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Qué credencial enviar (igual que `ApiAuth` en `apps/web/src/lib/api.ts`): `AUTO` (host si
 * existe, si no invitado), `HOST` (solo la sesión de host) o `GUEST` (solo el token de
 * invitado, aunque haya sesión de host) o `NONE` (sin credencial: rutas anónimas como el canje
 * de una invitación).
 */
enum class ApiAuth { AUTO, HOST, GUEST, NONE }

/** Los campos que no conoce el cliente se ignoran: añadir un campo al API no lo rompe. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Cliente REST de identity-api. Usa las mismas rutas `/api/v1` que la web, así que la
 * lógica del backend no se duplica. `onHostSessionExpired` se llama cuando el servidor
 * rechaza (401) la sesión de host; el token se descarta (falla cerrado).
 */
class ApiClient(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val tokens: TokenStore,
    private val onHostSessionExpired: () -> Unit = {},
    private val hostTokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null,
) {
    val json: Json get() = ApiJson

    suspend inline fun <reified T> get(path: String, auth: ApiAuth = ApiAuth.AUTO): T =
        json.decodeFromString(request("GET", path, null, auth))

    suspend inline fun <reified B, reified T> post(path: String, body: B, auth: ApiAuth = ApiAuth.AUTO): T =
        json.decodeFromString(request("POST", path, json.encodeToString(body), auth))

    /** Devuelve el cuerpo de una respuesta 2xx o lanza [ApiError]. */
    suspend fun request(method: String, path: String, body: String? = null, auth: ApiAuth = ApiAuth.AUTO): String {
        val bearer = bearerFor(auth)
        var res = execute(method, path, body, bearer.token)
        if (res.code == 401 && bearer.host) {
            // El ID token de Firebase dura ~1 h: se fuerza una renovación y se reintenta una vez.
            val renewed = freshHostToken(forceRefresh = true)
            if (renewed != null) res = execute(method, path, body, renewed)
            if (res.code == 401) {
                // Falla cerrado: la sesión de host ya no sirve.
                tokens.hostToken = null
                onHostSessionExpired()
            }
        }
        if (res.code !in 200..299) throw ApiError(res.code, errorMessage(res.code, res.text))
        return res.text
    }

    private class Reply(val code: Int, val text: String)

    private suspend fun execute(method: String, path: String, body: String?, bearer: String?): Reply {
        val builder = Request.Builder().url(baseUrl + path)
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        builder.method(method, requestBody(method, body))
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { Reply(it.code, it.body?.string().orEmpty()) }
            } catch (e: IOException) {
                throw ApiError(0, e.message ?: "Network error")
            }
        }
    }

    private class Bearer(val token: String?, val host: Boolean)

    private suspend fun bearerFor(auth: ApiAuth): Bearer {
        if (auth == ApiAuth.NONE) return Bearer(null, host = false)
        val stored = tokens.hostToken
        if (auth == ApiAuth.GUEST || (auth == ApiAuth.AUTO && stored == null)) {
            return Bearer(tokens.guestToken, host = false)
        }
        // Solo con sesión de host activa: tras cerrar sesión no se revive con Firebase.
        if (stored == null) return Bearer(null, host = false)
        return Bearer(freshHostToken(forceRefresh = false) ?: stored, host = true)
    }

    /** Pide un token al proveedor; si no hay o falla, null (se sigue con el guardado). */
    private suspend fun freshHostToken(forceRefresh: Boolean): String? {
        val provider = hostTokenProvider ?: return null
        val token = try {
            provider(forceRefresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (token != null) tokens.hostToken = token
        return token
    }

    private fun requestBody(method: String, body: String?): RequestBody? = when {
        body != null -> body.toRequestBody(JSON_TYPE)
        method == "GET" || method == "HEAD" -> null
        else -> ByteArray(0).toRequestBody(null)
    }

    private fun errorMessage(status: Int, text: String): String =
        runCatching { ApiJson.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }
            .getOrNull() ?: "API error $status"

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
