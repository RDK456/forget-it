package app.forgetit.ui.bills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forgetit.AppContainer
import app.forgetit.data.OwnerType
import app.forgetit.data.SaveResult
import app.forgetit.domain.Bill
import app.forgetit.domain.BillType
import app.forgetit.domain.Cycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BillForm(
    val name: String = "",
    val type: BillType = BillType.ELECTRICITY,
    val currency: String = "USD",
    val cycle: Cycle = Cycle.MONTHLY,
    val customDays: String = "30",
    val anchor: LocalDate = LocalDate.now().plusDays(7),
    val remindDays: Int = 3,
    val extraRemind: List<Int> = emptyList(),
    val notes: String = "",
    val active: Boolean = true,
)

class BillEditViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var form by mutableStateOf(BillForm())
        private set
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var savedId by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            if (id == 0L) c.photos.deleteAll(OwnerType.BILL, 0)
            val b = if (id != 0L) c.bills.getBill(id) else null
            form = if (b != null) {
                BillForm(b.name, b.type, b.currency, b.cycle, (b.customDays ?: 30).toString(), b.anchorDate, b.remindDaysBefore, b.extraRemindDays, b.notes, b.active)
            } else BillForm(currency = c.settings.flow.first().defaultCurrency, anchor = LocalDate.now(c.clock).plusDays(7))
        }
    }

    fun update(block: (BillForm) -> BillForm) { form = block(form) }

    fun save() {
        val f = form
        val days = f.customDays.trim().toIntOrNull()
        if (f.cycle == Cycle.CUSTOM_DAYS && days == null) { errors = mapOf("customDays" to "Enter a number of days"); return }
        val bill = Bill(
            id = id, name = f.name, type = f.type, currency = f.currency.trim().uppercase(), cycle = f.cycle,
            customDays = if (f.cycle == Cycle.CUSTOM_DAYS) days else null, anchorDate = f.anchor, remindDaysBefore = f.remindDays,
            extraRemindDays = f.extraRemind, notes = f.notes, active = f.active,
        )
        viewModelScope.launch {
            when (val r = c.bills.save(bill)) {
                is SaveResult.Saved -> {
                    if (id == 0L) c.photos.reassign(OwnerType.BILL, 0, r.id)
                    errors = emptyMap(); savedId = r.id
                }
                is SaveResult.Invalid -> errors = r.errors.associate { it.field to it.message }
            }
        }
    }
}
