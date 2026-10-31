package com.tint.app.auth

/** Obtiene un ID token de Google (con el selector de cuentas del sistema). */
interface GoogleIdTokenSource {
    /** @throws GoogleSignInException si el usuario cancela o Google falla. */
    suspend fun getGoogleIdToken(): String
}

sealed class GoogleSignInException(message: String) : Exception(message) {
    class Cancelled : GoogleSignInException("Inicio de sesión cancelado")

    class Failed(message: String) : GoogleSignInException(message)
}
