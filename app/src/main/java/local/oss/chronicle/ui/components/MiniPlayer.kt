package local.oss.chronicle.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import local.oss.chronicle.ui.theme.OpusColors
import local.oss.chronicle.ui.theme.OpusTheme

/**
 * State for the Mini Player.
 */
data class MiniPlayerState(
    val isVisible: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val coverUrl: String? = null,
    val progress: Float = 0f, // 0.0 to 1.0
    val isPlaying: Boolean = false,
)

/**
 * Mini Player composable that appears above the bottom navigation.
 * Shows current book info with play/pause controls.
 *
 * Height: 72dp (matching the XML mini player)
 */
@Composable
fun MiniPlayer(
    state: MiniPlayerState,
    onPlayPause: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            color = OpusColors.Surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onClick)
        ) {
            Column {
                // Progress bar at top (2dp height)
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = OpusColors.Primary,
                    trackColor = OpusColors.Primary.copy(alpha = 0.2f),
                )

                // Content row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cover art (56x56)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(state.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = state.bookTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(OpusColors.Surface)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title and chapter
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Chapter title (primary text)
                        Text(
                            text = state.chapterTitle.ifEmpty { "No chapter" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OpusColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                        )

                        // Book title (secondary text)
                        Text(
                            text = state.bookTitle.ifEmpty { "No book playing" },
                            style = MaterialTheme.typography.bodySmall,
                            color = OpusColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                        )
                    }

                    // Play/Pause button
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = OpusColors.TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Variant of MiniPlayer without animation for use in static layouts.
 */
@Composable
fun MiniPlayerContent(
    state: MiniPlayerState,
    onPlayPause: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    Surface(
        color = OpusColors.Surface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Progress bar at top
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = OpusColors.Primary,
                trackColor = OpusColors.Primary.copy(alpha = 0.2f),
            )

            // Content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover art with placeholder
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(OpusColors.SurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.coverUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = state.bookTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight()
                        )
                    } else {
                        // Placeholder icon when no cover
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = OpusColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.chapterTitle.ifEmpty { "No chapter" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpusColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = state.bookTitle.ifEmpty { "No book playing" },
                        style = MaterialTheme.typography.bodySmall,
                        color = OpusColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                    )
                }

                // Vertical divider before play/pause button
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp)
                        .background(OpusColors.TextSecondary.copy(alpha = 0.3f))
                )

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = OpusColors.TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MiniPlayerPreview() {
    OpusTheme(darkTheme = true) {
        MiniPlayerContent(
            state = MiniPlayerState(
                isVisible = true,
                bookTitle = "Project Hail Mary",
                chapterTitle = "Chapter 12: The Answer",
                progress = 0.45f,
                isPlaying = true,
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MiniPlayerPausedPreview() {
    OpusTheme(darkTheme = true) {
        MiniPlayerContent(
            state = MiniPlayerState(
                isVisible = true,
                bookTitle = "The Martian",
                chapterTitle = "Sol 6",
                progress = 0.15f,
                isPlaying = false,
            )
        )
    }
}
