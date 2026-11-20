package ar.gob.buenosaires.recs.go2hmi.data.simulator

import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.HriState
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotAlert
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Contratos JSON internos para simular la comunicación entre la app y el Go2.
 *
 * Aclaración: este formato es propio del TP para desacoplar el simulador con
 * `kotlinx.serialization`; no representa el protocolo oficial de Unitree.
 */
data class AlertDto(
    val id: String,
    val severity: String,
    val category: String,
    val title: String,
    val description: String,
    val waypointIndex: Int,
    val isAcknowledged: Boolean,
    val timestampEpochMs: Long
) {
    fun toDomain(): RobotAlert = RobotAlert(
        id = id,
        severity = AlertSeverity.valueOf(severity),
        category = AlertCategory.valueOf(category),
        title = title,
        description = description,
        waypointIndex = waypointIndex,
        isAcknowledged = isAcknowledged,
        timestampEpochMs = timestampEpochMs
    )

    companion object {
        fun fromDomain(alert: RobotAlert): AlertDto = AlertDto(
            id = alert.id,
            severity = alert.severity.name,
            category = alert.category.name,
            title = alert.title,
            description = alert.description,
            waypointIndex = alert.waypointIndex,
            isAcknowledged = alert.isAcknowledged,
            timestampEpochMs = alert.timestampEpochMs
        )
    }
}

