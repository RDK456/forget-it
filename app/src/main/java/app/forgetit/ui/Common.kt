package app.forgetit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forgetit.domain.Cycle
import app.forgetit.domain.Money
import app.forgetit.domain.Subscription
import app.forgetit.domain.Totals
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days in 2..14 -> "In $days days"
        days in -14..-2 -> "${-days} days ago"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

fun Subscription.cycleLabel(): String = when (cycle) {
    Cycle.WEEKLY -> "Weekly"
    Cycle.MONTHLY -> "Monthly"
    Cycle.QUARTERLY -> "Quarterly"
    Cycle.YEARLY -> "Yearly"
    Cycle.CUSTOM_DAYS -> "Every $customDays days"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(title: String, onBack: (() -> Unit)?, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.Back, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        content = content,
    )
}

/** The standard scrolling list. With [onRefresh] a pull down on the list runs it and shows the spinner for at least a moment. */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun ListScreen(
    pad: PaddingValues,
    onRefresh: (suspend () -> Unit)? = null,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    if (onRefresh == null) {
        LazyColumn(Modifier.padding(pad), state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        return
    }
    var refreshing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                val started = System.currentTimeMillis()
                try { onRefresh() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                val left = 700 - (System.currentTimeMillis() - started)
                if (left > 0) kotlinx.coroutines.delay(left)
                refreshing = false
            }
        },
        modifier = Modifier.padding(pad),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun TotalsCard(label: String, totals: Totals, currency: String, modifier: Modifier = Modifier) {
    GradientHeader(
        AppIcons.Subscriptions, label, app.forgetit.ui.theme.Brushes.subscription, modifier, valueMinor = totals.monthlyMinor, currency = currency,
        supporting = "${Money.format(totals.yearlyMinor, currency)} per year",
        warning = if (totals.excluded > 0) "${totals.excluded} excluded - set exchange rates in Settings" else null,
    )
}

/** Cards that dip slightly under the finger, so a tap feels acknowledged before the screen changes. */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = 700f), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

/** Like [relativeDay] but lowercase for use inside a sentence; month names in far dates keep their capital. */
fun relativeDayLower(date: LocalDate, today: LocalDate): String {
    val s = relativeDay(date, today)
    return if (s.startsWith("Today") || s.startsWith("Tomorrow") || s.startsWith("Yesterday") || s.startsWith("In ") || s.endsWith(" ago")) s.replaceFirstChar { it.lowercase() } else s
}
