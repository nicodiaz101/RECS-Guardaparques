package ar.gob.buenosaires.recs.go2hmi.domain.repository

import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato separado exclusivo para los controles del simulador ("Modo Defensa" / pruebas).
 *
 * Al separarlo de [Go2RobotRepository], una futura implementación conectada a un robot real
 * no queda obligada a implementar inyección de fallas ni disparo de escenarios de demostración.
 */
interface RobotSimulationController {
    val isForcedDisconnectedFlow: StateFlow<Boolean>
    val isForceRejectNextCommandFlow: StateFlow<Boolean>
    val activeScenarioFlow: StateFlow<SimulationScenario>

    suspend fun triggerScenario(scenario: SimulationScenario)
    suspend fun toggleForcedDisconnection(disconnect: Boolean)
    suspend fun toggleForceRejectNextCommand(forceReject: Boolean)
}
