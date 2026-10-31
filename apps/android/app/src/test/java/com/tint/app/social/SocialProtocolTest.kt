package com.tint.app.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialProtocolTest {
    private fun chat(id: String = "e1", extra: String = "") =
        """{"event_id":"$id","type":"chat_message","room_id":"r1","user_id":"guest:a","nick":"Ana","text":"hola",
        "occurred_at":"2026-10-31T12:00:00Z","received_at":"2026-10-31T12:00:00Z"$extra}"""

    @Test
    fun `parsea un chat_message con y sin spoiler`() {
        assertEquals(
            SocialMessage.Chat(ChatItem.Message("e1", "guest:a", "Ana", "hola", spoiler = false)),
            SocialProtocol.parse(chat()),
        )
        assertEquals(
            SocialMessage.Chat(ChatItem.Message("e1", "guest:a", "Ana", "hola", spoiler = true)),
            SocialProtocol.parse(chat(extra = ""","spoiler":true""")),
        )
    }

    @Test
    fun `parsea el historial y descarta los mensajes mal formados`() {
        val raw = """{"type":"history","room_id":"r1","messages":[${chat("a")},{"type":"chat_message"},${chat("b")}]}"""

        val msg = SocialProtocol.parse(raw) as SocialMessage.History

        assertEquals(listOf("a", "b"), msg.items.map { it.eventId })
    }

    @Test
    fun `un historial vacio o sin mensajes es valido`() {
        assertEquals(SocialMessage.History(emptyList()), SocialProtocol.parse("""{"type":"history","messages":[]}"""))
        assertEquals(SocialMessage.History(emptyList()), SocialProtocol.parse("""{"type":"history"}"""))
    }

    @Test
    fun `parsea los avisos de presencia`() {
        val msg = SocialProtocol.parse(
            """{"event_id":"n1","type":"notice","room_id":"r1","user_id":"u","nick":"Ana","kind":"joined","text":"Ana se unió","occurred_at":"x"}""",
        )

        assertEquals(SocialMessage.Chat(ChatItem.Notice("n1", "Ana se unió")), msg)
    }

    @Test
    fun `parsea los ajustes del chat`() {
        assertEquals(
            SocialMessage.Settings(10, listOf("guest:a", "guest:b")),
            SocialProtocol.parse("""{"type":"chat_settings","slow_mode_s":10,"muted_user_ids":["guest:a","guest:b"]}"""),
        )
        assertEquals(
            SocialMessage.Settings(0, emptyList()),
            SocialProtocol.parse("""{"type":"chat_settings"}"""),
        )
        assertEquals(
            SocialMessage.Settings(5, listOf("a")),
            SocialProtocol.parse("""{"type":"chat_settings","slow_mode_s":5,"muted_user_ids":["a",7,null]}"""),
        )
    }

    @Test
    fun `parsea reacciones y errores`() {
        assertEquals(
            SocialMessage.ReactionMsg(Reaction("r1", "guest:a", "Ana", "😂", 61_000)),
            SocialProtocol.parse(
                """{"event_id":"r1","type":"reaction","room_id":"x","user_id":"guest:a","nick":"Ana","emoji":"😂","position_ms":61000}""",
            ),
        )
        assertEquals(SocialMessage.Error("MUTED", null), SocialProtocol.parse("""{"type":"error","code":"MUTED"}"""))
        assertEquals(
            SocialMessage.Error("SLOW_MODE", 4000),
            SocialProtocol.parse("""{"type":"error","code":"SLOW_MODE","retry_after_ms":4000,"event_id_ref":"e"}"""),
        )
    }

    @Test
    fun `ignora lo desconocido o incompleto`() {
        assertNull(SocialProtocol.parse("basura"))
        assertNull(SocialProtocol.parse("[]"))
        assertNull(SocialProtocol.parse("""{"type":"poll_state","poll_id":"p"}"""))
        assertNull(SocialProtocol.parse("""{"type":"laser"}"""))
        assertNull(SocialProtocol.parse("""{"type":"chat_message","event_id":"x"}"""))
        assertNull(SocialProtocol.parse("""{"type":"notice","event_id":"n"}"""))
        assertNull(SocialProtocol.parse("""{"type":"reaction","event_id":"r"}"""))
        assertNull(SocialProtocol.parse("""{"type":"error"}"""))
    }

    @Test
    fun `chat_send lleva el texto y el spoiler solo si se pide`() {
        val plain = Json.parseToJsonElement(SocialProtocol.chatSend("r1", "hola", false, "ev", "2026-10-31T12:00:00Z")).jsonObject
        assertEquals("chat_send", plain["type"]!!.jsonPrimitive.content)
        assertEquals("r1", plain["room_id"]!!.jsonPrimitive.content)
        assertEquals("hola", plain["text"]!!.jsonPrimitive.content)
        assertEquals("ev", plain["event_id"]!!.jsonPrimitive.content)
        assertEquals("2026-10-31T12:00:00Z", plain["occurred_at"]!!.jsonPrimitive.content)
        assertFalse(plain.containsKey("spoiler"))

        val spoiler = Json.parseToJsonElement(SocialProtocol.chatSend("r1", "x", true, "ev", "t")).jsonObject
        assertEquals("true", spoiler["spoiler"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reaction_send lleva el emoji y la posicion sin negativos`() {
        val json = Json.parseToJsonElement(SocialProtocol.reactionSend("r1", "❤️", -5, "ev", "t")).jsonObject

        assertEquals("reaction_send", json["type"]!!.jsonPrimitive.content)
        assertEquals("❤️", json["emoji"]!!.jsonPrimitive.content)
        assertEquals("0", json["position_ms"]!!.jsonPrimitive.content)
    }

    @Test
    fun `el conjunto de reacciones es el de la web`() {
        assertEquals(listOf("👏", "😂", "😮", "😱", "❤️", "😢"), SocialProtocol.REACTION_EMOJIS)
        assertTrue(SocialProtocol.isReaction("😂"))
        assertFalse(SocialProtocol.isReaction("🙂"))
    }

    @Test
    fun `mensajes de error comprensibles, iguales que en la web`() {
        assertEquals("El chat está en modo lento: espera un momento para volver a escribir.", SocialProtocol.errorMessage("SLOW_MODE"))
        assertEquals("El host te silenció; puedes leer el chat.", SocialProtocol.errorMessage("MUTED"))
        assertEquals("Vas muy rápido: ve más despacio.", SocialProtocol.errorMessage("RATE_LIMITED"))
        assertEquals("No tienes permiso para hacer eso.", SocialProtocol.errorMessage("FORBIDDEN"))
        assertEquals("El chat no está disponible ahora. Inténtalo en un momento.", SocialProtocol.errorMessage("UNAVAILABLE"))
        assertEquals("No se pudo completar la acción. Inténtalo de nuevo.", SocialProtocol.errorMessage("CUALQUIERA"))
    }
}
