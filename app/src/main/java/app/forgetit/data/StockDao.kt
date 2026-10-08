package app.forgetit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_item") fun observeItems(): Flow<List<StockItemEntity>>
    @Query("SELECT * FROM stock_item") suspend fun getItems(): List<StockItemEntity>
    @Query("SELECT * FROM stock_item WHERE id = :id") suspend fun getItem(id: Long): StockItemEntity?
    @Upsert suspend fun upsertItem(e: StockItemEntity): Long
    @Query("DELETE FROM stock_item WHERE id = :id") suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM stock_batch") fun observeBatches(): Flow<List<StockBatchEntity>>
    @Query("SELECT * FROM stock_batch") suspend fun getBatches(): List<StockBatchEntity>
    @Query("SELECT * FROM stock_batch WHERE itemId = :itemId") suspend fun batchesFor(itemId: Long): List<StockBatchEntity>
    @Query("SELECT * FROM stock_batch WHERE id = :id") suspend fun getBatch(id: Long): StockBatchEntity?
    @Insert suspend fun insertBatch(e: StockBatchEntity): Long
    @Upsert suspend fun upsertBatch(e: StockBatchEntity): Long
    @Query("DELETE FROM stock_batch WHERE id = :id") suspend fun deleteBatch(id: Long)
    @Query("DELETE FROM stock_batch WHERE itemId = :itemId") suspend fun deleteBatches(itemId: Long)

    @Query("SELECT * FROM stock_log") fun observeLogs(): Flow<List<StockLogEntity>>
    @Query("SELECT * FROM stock_log") suspend fun getLogs(): List<StockLogEntity>
    @Insert suspend fun insertLog(e: StockLogEntity): Long
    @Query("DELETE FROM stock_log WHERE itemId = :itemId") suspend fun deleteLogs(itemId: Long)
}
