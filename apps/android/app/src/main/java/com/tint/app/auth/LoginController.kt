package com.tint.app.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object SignedOut : LoginState

    data object Loading : LoginState

    data object SignedIn : LoginState

    /** Un intento falló o se canceló: se muestra el motivo y se puede reintentar. */
    data class Error(val message: String) : LoginState
}

/** Estado de la pantalla de acceso: sin lógica de Compose, para poder probarla. */
class LoginController(private val auth: SessionAuth, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<LoginState>(
        if (auth.isSignedIn()) LoginState.SignedIn else LoginState.SignedOut,
    )
    val state: StateFlow<LoginState> = _state

    fun signIn() {
        if (_state.value == LoginState.Loading) return
        _state.value = LoginState.Loading
        scope.launch {
            _state.value = when (val result = auth.signInWithGoogle()) {
                SignInResult.Success -> LoginState.SignedIn
                is SignInResult.Failure -> LoginState.Error(result.message)
            }
        }
    }

    fun signOut() {
        scope.launch {
            auth.signOut()
            _state.value = LoginState.SignedOut
        }
    }

    /** El backend rechazó la sesión de host: hay que volver a iniciar sesión. */
    fun onSessionExpired() {
        _state.value = LoginState.Error("Tu sesión caducó. Inicia sesión de nuevo.")
    }
}
