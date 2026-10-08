package app.forgetit.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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
)

fun TxnEntity.toDomain() = Txn(
    id, TxnDirection.valueOf(direction), amountMinor, currency, merchant, accountHint, LocalDate.ofEpochDay(epochDay), source, status, snippet,
)

@Dao
interface TxnDao {
    @Query("SELECT * FROM txn ORDER BY epochDay DESC, id DESC") fun observeAll(): Flow<List<TxnEntity>>
    @Query("SELECT * FROM txn") suspend fun getAll(): List<TxnEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: TxnEntity): Long
    @Query("UPDATE txn SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)
    @Query("DELETE FROM txn WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM txn") suspend fun deleteAll()
}
