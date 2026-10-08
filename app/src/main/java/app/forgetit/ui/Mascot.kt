package app.forgetit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Mood { HAPPY, ALERT, WORRIED }

/** Remy: the app mark with a face. The mood follows what is really happening in the user's money and stock. */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 76.dp) {
    val reduce = rememberReduceMotion()
    val primary = MaterialTheme.colorScheme.primary
    val light = lerp(primary, Color.White, 0.4f)
    val ink = Color(0xFF10201F)
    val curve by animateFloatAsState(
        when (mood) { Mood.HAPPY -> 1f; Mood.ALERT -> 0.2f; Mood.WORRIED -> -0.9f }, spring(dampingRatio = 0.55f), label = "mouth",
    )
    val brow by animateFloatAsState(when (mood) { Mood.HAPPY -> 0f; Mood.ALERT -> 0.3f; Mood.WORRIED -> 1f }, spring(), label = "brow")
    val eye = remember { Animatable(1f) }
    LaunchedEffect(reduce) {
        if (!reduce) while (true) {
            delay(2400 + Random.nextLong(2200))
            eye.animateTo(0.08f, tween(70)); eye.animateTo(1f, tween(120))
        }
    }
    val t = rememberInfiniteTransition(label = "idle")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val tilt by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")

    Canvas(modifier.size(size).graphicsLayer {
        if (!reduce) { translationY = bob * 3.dp.toPx(); rotationZ = tilt * 3f }
    }) {
        val s = this.size.minDimension
        drawOval(Color.Black.copy(alpha = 0.08f), Offset(s * 0.22f, s * 0.9f), Size(s * 0.56f, s * 0.07f))
        drawCircle(Brush.radialGradient(listOf(light, primary), Offset(s * 0.38f, s * 0.35f), s * 0.62f), s * 0.44f, Offset(s / 2, s * 0.5f))
        drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.5f), s * 0.07f, Offset(s * 0.26f, s * 0.58f))
        drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.5f), s * 0.07f, Offset(s * 0.74f, s * 0.58f))
        for ((i, ex) in listOf(0.36f, 0.64f).withIndex()) {
            val c = Offset(s * ex, s * 0.45f)
            val h = s * 0.19f * eye.value
            drawOval(Color.White, Offset(c.x - s * 0.075f, c.y - h / 2), Size(s * 0.15f, h))
            drawCircle(ink, s * 0.034f * eye.value.coerceAtLeast(0.3f), Offset(c.x + s * 0.008f, c.y + s * 0.01f))
            val side = if (i == 0) 1f else -1f
            drawLine(
                ink, Offset(c.x - s * 0.07f, c.y - s * (0.13f - 0.03f * brow * side)), Offset(c.x + s * 0.07f, c.y - s * (0.13f + 0.03f * brow * side)),
                strokeWidth = s * 0.026f, cap = StrokeCap.Round,
            )
        }
        val y0 = s * 0.62f
        val mouth = Path().apply { moveTo(s * 0.4f, y0); quadraticTo(s * 0.5f, y0 + s * 0.13f * curve, s * 0.6f, y0) }
        drawPath(mouth, ink, style = Stroke(s * 0.035f, cap = StrokeCap.Round))
        val sweat = (-curve).coerceIn(0f, 1f)
        if (sweat > 0.05f) drawCircle(Color(0xFF8FD3F4).copy(alpha = sweat), s * 0.045f, Offset(s * 0.8f, s * 0.3f))
    }
}

/** Remy with a speech bubble; the message cross-fades when it changes. */
@Composable
fun RemyHeader(mood: Mood, greeting: String, message: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mascot(mood)
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(greeting, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                AnimatedContent(
                    message, label = "remy-message",
                    transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }) togetherWith fadeOut(tween(120)) },
                ) { m -> Text(m, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
}
