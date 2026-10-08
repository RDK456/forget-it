package app.forgetit.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.forgetit.ForgetItApp
import app.forgetit.MainActivity
import app.forgetit.domain.Money
import app.forgetit.domain.Renewal
import app.forgetit.domain.computeTotals
import app.forgetit.ui.relativeDay
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Home-screen widget: monthly outgo and the next three charges. Amounts are hidden while the app lock is on. */
class ForgetItWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as ForgetItApp).container
        val today = LocalDate.now(c.clock)
        val settings = c.settings.flow.first()
        val subs = c.subscriptions.getAll()
        val totals = computeTotals(subs, today, settings.defaultCurrency, settings.rates.mapValues { it.value.value })
        val hide = settings.biometricLock
        val rows = subs.filter { it.active }.map { it to Renewal.next(it, today) }.sortedBy { it.second }.take(3)

        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)
                        .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java))),
                ) {
                    Text("Monthly outgo", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    Text(
                        if (hide) "Locked" else Money.format(totals.monthlyMinor, settings.defaultCurrency),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    if (rows.isEmpty()) {
                        Text("Nothing scheduled", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                    rows.forEach { (s, next) ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(s.name, GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
                            val right = relativeDay(next, today) + if (hide) "" else "  " + Money.format(s.amountMinor, s.currency)
                            Text(right, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
