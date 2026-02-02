package local.oss.chronicle.features.home.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.CollectionsRepository
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.LibrarySyncRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.Audiobook
import local.oss.chronicle.data.model.Collection
import local.oss.chronicle.data.sources.plex.PlexConfig
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Compose-based Home screen.
 *
 * Provides data for:
 * - Featured/hero book (most recently played)
 * - Continue listening (books in progress)
 * - Recently added
 * - Downloaded books
 * - Collections
 */
@HiltViewModel
class ComposeHomeViewModel @Inject constructor(
    private val bookRepository: IBookRepository,
    private val collectionsRepository: CollectionsRepository,
    private val librarySyncRepository: LibrarySyncRepository,
    private val plexConfig: PlexConfig,
    private val prefsRepo: PrefsRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        loadInitialData()
    }

    private fun observeData() {
        // Observe recently listened books
        bookRepository.getRecentlyListened().observeForever { books ->
            val homeBooks = books?.map { it.toHomeBook() } ?: emptyList()
            _uiState.update { state ->
                state.copy(
                    continueListening = homeBooks,
                    // Use first in-progress book as featured if we don't have one
                    featuredBook = state.featuredBook ?: homeBooks.firstOrNull()
                )
            }
        }

        // Observe recently added books
        bookRepository.getRecentlyAdded().observeForever { books ->
            val homeBooks = books?.map { it.toHomeBook() } ?: emptyList()
            _uiState.update { it.copy(recentlyAdded = homeBooks) }
        }

        // Observe downloaded books
        bookRepository.getCachedAudiobooks().observeForever { books ->
            val homeBooks = books?.map { it.toHomeBook() } ?: emptyList()
            _uiState.update { it.copy(downloaded = homeBooks) }
        }

        // Observe collections
        collectionsRepository.getAllCollections().observeForever { collections ->
            val homeCollections = collections?.map { it.toHomeCollection() } ?: emptyList()
            _uiState.update { it.copy(collections = homeCollections) }
        }

        // Observe refresh state
        librarySyncRepository.isRefreshing.observeForever { refreshing ->
            _uiState.update { it.copy(isRefreshing = refreshing) }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // Get most recently played book for featured section
                val mostRecent = bookRepository.getMostRecentlyPlayed()
                if (mostRecent.id > 0) {
                    _uiState.update { state ->
                        state.copy(
                            featuredBook = mostRecent.toHomeBook(),
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }

                // Check offline mode
                _uiState.update { it.copy(isOfflineMode = prefsRepo.offlineMode) }

            } catch (e: Exception) {
                Timber.e(e, "Error loading initial home data")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                librarySyncRepository.refreshLibrary()
                // isRefreshing will be updated via observer
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing library")
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun playFeaturedBook() {
        val featuredBook = _uiState.value.featuredBook
        if (featuredBook != null) {
            Timber.i("Play featured book: ${featuredBook.title}")
            // Playback will be handled by the Fragment via callback
        }
    }

    private fun Audiobook.toHomeBook(): HomeBook {
        val progressPercent = if (duration > 0) {
            (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        return HomeBook(
            id = id,
            title = title,
            author = author,
            coverUrl = thumb?.let { plexConfig.makeThumbUri(it).toString() },
            coverUrlHighRes = thumb?.let { plexConfig.makeHighResThumbUri(it).toString() },
            progress = progressPercent,
            duration = duration,
            isDownloaded = isCached,
            lastPlayedAt = lastViewedAt,
        )
    }

    private fun Collection.toHomeCollection(): HomeCollection {
        return HomeCollection(
            id = id,
            title = title,
            coverUrl = thumb?.let { plexConfig.makeThumbUri(it).toString() },
            bookCount = childCount.toInt(),
        )
    }
}
