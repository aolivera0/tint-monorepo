package com.tint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tint.app.auth.LoginState

val TintAccent = Color(0xFFFF8A1A)
val TintBackground = Color(0xFF0F1115)

@Composable
fun LoginScreen(state: LoginState, onSignIn: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(TintBackground).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TINT", style = MaterialTheme.typography.displayMedium, color = TintAccent)
        Spacer(Modifier.height(8.dp))
        Text("Mira, comenta y compite en sincronía", color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        if (state is LoginState.Error) {
            Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
        }
        if (state == LoginState.Loading) {
            CircularProgressIndicator(color = TintAccent)
        } else {
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text(if (state is LoginState.Error) "Reintentar con Google" else "Iniciar sesión con Google")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
                Text("Tengo un link o código")
            }
        }
    }
}

@Composable
fun HomeScreen(onJoin: () -> Unit, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(TintBackground).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TINT", style = MaterialTheme.typography.displayMedium, color = TintAccent)
        Spacer(Modifier.height(8.dp))
        Text("Sesión iniciada", color = Color.White)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
            Text("Unirme a una sala")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
        }
    }
}
