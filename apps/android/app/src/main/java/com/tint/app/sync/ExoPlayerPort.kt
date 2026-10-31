package com.tint.app.sync

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** [PlayerPort] sobre ExoPlayer. Solo se puede usar desde el hilo principal. */
class ExoPlayerPort(private val player: ExoPlayer) : PlayerPort {
    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long? get() = player.duration.takeIf { it != C.TIME_UNSET }
    override val isPlaying: Boolean get() = player.playWhenReady
    override val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override val isEnded: Boolean get() = player.playbackState == Player.STATE_ENDED

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(ms: Long) = player.seekTo(ms)

    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    override fun setVolume(volume: Float) {
        player.volume = volume
    }
}
