package app.forgetit.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forgetit.AppContainer
import app.forgetit.data.OwnerType
import app.forgetit.data.SaveResult
import app.forgetit.domain.Cycle
import app.forgetit.domain.Money
import app.forgetit.domain.Preset
import app.forgetit.domain.Subscription
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SubForm(
    val name: String = "",
    val amount: String = "",
    val currency: String = "USD",
    val cycle: Cycle = Cycle.MONTHLY,
    val customDays: String = "30",
    val startDate: LocalDate = LocalDate.now(),
    val category: String = "Other",
    val notes: String = "",
    val cancelUrl: String = "",
    val paymentMethod: String = "",
    val isTrial: Boolean = false,
    val trialEndsAt: LocalDate = LocalDate.now().plusDays(7),
    val remindDays: Int = 2,
    val extraRemind: List<Int> = emptyList(),
    val active: Boolean = true,
    val presetKey: String? = null,
)

class EditViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var form by mutableStateOf(SubForm())
        private set
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var savedId by mutableStateOf<Long?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            if (id == 0L) c.photos.deleteAll(OwnerType.SUBSCRIPTION, 0)
            val today = LocalDate.now(c.clock)
            val existing = if (id != 0L) c.subscriptions.get(id) else null
            form = if (existing != null) {
                SubForm(
                    name = existing.name, amount = Money.toPlain(existing.amountMinor, existing.currency),
                    currency = existing.currency, cycle = existing.cycle, customDays = (existing.customDays ?: 30).toString(),
                    startDate = existing.startDate, category = existing.category, notes = existing.notes,
                    cancelUrl = existing.cancelUrl.orEmpty(), paymentMethod = existing.paymentMethod,
                    isTrial = existing.isTrial, trialEndsAt = existing.trialEndsAt ?: today.plusDays(7),
                    remindDays = existing.remindDaysBefore, extraRemind = existing.extraRemindDays, active = existing.active, presetKey = existing.presetKey,
                )
            } else {
                SubForm(currency = c.settings.flow.first().defaultCurrency, startDate = today, trialEndsAt = today.plusDays(7))
            }
            loaded = true
        }
    }

    fun update(block: (SubForm) -> SubForm) { form = block(form) }

    fun applyPreset(p: Preset) = update {
        it.copy(name = p.name, category = p.category, cancelUrl = p.accountUrl.orEmpty(), cycle = p.cycle, presetKey = p.key)
    }

    fun setTrial(on: Boolean) = update { it.copy(isTrial = on, remindDays = if (on) 3 else 2) }

    fun save() {
        val f = form
        val currency = f.currency.trim().uppercase()
        val local = mutableMapOf<String, String>()
        val minor = Money.parseMinor(f.amount, currency)
        if (minor == null) local["amount"] = "Enter an amount like 9.99"
        val days = f.customDays.trim().toIntOrNull()
        if (f.cycle == Cycle.CUSTOM_DAYS && days == null) local["customDays"] = "Enter a number of days"
        if (local.isNotEmpty()) { errors = local; return }

        val sub = Subscription(
            id = id, name = f.name, amountMinor = minor!!, currency = currency, cycle = f.cycle,
            customDays = if (f.cycle == Cycle.CUSTOM_DAYS) days else null, startDate = f.startDate,
            category = f.category, notes = f.notes, cancelUrl = f.cancelUrl.trim().ifBlank { null },
            presetKey = f.presetKey, paymentMethod = f.paymentMethod, isTrial = f.isTrial,
            trialEndsAt = if (f.isTrial) f.trialEndsAt else null, remindDaysBefore = f.remindDays, extraRemindDays = f.extraRemind, active = f.active,
        )
        viewModelScope.launch {
            when (val r = c.subscriptions.save(sub)) {
                is SaveResult.Saved -> {
                    if (id == 0L) c.photos.reassign(OwnerType.SUBSCRIPTION, 0, r.id)
                    errors = emptyMap(); savedId = r.id
                }
                is SaveResult.Invalid -> errors = r.errors.associate { it.field to it.message }
            }
        }
    }
}
