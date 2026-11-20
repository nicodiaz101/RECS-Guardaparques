package ar.gob.buenosaires.recs.go2hmi.domain.model

/**
 * Órdenes discretas soportadas por la HMI (sin control por joystick continuo).
 * Cubre las 7 acciones exigidas por el trabajo práctico.
 */
enum class RobotCommandType(val label: String) {
    START_MISSION("Iniciar Misión"),
    PAUSE_MISSION("Pausar Misión"),
    RESUME_MISSION("Reanudar Misión"),
    CANCEL_MISSION("Cancelar Misión"),
    RETURN_TO_SAFE("Regreso a Punto Seguro (RTH)"),
    OPERATIONAL_STOP("Detención Operativa"),
    ACK_ALERT("Reconocer Alerta (ACK)")
}

/**
 * Ciclo de vida visible de cada comando enviado desde la consola HMI.
 * Refleja la regla de seguridad: "una orden enviada no equivale a una orden ejecutada".
 */
enum class CommandExecutionState {
    /** Consola en espera, sin orden en curso. */
    IDLE,
    /** Orden enviada por enlace 4G, aguardando respuesta/ACK del robot. */
    PENDING,
    /** El robot validó precondiciones y ejecutó la transición de estado. */
    CONFIRMED,
    /** El robot rechazó la orden por regla de seguridad o precondición incumplida. */
    REJECTED,
    /** Tiempo de espera agotado (timeout 3 s) sin respuesta por corte de enlace. */
    NO_RESPONSE
}

/**
 * Resultado del último comando emitido por el operador, mostrado en el banner de estado.
 */
data class CommandStatusReport(
    val command: RobotCommandType? = null,
    val state: CommandExecutionState = CommandExecutionState.IDLE,
    val message: String = "Consola lista — Esperando orden discreta",
    val timestampEpochMs: Long = 0L
)

/**
 * Los 5 escenarios demostrables exigidos para la evaluación y defensa del TP.
 */
enum class SimulationScenario(
    val number: Int,
    val title: String,
    val subtitle: String
) {
    SCENARIO_1_NORMAL(
        number = 1,
        title = "Escenario 1 — Misión Normal",
        subtitle = "WP0 Base • 95% batería • 24.2 °C • 12 ppm • Patrulla WP0 → WP5"
    ),
    SCENARIO_2_LOW_BATTERY(
        number = 2,
        title = "Escenario 2 — Batería Baja (18%)",
        subtitle = "WP2 • Alerta preventiva 18% • Sugerencia de RTH"
    ),
    SCENARIO_3_DISCONNECTION(
        number = 3,
        title = "Escenario 3 — Desconexión y Datos Antiguos",
        subtitle = "Cañadón WP4 sin señal 4G • Timeout 3s • Tarjetas congeladas"
    ),
    SCENARIO_4_OBSTACLE(
        number = 4,
        title = "Escenario 4 — Incidente (Obstáculo LiDAR)",
        subtitle = "WP5 sauce caído 35 cm • ALERT_HOLD • Exige ACK antes de desviar"
    ),
    SCENARIO_5_FIRE_ALERT(
        number = 5,
        title = "Escenario 5 — Detección Foco Ígneo (WP3)",
        subtitle = "114.5 °C • 390 ppm • EXTREMO • Bloqueo térmico de Reanudar"
    )
}

/**
 * Categoría de registro persistido en la base local Room (`history_events`).
 */
enum class HistoryEventCategory(val label: String) {
    MISSION("Misiones"),
    FIRE_ALERT("Alertas Ígneas"),
    COMMAND("Comandos"),
    INCIDENT("Incidentes")
}

/**
 * Modelo de dominio desacoplado de la entidad Room (`HistoryEventEntity`).
 */
data class HistoryEventItem(
    val id: Long,
    val timestampEpochMs: Long,
    val category: HistoryEventCategory,
    val severity: AlertSeverity,
    val title: String,
    val details: String,
    val waypointCode: String,
    val coordinates: String
)
