package com.tint.app.join

import com.tint.app.identity.DeviceIdProvider
import com.tint.app.net.ApiAuth
import com.tint.app.net.ApiClient
import com.tint.app.net.ApiError
import com.tint.app.net.TokenStore
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

data class JoinedRoom(val roomId: String, val title: String?, val nick: String)

sealed interface ClaimResult {
    data class Joined(val room: JoinedRoom) : ClaimResult

    data class Failure(val message: String) : ClaimResult
}

/** Lo que el formulario de unirse necesita (se sustituye en las pruebas). */
interface RoomInvites {
    suspend fun claim(code: String, nick: String): ClaimResult
}

@Serializable
private class ClaimRequest(val code: String, val nick: String, val deviceId: String)

@Serializable
private class ClaimResponse(
    val token: String,
    val roomId: String,
    val nick: String? = null,
    val roomTitle: String? = null,
)

/**
 * Canjea una invitación (`POST /invitations/claim`, anónimo) como lo hace
 * `apps/web/src/pages/InviteLanding.tsx`. Guarda el token de invitado y **no toca** la
 * sesión de host: la web la borra en silencio, aquí se conserva.
 */
class InviteRepository(
    private val api: ApiClient,
    private val tokens: TokenStore,
    private val deviceId: DeviceIdProvider,
) : RoomInvites {
    override suspend fun claim(code: String, nick: String): ClaimResult = try {
        val res = api.post<ClaimRequest, ClaimResponse>(
            "/invitations/claim",
            ClaimRequest(code, nick, deviceId.get()),
            ApiAuth.NONE,
        )
        tokens.guestToken = res.token
        ClaimResult.Joined(JoinedRoom(res.roomId, res.roomTitle, res.nick ?: nick))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ClaimResult.Failure(errorMessage(e))
    }

    /** Mensajes de `inviteClaimErrorMessage` (apps/web/src/lib/invite.ts). */
    private fun errorMessage(e: Exception): String {
        if (e is ApiError) {
            return when {
                e.status == 403 && e.message == "BANNED" -> "El host te bloqueó en esta sala"
                e.status == 410 -> "Esta invitación expiró o la sala fue eliminada."
                e.status == 404 -> "Esta invitación no existe o la sala fue eliminada."
                e.status == 409 -> "Esta invitación fue revocada por el host."
                e.status == 0 -> "No se pudo conectar con el servidor. Revisa tu conexión."
                else -> "No se pudo unir a la sala"
            }
        }
        return "No se pudo unir a la sala"
    }
}
