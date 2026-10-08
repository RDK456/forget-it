package app.forgetit.domain

import java.time.LocalDate

enum class BillType { ELECTRICITY, WATER, GAS, BROADBAND, MOBILE, DTH, INSURANCE, RENT, SCHOOL, OTHER }

/** A recurring bill whose amount changes from cycle to cycle (electricity, water, mobile postpaid...). */
data class Bill(
    val id: Long = 0,
    val name: String,
    val type: BillType = BillType.OTHER,
    val currency: String,
    val cycle: Cycle = Cycle.MONTHLY,
    val customDays: Int? = null,
    val anchorDate: LocalDate,
    val remindDaysBefore: Int = 3,
    val extraRemindDays: List<Int> = emptyList(),
    val notes: String = "",
    val active: Boolean = true,
)

/** One recorded bill: what it came to for the cycle due on [dueDate], and when it was paid. */
data class BillEntry(val id: Long = 0, val billId: Long, val dueDate: LocalDate, val amountMinor: Long, val paidOn: LocalDate? = null)
