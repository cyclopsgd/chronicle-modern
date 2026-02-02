package local.oss.chronicle.features.collections.compose

import androidx.lifecycle.SavedStateHandle
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
import local.oss.chronicle.data.model.Audiobook
import local.oss.chronicle.data.sources.plex.PlexConfig
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Compose-based Collection Details screen.
 *
 * Provides data for:
 * - Collection title
 * - Books within the collection
 */
@HiltViewModel
class ComposeCollectionDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionsRepository: CollectionsRepository,
    private val bookRepository: IBookRepository,
    private val plexConfig: PlexConfig,
) : ViewModel() {

    private val collectionId: Int = savedStateHandle.get<Int>(ARG_COLLECTION_ID)
        ?: throw IllegalArgumentException("Collection ID is required")

    private val _uiState = MutableStateFlow(CollectionDetailsUiState(isLoading = true))
    val uiState: StateFlow<CollectionDetailsUiState> = _uiState.asStateFlow()

    init {
        loadCollectionData()
    }

    private fun loadCollectionData() {
        viewModelScope.launch {
            try {
                // Observe collection title
                collectionsRepository.getCollection(collectionId).observeForever { collection ->
                    collection?.let {
                        _uiState.update { state ->
                            state.copy(collectionTitle = it.title)
                        }
                    }
                }

                // Load books in the collection
                loadBooks()
            } catch (e: Exception) {
                Timber.e(e, "Error loading collection data")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadBooks() {
        try {
            val childIds = collectionsRepository.getChildIds(collectionId)
            Timber.d("Loading books for collection $collectionId, childIds: $childIds")

            val books = childIds.mapNotNull { bookId ->
                bookRepository.getAudiobookAsync(bookId.toInt())?.toCollectionBook()
            }

            _uiState.update { state ->
                state.copy(
                    books = books,
                    isLoading = false,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading books for collection")
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun Audiobook.toCollectionBook(): CollectionBook {
        val progressPercent = if (duration > 0) {
            (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        return CollectionBook(
            id = id,
            title = title,
            author = author,
            coverUrl = thumb?.let { plexConfig.makeThumbUri(it).toString() },
            progress = progressPercent,
            isDownloaded = isCached,
        )
    }

    companion object {
        const val ARG_COLLECTION_ID = "collection_id"
    }
}
