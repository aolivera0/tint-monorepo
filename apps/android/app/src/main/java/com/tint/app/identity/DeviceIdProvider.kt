package com.tint.app.identity

import com.tint.app.store.KeyValueStore
import java.util.UUID

/**
 * Identificador anónimo de la instalación (`tint_device_id`). Se envía como `deviceId` al
 * canjear una invitación para que el host pueda bloquear por dispositivo (ADR 0013).
 * Borrar los datos de la app lo cambia, igual que en la web.
 */
class DeviceIdProvider(
    private val store: KeyValueStore,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    private var cached: String? = null

    fun get(): String {
        val stored = store.get(KEY)
        if (stored != null && UUID_RE.matches(stored)) {
            cached = stored
            return stored
        }
        cached?.let { return it }
        val id = newUuid()
        cached = id
        store.put(KEY, id)
        return id
    }

    companion object {
        const val KEY = "tint_device_id"
        private val UUID_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }
}
