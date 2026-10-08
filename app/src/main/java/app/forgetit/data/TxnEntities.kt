package app.forgetit.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.forgetit.domain.Txn
import app.forgetit.domain.TxnDirection
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "txn", indices = [Index(value = ["dedupe"], unique = true)])
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val accountHint: String?,
    val epochDay: Long,
    val source: String,
    val status: String,
    val snippet: String,
    val dedupe: String,
    @ColumnInfo(defaultValue = "''") val sender: String = "",
    /** Empty means "work it out from the text", so improved keywords and learned rules apply to older payments too. */
    @ColumnInfo(defaultValue = "''") val category: String = "",
    @ColumnInfo(defaultValue = "''") val note: String = "",
)

/** The user changed a merchant's category once; later payments to the same merchant follow it. */
@Entity(tableName = "category_rule")
data class CategoryRuleEntity(@PrimaryKey val merchantKey: String, val category: String)

/** A monthly limit for one spending category. */
@Entity(tableName = "category_budget")
data class CategoryBudgetEntity(@PrimaryKey val category: String, val limitMinor: Long)

fun TxnEntity.toDomain() = Txn(
    id, TxnDirection.valueOf(direction), amountMinor, currency, merchant, accountHint, LocalDate.ofEpochDay(epochDay), source, status, snippet, sender, category, note,
)

@Dao
interface TxnDao {
    @Query("SELECT * FROM txn ORDER BY epochDay DESC, id DESC") fun observeAll(): Flow<List<TxnEntity>>
    @Query("SELECT * FROM txn") suspend fun getAll(): List<TxnEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: TxnEntity): Long
    @Query("UPDATE txn SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)
    @Query("UPDATE txn SET status = 'IGNORED' WHERE sender = :sender") suspend fun ignoreSender(sender: String)
    @Query("SELECT * FROM txn WHERE id = :id") suspend fun get(id: Long): TxnEntity?
    @Upsert suspend fun upsert(e: TxnEntity): Long
    @Query("SELECT * FROM category_rule") fun observeRules(): Flow<List<CategoryRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRule(r: CategoryRuleEntity)
    @Query("SELECT * FROM category_budget") fun observeBudgets(): Flow<List<CategoryBudgetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudget(b: CategoryBudgetEntity)
    @Query("DELETE FROM category_budget WHERE category = :category") suspend fun deleteBudget(category: String)
    @Query("DELETE FROM txn WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM txn") suspend fun deleteAll()
}
