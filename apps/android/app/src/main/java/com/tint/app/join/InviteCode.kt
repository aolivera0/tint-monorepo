package com.tint.app.join

/** Extrae el código de invitación (espejo de `parseInviteCode` en `apps/web/src/lib/invite.ts`). */
object InviteCode {
    private val FROM_PATH = Regex("/invite/([A-Za-z0-9_-]+)")
    private val BARE = Regex("^[A-Za-z0-9_-]{6,32}$")
    private val DEEP_LINK = Regex("^(?:tint://invite/|https?://[^/]+/invite/)([A-Za-z0-9_-]+)")

    /** Acepta un link absoluto, una ruta `/invite/:code` o el código pelado. */
    fun parse(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        FROM_PATH.find(value)?.let { return it.groupValues[1] }
        return value.takeIf { BARE.matches(it) }
    }

    /** Código de un enlace que abrió la app: `tint://invite/:code` o `https://host/invite/:code`. */
    fun fromDeepLink(uri: String?): String? {
        val value = uri?.trim().orEmpty()
        return DEEP_LINK.find(value)?.groupValues?.get(1)
    }
}
