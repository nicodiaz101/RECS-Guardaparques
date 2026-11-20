package ar.gob.buenosaires.recs.go2hmi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.gob.buenosaires.recs.go2hmi.domain.model.ConnectivityStatus
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.policy.RobotSafetyPolicy
import ar.gob.buenosaires.recs.go2hmi.ui.theme.LocalHmiSemanticColors
import ar.gob.buenosaires.recs.go2hmi.ui.theme.TelemetryTextStyles

/**
 * Pantalla 1: Selección de Unidades Cuadrúpedas asignadas a la RECS.
 *
 * Muestra la unidad activa (`GO2-RECS-01`) reflejando su estado real de enlace,
 * batería y waypoint actual, junto a una segunda unidad de reserva en estado
 * deshabilitado estándar Material 3 (opacidad 12%/38%) para evidenciar precondiciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotSelectionScreen(
    telemetry: TelemetrySnapshot,
    isDataStale: Boolean = false,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Guardaparques RECS — Patrullaje Robótico",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Unidades cuadrúpedas asignadas • Reserva Ecológica Costanera Sur",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Tarjeta 1: Unitree Go2 EDU #01 (Activa / Seleccionable)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = "Unitree Go2 EDU #01",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Unitree Go2 EDU #01 (Patrulla Sector Sur)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cámara Térmica IR + Sensor Humo/CO • LiDAR 4D",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val linkLost = isDataStale || telemetry.connectivityStatus != ConnectivityStatus.ONLINE
                        val linkColor = if (linkLost) semantic.warning else semantic.normal
                        val linkContainer = if (linkLost) semantic.warningContainer else semantic.normalContainer
                        val onLinkContainer = if (linkLost) semantic.onWarningContainer else semantic.onNormalContainer
                        val linkLabel = when {
                            telemetry.connectivityStatus == ConnectivityStatus.DISCONNECTED ->
                                "SIN SEÑAL 4G (${telemetry.rssiDbm} dBm)"
                            isDataStale || telemetry.connectivityStatus == ConnectivityStatus.STALE_DATA ->
                                "DATOS ANTIGUOS (4G ${telemetry.rssiDbm} dBm)"
                            else -> "ONLINE (4G LTE ${telemetry.rssiDbm} dBm)"
                        }

                        AssistChip(
                            onClick = onOpenConsole,
                            label = {
                                Text(
                                    text = linkLabel,
                                    style = TelemetryTextStyles.labelMono
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (linkLost) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                                    contentDescription = "Estado enlace 4G LTE",
                                    tint = linkColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = linkContainer,
                                labelColor = onLinkContainer
                            ),
                            border = BorderStroke(1.dp, linkColor)
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            val summaryColor = when {
                                telemetry.operationalState == RobotOperationalState.ALERT_HOLD ||
                                    telemetry.operationalState == RobotOperationalState.STOPPED -> semantic.critical
                                linkLost || telemetry.batteryPercent <= RobotSafetyPolicy.LOW_BATTERY_WARNING_PERCENT ->
                                    semantic.warning
                                else -> semantic.normal
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Batería: ${telemetry.batteryPercent}% (${telemetry.voltageV}V)  •  Estado: ${telemetry.operationalState.name}",
                                    style = TelemetryTextStyles.bodyMono,
                                    color = summaryColor
                                )
                                Text(
                                    text = "Ubicación: ${telemetry.currentWaypointName} • Circuito 3.8 km (${telemetry.missionProgressPercent}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onOpenConsole,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dashboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Conectar y Abrir Consola HMI",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Tarjeta 2: Unitree Go2 EDU #02 (Estado Deshabilitado Estándar M3 12% / 38%)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = "Unitree Go2 EDU #02",
                            tint = semantic.disabledContent,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Unitree Go2 EDU #02 (Reserva Norte)",
                                style = MaterialTheme.typography.titleMedium,
                                color = semantic.disabledContent
                            )
                            Text(
                                text = "Estación de carga solar • Mantenimiento preventivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.disabledContent
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = semantic.disabledSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = semantic.disabledContent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "EN CARGA / MANTENIMIENTO",
                                style = TelemetryTextStyles.labelMono,
                                color = semantic.disabledContent
                            )
                        }
                    }

                    Text(
                        text = "Batería: 34% • En dock de carga. Precondiciones operativas no cumplidas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.disabledContent
                    )

                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = semantic.disabledSurface,
                            disabledContentColor = semantic.disabledContent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Conectar (Unidad No Disponible)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Tarjeta de seguridad operativa
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Consola M3 Estática (dynamicColor = false)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Paleta Tactical Outdoor Dark M3 fija para exteriores. Control exclusivo mediante órdenes discretas (sin joystick) con confirmación asíncrona.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
