package ar.gob.buenosaires.recs.go2hmi

import ar.gob.buenosaires.recs.go2hmi.data.simulator.CommandRequestDto
import ar.gob.buenosaires.recs.go2hmi.data.simulator.CommandResponseDto
import ar.gob.buenosaires.recs.go2hmi.data.simulator.Go2SimulationEngine
import ar.gob.buenosaires.recs.go2hmi.data.simulator.SimulationDomainEvent
import ar.gob.buenosaires.recs.go2hmi.data.simulator.TelemetryDto
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Go2SimulationEngineTest {

    @Test
    fun jsonContractsRoundTripCorrectly() {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        val rawJson = engine.telemetryJsonFlow.value
        val parsed = TelemetryDto.fromJsonString(rawJson).toDomain()

        assertEquals(95, parsed.batteryPercent)
        assertEquals(0, parsed.currentWaypointIndex)
        assertEquals(RobotOperationalState.IDLE, parsed.operationalState)

        val cmdReq = CommandRequestDto(
            command = RobotCommandType.START_MISSION.name,
            alertIdToAck = null,
            sentEpochMs = 123456789L
        )
        val decodedReq = CommandRequestDto.fromJsonString(cmdReq.toJsonString())
        assertEquals(RobotCommandType.START_MISSION.name, decodedReq.command)

        val cmdResp = CommandResponseDto(
            command = RobotCommandType.RETURN_TO_SAFE.name,
            status = CommandExecutionState.CONFIRMED.name,
            message = "OK",
            timestampEpochMs = 123456900L
        )
        val decodedResp = CommandResponseDto.fromJsonString(cmdResp.toJsonString()).toDomain()
        assertEquals(CommandExecutionState.CONFIRMED, decodedResp.state)
        engine.close()
    }

    @Test
    fun scenario1NormalPatrolReaches100PercentAndReturnsToIdleAtWp0() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_1_NORMAL)
        val startResult = engine.processCommand(RobotCommandType.START_MISSION)
        assertEquals(CommandExecutionState.CONFIRMED, startResult.state)
        assertEquals(RobotOperationalState.PATROLLING, engine.telemetryFlow.value.operationalState)

        repeat(25) {
            engine.stepSimulationTick()
        }

        val finalSnap = engine.telemetryFlow.value
        assertEquals(100, finalSnap.missionProgressPercent)
        assertEquals(RobotOperationalState.IDLE, finalSnap.operationalState)
        assertEquals(0, finalSnap.currentWaypointIndex)
        assertEquals(0.0f, finalSnap.linearSpeedMps, 0.01f)
        engine.close()
    }

    @Test
    fun scenario2LowBatteryRejectsStartAndResumeEvenAfterAckAndAllowsRth() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_2_LOW_BATTERY)

        val snap = engine.telemetryFlow.value
        assertEquals(18, snap.batteryPercent)
        assertEquals(2, snap.currentWaypointIndex)
        assertTrue(snap.activeAlerts.any { it.description.contains("Batería Baja (18%)") })

        val startRejected = engine.processCommand(RobotCommandType.START_MISSION)
        assertEquals(CommandExecutionState.REJECTED, startRejected.state)

        val alertId = snap.activeAlerts.first().id
        val ackResult = engine.processCommand(RobotCommandType.ACK_ALERT, alertId)
        assertEquals(CommandExecutionState.CONFIRMED, ackResult.state)

        // Incluso tras ACK, reanudar patrulla con 18% debe rechazarse por autonomía insuficiente
        val resumeRejected = engine.processCommand(RobotCommandType.RESUME_MISSION)
        assertEquals(CommandExecutionState.REJECTED, resumeRejected.state)

        val rthOk = engine.processCommand(RobotCommandType.RETURN_TO_SAFE)
        assertEquals(CommandExecutionState.CONFIRMED, rthOk.state)
        assertEquals(RobotOperationalState.RETURNING_SAFE, engine.telemetryFlow.value.operationalState)
        engine.close()
    }

    @Test
    fun scenario3DisconnectionStopsTelemetryAndTimesOutCommands() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 5L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_3_DISCONNECTION)

        val frozenBefore = engine.telemetryFlow.value
        assertEquals(ConnectivityStatus.DISCONNECTED, frozenBefore.connectivityStatus)
        assertEquals(4, frozenBefore.currentWaypointIndex)

        engine.stepSimulationTick(nowEpochMs = frozenBefore.lastUpdatedEpochMs + 10_000L)
        assertEquals(frozenBefore.lastUpdatedEpochMs, engine.telemetryFlow.value.lastUpdatedEpochMs)

        val timeoutResult = engine.processCommand(RobotCommandType.PAUSE_MISSION)
        assertEquals(CommandExecutionState.NO_RESPONSE, timeoutResult.state)

        engine.toggleForcedDisconnection(false)
        assertEquals(ConnectivityStatus.ONLINE, engine.telemetryFlow.value.connectivityStatus)
        engine.close()
    }

    @Test
    fun scenario4ObstacleRequiresAckBeforeResume() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_4_OBSTACLE)

        assertEquals(RobotOperationalState.ALERT_HOLD, engine.telemetryFlow.value.operationalState)
        assertEquals(5, engine.telemetryFlow.value.currentWaypointIndex)

        val rejectedBeforeAck = engine.processCommand(RobotCommandType.RESUME_MISSION)
        assertEquals(CommandExecutionState.REJECTED, rejectedBeforeAck.state)

        val alertId = engine.telemetryFlow.value.activeAlerts.first().id
        val ackResult = engine.processCommand(RobotCommandType.ACK_ALERT, alertId)
        assertEquals(CommandExecutionState.CONFIRMED, ackResult.state)
        assertTrue(engine.telemetryFlow.value.activeAlerts.first().isAcknowledged)

        val resumeAfterAck = engine.processCommand(RobotCommandType.RESUME_MISSION)
        assertEquals(CommandExecutionState.CONFIRMED, resumeAfterAck.state)
        assertEquals(RobotOperationalState.PATROLLING, engine.telemetryFlow.value.operationalState)
        engine.close()
    }

    @Test
    fun scenario5FireAlertBlocksResumeEvenAfterAckAndAllowsRth() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_5_FIRE_ALERT)

        val snap = engine.telemetryFlow.value
        assertEquals(3, snap.currentWaypointIndex)
        assertEquals(50, snap.missionProgressPercent)
        assertEquals(114.5f, snap.maxThermalTempC, 0.01f)
        assertEquals(390, snap.smokePpm)
        assertEquals(FireRiskLevel.EXTREMO, snap.fireWeatherRisk)
        assertEquals(RobotOperationalState.ALERT_HOLD, snap.operationalState)
        assertTrue(snap.hriStatus.isEmergencyAlarm)
        assertFalse(snap.activeAlerts.first().isAcknowledged)

        // Intentar reanudar antes de ACK -> RECHAZADA por protección térmica
        val reject1 = engine.processCommand(RobotCommandType.RESUME_MISSION)
        assertEquals(CommandExecutionState.REJECTED, reject1.state)
        assertTrue(reject1.message.contains("Protección térmica activa"))

        // Reconocer alerta (ACK)
        val alertId = snap.activeAlerts.first().id
        val ack = engine.processCommand(RobotCommandType.ACK_ALERT, alertId)
        assertEquals(CommandExecutionState.CONFIRMED, ack.state)

        // Intentar reanudar después de ACK -> sigue RECHAZADA por fuego activo
        val reject2 = engine.processCommand(RobotCommandType.RESUME_MISSION)
        assertEquals(CommandExecutionState.REJECTED, reject2.state)
        assertTrue(reject2.message.contains("Prohibido avanzar hacia el fuego"))

        // Solicitar RTH -> CONFIRMADO
        val rth = engine.processCommand(RobotCommandType.RETURN_TO_SAFE)
        assertEquals(CommandExecutionState.CONFIRMED, rth.state)
        assertEquals(RobotOperationalState.RETURNING_SAFE, engine.telemetryFlow.value.operationalState)
        engine.close()
    }

    @Test
    fun cancelMidTrailPreventsTeleportOnStartAndRequiresRthToReturnToBase() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_1_NORMAL)
        assertEquals(CommandExecutionState.CONFIRMED, engine.processCommand(RobotCommandType.START_MISSION).state)

        // Avanzar ~12 ticks (~52% del circuito -> WP3)
        repeat(12) { engine.stepSimulationTick() }
        val wpBeforeCancel = engine.telemetryFlow.value.currentWaypointIndex
        assertTrue(wpBeforeCancel > 0)

        // Cancelar en medio del sendero -> pasa a IDLE pero permanece en el waypoint actual
        val cancelResult = engine.processCommand(RobotCommandType.CANCEL_MISSION)
        assertEquals(CommandExecutionState.CONFIRMED, cancelResult.state)
        assertEquals(RobotOperationalState.IDLE, engine.telemetryFlow.value.operationalState)
        assertEquals(wpBeforeCancel, engine.telemetryFlow.value.currentWaypointIndex)

        // Cancelar estando ya IDLE debe rechazarse
        val secondCancel = engine.processCommand(RobotCommandType.CANCEL_MISSION)
        assertEquals(CommandExecutionState.REJECTED, secondCancel.state)

        // Iniciar misión estando fuera de WP0 debe rechazarse (sin teletransporte)
        val startAwayFromBase = engine.processCommand(RobotCommandType.START_MISSION)
        assertEquals(CommandExecutionState.REJECTED, startAwayFromBase.state)

        // RTH regresa progresivamente hasta WP0
        val rth = engine.processCommand(RobotCommandType.RETURN_TO_SAFE)
        assertEquals(CommandExecutionState.CONFIRMED, rth.state)
        repeat(10) { engine.stepSimulationTick() }
        assertEquals(RobotOperationalState.IDLE, engine.telemetryFlow.value.operationalState)
        assertEquals(0, engine.telemetryFlow.value.currentWaypointIndex)
        engine.close()
    }

    @Test
    fun pausingDuringRthPreservesReturningSafeStateOnResume() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_2_LOW_BATTERY)

        // Solicitar RTH con 18% de batería
        assertEquals(CommandExecutionState.CONFIRMED, engine.processCommand(RobotCommandType.RETURN_TO_SAFE).state)
        assertEquals(RobotOperationalState.RETURNING_SAFE, engine.telemetryFlow.value.operationalState)

        // Pausar temporalmente durante RTH
        assertEquals(CommandExecutionState.CONFIRMED, engine.processCommand(RobotCommandType.PAUSE_MISSION).state)
        assertEquals(RobotOperationalState.PAUSED, engine.telemetryFlow.value.operationalState)
        assertTrue(engine.isPausedFromRthFlow.value)

        // Reanudar debe volver a RETURNING_SAFE (hacia WP0), nunca a PATROLLING
        assertEquals(CommandExecutionState.CONFIRMED, engine.processCommand(RobotCommandType.RESUME_MISSION).state)
        assertEquals(RobotOperationalState.RETURNING_SAFE, engine.telemetryFlow.value.operationalState)
        assertFalse(engine.isPausedFromRthFlow.value)
        engine.close()
    }

    @Test
    fun organicLowBatteryAlertEmitsAndPausesPatrolAt20Percent() = runTest {
        val engine = Go2SimulationEngine(
            dispatcher = Dispatchers.Unconfined,
            autoStartLoop = false,
            networkLatencyMs = 0L,
            timeoutMs = 10L
        )
        val triggeredAlerts = mutableListOf<SimulationDomainEvent.AlertTriggered>()
        val collectorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.domainEventsFlow.collect { ev ->
                if (ev is SimulationDomainEvent.AlertTriggered) {
                    triggeredAlerts += ev
                }
            }
        }

        // Ejecutar múltiples misiones sucesivas hasta cruzar orgánicamente el umbral del 20%
        var missions = 0
        while (engine.telemetryFlow.value.batteryPercent > 20 && missions < 20) {
            if (engine.telemetryFlow.value.operationalState == RobotOperationalState.IDLE) {
                engine.processCommand(RobotCommandType.START_MISSION)
                missions++
            }
            engine.stepSimulationTick()
        }

        assertEquals(20, engine.telemetryFlow.value.batteryPercent)
        assertEquals(RobotOperationalState.PAUSED, engine.telemetryFlow.value.operationalState)
        assertTrue(engine.telemetryFlow.value.activeAlerts.any { it.category == AlertCategory.BATTERY })
        assertTrue(triggeredAlerts.any { it.alert.category == AlertCategory.BATTERY })

        collectorJob.cancel()
        engine.close()
    }

    @Test
    fun scenarioSwitchWhileCommandInFlightDoesNotClobberNewScenarioStatus() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = Go2SimulationEngine(
            dispatcher = testDispatcher,
            autoStartLoop = false,
            networkLatencyMs = 450L,
            timeoutMs = 3000L
        )
        engine.triggerScenario(SimulationScenario.SCENARIO_3_DISCONNECTION)

        val pendingDeferred = async {
            engine.processCommand(RobotCommandType.PAUSE_MISSION)
        }
        runCurrent()
        assertEquals(CommandExecutionState.PENDING, engine.lastCommandStatusFlow.value.state)

        // Mientras el comando está esperando timeout, cambiamos a Escenario 1
        advanceTimeBy(500L)
        engine.triggerScenario(SimulationScenario.SCENARIO_1_NORMAL)
        assertEquals(CommandExecutionState.IDLE, engine.lastCommandStatusFlow.value.state)

        // Al cumplirse los 3000 ms originales, el timeout viejo NO pisa el estado de Escenario 1
        advanceTimeBy(3000L)
        val oldResult = pendingDeferred.await()
        assertEquals(CommandExecutionState.NO_RESPONSE, oldResult.state)
        assertEquals(CommandExecutionState.IDLE, engine.lastCommandStatusFlow.value.state)
        engine.close()
    }
}
