package local.oss.chronicle.features.library.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.LibrarySyncRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.Audiobook
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_AUTHOR
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_DATE_ADDED
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_DATE_PLAYED
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_DURATION
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_TITLE
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_YEAR
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.features.player.MediaServiceConnection
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Compose-based Library screen.
 *
 * Uses StateFlow for reactive state management with the Compose UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComposeLibraryViewModel @Inject constructor(
    private val bookRepository: IBookRepository,
    private val librarySyncRepository: LibrarySyncRepository,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig,
    private val mediaServiceConnection: MediaServiceConnection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Cache of all books from the database
    private var allBooks: List<Audiobook> = emptyList()

    init {
        // Load initial preferences
        loadPreferences()

        // Observe books from repository
        observeBooks()

        // Observe refresh state
        observeRefreshState()
    }

    private fun loadPreferences() {
        val sortKeyString = prefsRepo.bookSortKey
        val sortKey = when (sortKeyString) {
            SORT_KEY_TITLE -> SortKey.TITLE
            SORT_KEY_AUTHOR -> SortKey.AUTHOR
            SORT_KEY_DATE_ADDED -> SortKey.DATE_ADDED
            SORT_KEY_DATE_PLAYED -> SortKey.DATE_PLAYED
            SORT_KEY_DURATION -> SortKey.DURATION
            SORT_KEY_YEAR -> SortKey.YEAR
            else -> SortKey.TITLE
        }

        _uiState.update { current ->
            current.copy(
                sortKey = sortKey,
                sortDescending = prefsRepo.isLibrarySortedDescending,
            )
        }
    }

    private fun observeBooks() {
        // Observe LiveData from repository
        bookRepository.getAllBooks().observeForever { books ->
            allBooks = books ?: emptyList()
            applyFiltersAndSort()
        }
    }

    private fun observeRefreshState() {
        librarySyncRepository.isRefreshing.observeForever { isRefreshing ->
            _uiState.update { it.copy(isRefreshing = isRefreshing) }
        }
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        val query = state.searchQuery.lowercase()

        var filteredBooks = allBooks

        // Apply search filter
        if (query.isNotEmpty()) {
            filteredBooks = filteredBooks.filter { book ->
                book.title.lowercase().contains(query) ||
                        book.author.lowercase().contains(query)
            }
        }

        // Apply progress filter
        filteredBooks = when (state.progressFilter) {
            ProgressFilter.ALL -> filteredBooks
            ProgressFilter.NOT_STARTED -> filteredBooks.filter {
                it.progress == 0L && it.viewCount == 0L
            }
            ProgressFilter.IN_PROGRESS -> filteredBooks.filter {
                it.progress > 0L && it.progress < it.duration - 120000 // Not within 2 min of end
            }
            ProgressFilter.FINISHED -> filteredBooks.filter {
                it.viewCount > 0L || (it.duration > 0 && it.progress >= it.duration - 120000)
            }
            ProgressFilter.DOWNLOADED -> filteredBooks.filter { it.isCached }
        }

        // Apply sorting
        val sortedBooks = sortBooks(filteredBooks, state.sortKey, state.sortDescending)

        // Convert to UI model
        val libraryBooks = sortedBooks.map { audiobook ->
            audiobook.toLibraryBook()
        }

        _uiState.update { current ->
            current.copy(
                books = libraryBooks,
                isLoading = false,
            )
        }
    }

    private fun sortBooks(
        books: List<Audiobook>,
        sortKey: SortKey,
        descending: Boolean
    ): List<Audiobook> {
        val comparator: Comparator<Audiobook> = when (sortKey) {
            SortKey.TITLE -> compareBy { it.titleSort.lowercase() }
            SortKey.AUTHOR -> compareBy { it.author.lowercase() }
            SortKey.DATE_ADDED -> compareByDescending { it.addedAt }
            SortKey.DATE_PLAYED -> compareByDescending { it.lastViewedAt }
            SortKey.DURATION -> compareByDescending { it.duration }
            SortKey.YEAR -> compareByDescending { it.year }
        }

        return if (descending) {
            books.sortedWith(comparator.reversed())
        } else {
            books.sortedWith(comparator)
        }
    }

    private fun Audiobook.toLibraryBook(): LibraryBook {
        val progress = if (duration > 0) {
            (this.progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Build cover URL with auth token
        val coverUrl = thumb.takeIf { it.isNotEmpty() }?.let {
            plexConfig.makeThumbUri(it).toString()
        }

        return LibraryBook(
            id = id,
            title = title,
            author = author,
            coverUrl = coverUrl,
            progress = progress,
            duration = duration,
            isDownloaded = isCached,
            isPlayed = viewCount > 0,
        )
    }

    // UI Actions

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (!active) {
            setSearchQuery("")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                librarySyncRepository.refreshLibrary()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh library")
            }
        }
    }

    fun showFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = true) }
    }

    fun hideFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = false) }
    }

    fun setSortKey(sortKey: SortKey) {
        val sortKeyString = when (sortKey) {
            SortKey.TITLE -> SORT_KEY_TITLE
            SortKey.AUTHOR -> SORT_KEY_AUTHOR
            SortKey.DATE_ADDED -> SORT_KEY_DATE_ADDED
            SortKey.DATE_PLAYED -> SORT_KEY_DATE_PLAYED
            SortKey.DURATION -> SORT_KEY_DURATION
            SortKey.YEAR -> SORT_KEY_YEAR
        }
        prefsRepo.bookSortKey = sortKeyString
        _uiState.update { it.copy(sortKey = sortKey) }
        applyFiltersAndSort()
    }

    fun toggleSortDirection() {
        val newDescending = !_uiState.value.sortDescending
        prefsRepo.isLibrarySortedDescending = newDescending
        _uiState.update { it.copy(sortDescending = newDescending) }
        applyFiltersAndSort()
    }

    fun setProgressFilter(filter: ProgressFilter) {
        _uiState.update { it.copy(progressFilter = filter) }
        applyFiltersAndSort()
    }

    fun toggleViewMode() {
        _uiState.update { current ->
            current.copy(
                viewMode = if (current.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
            )
        }
    }

    /**
     * Get the audiobook ID for navigation to details.
     */
    fun getAudiobookId(libraryBook: LibraryBook): Int = libraryBook.id

    /**
     * Start playback of a book directly from the library.
     * The mini player will show and start playing.
     */
    fun playBook(libraryBook: LibraryBook) {
        val transportControls = mediaServiceConnection.transportControls
        if (transportControls != null) {
            transportControls.playFromMediaId(libraryBook.id.toString(), null)
            Timber.d("Starting playback of book: ${libraryBook.title}")
        } else {
            // Try to connect first, then play
            mediaServiceConnection.connect {
                mediaServiceConnection.transportControls?.playFromMediaId(libraryBook.id.toString(), null)
                Timber.d("Connected and starting playback of book: ${libraryBook.title}")
            }
        }
    }
}
