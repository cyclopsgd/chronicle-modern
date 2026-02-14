package local.oss.chronicle.features.collections.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 * UI State for the Collections screen.
 */
@Immutable
data class CollectionsUiState(
    val collections: List<CollectionItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isEmpty: Boolean = false,
    val isOfflineMode: Boolean = false,
)

/**
 * Simplified collection model for the Collections UI.
 */
@Immutable
data class CollectionItem(
    val id: Int,
    val title: String,
    val coverUrl: String?,
    val bookCount: Int,
)

/**
 * Main Collections screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    state: CollectionsUiState,
    onCollectionClick: (CollectionItem) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchActiveChange: (Boolean) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OpusColors.Background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar with Search
            CollectionsTopBar(
                searchQuery = state.searchQuery,
                isSearchActive = state.isSearchActive,
                onSearchQueryChange = onSearchQueryChange,
                onSearchActiveChange = onSearchActiveChange,
            )

            // Offline mode banner
            AnimatedVisibility(
                visible = state.isOfflineMode,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                OfflineBanner()
            }

            // Content
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading && !state.isRefreshing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OpusColors.Primary)
                        }
                    }
                    state.isEmpty || state.collections.isEmpty() -> {
                        EmptyCollectionsState(
                            isSearching = state.isSearchActive && state.searchQuery.isNotEmpty()
                        )
                    }
                    else -> {
                        CollectionsGrid(
                            collections = state.collections,
                            onCollectionClick = onCollectionClick,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionsTopBar(
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    TopAppBar(
        title = {
            if (isSearchActive) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = OpusColors.TextPrimary,
                    ),
                    cursorBrush = SolidColor(OpusColors.Primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search collections...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OpusColors.TextSecondary,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            } else {
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleLarge,
                    color = OpusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        actions = {
            if (isSearchActive) {
                IconButton(
                    onClick = {
                        onSearchQueryChange("")
                        onSearchActiveChange(false)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Close search",
                        tint = OpusColors.TextPrimary,
                    )
                }
            } else {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = OpusColors.TextPrimary,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = OpusColors.Background,
            titleContentColor = OpusColors.TextPrimary,
        ),
    )
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
                text = "Offline Mode - Showing downloaded content only",
                style = MaterialTheme.typography.bodySmall,
                color = OpusColors.Primary,
            )
        }
    }
}

@Composable
private fun CollectionsGrid(
    collections: List<CollectionItem>,
    onCollectionClick: (CollectionItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        items(collections, key = { it.id }) { collection ->
            CollectionGridCard(
                collection = collection,
                onClick = { onCollectionClick(collection) },
            )
        }

        // Bottom spacing for mini player
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CollectionGridCard(
    collection: CollectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = OpusColors.Surface,
    ) {
        Box {
            // Cover image
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
                                Color.Transparent,
                                OpusColors.Background.copy(alpha = 0.8f),
                                OpusColors.Background.copy(alpha = 0.95f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Collection info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${collection.bookCount} ${if (collection.bookCount == 1) "book" else "books"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpusColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }

            // Book count badge in top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = OpusColors.Primary,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = collection.bookCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = OpusColors.Background,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmptyCollectionsState(
    isSearching: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSearching) "No Results" else "No Collections Yet",
                style = MaterialTheme.typography.headlineSmall,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSearching) {
                    "No collections match your search.\nTry a different search term."
                } else {
                    "Collections from your Plex library\nwill appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OpusColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CollectionsScreenPreview() {
    OpusTheme(darkTheme = true) {
        CollectionsScreen(
            state = CollectionsUiState(
                collections = listOf(
                    CollectionItem(1, "Sci-Fi Favorites", null, 12),
                    CollectionItem(2, "Must Read 2024", null, 8),
                    CollectionItem(3, "Fantasy Epic Series", null, 5),
                    CollectionItem(4, "Quick Listens", null, 15),
                ),
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CollectionsScreenEmptyPreview() {
    OpusTheme(darkTheme = true) {
        CollectionsScreen(
            state = CollectionsUiState(
                collections = emptyList(),
                isEmpty = true,
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CollectionsScreenSearchPreview() {
    OpusTheme(darkTheme = true) {
        CollectionsScreen(
            state = CollectionsUiState(
                collections = listOf(
                    CollectionItem(1, "Sci-Fi Favorites", null, 12),
                ),
                isSearchActive = true,
                searchQuery = "sci",
            )
        )
    }
}
