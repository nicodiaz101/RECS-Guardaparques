package ar.gob.buenosaires.recs.go2hmi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandExecutionState
import ar.gob.buenosaires.recs.go2hmi.domain.model.CommandStatusReport
import ar.gob.buenosaires.recs.go2hmi.domain.model.HriState
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.SimulationScenario
import ar.gob.buenosaires.recs.go2hmi.ui.theme.LocalHmiSemanticColors
import ar.gob.buenosaires.recs.go2hmi.ui.theme.TelemetryTextStyles

enum class HmiVisualState {
    NORMAL,
    DISABLED,
    WARNING,
    CRITICAL
}

fun Modifier.dashedAmberBorder(
    enabled: Boolean,
    color: Color,
    cornerRadiusDp: Float = 12f
): Modifier = if (!enabled) {
    this
} else {
    this.drawWithContent {
        drawContent()
        val strokePx = 1.5.dp.toPx()
        val radiusPx = cornerRadiusDp.dp.toPx()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(
                width = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )
        )
    }
}

@Composable
fun OperationalStateBadge(
    state: RobotOperationalState,
    isStale: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current
    val (bg, border, fg, label) = when {
        isStale -> Quad(semantic.warningContainer, semantic.warning, semantic.warning, "SIN ENLACE")
        state == RobotOperationalState.ALERT_HOLD || state == RobotOperationalState.STOPPED ->
            Quad(semantic.criticalContainer, semantic.critical, semantic.critical, state.name)
        state == RobotOperationalState.PAUSED ->
            Quad(semantic.warningContainer, semantic.warning, semantic.warning, state.name)
        state == RobotOperationalState.PATROLLING || state == RobotOperationalState.RETURNING_SAFE ->
            Quad(semantic.normalContainer, semantic.normal, semantic.normal, state.name)
        else ->
            Quad(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, state.name)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(if (state == RobotOperationalState.ALERT_HOLD) 1.5.dp else 1.dp, border)
    ) {
        Text(
            text = label,
            style = TelemetryTextStyles.labelMono,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TelemetryVariableCard(
    title: String,
    valueText: String,
    unitText: String,
    subtitle: String,
    icon: ImageVector,
    visualState: HmiVisualState,
    isCompactTextValue: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current
    val isStale = visualState == HmiVisualState.WARNING

    val (bgColor, borderColor, borderWidth, accentColor, valueColor, subColor) = when (visualState) {
        HmiVisualState.NORMAL -> SixColors(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.outlineVariant,
            1.dp,
            semantic.normal,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        HmiVisualState.DISABLED -> SixColors(
            semantic.disabledSurface,
            MaterialTheme.colorScheme.outlineVariant,
            1.dp,
            semantic.disabledContent,
            semantic.disabledContent,
            semantic.disabledContent
        )
        HmiVisualState.WARNING -> SixColors(
            semantic.warningContainer,
            Color.Transparent,
            0.dp,
            semantic.warning,
            semantic.onWarningContainer,
            semantic.warning
        )
        HmiVisualState.CRITICAL -> SixColors(
            semantic.criticalContainer,
            semantic.critical,
            2.dp,
            semantic.critical,
            semantic.onCriticalContainer,
            semantic.onCriticalContainer
        )
    }

    ElevatedCard(
        modifier = modifier
            .alpha(if (isStale) 0.65f else 1f)
            .dashedAmberBorder(enabled = isStale, color = semantic.warning, cornerRadiusDp = 12f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (visualState == HmiVisualState.NORMAL) 2.dp else 0.dp)
    ) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = valueText,
                        style = if (isCompactTextValue) {
                            TelemetryTextStyles.bodyMono.copy(fontSize = 14.sp)
                        } else {
                            TelemetryTextStyles.headlineMono.copy(fontSize = 21.sp, lineHeight = 26.sp)
                        },
                        color = valueColor,
                        maxLines = 1
                    )
                    if (unitText.isNotEmpty()) {
                        Text(
                            text = unitText,
                            style = TelemetryTextStyles.labelMono,
                            color = accentColor,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HriTrailStatusBar(
    hriState: HriState,
    isDataStale: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current
    val (bg, border, sw, fg) = when {
        isDataStale -> Quad(semantic.warningContainer, semantic.warning, 0.dp, semantic.onWarningContainer)
        hriState.isEmergencyAlarm -> Quad(semantic.criticalContainer, semantic.critical, 2.dp, semantic.onCriticalContainer)
        else -> Quad(semantic.normalContainer, semantic.normal, 1.dp, semantic.onNormalContainer)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDataStale) 0.65f else 1f)
            .dashedAmberBorder(enabled = isDataStale, color = semantic.warning, cornerRadiusDp = 10f),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = if (sw > 0.dp) BorderStroke(sw, border) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Estado HRI",
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isDataStale) {
                    "HRI (DATO ANTIGUO): ${hriState.beaconMode} | ${hriState.speakerAnnouncement}"
                } else {
                    "HRI en Sendero: ${hriState.beaconMode} | ${hriState.speakerAnnouncement}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CommandFeedbackBanner(
    report: CommandStatusReport,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current
    val (bg, border, sw, fg, icon) = when (report.state) {
        CommandExecutionState.IDLE -> FiveBanner(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.outlineVariant,
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.Schedule
        )
        CommandExecutionState.PENDING -> FiveBanner(
            semantic.warningContainer,
            semantic.warning,
            1.5.dp,
            semantic.onWarningContainer,
            Icons.Rounded.Schedule
        )
        CommandExecutionState.CONFIRMED -> FiveBanner(
            semantic.normalContainer,
            semantic.normal,
            1.dp,
            semantic.onNormalContainer,
            Icons.Rounded.CheckCircle
        )
        CommandExecutionState.REJECTED -> FiveBanner(
            semantic.criticalContainer,
            semantic.critical,
            2.dp,
            semantic.onCriticalContainer,
            Icons.Rounded.Close
        )
        CommandExecutionState.NO_RESPONSE -> FiveBanner(
            semantic.warningContainer,
            semantic.warning,
            1.5.dp,
            semantic.onWarningContainer,
            Icons.Rounded.WarningAmber
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(sw, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (report.state == CommandExecutionState.PENDING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = semantic.warning
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = report.state.name,
                    tint = fg,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = report.message,
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioSimulatorBottomSheet(
    activeScenario: SimulationScenario,
    isForcedDisconnected: Boolean,
    isForceRejectNextCommand: Boolean,
    onSelectScenario: (SimulationScenario) -> Unit,
    onToggleDisconnected: (Boolean) -> Unit,
    onToggleForceReject: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = LocalHmiSemanticColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Simulador de Escenarios — Cátedra (1 a 5)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Disparo local offline mediante contratos JSON en Go2SimulationEngine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SimulationScenario.entries.forEach { scenario ->
                val isSelected = scenario == activeScenario
                val (badgeBg, accentColor, icon) = when (scenario) {
                    SimulationScenario.SCENARIO_1_NORMAL -> Triple(semantic.normalContainer, semantic.normal, Icons.Rounded.PlayArrow)
                    SimulationScenario.SCENARIO_2_LOW_BATTERY -> Triple(semantic.warningContainer, semantic.warning, Icons.Rounded.BatteryAlert)
                    SimulationScenario.SCENARIO_3_DISCONNECTION -> Triple(semantic.warningContainer, semantic.warning, Icons.Rounded.WifiOff)
                    SimulationScenario.SCENARIO_4_OBSTACLE -> Triple(semantic.warningContainer, semantic.warning, Icons.Rounded.WarningAmber)
                    SimulationScenario.SCENARIO_5_FIRE_ALERT -> Triple(semantic.criticalContainer, semantic.critical, Icons.Rounded.LocalFireDepartment)
                }

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectScenario(scenario)
                            onDismiss()
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(badgeBg, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = scenario.title,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = scenario.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = scenario.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cortar señal 4G manualmente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isForcedDisconnected,
                            onCheckedChange = onToggleDisconnected
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rechazar próxima orden (Falla ACK)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isForceRejectNextCommand,
                            onCheckedChange = onToggleForceReject
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class FiveBanner<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private data class SixColors<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
