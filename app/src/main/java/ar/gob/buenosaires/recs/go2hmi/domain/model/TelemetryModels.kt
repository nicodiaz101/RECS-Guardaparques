package ar.gob.buenosaires.recs.go2hmi.domain.model

/**
 * Estado del enlace de telemetría 4G LTE entre el robot y la consola del guardaparques.
 *
 * - [ONLINE]: paquetes recibidos dentro de la ventana de vigencia (< 5 s).
 * - [STALE_DATA]: la última trama superó el umbral de frescura (>= 5 s) sin confirmación de corte.
 * - [DISCONNECTED]: pérdida explícita de enlace 4G (ej. cañadón WP4 o corte manual en simulador).
 */
enum class ConnectivityStatus {
    ONLINE,
    STALE_DATA,
    DISCONNECTED
}

/**
 * Máquina de estados operativos del cuadrúpedo Unitree Go2 EDU.
 */
enum class RobotOperationalState {
    /** En espera en base o waypoint tras finalizar/cancelar misión. */
    IDLE,
    /** Recorriendo autónomamente el circuito WP0 -> WP5. */
    PATROLLING,
    /** Detenido temporalmente por orden del operador o prevención de batería baja. */
    PAUSED,
    /** Freno autónomo por detección de obstáculo LiDAR 4D o foco ígneo (exige ACK/decisión). */
    ALERT_HOLD,
    /** Evacuación autónoma en reversa/retorno hacia WP0 Base Acceso Brasil (RTH). */
    RETURNING_SAFE,
    /** Detención operativa por software solicitada desde la app (no reemplaza E-Stop físico). */
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

/**
 * Índice meteorológico de peligro de incendio en la Reserva Ecológica Costanera Sur.
 */
enum class FireRiskLevel(val label: String) {
    BAJO("BAJO"),
    MODERADO("MODERADO"),
    ALTO("ALTO"),
    EXTREMO("EXTREMO")
}

/**
 * Alerta emitida por los sensores embarcados o por el supervisor de autonomía del robot.
 */
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

/**
 * Estado de la Interacción Humano-Robot (HRI) con visitantes presentes en el sendero.
 * Modela la señalización luminosa (baliza) y acústica (altavoz/sirena) para evitar
 * incidentes con peatones y ciclistas dentro de la Reserva.
 */
data class HriState(
    val beaconMode: String,
    val speakerAnnouncement: String,
    val isEmergencyAlarm: Boolean
)

/**
 * Punto de referencia georreferenciado del circuito de patrullaje.
 */
data class WaypointNode(
    val index: Int,
    val code: String,
    val name: String,
    val shortLabel: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Snapshot inmutable con las variables de telemetría requeridas por la consigna + estado HRI.
 *
 * Incluye las 8 métricas base (conectividad, energía, estado, misión/progreso, GPS, cinemática,
 * alertas y timestamp) más las 3 variables específicas del servicio de prevención ígnea en RECS
 * (temperatura máxima IR, concentración de humo/CO e índice meteorológico de fuego).
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

/**
 * Catálogo estático de los 6 waypoints del circuito cerrado "Laguna de los Coipos" (3.8 km).
 */
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
