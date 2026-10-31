package com.tint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tint.app.social.RankEntry
import com.tint.app.social.TriviaState
import kotlinx.coroutines.delay

private val CardBg = Color(0xFF1B1E26)
private val Good = Color(0xFF6EE7B7)

/**
 * Pregunta de trivia sobre la sala. Cuenta regresiva (reloj local), opciones, lo que pasó con mi respuesta
 * al enviarla (el servidor devuelve el puntaje al instante) y, al cierre, la respuesta correcta y el ranking.
 */
@Composable
fun TriviaCard(trivia: TriviaState, nowMs: () -> Long, onAnswer: (Int) -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(trivia.question.triviaId, trivia.result) {
        while (trivia.result == null) {
            now = nowMs()
            delay(250)
        }
    }
    val remaining = trivia.remainingMs(now)
    val q = trivia.question
    val result = trivia.result

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(CardBg, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TRIVIA", color = TintAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (result == null && remaining > 0) Text("${(remaining + 999) / 1000} s", color = Color.Gray, fontSize = 12.sp)
        }
        Text(q.question, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))

        if (result == null && remaining > 0 && trivia.myOption == null) {
            LinearProgressIndicator(
                progress = { if (trivia.windowMs > 0) remaining.toFloat() / trivia.windowMs else 0f },
                color = TintAccent,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            q.options.forEachIndexed { index, option ->
                OutlinedButton(onClick = { onAnswer(index) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
            }
            return@Column
        }

        val mine = trivia.myOption?.let { q.options.getOrNull(it) }
        if (mine != null) Text("Tu respuesta: $mine", color = Color.Gray, fontSize = 12.sp)

        when {
            result != null -> ResultBlock(trivia, onDismiss)
            trivia.ack != null -> {
                val score = trivia.ack.score
                Text(
                    if (score > 0) "¡Correcto! +$score puntos" else "Incorrecto · 0 puntos",
                    color = if (score > 0) Good else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Text("Esperando el cierre de la pregunta…", color = Color.Gray, fontSize = 12.sp)
            }
            trivia.myOption != null -> Text("Respuesta enviada…", color = Color.Gray)
            else -> Text("Se acabó el tiempo.", color = Color.Gray)
        }
    }
}

@Composable
private fun ResultBlock(trivia: TriviaState, onDismiss: () -> Unit) {
    val result = trivia.result ?: return
    val correct = trivia.question.options.getOrNull(result.correct) ?: "—"
    val points = result.myScore ?: trivia.ack?.score ?: 0
    Text("Respuesta correcta: $correct", color = Good, fontWeight = FontWeight.Bold)
    Text(
        when {
            trivia.myOption == null -> "Se acabó el tiempo sin responder: $points puntos"
            trivia.myOption == result.correct -> "¡Acertaste! Tus puntos: $points"
            else -> "Esta vez no. Tus puntos: $points"
        },
        color = Color.White,
    )
    result.myTotal?.let { Text("Total en la sala: $it", color = Color.Gray, fontSize = 12.sp) }
    Ranking("Top de la pregunta", result.top)
    Ranking("Ranking de la sala", result.leaderboard)
    Spacer(Modifier.height(4.dp))
    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
}

@Composable
private fun Ranking(title: String, entries: List<RankEntry>) {
    if (entries.isEmpty()) return
    Text(title, color = TintAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    entries.take(3).forEachIndexed { i, e ->
        Text("${i + 1}. ${e.nick} · ${e.score}", color = Color.White, fontSize = 12.sp)
    }
}
