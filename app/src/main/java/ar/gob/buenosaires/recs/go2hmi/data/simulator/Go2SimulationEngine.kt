package ar.gob.buenosaires.recs.go2hmi.data.simulator

import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.HriState
import ar.gob.buenosaires.recs.go2hmi.domain.model.RecsTrailWaypoints
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class SimulationDomainEvent {
    data class MissionStarted(val missionId: String, val waypointCode: String, val coordinates: String, val epochMs: Long) : SimulationDomainEvent()
    data class MissionCompleted(val missionId: String, val waypointCode: String, val coordinates: String, val epochMs: Long) : SimulationDomainEvent()
    data class AlertTriggered(val alert: RobotAlert, val waypointCode: String, val coordinates: String) : SimulationDomainEvent()
    data class AlertAcknowledged(val alertTitle: String, val waypointCode: String, val coordinates: String, val epochMs: Long) : SimulationDomainEvent()
}

/**
 * Motor autónomo 100% offline que simula el Unitree Go2 EDU recorriendo
 * el circuito Laguna de los Coipos (3.8 km, WP0..WP5) con contratos JSON internos.
 */
class Go2SimulationEngine(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    autoStartLoop: Boolean = true,
    private val networkLatencyMs: Long = 450L,
    private val timeoutMs: Long = 3000L
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateMutex = Mutex()

    private val _telemetryJsonFlow = MutableStateFlow(TelemetryDto.fromDomain(initialSnapshot()).toJsonString())
    val telemetryJsonFlow: StateFlow<String> = _telemetryJsonFlow.asStateFlow()

    private val _telemetryFlow = MutableStateFlow(TelemetryDto.fromJsonString(_telemetryJsonFlow.value).toDomain())
    val telemetryFlow: StateFlow<TelemetrySnapshot> = _telemetryFlow.asStateFlow()

    private val _lastCommandStatusFlow = MutableStateFlow(CommandStatusReport())
    val lastCommandStatusFlow: StateFlow<CommandStatusReport> = _lastCommandStatusFlow.asStateFlow()

    private val _isForcedDisconnectedFlow = MutableStateFlow(false)
    val isForcedDisconnectedFlow: StateFlow<Boolean> = _isForcedDisconnectedFlow.asStateFlow()

    private val _isForceRejectNextCommandFlow = MutableStateFlow(false)
    val isForceRejectNextCommandFlow: StateFlow<Boolean> = _isForceRejectNextCommandFlow.asStateFlow()

    private val _activeScenarioFlow = MutableStateFlow(SimulationScenario.SCENARIO_1_NORMAL)
    val activeScenarioFlow: StateFlow<SimulationScenario> = _activeScenarioFlow.asStateFlow()

    private val _domainEventsFlow = MutableSharedFlow<SimulationDomainEvent>(extraBufferCapacity = 32)
    val domainEventsFlow: SharedFlow<SimulationDomainEvent> = _domainEventsFlow.asSharedFlow()

    private var currentState: TelemetrySnapshot = _telemetryFlow.value
    private var tickCounter: Int = 0
    private var missionNumber: Int = 42

    init {
        if (autoStartLoop) {
            scope.launch {
                while (isActive) {
                    delay(1000L)
                    stepSimulationTick()
                }
            }
        }
    }

    suspend fun stepSimulationTick(nowEpochMs: Long = System.currentTimeMillis()) {
        val completedEvent = stateMutex.withLock {
            if (_isForcedDisconnectedFlow.value) {
                return@withLock null
            }

            tickCounter++
            var updated = currentState
            var missionDoneEvent: SimulationDomainEvent.MissionCompleted? = null

            when (updated.operationalState) {
                RobotOperationalState.PATROLLING -> {
                    val nextProgress = (updated.missionProgressPercent + 4).coerceAtMost(100)
                    val nextBattery = if (tickCounter % 5 == 0) {
                        (updated.batteryPercent - 1).coerceAtLeast(5)
                    } else {
                        updated.batteryPercent
                    }

                    if (nextProgress >= 100) {
                        val wp0 = RecsTrailWaypoints.byIndex(0)
                        updated = updated.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -64,
                            batteryPercent = nextBattery,
                            voltageV = batteryToVoltage(nextBattery),
                            operationalState = RobotOperationalState.IDLE,
                            currentWaypointIndex = 0,
                            currentWaypointName = wp0.name,
                            missionProgressPercent = 100,
                            latitude = wp0.latitude,
                            longitude = wp0.longitude,
                            linearSpeedMps = 0.0f,
                            headingDeg = 0,
                            posture = "Estacionado en Base WP0 (Misión completada)",
                            lastUpdatedEpochMs = nowEpochMs,
                            hriStatus = HriState(
                                beaconMode = "Baliza Blanca Espera",
                                speakerAnnouncement = "Misión finalizada en Base Acceso Brasil",
                                isEmergencyAlarm = false
                            )
                        )
                        missionDoneEvent = SimulationDomainEvent.MissionCompleted(
                            missionId = updated.missionId,
                            waypointCode = wp0.code,
                            coordinates = formatCoords(wp0.latitude, wp0.longitude),
                            epochMs = nowEpochMs
                        )
                    } else {
                        val pos = interpolateCircuitPosition(nextProgress)
                        val wp = RecsTrailWaypoints.byIndex(pos.waypointIndex)
                        updated = updated.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -64 - (tickCounter % 6),
                            batteryPercent = nextBattery,
                            voltageV = batteryToVoltage(nextBattery),
                            currentWaypointIndex = wp.index,
                            currentWaypointName = wp.name,
                            missionProgressPercent = nextProgress,
                            latitude = pos.latitude,
                            longitude = pos.longitude,
                            linearSpeedMps = 1.5f,
                            headingDeg = pos.headingDeg,
                            posture = "Trote estable • Rumbo ${wp.code}",
                            lastUpdatedEpochMs = nowEpochMs
                        )
                    }
                }

                RobotOperationalState.RETURNING_SAFE -> {
                    val nextProgress = (updated.missionProgressPercent - 8).coerceAtLeast(0)
                    val nextBattery = if (tickCounter % 6 == 0) {
                        (updated.batteryPercent - 1).coerceAtLeast(5)
                    } else {
                        updated.batteryPercent
                    }
                    if (nextProgress == 0) {
                        val wp0 = RecsTrailWaypoints.byIndex(0)
                        updated = updated.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -62,
                            batteryPercent = nextBattery,
                            voltageV = batteryToVoltage(nextBattery),
                            operationalState = RobotOperationalState.IDLE,
                            currentWaypointIndex = 0,
                            currentWaypointName = wp0.name,
                            missionProgressPercent = 0,
                            latitude = wp0.latitude,
                            longitude = wp0.longitude,
                            linearSpeedMps = 0.0f,
                            headingDeg = 0,
                            posture = "En Punto Seguro WP0",
                            lastUpdatedEpochMs = nowEpochMs,
                            maxThermalTempC = 24.4f,
                            smokePpm = 12,
                            fireWeatherRisk = FireRiskLevel.MODERADO,
                            activeAlerts = emptyList(),
                            hriStatus = HriState(
                                beaconMode = "Baliza Blanca Espera",
                                speakerAnnouncement = "Arribo seguro a Base Acceso Brasil",
                                isEmergencyAlarm = false
                            )
                        )
                    } else {
                        val pos = interpolateCircuitPosition(nextProgress)
                        updated = updated.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -66,
                            batteryPercent = nextBattery,
                            voltageV = batteryToVoltage(nextBattery),
                            currentWaypointIndex = pos.waypointIndex,
                            currentWaypointName = RecsTrailWaypoints.byIndex(pos.waypointIndex).name,
                            missionProgressPercent = nextProgress,
                            latitude = pos.latitude,
                            longitude = pos.longitude,
                            linearSpeedMps = 1.8f,
                            headingDeg = (pos.headingDeg + 180) % 360,
                            posture = "Retorno rápido RTH hacia WP0",
                            lastUpdatedEpochMs = nowEpochMs
                        )
                    }
                }

                else -> {
                    updated = updated.copy(
                        connectivityStatus = ConnectivityStatus.ONLINE,
                        lastUpdatedEpochMs = nowEpochMs
                    )
                }
            }

            publishTelemetry(updated)
            missionDoneEvent
        }

        if (completedEvent != null) {
            _domainEventsFlow.emit(completedEvent)
        }
    }

    suspend fun processCommand(
        command: RobotCommandType,
        alertIdToAck: String? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): CommandStatusReport {
        val requestJson = CommandRequestDto(
            command = command.name,
            alertIdToAck = alertIdToAck,
            sentEpochMs = nowEpochMs
        ).toJsonString()
        val parsedRequest = CommandRequestDto.fromJsonString(requestJson)
        val cmd = RobotCommandType.valueOf(parsedRequest.command)

        _lastCommandStatusFlow.value = CommandStatusReport(
            command = cmd,
            state = CommandExecutionState.PENDING,
            message = "⏳ PENDIENTE: Enviando orden '${cmd.label}' al Go2...",
            timestampEpochMs = nowEpochMs
        )

        if (_isForcedDisconnectedFlow.value) {
            delay(timeoutMs)
            val responseJson = CommandResponseDto(
                command = cmd.name,
                status = CommandExecutionState.NO_RESPONSE.name,
                message = "⚠️ SIN RESPUESTA: Timeout de red 4G agotado tras ${timeoutMs / 1000}s",
                timestampEpochMs = System.currentTimeMillis()
            ).toJsonString()
            val parsed = CommandResponseDto.fromJsonString(responseJson).toDomain()
            _lastCommandStatusFlow.value = parsed
            return parsed
        }

        delay(networkLatencyMs)

        val eventsToEmit = mutableListOf<SimulationDomainEvent>()
        val responseJson = stateMutex.withLock {
            val respNow = System.currentTimeMillis()
            val s = currentState

            if (_isForceRejectNextCommandFlow.value) {
                _isForceRejectNextCommandFlow.value = false
                return@withLock CommandResponseDto(
                    command = cmd.name,
                    status = CommandExecutionState.REJECTED.name,
                    message = "❌ RECHAZADA: Simulación de rechazo forzado por validación de seguridad Go2",
                    timestampEpochMs = respNow
                ).toJsonString()
            }

            when (cmd) {
                RobotCommandType.START_MISSION -> {
                    when {
                        s.batteryPercent <= 20 -> CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: Batería en ${s.batteryPercent}% (<= 20%). Recargue o ejecute RTH.",
                            timestampEpochMs = respNow
                        )
                        s.operationalState == RobotOperationalState.PATROLLING -> CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: El Go2 ya se encuentra patrullando (${s.missionId}).",
                            timestampEpochMs = respNow
                        )
                        else -> {
                            missionNumber++
                            val newMissionId = "MIS-#%03d".format(Locale.US, missionNumber)
                            val wp0 = RecsTrailWaypoints.byIndex(0)
                            val next = s.copy(
                                operationalState = RobotOperationalState.PATROLLING,
                                missionId = newMissionId,
                                currentWaypointIndex = 0,
                                currentWaypointName = wp0.name,
                                missionProgressPercent = 4,
                                latitude = wp0.latitude,
                                longitude = wp0.longitude,
                                linearSpeedMps = 1.5f,
                                posture = "Trote estable (Patrulla iniciada)",
                                activeAlerts = emptyList(),
                                maxThermalTempC = 24.2f,
                                smokePpm = 12,
                                fireWeatherRisk = FireRiskLevel.MODERADO,
                                lastUpdatedEpochMs = respNow,
                                hriStatus = HriState(
                                    beaconMode = "Luz Verde Cortesía",
                                    speakerAnnouncement = "Ronda Preventiva — Reserva Ecológica",
                                    isEmergencyAlarm = false
                                )
                            )
                            publishTelemetry(next)
                            eventsToEmit += SimulationDomainEvent.MissionStarted(
                                missionId = newMissionId,
                                waypointCode = wp0.code,
                                coordinates = formatCoords(wp0.latitude, wp0.longitude),
                                epochMs = respNow
                            )
                            CommandResponseDto(
                                command = cmd.name,
                                status = CommandExecutionState.CONFIRMED.name,
                                message = "✅ CONFIRMADA: $newMissionId iniciada por el Go2 (ACK)",
                                timestampEpochMs = respNow
                            )
                        }
                    }
                }

                RobotCommandType.PAUSE_MISSION -> {
                    if (s.operationalState == RobotOperationalState.PATROLLING ||
                        s.operationalState == RobotOperationalState.RETURNING_SAFE
                    ) {
                        publishTelemetry(
                            s.copy(
                                operationalState = RobotOperationalState.PAUSED,
                                linearSpeedMps = 0.0f,
                                posture = "Detenido en sendero (Pausa operativa)",
                                lastUpdatedEpochMs = respNow,
                                hriStatus = HriState(
                                    beaconMode = "Luz Ámbar Espera",
                                    speakerAnnouncement = "Unidad en pausa operativa",
                                    isEmergencyAlarm = false
                                )
                            )
                        )
                        CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.CONFIRMED.name,
                            message = "✅ CONFIRMADA: Misión pausada en ${RecsTrailWaypoints.byIndex(s.currentWaypointIndex).code} (ACK)",
                            timestampEpochMs = respNow
                        )
                    } else {
                        CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: Solo se puede pausar durante patrullaje o retorno.",
                            timestampEpochMs = respNow
                        )
                    }
                }

                RobotCommandType.RESUME_MISSION -> {
                    val hasUnackedAlert = s.activeAlerts.any { !it.isAcknowledged }
                    when {
                        s.maxThermalTempC >= 70.0f -> CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "RECHAZADA: Protección térmica activa (${formatTemp(s.maxThermalTempC)} °C). Prohibido avanzar hacia el fuego; solicite Regreso a Punto Seguro",
                            timestampEpochMs = respNow
                        )
                        hasUnackedAlert -> CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: Debe reconocer la alerta activa (ACK) antes de reanudar.",
                            timestampEpochMs = respNow
                        )
                        s.operationalState == RobotOperationalState.PAUSED ||
                        s.operationalState == RobotOperationalState.ALERT_HOLD -> {
                            publishTelemetry(
                                s.copy(
                                    operationalState = RobotOperationalState.PATROLLING,
                                    linearSpeedMps = 1.5f,
                                    posture = "Desvío lateral seguro • Reanudando patrulla",
                                    activeAlerts = emptyList(),
                                    lastUpdatedEpochMs = respNow,
                                    hriStatus = HriState(
                                        beaconMode = "Luz Verde Cortesía",
                                        speakerAnnouncement = "Ronda Preventiva en curso",
                                        isEmergencyAlarm = false
                                    )
                                )
                            )
                            CommandResponseDto(
                                command = cmd.name,
                                status = CommandExecutionState.CONFIRMED.name,
                                message = "✅ CONFIRMADA: Patrullaje reanudado con desvío seguro (ACK)",
                                timestampEpochMs = respNow
                            )
                        }
                        else -> CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: El robot no se encuentra pausado ni en espera.",
                            timestampEpochMs = respNow
                        )
                    }
                }

                RobotCommandType.CANCEL_MISSION -> {
                    val wp0 = RecsTrailWaypoints.byIndex(0)
                    publishTelemetry(
                        s.copy(
                            operationalState = RobotOperationalState.IDLE,
                            missionProgressPercent = 0,
                            linearSpeedMps = 0.0f,
                            posture = "Misión cancelada • Listo en ${RecsTrailWaypoints.byIndex(s.currentWaypointIndex).code}",
                            activeAlerts = emptyList(),
                            lastUpdatedEpochMs = respNow,
                            hriStatus = HriState(
                                beaconMode = "Baliza Blanca Espera",
                                speakerAnnouncement = "Altavoz en silencio",
                                isEmergencyAlarm = false
                            )
                        )
                    )
                    CommandResponseDto(
                        command = cmd.name,
                        status = CommandExecutionState.CONFIRMED.name,
                        message = "✅ CONFIRMADA: Misión ${s.missionId} cancelada por operador (ACK)",
                        timestampEpochMs = respNow
                    )
                }

                RobotCommandType.RETURN_TO_SAFE -> {
                    if (s.operationalState == RobotOperationalState.IDLE && s.currentWaypointIndex == 0 && s.missionProgressPercent == 0) {
                        CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: El Go2 ya se encuentra en WP0 Punto Seguro.",
                            timestampEpochMs = respNow
                        )
                    } else {
                        val safeProgress = if (s.missionProgressPercent == 0) 16 else s.missionProgressPercent
                        publishTelemetry(
                            s.copy(
                                operationalState = RobotOperationalState.RETURNING_SAFE,
                                missionProgressPercent = safeProgress,
                                linearSpeedMps = 1.8f,
                                posture = "Evacuación RTH hacia WP0 Acceso Brasil",
                                lastUpdatedEpochMs = respNow,
                                hriStatus = HriState(
                                    beaconMode = "Baliza Verde/Ámbar Retorno",
                                    speakerAnnouncement = "Unidad retornando a Base Acceso Brasil",
                                    isEmergencyAlarm = s.maxThermalTempC >= 70f
                                )
                            )
                        )
                        CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.CONFIRMED.name,
                            message = "✅ CONFIRMADA: Regreso a Punto Seguro (RTH -> WP0) en ejecución (ACK)",
                            timestampEpochMs = respNow
                        )
                    }
                }

                RobotCommandType.OPERATIONAL_STOP -> {
                    publishTelemetry(
                        s.copy(
                            operationalState = RobotOperationalState.STOPPED,
                            linearSpeedMps = 0.0f,
                            posture = "STOPPED (Detención operativa por software)",
                            lastUpdatedEpochMs = respNow,
                            hriStatus = HriState(
                                beaconMode = "Baliza Roja Fija",
                                speakerAnnouncement = "Robot detenido por operador",
                                isEmergencyAlarm = true
                            )
                        )
                    )
                    CommandResponseDto(
                        command = cmd.name,
                        status = CommandExecutionState.CONFIRMED.name,
                        message = "✅ CONFIRMADA: Detención operativa ejecutada (Software Stop ACK)",
                        timestampEpochMs = respNow
                    )
                }

                RobotCommandType.ACK_ALERT -> {
                    if (s.activeAlerts.isEmpty()) {
                        CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.REJECTED.name,
                            message = "❌ RECHAZADA: No hay alertas activas pendientes de reconocimiento.",
                            timestampEpochMs = respNow
                        )
                    } else {
                        val updatedAlerts = s.activeAlerts.map { alert ->
                            if (parsedRequest.alertIdToAck == null || alert.id == parsedRequest.alertIdToAck) {
                                alert.copy(isAcknowledged = true)
                            } else {
                                alert
                            }
                        }
                        val ackedTitle = updatedAlerts.firstOrNull()?.title ?: "Alerta"
                        val wp = RecsTrailWaypoints.byIndex(s.currentWaypointIndex)
                        publishTelemetry(
                            s.copy(
                                activeAlerts = updatedAlerts,
                                lastUpdatedEpochMs = respNow
                            )
                        )
                        eventsToEmit += SimulationDomainEvent.AlertAcknowledged(
                            alertTitle = ackedTitle,
                            waypointCode = wp.code,
                            coordinates = formatCoords(s.latitude, s.longitude),
                            epochMs = respNow
                        )
                        CommandResponseDto(
                            command = cmd.name,
                            status = CommandExecutionState.CONFIRMED.name,
                            message = "✅ CONFIRMADA: Alerta reconocida (ACK) en ${wp.code}",
                            timestampEpochMs = respNow
                        )
                    }
                }
            }
        }.let { dto -> if (dto is CommandResponseDto) dto.toJsonString() else dto.toString() }

        val finalReport = CommandResponseDto.fromJsonString(responseJson).toDomain()
        _lastCommandStatusFlow.value = finalReport
        eventsToEmit.forEach { _domainEventsFlow.emit(it) }
        return finalReport
    }

    suspend fun triggerScenario(scenario: SimulationScenario) {
        val now = System.currentTimeMillis()
        val emittedAlerts = mutableListOf<SimulationDomainEvent.AlertTriggered>()

        stateMutex.withLock {
            _activeScenarioFlow.value = scenario
            when (scenario) {
                SimulationScenario.SCENARIO_1_NORMAL -> {
                    _isForcedDisconnectedFlow.value = false
                    _isForceRejectNextCommandFlow.value = false
                    val wp0 = RecsTrailWaypoints.byIndex(0)
                    publishTelemetry(
                        TelemetrySnapshot(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -64,
                            batteryPercent = 95,
                            voltageV = 29.8f,
                            operationalState = RobotOperationalState.IDLE,
                            missionId = "MIS-#042",
                            currentWaypointIndex = 0,
                            currentWaypointName = wp0.name,
                            missionProgressPercent = 0,
                            latitude = wp0.latitude,
                            longitude = wp0.longitude,
                            sectorName = "Sector Sur — Laguna de los Coipos (3.8 km)",
                            linearSpeedMps = 0.0f,
                            headingDeg = 22,
                            posture = "En espera en WP0 Base",
                            activeAlerts = emptyList(),
                            lastUpdatedEpochMs = now,
                            maxThermalTempC = 24.2f,
                            smokePpm = 12,
                            fireWeatherRisk = FireRiskLevel.MODERADO,
                            windSpeedKmh = 22,
                            windDirection = "SE",
                            humidityPercent = 38,
                            hriStatus = HriState(
                                beaconMode = "Baliza Blanca Espera",
                                speakerAnnouncement = "Altavoz en espera (Listo en WP0)",
                                isEmergencyAlarm = false
                            )
                        )
                    )
                    _lastCommandStatusFlow.value = CommandStatusReport(
                        state = CommandExecutionState.IDLE,
                        message = "Escenario 1 cargado — Presioná \"Iniciar Misión\" para recorrer WP0 → WP5",
                        timestampEpochMs = now
                    )
                }

                SimulationScenario.SCENARIO_2_LOW_BATTERY -> {
                    _isForcedDisconnectedFlow.value = false
                    val wp2 = RecsTrailWaypoints.byIndex(2)
                    val alert = RobotAlert(
                        id = "ALT-BATT-$now",
                        severity = AlertSeverity.WARNING,
                        category = AlertCategory.BATTERY,
                        title = "Batería Baja (18%)",
                        description = "Batería Baja (18%): Autonomía insuficiente para completar el circuito",
                        waypointIndex = 2,
                        isAcknowledged = false,
                        timestampEpochMs = now
                    )
                    publishTelemetry(
                        currentState.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -71,
                            batteryPercent = 18,
                            voltageV = 25.1f,
                            operationalState = RobotOperationalState.PAUSED,
                            currentWaypointIndex = 2,
                            currentWaypointName = wp2.name,
                            missionProgressPercent = 40,
                            latitude = wp2.latitude,
                            longitude = wp2.longitude,
                            linearSpeedMps = 0.0f,
                            posture = "Pausa preventiva por batería crítica (18%)",
                            activeAlerts = listOf(alert),
                            lastUpdatedEpochMs = now,
                            maxThermalTempC = 24.8f,
                            smokePpm = 14,
                            fireWeatherRisk = FireRiskLevel.MODERADO,
                            hriStatus = HriState(
                                beaconMode = "Luz Ámbar Precaución",
                                speakerAnnouncement = "Unidad con batería baja retornando a base",
                                isEmergencyAlarm = false
                            )
                        )
                    )
                    emittedAlerts += SimulationDomainEvent.AlertTriggered(
                        alert = alert,
                        waypointCode = wp2.code,
                        coordinates = formatCoords(wp2.latitude, wp2.longitude)
                    )
                    _lastCommandStatusFlow.value = CommandStatusReport(
                        command = RobotCommandType.RETURN_TO_SAFE,
                        state = CommandExecutionState.IDLE,
                        message = "⚠️ Batería en 18%: Se recomienda solicitar Regreso a Punto Seguro (RTH)",
                        timestampEpochMs = now
                    )
                }

                SimulationScenario.SCENARIO_3_DISCONNECTION -> {
                    val wp4 = RecsTrailWaypoints.byIndex(4)
                    val staleTime = now - 6000L
                    publishTelemetry(
                        currentState.copy(
                            connectivityStatus = ConnectivityStatus.DISCONNECTED,
                            rssiDbm = -112,
                            batteryPercent = currentState.batteryPercent.coerceAtLeast(64),
                            operationalState = RobotOperationalState.PATROLLING,
                            currentWaypointIndex = 4,
                            currentWaypointName = wp4.name,
                            missionProgressPercent = 76,
                            latitude = wp4.latitude,
                            longitude = wp4.longitude,
                            linearSpeedMps = 1.5f,
                            posture = "Cañadón WP4 (Último paquete congelado)",
                            lastUpdatedEpochMs = staleTime,
                            hriStatus = HriState(
                                beaconMode = "Último estado conocido",
                                speakerAnnouncement = "Enlace 4G interrumpido en cañadón WP4",
                                isEmergencyAlarm = false
                            )
                        )
                    )
                    _isForcedDisconnectedFlow.value = true
                    _lastCommandStatusFlow.value = CommandStatusReport(
                        state = CommandExecutionState.NO_RESPONSE,
                        message = "⚠️ SIN SEÑAL 4G en WP4: Paquetes detenidos — Modo Datos Antiguos activo",
                        timestampEpochMs = now
                    )
                }

                SimulationScenario.SCENARIO_4_OBSTACLE -> {
                    _isForcedDisconnectedFlow.value = false
                    val wp5 = RecsTrailWaypoints.byIndex(5)
                    val alert = RobotAlert(
                        id = "ALT-OBS-$now",
                        severity = AlertSeverity.CRITICAL,
                        category = AlertCategory.OBSTACLE,
                        title = "Obstáculo LiDAR 4D en WP5",
                        description = "Sauce caído de 35 cm bloqueando el sendero. Reconozca alerta (ACK) para decidir desvío lateral o RTH.",
                        waypointIndex = 5,
                        isAcknowledged = false,
                        timestampEpochMs = now
                    )
                    publishTelemetry(
                        currentState.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -68,
                            operationalState = RobotOperationalState.ALERT_HOLD,
                            currentWaypointIndex = 5,
                            currentWaypointName = wp5.name,
                            missionProgressPercent = 88,
                            latitude = wp5.latitude,
                            longitude = wp5.longitude,
                            linearSpeedMps = 0.0f,
                            posture = "ALERT_HOLD • Obstáculo 35 cm detectado por LiDAR 4D",
                            activeAlerts = listOf(alert),
                            lastUpdatedEpochMs = now,
                            maxThermalTempC = 24.6f,
                            smokePpm = 15,
                            fireWeatherRisk = FireRiskLevel.MODERADO,
                            hriStatus = HriState(
                                beaconMode = "Luz Ámbar Precaución",
                                speakerAnnouncement = "Obstáculo en sendero sur — Mantenga distancia",
                                isEmergencyAlarm = false
                            )
                        )
                    )
                    emittedAlerts += SimulationDomainEvent.AlertTriggered(
                        alert = alert,
                        waypointCode = wp5.code,
                        coordinates = formatCoords(wp5.latitude, wp5.longitude)
                    )
                    _lastCommandStatusFlow.value = CommandStatusReport(
                        state = CommandExecutionState.IDLE,
                        message = "⚠️ Obstáculo en WP5: Presioná Reconocer Alerta (ACK) antes de Reanudar o RTH",
                        timestampEpochMs = now
                    )
                }

                SimulationScenario.SCENARIO_5_FIRE_ALERT -> {
                    _isForcedDisconnectedFlow.value = false
                    val wp3 = RecsTrailWaypoints.byIndex(3)
                    val alert = RobotAlert(
                        id = "ALT-FIRE-$now",
                        severity = AlertSeverity.CRITICAL,
                        category = AlertCategory.FIRE,
                        title = "¡ALERTA CRÍTICA! Foco Ígneo Detectado en WP3",
                        description = "Cámara IR: 114.5 °C | Humo: 390 ppm en WP3 Cortafuegos Este. Robot frenado a 5m (ALERT_HOLD).",
                        waypointIndex = 3,
                        isAcknowledged = false,
                        timestampEpochMs = now
                    )
                    publishTelemetry(
                        currentState.copy(
                            connectivityStatus = ConnectivityStatus.ONLINE,
                            rssiDbm = -65,
                            batteryPercent = currentState.batteryPercent.coerceAtLeast(82),
                            voltageV = batteryToVoltage(currentState.batteryPercent.coerceAtLeast(82)),
                            operationalState = RobotOperationalState.ALERT_HOLD,
                            currentWaypointIndex = 3,
                            currentWaypointName = wp3.name,
                            missionProgressPercent = 60,
                            latitude = wp3.latitude,
                            longitude = wp3.longitude,
                            linearSpeedMps = 0.0f,
                            posture = "ALERT_HOLD • Frenado automático a 5m del foco",
                            activeAlerts = listOf(alert),
                            lastUpdatedEpochMs = now,
                            maxThermalTempC = 114.5f,
                            smokePpm = 390,
                            fireWeatherRisk = FireRiskLevel.EXTREMO,
                            windSpeedKmh = 34,
                            windDirection = "SE",
                            humidityPercent = 18,
                            hriStatus = HriState(
                                beaconMode = "Baliza Roja + Sirena Evacuación",
                                speakerAnnouncement = "ALERTA INCENDIO: Visitantes evacuar Cortafuegos Este",
                                isEmergencyAlarm = true
                            )
                        )
                    )
                    emittedAlerts += SimulationDomainEvent.AlertTriggered(
                        alert = alert,
                        waypointCode = wp3.code,
                        coordinates = formatCoords(wp3.latitude, wp3.longitude)
                    )
                    _lastCommandStatusFlow.value = CommandStatusReport(
                        state = CommandExecutionState.IDLE,
                        message = "🔥 FOCO ÍGNEO WP3 (114.5 °C): Reanudar bloqueado por protección térmica",
                        timestampEpochMs = now
                    )
                }
            }
        }

        emittedAlerts.forEach { _domainEventsFlow.emit(it) }
    }

    suspend fun toggleForcedDisconnection(disconnect: Boolean) {
        stateMutex.withLock {
            _isForcedDisconnectedFlow.value = disconnect
            if (!disconnect) {
                val now = System.currentTimeMillis()
                publishTelemetry(
                    currentState.copy(
                        connectivityStatus = ConnectivityStatus.ONLINE,
                        rssiDbm = -64,
                        lastUpdatedEpochMs = now
                    )
                )
                _lastCommandStatusFlow.value = CommandStatusReport(
                    state = CommandExecutionState.CONFIRMED,
                    message = "✅ Señal 4G LTE restablecida — Telemetría sincronizada en vivo",
                    timestampEpochMs = now
                )
            } else {
                publishTelemetry(
                    currentState.copy(
                        connectivityStatus = ConnectivityStatus.DISCONNECTED,
                        rssiDbm = -115
                    )
                )
            }
        }
    }

    fun setForceRejectNextCommand(forceReject: Boolean) {
        _isForceRejectNextCommandFlow.value = forceReject
    }

    private fun publishTelemetry(snapshot: TelemetrySnapshot) {
        currentState = snapshot
        val json = TelemetryDto.fromDomain(snapshot).toJsonString()
        _telemetryJsonFlow.value = json
        _telemetryFlow.value = TelemetryDto.fromJsonString(json).toDomain()
    }

    private data class InterpolatedPosition(
        val waypointIndex: Int,
        val latitude: Double,
        val longitude: Double,
        val headingDeg: Int
    )

    private fun interpolateCircuitPosition(progressPercent: Int): InterpolatedPosition {
        val waypoints = RecsTrailWaypoints.sectorSurWaypoints
        val totalSegments = waypoints.size
        val normalized = (progressPercent.coerceIn(0, 99) / 100.0) * totalSegments
        val segIndex = normalized.toInt().coerceIn(0, totalSegments - 1)
        val fraction = normalized - segIndex
        val from = waypoints[segIndex]
        val to = waypoints[(segIndex + 1) % totalSegments]

        val lat = from.latitude + (to.latitude - from.latitude) * fraction
        val lon = from.longitude + (to.longitude - from.longitude) * fraction
        val heading = ((Math.toDegrees(atan2(to.longitude - from.longitude, to.latitude - from.latitude)) + 360.0) % 360.0).roundToInt()
        val displayWp = if (fraction > 0.5) to.index else from.index
        return InterpolatedPosition(displayWp, lat, lon, heading)
    }

    companion object {
        private fun batteryToVoltage(percent: Int): Float =
            ((24.2f + (percent / 100f) * 5.6f) * 10f).roundToInt() / 10f

        private fun formatTemp(temp: Float): String =
            String.format(Locale.US, "%.1f", temp)

        private fun formatCoords(lat: Double, lon: Double): String =
            String.format(Locale.US, "%.4f, %.4f", lat, lon)

        private fun initialSnapshot(): TelemetrySnapshot {
            val wp0 = RecsTrailWaypoints.byIndex(0)
            return TelemetrySnapshot(
                connectivityStatus = ConnectivityStatus.ONLINE,
                rssiDbm = -64,
                batteryPercent = 95,
                voltageV = 29.8f,
                operationalState = RobotOperationalState.IDLE,
                missionId = "MIS-#042",
                currentWaypointIndex = 0,
                currentWaypointName = wp0.name,
                missionProgressPercent = 0,
                latitude = wp0.latitude,
                longitude = wp0.longitude,
                sectorName = "Sector Sur — Laguna de los Coipos (3.8 km)",
                linearSpeedMps = 0.0f,
                headingDeg = 22,
                posture = "En espera en WP0 Base Acceso Brasil",
                activeAlerts = emptyList(),
                lastUpdatedEpochMs = System.currentTimeMillis(),
                maxThermalTempC = 24.2f,
                smokePpm = 12,
                fireWeatherRisk = FireRiskLevel.MODERADO,
                windSpeedKmh = 22,
                windDirection = "SE",
                humidityPercent = 38,
                hriStatus = HriState(
                    beaconMode = "Baliza Blanca Espera",
                    speakerAnnouncement = "Altavoz en silencio (Base WP0)",
                    isEmergencyAlarm = false
                )
            )
        }
    }
}
