package local.oss.chronicle.features.collections.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.CollectionsRepository
import local.oss.chronicle.data.local.LibrarySyncRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.Collection
import local.oss.chronicle.data.sources.plex.PlexConfig
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Compose-based Collections screen.
 *
 * Provides data for:
 * - All collections from the library
 * - Search/filter functionality
 * - Pull-to-refresh
 */
@HiltViewModel
class ComposeCollectionsViewModel @Inject constructor(
    private val collectionsRepository: CollectionsRepository,
    private val librarySyncRepository: LibrarySyncRepository,
    private val plexConfig: PlexConfig,
    private val prefsRepo: PrefsRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState(isLoading = true))
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    // Cache of all collections for filtering
    private var allCollections: List<CollectionItem> = emptyList()

    init {
        observeData()
        loadInitialData()
    }

    private fun observeData() {
        // Observe all collections
        collectionsRepository.getAllCollections().observeForever { collections ->
            val collectionItems = collections?.map { it.toCollectionItem() } ?: emptyList()
            allCollections = collectionItems

            // Apply current search filter
            val filteredCollections = filterCollections(collectionItems, _uiState.value.searchQuery)

            _uiState.update { state ->
                state.copy(
                    collections = filteredCollections,
                    isEmpty = collectionItems.isEmpty(),
                    isLoading = false,
                )
            }
        }

        // Observe refresh state
        librarySyncRepository.isRefreshing.observeForever { refreshing ->
            _uiState.update { it.copy(isRefreshing = refreshing) }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // Check offline mode
                _uiState.update { it.copy(isOfflineMode = prefsRepo.offlineMode) }
            } catch (e: Exception) {
                Timber.e(e, "Error loading initial collections data")
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

    fun setSearchQuery(query: String) {
        val filteredCollections = filterCollections(allCollections, query)
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                collections = filteredCollections,
            )
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { state ->
            if (!active) {
                // When closing search, reset to show all collections
                state.copy(
                    isSearchActive = false,
                    searchQuery = "",
                    collections = allCollections,
                )
            } else {
                state.copy(isSearchActive = true)
            }
        }
    }

    private fun filterCollections(
        collections: List<CollectionItem>,
        query: String,
    ): List<CollectionItem> {
        if (query.isBlank()) return collections

        val lowerQuery = query.lowercase()
        return collections.filter { collection ->
            collection.title.lowercase().contains(lowerQuery)
        }
    }

    private fun Collection.toCollectionItem(): CollectionItem {
        return CollectionItem(
            id = id,
            title = title,
            coverUrl = thumb.takeIf { it.isNotBlank() }?.let { plexConfig.makeThumbUri(it).toString() },
            bookCount = childCount.toInt(),
        )
    }
}
