package ar.gob.buenosaires.recs.go2hmi

import ar.gob.buenosaires.recs.go2hmi.data.simulator.CommandRequestDto
import ar.gob.buenosaires.recs.go2hmi.data.simulator.CommandResponseDto
import ar.gob.buenosaires.recs.go2hmi.data.simulator.Go2SimulationEngine
import ar.gob.buenosaires.recs.go2hmi.data.simulator.TelemetryDto
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }

    @Test
    fun scenario2LowBatteryRejectsStartAndAllowsRth() = runTest {
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

        val rthOk = engine.processCommand(RobotCommandType.RETURN_TO_SAFE)
        assertEquals(CommandExecutionState.CONFIRMED, rthOk.state)
        assertEquals(RobotOperationalState.RETURNING_SAFE, engine.telemetryFlow.value.operationalState)
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
    }
}
