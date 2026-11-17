package ar.gob.buenosaires.recs.go2hmi.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryEventDao {
    @Query("SELECT * FROM history_events ORDER BY timestampEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEventEntity>>

    @Query("SELECT COUNT(*) FROM history_events")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HistoryEventEntity>)

    @Query("DELETE FROM history_events")
    suspend fun deleteAll()
}
