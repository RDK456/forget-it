package app.forgetit.gmail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Google sign-in for read-only Gmail access. Needs an Android OAuth client for this package in Google Cloud (see README). */
object GmailAuth {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    sealed interface Result {
        data class Token(val value: String, val email: String?) : Result
        data class NeedsConsent(val intent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    /** Turns Google's terse errors into what the person has to do. */
    fun explain(raw: String?): String {
        val m = raw.orEmpty()
        return when {
            m.contains("8:") || m.contains("UNREGISTERED", true) || m.contains("10:") || m.contains("DEVELOPER_ERROR", true) || m.contains("12500") || m.contains("16:") ->
                "Google does not recognise this app yet. Gmail needs a one-time Google Cloud setup: an Android OAuth client for app.forgetit with this app's SHA-1, and your Gmail address added as a test user. Steps are in the README under Gmail setup."
            m.contains("7:") || m.contains("network", true) -> "Could not reach Google. Check your connection and try again."
            m.contains("access_denied", true) || m.contains("blocked", true) -> "Google blocked the sign-in. Add your Gmail address as a test user on the OAuth consent screen, then try again."
            m.isBlank() -> "Google sign-in failed"
            else -> "Google sign-in failed: " + m
        }
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private fun fromResult(r: AuthorizationResult): Result {
        if (r.hasResolution()) return r.pendingIntent?.let { Result.NeedsConsent(it) } ?: Result.Failed("Google sign-in needs your approval")
        val token = r.accessToken ?: return Result.Failed("Google did not return access")
        return Result.Token(token, runCatching { r.toGoogleSignInAccount()?.email }.getOrNull())
    }

    /** Silent when the user already approved; otherwise asks for consent. Works from a background worker too. */
    suspend fun authorize(context: Context): Result = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { if (cont.isActive) cont.resume(fromResult(it)) }
            .addOnFailureListener { if (cont.isActive) cont.resume(Result.Failed(explain(it.message))) }
    }

    fun fromIntent(context: Context, data: Intent): Result = try {
        fromResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        Result.Failed(explain(e.message))
    }
}
