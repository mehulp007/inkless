package com.mehul.inkless.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.mehul.inkless.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Gets a Google ID token through Credential Manager, which shows the system account picker. */
interface GoogleIdTokens {
    /** [activity] must be an Activity context, the picker is shown on top of it. */
    suspend fun request(activity: Context): String

    /** Forget the picked account, so the picker shows again after logging out. */
    suspend fun clear()
}

class CredentialManagerGoogleIdTokens @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : GoogleIdTokens {

    override suspend fun request(activity: Context): String {
        // Web client id, written into resources by the google-services plugin from google-services.json.
        val option = GetSignInWithGoogleOption.Builder(appContext.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw AuthException(AuthFailure.CANCELLED, e)
        } catch (e: NoCredentialException) {
            throw AuthException(AuthFailure.GOOGLE_NO_ACCOUNT, e)
        } catch (e: GetCredentialException) {
            throw AuthException(AuthFailure.GOOGLE_UNAVAILABLE, e)
        }

        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AuthException(AuthFailure.GOOGLE_UNAVAILABLE)
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthException(AuthFailure.GOOGLE_UNAVAILABLE, e)
        }
    }

    override suspend fun clear() {
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            // Nothing to clear, or no provider on this phone.
        }
    }
}
