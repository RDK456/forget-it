package app.forgetit.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.forgetit.ui.theme.Brushes
import app.forgetit.ui.theme.TrackerBrush

/** Big coloured summary at the top of a tracker screen. The tint and the faint floating icon say which tracker you are in. */
@Composable
fun GradientHeader(
    icon: ImageVector,
    label: String,
    brush: TrackerBrush,
    modifier: Modifier = Modifier,
    valueMinor: Long? = null,
    currency: String = "",
    bigText: String? = null,
    supporting: String? = null,
    warning: String? = null,
) {
    val reduce = rememberReduceMotion()
    val float by rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "floatY",
    )
    val lift = if (reduce) 0f else float
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(brush.from, brush.to)))) {
        Icon(
            icon, null,
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(112.dp)
                .graphicsLayer { rotationZ = -10f + 6f * lift; translationY = -6f * lift; alpha = 0.2f },
            tint = brush.on,
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = brush.on.copy(alpha = 0.9f))
            if (valueMinor != null) AnimatedMoney(valueMinor, currency, MaterialTheme.typography.headlineMedium, brush.on)
            else if (bigText != null) Text(bigText, style = MaterialTheme.typography.headlineMedium, color = brush.on)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = brush.on.copy(alpha = 0.9f)) }
            warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = brush.on, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

/** Round gradient button with a bouncy press and a small buzz, for the actions people reach for most. */
@Composable
fun QuickTile(icon: ImageVector, label: String, brush: TrackerBrush, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "tile",
    )
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(brush.from, brush.to))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(28.dp), tint = brush.on) }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** The row of shortcuts on Overview: scan anything, then one add button per tracker. */
@Composable
fun QuickActions(onAction: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickTile(AppIcons.Camera, "Scan", Brushes.scan, Modifier.weight(1f)) { onAction("scan") }
        QuickTile(AppIcons.Subscriptions, "Sub", Brushes.subscription, Modifier.weight(1f)) { onAction("edit/0") }
        QuickTile(AppIcons.Loans, "EMI", Brushes.loan, Modifier.weight(1f)) { onAction("loanedit/0") }
        QuickTile(AppIcons.Bills, "Bill", Brushes.bill, Modifier.weight(1f)) { onAction("billedit/0") }
        QuickTile(AppIcons.Stock, "Stock", Brushes.stock, Modifier.weight(1f)) { onAction("stockedit/0") }
    }
}
