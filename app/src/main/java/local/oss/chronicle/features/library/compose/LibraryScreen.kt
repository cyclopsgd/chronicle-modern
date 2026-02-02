package local.oss.chronicle.features.library.compose

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import local.oss.chronicle.ui.theme.OpusColors
import local.oss.chronicle.ui.theme.OpusTheme

/**
 * View mode for the library display.
 */
enum class ViewMode {
    GRID,
    LIST,
}

/**
 * UI State for the Library screen.
 */
data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortKey: SortKey = SortKey.TITLE,
    val sortDescending: Boolean = true,
    val progressFilter: ProgressFilter = ProgressFilter.ALL,
    val showFilterSheet: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
)

enum class ProgressFilter(val displayName: String) {
    ALL("All Books"),
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    FINISHED("Finished"),
    DOWNLOADED("Downloaded"),
}

/**
 * Simplified book model for the library UI.
 */
data class LibraryBook(
    val id: Int,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val progress: Float, // 0.0 to 1.0
    val duration: Long,
    val isDownloaded: Boolean = false,
    val isPlayed: Boolean = false, // viewCount > 0
)

enum class SortKey(val displayName: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    DATE_ADDED("Date Added"),
    DATE_PLAYED("Recently Played"),
    DURATION("Duration"),
    YEAR("Year"),
}

/**
 * Main Library screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onBookClick: (LibraryBook) -> Unit = {},
    onBookPlayClick: (LibraryBook) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchActiveChange: (Boolean) -> Unit = {},
    onRefresh: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onDismissFilter: () -> Unit = {},
    onSortKeyChange: (SortKey) -> Unit = {},
    onSortDirectionToggle: () -> Unit = {},
    onProgressFilterChange: (ProgressFilter) -> Unit = {},
    onViewModeToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter bottom sheet
    if (state.showFilterSheet) {
        FilterSortBottomSheet(
            sortKey = state.sortKey,
            sortDescending = state.sortDescending,
            progressFilter = state.progressFilter,
            sheetState = filterSheetState,
            onSortKeyChange = onSortKeyChange,
            onSortDirectionToggle = onSortDirectionToggle,
            onProgressFilterChange = onProgressFilterChange,
            onDismiss = {
                scope.launch {
                    filterSheetState.hide()
                    onDismissFilter()
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OpusColors.Background)
    ) {
        // Top bar with search and filter
        LibraryTopBar(
            searchQuery = state.searchQuery,
            isSearchActive = state.isSearchActive,
            bookCount = state.books.size,
            viewMode = state.viewMode,
            onSearchQueryChange = onSearchQueryChange,
            onSearchActiveChange = onSearchActiveChange,
            onFilterClick = onFilterClick,
            onViewModeToggle = onViewModeToggle,
        )

        // Book grid with pull-to-refresh
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isLoading && state.books.isEmpty()) {
                // Initial loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OpusColors.Primary)
                }
            } else if (state.books.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.searchQuery.isNotEmpty()) {
                            "No books found for \"${state.searchQuery}\""
                        } else {
                            "No books in library"
                        },
                        color = OpusColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                // Book grid or list based on view mode
                when (state.viewMode) {
                    ViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = state.books,
                                key = { it.id }
                            ) { book ->
                                BookGridItem(
                                    book = book,
                                    onClick = { onBookClick(book) }
                                )
                            }
                        }
                    }
                    ViewMode.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            lazyListItems(
                                items = state.books,
                                key = { it.id }
                            ) { book ->
                                BookListItem(
                                    book = book,
                                    onClick = { onBookClick(book) },
                                    onPlayClick = { onBookPlayClick(book) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    searchQuery: String,
    isSearchActive: Boolean,
    bookCount: Int,
    viewMode: ViewMode,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onFilterClick: () -> Unit,
    onViewModeToggle: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Surface(
        color = OpusColors.Surface,
        shadowElevation = 4.dp,
    ) {
        Column {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "Search books...",
                                    color = OpusColors.TextSecondary
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = OpusColors.TextPrimary,
                                unfocusedTextColor = OpusColors.TextPrimary,
                                cursorColor = OpusColors.Primary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Library",
                            color = OpusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            onSearchActiveChange(false)
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close search",
                                tint = OpusColors.TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { onSearchActiveChange(true) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = OpusColors.TextPrimary
                            )
                        }
                    }
                    // View mode toggle (grid/list)
                    IconButton(onClick = onViewModeToggle) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (viewMode == ViewMode.GRID) "Switch to list view" else "Switch to grid view",
                            tint = OpusColors.TextPrimary
                        )
                    }
                    IconButton(onClick = onFilterClick) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter and sort",
                            tint = OpusColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OpusColors.Surface
                )
            )

            // Book count subtitle
            if (!isSearchActive && bookCount > 0) {
                Text(
                    text = "$bookCount books",
                    color = OpusColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BookGridItem(
    book: LibraryBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        // Cover art with progress overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f) // Book cover aspect ratio
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Cover image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(book.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay at bottom for progress bar visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // Progress bar at bottom
            if (book.progress > 0f) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = OpusColors.Primary,
                    trackColor = OpusColors.SliderTrack,
                )
            }

            // Downloaded indicator
            if (book.isDownloaded) {
                Surface(
                    color = OpusColors.Secondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(2.dp)
                    )
                }
            }

            // "Not played" indicator (dog-ear style)
            if (!book.isPlayed && book.progress == 0f) {
                Surface(
                    color = OpusColors.Primary,
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "NEW",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = book.title,
            color = OpusColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )

        // Author
        Text(
            text = book.author,
            color = OpusColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * List view item for a book - cover on left, title/author in middle, play button on right.
 */
