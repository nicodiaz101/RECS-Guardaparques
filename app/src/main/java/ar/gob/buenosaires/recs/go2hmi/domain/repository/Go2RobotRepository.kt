package ar.gob.buenosaires.recs.go2hmi.domain.repository

import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Go2RobotRepository {
    val telemetryFlow: StateFlow<TelemetrySnapshot>
    val lastCommandStatusFlow: StateFlow<CommandStatusReport>
    val historyEventsFlow: Flow<List<HistoryEventItem>>
    val isForcedDisconnectedFlow: StateFlow<Boolean>
    val isForceRejectNextCommandFlow: StateFlow<Boolean>
    val activeScenarioFlow: StateFlow<SimulationScenario>

    suspend fun sendCommand(command: RobotCommandType, alertIdToAck: String? = null)
    suspend fun triggerScenario(scenario: SimulationScenario)
    suspend fun toggleForcedDisconnection(disconnect: Boolean)
    suspend fun toggleForceRejectNextCommand(forceReject: Boolean)
    suspend fun clearHistory()
}
