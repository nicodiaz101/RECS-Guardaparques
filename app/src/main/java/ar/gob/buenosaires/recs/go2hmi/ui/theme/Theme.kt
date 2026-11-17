package ar.gob.buenosaires.recs.go2hmi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class HmiSemanticColors(
    val normal: Color,
    val normalContainer: Color,
    val onNormalContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val critical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
    val disabledSurface: Color,
    val disabledContent: Color
)

val TacticalHmiSemanticColors = HmiSemanticColors(
    normal = SemanticNormal,
    normalContainer = SemanticNormalContainer,
    onNormalContainer = SemanticOnNormalContainer,
    warning = SemanticWarning,
    warningContainer = SemanticWarningContainer,
    onWarningContainer = SemanticOnWarningContainer,
    critical = TacticalError,
    criticalContainer = TacticalErrorContainer,
    onCriticalContainer = TacticalOnErrorContainer,
    disabledSurface = TacticalOnSurface.copy(alpha = 0.12f),
    disabledContent = TacticalOnSurface.copy(alpha = 0.38f)
)

val LocalHmiSemanticColors = staticCompositionLocalOf { TacticalHmiSemanticColors }

private val TacticalDarkColorScheme = darkColorScheme(
    primary = TacticalPrimary,
    onPrimary = TacticalOnPrimary,
    primaryContainer = TacticalPrimaryContainer,
    onPrimaryContainer = TacticalOnPrimaryContainer,
    secondary = TacticalPrimary,
    onSecondary = TacticalOnPrimary,
    secondaryContainer = TacticalSecondaryContainer,
    onSecondaryContainer = TacticalOnSecondaryContainer,
    background = TacticalBackground,
    onBackground = TacticalOnSurface,
    surface = TacticalSurface,
    onSurface = TacticalOnSurface,
    surfaceVariant = TacticalSurfaceContainer,
    onSurfaceVariant = TacticalOnSurfaceVariant,
    surfaceContainer = TacticalSurfaceContainer,
    surfaceContainerLow = TacticalSurface,
    surfaceContainerHigh = TacticalSurfaceContainerHigh,
    outline = TacticalOutline,
    outlineVariant = TacticalOutlineVariant,
    error = TacticalError,
    onError = TacticalOnError,
    errorContainer = TacticalErrorContainer,
    onErrorContainer = TacticalOnErrorContainer
)

/**
 * Tema estático Material 3 "Tactical Outdoor Dark M3".
 * dynamicColor está explícitamente deshabilitado (dynamicColor = false)
 * para evitar extracción de colores del wallpaper del teléfono.
 */
@Composable
fun Go2HmiTheme(
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Seguridad HMI: jamás usar dynamicDarkColorScheme aunque se invoque con otro valor.
    CompositionLocalProvider(LocalHmiSemanticColors provides TacticalHmiSemanticColors) {
        MaterialTheme(
            colorScheme = TacticalDarkColorScheme,
            typography = TacticalTypography,
            content = content
        )
    }
}
