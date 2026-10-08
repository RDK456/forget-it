package app.forgetit.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.forgetit.domain.Money
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** True when the user switched system animations off; every decorative animation then stays still. */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Items slide up and fade in one after another the first time they appear; scrolling back does not replay it. */
fun Modifier.enterStagger(index: Int, key: Any = index): Modifier = composed {
    val reduce = rememberReduceMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember { Animatable(if (played || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played && !reduce) {
            delay((index.coerceAtMost(8) * 55).toLong())
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        played = true
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/** A gentle repeating heartbeat, for things that deserve attention such as a trial ending soon. */
fun Modifier.pulse(enabled: Boolean = true): Modifier = composed {
    val reduce = rememberReduceMotion()
    if (!enabled || reduce) return@composed this
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, 1.07f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Slow floating, for empty-state illustrations. */
fun Modifier.bob(amplitudeDp: Float = 6f): Modifier = composed {
    val reduce = rememberReduceMotion()
    if (reduce) return@composed this
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    this.graphicsLayer { translationY = y * amplitudeDp.dp.toPx() }
}

/** Money that counts up or down to its new value instead of jumping. */
@Composable
fun AnimatedMoney(minor: Long, currency: String, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val shown by animateFloatAsState(minor.toFloat(), if (reduce) tween(0) else tween(650, easing = FastOutSlowInEasing), label = "money")
    Text(Money.format(shown.toLong(), currency), style = style, color = color, modifier = modifier)
}

/** Overshooting scale for a selected icon. */
@Composable
fun bounceScale(selected: Boolean): Float {
    val s by animateFloatAsState(if (selected) 1.2f else 1f, spring(dampingRatio = 0.35f, stiffness = 380f), label = "bounce")
    return s
}

/** A one-shot burst of confetti; change [trigger] to fire it. Reserved for real wins such as paying an EMI. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(1f) }
    var seed by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !reduce) {
            seed = trigger
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing))
        }
    }
    if (progress.value >= 1f) return
    val palette = listOf(Color(0xFF0B8F88), Color(0xFF5560E0), Color(0xFFD98E04), Color(0xFFB3254F), Color(0xFF2E86C1), Color(0xFF6BA43A))
    Canvas(modifier.fillMaxSize()) {
        val rnd = Random(seed)
        val t = progress.value
        repeat(72) {
            val angle = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = 260f + rnd.nextFloat() * 760f
            val x = size.width / 2f + cos(angle) * speed * t
            val y = size.height * 0.32f + sin(angle) * speed * t * 0.8f + 900f * t * t
            val w = 6.dp.toPx() + rnd.nextFloat() * 6.dp.toPx()
            rotate(rnd.nextFloat() * 360f + t * 540f, Offset(x, y)) {
                drawRect(palette[rnd.nextInt(palette.size)], Offset(x - w / 2, y - w / 4), Size(w, w / 2), alpha = (1f - t * t).coerceIn(0f, 1f))
            }
        }
    }
}

/** A progress bar that fills smoothly to its value. */
@Composable
fun AnimatedProgress(target: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(target, tween(800, easing = FastOutSlowInEasing), label = "progress")
    androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = modifier)
}

/** A friendly empty state: a floating icon and a line that says what to do next. */
@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(84.dp).bob(7f).background(androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, null, Modifier.size(38.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
