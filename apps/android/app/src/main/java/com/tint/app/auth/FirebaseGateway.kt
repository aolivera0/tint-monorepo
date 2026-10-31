package com.tint.app.auth

/** Lo que la app necesita de Firebase Auth (el mismo proyecto que usa la web). */
interface FirebaseGateway {
    /** Canjea el ID token de Google por la sesión de Firebase y devuelve su ID token. */
    suspend fun signInWithGoogle(googleIdToken: String): String

    /** ID token vigente del usuario actual (renovado si hace falta) o null si no hay sesión. */
    suspend fun idToken(forceRefresh: Boolean): String?

    fun signOut()
}
