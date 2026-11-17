package ar.gob.buenosaires.recs.go2hmi.data.repository

import ar.gob.buenosaires.recs.go2hmi.data.local.room.HistoryEventDao
import ar.gob.buenosaires.recs.go2hmi.data.local.room.HistoryEventEntity
import ar.gob.buenosaires.recs.go2hmi.data.simulator.Go2SimulationEngine
import ar.gob.buenosaires.recs.go2hmi.data.simulator.SimulationDomainEvent
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.HistoryEventItem
import ar.gob.buenosaires.recs.go2hmi.domain.model.RecsTrailWaypoints
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.repository.Go2RobotRepository
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class Go2RobotRepositoryImpl(
    private val engine: Go2SimulationEngine,
    private val historyDao: HistoryEventDao
) : Go2RobotRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val telemetryFlow: StateFlow<TelemetrySnapshot> = engine.telemetryFlow
    override val lastCommandStatusFlow: StateFlow<CommandStatusReport> = engine.lastCommandStatusFlow
    override val isForcedDisconnectedFlow: StateFlow<Boolean> = engine.isForcedDisconnectedFlow
    override val isForceRejectNextCommandFlow: StateFlow<Boolean> = engine.isForceRejectNextCommandFlow
    override val activeScenarioFlow: StateFlow<SimulationScenario> = engine.activeScenarioFlow

    override val historyEventsFlow: Flow<List<HistoryEventItem>> =
        historyDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    init {
        repoScope.launch {
            seedInitialHistoryIfEmpty()
        }
        repoScope.launch {
            engine.domainEventsFlow.collect { event ->
                persistDomainEvent(event)
            }
        }
    }

    override suspend fun sendCommand(command: RobotCommandType, alertIdToAck: String?) {
        val result = engine.processCommand(command = command, alertIdToAck = alertIdToAck)
        val snap = engine.telemetryFlow.value
        val wp = RecsTrailWaypoints.byIndex(snap.currentWaypointIndex)
        val coords = String.format(Locale.US, "%.4f, %.4f", snap.latitude, snap.longitude)

        val severity = when (result.state) {
            CommandExecutionState.CONFIRMED -> AlertSeverity.INFO
            CommandExecutionState.REJECTED -> AlertSeverity.CRITICAL
            CommandExecutionState.NO_RESPONSE -> AlertSeverity.WARNING
            else -> AlertSeverity.INFO
        }

        historyDao.insert(
            HistoryEventEntity(
                timestampEpochMs = result.timestampEpochMs,
                category = HistoryEventCategory.COMMAND.name,
                severity = severity.name,
                title = "Comando ${command.name} -> ${result.state.name}",
                details = result.message,
                waypointCode = wp.code,
                coordinates = coords
            )
        )
    }

    override suspend fun triggerScenario(scenario: SimulationScenario) {
        engine.triggerScenario(scenario)
    }

    override suspend fun toggleForcedDisconnection(disconnect: Boolean) {
        engine.toggleForcedDisconnection(disconnect)
        val snap = engine.telemetryFlow.value
        val wp = RecsTrailWaypoints.byIndex(snap.currentWaypointIndex)
        historyDao.insert(
            HistoryEventEntity(
                timestampEpochMs = System.currentTimeMillis(),
                category = HistoryEventCategory.INCIDENT.name,
                severity = if (disconnect) AlertSeverity.WARNING.name else AlertSeverity.INFO.name,
                title = if (disconnect) "Enlace 4G interrumpido (Simulación)" else "Enlace 4G LTE restablecido",
                details = if (disconnect) {
                    "Se cortó la transmisión de paquetes hacia la consola HMI."
                } else {
                    "Telemetría en vivo recuperada en ${wp.name}."
                },
                waypointCode = wp.code,
                coordinates = String.format(Locale.US, "%.4f, %.4f", snap.latitude, snap.longitude)
            )
        )
    }

    override suspend fun toggleForceRejectNextCommand(forceReject: Boolean) {
        engine.setForceRejectNextCommand(forceReject)
    }

    override suspend fun clearHistory() {
        historyDao.deleteAll()
    }

    private suspend fun persistDomainEvent(event: SimulationDomainEvent) {
        val entity = when (event) {
            is SimulationDomainEvent.MissionStarted -> HistoryEventEntity(
                timestampEpochMs = event.epochMs,
                category = HistoryEventCategory.MISSION.name,
                severity = AlertSeverity.INFO.name,
                title = "Misión ${event.missionId} Iniciada",
                details = "Inicio de recorrido autónomo Sector Sur (WP0 → WP5, 3.8 km).",
                waypointCode = event.waypointCode,
                coordinates = event.coordinates
            )
            is SimulationDomainEvent.MissionCompleted -> HistoryEventEntity(
                timestampEpochMs = event.epochMs,
                category = HistoryEventCategory.MISSION.name,
                severity = AlertSeverity.INFO.name,
                title = "Misión ${event.missionId} Completada 100%",
                details = "Circuito Laguna de los Coipos (3.8 km) completado y unidad estacionada en WP0.",
                waypointCode = event.waypointCode,
                coordinates = event.coordinates
            )
            is SimulationDomainEvent.AlertTriggered -> {
                val category = when (event.alert.category) {
                    AlertCategory.FIRE -> HistoryEventCategory.FIRE_ALERT
                    else -> HistoryEventCategory.INCIDENT
                }
                HistoryEventEntity(
                    timestampEpochMs = event.alert.timestampEpochMs,
                    category = category.name,
                    severity = event.alert.severity.name,
                    title = "${event.alert.title} (${event.waypointCode})",
                    details = "${event.alert.description} • GPS: ${event.coordinates}",
                    waypointCode = event.waypointCode,
                    coordinates = event.coordinates
                )
            }
            is SimulationDomainEvent.AlertAcknowledged -> HistoryEventEntity(
                timestampEpochMs = event.epochMs,
                category = HistoryEventCategory.FIRE_ALERT.name,
                severity = AlertSeverity.INFO.name,
                title = "${event.alertTitle} — Reconocida (ACK)",
                details = "Guardaparques confirmó recepción y evaluación de alerta en ${event.waypointCode}.",
                waypointCode = event.waypointCode,
                coordinates = event.coordinates
            )
        }
        historyDao.insert(entity)
    }

    private suspend fun seedInitialHistoryIfEmpty() {
        if (historyDao.count() > 0) return
        val now = System.currentTimeMillis()
        historyDao.insertAll(
            listOf(
                HistoryEventEntity(
                    timestampEpochMs = now - 3_600_000L,
                    category = HistoryEventCategory.MISSION.name,
                    severity = AlertSeverity.INFO.name,
                    title = "Misión MIS-#041 Completada 100%",
                    details = "Patrulla matutina preventiva Sector Sur (WP0–WP5, 3.8 km) sin focos térmicos.",
                    waypointCode = "WP0",
                    coordinates = "-34.6152, -58.3568"
                ),
                HistoryEventEntity(
                    timestampEpochMs = now - 1_800_000L,
                    category = HistoryEventCategory.COMMAND.name,
                    severity = AlertSeverity.INFO.name,
                    title = "Comando RETURN_TO_SAFE -> CONFIRMED",
                    details = "Verificación previa de retorno autónomo a Base Acceso Brasil.",
                    waypointCode = "WP0",
                    coordinates = "-34.6152, -58.3568"
                )
            )
        )
    }
}
