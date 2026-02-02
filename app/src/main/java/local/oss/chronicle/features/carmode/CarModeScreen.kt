package local.oss.chronicle.features.carmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import local.oss.chronicle.ui.theme.OpusColors
import local.oss.chronicle.ui.theme.OpusTheme

/**
 * State holder for the Car Mode screen.
 */
data class CarModeUiState(
    val bookTitle: String = "",
    val author: String = "",
    val chapterTitle: String = "",
    val coverArtUrl: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCarModeAutoDetected: Boolean = false,
)

/**
 * Car Mode screen optimized for use while driving.
 *
 * Features:
 * - Giant play/pause button (120dp+) for easy tapping
 * - Large skip controls
 * - High contrast UI
 * - Minimal distractions (no sleep timer, no speed controls)
 * - Large, readable text
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarModeScreen(
    state: CarModeUiState,
    onExitCarMode: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onSkipBackward: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
    onSkipToPrevious: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarModeColors.Background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar with exit button
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = CarModeColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Car Mode",
                            color = CarModeColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (state.isCarModeAutoDetected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = CarModeColors.Accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = "Auto",
                                    color = CarModeColors.Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExitCarMode) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit car mode",
                            tint = CarModeColors.TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Cover Art and Book Info Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.25f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cover Art (smaller in car mode)
                    Surface(
                        modifier = Modifier
                            .size(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 8.dp,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.coverArtUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Book info (large text for readability)
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = state.bookTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = CarModeColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 20.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.author,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CarModeColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                        )
                        if (state.chapterTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.chapterTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CarModeColors.Accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                // Progress bar (simplified - no seeking in car mode)
                ProgressSection(
                    currentPositionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Giant Playback Controls (main focus)
                GiantPlaybackControls(
                    isPlaying = state.isPlaying,
                    onPlayPause = onPlayPause,
                    onSkipForward = onSkipForward,
                    onSkipBackward = onSkipBackward,
                    onSkipToNext = onSkipToNext,
                    onSkipToPrevious = onSkipToPrevious,
                    modifier = Modifier.weight(0.55f)
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProgressSection(
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = CarModeColors.Accent,
            trackColor = CarModeColors.ProgressTrack,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPositionMs),
                style = MaterialTheme.typography.bodyLarge,
                color = CarModeColors.TextSecondary,
                fontSize = 16.sp,
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.bodyLarge,
                color = CarModeColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun GiantPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Main controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Skip backward
            GiantControlButton(
                icon = Icons.Default.Replay10,
                contentDescription = "Rewind 10 seconds",
                onClick = onSkipBackward,
                size = 64.dp,
            )

            // GIANT Play/Pause button - 120dp+ as specified
            Surface(
                color = CarModeColors.PlayButtonBackground,
                shape = CircleShape,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPlayPause)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            // Skip forward
            GiantControlButton(
                icon = Icons.Default.Forward30,
                contentDescription = "Forward 30 seconds",
                onClick = onSkipForward,
                size = 64.dp,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Secondary controls (previous/next chapter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GiantControlButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous chapter",
                onClick = onSkipToPrevious,
                size = 48.dp,
            )

            Spacer(modifier = Modifier.width(48.dp))

            GiantControlButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next chapter",
                onClick = onSkipToNext,
                size = 48.dp,
            )
        }
    }
}

@Composable
private fun GiantControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CarModeColors.ControlBackground,
        shape = CircleShape,
        modifier = modifier
            .size(size + 32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = CarModeColors.TextPrimary,
                modifier = Modifier.size(size)
            )
        }
    }
}

// Car mode specific colors - higher contrast for visibility
private object CarModeColors {
    val Background = Color(0xFF000000) // Pure black for AMOLED
    val TextPrimary = Color(0xFFFFFFFF) // Pure white
    val TextSecondary = Color(0xFFB0B0B0) // Light gray
    val Accent = Color(0xFFFFAB40) // Amber to match Opus branding
    val PlayButtonBackground = Color(0xFFFFAB40) // Amber play button
    val ControlBackground = Color(0xFF2A2A2A) // Dark gray
    val ProgressTrack = Color(0xFF3A3A3A) // Progress track
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CarModeScreenPreview() {
    OpusTheme(darkTheme = true) {
        CarModeScreen(
            state = CarModeUiState(
                bookTitle = "The Hitchhiker's Guide to the Galaxy",
                author = "Douglas Adams",
                chapterTitle = "Chapter 12: The Answer",
                isPlaying = true,
                currentPositionMs = 125000,
                durationMs = 360000,
                isCarModeAutoDetected = true,
            )
        )
    }
}
