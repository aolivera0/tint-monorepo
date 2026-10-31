package com.tint.app.ui

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tint.app.AppContainer
import com.tint.app.BuildConfig
import com.tint.app.join.JoinedRoom
import com.tint.app.net.TokenClaims
import com.tint.app.social.SocialClient
import com.tint.app.sync.ExoPlayerPort
import com.tint.app.sync.OkHttpSyncSocketFactory
import com.tint.app.sync.PositionTracker
import com.tint.app.sync.RoomController
import com.tint.app.sync.SyncClient
import com.tint.app.sync.SyncedPlayback
import kotlinx.coroutines.delay

/**
 * Sala del invitado: el vídeo sigue al host (play, pausa, seek, velocidad, volumen) por el Canal Sync.
 * Los participantes no tienen controles propios; el reproductor solo obedece al estado maestro.
 */
@Composable
fun RoomScreen(room: JoinedRoom, container: AppContainer, onLeave: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()
    val clock = { SystemClock.elapsedRealtime() }
    val tracker = remember { PositionTracker(clock) }
    val sync = remember(room.roomId) {
        SyncClient(
            roomId = room.roomId,
            token = { container.tokens.guestToken },
            urlFor = container.endpoints::syncUrl,
            sockets = OkHttpSyncSocketFactory(container.http),
            scope = scope,
            nowMs = clock,
            localPositionMs = tracker::current,
        )
    }
    val social = remember(room.roomId) {
        SocialClient(
            roomId = room.roomId,
            token = { container.tokens.guestToken },
            urlFor = container.endpoints::socialUrl,
            sockets = OkHttpSyncSocketFactory(container.http),
            scope = scope,
            nowMs = clock,
            selfUserId = { TokenClaims.subject(container.tokens.guestToken) },
        )
    }
    val controller = remember(room.roomId) { RoomController(room.roomId, sync, container.playback, scope) }
    val ui by controller.ui.collectAsState()
    val syncState by sync.state.collectAsState()
    val socialState by social.state.collectAsState()

    val player = remember { ExoPlayer.Builder(context).build() }
    val playback = remember(player) { SyncedPlayback(ExoPlayerPort(player), clock) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            // La URL prefirmada pudo caducar o la red falló: se pide una nueva.
            override fun onPlayerError(error: PlaybackException) = controller.recover()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Segundo plano: se pausa y se cierra el canal; al volver llega un STATE nuevo y el vídeo salta a la sala.
    DisposableEffect(activity, controller, social) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    controller.start()
                    social.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    player.pause()
                    tracker.clear()
                    controller.stop()
                    social.stop()
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            controller.stop()
            social.stop()
        }
    }

    LaunchedEffect(ui.videoUrl) {
        val url = ui.videoUrl
        if (url == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            syncState.snapshot?.let(playback::apply)
        }
    }
    LaunchedEffect(syncState.snapshot) {
        syncState.snapshot?.let {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "apply v=${it.state.version} status=${it.state.status} rate=${it.state.rate} pos=${it.state.positionMs}")
            }
            playback.apply(it)
        }
    }
    LaunchedEffect(syncState.correction?.seq) {
        syncState.correction?.let {
            if (BuildConfig.DEBUG) Log.d(TAG, "correction ${it.strategy} drift=${it.driftMs} target=${it.targetMs}")
            playback.onCorrection(it, syncState.snapshot?.state?.rate ?: 1.0)
        }
    }
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            val advancing = player.playWhenReady && player.playbackState == Player.STATE_READY
            tracker.update(player.currentPosition, if (advancing) player.playbackParameters.speed.toDouble() else 0.0)
            playback.tick()
            // Solo en debug: una línea por segundo para observar la sincronía con `adb logcat -s TintSync`.
            if (BuildConfig.DEBUG && ticks++ % TICKS_PER_LOG == 0) {
                Log.d(
                    TAG,
                    "drift=${playback.driftMs()} speed=${player.playbackParameters.speed} advancing=$advancing " +
                        "room=${syncState.snapshot?.state?.status}@${syncState.snapshot?.state?.rate} " +
                        "srvDrift=${syncState.driftMs} rtt=${syncState.rttMs}",
                )
            }
            delay(POSITION_TICK_MS)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TintBackground).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(room.title ?: "Sala sin título", style = MaterialTheme.typography.titleMedium, color = TintAccent, maxLines = 1)
                Text("Entraste como ${room.nick}", color = Color.Gray, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onLeave) { Text("Salir") }
        }
        Spacer(Modifier.height(8.dp))

        when {
            ui.revoked || socialState.revoked -> Notice("Tu invitación ya no es válida. El host te retiró de la sala.")
            ui.expired || socialState.expired -> Notice("Tu invitación caducó. Vuelve a unirte con el link.")
            else -> {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
                    )
                    if (ui.videoUrl == null) {
                        when {
                            ui.noVideo -> Text("La sala todavía no tiene vídeo", color = Color.White, textAlign = TextAlign.Center)
                            ui.error != null -> Text(ui.error!!, color = Color.White, textAlign = TextAlign.Center)
                            else -> CircularProgressIndicator(color = TintAccent)
                        }
                    }
                }
                Text(
                    statusLine(ui.connected, ui.hostOnline, syncState.reconnectAttempt, syncState.rttMs, syncState.driftMs),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
                if (ui.error != null && ui.videoUrl == null) {
                    Button(onClick = controller::recover) { Text("Reintentar") }
                }
                socialState.trivia?.let { trivia ->
                    TriviaCard(trivia, nowMs = clock, onAnswer = { social.answerTrivia(it) }, onDismiss = social::dismissTrivia)
                }
                ReactionBar(socialState) { emoji ->
                    social.sendReaction(emoji, tracker.current() ?: 0)
                }
                ChatPanel(
                    state = socialState,
                    selfUserId = TokenClaims.subject(container.tokens.guestToken),
                    nowMs = clock,
                    onSend = social::send,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
}

private fun statusLine(connected: Boolean, hostOnline: Boolean?, attempt: Int, rttMs: Long?, driftMs: Long?): String = when {
    !connected && attempt > 0 -> "Reconectando… intento $attempt"
    !connected -> "Conectando con la sala…"
    hostOnline == false -> "El host no está conectado"
    rttMs != null -> "Sincronizado · RTT $rttMs ms · desfase ${driftMs ?: 0} ms"
    else -> "Sincronizado con la sala"
}

private const val POSITION_TICK_MS = 200L
private const val TICKS_PER_LOG = 5
private const val TAG = "TintSync"
