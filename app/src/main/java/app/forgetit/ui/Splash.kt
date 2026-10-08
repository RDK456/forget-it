package app.forgetit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** On launch the app mark draws itself (a ring, then the check), then dissolves into the app. */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val reduce = rememberReduceMotion()
    val draw = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (reduce) { onFinished(); return@LaunchedEffect }
        draw.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        delay(120)
        fade.animateTo(0f, tween(380))
        onFinished()
    }
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().alpha(fade.value).background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(132.dp).graphicsLayer { val pop = 1f + 0.06f * (1f - kotlin.math.abs(draw.value * 2f - 1f)); scaleX = pop; scaleY = pop }) {
            val s = size.minDimension
            val ring = (draw.value / 0.62f).coerceIn(0f, 1f)
            drawArc(primary.copy(alpha = 0.18f), 0f, 360f, false, Offset(s * 0.08f, s * 0.08f), Size(s * 0.84f, s * 0.84f), style = Stroke(s * 0.07f))
            drawArc(primary, -90f, 360f * ring, false, Offset(s * 0.08f, s * 0.08f), Size(s * 0.84f, s * 0.84f), style = Stroke(s * 0.07f, cap = StrokeCap.Round))
            val tick = ((draw.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
            if (tick > 0f) {
                val full = Path().apply { moveTo(s * 0.3f, s * 0.53f); lineTo(s * 0.45f, s * 0.68f); lineTo(s * 0.72f, s * 0.37f) }
                val m = PathMeasure().apply { setPath(full, false) }
                val part = Path()
                m.getSegment(0f, m.length * tick, part, true)
                drawPath(part, primary, style = Stroke(s * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(
            "Forget-it", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
            modifier = Modifier.alpha(((draw.value - 0.4f) / 0.6f).coerceIn(0f, 1f)),
        )
    }
}
