package app.forgetit.ui.insights

import app.forgetit.ui.AppIcons
import app.forgetit.ui.EmptyState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.forgetit.ui.theme.domainColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedCard
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
import app.forgetit.domain.CategorySlice
import app.forgetit.domain.computeInsights
import app.forgetit.domain.loanMonthlyOutgo
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import java.time.LocalDate


@Composable
fun InsightsScreen(vm: MainViewModel, today: LocalDate, onBack: () -> Unit) {
    val subs by vm.subs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cur = settings.defaultCurrency
    val loans by vm.loans.collectAsStateWithLifecycle()
    val loanPay by vm.loanPayments.collectAsStateWithLifecycle()
    val loanAdj by vm.loanAdjustments.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val billEntries by vm.billEntries.collectAsStateWithLifecycle()
    val rates = settings.rates.mapValues { it.value.value }
    val emi = loanMonthlyOutgo(loans, loanAdj, loanPay, today, cur, rates)
    val billOut = app.forgetit.domain.billMonthlyOutgo(bills, billEntries, cur, rates)
    val base = computeInsights(subs, today, cur, rates, listOf(CategorySlice("Loans (EMI)", emi.monthlyMinor), CategorySlice("Bills", billOut.monthlyMinor)))
    val ins = base.copy(excluded = base.excluded + emi.excluded + billOut.excluded)

    ScreenScaffold("Insights", onBack) { pad ->
        ListScreen(pad) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
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
            item { SavingsSpotlight(ins.top, cur) }
            if (ins.slices.isEmpty()) item { EmptyState(AppIcons.Insights, "Nothing to chart yet", "Add a subscription or a loan and the colours will show up here.") }
            else {
                item { Text("By category", style = MaterialTheme.typography.titleMedium) }
                item { Donut(ins.slices.map { it.monthlyMinor.toFloat() }, ins.slices.mapIndexed { i, s -> sliceColor(s.label, i) }) }
                ins.slices.forEachIndexed { i, s ->
                    item(key = "slice${s.label}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(sliceColor(s.label, i)))
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

private val PALETTE = listOf(0xFF0B8F88, 0xFFD98E04, 0xFF5560E0, 0xFFC2185B, 0xFF2E86C1, 0xFF6BA43A, 0xFF8E5BD0, 0xFF6B7C7A).map { Color(it) }

@Composable
private fun sliceColor(label: String, i: Int): Color = if (label.startsWith("Loans")) domainColors.loan else PALETTE[i % PALETTE.size]

@Composable
private fun Donut(values: List<Float>, colors: List<Color>) {
    val total = values.sum().takeIf { it > 0f } ?: return
    val t = remember { Animatable(0f) }
    LaunchedEffect(values) { t.snapTo(0f); t.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.fillMaxWidth().size(180.dp).padding(8.dp)) {
        val stroke = 36.dp.toPx()
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * v / total * t.value
            drawArc(colors[i], start, (sweep - 1f).coerceAtLeast(0.5f), false, topLeft, Size(d, d), style = Stroke(stroke))
            start += sweep
        }
    }
}
