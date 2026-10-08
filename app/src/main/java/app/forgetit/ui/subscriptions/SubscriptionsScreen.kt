package app.forgetit.ui.subscriptions

import app.forgetit.ui.EmptyState
import app.forgetit.ui.enterStagger
import app.forgetit.ui.categoryColor
import app.forgetit.ui.categoryIcon
import app.forgetit.ui.loanIcon
import app.forgetit.ui.pressScale
import app.forgetit.ui.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.CATEGORIES
import app.forgetit.domain.Money
import app.forgetit.domain.Renewal
import app.forgetit.domain.Subscription
import app.forgetit.domain.computeTotals
import app.forgetit.domain.isTrialActive
import app.forgetit.ui.Avatar
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.TotalsCard
import app.forgetit.ui.cycleLabel
import app.forgetit.ui.relativeDay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(vm: MainViewModel, today: LocalDate, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val subs by vm.subs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(setOf<Long>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val visible = subs.filter { it.id !in pending }
    val totals = computeTotals(visible, today, settings.defaultCurrency, settings.rates.mapValues { it.value.value })
    val shown = visible
        .filter { (category == null || it.category == category) && it.name.contains(query.trim(), ignoreCase = true) }
        .sortedBy { if (it.active) Renewal.next(it, today) else LocalDate.MAX }

    fun delete(s: Subscription) {
        pending = pending + s.id
        scope.launch {
            val r = snackbar.showSnackbar("Deleted ${s.name}", "Undo", duration = SnackbarDuration.Short)
            if (r != SnackbarResult.ActionPerformed) vm.deleteSubscription(s.id).join()
            pending = pending - s.id
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Subscriptions") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(AppIcons.Add, contentDescription = "Add subscription") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        ListScreen(pad) {
            item { TotalsCard("Subscriptions per month", totals, settings.defaultCurrency) }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Search") }, leadingIcon = { Icon(AppIcons.Search, contentDescription = null) }, shape = MaterialTheme.shapes.large,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val used = CATEGORIES.filter { c -> subs.any { it.category == c } }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(category == null, { category = null }, { Text("All") }) }
                    items(used) { c -> FilterChip(category == c, { category = if (category == c) null else c }, { Text(c) }) }
                }
            }
            if (subs.isEmpty()) item { EmptyState(AppIcons.Subscriptions, "Nothing to forget yet", "Tap + and add your first subscription. Forget-it keeps the renewal date for you.") }
            else if (shown.isEmpty()) item {
                Text(
                    if (subs.isEmpty()) "Nothing here yet" else "No matches.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(shown, key = { it.id }) { s ->
                val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                    if (v == SwipeToDismissBoxValue.EndToStart) { delete(s); true } else false
                })
                SwipeToDismissBox(
                    state = state, modifier = Modifier.animateItem().enterStagger(shown.indexOf(s), s.id), enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().then(if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer) else Modifier).padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(AppIcons.Delete, contentDescription = "Delete")
                        }
                    },
                ) { SubscriptionRow(s, today, covers["SUBSCRIPTION:${s.id}"]) { onOpen(s.id) } }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(s: Subscription, today: LocalDate, cover: java.io.File?, onClick: () -> Unit) {
    val next = Renewal.next(s, today)
    OutlinedCard(Modifier.fillMaxWidth().pressScale(onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.name, cover, icon = categoryIcon(s.category), tint = categoryColor(s.category))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                Text("${s.cycleLabel()} - ${s.category}", style = MaterialTheme.typography.bodySmall)
                if (s.isTrialActive(today)) {
                    Text("Free trial", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(s.amountMinor, s.currency), style = MaterialTheme.typography.titleMedium)
                Text(if (s.active) relativeDay(next, today) else "Paused", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
