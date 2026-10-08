package app.forgetit.ui.loans

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forgetit.AppContainer
import app.forgetit.data.OwnerType
import app.forgetit.data.SaveResult
import app.forgetit.domain.Loan
import app.forgetit.domain.LoanType
import app.forgetit.domain.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class LoanForm(
    val name: String = "",
    val lender: String = "",
    val type: LoanType = LoanType.PERSONAL,
    val principal: String = "",
    val currency: String = "USD",
    val rate: String = "",
    val tenure: String = "12",
    val firstEmi: LocalDate = LocalDate.now().plusMonths(1),
    val emiOverride: String = "",
    val remindDays: Int = 2,
    val notes: String = "",
    val active: Boolean = true,
)

class LoanEditViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var form by mutableStateOf(LoanForm())
        private set
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var savedId by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            if (id == 0L) c.photos.deleteAll(OwnerType.LOAN, 0)
            val l = if (id != 0L) c.loans.getLoan(id) else null
            form = if (l != null) {
                LoanForm(
                    l.name, l.lender, l.type, Money.toPlain(l.principalMinor, l.currency), l.currency,
                    l.annualRatePercent.toPlainString(), l.tenureMonths.toString(), l.firstEmiDate,
                    l.emiOverrideMinor?.let { Money.toPlain(it, l.currency) }.orEmpty(), l.remindDaysBefore, l.notes, l.active,
                )
            } else {
                val today = LocalDate.now(c.clock)
                LoanForm(currency = c.settings.flow.first().defaultCurrency, firstEmi = today.plusMonths(1))
            }
        }
    }

    fun update(block: (LoanForm) -> LoanForm) { form = block(form) }

    /** Parses the form into a Loan, filling [errors] for text fields that do not parse. */
    private fun toLoan(): Loan? {
        val f = form
        val cur = f.currency.trim().uppercase()
        val local = mutableMapOf<String, String>()
        val principal = Money.parseMinor(f.principal, cur)
        if (principal == null) local["principal"] = "Enter an amount like 5000.00"
        val rate = f.rate.trim().replace(',', '.').toBigDecimalOrNull()
        if (rate == null) local["rate"] = "Enter a rate like 8.5 (use 0 for no interest)"
        val tenure = f.tenure.trim().toIntOrNull()
        if (tenure == null) local["tenure"] = "Enter the number of months"
        val override = if (f.emiOverride.isBlank()) null else Money.parseMinor(f.emiOverride, cur)
        if (f.emiOverride.isNotBlank() && override == null) local["emi"] = "Enter an amount like 250.00"
        errors = local
        if (local.isNotEmpty()) return null
        return Loan(
            id = id, name = f.name, lender = f.lender, type = f.type, principalMinor = principal!!, currency = cur,
            annualRatePercent = rate!!, tenureMonths = tenure!!, firstEmiDate = f.firstEmi, emiOverrideMinor = override,
            remindDaysBefore = f.remindDays, notes = f.notes, active = f.active,
        )
    }

    /** Live EMI estimate for the form, or null while the inputs are incomplete. */
    fun estimate(): Pair<Long, String>? {
        val f = form
        val cur = f.currency.trim().uppercase()
        val p = Money.parseMinor(f.principal, cur) ?: return null
        val r = f.rate.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        val n = f.tenure.trim().toIntOrNull()?.takeIf { it in 1..600 } ?: return null
        if (p <= 0 || r < BigDecimal.ZERO || r > BigDecimal(100)) return null
        val loan = Loan(name = "x", principalMinor = p, currency = cur, annualRatePercent = r, tenureMonths = n, firstEmiDate = f.firstEmi)
        return app.forgetit.domain.Amortization.baseEmi(loan) to cur
    }

    fun save() {
        val loan = toLoan() ?: return
        viewModelScope.launch {
            when (val r = c.loans.save(loan)) {
                is SaveResult.Saved -> {
                    if (id == 0L) c.photos.reassign(OwnerType.LOAN, 0, r.id)
                    savedId = r.id
                }
                is SaveResult.Invalid -> errors = r.errors.associate { it.field to it.message }
            }
        }
    }
}
