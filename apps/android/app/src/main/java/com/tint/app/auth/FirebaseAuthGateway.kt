package com.tint.app.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [FirebaseGateway] sobre el SDK de Firebase Auth; capa fina, se prueba en el dispositivo. */
class FirebaseAuthGateway(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : FirebaseGateway {
    override suspend fun signInWithGoogle(googleIdToken: String): String {
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val user = auth.signInWithCredential(credential).await().user ?: error("Firebase no devolvió usuario")
        return user.getIdToken(false).await().token ?: error("Firebase no devolvió ID token")
    }

    override suspend fun idToken(forceRefresh: Boolean): String? =
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.token

    override fun signOut() = auth.signOut()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
