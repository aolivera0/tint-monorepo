package com.tint.app.net

import com.tint.app.store.KeyValueStore

/**
 * Credenciales de la sesión: el ID token de Firebase del host (`tint_token`) y el token de
 * invitado de identity-api (`tint_guest_token`). Mismas claves que la web.
 */
interface TokenStore {
    var hostToken: String?
    var guestToken: String?
}

class InMemoryTokenStore : TokenStore {
    override var hostToken: String? = null
    override var guestToken: String? = null
}

/** [TokenStore] persistente sobre cualquier [KeyValueStore]. */
class StoredTokenStore(private val store: KeyValueStore) : TokenStore {
    override var hostToken: String?
        get() = store.get(HOST_KEY)
        set(value) = write(HOST_KEY, value)

    override var guestToken: String?
        get() = store.get(GUEST_KEY)
        set(value) = write(GUEST_KEY, value)

    private fun write(key: String, value: String?) {
        if (value == null) store.remove(key) else store.put(key, value)
    }

    companion object {
        const val HOST_KEY = "tint_token"
        const val GUEST_KEY = "tint_guest_token"
    }
}
