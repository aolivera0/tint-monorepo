package com.tint.app.join

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteCodeTest {
    @Test
    fun `extrae el codigo de un link absoluto`() {
        assertEquals("wVRbfj_3dEnd", InviteCode.parse("https://tint.jaucor.online/invite/wVRbfj_3dEnd"))
    }

    @Test
    fun `extrae el codigo de una ruta relativa`() {
        assertEquals("abc-123_XY", InviteCode.parse("/invite/abc-123_XY"))
    }

    @Test
    fun `acepta el codigo pelado y recorta espacios`() {
        assertEquals("wVRbfj_3dEnd", InviteCode.parse("  wVRbfj_3dEnd \n"))
    }

    @Test
    fun `ignora lo que venga despues del codigo en el link`() {
        assertEquals("abcdef12", InviteCode.parse("https://h/invite/abcdef12?utm=1#x"))
    }

    @Test
    fun `rechaza vacio, muy corto, muy largo o con caracteres raros`() {
        assertNull(InviteCode.parse(""))
        assertNull(InviteCode.parse("   "))
        assertNull(InviteCode.parse("abc"))
        assertNull(InviteCode.parse("a".repeat(33)))
        assertNull(InviteCode.parse("hola mundo!"))
        assertNull(InviteCode.parse("https://tint.jaucor.online/"))
    }

    @Test
    fun `el deep link con esquema propio y el https dan el codigo`() {
        assertEquals("wVRbfj_3dEnd", InviteCode.fromDeepLink("tint://invite/wVRbfj_3dEnd"))
        assertEquals("wVRbfj_3dEnd", InviteCode.fromDeepLink("https://tint.jaucor.online/invite/wVRbfj_3dEnd"))
    }

    @Test
    fun `un deep link ajeno o vacio no da codigo`() {
        assertNull(InviteCode.fromDeepLink(null))
        assertNull(InviteCode.fromDeepLink(""))
        assertNull(InviteCode.fromDeepLink("tint://otra/cosa"))
        assertNull(InviteCode.fromDeepLink("https://tint.jaucor.online/login"))
    }
}
