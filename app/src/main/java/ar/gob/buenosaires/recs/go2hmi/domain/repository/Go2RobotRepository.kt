package ar.gob.buenosaires.recs.go2hmi.domain.repository

import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato puro de dominio para operar y supervisar al robot Unitree Go2 EDU.
 *
 * Desacopla completamente la capa `ui` (ViewModel) de los detalles de transporte,
 * simulación JSON o base de datos Room. Si en el futuro se reemplaza el simulador por
 * un enlace real (MQTT / WebRTC / SDK Unitree), esta interfaz permanece intacta.
 */
interface Go2RobotRepository {
    /** Flujo reactivo de solo lectura con la última telemetría conocida del robot. */
    val telemetryFlow: StateFlow<TelemetrySnapshot>

    /** Flujo de solo lectura con el ciclo de vida del último comando enviado. */
    val lastCommandStatusFlow: StateFlow<CommandStatusReport>

    /** Indica si una pausa actual interrumpió una evacuación RTH (`RETURNING_SAFE`). */
    val isPausedFromRthFlow: StateFlow<Boolean>

    /** Flujo reactivo del historial local persistido en Room. */
    val historyEventsFlow: Flow<List<HistoryEventItem>>

    /** Envía una orden discreta al robot y persiste el resultado en el historial. */
    suspend fun sendCommand(command: RobotCommandType, alertIdToAck: String? = null)

    /** Limpia todos los eventos almacenados en el historial local. */
    suspend fun clearHistory()
}
