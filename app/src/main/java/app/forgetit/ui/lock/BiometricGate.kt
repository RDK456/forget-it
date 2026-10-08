package app.forgetit.ui.lock

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
private const val RELOCK_AFTER_MS = 60_000L

fun canAuthenticate(context: Context) = BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

private fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Forget-it").setAllowedAuthenticators(AUTH).build()
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    }
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
}

/** Shows [content] only while unlocked. Locks on cold start and after more than a minute in the background. */
@Composable
fun BiometricGate(activity: FragmentActivity, enabled: Boolean, content: @Composable () -> Unit) {
    var locked by rememberSaveable { mutableStateOf(true) }
    var stoppedAt by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = SystemClock.elapsedRealtime() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) locked = true
    }
    if (!enabled || !locked) { content(); return }

    LaunchedEffect(Unit) { authenticate(activity) { locked = false } }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Forget-it is locked", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { authenticate(activity) { locked = false } }) { Text("Unlock") }
    }
}
