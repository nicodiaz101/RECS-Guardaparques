package ar.gob.buenosaires.recs.go2hmi.domain.policy

import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot

/**
 * Matriz de habilitación de comandos discretos mostrada por la consola HMI.
 *
 * Modela qué acciones están permitidas en función del estado operativo del Go2,
 * su nivel de energía, el riesgo térmico y si hay una orden en curso (`PENDING`).
 */
data class CommandPermissions(
    val canStartMission: Boolean = false,
    val canPauseMission: Boolean = false,
    val canResumeMission: Boolean = false,
    val canCancelMission: Boolean = false,
    val canReturnToSafe: Boolean = false,
    val canOperationalStop: Boolean = true,
    val canAckAlert: Boolean = false,
    val highlightRth: Boolean = false
)

/**
 * Resultado de validar una orden contra las reglas de seguridad del dominio.
 */
sealed interface CommandValidationResult {
    data object Allowed : CommandValidationResult
    data class Rejected(val reasonMessage: String) : CommandValidationResult
}

/**
 * Reglas de seguridad operativa e interbloqueos del Unitree Go2 EDU.
 *
 * Vive en la capa `domain` para que tanto [OperatorViewModel] (habilitación de botones)
 * como el simulador (validación de órdenes en el robot) usen exactamente los mismos umbrales
 * y condiciones sin duplicar lógica entre capas.
 */
object RobotSafetyPolicy {

    /** Batería mínima exigida para iniciar o reanudar una patrulla preventiva. */
    const val MIN_MISSION_BATTERY_PERCENT = 20

    /** Umbral preventivo que resalta la recomendación de Regreso a Punto Seguro (RTH). */
    const val LOW_BATTERY_WARNING_PERCENT = 25

    /** Nivel crítico mínimo antes de detener la marcha por protección de celdas. */
    const val CRITICAL_BATTERY_STOP_PERCENT = 5

    /** Temperatura infrarroja (°C) que activa el bloqueo térmico hacia el frente de fuego. */
    const val THERMAL_LOCKOUT_TEMP_C = 70.0f

    /** Concentración de humo/CO (ppm) considerada crítica en tarjeta de telemetría. */
    const val SMOKE_CRITICAL_PPM = 200

    /** Antigüedad máxima (segundos) de un paquete antes de marcar la HMI como DATO ANTIGUO. */
    const val STALE_TELEMETRY_THRESHOLD_SEC = 5L

    /**
     * Indica si el robot está estacionado en la Base Acceso Brasil (WP0) sin misión en curso
     * (tanto al inicio con 0% como al completar el circuito al 100%).
     */
    fun isAtSafeBase(telemetry: TelemetrySnapshot): Boolean =
        telemetry.operationalState == RobotOperationalState.IDLE &&
            telemetry.currentWaypointIndex == 0

    /** Indica si la cámara IR detecta un foco térmico por encima del umbral de seguridad. */
    fun hasThermalHazard(telemetry: TelemetrySnapshot): Boolean =
        telemetry.maxThermalTempC >= THERMAL_LOCKOUT_TEMP_C

    /**
     * Calcula los permisos de la botonera discreta para el estado actual del robot.
     *
     * @param telemetry Última fotografía de telemetría recibida.
     * @param isCommandPending `true` si hay una orden esperando confirmación (`PENDING`);
     *   mientras una orden está en tránsito se bloquean nuevos envíos para garantizar
     *   el principio "orden enviada != orden ejecutada".
     * @param isResumingRth `true` cuando el estado `PAUSED` interrumpió un retorno `RETURNING_SAFE`
     *   (en ese caso sí se permite reanudar la evacuación incluso con batería baja o foco ígneo).
     */
    fun evaluatePermissions(
        telemetry: TelemetrySnapshot,
        isCommandPending: Boolean,
        isResumingRth: Boolean = false
    ): CommandPermissions {
        val state = telemetry.operationalState
        val atBase = isAtSafeBase(telemetry)
        val hasFireHazard = hasThermalHazard(telemetry)
        val hasUnackedAlert = telemetry.activeAlerts.any { !it.isAcknowledged }

        // Para iniciar una misión desde WP0 se requiere estar en base (IDLE o STOPPED en WP0),
        // contar con batería suficiente y no tener riesgo térmico activo.
        val canStartState = state == RobotOperationalState.IDLE || state == RobotOperationalState.STOPPED
        val canStart = !isCommandPending &&
            canStartState &&
            telemetry.currentWaypointIndex == 0 &&
            telemetry.batteryPercent > MIN_MISSION_BATTERY_PERCENT &&
            !hasFireHazard

        // Pausar solo tiene sentido si la unidad está en movimiento (patrullando o retornando).
        val canPause = !isCommandPending &&
            (state == RobotOperationalState.PATROLLING || state == RobotOperationalState.RETURNING_SAFE)

        // Nota de diseño HMI (Flujo 2 / Escenario 5):
        // En ALERT_HOLD térmico mantenemos habilitado el botón "Reanudar Misión" para que, si el
        // operador intenta avanzar hacia el fuego, el robot responda explícitamente con RECHAZADA
        // (protección térmica activa). En cambio, con batería <= 20% en PAUSED (Escenario 2)
        // se deshabilita reanudar patrulla salvo que esté reanudando un retorno RTH.
        val canResume = !isCommandPending && when (state) {
            RobotOperationalState.ALERT_HOLD -> true
            RobotOperationalState.PAUSED ->
                isResumingRth || telemetry.batteryPercent > MIN_MISSION_BATTERY_PERCENT
            else -> false
        }

        // No se puede cancelar si el robot ya está en espera sin misión activa (IDLE).
        val canCancel = !isCommandPending && state != RobotOperationalState.IDLE

        // El retorno a punto seguro (RTH -> WP0) está disponible siempre que no esté ya en base
        // o ya ejecutando el retorno hacia WP0.
        val canRth = !isCommandPending &&
            !atBase &&
            state != RobotOperationalState.RETURNING_SAFE

        val canStop = !isCommandPending && state != RobotOperationalState.STOPPED
        val canAck = !isCommandPending && hasUnackedAlert

        // Se destaca visualmente el botón RTH ante batería baja, incendio, espera por alerta,
        // o cuando el robot quedó detenido fuera de la base tras cancelar o detenerse.
        val shouldHighlightRth = canRth && (
            telemetry.batteryPercent <= LOW_BATTERY_WARNING_PERCENT ||
                hasFireHazard ||
                state == RobotOperationalState.ALERT_HOLD ||
                telemetry.currentWaypointIndex != 0
            )

        return CommandPermissions(
            canStartMission = canStart,
            canPauseMission = canPause,
            canResumeMission = canResume,
            canCancelMission = canCancel,
            canReturnToSafe = canRth,
            canOperationalStop = canStop,
            canAckAlert = canAck,
            highlightRth = shouldHighlightRth
        )
    }

