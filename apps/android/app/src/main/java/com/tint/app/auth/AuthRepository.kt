package com.tint.app.auth

import com.tint.app.net.ApiAuth
import com.tint.app.net.ApiClient
import com.tint.app.net.ApiError
import com.tint.app.net.TokenStore
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

sealed interface SignInResult {
    data object Success : SignInResult

    data class Failure(val message: String) : SignInResult
}

/** Lo que la pantalla de acceso necesita de la sesión (se sustituye en las pruebas). */
interface SessionAuth {
    fun isSignedIn(): Boolean

    suspend fun signInWithGoogle(): SignInResult

    suspend fun signOut()
}

@Serializable
private class EmptyBody

@Serializable
private class VerifyResponse(val isRegistered: Boolean = false)

/**
 * Sesión de host con Google, espejo de `pages/Login.tsx` y `lib/authSession.ts`:
 * Google → Firebase → `tint_token` → `POST /auth/verify`. Si algo falla no queda sesión.
 */
class AuthRepository(
    private val api: ApiClient,
    private val tokens: TokenStore,
    private val google: GoogleIdTokenSource,
    private val firebase: FirebaseGateway,
) : SessionAuth {
    override fun isSignedIn(): Boolean = tokens.hostToken != null

    override suspend fun signInWithGoogle(): SignInResult {
        val firebaseToken = try {
            firebase.signInWithGoogle(google.getGoogleIdToken())
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleSignInException.Cancelled) {
            return SignInResult.Failure(CANCELLED)
        } catch (e: GoogleSignInException.Failed) {
            return SignInResult.Failure(e.message ?: GENERIC)
        } catch (e: Exception) {
            return SignInResult.Failure(GENERIC)
        }
        tokens.hostToken = firebaseToken
        // La sesión de host manda: se borran los restos de invitado.
        tokens.guestToken = null
        return try {
            api.post<EmptyBody, VerifyResponse>("/auth/verify", EmptyBody(), ApiAuth.HOST)
            SignInResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tokens.hostToken = null
            firebase.signOut()
            SignInResult.Failure(if (e is ApiError && e.status == 0) NO_NETWORK else GENERIC)
        }
    }

    override suspend fun signOut() {
        tokens.hostToken = null
        tokens.guestToken = null
        firebase.signOut()
    }

    private companion object {
        const val CANCELLED = "Cancelaste el inicio de sesión. Puedes intentarlo de nuevo."
        const val NO_NETWORK = "No se pudo conectar con el servidor. Revisa tu conexión."
        const val GENERIC = "No se pudo iniciar sesión"
    }
}
