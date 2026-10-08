package app.forgetit.ui.stock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forgetit.AppContainer
import app.forgetit.data.OwnerType
import app.forgetit.data.SaveResult
import app.forgetit.domain.Money
import app.forgetit.domain.StockItem
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StockForm(
    val name: String = "",
    val unit: String = "pcs",
    val category: String = "Grocery",
    val threshold: String = "",
    val usage: String = "",
    val expiryAlertDays: Int = 2,
    val notes: String = "",
    val active: Boolean = true,
    val startQty: String = "",
    val hasExpiry: Boolean = false,
    val startExpiry: LocalDate = LocalDate.now().plusDays(7),
)

class StockEditViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var form by mutableStateOf(StockForm())
        private set
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var savedId by mutableStateOf<Long?>(null)
        private set
    private var existing: StockItem? = null

    init {
        viewModelScope.launch {
            if (id == 0L) c.photos.deleteAll(OwnerType.STOCK_ITEM, 0)
            existing = if (id != 0L) c.stock.getItem(id) else null
            val e = existing
            form = if (e != null) {
                StockForm(
                    e.name, e.unit, e.category, Money.milliToPlain(e.lowThresholdMilli), e.dailyUsageMilli?.let(Money::milliToPlain).orEmpty(),
                    e.expiryAlertDays, e.notes, e.active,
                )
            } else StockForm(startExpiry = LocalDate.now(c.clock).plusDays(7))
        }
    }

    fun update(block: (StockForm) -> StockForm) { form = block(form) }

    fun save() {
        val f = form
        val local = mutableMapOf<String, String>()
        val threshold = if (f.threshold.isBlank()) 0L else Money.parseMilli(f.threshold)
        if (threshold == null) local["threshold"] = "Enter an amount like 0.5"
        val usage = if (f.usage.isBlank()) null else Money.parseMilli(f.usage)
        if (f.usage.isNotBlank() && usage == null) local["usage"] = "Enter an amount like 0.25"
        val start = if (f.startQty.isBlank()) null else Money.parseMilli(f.startQty)
        if (f.startQty.isNotBlank() && start == null) local["startQty"] = "Enter an amount like 2"
        errors = local
        if (local.isNotEmpty()) return

        val today = LocalDate.now(c.clock)
        val item = StockItem(
            id = id, name = f.name, unit = f.unit.trim(), category = f.category, lowThresholdMilli = threshold!!,
            dailyUsageMilli = usage, expiryAlertDays = f.expiryAlertDays, baselineDate = existing?.baselineDate ?: today,
            notes = f.notes, active = f.active,
        )
        viewModelScope.launch {
            when (val r = c.stock.save(item)) {
                is SaveResult.Saved -> {
                    if (id == 0L) {
                        c.photos.reassign(OwnerType.STOCK_ITEM, 0, r.id)
                        if (start != null && start > 0) c.stock.restock(r.id, start, if (f.hasExpiry) f.startExpiry else null, today)
                    }
                    savedId = r.id
                }
                is SaveResult.Invalid -> errors = r.errors.associate { it.field to it.message }
            }
        }
    }
}
