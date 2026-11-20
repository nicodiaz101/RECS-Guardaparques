package ar.gob.buenosaires.recs.go2hmi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.gob.buenosaires.recs.go2hmi.domain.model.RecsTrailWaypoints
import ar.gob.buenosaires.recs.go2hmi.domain.model.RobotOperationalState
import ar.gob.buenosaires.recs.go2hmi.domain.model.TelemetrySnapshot
import ar.gob.buenosaires.recs.go2hmi.domain.policy.RobotSafetyPolicy
import ar.gob.buenosaires.recs.go2hmi.ui.theme.LocalHmiSemanticColors
import ar.gob.buenosaires.recs.go2hmi.ui.theme.MapLandBackground
import ar.gob.buenosaires.recs.go2hmi.ui.theme.MapRiverFill
import ar.gob.buenosaires.recs.go2hmi.ui.theme.MapWaterBorder
import ar.gob.buenosaires.recs.go2hmi.ui.theme.MapWaterFill
import ar.gob.buenosaires.recs.go2hmi.ui.theme.TelemetryTextStyles
import java.util.Locale

/**
 * Mapa táctico vectorial en Compose del circuito Laguna de los Coipos (3.8 km, WP0–WP5).
 *
 * Representacion de forma esquemática el espejo de agua, el Río de la Plata al Este, el trazo
 * recorrido, el halo de foco térmico cuando la IR supera [RobotSafetyPolicy.THERMAL_LOCKOUT_TEMP_C]
 * y el indicador visual de datos antiguos ("CONGELADO") ante desconexión.
 */
