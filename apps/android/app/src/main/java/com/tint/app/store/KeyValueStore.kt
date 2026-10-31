package com.tint.app.store

/** Almacenamiento clave-valor mínimo (equivale al `localStorage` de la web). */
interface KeyValueStore {
    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)
}
