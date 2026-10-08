package app.forgetit

import android.content.Context
import java.time.Clock

class AppContainer(val context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
}
