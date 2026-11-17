package ar.gob.buenosaires.recs.go2hmi.domain.model

enum class ConnectivityStatus {
    ONLINE,
    STALE_DATA,
    DISCONNECTED
}

enum class RobotOperationalState {
    IDLE,
    PATROLLING,
    PAUSED,
    ALERT_HOLD,
    RETURNING_SAFE,
    STOPPED
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class AlertCategory {
    FIRE,
    OBSTACLE,
    BATTERY,
    SYSTEM
}

enum class FireRiskLevel(val label: String) {
    BAJO("BAJO"),
    MODERADO("MODERADO"),
    ALTO("ALTO"),
    EXTREMO("EXTREMO")
}

data class RobotAlert(
    val id: String,
    val severity: AlertSeverity,
    val category: AlertCategory,
    val title: String,
    val description: String,
    val waypointIndex: Int,
    val isAcknowledged: Boolean,
    val timestampEpochMs: Long
)

data class HriState(
    val beaconMode: String,
    val speakerAnnouncement: String,
    val isEmergencyAlarm: Boolean
)

data class WaypointNode(
    val index: Int,
    val code: String,
    val name: String,
    val shortLabel: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Snapshot inmutable con las 11 variables de telemetría requeridas + estado HRI.
 */
data class TelemetrySnapshot(
    // 1) Conectividad
    val connectivityStatus: ConnectivityStatus,
    val rssiDbm: Int,
    // 2) Energía
    val batteryPercent: Int,
    val voltageV: Float,
    // 3) Estado operativo
    val operationalState: RobotOperationalState,
    // 4) Progreso de misión y waypoint
    val missionId: String,
    val currentWaypointIndex: Int,
    val currentWaypointName: String,
    val missionProgressPercent: Int,
    // 5) Posición GPS y sector
    val latitude: Double,
    val longitude: Double,
    val sectorName: String,
    // 6) Cinemática y postura
    val linearSpeedMps: Float,
    val headingDeg: Int,
    val posture: String,
    // 7) Alertas activas
    val activeAlerts: List<RobotAlert>,
    // 8) Timestamp de último paquete recibido
    val lastUpdatedEpochMs: Long,
    // 9) Variable de Servicio 1: Temperatura máxima IR
    val maxThermalTempC: Float,
    // 10) Variable de Servicio 2: Concentración de humo / CO
    val smokePpm: Int,
    // 11) Variable de Servicio 3: Índice meteorológico ígneo
    val fireWeatherRisk: FireRiskLevel,
    val windSpeedKmh: Int,
    val windDirection: String,
    val humidityPercent: Int,
    // Estado HRI con visitantes en sendero
    val hriStatus: HriState
)

object RecsTrailWaypoints {
    val sectorSurWaypoints: List<WaypointNode> = listOf(
        WaypointNode(0, "WP0", "Base Acceso Brasil / Punto Seguro", "WP0 Base", -34.6152, -58.3568),
        WaypointNode(1, "WP1", "Sendero de los Sauces", "WP1 Sauces", -34.6129, -58.3554),
        WaypointNode(2, "WP2", "Mirador Norte", "WP2 Mirador", -34.6108, -58.3531),
        WaypointNode(3, "WP3", "Cortafuegos Este - Zona Cortaderas", "WP3 Cortafuegos", -34.6118, -58.3512),
        WaypointNode(4, "WP4", "Camino Costero Río de la Plata", "WP4 Costero", -34.6141, -58.3498),
        WaypointNode(5, "WP5", "Sendero Retorno Sur", "WP5 Sur", -34.6158, -58.3535)
    )

    fun byIndex(index: Int): WaypointNode =
        sectorSurWaypoints[index.coerceIn(0, sectorSurWaypoints.lastIndex)]
}
