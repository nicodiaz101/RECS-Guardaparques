package ar.gob.buenosaires.recs.go2hmi.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) de Room para el historial local de misiones, alertas y comandos.
 *
 * Se define como `abstract class` para soportar métodos compuestos con `@Transaction`,
 * garantizando atomicidad en operaciones multi-sentencia como la siembra inicial
 * (`seedIfEmpty`) y la inserción con poda de retención (`insertAndPrune`).
 */
@Dao
abstract class HistoryEventDao {

    /**
     * Observa de forma reactiva todos los eventos ordenados del más reciente al más antiguo.
     * Room re-emite automáticamente ante cada cambio en la tabla `history_events`.
     */
    @Query("SELECT * FROM history_events ORDER BY timestampEpochMs DESC, id DESC")
    abstract fun observeAll(): Flow<List<HistoryEventEntity>>

    @Query("SELECT COUNT(*) FROM history_events")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: HistoryEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<HistoryEventEntity>)

    @Query(
        "DELETE FROM history_events WHERE id NOT IN " +
            "(SELECT id FROM history_events ORDER BY timestampEpochMs DESC, id DESC LIMIT :maxRows)"
    )
    abstract suspend fun trimOldest(maxRows: Int)

    @Query("DELETE FROM history_events")
    abstract suspend fun deleteAll()

    /**
     * Siembra datos de ejemplo únicamente si la tabla está vacía.
     * Al ejecutarse dentro de `@Transaction`, evita que dos corrutinas concurrentes
     * inserten los datos iniciales por duplicado entre `count()` e `insertAll()`.
     */
    @Transaction
    open suspend fun seedIfEmpty(entities: List<HistoryEventEntity>) {
        if (count() == 0) {
            insertAll(entities)
        }
    }

    /**
     * Inserta un evento nuevo y conserva como máximo [maxRows] registros recientes
     * dentro de una misma transacción SQLite para evitar crecimiento ilimitado offline.
     */
    @Transaction
    open suspend fun insertAndPrune(entity: HistoryEventEntity, maxRows: Int = 200): Long {
        val rowId = insert(entity)
        trimOldest(maxRows)
        return rowId
    }
}
