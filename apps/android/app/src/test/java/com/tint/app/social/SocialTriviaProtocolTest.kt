package com.tint.app.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialTriviaProtocolTest {
    private val question =
        """{"event_id":"q1","type":"trivia_question","room_id":"r1","trivia_id":"t1","question":"¿Quién es Calcifer?",
        "options":["Un demonio de fuego","Un gato","Un mago"],"opens_at_ms":1000000,"closes_at_ms":1020000}"""

    @Test
    fun `parsea una pregunta de trivia`() {
        assertEquals(
            SocialMessage.TriviaQuestionMsg(
                TriviaQuestion("t1", "¿Quién es Calcifer?", listOf("Un demonio de fuego", "Un gato", "Un mago"), 1_000_000, 1_020_000),
            ),
            SocialProtocol.parse(question),
        )
    }

    @Test
    fun `una pregunta mal formada se ignora`() {
        assertNull(SocialProtocol.parse("""{"type":"trivia_question","trivia_id":"t1"}"""))
        assertNull(SocialProtocol.parse(question.replace("""["Un demonio de fuego","Un gato","Un mago"]""", "[]")))
        assertNull(SocialProtocol.parse(question.replace("\"opens_at_ms\":1000000,", "")))
    }

    @Test
    fun `parsea el ack con el puntaje`() {
        assertEquals(
            SocialMessage.TriviaAckMsg(TriviaAck("t1", 0, 1450)),
            SocialProtocol.parse("""{"type":"trivia_ack","trivia_id":"t1","option":0,"score":1450,"event_id_ref":"e"}"""),
        )
        assertNull(SocialProtocol.parse("""{"type":"trivia_ack","trivia_id":"t1"}"""))
    }

    @Test
    fun `parsea el resultado con ranking y puntaje propio`() {
        val raw = """{"event_id":"x","type":"trivia_result","trivia_id":"t1","correct":0,
            "top":[{"user_id":"u1","nick":"Ana","score":1800},{"user_id":"u2","nick":"Luis","score":1200}],
            "leaderboard":[{"user_id":"u1","nick":"Ana","score":2500}],"my_score":1200,"my_total":3000}"""

        assertEquals(
            SocialMessage.TriviaResultMsg(
                TriviaResult(
                    triviaId = "t1",
                    correct = 0,
                    top = listOf(RankEntry("u1", "Ana", 1800), RankEntry("u2", "Luis", 1200)),
                    leaderboard = listOf(RankEntry("u1", "Ana", 2500)),
                    myScore = 1200,
                    myTotal = 3000,
                ),
            ),
            SocialProtocol.parse(raw),
        )
    }

    @Test
    fun `el resultado tolera ranking y puntajes ausentes`() {
        val msg = SocialProtocol.parse("""{"type":"trivia_result","trivia_id":"t1","correct":2}""") as SocialMessage.TriviaResultMsg

        assertEquals(TriviaResult("t1", 2, emptyList(), emptyList(), null, null), msg.result)
        assertNull(SocialProtocol.parse("""{"type":"trivia_result","trivia_id":"t1"}"""))
    }

    @Test
    fun `las entradas de ranking incompletas se descartan`() {
        val msg = SocialProtocol.parse(
            """{"type":"trivia_result","trivia_id":"t1","correct":0,"top":[{"nick":"X"},{"user_id":"u","nick":"Ana","score":5}]}""",
        ) as SocialMessage.TriviaResultMsg

        assertEquals(listOf(RankEntry("u", "Ana", 5)), msg.result.top)
    }

    @Test
    fun `trivia_answer lleva la pregunta y el indice de la opcion`() {
        val json = Json.parseToJsonElement(SocialProtocol.triviaAnswer("r1", "t1", 2, "ev")).jsonObject

        assertEquals("trivia_answer", json["type"]!!.jsonPrimitive.content)
        assertEquals("r1", json["room_id"]!!.jsonPrimitive.content)
        assertEquals("t1", json["trivia_id"]!!.jsonPrimitive.content)
        assertEquals("2", json["option"]!!.jsonPrimitive.content)
        assertEquals("ev", json["event_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `mensajes de error de la trivia, iguales que en la web`() {
        assertEquals("Esa pregunta ya no existe.", SocialProtocol.errorMessage("TRIVIA_NOT_FOUND"))
        assertEquals("Se acabó el tiempo para responder.", SocialProtocol.errorMessage("TRIVIA_CLOSED"))
        assertEquals("Ya respondiste esta pregunta.", SocialProtocol.errorMessage("ALREADY_ANSWERED"))
    }
}