@Composable
fun TacticalMapCard(
    telemetry: TelemetrySnapshot,
    isDataStale: Boolean,
    secondsSinceLastUpdate: Long,
    modifier: Modifier = Modifier
) {
    val semantic = LocalHmiSemanticColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hasFire = telemetry.maxThermalTempC >= RobotSafetyPolicy.THERMAL_LOCKOUT_TEMP_C
    val textMeasurer = rememberTextMeasurer()

    val borderColor = when {
        isDataStale -> Color.Transparent
        hasFire -> semantic.critical
        telemetry.missionProgressPercent >= 100 -> semantic.normal
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (hasFire && !isDataStale) 2.dp else 1.dp

    val trailColor = when {
        isDataStale -> semantic.warning
        hasFire -> semantic.critical
        telemetry.missionProgressPercent >= 100 -> semantic.normal
        else -> MaterialTheme.colorScheme.primary
    }

    val gpsSummary = String.format(
        Locale.US,
        "%.4f, %.4f • %.1f m/s",
        telemetry.latitude,
        telemetry.longitude,
        telemetry.linearSpeedMps
    )

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDataStale) 0.65f else 1f)
            .dashedAmberBorder(enabled = isDataStale, color = semantic.warning, cornerRadiusDp = 12f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (!isDataStale) BorderStroke(borderWidth, borderColor) else BorderStroke(0.dp, Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Laguna de los Coipos (3.8 km)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (isDataStale) "CONGELADO (${secondsSinceLastUpdate}s)" else gpsSummary,
                    style = TelemetryTextStyles.labelMono.copy(fontSize = 10.sp),
                    color = if (isDataStale) semantic.warning else MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MapLandBackground)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Grilla táctica
                    val gridColor = Color(0xFF1E293B)
                    for (i in 1..3) {
                        val y = h * (i / 4f)
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        val x = w * (i / 4f)
                        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    }

                    // Río de la Plata al Este
                    val riverPath = Path().apply {
                        moveTo(w * 0.90f, 0f)
                        cubicTo(w * 0.94f, h * 0.35f, w * 0.96f, h * 0.70f, w * 0.91f, h)
                        lineTo(w, h)
                        lineTo(w, 0f)
                        close()
                    }
                    drawPath(riverPath, color = MapRiverFill)

                    // Laguna de los Coipos
                    val lagoonPath = Path().apply {
                        moveTo(w * 0.31f, h * 0.42f)
                        cubicTo(w * 0.40f, h * 0.30f, w * 0.60f, h * 0.34f, w * 0.67f, h * 0.47f)
                        cubicTo(w * 0.71f, h * 0.58f, w * 0.63f, h * 0.73f, w * 0.50f, h * 0.74f)
                        cubicTo(w * 0.35f, h * 0.75f, w * 0.25f, h * 0.58f, w * 0.31f, h * 0.42f)
                        close()
                    }
                    drawPath(lagoonPath, color = MapWaterFill)
                    drawPath(lagoonPath, color = MapWaterBorder, style = Stroke(width = 1.5f))

                    // Normalización de coordenadas reales de los 6 waypoints a posiciones del mapa
                    val wpCanvasPoints = listOf(
                        Offset(w * 0.13f, h * 0.78f), // WP0 Base Acceso Brasil
                        Offset(w * 0.19f, h * 0.32f), // WP1 Sendero de los Sauces
                        Offset(w * 0.48f, h * 0.18f), // WP2 Mirador Norte
                        Offset(w * 0.80f, h * 0.35f), // WP3 Cortafuegos Este
                        Offset(w * 0.86f, h * 0.76f), // WP4 Camino Costero
                        Offset(w * 0.52f, h * 0.84f)  // WP5 Sendero Retorno Sur
                    )

                    // Circuito cerrado punteado
                    val fullLoop = Path().apply {
                        moveTo(wpCanvasPoints[0].x, wpCanvasPoints[0].y)
                        for (i in 1 until wpCanvasPoints.size) {
                            lineTo(wpCanvasPoints[i].x, wpCanvasPoints[i].y)
                        }
                        close()
                    }
                    drawPath(
                        path = fullLoop,
                        color = Color(0xFF2B3642),
                        style = Stroke(
                            width = 4f,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                    )

                    // Posición interpolada del robot sobre el circuito según progreso o WP actual
                    val progressRatio = (telemetry.missionProgressPercent.coerceIn(0, 100) / 100f)
                    val totalSegs = wpCanvasPoints.size
                    val scaled = progressRatio * totalSegs
                    val segIdx = scaled.toInt().coerceIn(0, totalSegs - 1)
                    val segFrac = scaled - segIdx
                    val robotPoint = if (telemetry.missionProgressPercent >= 100) {
                        wpCanvasPoints[0]
                    } else if (telemetry.missionProgressPercent == 0) {
                        wpCanvasPoints[telemetry.currentWaypointIndex.coerceIn(0, totalSegs - 1)]
                    } else {
                        val a = wpCanvasPoints[segIdx]
                        val b = wpCanvasPoints[(segIdx + 1) % totalSegs]
                        Offset(a.x + (b.x - a.x) * segFrac, a.y + (b.y - a.y) * segFrac)
                    }

                    // Tramo recorrido activo
                    if (telemetry.missionProgressPercent > 0) {
                        val activePath = Path().apply {
                            moveTo(wpCanvasPoints[0].x, wpCanvasPoints[0].y)
                            if (telemetry.missionProgressPercent >= 100) {
                                for (i in 1 until wpCanvasPoints.size) {
                                    lineTo(wpCanvasPoints[i].x, wpCanvasPoints[i].y)
                                }
                                close()
                            } else {
                                for (i in 1..segIdx) {
                                    lineTo(wpCanvasPoints[i].x, wpCanvasPoints[i].y)
                                }
                                lineTo(robotPoint.x, robotPoint.y)
                            }
                        }
                        drawPath(
                            path = activePath,
                            color = trailColor,
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Halo de fuego pulsante en WP3 si hay foco ígneo activo
                    if (hasFire) {
                        val wp3 = wpCanvasPoints[3]
                        drawCircle(
                            color = semantic.critical.copy(alpha = 0.25f),
                            radius = 24f,
                            center = wp3
                        )
                        drawCircle(
                            color = semantic.critical,
                            radius = 16f,
                            center = wp3,
                            style = Stroke(width = 2.5f)
                        )
                    }

                    // Dibujar los 6 waypoints y sus etiquetas
                    val labelStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp
                    )
                    RecsTrailWaypoints.sectorSurWaypoints.forEachIndexed { idx, wp ->
                        val pt = wpCanvasPoints[idx]
                        val nodeColor = when {
                            idx == 3 && hasFire -> semantic.critical
                            idx == 0 -> semantic.normal
                            idx == telemetry.currentWaypointIndex -> primaryColor
                            else -> onSurfaceVariantColor
                        }
                        drawCircle(color = Color(0xFF0F1419), radius = 6.5f, center = pt)
                        drawCircle(color = nodeColor, radius = 4.5f, center = pt)

                        val measured = textMeasurer.measure(wp.shortLabel, style = labelStyle)
                        val labelOffset = when (idx) {
                            0 -> Offset((pt.x - 16f).coerceAtLeast(4f), (pt.y + 8f).coerceAtMost(h - measured.size.height - 2f))
                            1 -> Offset(pt.x - 10f, (pt.y - measured.size.height - 6f).coerceAtLeast(2f))
                            2 -> Offset(pt.x - measured.size.width / 2f, (pt.y - measured.size.height - 5f).coerceAtLeast(2f))
                            3 -> Offset((pt.x - measured.size.width * 0.65f), pt.y + 8f)
                            4 -> Offset((pt.x - measured.size.width * 0.85f), (pt.y + 8f).coerceAtMost(h - measured.size.height - 2f))
                            else -> Offset(pt.x - measured.size.width / 2f, (pt.y + 7f).coerceAtMost(h - measured.size.height - 2f))
                        }
                        drawText(
                            textLayoutResult = measured,
                            color = if (idx == 3 && hasFire) semantic.critical else if (idx == 0) semantic.normal else Color(0xFFCBD5E1),
                            topLeft = labelOffset
                        )
                    }

                    // Pin del Robot Unitree Go2 EDU
                    val pinColor = when {
                        isDataStale -> semantic.warning
                        telemetry.operationalState == RobotOperationalState.ALERT_HOLD -> semantic.critical
                        else -> primaryColor
                    }
                    drawCircle(color = pinColor.copy(alpha = 0.28f), radius = 14f, center = robotPoint)
                    drawCircle(color = Color(0xFF0F1419), radius = 7.5f, center = robotPoint)
                    drawCircle(color = pinColor, radius = 5.5f, center = robotPoint)
                }

                if (isDataStale) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = semantic.warningContainer,
                        border = BorderStroke(1.dp, semantic.warning)
                    ) {
                        Text(
                            text = "DATO ANTIGUO (CONGELADO)",
                            style = TelemetryTextStyles.labelMono.copy(fontSize = 9.sp),
                            color = semantic.onWarningContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            val nextWpCode = RecsTrailWaypoints.byIndex((telemetry.currentWaypointIndex + 1) % 6).code
            val missionStatusText = when {
                isDataStale -> "DATO ANTIGUO (CONGELADO) • Última pos. ${RecsTrailWaypoints.byIndex(telemetry.currentWaypointIndex).code}"
                telemetry.operationalState == RobotOperationalState.ALERT_HOLD ->
                    "ALERT_HOLD — ${telemetry.posture}"
                telemetry.operationalState == RobotOperationalState.RETURNING_SAFE ->
                    "${telemetry.missionId} — Retornando a WP0 Punto Seguro"
                telemetry.missionProgressPercent >= 100 ->
                    "${telemetry.missionId} — 100% completado — En WP0 Base"
                else ->
                    "${telemetry.missionId} — ${telemetry.missionProgressPercent}% completado — Rumbo a $nextWpCode"
            }

            Text(
                text = missionStatusText,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isDataStale -> semantic.warning
                    hasFire -> semantic.critical
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = { telemetry.missionProgressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = trailColor,
                trackColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    }
}
