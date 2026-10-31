package com.tint.app.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** Una línea del chat: un mensaje de alguien o un aviso de presencia ("Ana se unió"). */
sealed interface ChatItem {
    val eventId: String

    data class Message(
        override val eventId: String,
        val userId: String,
        val nick: String,
        val text: String,
        /** El autor pidió ocultar el texto hasta que se toque. */
        val spoiler: Boolean,
    ) : ChatItem

    data class Notice(override val eventId: String, val text: String) : ChatItem
}

data class Reaction(val eventId: String, val userId: String, val nick: String, val emoji: String, val positionMs: Long)

/** Pregunta lanzada a todos (sin la respuesta correcta). `*AtMs` son del reloj del servidor. */
data class TriviaQuestion(
    val triviaId: String,
    val question: String,
    val options: List<String>,
    val opensAtMs: Long,
    val closesAtMs: Long,
)

/** Confirmación de mi respuesta: `score` es 0 si falló; si acertó, 1000 más un bono por rapidez. */
data class TriviaAck(val triviaId: String, val option: Int, val score: Int)

data class RankEntry(val userId: String, val nick: String, val score: Int)

/** Cierre de la pregunta: la correcta (índice), el top de la pregunta y el acumulado de la sala. */
data class TriviaResult(
    val triviaId: String,
    val correct: Int,
    val top: List<RankEntry>,
    val leaderboard: List<RankEntry>,
    val myScore: Int?,
    val myTotal: Int?,
)

sealed interface SocialMessage {
    /** Se manda al entrar: los últimos mensajes de la sala. */
    data class History(val items: List<ChatItem>) : SocialMessage

    data class Chat(val item: ChatItem) : SocialMessage

    /** Modo lento (segundos, 0 = apagado) y quiénes están silenciados (`sub`). */
    data class Settings(val slowModeS: Int, val mutedUserIds: List<String>) : SocialMessage

    data class ReactionMsg(val reaction: Reaction) : SocialMessage

    data class Error(val code: String, val retryAfterMs: Long?) : SocialMessage

    data class TriviaQuestionMsg(val question: TriviaQuestion) : SocialMessage

    data class TriviaAckMsg(val ack: TriviaAck) : SocialMessage

    data class TriviaResultMsg(val result: TriviaResult) : SocialMessage
}

/**
 * Protocolo del Canal Social (`/ws/social`), parte del chat y las reacciones. Espejo de
 * `apps/web/src/lib/socialSocket.ts`; el contrato está en `services/social-core/AGENTS.md`.
 * Los mensajes de encuestas, láser y recap se ignoran por ahora.
 */
object SocialProtocol {
    const val CHAT_MAX_LENGTH = 280
    const val HISTORY_LIMIT = 50

    /** Mismo conjunto que la web (aplauso, risa, sorpresa, susto, corazón, tristeza). */
    val REACTION_EMOJIS = listOf("👏", "😂", "😮", "😱", "❤️", "😢")

    private val ERROR_MESSAGES = mapOf(
        "SLOW_MODE" to "El chat está en modo lento: espera un momento para volver a escribir.",
        "MUTED" to "El host te silenció; puedes leer el chat.",
        "FORBIDDEN" to "No tienes permiso para hacer eso.",
        "RATE_LIMITED" to "Vas muy rápido: ve más despacio.",
        "UNAVAILABLE" to "El chat no está disponible ahora. Inténtalo en un momento.",
        "TRIVIA_NOT_FOUND" to "Esa pregunta ya no existe.",
        "TRIVIA_CLOSED" to "Se acabó el tiempo para responder.",
        "ALREADY_ANSWERED" to "Ya respondiste esta pregunta.",
    )
    private const val GENERIC_ERROR = "No se pudo completar la acción. Inténtalo de nuevo."

    fun isReaction(emoji: String): Boolean = emoji in REACTION_EMOJIS

    fun errorMessage(code: String): String = ERROR_MESSAGES[code] ?: GENERIC_ERROR

