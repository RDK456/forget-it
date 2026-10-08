package app.forgetit

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.data.ThemeMode
import app.forgetit.ui.ForgetItRoot
import app.forgetit.ui.lock.BiometricGate
import app.forgetit.ui.theme.ForgetItTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ForgetItApp).container
        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            val dark = when (s.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(s.biometricLock) {
                if (s.biometricLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            ForgetItTheme(dark) {
                Surface { BiometricGate(this@MainActivity, s.biometricLock) { ForgetItRoot(container) } }
            }
        }
    }
}
