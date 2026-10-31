package com.tint.app.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomUiState(
    val connected: Boolean = false,
    val hostOnline: Boolean? = null,
    /** Vídeo actual de la sala; null mientras no se conoce. */
    val mediaId: String? = null,
    val videoUrl: String? = null,
    val loading: Boolean = false,
    /** La sala no tiene vídeo asignado todavía. */
    val noVideo: Boolean = false,
    val error: String? = null,
    val revoked: Boolean = false,
    val expired: Boolean = false,
)

/**
 * Une la sincronía con la URL del vídeo: cuando el estado maestro dice qué vídeo toca, pide su URL a
 * identity-api. Una respuesta tardía de un vídeo anterior no pisa al actual.
 */
class RoomController(
    private val roomId: String,
    private val session: SyncSession,
    private val playback: PlaybackSource,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(RoomUiState())
    val ui: StateFlow<RoomUiState> = _ui

    private var currentMedia: String? = null

    init {
        scope.launch { session.state.collect(::onSync) }
    }

    fun start() = session.start()

    fun stop() = session.stop()

    /** Vuelve a pedir la URL del vídeo actual (reproductor con error, URL caducada, reintento manual). */
    fun recover() {
        val media = currentMedia?.takeIf { it.isNotEmpty() } ?: return
        load(media, reset = false)
    }

    private fun onSync(sync: SyncUiState) {
        _ui.update {
            it.copy(
                connected = sync.connected,
                hostOnline = sync.hostOnline,
                expired = sync.expired,
                revoked = it.revoked || sync.revoked,
            )
        }
        val media = sync.snapshot?.state?.mediaId ?: return
        if (media == currentMedia) return
        currentMedia = media
        if (media.isEmpty()) {
            _ui.update { it.copy(mediaId = null, videoUrl = null, loading = false, noVideo = true, error = null) }
        } else {
            load(media, reset = true)
        }
    }

    private fun load(media: String, reset: Boolean) {
        _ui.update {
            it.copy(
                mediaId = media,
                videoUrl = if (reset) null else it.videoUrl,
                loading = true,
                noVideo = false,
                error = null,
            )
        }
        scope.launch {
            val result = playback.get(roomId)
            if (currentMedia != media) return@launch
            _ui.update {
                when (result) {
                    is PlaybackResult.Loaded -> {
                        val url = result.playback.url
                        if (url == null) {
                            it.copy(loading = false, error = "El vídeo se está preparando. Intenta de nuevo en un momento.")
                        } else {
                            it.copy(loading = false, videoUrl = url)
                        }
                    }
                    PlaybackResult.Revoked -> it.copy(loading = false, revoked = true)
                    is PlaybackResult.Failure -> it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
