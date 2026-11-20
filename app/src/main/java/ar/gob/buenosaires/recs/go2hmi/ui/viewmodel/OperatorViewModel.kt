package ar.gob.buenosaires.recs.go2hmi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.policy.CommandPermissions
import ar.gob.buenosaires.recs.go2hmi.domain.policy.RobotSafetyPolicy
import ar.gob.buenosaires.recs.go2hmi.domain.repository.Go2RobotRepository
import ar.gob.buenosaires.recs.go2hmi.domain.repository.RobotSimulationController
import ar.gob.buenosaires.recs.go2hmi.domain.util.SystemTimeProvider
import ar.gob.buenosaires.recs.go2hmi.domain.util.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado único e inmutable que observa la interfaz Compose.
 */
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

/**
 * ViewModel principal de operación y supervisión del guardaparques.
 *
 * Responsabilidades:
 * - Combinar flujos de solo lectura de [Go2RobotRepository] y [RobotSimulationController]
 *   en un único [uiState] expuesto como `StateFlow<HmiUiState>`.
 * - Calcular si la telemetría quedó desactualizada (>= 5 s) y qué botones habilitar
 *   usando las reglas compartidas de [RobotSafetyPolicy].
 * - Manejar los diálogos de confirmación para acciones críticas (`CANCEL_MISSION` y
 *   `OPERATIONAL_STOP`) y las alertas críticas sin meter lógica de negocio en Compose.
 */
class OperatorViewModel(
    private val repository: Go2RobotRepository,
    private val simulationController: RobotSimulationController,
    private val timeProvider: TimeProvider = SystemTimeProvider
) : ViewModel() {

    private data class RobotCoreSnapshot(
        val telemetry: TelemetrySnapshot,
        val cmdStatus: CommandStatusReport,
        val history: List<HistoryEventItem>,
        val isPausedFromRth: Boolean
    )

    private data class SimulationFlags(
        val scenario: SimulationScenario,
        val forcedDisconnected: Boolean,
        val forceRejectNextCommand: Boolean
    )

    private data class UiTransientState(
        val nowEpochMs: Long,
        val pendingConfirmation: RobotCommandType?,
        val dismissedModalAlertId: String?
    )

    // Flujos internos estrictamente privados; la UI solo observa uiState (read-only StateFlow)
    private val currentTimeMillisFlow = MutableStateFlow(timeProvider.nowEpochMs())
    private val pendingConfirmationFlow = MutableStateFlow<RobotCommandType?>(null)
    private val dismissedModalAlertIdFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                currentTimeMillisFlow.value = timeProvider.nowEpochMs()
            }
        }
    }

    private val robotCoreFlow = combine(
        repository.telemetryFlow,
        repository.lastCommandStatusFlow,
        repository.historyEventsFlow,
        repository.isPausedFromRthFlow
    ) { telemetry, cmdStatus, history, pausedFromRth ->
        RobotCoreSnapshot(
            telemetry = telemetry,
            cmdStatus = cmdStatus,
            history = history,
            isPausedFromRth = pausedFromRth
        )
    }

    private val simulationFlagsFlow = combine(
        simulationController.activeScenarioFlow,
        simulationController.isForcedDisconnectedFlow,
        simulationController.isForceRejectNextCommandFlow
    ) { scenario, forcedDisconnected, forceRejectNextCommand ->
        SimulationFlags(
            scenario = scenario,
            forcedDisconnected = forcedDisconnected,
            forceRejectNextCommand = forceRejectNextCommand
        )
    }

    private val uiTransientFlow = combine(
        currentTimeMillisFlow,
        pendingConfirmationFlow,
        dismissedModalAlertIdFlow
    ) { nowEpochMs, pendingConfirmation, dismissedModalAlertId ->
        UiTransientState(
            nowEpochMs = nowEpochMs,
            pendingConfirmation = pendingConfirmation,
            dismissedModalAlertId = dismissedModalAlertId
        )
    }

    /**
     * Único flujo público de estado observado por Compose mediante `collectAsStateWithLifecycle()`.
     */
    val uiState: StateFlow<HmiUiState> = combine(
        robotCoreFlow,
        simulationFlagsFlow,
        uiTransientFlow
    ) { core, simFlags, transient ->
        val elapsedSec = ((transient.nowEpochMs - core.telemetry.lastUpdatedEpochMs) / 1000L)
            .coerceAtLeast(0L)
        val stale = simFlags.forcedDisconnected ||
            elapsedSec >= RobotSafetyPolicy.STALE_TELEMETRY_THRESHOLD_SEC

        // Si superó el umbral de frescura sin desconexión explícita, reflejar STALE_DATA en el estado
        val effectiveTelemetry = if (stale && core.telemetry.connectivityStatus == ConnectivityStatus.ONLINE) {
            core.telemetry.copy(connectivityStatus = ConnectivityStatus.STALE_DATA)
        } else {
            core.telemetry
        }

        val permissions = RobotSafetyPolicy.evaluatePermissions(
            telemetry = effectiveTelemetry,
            isCommandPending = core.cmdStatus.state == CommandExecutionState.PENDING,
            isResumingRth = core.isPausedFromRth
        )

        val criticalAlert = effectiveTelemetry.activeAlerts.firstOrNull { alert ->
            !alert.isAcknowledged &&
                alert.id != transient.dismissedModalAlertId &&
                (RobotSafetyPolicy.hasThermalHazard(effectiveTelemetry) ||
                    alert.severity == AlertSeverity.CRITICAL)
        }

        HmiUiState(
            telemetry = effectiveTelemetry,
            lastCommandStatus = core.cmdStatus,
            historyEvents = core.history,
            activeScenario = simFlags.scenario,
            isForcedDisconnected = simFlags.forcedDisconnected,
            isForceRejectNextCommand = simFlags.forceRejectNextCommand,
            secondsSinceLastUpdate = elapsedSec,
            isDataStale = stale,
            commandPermissions = permissions,
            pendingConfirmationCommand = transient.pendingConfirmation,
            criticalModalAlert = criticalAlert
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HmiUiState(
            telemetry = repository.telemetryFlow.value,
            lastCommandStatus = repository.lastCommandStatusFlow.value,
            commandPermissions = RobotSafetyPolicy.evaluatePermissions(
                telemetry = repository.telemetryFlow.value,
                isCommandPending = false,
                isResumingRth = repository.isPausedFromRthFlow.value
            )
        )
    )

    fun onCommandRequested(command: RobotCommandType, alertIdToAck: String? = null) {
        // Pedimos confirmación previa para evitar cancelar o detener el robot por accidente
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
            simulationController.triggerScenario(scenario)
            currentTimeMillisFlow.value = timeProvider.nowEpochMs()
        }
    }

    fun toggleForcedDisconnection(disconnect: Boolean) {
        viewModelScope.launch {
            simulationController.toggleForcedDisconnection(disconnect)
            currentTimeMillisFlow.value = timeProvider.nowEpochMs()
        }
    }

    fun toggleForceRejectNextCommand(forceReject: Boolean) {
        viewModelScope.launch {
            simulationController.toggleForceRejectNextCommand(forceReject)
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

    class Factory(
        private val repository: Go2RobotRepository,
        private val simulationController: RobotSimulationController,
        private val timeProvider: TimeProvider = SystemTimeProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OperatorViewModel::class.java)) {
                return OperatorViewModel(
                    repository = repository,
                    simulationController = simulationController,
                    timeProvider = timeProvider
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
