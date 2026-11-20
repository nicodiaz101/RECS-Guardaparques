package ar.gob.buenosaires.recs.go2hmi.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem

/**
 * Tabla de Room que guarda en SQLite cada evento de patrulla, alerta o comando.
 *
 * Se convierte a [HistoryEventItem] con [toDomain] para que el dominio y la UI
 * no dependan de las anotaciones de Room.
 */
@Entity(tableName = "history_events")
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampEpochMs: Long,
    val category: String,
    val severity: String,
    val title: String,
    val details: String,
    val waypointCode: String,
    val coordinates: String
) {
    /**
     * Pasa la fila de SQLite al modelo de dominio (con valor por defecto si hubiera un enum desconocido).
     */
    fun toDomain(): HistoryEventItem = HistoryEventItem(
        id = id,
        timestampEpochMs = timestampEpochMs,
        category = runCatching { HistoryEventCategory.valueOf(category) }
            .getOrDefault(HistoryEventCategory.COMMAND),
        severity = runCatching { AlertSeverity.valueOf(severity) }
            .getOrDefault(AlertSeverity.INFO),
        title = title,
        details = details,
        waypointCode = waypointCode,
        coordinates = coordinates
    )
}