data class TelemetryDto(
    val connectivityStatus: String,
    val rssiDbm: Int,
    val batteryPercent: Int,
    val voltageV: Float,
    val operationalState: String,
    val missionId: String,
    val currentWaypointIndex: Int,
    val currentWaypointName: String,
    val missionProgressPercent: Int,
    val latitude: Double,
    val longitude: Double,
    val sectorName: String,
    val linearSpeedMps: Float,
    val headingDeg: Int,
    val posture: String,
    val activeAlerts: List<AlertDto>,
    val lastUpdatedEpochMs: Long,
    val maxThermalTempC: Float,
    val smokePpm: Int,
    val fireWeatherRisk: String,
    val windSpeedKmh: Int,
    val windDirection: String,
    val humidityPercent: Int,
    val hriBeaconMode: String,
    val hriSpeakerAnnouncement: String,
    val hriIsEmergencyAlarm: Boolean
) {
    fun toJsonString(): String = buildJsonObject {
        put("connectivityStatus", connectivityStatus)
        put("rssiDbm", rssiDbm)
        put("batteryPercent", batteryPercent)
        put("voltageV", voltageV)
        put("operationalState", operationalState)
        put("missionId", missionId)
        put("currentWaypointIndex", currentWaypointIndex)
        put("currentWaypointName", currentWaypointName)
        put("missionProgressPercent", missionProgressPercent)
        put("latitude", latitude)
        put("longitude", longitude)
        put("sectorName", sectorName)
        put("linearSpeedMps", linearSpeedMps)
        put("headingDeg", headingDeg)
        put("posture", posture)
        put("activeAlerts", buildJsonArray {
            activeAlerts.forEach { a ->
                add(buildJsonObject {
                    put("id", a.id)
                    put("severity", a.severity)
                    put("category", a.category)
                    put("title", a.title)
                    put("description", a.description)
                    put("waypointIndex", a.waypointIndex)
                    put("isAcknowledged", a.isAcknowledged)
                    put("timestampEpochMs", a.timestampEpochMs)
                })
            }
        })
        put("lastUpdatedEpochMs", lastUpdatedEpochMs)
        put("maxThermalTempC", maxThermalTempC)
        put("smokePpm", smokePpm)
        put("fireWeatherRisk", fireWeatherRisk)
        put("windSpeedKmh", windSpeedKmh)
        put("windDirection", windDirection)
        put("humidityPercent", humidityPercent)
        put("hriBeaconMode", hriBeaconMode)
        put("hriSpeakerAnnouncement", hriSpeakerAnnouncement)
        put("hriIsEmergencyAlarm", hriIsEmergencyAlarm)
    }.toString()

    fun toDomain(): TelemetrySnapshot = TelemetrySnapshot(
        connectivityStatus = ConnectivityStatus.valueOf(connectivityStatus),
        rssiDbm = rssiDbm,
        batteryPercent = batteryPercent,
        voltageV = voltageV,
        operationalState = RobotOperationalState.valueOf(operationalState),
        missionId = missionId,
        currentWaypointIndex = currentWaypointIndex,
        currentWaypointName = currentWaypointName,
        missionProgressPercent = missionProgressPercent,
        latitude = latitude,
        longitude = longitude,
        sectorName = sectorName,
        linearSpeedMps = linearSpeedMps,
        headingDeg = headingDeg,
        posture = posture,
        activeAlerts = activeAlerts.map { it.toDomain() },
        lastUpdatedEpochMs = lastUpdatedEpochMs,
        maxThermalTempC = maxThermalTempC,
        smokePpm = smokePpm,
        fireWeatherRisk = FireRiskLevel.valueOf(fireWeatherRisk),
        windSpeedKmh = windSpeedKmh,
        windDirection = windDirection,
        humidityPercent = humidityPercent,
        hriStatus = HriState(
            beaconMode = hriBeaconMode,
            speakerAnnouncement = hriSpeakerAnnouncement,
            isEmergencyAlarm = hriIsEmergencyAlarm
        )
    )

    companion object {
        fun fromDomain(s: TelemetrySnapshot): TelemetryDto = TelemetryDto(
            connectivityStatus = s.connectivityStatus.name,
            rssiDbm = s.rssiDbm,
            batteryPercent = s.batteryPercent,
            voltageV = s.voltageV,
            operationalState = s.operationalState.name,
            missionId = s.missionId,
            currentWaypointIndex = s.currentWaypointIndex,
            currentWaypointName = s.currentWaypointName,
            missionProgressPercent = s.missionProgressPercent,
            latitude = s.latitude,
            longitude = s.longitude,
            sectorName = s.sectorName,
            linearSpeedMps = s.linearSpeedMps,
            headingDeg = s.headingDeg,
            posture = s.posture,
            activeAlerts = s.activeAlerts.map { AlertDto.fromDomain(it) },
            lastUpdatedEpochMs = s.lastUpdatedEpochMs,
            maxThermalTempC = s.maxThermalTempC,
            smokePpm = s.smokePpm,
            fireWeatherRisk = s.fireWeatherRisk.name,
            windSpeedKmh = s.windSpeedKmh,
            windDirection = s.windDirection,
            humidityPercent = s.humidityPercent,
            hriBeaconMode = s.hriStatus.beaconMode,
            hriSpeakerAnnouncement = s.hriStatus.speakerAnnouncement,
            hriIsEmergencyAlarm = s.hriStatus.isEmergencyAlarm
        )

        fun fromJsonString(jsonString: String): TelemetryDto {
            val obj = Json.parseToJsonElement(jsonString).jsonObject
            val alerts = obj.getValue("activeAlerts").jsonArray.map { el ->
                val a = el.jsonObject
                AlertDto(
                    id = a.getValue("id").jsonPrimitive.content,
                    severity = a.getValue("severity").jsonPrimitive.content,
                    category = a.getValue("category").jsonPrimitive.content,
                    title = a.getValue("title").jsonPrimitive.content,
                    description = a.getValue("description").jsonPrimitive.content,
                    waypointIndex = a.getValue("waypointIndex").jsonPrimitive.int,
                    isAcknowledged = a.getValue("isAcknowledged").jsonPrimitive.boolean,
                    timestampEpochMs = a.getValue("timestampEpochMs").jsonPrimitive.long
                )
            }
            return TelemetryDto(
                connectivityStatus = obj.getValue("connectivityStatus").jsonPrimitive.content,
                rssiDbm = obj.getValue("rssiDbm").jsonPrimitive.int,
                batteryPercent = obj.getValue("batteryPercent").jsonPrimitive.int,
                voltageV = obj.getValue("voltageV").jsonPrimitive.float,
                operationalState = obj.getValue("operationalState").jsonPrimitive.content,
                missionId = obj.getValue("missionId").jsonPrimitive.content,
                currentWaypointIndex = obj.getValue("currentWaypointIndex").jsonPrimitive.int,
                currentWaypointName = obj.getValue("currentWaypointName").jsonPrimitive.content,
                missionProgressPercent = obj.getValue("missionProgressPercent").jsonPrimitive.int,
                latitude = obj.getValue("latitude").jsonPrimitive.double,
                longitude = obj.getValue("longitude").jsonPrimitive.double,
                sectorName = obj.getValue("sectorName").jsonPrimitive.content,
                linearSpeedMps = obj.getValue("linearSpeedMps").jsonPrimitive.float,
                headingDeg = obj.getValue("headingDeg").jsonPrimitive.int,
                posture = obj.getValue("posture").jsonPrimitive.content,
                activeAlerts = alerts,
                lastUpdatedEpochMs = obj.getValue("lastUpdatedEpochMs").jsonPrimitive.long,
                maxThermalTempC = obj.getValue("maxThermalTempC").jsonPrimitive.float,
                smokePpm = obj.getValue("smokePpm").jsonPrimitive.int,
                fireWeatherRisk = obj.getValue("fireWeatherRisk").jsonPrimitive.content,
                windSpeedKmh = obj.getValue("windSpeedKmh").jsonPrimitive.int,
                windDirection = obj.getValue("windDirection").jsonPrimitive.content,
                humidityPercent = obj.getValue("humidityPercent").jsonPrimitive.int,
                hriBeaconMode = obj.getValue("hriBeaconMode").jsonPrimitive.content,
                hriSpeakerAnnouncement = obj.getValue("hriSpeakerAnnouncement").jsonPrimitive.content,
                hriIsEmergencyAlarm = obj.getValue("hriIsEmergencyAlarm").jsonPrimitive.boolean
            )
        }
    }
}

