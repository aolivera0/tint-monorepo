package com.tint.app.join

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface JoinState {
    data object Idle : JoinState

    data object Joining : JoinState

    data class Joined(val room: JoinedRoom) : JoinState

    /** El código o el nick no sirvieron: se muestra el motivo y se puede reintentar. */
    data class Error(val message: String) : JoinState
}

/** Estado del formulario de unirse a una sala, sin Compose para poder probarlo. */
class JoinController(private val invites: RoomInvites, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<JoinState>(JoinState.Idle)
    val state: StateFlow<JoinState> = _state

    /** `codeOrLink` es lo que escribió o pegó la persona: link, ruta o código. */
    fun submit(codeOrLink: String, nick: String) {
        if (_state.value == JoinState.Joining) return
        val code = InviteCode.parse(codeOrLink)
        if (code == null) {
            _state.value = JoinState.Error("Ese link no parece válido. Revisa que incluya /invite/ o el código.")
            return
        }
        val cleanNick = nick.trim()
        if (cleanNick.length !in NICK_LENGTH) {
            _state.value = JoinState.Error("El nick debe tener entre 2 y 24 caracteres.")
            return
        }
        _state.value = JoinState.Joining
        scope.launch {
            _state.value = when (val result = invites.claim(code, cleanNick)) {
                is ClaimResult.Joined -> JoinState.Joined(result.room)
                is ClaimResult.Failure -> JoinState.Error(result.message)
            }
        }
    }

    fun leave() {
        _state.value = JoinState.Idle
    }

    private companion object {
        val NICK_LENGTH = 2..24
    }
}
