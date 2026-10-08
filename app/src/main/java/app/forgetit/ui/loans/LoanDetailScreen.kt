package app.forgetit.ui.loans

import app.forgetit.ui.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.AdjustmentKind
import app.forgetit.domain.Amortization
import app.forgetit.domain.LoanAdjustment
import app.forgetit.domain.Money
import app.forgetit.domain.onTimeStreak
import app.forgetit.ui.FunIcons
import app.forgetit.ui.pulse
import androidx.compose.foundation.layout.size
import app.forgetit.domain.RowStatus
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.relativeDay
import java.time.LocalDate

@Composable
fun LoanDetailScreen(vm: MainViewModel, loanId: Long, today: LocalDate, onBack: () -> Unit, onEdit: () -> Unit, photos: @Composable () -> Unit) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val pay by vm.loanPayments.collectAsStateWithLifecycle()
    val adj by vm.loanAdjustments.collectAsStateWithLifecycle()
    val loan = loans.firstOrNull { it.id == loanId }
    var dialog by remember { mutableStateOf<String?>(null) }
    if (loan == null) { ScreenScaffold("Loan", onBack) {}; return }

    val myAdj = adj.filter { it.loanId == loanId }
    val s = Amortization.build(loan, myAdj, pay.filter { it.loanId == loanId }, today)
    val cur = loan.currency
    fun money(v: Long) = Money.format(v, cur)

    ScreenScaffold(loan.name, onBack) { pad ->
        ListScreen(pad) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Outstanding", style = MaterialTheme.typography.labelLarge)
                        Text(money(s.outstandingMinor), style = MaterialTheme.typography.headlineMedium)
                        Text("EMI ${money(s.emiMinor)} per month")
                        Text("Interest still to pay: ${money(s.interestRemainingMinor)}")
                        s.payoffDate?.let { Text("Debt-free on $it") }
                        if (s.completed) Text("All installments paid", color = MaterialTheme.colorScheme.primary)
                        val streak = onTimeStreak(s.rows, pay.filter { it.loanId == loanId })
                        if (streak >= 2) Row(Modifier.padding(top = 4.dp).pulse(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(FunIcons.Streak, null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color(0xFFD98E04))
                            Text("  $streak on-time payments in a row", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            val next = s.nextDue
            if (next != null) item {
                Button({ vm.markPaid(loanId, next.no, next.paymentMinor) }, Modifier.fillMaxWidth()) {
                    Text("Mark EMI ${next.no} paid (${money(next.paymentMinor)})")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ dialog = "prepay" }) { Text("Prepay") }
                    OutlinedButton({ dialog = "balance" }) { Text("Set balance") }
                    OutlinedButton(onEdit) { Text("Edit") }
                }
            }
            item { Text("Figures are calculated from the terms you entered and can differ slightly from your lender statement. Use Set balance to re-sync.", style = MaterialTheme.typography.bodySmall) }
            item { photos() }
            if (myAdj.isNotEmpty()) {
                item { Text("Prepayments and balance changes", style = MaterialTheme.typography.titleMedium) }
                items(myAdj.sortedBy { it.date }, key = { "adj${it.id}" }) { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val what = when (a.kind) {
                            AdjustmentKind.PREPAYMENT_REDUCE_TENURE -> "Prepaid, shorter loan"
                            AdjustmentKind.PREPAYMENT_REDUCE_EMI -> "Prepaid, smaller EMI"
                            AdjustmentKind.BALANCE_RESET -> "Balance set to"
                        }
                        Text("${a.date}  $what ${money(a.amountMinor)}", Modifier.weight(1f))
                        IconButton({ vm.deleteAdjustment(a.id) }) { Icon(AppIcons.Delete, "Remove") }
                    }
                }
            }
            item { Text("Installments (tap to mark paid or unpaid)", style = MaterialTheme.typography.titleMedium) }
            items(s.rows, key = { "row${it.no}" }) { r ->
                val color = when (r.status) {
                    RowStatus.OVERDUE -> MaterialTheme.colorScheme.error
                    RowStatus.PAID -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    Modifier.fillMaxWidth().clickable { if (r.status == RowStatus.PAID) vm.unmarkPaid(loanId, r.no) else vm.markPaid(loanId, r.no, r.paymentMinor) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.no}.  ${money(r.paymentMinor)}  ${if (r.status == RowStatus.PAID) "paid" else relativeDay(r.dueDate, today)}", color = color)
                        Text("${r.dueDate} - interest ${money(r.interestMinor)}, principal ${money(r.principalMinor)}", style = MaterialTheme.typography.bodySmall, color = color)
                    }
                    if (r.status == RowStatus.PAID) Icon(AppIcons.Check, "Paid", tint = MaterialTheme.colorScheme.primary)
                }
            }
            item { OutlinedButton({ dialog = "delete" }, Modifier.fillMaxWidth()) { Text("Delete this loan") } }
        }
    }
    when (dialog) {
        "prepay" -> AmountDialog("Prepayment", "How much did you prepay today?", cur, true, { amt, kind ->
            vm.addAdjustment(LoanAdjustment(loanId = loanId, date = today, kind = kind, amountMinor = amt)); dialog = null
        }, { dialog = null })
        "balance" -> AmountDialog("Set outstanding balance", "Enter the outstanding principal from your lender statement as of today.", cur, false, { amt, _ ->
            vm.addAdjustment(LoanAdjustment(loanId = loanId, date = today, kind = AdjustmentKind.BALANCE_RESET, amountMinor = amt)); dialog = null
        }, { dialog = null })
        "delete" -> ConfirmDialog("Delete loan?", "This removes the loan, its payments and photos.", "Delete", { vm.deleteLoan(loanId); dialog = null; onBack() }, { dialog = null })
    }
}
