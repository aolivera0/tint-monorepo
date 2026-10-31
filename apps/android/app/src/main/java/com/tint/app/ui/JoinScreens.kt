package com.tint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tint.app.join.JoinState

/** Formulario para entrar a una sala con un link o código. `initialCode` viene de un deep link. */
@Composable
fun JoinScreen(state: JoinState, initialCode: String?, onSubmit: (code: String, nick: String) -> Unit, onBack: () -> Unit) {
    var code by remember(initialCode) { mutableStateOf(initialCode.orEmpty()) }
    var nick by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(TintBackground).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Unirme a una sala", style = MaterialTheme.typography.headlineSmall, color = TintAccent)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Link o código de invitación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nick,
            onValueChange = { nick = it },
            label = { Text("Tu nick") },
            supportingText = { Text("Así te verán en el chat y las reacciones.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (state is JoinState.Error) {
            Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        if (state == JoinState.Joining) {
            CircularProgressIndicator(color = TintAccent)
        } else {
            Button(onClick = { onSubmit(code, nick) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state is JoinState.Error) "Reintentar" else "Entrar a la sala")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}
