package ar.gob.buenosaires.recs.go2hmi

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem
import ar.gob.buenosaires.recs.go2hmi.domain.model.HriState
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.repository.Go2RobotRepository
import ar.gob.buenosaires.recs.go2hmi.domain.repository.RobotSimulationController
import ar.gob.buenosaires.recs.go2hmi.domain.util.TimeProvider
import ar.gob.buenosaires.recs.go2hmi.ui.viewmodel.OperatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperatorViewModelTest {

    private class FakeTimeProvider(var currentMs: Long = 10_000L) : TimeProvider {
        override fun nowEpochMs(): Long = currentMs
    }

    private class FakeRobotGateway(initialSnapshot: TelemetrySnapshot) :
        Go2RobotRepository,
        RobotSimulationController {

        val mutableTelemetry = MutableStateFlow(initialSnapshot)
        override val telemetryFlow = mutableTelemetry.asStateFlow()

        private val mutableCmdStatus = MutableStateFlow(CommandStatusReport(timestampEpochMs = 10_000L))
        override val lastCommandStatusFlow = mutableCmdStatus.asStateFlow()

        private val mutablePausedFromRth = MutableStateFlow(false)
        override val isPausedFromRthFlow = mutablePausedFromRth.asStateFlow()

        private val mutableHistory = MutableStateFlow<List<HistoryEventItem>>(emptyList())
        override val historyEventsFlow: Flow<List<HistoryEventItem>> = mutableHistory

        private val mutableDisconnected = MutableStateFlow(false)
        override val isForcedDisconnectedFlow = mutableDisconnected.asStateFlow()

        private val mutableForceReject = MutableStateFlow(false)
        override val isForceRejectNextCommandFlow = mutableForceReject.asStateFlow()

        private val mutableScenario = MutableStateFlow(SimulationScenario.SCENARIO_1_NORMAL)
        override val activeScenarioFlow = mutableScenario.asStateFlow()

        val sentCommands = mutableListOf<Pair<RobotCommandType, String?>>()

        override suspend fun sendCommand(command: RobotCommandType, alertIdToAck: String?) {
            sentCommands += command to alertIdToAck
        }

        override suspend fun clearHistory() {
            mutableHistory.value = emptyList()
        }

        override suspend fun triggerScenario(scenario: SimulationScenario) {
            mutableScenario.value = scenario
        }

        override suspend fun toggleForcedDisconnection(disconnect: Boolean) {
            mutableDisconnected.value = disconnect
        }

        override suspend fun toggleForceRejectNextCommand(forceReject: Boolean) {
            mutableForceReject.value = forceReject
        }
    }

    private fun sampleSnapshot(lastUpdatedMs: Long = 10_000L): TelemetrySnapshot = TelemetrySnapshot(
        connectivityStatus = ConnectivityStatus.ONLINE,
        rssiDbm = -64,
        batteryPercent = 95,
        voltageV = 29.8f,
        operationalState = RobotOperationalState.PATROLLING,
        missionId = "MIS-#042",
        currentWaypointIndex = 1,
        currentWaypointName = "Sendero de los Sauces",
        missionProgressPercent = 20,
        latitude = -34.6129,
        longitude = -58.3554,
        sectorName = "Sector Sur",
        linearSpeedMps = 1.5f,
        headingDeg = 20,
        posture = "Trote",
        activeAlerts = emptyList(),
        lastUpdatedEpochMs = lastUpdatedMs,
        maxThermalTempC = 24.0f,
        smokePpm = 12,
        fireWeatherRisk = FireRiskLevel.MODERADO,
        windSpeedKmh = 20,
        windDirection = "SE",
        humidityPercent = 40,
        hriStatus = HriState("Verde", "Ronda", false)
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun destructiveCommandsRequireConfirmationBeforeDispatching() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val gateway = FakeRobotGateway(sampleSnapshot())
        val clock = FakeTimeProvider(10_000L)
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            OperatorViewModel.Factory(gateway, gateway, clock)
        )[OperatorViewModel::class.java]

        val collector = launch(dispatcher) { viewModel.uiState.collect {} }
        try {
            // CANCEL_MISSION no se envía directo: abre confirmación previa
            viewModel.onCommandRequested(RobotCommandType.CANCEL_MISSION)
            assertEquals(RobotCommandType.CANCEL_MISSION, viewModel.uiState.value.pendingConfirmationCommand)
            assertTrue(gateway.sentCommands.isEmpty())

            // Al confirmar, recién entonces se envía al repositorio
            viewModel.confirmPendingCommand()
            assertNull(viewModel.uiState.value.pendingConfirmationCommand)
            assertEquals(listOf(RobotCommandType.CANCEL_MISSION to null), gateway.sentCommands)

            // PAUSE_MISSION no es destructivo -> se envía directo sin diálogo
            viewModel.onCommandRequested(RobotCommandType.PAUSE_MISSION)
            assertEquals(2, gateway.sentCommands.size)
            assertEquals(RobotCommandType.PAUSE_MISSION, gateway.sentCommands.last().first)
        } finally {
            collector.cancel()
            store.clear()
        }
    }

    @Test
    fun staleThresholdMarksTelemetryAsStaleDataAfterFiveSeconds() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val gateway = FakeRobotGateway(sampleSnapshot(lastUpdatedMs = 10_000L))
        val clock = FakeTimeProvider(10_000L)
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            OperatorViewModel.Factory(gateway, gateway, clock)
        )[OperatorViewModel::class.java]

        val collector = launch(dispatcher) { viewModel.uiState.collect {} }
        try {
            assertFalse(viewModel.uiState.value.isDataStale)
            assertEquals(ConnectivityStatus.ONLINE, viewModel.uiState.value.telemetry.connectivityStatus)

            // Avanzar reloj simulado 6 segundos sin paquetes nuevos
            clock.currentMs = 16_000L
            viewModel.toggleForcedDisconnection(false) // dispara actualización de reloj en VM

            assertTrue(viewModel.uiState.value.isDataStale)
            assertEquals(6L, viewModel.uiState.value.secondsSinceLastUpdate)
            assertEquals(ConnectivityStatus.STALE_DATA, viewModel.uiState.value.telemetry.connectivityStatus)
        } finally {
            collector.cancel()
            store.clear()
        }
    }

    @Test
    fun dismissingCriticalModalHidesDialogUntilNewScenarioTriggers() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val alert = RobotAlert(
            id = "ALT-FIRE-1",
            severity = AlertSeverity.CRITICAL,
            category = AlertCategory.FIRE,
            title = "Foco Ígneo",
            description = "114.5 C",
            waypointIndex = 3,
            isAcknowledged = false,
            timestampEpochMs = 10_000L
        )
        val gateway = FakeRobotGateway(sampleSnapshot().copy(activeAlerts = listOf(alert), maxThermalTempC = 114.5f))
        val clock = FakeTimeProvider(10_000L)
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            OperatorViewModel.Factory(gateway, gateway, clock)
        )[OperatorViewModel::class.java]

        val collector = launch(dispatcher) { viewModel.uiState.collect {} }
        try {
            assertEquals("ALT-FIRE-1", viewModel.uiState.value.criticalModalAlert?.id)

            viewModel.dismissCriticalAlertDialog("ALT-FIRE-1")
            assertNull(viewModel.uiState.value.criticalModalAlert)

            // Al disparar un escenario, se limpia el ID descartado para volver a mostrar alertas nuevas
            viewModel.triggerScenario(SimulationScenario.SCENARIO_5_FIRE_ALERT)
            assertEquals("ALT-FIRE-1", viewModel.uiState.value.criticalModalAlert?.id)
        } finally {
            collector.cancel()
            store.clear()
        }
    }
}
