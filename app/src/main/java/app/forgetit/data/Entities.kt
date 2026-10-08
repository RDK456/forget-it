package app.forgetit.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.forgetit.domain.Cycle
import app.forgetit.domain.Subscription
import java.time.LocalDate

@Entity(tableName = "subscription")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val cycle: String,
    val customDays: Int?,
    val startEpochDay: Long,
    val category: String,
    val notes: String,
    val cancelUrl: String?,
    val presetKey: String?,
    val paymentMethod: String,
    val isTrial: Boolean,
    val trialEndsEpochDay: Long?,
    val remindDaysBefore: Int,
    val active: Boolean,
    @ColumnInfo(defaultValue = "''") val extraRemind: String = "",
)

fun SubscriptionEntity.toDomain() = Subscription(
    id = id, name = name, amountMinor = amountMinor, currency = currency, cycle = Cycle.valueOf(cycle),
    customDays = customDays, startDate = LocalDate.ofEpochDay(startEpochDay), category = category, notes = notes,
    cancelUrl = cancelUrl, presetKey = presetKey, paymentMethod = paymentMethod, isTrial = isTrial,
    trialEndsAt = trialEndsEpochDay?.let(LocalDate::ofEpochDay), remindDaysBefore = remindDaysBefore, extraRemindDays = app.forgetit.domain.Offsets.parse(extraRemind), active = active,
)

fun Subscription.toEntity() = SubscriptionEntity(
    id = id, name = name.trim(), amountMinor = amountMinor, currency = currency, cycle = cycle.name,
    customDays = customDays, startEpochDay = startDate.toEpochDay(), category = category, notes = notes,
    cancelUrl = cancelUrl?.trim()?.ifBlank { null }, presetKey = presetKey, paymentMethod = paymentMethod,
    isTrial = isTrial, trialEndsEpochDay = trialEndsAt?.toEpochDay(), remindDaysBefore = remindDaysBefore, active = active, extraRemind = app.forgetit.domain.Offsets.format(extraRemindDays),
)

enum class OwnerType { SUBSCRIPTION, LOAN, STOCK_ITEM, BILL }

@Entity(tableName = "photo", indices = [Index("ownerType", "ownerId")])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,
    val ownerId: Long,
    val fileName: String,
    val addedEpochDay: Long,
    val sortOrder: Int,
)

/** One row per reminder already shown; reminderKey is kind:entityId:eventDate. */
@Entity(tableName = "reminder_log")
data class ReminderLogEntity(
    @PrimaryKey val reminderKey: String,
    val notifiedAtMillis: Long,
)
