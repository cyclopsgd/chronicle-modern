package local.oss.chronicle.features.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * UI State for the Home screen.
 */
data class HomeUiState(
    val continueListening: List<HomeBook> = emptyList(),
    val recentlyAdded: List<HomeBook> = emptyList(),
    val downloaded: List<HomeBook> = emptyList(),
    val collections: List<HomeCollection> = emptyList(),
    val featuredBook: HomeBook? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOfflineMode: Boolean = false,
)

/**
 * Simplified book model for the Home UI.
 */
data class HomeBook(
    val id: Int,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val progress: Float, // 0.0 to 1.0
    val duration: Long,
    val isDownloaded: Boolean = false,
    val lastPlayedAt: Long = 0L,
)

/**
 * Collection model for the Home UI.
 */
data class HomeCollection(
    val id: Int,
    val title: String,
    val coverUrl: String?,
    val bookCount: Int,
)

/**
 * Main Home screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onBookClick: (HomeBook) -> Unit = {},
    onCollectionClick: (HomeCollection) -> Unit = {},
    onSeeAllContinueListening: () -> Unit = {},
    onSeeAllRecentlyAdded: () -> Unit = {},
    onSeeAllDownloaded: () -> Unit = {},
    onSeeAllCollections: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onPlayFeatured: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OpusColors.Background,
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
        ) {
            if (state.isLoading && !state.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OpusColors.Primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                ) {
                    // Offline mode banner
                    if (state.isOfflineMode) {
                        OfflineBanner()
                    }

                    // Featured/Hero Section (most recently played book)
                    state.featuredBook?.let { book ->
                        FeaturedBookSection(
                            book = book,
                            onPlayClick = onPlayFeatured,
                            onBookClick = { onBookClick(book) },
                        )
                    }

                    // Continue Listening Section
                    if (state.continueListening.isNotEmpty()) {
                        HomeSection(
                            title = "Continue Listening",
                            onSeeAllClick = onSeeAllContinueListening,
                        ) {
                            BookRow(
                                books = state.continueListening,
                                onBookClick = onBookClick,
                                showProgress = true,
                            )
                        }
                    }

                    // Recently Added Section
                    if (state.recentlyAdded.isNotEmpty()) {
                        HomeSection(
                            title = "Recently Added",
                            onSeeAllClick = onSeeAllRecentlyAdded,
                        ) {
                            BookRow(
                                books = state.recentlyAdded,
                                onBookClick = onBookClick,
                                showProgress = false,
                            )
                        }
                    }

                    // Downloaded Section
                    if (state.downloaded.isNotEmpty()) {
                        HomeSection(
                            title = "Downloaded",
                            onSeeAllClick = onSeeAllDownloaded,
                        ) {
                            BookRow(
                                books = state.downloaded,
                                onBookClick = onBookClick,
                                showProgress = true,
                            )
                        }
                    }

                    // Collections Section
                    if (state.collections.isNotEmpty()) {
                        HomeSection(
                            title = "Collections",
                            onSeeAllClick = onSeeAllCollections,
                        ) {
                            CollectionRow(
                                collections = state.collections,
                                onCollectionClick = onCollectionClick,
                            )
                        }
                    }

                    // Empty state
                    if (state.continueListening.isEmpty() &&
                        state.recentlyAdded.isEmpty() &&
                        state.downloaded.isEmpty() &&
                        state.featuredBook == null
                    ) {
                        EmptyHomeState()
                    }

                    // Bottom spacing for mini player
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpusColors.Primary.copy(alpha = 0.2f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = OpusColors.Primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline Mode - Showing downloaded books only",
                style = MaterialTheme.typography.bodySmall,
                color = OpusColors.Primary,
            )
        }
    }
}

@Composable
private fun FeaturedBookSection(
    book: HomeBook,
    onPlayClick: () -> Unit,
    onBookClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable(onClick = onBookClick)
    ) {
        // Background cover image with gradient
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(book.coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            OpusColors.Background.copy(alpha = 0.7f),
                            OpusColors.Background
                        )
                    )
                )
        )

        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (book.progress > 0f) {
                Text(
                    text = "CONTINUE LISTENING",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpusColors.Primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = OpusColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play button
                Surface(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(24.dp),
                    color = OpusColors.Primary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = OpusColors.Background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (book.progress > 0f) "Resume" else "Play",
                            style = MaterialTheme.typography.labelLarge,
                            color = OpusColors.Background,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Progress indicator
                if (book.progress > 0f) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${(book.progress * 100).toInt()}% complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpusColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    onSeeAllClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onSeeAllClick) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium,
                    color = OpusColors.Primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        content()
    }
}

@Composable
private fun BookRow(
    books: List<HomeBook>,
    onBookClick: (HomeBook) -> Unit,
    showProgress: Boolean,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onClick = { onBookClick(book) },
                showProgress = showProgress,
            )
        }
    }
}

@Composable
private fun BookCard(
    book: HomeBook,
    onClick: () -> Unit,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(book.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Downloaded indicator
            if (book.isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            color = OpusColors.Background.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Downloaded",
                        tint = OpusColors.Secondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Progress bar overlay
            if (showProgress && book.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = OpusColors.Primary,
                        trackColor = OpusColors.Background.copy(alpha = 0.5f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            color = OpusColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )

        Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = OpusColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CollectionRow(
    collections: List<HomeCollection>,
    onCollectionClick: (HomeCollection) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(collections, key = { it.id }) { collection ->
            CollectionCard(
                collection = collection,
                onClick = { onCollectionClick(collection) },
            )
        }
    }
}

@Composable
private fun CollectionCard(
    collection: HomeCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(OpusColors.Surface)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(collection.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = collection.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                OpusColors.Background.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // Collection info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${collection.bookCount} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpusColors.TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to Opus",
                style = MaterialTheme.typography.headlineSmall,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your audiobook library is empty.\nSync with your Plex server to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpusColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HomeScreenPreview() {
    OpusTheme(darkTheme = true) {
        HomeScreen(
            state = HomeUiState(
                featuredBook = HomeBook(
                    id = 1,
                    title = "Project Hail Mary",
                    author = "Andy Weir",
                    coverUrl = null,
                    progress = 0.45f,
                    duration = 100000L,
                ),
                continueListening = listOf(
                    HomeBook(1, "Project Hail Mary", "Andy Weir", null, 0.45f, 100000L),
                    HomeBook(2, "The Martian", "Andy Weir", null, 0.2f, 80000L),
                ),
                recentlyAdded = listOf(
                    HomeBook(3, "Dune", "Frank Herbert", null, 0f, 120000L),
                    HomeBook(4, "Foundation", "Isaac Asimov", null, 0f, 90000L),
                ),
                downloaded = listOf(
                    HomeBook(5, "Ender's Game", "Orson Scott Card", null, 0.8f, 70000L, true),
                ),
                collections = listOf(
                    HomeCollection(1, "Sci-Fi Favorites", null, 12),
                    HomeCollection(2, "Must Read", null, 8),
                ),
            )
        )
    }
}
