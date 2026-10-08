package app.forgetit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import app.forgetit.ui.ForgetItRoot
import app.forgetit.ui.theme.ForgetItTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ForgetItApp).container
        setContent { ForgetItTheme { Surface { ForgetItRoot(container) } } }
    }
}
