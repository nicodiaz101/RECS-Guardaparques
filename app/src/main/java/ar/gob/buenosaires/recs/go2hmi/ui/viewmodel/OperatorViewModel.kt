package ar.gob.buenosaires.recs.go2hmi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.repository.Go2RobotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CommandPermissions(
    val canStartMission: Boolean = false,
    val canPauseMission: Boolean = false,
    val canResumeMission: Boolean = false,
    val canCancelMission: Boolean = false,
    val canReturnToSafe: Boolean = false,
    val canOperationalStop: Boolean = true,
    val canAckAlert: Boolean = false,
    val highlightRth: Boolean = false
)

data class HmiUiState(
    val telemetry: TelemetrySnapshot,
    val lastCommandStatus: CommandStatusReport,
    val historyEvents: List<HistoryEventItem> = emptyList(),
    val activeScenario: SimulationScenario = SimulationScenario.SCENARIO_1_NORMAL,
    val isForcedDisconnected: Boolean = false,
    val isForceRejectNextCommand: Boolean = false,
    val secondsSinceLastUpdate: Long = 0L,
    val isDataStale: Boolean = false,
    val commandPermissions: CommandPermissions = CommandPermissions(),
    val pendingConfirmationCommand: RobotCommandType? = null,
    val criticalModalAlert: RobotAlert? = null
)

class OperatorViewModel(
    private val repository: Go2RobotRepository
) : ViewModel() {

    private val currentTimeMillisFlow = MutableStateFlow(System.currentTimeMillis())
    private val pendingConfirmationFlow = MutableStateFlow<RobotCommandType?>(null)
    private val dismissedModalAlertIdFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                currentTimeMillisFlow.value = System.currentTimeMillis()
            }
        }
    }

    val uiState: StateFlow<HmiUiState> = combine(
        repository.telemetryFlow,
        repository.lastCommandStatusFlow,
        repository.historyEventsFlow,
        repository.activeScenarioFlow,
        repository.isForcedDisconnectedFlow,
        repository.isForceRejectNextCommandFlow,
        currentTimeMillisFlow,
        pendingConfirmationFlow,
        dismissedModalAlertIdFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val telemetry = values[0] as TelemetrySnapshot
        val cmdStatus = values[1] as CommandStatusReport
        @Suppress("UNCHECKED_CAST")
        val history = values[2] as List<HistoryEventItem>
        val scenario = values[3] as SimulationScenario
        val forcedDisc = values[4] as Boolean
        val forceReject = values[5] as Boolean
        val nowMs = values[6] as Long
        val pendingConfirm = values[7] as RobotCommandType?
        val dismissedAlertId = values[8] as String?

        val elapsedSec = ((nowMs - telemetry.lastUpdatedEpochMs) / 1000L).coerceAtLeast(0L)
        val stale = forcedDisc || elapsedSec >= 5L
        val permissions = computePermissions(telemetry, cmdStatus)

        val criticalAlert = telemetry.activeAlerts.firstOrNull { alert ->
            !alert.isAcknowledged && alert.id != dismissedAlertId &&
                (telemetry.maxThermalTempC >= 70f ||
                    alert.severity == ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity.CRITICAL)
        }

        HmiUiState(
            telemetry = telemetry,
            lastCommandStatus = cmdStatus,
            historyEvents = history,
            activeScenario = scenario,
            isForcedDisconnected = forcedDisc,
            isForceRejectNextCommand = forceReject,
            secondsSinceLastUpdate = elapsedSec,
            isDataStale = stale,
            commandPermissions = permissions,
            pendingConfirmationCommand = pendingConfirm,
            criticalModalAlert = criticalAlert
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HmiUiState(
            telemetry = repository.telemetryFlow.value,
            lastCommandStatus = repository.lastCommandStatusFlow.value
        )
    )

    fun onCommandRequested(command: RobotCommandType, alertIdToAck: String? = null) {
        if (command == RobotCommandType.CANCEL_MISSION || command == RobotCommandType.OPERATIONAL_STOP) {
            pendingConfirmationFlow.value = command
            return
        }
        executeCommand(command, alertIdToAck)
    }

    fun confirmPendingCommand() {
        val pending = pendingConfirmationFlow.value ?: return
        pendingConfirmationFlow.value = null
        executeCommand(pending, null)
    }

    fun dismissPendingConfirmation() {
        pendingConfirmationFlow.value = null
    }

    fun dismissCriticalAlertDialog(alertId: String) {
        dismissedModalAlertIdFlow.value = alertId
    }

    fun triggerScenario(scenario: SimulationScenario) {
        dismissedModalAlertIdFlow.value = null
        viewModelScope.launch {
            repository.triggerScenario(scenario)
            currentTimeMillisFlow.value = System.currentTimeMillis()
        }
    }

    fun toggleForcedDisconnection(disconnect: Boolean) {
        viewModelScope.launch {
            repository.toggleForcedDisconnection(disconnect)
            currentTimeMillisFlow.value = System.currentTimeMillis()
        }
    }

    fun toggleForceRejectNextCommand(forceReject: Boolean) {
        viewModelScope.launch {
            repository.toggleForceRejectNextCommand(forceReject)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private fun executeCommand(command: RobotCommandType, alertIdToAck: String?) {
        viewModelScope.launch {
            repository.sendCommand(command, alertIdToAck)
        }
    }

    private fun computePermissions(
        telemetry: TelemetrySnapshot,
        cmdStatus: CommandStatusReport
    ): CommandPermissions {
        val isPending = cmdStatus.state == CommandExecutionState.PENDING
        val state = telemetry.operationalState
        val isAtBase = state == RobotOperationalState.IDLE &&
            telemetry.currentWaypointIndex == 0 &&
            telemetry.missionProgressPercent == 0
        val hasFireHazard = telemetry.maxThermalTempC >= 70f
        val hasActiveAlerts = telemetry.activeAlerts.isNotEmpty()
        val hasUnackedAlert = telemetry.activeAlerts.any { !it.isAcknowledged }

        return CommandPermissions(
            canStartMission = !isPending &&
                (state == RobotOperationalState.IDLE || state == RobotOperationalState.STOPPED) &&
                telemetry.batteryPercent > 20 &&
                !hasFireHazard,
            canPauseMission = !isPending &&
                (state == RobotOperationalState.PATROLLING || state == RobotOperationalState.RETURNING_SAFE),
            // En Escenario 5 permitimos click en "Reanudar Misión" cuando hay fuego o pausa para demostrar el rechazo de seguridad
            // O si se pide deshabilitar en IDLE; en ALERT_HOLD térmico se habilita click para mostrar el chip RECHAZADA como exige Flujo 2,
            // mientras que en IDLE o PATROLLING está deshabilitado por precondición lógica.
            canResumeMission = !isPending &&
                (state == RobotOperationalState.PAUSED || state == RobotOperationalState.ALERT_HOLD),
            canCancelMission = !isPending && !isAtBase,
            canReturnToSafe = !isPending && !isAtBase,
            canOperationalStop = !isPending && state != RobotOperationalState.STOPPED,
            canAckAlert = !isPending && hasActiveAlerts && hasUnackedAlert,
            highlightRth = telemetry.batteryPercent <= 25 || hasFireHazard || state == RobotOperationalState.ALERT_HOLD
        )
    }

    class Factory(
        private val repository: Go2RobotRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OperatorViewModel::class.java)) {
                return OperatorViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
