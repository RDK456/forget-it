package app.forgetit.widget

import android.content.Context
import android.content.Intent
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
import app.forgetit.domain.Amortization
import app.forgetit.domain.Money
import app.forgetit.domain.Renewal
import app.forgetit.domain.computeTotals
import app.forgetit.domain.loanMonthlyOutgo
import app.forgetit.ui.relativeDay
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private data class Row3(val name: String, val date: LocalDate, val amount: String)

/** Home-screen widget: monthly outgo (subscriptions plus EMIs) and the next three charges. Amounts hide while the app lock is on. */
class ForgetItWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as ForgetItApp).container
        val today = LocalDate.now(c.clock)
        val settings = c.settings.flow.first()
        val rates = settings.rates.mapValues { it.value.value }
        val subs = c.subscriptions.getAll()
        val loans = c.loans.getLoans()
        val adj = c.loans.getAdjustments()
        val pay = c.loans.getPayments()
        val bills = c.bills.getBills()
        val billEntries = c.bills.getEntries()
        val subTotals = computeTotals(subs, today, settings.defaultCurrency, rates)
        val emi = loanMonthlyOutgo(loans, adj, pay, today, settings.defaultCurrency, rates)
        val billOut = app.forgetit.domain.billMonthlyOutgo(bills, billEntries, settings.defaultCurrency, rates)
        val monthly = subTotals.monthlyMinor + emi.monthlyMinor + billOut.monthlyMinor
        val hide = settings.biometricLock

        val subRows = subs.filter { it.active }.map { Row3(it.name, Renewal.next(it, today), Money.format(it.amountMinor, it.currency)) }
        val emiRows = loans.filter { it.active }.mapNotNull { l ->
            val row = Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today).nextDue ?: return@mapNotNull null
            Row3("${l.name} EMI", row.dueDate, Money.format(row.paymentMinor, l.currency))
        }
        val billRows = bills.filter { it.active }.mapNotNull { b ->
            val mine = billEntries.filter { it.billId == b.id }
            val due = app.forgetit.domain.BillMath.pendingDue(b, mine, today) ?: return@mapNotNull null
            Row3(b.name, due, app.forgetit.domain.BillMath.average(mine)?.let { "~" + Money.format(it, b.currency) } ?: "")
        }
        val rows = (subRows + emiRows + billRows).sortedBy { it.date }.take(3)
        val open = actionStartActivity(Intent(context, MainActivity::class.java))

        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp).clickable(open)) {
                    Text("Going out this month", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    Text(
                        if (hide) "Locked" else Money.format(monthly, settings.defaultCurrency),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    if (rows.isEmpty()) Text("Nothing scheduled", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    rows.forEach { r ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(r.name, GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
                            Text(
                                relativeDay(r.date, today) + if (hide) "" else "  " + r.amount,
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
