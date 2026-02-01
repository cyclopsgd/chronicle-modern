package local.oss.chronicle.features.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.ui.theme.OpusColors
import local.oss.chronicle.ui.theme.OpusTheme

/**
 * State holder for the Now Playing screen.
 */
data class NowPlayingUiState(
    val bookTitle: String = "",
    val author: String = "",
    val narrator: String = "",
    val chapterTitle: String = "",
    val coverArtUrl: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingMs: Long = 0L,
    val isSleepTimerActive: Boolean = false,
    val isBookmarked: Boolean = false,
    val skipBackwardSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    // Chapter list
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val showChapterList: Boolean = false,
)

/**
 * Main Now Playing screen composable.
 *
 * Displays the currently playing audiobook with cover art, playback controls,
 * and progress information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    onNavigateBack: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onSkipBackward: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
    onSkipToPrevious: () -> Unit = {},
    onSeekTo: (Float) -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onChapterClick: () -> Unit = {},
    onChapterSelected: (Chapter) -> Unit = {},
    onDismissChapterList: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Show chapter list bottom sheet
    if (state.showChapterList) {
        ChapterListBottomSheet(
            chapters = state.chapters,
            currentChapterIndex = state.currentChapterIndex,
            sheetState = sheetState,
            onChapterSelected = { chapter ->
                scope.launch {
                    sheetState.hide()
                    onChapterSelected(chapter)
                }
            },
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    onDismissChapterList()
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OpusColors.Background)
    ) {
        // Blurred background from cover art
        if (state.coverArtUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(state.coverArtUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
            )
            // Gradient overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                OpusColors.Background.copy(alpha = 0.7f),
                                OpusColors.Background.copy(alpha = 0.9f),
                                OpusColors.Background,
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OpusColors.TextPrimary
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
                // Cover Art - takes ~45% of screen
                CoverArtSection(
                    coverArtUrl = state.coverArtUrl,
                    sleepTimerRemainingMs = state.sleepTimerRemainingMs,
                    isSleepTimerActive = state.isSleepTimerActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                        .padding(vertical = 16.dp)
                )

                // Book info
                BookInfoSection(
                    title = state.bookTitle,
                    author = state.author,
                    narrator = state.narrator,
                    chapterTitle = state.chapterTitle,
                    onChapterClick = onChapterClick,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Progress slider
                ProgressSection(
                    currentPositionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Playback controls
                PlaybackControlsSection(
                    isPlaying = state.isPlaying,
                    skipBackwardSeconds = state.skipBackwardSeconds,
                    skipForwardSeconds = state.skipForwardSeconds,
                    onPlayPause = onPlayPause,
                    onSkipForward = onSkipForward,
                    onSkipBackward = onSkipBackward,
                    onSkipToNext = onSkipToNext,
                    onSkipToPrevious = onSkipToPrevious,
                )

                Spacer(modifier = Modifier.weight(0.1f))

                // Bottom controls (speed, sleep timer, bookmark)
                BottomControlsSection(
                    playbackSpeed = state.playbackSpeed,
                    isSleepTimerActive = state.isSleepTimerActive,
                    isBookmarked = state.isBookmarked,
                    onSpeedClick = onSpeedClick,
                    onSleepTimerClick = onSleepTimerClick,
                    onBookmarkClick = onBookmarkClick,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun CoverArtSection(
    coverArtUrl: String?,
    sleepTimerRemainingMs: Long,
    isSleepTimerActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 16.dp,
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverArtUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Sleep timer overlay
                AnimatedVisibility(
                    visible = isSleepTimerActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(
                        color = OpusColors.SleepTimerActive.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomStart = 16.dp),
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatTime(sleepTimerRemainingMs),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoSection(
    title: String,
    author: String,
    narrator: String,
    chapterTitle: String,
    onChapterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Book title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = OpusColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 22.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Author
        Text(
            text = author,
            style = MaterialTheme.typography.bodyLarge,
            color = OpusColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
        )

        // Narrator (if available)
        if (narrator.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Narrated by $narrator",
                style = MaterialTheme.typography.bodyMedium,
                color = OpusColors.TextTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
            )
        }

        // Chapter (clickable)
        if (chapterTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = OpusColors.ControlsBackground,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable(onClick = onChapterClick)
            ) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpusColors.Primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSliding by remember { mutableStateOf(false) }

    val progress = if (durationMs > 0) {
        currentPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }

    val displayPosition = if (isSliding) sliderPosition else progress

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayPosition.coerceIn(0f, 1f),
            onValueChange = { newValue ->
                isSliding = true
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                isSliding = false
                onSeekTo(sliderPosition)
            },
            colors = SliderDefaults.colors(
                thumbColor = OpusColors.Primary,
                activeTrackColor = OpusColors.Primary,
                inactiveTrackColor = OpusColors.SliderTrack,
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Elapsed time
            Text(
                text = formatTime(if (isSliding) (sliderPosition * durationMs).toLong() else currentPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = OpusColors.TextSecondary,
                fontSize = 12.sp,
            )

            // Remaining time
            val remainingMs = durationMs - (if (isSliding) (sliderPosition * durationMs).toLong() else currentPositionMs)
            Text(
                text = "-${formatTime(remainingMs.coerceAtLeast(0))}",
                style = MaterialTheme.typography.bodySmall,
                color = OpusColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    skipBackwardSeconds: Int,
    skipForwardSeconds: Int,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Skip to previous
        ControlButton(
            icon = Icons.Default.SkipPrevious,
            contentDescription = "Previous track",
            onClick = onSkipToPrevious,
            size = 32.dp,
        )

        // Skip backward
        ControlButton(
            icon = getReplayIcon(skipBackwardSeconds),
            contentDescription = "Rewind $skipBackwardSeconds seconds",
            onClick = onSkipBackward,
            size = 40.dp,
        )

        // Play/Pause (center, larger)
        Surface(
            color = OpusColors.PlayButtonBackground,
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlayPause)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Skip forward
        ControlButton(
            icon = getForwardIcon(skipForwardSeconds),
            contentDescription = "Forward $skipForwardSeconds seconds",
            onClick = onSkipForward,
            size = 40.dp,
        )

        // Skip to next
        ControlButton(
            icon = Icons.Default.SkipNext,
            contentDescription = "Next track",
            onClick = onSkipToNext,
            size = 32.dp,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = OpusColors.TextPrimary,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
private fun BottomControlsSection(
    playbackSpeed: Float,
    isSleepTimerActive: Boolean,
    isBookmarked: Boolean,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Speed button
        Surface(
            color = OpusColors.ControlsBackground,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.clickable(onClick = onSpeedClick)
        ) {
            Text(
                text = formatSpeed(playbackSpeed),
                style = MaterialTheme.typography.bodyMedium,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Sleep timer button
        IconButton(onClick = onSleepTimerClick) {
            Icon(
                imageVector = if (isSleepTimerActive) Icons.Default.Timer else Icons.Default.TimerOff,
                contentDescription = "Sleep timer",
                tint = if (isSleepTimerActive) OpusColors.SleepTimerActive else OpusColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }

        // Bookmark button
        IconButton(onClick = onBookmarkClick) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = if (isBookmarked) OpusColors.Primary else OpusColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Chapter list bottom sheet.
 * Displays all chapters with the current one highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListBottomSheet(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    sheetState: androidx.compose.material3.SheetState,
    onChapterSelected: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Scroll to current chapter when sheet opens
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0 && chapters.isNotEmpty()) {
            listState.animateScrollToItem(
                index = currentChapterIndex.coerceIn(0, chapters.lastIndex),
                scrollOffset = -100 // Offset to show some context above
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OpusColors.Surface,
        contentColor = OpusColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider(color = OpusColors.ControlsBackground)

            // Chapter list
            if (chapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chapters available",
                        color = OpusColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        ChapterListItem(
                            chapter = chapter,
                            isCurrentChapter = index == currentChapterIndex,
                            onClick = { onChapterSelected(chapter) }
                        )
                    }
                }
            }

            // Bottom padding
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Individual chapter item in the list.
 */
