package local.oss.chronicle.features.collections.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
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
 * UI State for the Collection Details screen.
 */
data class CollectionDetailsUiState(
    val collectionTitle: String = "",
    val books: List<CollectionBook> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * Book model for the Collection Details UI.
 */
data class CollectionBook(
    val id: Int,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val progress: Float = 0f, // 0.0 to 1.0
    val isDownloaded: Boolean = false,
)

/**
 * Collection Details screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailsScreen(
    state: CollectionDetailsUiState,
    onBackClick: () -> Unit = {},
    onBookClick: (CollectionBook) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OpusColors.Background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar with Back Button
            TopAppBar(
                title = {
                    Text(
                        text = state.collectionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = OpusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OpusColors.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OpusColors.Background,
                    titleContentColor = OpusColors.TextPrimary,
                ),
            )

            // Content
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = OpusColors.Primary)
                    }
                }
                state.books.isEmpty() -> {
                    EmptyCollectionState()
                }
                else -> {
                    BooksGrid(
                        books = state.books,
                        onBookClick = onBookClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BooksGrid(
    books: List<CollectionBook>,
    onBookClick: (CollectionBook) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                onClick = { onBookClick(book) },
            )
        }

        // Bottom spacing for mini player
        item { Spacer(modifier = Modifier.height(80.dp)) }
        item { Spacer(modifier = Modifier.height(80.dp)) }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun BookGridItem(
    book: CollectionBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Book cover with 1:1 aspect ratio (Audible-style square covers)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            color = OpusColors.Surface,
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Downloaded indicator badge
                if (book.isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = OpusColors.Secondary,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Downloaded",
                            tint = OpusColors.Background,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Progress bar overlay at bottom
                if (book.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = OpusColors.Primary,
                        trackColor = OpusColors.Background.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Book title
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            color = OpusColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )

        // Author name
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
private fun EmptyCollectionState() {
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
                text = "No Books",
                style = MaterialTheme.typography.headlineSmall,
                color = OpusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This collection doesn't have any books yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpusColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CollectionDetailsScreenPreview() {
    OpusTheme(darkTheme = true) {
        CollectionDetailsScreen(
            state = CollectionDetailsUiState(
                collectionTitle = "Sci-Fi Favorites",
                books = listOf(
                    CollectionBook(
                        id = 1,
                        title = "Project Hail Mary",
                        author = "Andy Weir",
                        coverUrl = null,
                        progress = 0.45f,
                    ),
                    CollectionBook(
                        id = 2,
                        title = "The Martian",
                        author = "Andy Weir",
                        coverUrl = null,
                        progress = 1.0f,
                        isDownloaded = true,
                    ),
                    CollectionBook(
                        id = 3,
                        title = "Dune",
                        author = "Frank Herbert",
                        coverUrl = null,
                        progress = 0f,
                    ),
                    CollectionBook(
                        id = 4,
                        title = "Foundation",
                        author = "Isaac Asimov",
                        coverUrl = null,
                        progress = 0.2f,
                    ),
                    CollectionBook(
                        id = 5,
                        title = "Ender's Game",
                        author = "Orson Scott Card",
                        coverUrl = null,
                        progress = 0f,
                        isDownloaded = true,
                    ),
                ),
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CollectionDetailsScreenEmptyPreview() {
    OpusTheme(darkTheme = true) {
        CollectionDetailsScreen(
            state = CollectionDetailsUiState(
                collectionTitle = "Empty Collection",
                books = emptyList(),
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CollectionDetailsScreenLoadingPreview() {
    OpusTheme(darkTheme = true) {
        CollectionDetailsScreen(
            state = CollectionDetailsUiState(
                collectionTitle = "Loading...",
                isLoading = true,
            )
        )
    }
}
