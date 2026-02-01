package local.oss.chronicle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Opus - Audiobook Player
 * Brand colors and theme
 */
object OpusColors {
    // Core palette
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFF2A2A2A)

    // Primary - Amber
    val Primary = Color(0xFFFFAB40)
    val PrimaryDark = Color(0xFFFF8F00)
    val PrimaryLight = Color(0xFFFFD180)

    // Secondary - Soft Green (for positive states like downloaded, completed)
    val Secondary = Color(0xFF81C784)
    val SecondaryDark = Color(0xFF4CAF50)

    // Text colors
    val TextPrimary = Color(0xFFFAFAFA)
    val TextSecondary = Color(0x99FAFAFA) // 60% opacity
    val TextTertiary = Color(0x66FAFAFA) // 40% opacity
    val TextDisabled = Color(0x4DFAFAFA) // 30% opacity

    // Playback controls
    val PlayButtonBackground = Primary
    val ControlsBackground = Color(0x33FAFAFA) // 20% white
    val SliderTrack = Color(0x4DFAFAFA) // 30% white
    val SliderProgress = Primary

    // Status colors
    val SleepTimerActive = Color(0xFFFF9800) // Orange
    val Downloaded = Secondary
    val Error = Color(0xFFEF5350)
    val Buffering = Color(0xFF64B5F6)

    // Dividers / separators
    val Divider = Color(0x1AFFFFFF) // 10% white - very subtle
    val DividerStrong = Color(0x33FFFFFF) // 20% white - slightly more visible
}

private val DarkColorScheme = darkColorScheme(
    primary = OpusColors.Primary,
    onPrimary = OpusColors.Background,
    primaryContainer = OpusColors.PrimaryDark,
    onPrimaryContainer = OpusColors.TextPrimary,
    secondary = OpusColors.Secondary,
    onSecondary = OpusColors.Background,
    secondaryContainer = OpusColors.SecondaryDark,
    onSecondaryContainer = OpusColors.TextPrimary,
    tertiary = OpusColors.PrimaryLight,
    background = OpusColors.Background,
    onBackground = OpusColors.TextPrimary,
    surface = OpusColors.Surface,
    onSurface = OpusColors.TextPrimary,
    surfaceVariant = OpusColors.SurfaceVariant,
    onSurfaceVariant = OpusColors.TextSecondary,
    error = OpusColors.Error,
    onError = OpusColors.TextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = OpusColors.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFFE65100),
    secondary = OpusColors.SecondaryDark,
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF666666),
)

/**
 * Extended color palette for Opus-specific UI elements.
 */
data class OpusColorPalette(
    val playButtonBackground: Color = OpusColors.PlayButtonBackground,
    val controlsBackground: Color = OpusColors.ControlsBackground,
    val sliderTrack: Color = OpusColors.SliderTrack,
    val sliderProgress: Color = OpusColors.SliderProgress,
    val sleepTimerActive: Color = OpusColors.SleepTimerActive,
    val downloaded: Color = OpusColors.Downloaded,
    val textTertiary: Color = OpusColors.TextTertiary,
    val textDisabled: Color = OpusColors.TextDisabled,
    val buffering: Color = OpusColors.Buffering,
)

val LocalOpusColors = staticCompositionLocalOf { OpusColorPalette() }

/**
 * Opus theme for the audiobook player.
 * Dark theme by default - easier on eyes during long listening sessions.
 */
@Composable
fun OpusTheme(
    darkTheme: Boolean = true, // Default to dark for audiobook experience
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val opusColors = OpusColorPalette()

    CompositionLocalProvider(LocalOpusColors provides opusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

/**
 * Access Opus-specific colors from Composables.
 */
object OpusTheme {
    val colors: OpusColorPalette
        @Composable
        get() = LocalOpusColors.current
}
