package app.forgetit.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.forgetit.domain.Bill
import app.forgetit.domain.BillEntry
import app.forgetit.domain.BillType
import app.forgetit.domain.Cycle
import app.forgetit.domain.Offsets
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "bill")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val currency: String,
    val cycle: String,
    val customDays: Int?,
    val anchorEpochDay: Long,
    val remindDaysBefore: Int,
    @ColumnInfo(defaultValue = "''") val extraRemind: String = "",
    val notes: String,
    val active: Boolean,
    @ColumnInfo(defaultValue = "''") val payUrl: String = "",
)

fun BillEntity.toDomain() = Bill(
    id = id, name = name, type = BillType.valueOf(type), currency = currency, cycle = Cycle.valueOf(cycle), customDays = customDays,
    anchorDate = LocalDate.ofEpochDay(anchorEpochDay), remindDaysBefore = remindDaysBefore, extraRemindDays = Offsets.parse(extraRemind),
    notes = notes, active = active, payUrl = payUrl,
)

fun Bill.toEntity() = BillEntity(
    id = id, name = name.trim(), type = type.name, currency = currency, cycle = cycle.name, customDays = customDays,
    anchorEpochDay = anchorDate.toEpochDay(), remindDaysBefore = remindDaysBefore, extraRemind = Offsets.format(extraRemindDays),
    notes = notes, active = active, payUrl = payUrl.trim(),
)

@Entity(tableName = "bill_entry")
data class BillEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val dueEpochDay: Long,
    val amountMinor: Long,
    val paidEpochDay: Long?,
)

fun BillEntryEntity.toDomain() = BillEntry(id, billId, LocalDate.ofEpochDay(dueEpochDay), amountMinor, paidEpochDay?.let(LocalDate::ofEpochDay))
fun BillEntry.toEntity() = BillEntryEntity(id, billId, dueDate.toEpochDay(), amountMinor, paidOn?.toEpochDay())

/** A reminder the user pushed back: it is shown again at [triggerAtMillis]. */
@Entity(tableName = "snooze")
data class SnoozeEntity(
    @PrimaryKey val snoozeKey: String,
    val kind: String,
    val title: String,
    val text: String,
    val triggerAtMillis: Long,
)

@Dao
interface BillDao {
    @Query("SELECT * FROM bill") fun observeBills(): Flow<List<BillEntity>>
    @Query("SELECT * FROM bill") suspend fun getBills(): List<BillEntity>
    @Query("SELECT * FROM bill WHERE id = :id") suspend fun getBill(id: Long): BillEntity?
    @Upsert suspend fun upsertBill(e: BillEntity): Long
    @Query("DELETE FROM bill WHERE id = :id") suspend fun deleteBill(id: Long)

    @Query("SELECT * FROM bill_entry") fun observeEntries(): Flow<List<BillEntryEntity>>
    @Query("SELECT * FROM bill_entry") suspend fun getEntries(): List<BillEntryEntity>
    @Query("DELETE FROM bill_entry WHERE billId = :billId AND dueEpochDay = :due") suspend fun deleteEntry(billId: Long, due: Long)
    @Query("DELETE FROM bill_entry WHERE billId = :billId") suspend fun deleteEntries(billId: Long)
    @Insert suspend fun insertEntry(e: BillEntryEntity): Long
}

@Dao
interface SnoozeDao {
    @Query("SELECT * FROM snooze") suspend fun all(): List<SnoozeEntity>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun insert(e: SnoozeEntity)
    @Query("DELETE FROM snooze WHERE triggerAtMillis < :before") suspend fun deleteOlderThan(before: Long)
}
