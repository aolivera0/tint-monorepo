package com.tint.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Selector de cuentas de Google con Credential Manager. `serverClientId` es el cliente web
 * del proyecto Firebase (`default_web_client_id`, generado desde `google-services.json`).
 * Necesita el contexto de una Activity para mostrar el selector.
 */
class CredentialManagerGoogleSource(
    private val activity: Context,
    private val serverClientId: String,
) : GoogleIdTokenSource {
    override suspend fun getGoogleIdToken(): String {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInException.Cancelled()
        } catch (e: NoCredentialException) {
            throw GoogleSignInException.Failed("No hay cuentas de Google en este dispositivo.")
        } catch (e: GetCredentialException) {
            throw GoogleSignInException.Failed("No se pudo obtener la cuenta de Google.")
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw GoogleSignInException.Failed("No se pudo obtener la cuenta de Google.")
    }
}
