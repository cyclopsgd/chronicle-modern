package local.oss.chronicle.features.bookdetails.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Main Book Details screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookDetailsScreen(
    state: BookDetailsUiState,
    onBackClick: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onCacheClick: () -> Unit = {},
    onChapterClick: (ChapterItem) -> Unit = {},
    onToggleSummary: () -> Unit = {},
    onToggleWatched: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onRetryConnection: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Show user messages as snackbar
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OpusColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { /* No title - book title is shown in header */ },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OpusColors.TextPrimary,
                        )
                    }
                },
                actions = {
                    // Menu button
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = OpusColors.TextPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sync from server")
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onSyncClick()
                                },
                                enabled = !state.isSyncing,
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (state.book?.isWatched == true)
                                                Icons.Default.VisibilityOff
                                            else
                                                Icons.Default.Visibility,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (state.book?.isWatched == true)
                                                "Mark as unplayed"
                                            else
                                                "Mark as played"
                                        )
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleWatched()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = OpusColors.Surface,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (state.isLoading && state.book == null) {
                // Initial loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = OpusColors.Primary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Connection banner (if not connected)
                    if (state.connectionState != ConnectionState.CONNECTED) {
                        item {
                            ConnectionBanner(
                                connectionState = state.connectionState,
                                onRetryClick = onRetryConnection,
                            )
                        }
                    }

                    // Book header
                    state.book?.let { book ->
                        item {
                            BookHeader(
                                book = book,
                                progressString = state.progressString,
                                progressPercent = state.progressPercent,
                                isPlaying = state.isPlaying,
                                cacheStatus = state.cacheStatus,
                                onPlayPause = onPlayPause,
                                onCacheClick = onCacheClick,
                            )
                        }

                        // Expandable summary
                        if (book.summary.isNotBlank()) {
                            item {
                                ExpandableSummary(
                                    summary = book.summary,
                                    isExpanded = state.isSummaryExpanded,
                                    onToggle = onToggleSummary,
                                )
                            }
                        }
                    }

                    // Chapters section header
                    if (state.chapters.isNotEmpty()) {
                        stickyHeader {
                            ChaptersStickyHeader()
                        }

                        // Chapter list - use composite key to avoid collisions
                        items(
                            items = state.chapters,
                            key = { "${it.id}_${it.startTimeOffset}" },
                        ) { chapter ->
                            ChapterListItem(
                                chapter = chapter,
                                onClick = { onChapterClick(chapter) },
                            )
                        }
                    } else if (!state.isLoading) {
                        // No chapters found
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No chapters found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OpusColors.TextSecondary,
                                )
                            }
                        }
                    }

                    // Bottom spacing for mini player
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }

                // Loading overlay for syncing
                if (state.isSyncing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OpusColors.Background.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = OpusColors.Primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Syncing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OpusColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    connectionState: ConnectionState,
    onRetryClick: () -> Unit,
) {
    val backgroundColor = when (connectionState) {
        ConnectionState.CONNECTING -> OpusColors.Primary.copy(alpha = 0.2f)
        ConnectionState.CONNECTION_FAILED -> OpusColors.Error.copy(alpha = 0.2f)
        ConnectionState.CONNECTED -> Color.Transparent
    }

    val textColor = when (connectionState) {
        ConnectionState.CONNECTING -> OpusColors.Primary
        ConnectionState.CONNECTION_FAILED -> OpusColors.Error
        ConnectionState.CONNECTED -> OpusColors.TextPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (connectionState == ConnectionState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = OpusColors.Primary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                    )
                } else {
                    Text(
                        text = "Not connected to server",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                    )
                }
            }

            if (connectionState == ConnectionState.CONNECTION_FAILED) {
                TextButton(
                    onClick = onRetryClick,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = OpusColors.Primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        color = OpusColors.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookHeader(
    book: BookDetail,
    progressString: String,
    progressPercent: Int,
    isPlaying: Boolean,
    cacheStatus: CacheStatus,
    onPlayPause: () -> Unit,
    onCacheClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cover art
        Surface(
            modifier = Modifier.size(196.dp),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
            color = OpusColors.Surface,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(book.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineSmall,
            color = OpusColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Author
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodyLarge,
            color = OpusColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress text
        Text(
            text = "$progressString ($progressPercent%)",
            style = MaterialTheme.typography.bodySmall,
            color = OpusColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Play and download buttons
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/Pause button
            Surface(
                onClick = onPlayPause,
                shape = RoundedCornerShape(24.dp),
                color = OpusColors.Primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = OpusColors.Background,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        style = MaterialTheme.typography.labelLarge,
                        color = OpusColors.Background,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Download button
            Surface(
                onClick = onCacheClick,
                shape = CircleShape,
                color = OpusColors.SurfaceVariant,
            ) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (cacheStatus) {
                        CacheStatus.CACHING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = OpusColors.Primary,
                                strokeWidth = 2.dp,
                            )
                        }
                        CacheStatus.CACHED -> {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove download",
                                tint = OpusColors.Secondary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        CacheStatus.NOT_CACHED -> {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download",
                                tint = OpusColors.TextPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSummary(
    summary: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = OpusColors.TextSecondary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = if (isExpanded) "Less" else "More",
                style = MaterialTheme.typography.labelMedium,
                color = OpusColors.Primary,
            )
        }
    }
}

@Composable
private fun ChaptersStickyHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpusColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            color = OpusColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChapterListItem(
    chapter: ChapterItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (chapter.isCurrentChapter)
            OpusColors.Primary.copy(alpha = 0.1f)
        else
            Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Playing indicator
                if (chapter.isCurrentChapter) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Currently playing",
                        tint = OpusColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chapter.isCurrentChapter)
                        OpusColors.Primary
                    else
                        OpusColors.TextPrimary,
                    fontWeight = if (chapter.isCurrentChapter)
                        FontWeight.Bold
                    else
                        FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            Text(
                text = chapter.durationString,
                style = MaterialTheme.typography.bodySmall,
                color = if (chapter.isCurrentChapter)
                    OpusColors.Primary
                else
                    OpusColors.TextSecondary,
            )
        }
    }
}

// Preview
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun BookDetailsScreenPreview() {
    OpusTheme(darkTheme = true) {
        BookDetailsScreen(
            state = BookDetailsUiState(
                book = BookDetail(
                    id = 1,
                    title = "Project Hail Mary",
                    author = "Andy Weir",
                    coverUrl = null,
                    summary = "Ryland Grace is the sole survivor on a desperate, last-chance mission - and if he fails, humanity and the Earth itself will perish. Except that right now, he doesn't know that. He can't even remember his own name, let alone the nature of his assignment or how to complete it.",
                    duration = 31500000L, // ~8:45:00
                    progress = 4890000L, // ~1:21:30
                    isWatched = false,
                ),
                chapters = listOf(
                    ChapterItem(1, 1, "Chapter 1: Awakening", 0, 1800000, "30:00", true),
                    ChapterItem(2, 1, "Chapter 2: Discovery", 1800000, 2100000, "35:00", false),
                    ChapterItem(3, 1, "Chapter 3: The Ship", 3900000, 1500000, "25:00", false),
                    ChapterItem(4, 1, "Chapter 4: First Contact", 5400000, 1800000, "30:00", false),
                ),
                isLoading = false,
                isPlaying = false,
                progressString = "1:21:30 / 8:45:00",
                progressPercent = 15,
                currentChapterIndex = 0,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun BookDetailsScreenLoadingPreview() {
    OpusTheme(darkTheme = true) {
        BookDetailsScreen(
            state = BookDetailsUiState(isLoading = true),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun BookDetailsScreenConnectionErrorPreview() {
    OpusTheme(darkTheme = true) {
        BookDetailsScreen(
            state = BookDetailsUiState(
                book = BookDetail(
                    id = 1,
                    title = "Project Hail Mary",
                    author = "Andy Weir",
                    coverUrl = null,
                    summary = "A great book about space exploration.",
                    duration = 31500000L,
                    progress = 4890000L,
                    isWatched = false,
                ),
                connectionState = ConnectionState.CONNECTION_FAILED,
                isLoading = false,
                progressString = "1:21:30 / 8:45:00",
                progressPercent = 15,
            ),
        )
    }
}