    /** Parsea un frame del servidor; null si no es del chat o le faltan campos. */
    fun parse(raw: String): SocialMessage? {
        val obj = try {
            Json.parseToJsonElement(raw) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        return when (obj.string("type")) {
            "chat_message" -> chatMessage(obj)?.let { SocialMessage.Chat(it) }
            "notice" -> notice(obj)?.let { SocialMessage.Chat(it) }
            "history" -> SocialMessage.History(
                (obj["messages"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::chatMessage) },
            )
            "chat_settings" -> SocialMessage.Settings(
                slowModeS = obj.long("slow_mode_s")?.toInt() ?: 0,
                mutedUserIds = (obj["muted_user_ids"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            )
            "reaction" -> reaction(obj)?.let { SocialMessage.ReactionMsg(it) }
            "error" -> obj.string("code")?.let { SocialMessage.Error(it, obj.long("retry_after_ms")) }
            "trivia_question" -> triviaQuestion(obj)?.let { SocialMessage.TriviaQuestionMsg(it) }
            "trivia_ack" -> triviaAck(obj)?.let { SocialMessage.TriviaAckMsg(it) }
            "trivia_result" -> triviaResult(obj)?.let { SocialMessage.TriviaResultMsg(it) }
            else -> null
        }
    }

    private fun chatMessage(o: JsonObject): ChatItem.Message? {
        return ChatItem.Message(
            eventId = o.string("event_id") ?: return null,
            userId = o.string("user_id") ?: return null,
            nick = o.string("nick") ?: return null,
            text = o.string("text") ?: return null,
            spoiler = o.bool("spoiler") ?: false,
        )
    }

    private fun notice(o: JsonObject): ChatItem.Notice? {
        return ChatItem.Notice(o.string("event_id") ?: return null, o.string("text") ?: return null)
    }

    private fun reaction(o: JsonObject): Reaction? {
        return Reaction(
            eventId = o.string("event_id") ?: return null,
            userId = o.string("user_id") ?: return null,
            nick = o.string("nick") ?: return null,
            emoji = o.string("emoji") ?: return null,
            positionMs = o.long("position_ms") ?: 0,
        )
    }

    private fun triviaQuestion(o: JsonObject): TriviaQuestion? {
        val options = (o["options"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        if (options.isEmpty()) return null
        return TriviaQuestion(
            triviaId = o.string("trivia_id") ?: return null,
            question = o.string("question") ?: return null,
            options = options,
            opensAtMs = o.long("opens_at_ms") ?: return null,
            closesAtMs = o.long("closes_at_ms") ?: return null,
        )
    }

    private fun triviaAck(o: JsonObject): TriviaAck? {
        return TriviaAck(
            triviaId = o.string("trivia_id") ?: return null,
            option = o.long("option")?.toInt() ?: return null,
            score = o.long("score")?.toInt() ?: return null,
        )
    }

    private fun triviaResult(o: JsonObject): TriviaResult? {
        return TriviaResult(
            triviaId = o.string("trivia_id") ?: return null,
            correct = o.long("correct")?.toInt() ?: return null,
            top = rank(o["top"]),
            leaderboard = rank(o["leaderboard"]),
            myScore = o.long("my_score")?.toInt(),
            myTotal = o.long("my_total")?.toInt(),
        )
    }

    private fun rank(element: kotlinx.serialization.json.JsonElement?): List<RankEntry> =
        (element as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            RankEntry(
                userId = o.string("user_id") ?: return@mapNotNull null,
                nick = o.string("nick") ?: return@mapNotNull null,
                score = o.long("score")?.toInt() ?: return@mapNotNull null,
            )
        }

    fun triviaAnswer(roomId: String, triviaId: String, option: Int, eventId: String): String =
        buildJsonObject {
            put("event_id", eventId)
            put("type", "trivia_answer")
            put("room_id", roomId)
            put("trivia_id", triviaId)
            put("option", option)
        }.toString()

    fun chatSend(roomId: String, text: String, spoiler: Boolean, eventId: String, occurredAt: String): String =
        buildJsonObject {
            put("event_id", eventId)
            put("type", "chat_send")
            put("room_id", roomId)
            put("text", text)
            put("occurred_at", occurredAt)
            if (spoiler) put("spoiler", true)
        }.toString()

    fun reactionSend(roomId: String, emoji: String, positionMs: Long, eventId: String, occurredAt: String): String =
        buildJsonObject {
            put("event_id", eventId)
            put("type", "reaction_send")
            put("room_id", roomId)
            put("emoji", emoji)
            put("position_ms", positionMs.coerceAtLeast(0))
            put("occurred_at", occurredAt)
        }.toString()

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        primitive(key)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }?.toLong()

    private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
