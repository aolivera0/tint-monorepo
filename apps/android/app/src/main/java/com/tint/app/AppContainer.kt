package com.tint.app

import android.content.Context
import com.tint.app.auth.FirebaseAuthGateway
import com.tint.app.config.Endpoints
import com.tint.app.identity.DeviceIdProvider
import com.tint.app.join.InviteRepository
import com.tint.app.net.ApiClient
import com.tint.app.net.StoredTokenStore
import com.tint.app.store.PrefsKeyValueStore
import com.tint.app.sync.PlaybackRepository
import okhttp3.OkHttpClient

/** Dependencias de la app (un único grafo, sin framework de inyección). */
class AppContainer(context: Context) {
    val endpoints = Endpoints(
        identityApiUrl = BuildConfig.IDENTITY_API_URL,
        syncWsUrl = BuildConfig.SYNC_WS_URL,
        socialWsUrl = BuildConfig.SOCIAL_WS_URL,
    )

    private val store = PrefsKeyValueStore(context.getSharedPreferences("tint", Context.MODE_PRIVATE))
    val tokens = StoredTokenStore(store)
    val deviceId = DeviceIdProvider(store)
    val firebase = FirebaseAuthGateway()
    val http: OkHttpClient = OkHttpClient()

    /** La pantalla de acceso se suscribe aquí para saber cuándo caduca la sesión de host. */
    var onHostSessionExpired: () -> Unit = {}

    val api = ApiClient(
        baseUrl = endpoints.identityApiUrl,
        http = http,
        tokens = tokens,
        onHostSessionExpired = { onHostSessionExpired() },
        hostTokenProvider = firebase::idToken,
    )

    val invites = InviteRepository(api, tokens, deviceId)
    val playback = PlaybackRepository(api, tokens)
}
