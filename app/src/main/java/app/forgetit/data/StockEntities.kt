package app.forgetit.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.forgetit.domain.LogKind
import app.forgetit.domain.StockBatch
import app.forgetit.domain.StockItem
import app.forgetit.domain.StockLog
import java.time.LocalDate

@Entity(tableName = "stock_item")
data class StockItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val category: String,
    val lowThresholdMilli: Long,
    val dailyUsageMilli: Long?,
    val expiryAlertDays: Int,
    val baselineEpochDay: Long,
    val notes: String,
    val active: Boolean,
    val packSizeMilli: Long? = null,
    @ColumnInfo(defaultValue = "0") val leadDays: Int = 0,
    @ColumnInfo(defaultValue = "''") val brand: String = "",
    @ColumnInfo(defaultValue = "''") val store: String = "",
)

fun StockItemEntity.toDomain() = StockItem(
    id = id, name = name, unit = unit, category = category, lowThresholdMilli = lowThresholdMilli, dailyUsageMilli = dailyUsageMilli,
    expiryAlertDays = expiryAlertDays, baselineDate = LocalDate.ofEpochDay(baselineEpochDay), notes = notes, active = active,
    packSizeMilli = packSizeMilli, leadDays = leadDays, brand = brand, store = store,
)

fun StockItem.toEntity() = StockItemEntity(
    id = id, name = name.trim(), unit = unit.trim(), category = category, lowThresholdMilli = lowThresholdMilli, dailyUsageMilli = dailyUsageMilli,
    expiryAlertDays = expiryAlertDays, baselineEpochDay = baselineDate.toEpochDay(), notes = notes, active = active,
    packSizeMilli = packSizeMilli, leadDays = leadDays, brand = brand.trim(), store = store.trim(),
)

@Entity(tableName = "stock_batch")
data class StockBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val quantityMilli: Long,
    val addedEpochDay: Long,
    val expiryEpochDay: Long?,
)

fun StockBatchEntity.toDomain() = StockBatch(id, itemId, quantityMilli, LocalDate.ofEpochDay(addedEpochDay), expiryEpochDay?.let(LocalDate::ofEpochDay))
fun StockBatch.toEntity() = StockBatchEntity(id, itemId, quantityMilli, addedOn.toEpochDay(), expiry?.toEpochDay())

@Entity(tableName = "stock_log")
data class StockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val epochDay: Long,
    val deltaMilli: Long,
    val kind: String,
    val priceMinor: Long? = null,
)

fun StockLogEntity.toDomain() = StockLog(id, itemId, LocalDate.ofEpochDay(epochDay), deltaMilli, LogKind.valueOf(kind), priceMinor)
fun StockLog.toEntity() = StockLogEntity(id, itemId, date.toEpochDay(), deltaMilli, kind.name, priceMinor)