@Composable
private fun ChapterListItem(
    chapter: Chapter,
    isCurrentChapter: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isCurrentChapter) OpusColors.Primary.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Playing indicator for current chapter
            if (isCurrentChapter) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Currently playing",
                    tint = OpusColors.Primary,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 0.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Chapter info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentChapter) OpusColors.Primary else OpusColors.TextPrimary,
                    fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Chapter duration
                val durationMs = chapter.endTimeOffset - chapter.startTimeOffset
                if (durationMs > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = OpusColors.TextSecondary,
                    )
                }
            }
        }
    }
}

// Helper functions

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

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toLong().toFloat()) {
        "${speed.toLong()}x"
    } else {
        String.format("%.1fx", speed)
    }
}

private fun getReplayIcon(seconds: Int): ImageVector {
    // Using default replay icon, could customize based on seconds
    return Icons.Default.Replay10
}

private fun getForwardIcon(seconds: Int): ImageVector {
    // Using default forward icon, could customize based on seconds
    return Icons.Default.Forward30
}

// Preview

@Preview(showBackground = true, backgroundColor = 0xFF0F0F1A)
@Composable
private fun NowPlayingScreenPreview() {
    OpusTheme(darkTheme = true) {
        NowPlayingScreen(
            state = NowPlayingUiState(
                bookTitle = "The Hitchhiker's Guide to the Galaxy",
                author = "Douglas Adams",
                narrator = "Stephen Fry",
                chapterTitle = "Chapter 12: The Answer",
                isPlaying = true,
                currentPositionMs = 125000,
                durationMs = 360000,
                playbackSpeed = 1.2f,
                isSleepTimerActive = true,
                sleepTimerRemainingMs = 900000,
            )
        )
    }
}