@Composable
private fun BookListItem(
    book: LibraryBook,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = OpusColors.SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover art with subtle shadow
            Surface(
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.size(64.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Progress bar at bottom of cover
                    if (book.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { book.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter),
                            color = OpusColors.Primary,
                            trackColor = OpusColors.SliderTrack,
                        )
                    }

                    // Downloaded indicator
                    if (book.isDownloaded) {
                        Surface(
                            color = OpusColors.Secondary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title and author
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    color = OpusColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = book.author,
                    color = OpusColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Progress text if in progress
                if (book.progress > 0f && book.progress < 1f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${(book.progress * 100).toInt()}% complete",
                        color = OpusColors.Primary,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play button with shadow
            Surface(
                color = OpusColors.Primary,
                shape = RoundedCornerShape(50),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onPlayClick)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${book.title}",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortBottomSheet(
    sortKey: SortKey,
    sortDescending: Boolean,
    progressFilter: ProgressFilter,
    sheetState: androidx.compose.material3.SheetState,
    onSortKeyChange: (SortKey) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onProgressFilterChange: (ProgressFilter) -> Unit,
    onDismiss: () -> Unit,
) {
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
                text = "Sort & Filter",
                style = MaterialTheme.typography.titleLarge,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider(color = OpusColors.ControlsBackground)

            // Sort direction toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSortDirectionToggle)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = OpusColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (sortDescending) "Descending" else "Ascending",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpusColors.TextPrimary,
                )
            }

            HorizontalDivider(color = OpusColors.ControlsBackground)

            // Sort options
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = OpusColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            SortKey.entries.forEach { key ->
                SortOptionItem(
                    label = key.displayName,
                    isSelected = sortKey == key,
                    onClick = { onSortKeyChange(key) }
                )
            }

            HorizontalDivider(color = OpusColors.ControlsBackground)

            // Filter options
            Text(
                text = "Filter",
                style = MaterialTheme.typography.labelMedium,
                color = OpusColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            // Progress filter options
            ProgressFilter.entries.forEach { filter ->
                FilterOptionItem(
                    label = filter.displayName,
                    isSelected = progressFilter == filter,
                    onClick = { onProgressFilterChange(filter) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FilterOptionItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) OpusColors.Primary else OpusColors.TextPrimary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = OpusColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SortOptionItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) OpusColors.Primary else OpusColors.TextPrimary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = OpusColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Preview

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LibraryScreenPreview() {
    OpusTheme(darkTheme = true) {
        LibraryScreen(
            state = LibraryUiState(
                books = listOf(
                    LibraryBook(
                        id = 1,
                        title = "The Hitchhiker's Guide to the Galaxy",
                        author = "Douglas Adams",
                        coverUrl = null,
                        progress = 0.45f,
                        duration = 360000,
                        isPlayed = true,
                    ),
                    LibraryBook(
                        id = 2,
                        title = "1984",
                        author = "George Orwell",
                        coverUrl = null,
                        progress = 0f,
                        duration = 240000,
                        isDownloaded = true,
                    ),
                    LibraryBook(
                        id = 3,
                        title = "Dune",
                        author = "Frank Herbert",
                        coverUrl = null,
                        progress = 1.0f,
                        duration = 720000,
                        isPlayed = true,
                    ),
                ),
            )
        )
    }
}
