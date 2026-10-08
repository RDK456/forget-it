package app.forgetit.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Money
import app.forgetit.domain.computeInsights
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import java.time.LocalDate

private fun sliceColor(i: Int) = Color.hsv((i * 47 % 360).toFloat(), 0.55f, 0.85f)

@Composable
fun InsightsScreen(vm: MainViewModel, today: LocalDate, onBack: () -> Unit) {
    val subs by vm.subs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cur = settings.defaultCurrency
    val ins = computeInsights(subs, today, cur, settings.rates.mapValues { it.value.value })

    ScreenScaffold("Insights", onBack) { pad ->
        ListScreen(pad) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cost", style = MaterialTheme.typography.titleMedium)
                        Text("${Money.format(ins.perDayMinor, cur)} per day")
                        Text("${Money.format(ins.monthlyMinor, cur)} per month")
                        Text("${Money.format(ins.yearlyMinor, cur)} per year")
                        Text("${ins.activeCount} active, ${ins.trialCount} on free trial", style = MaterialTheme.typography.bodySmall)
                        if (ins.excluded > 0) Text("${ins.excluded} excluded (no exchange rate)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (ins.slices.isEmpty()) item { Text("Add subscriptions to see where the money goes.") }
            else {
                item { Text("By category", style = MaterialTheme.typography.titleMedium) }
                item { Donut(ins.slices.map { it.monthlyMinor.toFloat() }) }
                ins.slices.forEachIndexed { i, s ->
                    item(key = "slice${s.label}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(sliceColor(i)))
                            Text(s.label, Modifier.weight(1f).padding(start = 8.dp))
                            Text(Money.format(s.monthlyMinor, cur))
                        }
                    }
                }
                item { Text("Most expensive", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                ins.top.forEach { (s, m) ->
                    item(key = "top${s.id}") {
                        Row { Text(s.name, Modifier.weight(1f)); Text("${Money.format(m, cur)} / month") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(values: List<Float>) {
    val total = values.sum().takeIf { it > 0f } ?: return
    Canvas(Modifier.fillMaxWidth().size(180.dp).padding(8.dp)) {
        val stroke = 36.dp.toPx()
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * v / total
            drawArc(sliceColor(i), start, (sweep - 1f).coerceAtLeast(0.5f), false, topLeft, Size(d, d), style = Stroke(stroke))
            start += sweep
        }
    }
}
