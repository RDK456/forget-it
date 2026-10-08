package app.forgetit.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forgetit.domain.Money
import app.forgetit.domain.Subscription
import app.forgetit.ui.AnimatedMoney
import app.forgetit.ui.FunIcons
import app.forgetit.ui.bob

/** A friendly nudge: what the priciest subscriptions cost over a year, so cancelling one feels like a real option. */
@Composable
fun SavingsSpotlight(top: List<Pair<Subscription, Long>>, currency: String, modifier: Modifier = Modifier) {
    if (top.isEmpty()) return
    val (name, monthly) = top.first().let { it.first.name to it.second }
    val allYearly = top.sumOf { it.second } * 12
    OutlinedCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FunIcons.Piggy, null, Modifier.size(40.dp).bob(5f), tint = MaterialTheme.colorScheme.tertiary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Savings spotlight", style = MaterialTheme.typography.labelLarge)
                Text("$name costs ${Money.format(monthly * 12, currency)} a year.", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (top.size > 1) "Cancel your top ${top.size} and keep: " else "Cancel it and keep: ", style = MaterialTheme.typography.bodyMedium)
                    AnimatedMoney(allYearly, currency, MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                Text("Worth a look before the next renewal?", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
