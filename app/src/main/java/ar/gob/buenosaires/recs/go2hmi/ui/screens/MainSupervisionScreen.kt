package ar.gob.buenosaires.recs.go2hmi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertCategory
import ar.gob.buenosaires.recs.go2hmi.domain.model.AlertSeverity
import ar.gob.buenosaires.recs.go2hmi.domain.model.FireRiskLevel
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotCommandType
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.ui.components.CommandFeedbackBanner
import ar.gob.buenosaires.recs.go2hmi.ui.components.HmiVisualState
import ar.gob.buenosaires.recs.go2hmi.ui.components.HriTrailStatusBar
import ar.gob.buenosaires.recs.go2hmi.ui.components.OperationalStateBadge
import ar.gob.buenosaires.recs.go2hmi.ui.components.ScenarioSimulatorBottomSheet
import ar.gob.buenosaires.recs.go2hmi.ui.components.TacticalMapCard
import ar.gob.buenosaires.recs.go2hmi.ui.components.TelemetryVariableCard
import ar.gob.buenosaires.recs.go2hmi.ui.theme.LocalHmiSemanticColors
import ar.gob.buenosaires.recs.go2hmi.ui.theme.TelemetryTextStyles
import ar.gob.buenosaires.recs.go2hmi.ui.viewmodel.HmiUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSupervisionScreen(
    uiState: HmiUiState,
    onNavigateBackToUnits: () -> Unit,
    onCommandRequested: (RobotCommandType, String?) -> Unit,
    onConfirmPendingCommand: () -> Unit,
    onDismissPendingConfirmation: () -> Unit,
    onDismissCriticalAlertDialog: (String) -> Unit,
    onTriggerScenario: (SimulationScenario) -> Unit,
    onToggleForcedDisconnection: (Boolean) -> Unit,
    onToggleForceRejectNextCommand: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current
    val telemetry = uiState.telemetry
    val isStale = uiState.isDataStale
    val perms = uiState.commandPermissions

    var showSimulatorSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // ZONA A: TopAppBar + Status Strip
        TopAppBar(
            title = {
                Text(
                    text = "GO2-RECS-01",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBackToUnits) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver a Unidades"
                    )
                }
            },
            actions = {
                OperationalStateBadge(
                    state = telemetry.operationalState,
                    isStale = isStale,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Status Strip (Conectividad + Última actualización + Batería/Voltaje)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isStale) semantic.warningContainer else semantic.normalContainer,
                    border = BorderStroke(1.dp, if (isStale) semantic.warning else semantic.normal)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isStale) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = if (isStale) semantic.warning else semantic.normal,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isStale) {
                                "SIN SEÑAL 4G — DATOS ANTIGUOS (${uiState.secondsSinceLastUpdate} s)"
                            } else {
                                "ONLINE 4G"
                            },
                            style = TelemetryTextStyles.labelMono.copy(fontSize = 10.sp),
                            color = if (isStale) semantic.onWarningContainer else semantic.onNormalContainer,
                            maxLines = 1
                        )
                    }
                }

                if (!isStale) {
                    Text(
                        text = "Hace ${uiState.secondsSinceLastUpdate} s",
                        style = TelemetryTextStyles.labelMono.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isLowBatt = telemetry.batteryPercent <= 25
                val battColor = when {
                    isStale -> semantic.warning
                    isLowBatt -> semantic.warning
                    else -> semantic.normal
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isLowBatt) Icons.Rounded.BatteryAlert else Icons.Rounded.BatteryStd,
                        contentDescription = "Batería",
                        tint = battColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${telemetry.batteryPercent}% • ${telemetry.voltageV}V",
                        style = TelemetryTextStyles.labelMono.copy(fontSize = 10.sp),
                        color = battColor
                    )
                }
            }
        }

        // Cuerpo principal desplazable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Botón de recuperación 4G cuando hay desconexión (Escenario 3)
            if (uiState.isForcedDisconnected || isStale) {
                Button(
                    onClick = { onToggleForcedDisconnection(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.normalContainer,
                        contentColor = semantic.onNormalContainer
                    ),
                    border = BorderStroke(1.dp, semantic.normal)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = semantic.normal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar Señal 4G",
                        style = MaterialTheme.typography.labelLarge,
                        color = semantic.onNormalContainer
                    )
                }
            }

            // Banner Superior de Alertas Activas (Foco Ígneo / Obstáculo / Batería Baja)
            telemetry.activeAlerts.forEach { alert ->
                val isCritical = alert.severity == AlertSeverity.CRITICAL
                val bg = if (isCritical) semantic.criticalContainer else semantic.warningContainer
                val bd = if (isCritical) semantic.critical else semantic.warning
                val fg = if (isCritical) semantic.onCriticalContainer else semantic.onWarningContainer

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    border = BorderStroke(if (isCritical) 2.dp else 1.5.dp, bd)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (alert.category == AlertCategory.FIRE) {
                                    Icons.Rounded.LocalFireDepartment
                                } else {
                                    Icons.Rounded.WarningAmber
                                },
                                contentDescription = null,
                                tint = bd,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (alert.isAcknowledged) {
                                    "${alert.title} (ACK RECONOCIDA)"
                                } else {
                                    alert.title
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = fg,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = alert.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = fg
                        )
                        if (!alert.isAcknowledged) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onCommandRequested(RobotCommandType.ACK_ALERT, alert.id)
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = bd,
                                        contentColor = if (isCritical) {
                                            MaterialTheme.colorScheme.onError
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary
                                        }
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Reconocer Alerta (ACK)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (alert.category == AlertCategory.BATTERY && perms.canReturnToSafe) {
                                    FilledTonalButton(
                                        onClick = { onCommandRequested(RobotCommandType.RETURN_TO_SAFE, null) },
                                        modifier = Modifier.heightIn(min = 40.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Ir a RTH (WP0)",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ZONA B: Tarjeta de Mapa Táctico y Misión
            TacticalMapCard(
                telemetry = telemetry,
                isDataStale = isStale,
                secondsSinceLastUpdate = uiState.secondsSinceLastUpdate
            )

            // ZONA C: Grilla de las 3 Variables de Servicio + Barra HRI
            val tempState = when {
                isStale -> HmiVisualState.WARNING
                telemetry.maxThermalTempC >= 70.0f -> HmiVisualState.CRITICAL
                else -> HmiVisualState.NORMAL
            }
            val smokeState = when {
                isStale -> HmiVisualState.WARNING
                telemetry.smokePpm >= 200 -> HmiVisualState.CRITICAL
                else -> HmiVisualState.NORMAL
            }
            val riskState = when {
                isStale -> HmiVisualState.WARNING
                telemetry.fireWeatherRisk == FireRiskLevel.EXTREMO -> HmiVisualState.CRITICAL
                telemetry.fireWeatherRisk == FireRiskLevel.ALTO -> HmiVisualState.WARNING
                else -> HmiVisualState.NORMAL
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryVariableCard(
                    title = "Máx. IR",
                    valueText = String.format(Locale.US, "%.1f", telemetry.maxThermalTempC),
                    unitText = "°C",
                    subtitle = if (isStale) "CONGELADO" else "Umbral: 70 °C",
                    icon = Icons.Rounded.Thermostat,
                    visualState = tempState,
                    modifier = Modifier.weight(1f)
                )
                TelemetryVariableCard(
                    title = "Humo",
                    valueText = telemetry.smokePpm.toString(),
                    unitText = "ppm",
                    subtitle = if (isStale) "CONGELADO" else "CO / PM2.5",
                    icon = Icons.Rounded.Cloud,
                    visualState = smokeState,
                    modifier = Modifier.weight(1f)
                )
                TelemetryVariableCard(
                    title = "Índ. Ígneo",
                    valueText = if (isStale) "CONGELADO" else telemetry.fireWeatherRisk.label,
                    unitText = "",
                    subtitle = "${telemetry.windSpeedKmh}km/h ${telemetry.windDirection} | ${telemetry.humidityPercent}%",
                    icon = Icons.Rounded.Air,
                    visualState = riskState,
                    isCompactTextValue = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HriTrailStatusBar(
                hriState = telemetry.hriStatus,
                isDataStale = isStale
            )

            // ZONA D: Barra de Estado del Comando Reciente (Feedback Asíncrono)
            CommandFeedbackBanner(report = uiState.lastCommandStatus)

            // ZONA E: Modo Defensa / Simulador de Escenarios (1 a 5)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MODO DEFENSA — ESCENARIOS (1 A 5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { showSimulatorSheet = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Simulador (1–5)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimulationScenario.entries.forEach { scenario ->
                        val selected = scenario == uiState.activeScenario
                        FilterChip(
                            selected = selected,
                            onClick = { onTriggerScenario(scenario) },
                            label = {
                                Text(
                                    text = "E${scenario.number}",
                                    style = TelemetryTextStyles.labelMono
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // ZONA E (cont.): Panel de Comandos Discretos (2x3 + Detención Operativa)
            Text(
                text = "CONTROL DISCRETO (SIN JOYSTICK)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCommandRequested(RobotCommandType.START_MISSION, null) },
                    enabled = perms.canStartMission,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = semantic.disabledSurface,
                        disabledContentColor = semantic.disabledContent
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Iniciar Misión", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                FilledTonalButton(
                    onClick = { onCommandRequested(RobotCommandType.PAUSE_MISSION, null) },
                    enabled = perms.canPauseMission,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        disabledContainerColor = semantic.disabledSurface,
                        disabledContentColor = semantic.disabledContent
                    )
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pausar Misión", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onCommandRequested(RobotCommandType.RESUME_MISSION, null) },
                    enabled = perms.canResumeMission,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        disabledContainerColor = semantic.disabledSurface,
                        disabledContentColor = semantic.disabledContent
                    )
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reanudar Misión", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = { onCommandRequested(RobotCommandType.CANCEL_MISSION, null) },
                    enabled = perms.canCancelMission,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = semantic.disabledContent
                    )
                ) {
                    Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancelar Misión", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onCommandRequested(RobotCommandType.RETURN_TO_SAFE, null) },
                    enabled = perms.canReturnToSafe,
                    modifier = Modifier
                        .weight(1.35f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (perms.highlightRth) semantic.warningContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (perms.highlightRth) semantic.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = semantic.disabledSurface,
                        disabledContentColor = semantic.disabledContent
                    ),
                    border = if (perms.canReturnToSafe) {
                        BorderStroke(
                            width = if (perms.highlightRth) 2.dp else 1.dp,
                            color = if (perms.highlightRth) semantic.warning else MaterialTheme.colorScheme.primary
                        )
                    } else null
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Regreso Seguro (RTH)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = {
                        val firstUnacked = telemetry.activeAlerts.firstOrNull { !it.isAcknowledged }?.id
                        onCommandRequested(RobotCommandType.ACK_ALERT, firstUnacked)
                    },
                    enabled = perms.canAckAlert,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = semantic.disabledContent
                    )
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ACK Alerta", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Detención Operativa (Ancho completo, Color error + Advertencia reglamentaria obligatoria)
            Button(
                onClick = { onCommandRequested(RobotCommandType.OPERATIONAL_STOP, null) },
                enabled = perms.canOperationalStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = semantic.disabledSurface,
                    disabledContentColor = semantic.disabledContent
                )
            ) {
                Icon(Icons.Rounded.Report, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Detención Operativa", style = MaterialTheme.typography.labelLarge)
            }

            Text(
                text = "Atención: La detención desde la app es un comando de software simulado; no reemplaza una parada de emergencia física (E-Stop). Una orden enviada no equivale a una orden ejecutada.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    // Diálogo M3 de confirmación para comandos críticos (Cancelar Misión / Detención Operativa)
    val confirmCmd = uiState.pendingConfirmationCommand
    if (confirmCmd != null) {
        AlertDialog(
            onDismissRequest = onDismissPendingConfirmation,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Confirmar ${confirmCmd.label}")
            },
            text = {
                Text(
                    text = if (confirmCmd == RobotCommandType.OPERATIONAL_STOP) {
                        "Se enviará la orden de detención operativa por software al Unitree Go2 EDU #01. Recuerde que esto no sustituye un E-Stop físico."
                    } else {
                        "Se cancelará la misión activa ${telemetry.missionId} y el robot quedará en espera en el waypoint actual."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmPendingCommand,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Confirmar orden")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPendingConfirmation) {
                    Text("Volver")
                }
            }
        )
    }

    // AlertDialog M3 Crítico (Escenario 5 Foco Ígneo / Escenario 4 Obstáculo)
    val modalAlert = uiState.criticalModalAlert
    if (modalAlert != null) {
        val isFire = modalAlert.category == AlertCategory.FIRE || telemetry.maxThermalTempC >= 70f
        AlertDialog(
            onDismissRequest = { onDismissCriticalAlertDialog(modalAlert.id) },
            containerColor = semantic.criticalContainer,
            titleContentColor = semantic.onCriticalContainer,
            textContentColor = semantic.onCriticalContainer,
            icon = {
                Icon(
                    imageVector = if (isFire) Icons.Rounded.LocalFireDepartment else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = semantic.critical,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = modalAlert.title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = modalAlert.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            onCommandRequested(RobotCommandType.ACK_ALERT, modalAlert.id)
                            onDismissCriticalAlertDialog(modalAlert.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = semantic.critical,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Reconocer Alerta (ACK)")
                    }
                    FilledTonalButton(
                        onClick = {
                            onDismissCriticalAlertDialog(modalAlert.id)
                            onCommandRequested(RobotCommandType.RETURN_TO_SAFE, null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("Solicitar Regreso a Punto Seguro (RTH)")
                    }
                    OutlinedButton(
                        onClick = {
                            onDismissCriticalAlertDialog(modalAlert.id)
                            onCommandRequested(RobotCommandType.RESUME_MISSION, null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, semantic.critical),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = semantic.onCriticalContainer
                        )
                    ) {
                        Text(
                            text = if (isFire) {
                                "Reanudar (Bloqueado por protección térmica)"
                            } else {
                                "Reanudar Misión (Desvío lateral)"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showSimulatorSheet) {
        ScenarioSimulatorBottomSheet(
            activeScenario = uiState.activeScenario,
            isForcedDisconnected = uiState.isForcedDisconnected,
            isForceRejectNextCommand = uiState.isForceRejectNextCommand,
            onSelectScenario = onTriggerScenario,
            onToggleDisconnected = onToggleForcedDisconnection,
            onToggleForceReject = onToggleForceRejectNextCommand,
            onDismiss = { showSimulatorSheet = false }
        )
    }
}