data class CommandRequestDto(
    val command: String,
    val alertIdToAck: String? = null,
    val sentEpochMs: Long
) {
    fun toJsonString(): String = buildJsonObject {
        put("command", command)
        if (alertIdToAck != null) put("alertIdToAck", alertIdToAck)
        put("sentEpochMs", sentEpochMs)
    }.toString()

    companion object {
        fun fromJsonString(jsonString: String): CommandRequestDto {
            val obj = Json.parseToJsonElement(jsonString).jsonObject
            return CommandRequestDto(
                command = obj.getValue("command").jsonPrimitive.content,
                alertIdToAck = obj["alertIdToAck"]?.jsonPrimitive?.content,
                sentEpochMs = obj.getValue("sentEpochMs").jsonPrimitive.long
            )
        }
    }
}

data class CommandResponseDto(
    val command: String,
    val status: String,
    val message: String,
    val timestampEpochMs: Long
) {
    fun toJsonString(): String = buildJsonObject {
        put("command", command)
        put("status", status)
        put("message", message)
        put("timestampEpochMs", timestampEpochMs)
    }.toString()

    fun toDomain(): CommandStatusReport = CommandStatusReport(
        command = RobotCommandType.valueOf(command),
        state = CommandExecutionState.valueOf(status),
        message = message,
        timestampEpochMs = timestampEpochMs
    )

    companion object {
        fun fromJsonString(jsonString: String): CommandResponseDto {
            val obj = Json.parseToJsonElement(jsonString).jsonObject
            return CommandResponseDto(
                command = obj.getValue("command").jsonPrimitive.content,
                status = obj.getValue("status").jsonPrimitive.content,
                message = obj.getValue("message").jsonPrimitive.content,
                timestampEpochMs = obj.getValue("timestampEpochMs").jsonPrimitive.long
            )
        }
    }
}
