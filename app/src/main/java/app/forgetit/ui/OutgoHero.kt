package app.forgetit.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.forgetit.domain.Money
import app.forgetit.ui.theme.domainColors

/** The one memorable element: this month's outgo as a big number, with a bar showing subscriptions, EMIs and bills. */
@Composable
fun OutgoHero(subsMinor: Long, emiMinor: Long, billsMinor: Long, yearlyMinor: Long, currency: String, excluded: Int, modifier: Modifier = Modifier) {
    val total = subsMinor + emiMinor + billsMinor
    val spec = tween<Float>(650, easing = FastOutSlowInEasing)
    val shown by animateFloatAsState(total.toFloat(), spec, label = "outgo")
    val subShare by animateFloatAsState(if (total > 0) subsMinor.toFloat() / total else 0f, spec, label = "sub")
    val emiShare by animateFloatAsState(if (total > 0) emiMinor.toFloat() / total else 0f, spec, label = "emi")
    val billShare by animateFloatAsState(if (total > 0) billsMinor.toFloat() / total else 0f, spec, label = "bill")
    val dc = domainColors

    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Going out this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.format(shown.toLong(), currency),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text("${Money.format(yearlyMinor, currency)} over a year", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                if (total > 0) Row(Modifier.fillMaxHeight()) {
                    if (subShare > 0.001f) Box(Modifier.weight(subShare).fillMaxHeight().background(dc.subscription))
                    if (emiShare > 0.001f) Box(Modifier.weight(emiShare).fillMaxHeight().background(dc.loan))
                    if (billShare > 0.001f) Box(Modifier.weight(billShare).fillMaxHeight().background(dc.bill))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                Legend(dc.subscription, "Subscriptions", Money.format(subsMinor, currency))
                Legend(dc.loan, "EMIs", Money.format(emiMinor, currency))
                if (billsMinor > 0) Legend(dc.bill, "Bills", Money.format(billsMinor, currency))
            }
            if (excluded > 0) {
                Text("$excluded item(s) left out - add exchange rates in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
