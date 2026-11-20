package ar.gob.buenosaires.recs.go2hmi

import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.HriState
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.policy.RobotSafetyPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotSafetyPolicyTest {

    private fun baseSnapshot(
        state: RobotOperationalState = RobotOperationalState.IDLE,
        waypointIndex: Int = 0,
        progress: Int = 0,
        battery: Int = 90,
        tempC: Float = 24.0f,
        alerts: List<RobotAlert> = emptyList()
    ): TelemetrySnapshot = TelemetrySnapshot(
        connectivityStatus = ConnectivityStatus.ONLINE,
        rssiDbm = -64,
        batteryPercent = battery,
        voltageV = 29.0f,
        operationalState = state,
        missionId = "MIS-#042",
        currentWaypointIndex = waypointIndex,
        currentWaypointName = "WP$waypointIndex",
        missionProgressPercent = progress,
        latitude = -34.6152,
        longitude = -58.3568,
        sectorName = "Sector Sur",
        linearSpeedMps = 0.0f,
        headingDeg = 0,
        posture = "Test",
        activeAlerts = alerts,
        lastUpdatedEpochMs = 1000L,
        maxThermalTempC = tempC,
        smokePpm = 12,
        fireWeatherRisk = FireRiskLevel.MODERADO,
        windSpeedKmh = 20,
        windDirection = "SE",
        humidityPercent = 40,
        hriStatus = HriState("Blanca", "Silencio", false)
    )

    @Test
    fun pendingCommandLocksAllButtons() {
        val perms = RobotSafetyPolicy.evaluatePermissions(
            telemetry = baseSnapshot(state = RobotOperationalState.PATROLLING, waypointIndex = 2, progress = 35),
            isCommandPending = true
        )
        assertFalse(perms.canStartMission)
        assertFalse(perms.canPauseMission)
        assertFalse(perms.canResumeMission)
        assertFalse(perms.canCancelMission)
        assertFalse(perms.canReturnToSafe)
        assertFalse(perms.canOperationalStop)
        assertFalse(perms.canAckAlert)
    }

    @Test
    fun idleAtBaseEnablesOnlyStartAndStop() {
        val perms = RobotSafetyPolicy.evaluatePermissions(
            telemetry = baseSnapshot(state = RobotOperationalState.IDLE, waypointIndex = 0, progress = 0),
            isCommandPending = false
        )
        assertTrue(perms.canStartMission)
        assertFalse(perms.canPauseMission)
        assertFalse(perms.canResumeMission)
        assertFalse(perms.canCancelMission)
        assertFalse(perms.canReturnToSafe)
        assertTrue(perms.canOperationalStop)
    }

    @Test
    fun idleAwayFromBaseDisablesStartAndHighlightsRth() {
        val perms = RobotSafetyPolicy.evaluatePermissions(
            telemetry = baseSnapshot(state = RobotOperationalState.IDLE, waypointIndex = 3, progress = 0),
            isCommandPending = false
        )
        assertFalse(perms.canStartMission)
        assertFalse(perms.canCancelMission)
        assertTrue(perms.canReturnToSafe)
        assertTrue(perms.highlightRth)
    }

    @Test
    fun pausedWithLowBatteryDisablesPatrolResumeUnlessResumingRth() {
        val lowBattPaused = baseSnapshot(
            state = RobotOperationalState.PAUSED,
            waypointIndex = 2,
            progress = 34,
            battery = 18
        )

        val patrolPerms = RobotSafetyPolicy.evaluatePermissions(
            telemetry = lowBattPaused,
            isCommandPending = false,
            isResumingRth = false
        )
        assertFalse(patrolPerms.canResumeMission)
        assertTrue(patrolPerms.canReturnToSafe)
        assertTrue(patrolPerms.highlightRth)

        val rthResumePerms = RobotSafetyPolicy.evaluatePermissions(
            telemetry = lowBattPaused,
            isCommandPending = false,
            isResumingRth = true
        )
        assertTrue(rthResumePerms.canResumeMission)
    }

    @Test
    fun alertHoldWithFireAllowsClickingResumeForThermalRejectionDemoAndEnablesAck() {
        val fireAlert = RobotAlert(
            id = "ALT-FIRE",
            severity = AlertSeverity.CRITICAL,
            category = AlertCategory.FIRE,
            title = "Foco Ígneo",
            description = "114.5 C",
            waypointIndex = 3,
            isAcknowledged = false,
            timestampEpochMs = 1000L
        )
        val perms = RobotSafetyPolicy.evaluatePermissions(
            telemetry = baseSnapshot(
                state = RobotOperationalState.ALERT_HOLD,
                waypointIndex = 3,
                progress = 50,
                tempC = 114.5f,
                alerts = listOf(fireAlert)
            ),
            isCommandPending = false
        )
        assertTrue(perms.canResumeMission)
        assertTrue(perms.canAckAlert)
        assertTrue(perms.canReturnToSafe)
        assertTrue(perms.highlightRth)
    }
}