    /**
     * Valida en el lado del robot/simulador si una orden discreta cumple las precondiciones.
     * Evita transiciones incoherentes aunque un cliente envíe comandos fuera de secuencia.
     */
    fun validateCommand(
        command: RobotCommandType,
        state: TelemetrySnapshot,
        waypointCode: String,
        isResumingRth: Boolean = false
    ): CommandValidationResult {
        val hasUnackedAlert = state.activeAlerts.any { !it.isAcknowledged }

        return when (command) {
            RobotCommandType.START_MISSION -> when {
                state.batteryPercent <= MIN_MISSION_BATTERY_PERCENT ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: Batería en ${state.batteryPercent}% (<= $MIN_MISSION_BATTERY_PERCENT%). Recargue o ejecute RTH."
                    )
                hasThermalHazard(state) ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: Alerta térmica activa (${"%.1f".format(java.util.Locale.US, state.maxThermalTempC)} °C). Prohibido iniciar patrulla."
                    )
                state.operationalState == RobotOperationalState.PATROLLING ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: El Go2 ya se encuentra patrullando (${state.missionId})."
                    )
                state.operationalState != RobotOperationalState.IDLE &&
                    state.operationalState != RobotOperationalState.STOPPED ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: Estado actual ${state.operationalState.name} impide iniciar nueva misión. Cancele o retorne a WP0 primero."
                    )
                state.currentWaypointIndex != 0 ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: Unidad fuera de base ($waypointCode). Ejecute Regreso a Punto Seguro (RTH) hacia WP0 antes de iniciar."
                    )
                else -> CommandValidationResult.Allowed
            }

            RobotCommandType.PAUSE_MISSION -> when (state.operationalState) {
                RobotOperationalState.PATROLLING,
                RobotOperationalState.RETURNING_SAFE -> CommandValidationResult.Allowed
                else -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: Solo se puede pausar durante patrullaje o retorno."
                )
            }

            RobotCommandType.RESUME_MISSION -> when {
                // Si el operador pausó un retorno seguro (RTH), permitir reanudar la evacuación
                // aunque exista foco ígneo o batería baja.
                state.operationalState == RobotOperationalState.PAUSED && isResumingRth ->
                    CommandValidationResult.Allowed
                hasThermalHazard(state) ->
                    CommandValidationResult.Rejected(
                        "RECHAZADA: Protección térmica activa (${"%.1f".format(java.util.Locale.US, state.maxThermalTempC)} °C). Prohibido avanzar hacia el fuego; solicite Regreso a Punto Seguro"
                    )
                hasUnackedAlert ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: Debe reconocer la alerta activa (ACK) antes de reanudar."
                    )
                state.batteryPercent <= MIN_MISSION_BATTERY_PERCENT ->
                    CommandValidationResult.Rejected(
                        "❌ RECHAZADA: Batería en ${state.batteryPercent}% (<= $MIN_MISSION_BATTERY_PERCENT%). Solicite Regreso a Punto Seguro (RTH)."
                    )
                state.operationalState == RobotOperationalState.PAUSED ||
                    state.operationalState == RobotOperationalState.ALERT_HOLD ->
                    CommandValidationResult.Allowed
                else -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: El robot no se encuentra pausado ni en espera."
                )
            }

            RobotCommandType.CANCEL_MISSION -> when (state.operationalState) {
                RobotOperationalState.IDLE -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: No hay una misión activa o detenida para cancelar."
                )
                else -> CommandValidationResult.Allowed
            }

            RobotCommandType.RETURN_TO_SAFE -> when {
                isAtSafeBase(state) -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: El Go2 ya se encuentra en WP0 Punto Seguro."
                )
                state.operationalState == RobotOperationalState.RETURNING_SAFE -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: El Go2 ya se encuentra ejecutando Regreso a Punto Seguro (RTH)."
                )
                else -> CommandValidationResult.Allowed
            }

            RobotCommandType.OPERATIONAL_STOP -> when (state.operationalState) {
                RobotOperationalState.STOPPED -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: El Go2 ya se encuentra en detención operativa (STOPPED)."
                )
                else -> CommandValidationResult.Allowed
            }

            RobotCommandType.ACK_ALERT -> when {
                state.activeAlerts.isEmpty() -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: No hay alertas activas pendientes de reconocimiento."
                )
                !hasUnackedAlert -> CommandValidationResult.Rejected(
                    "❌ RECHAZADA: Las alertas activas ya fueron reconocidas (ACK)."
                )
                else -> CommandValidationResult.Allowed
            }
        }
    }
}
