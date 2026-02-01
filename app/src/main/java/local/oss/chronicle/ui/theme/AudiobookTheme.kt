package local.oss.chronicle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Chronicle brand colors
object AudiobookColors {
    // Primary palette (dark theme focused)
    val Primary = Color(0xFF2D3043)
    val PrimaryDark = Color(0xFF191A2A)
    val Surface = Color(0xFF0F0F1A)
    val Background = Color(0xFF0F0F1A)

    // Accent
    val Accent = Color(0xFF00B8D4)
    val AccentDark = Color(0xFF0097A7)

    // Text colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF) // 70% white
    val TextTertiary = Color(0x80FFFFFF) // 50% white
    val TextDisabled = Color(0x4DFFFFFF) // 30% white

    // Playback controls
    val PlayButtonBackground = Color(0xFF00B8D4)
    val ControlsBackground = Color(0x33FFFFFF) // 20% white
    val SliderTrack = Color(0x4DFFFFFF) // 30% white
    val SliderProgress = Color(0xFF00B8D4)

    // Status colors
    val SleepTimerActive = Color(0xFFFF9800)
    val Downloaded = Color(0xFF4CAF50)
    val Error = Color(0xFFE53935)
}

private val DarkColorScheme = darkColorScheme(
    primary = AudiobookColors.Accent,
    onPrimary = AudiobookColors.TextPrimary,
    primaryContainer = AudiobookColors.Primary,
    onPrimaryContainer = AudiobookColors.TextPrimary,
    secondary = AudiobookColors.AccentDark,
    onSecondary = AudiobookColors.TextPrimary,
    tertiary = AudiobookColors.Accent,
    background = AudiobookColors.Background,
    onBackground = AudiobookColors.TextPrimary,
    surface = AudiobookColors.Surface,
    onSurface = AudiobookColors.TextPrimary,
    surfaceVariant = AudiobookColors.Primary,
    onSurfaceVariant = AudiobookColors.TextSecondary,
    error = AudiobookColors.Error,
    onError = AudiobookColors.TextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = AudiobookColors.AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF006064),
    secondary = AudiobookColors.Primary,
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF666666),
)

// Custom color palette available via LocalAudiobookColors
data class AudiobookColorPalette(
    val playButtonBackground: Color = AudiobookColors.PlayButtonBackground,
    val controlsBackground: Color = AudiobookColors.ControlsBackground,
    val sliderTrack: Color = AudiobookColors.SliderTrack,
    val sliderProgress: Color = AudiobookColors.SliderProgress,
    val sleepTimerActive: Color = AudiobookColors.SleepTimerActive,
    val downloaded: Color = AudiobookColors.Downloaded,
    val textTertiary: Color = AudiobookColors.TextTertiary,
    val textDisabled: Color = AudiobookColors.TextDisabled,
)

val LocalAudiobookColors = staticCompositionLocalOf { AudiobookColorPalette() }

@Composable
fun AudiobookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled - we want consistent brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val audiobookColors = AudiobookColorPalette()

    CompositionLocalProvider(LocalAudiobookColors provides audiobookColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// Extension to access custom colors
object AudiobookTheme {
    val colors: AudiobookColorPalette
        @Composable
        get() = LocalAudiobookColors.current
}
