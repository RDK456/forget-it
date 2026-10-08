package app.forgetit.ui.bills

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.forgetit.domain.BillEntry
import app.forgetit.domain.BillMath
import app.forgetit.ui.rememberReduceMotion
import app.forgetit.ui.theme.domainColors
import java.time.format.TextStyle
import java.util.Locale

/** The last six bills as bars that grow in one after another; bills well above the earlier ones are drawn in the alert colour. */
@Composable
fun BillBars(entries: List<BillEntry>, modifier: Modifier = Modifier) {
    val recent = entries.sortedBy { it.dueDate }.takeLast(6)
    if (recent.isEmpty()) return
    val max = recent.maxOf { it.amountMinor }.coerceAtLeast(1L)
    val reduce = rememberReduceMotion()
    val progress = remember(recent.size) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(recent.size) { if (!reduce) progress.animateTo(1f, tween(900, easing = LinearEasing)) }
    val normal = domainColors.bill
    val alert = MaterialTheme.colorScheme.error
    val high = recent.map { e -> BillMath.isHigh(e.amountMinor, entries.filter { it.dueDate.isBefore(e.dueDate) }) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = recent.size
            val slot = size.width / n
            val bar = slot * 0.56f
            recent.forEachIndexed { i, e ->
                val grow = ((progress.value * (n + 1)) - i).coerceIn(0f, 1f)
                val h = size.height * (e.amountMinor.toFloat() / max) * grow
                drawRoundRect(
                    color = if (high[i]) alert else normal, topLeft = Offset(i * slot + (slot - bar) / 2, size.height - h),
                    size = Size(bar, h), cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            recent.forEach { e ->
                Text(
                    e.dueDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
