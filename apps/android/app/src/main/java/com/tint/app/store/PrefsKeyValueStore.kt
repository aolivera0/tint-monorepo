package com.tint.app.store

import android.content.SharedPreferences

/** [KeyValueStore] sobre `SharedPreferences`; capa fina, se prueba en el dispositivo. */
class PrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
