package app.forgetit.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.forgetit.ui.AppIcons
import app.forgetit.ui.theme.TrackerBrush

/** One category of settings: a coloured icon, a one-line summary of what is set, and the controls when it is opened. */
@Composable
fun SettingsGroup(
    icon: ImageVector,
    title: String,
    summary: String,
    brush: TrackerBrush,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    OutlinedCard(modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(brush.from, brush.to))),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(22.dp), tint = brush.on) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(AppIcons.Next, if (open) "Collapse" else "Expand", Modifier.graphicsLayer { rotationZ = turn })
            }
            if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
