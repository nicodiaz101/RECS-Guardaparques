package ar.gob.buenosaires.recs.go2hmi.data.simulator

import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import java.io.Closeable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Eventos de dominio emitidos por la fuente de telemetría para ser auditados en Room.
 */
sealed class SimulationDomainEvent {
    data class MissionStarted(
        val missionId: String,
        val waypointCode: String,
        val coordinates: String,
        val epochMs: Long
    ) : SimulationDomainEvent()

    data class MissionCompleted(
        val missionId: String,
        val waypointCode: String,
        val coordinates: String,
        val epochMs: Long
    ) : SimulationDomainEvent()

    data class AlertTriggered(
        val alert: RobotAlert,
        val waypointCode: String,
        val coordinates: String
    ) : SimulationDomainEvent()

    data class AlertAcknowledged(
        val alertTitle: String,
        val alertCategory: AlertCategory,
        val waypointCode: String,
        val coordinates: String,
        val epochMs: Long
    ) : SimulationDomainEvent()
}

/**
 * Abstracción del Data Source del robot dentro de la capa `data`.
 *
 * Permite que [ar.gob.buenosaires.recs.go2hmi.data.repository.Go2RobotRepositoryImpl]
 * dependa de una interfaz en lugar de acoplarse directamente a la clase concreta
 * [Go2SimulationEngine], habilitando reemplazos o dobles de prueba en tests unitarios.
 */
interface RobotTelemetryDataSource : Closeable {
    val telemetryFlow: StateFlow<TelemetrySnapshot>
    val lastCommandStatusFlow: StateFlow<CommandStatusReport>
    val isForcedDisconnectedFlow: StateFlow<Boolean>
    val isForceRejectNextCommandFlow: StateFlow<Boolean>
    val activeScenarioFlow: StateFlow<SimulationScenario>
    val isPausedFromRthFlow: StateFlow<Boolean>
    val domainEventsFlow: SharedFlow<SimulationDomainEvent>

    suspend fun processCommand(
        command: RobotCommandType,
        alertIdToAck: String? = null,
        nowEpochMs: Long? = null
    ): CommandStatusReport

    suspend fun triggerScenario(scenario: SimulationScenario)
    suspend fun toggleForcedDisconnection(disconnect: Boolean)
    fun setForceRejectNextCommand(forceReject: Boolean)
}
