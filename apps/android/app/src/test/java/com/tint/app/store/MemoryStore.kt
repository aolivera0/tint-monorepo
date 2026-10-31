package com.tint.app.store

/** [KeyValueStore] en memoria para las pruebas. */
class MemoryStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    val data = initial.toMutableMap()

    override fun get(key: String): String? = data[key]

    override fun put(key: String, value: String) {
        data[key] = value
    }

    override fun remove(key: String) {
        data.remove(key)
    }
}
