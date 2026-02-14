package local.oss.chronicle.features.bookdetails.compose

import android.os.Bundle
import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.Audiobook
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.data.model.MediaItemTrack
import local.oss.chronicle.data.model.asChapterList
import local.oss.chronicle.data.model.getProgress
import local.oss.chronicle.data.sources.plex.ICachedFileManager
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexMediaService
import local.oss.chronicle.data.sources.plex.model.getDuration
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.player.MediaPlayerService
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.id
import local.oss.chronicle.features.player.isPlaying
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for the Book Details screen.
 */
@Immutable
data class BookDetailsUiState(
    val book: BookDetail? = null,
    val chapters: List<ChapterItem> = emptyList(),
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isBookActive: Boolean = false,
    val isSummaryExpanded: Boolean = false,
    val cacheStatus: CacheStatus = CacheStatus.NOT_CACHED,
    val connectionState: ConnectionState = ConnectionState.CONNECTED,
    val currentChapterIndex: Int = -1,
    val progressString: String = "0:00 / 0:00",
    val progressPercent: Int = 0,
    val isSyncing: Boolean = false,
    val userMessage: String? = null,
)

/**
 * Simplified book model for the Book Details UI.
 */
@Immutable
data class BookDetail(
    val id: Int,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val summary: String,
    val duration: Long,
    val progress: Long,
    val isWatched: Boolean,
)

/**
 * Chapter item model for the chapter list.
 */
@Immutable
data class ChapterItem(
    val id: Long,
    val trackId: Long,
    val title: String,
    val startTimeOffset: Long,
    val durationMs: Long,
    val durationString: String,
    val isCurrentChapter: Boolean = false,
)

enum class CacheStatus { NOT_CACHED, CACHING, CACHED }
enum class ConnectionState { CONNECTED, CONNECTING, CONNECTION_FAILED }

/**
 * ViewModel for the Compose-based Book Details screen.
 *
 * Provides data for:
 * - Book header with cover, title, author, progress
 * - Expandable summary
 * - Chapter list with current chapter highlighting
 * - Playback controls
 * - Download/cache management
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComposeBookDetailsViewModel @Inject constructor(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val cachedFileManager: ICachedFileManager,
    private val mediaServiceConnection: MediaServiceConnection,
    private val plexConfig: PlexConfig,
    private val prefsRepo: PrefsRepo,
    private val plexMediaService: PlexMediaService,
    private val currentlyPlaying: CurrentlyPlaying,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailsUiState(isLoading = true))
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    private var bookId: Int = -1
    private var currentAudiobook: Audiobook? = null
    private var currentTracks: List<MediaItemTrack> = emptyList()
    private var currentChapters: List<Chapter> = emptyList()

    /**
     * Initialize the ViewModel with the book ID.
     * This should be called from the Fragment after the ViewModel is created.
     */
    fun loadBook(bookId: Int, bookTitle: String, isCached: Boolean) {
        this.bookId = bookId

        _uiState.update { it.copy(isLoading = true) }

        // Observe the audiobook from repository
        observeAudiobook(bookId)

        // Observe tracks and chapters
        observeTracks(bookId)

        // Observe playback state
        observePlaybackState()

        // Observe cache status
        observeCacheStatus(bookId)

        // Observe connection state
        observeConnectionState()

        // Observe currently playing chapter
        observeCurrentlyPlaying()

        // Load book details from network
        loadBookDetails(bookId)
    }

    private fun observeAudiobook(bookId: Int) {
        bookRepository.getAudiobook(bookId).observeForever { audiobook ->
            if (audiobook != null) {
                currentAudiobook = audiobook
                updateBookDetail(audiobook)
                updateChaptersFromAudiobook(audiobook)
            }
        }
    }

    private fun observeTracks(bookId: Int) {
        trackRepository.getTracksForAudiobook(bookId).observeForever { tracks ->
            if (tracks != null) {
                currentTracks = tracks
                updateProgressFromTracks(tracks)
                // If no chapters from audiobook, generate from tracks
                if (currentChapters.isEmpty() && tracks.isNotEmpty()) {
                    viewModelScope.launch {
                        val chapters = tracks.asChapterList()
                        updateChaptersFromList(chapters)
                    }
                }
            }
        }
    }

    private fun observePlaybackState() {
        mediaServiceConnection.playbackState.observeForever { state ->
            val isPlaying = state?.isPlaying ?: false
            val isBookActive = currentAudiobook?.let { book ->
                mediaServiceConnection.nowPlaying.value?.id == book.id.toString()
            } ?: false

            _uiState.update {
                it.copy(
                    isPlaying = isPlaying && isBookActive,
                    isBookActive = isBookActive
                )
            }
        }

        mediaServiceConnection.nowPlaying.observeForever { metadata ->
            val isBookActive = currentAudiobook?.let { book ->
                metadata?.id == book.id.toString()
            } ?: false
            val isPlaying = mediaServiceConnection.playbackState.value?.isPlaying ?: false

            _uiState.update {
                it.copy(
                    isPlaying = isPlaying && isBookActive,
                    isBookActive = isBookActive
                )
            }
        }
    }

    private fun observeCacheStatus(bookId: Int) {
        cachedFileManager.activeBookDownloads.observeForever { activeDownloads ->
            val isDownloading = bookId in (activeDownloads ?: emptySet())
            val isCached = currentAudiobook?.isCached ?: false

            val status = when {
                isCached -> CacheStatus.CACHED
                isDownloading -> CacheStatus.CACHING
                else -> CacheStatus.NOT_CACHED
            }

            _uiState.update { it.copy(cacheStatus = status) }
        }
    }

    private fun observeConnectionState() {
        plexConfig.connectionState.observeForever { state ->
            val connectionState = when (state) {
                PlexConfig.ConnectionState.CONNECTED -> ConnectionState.CONNECTED
                PlexConfig.ConnectionState.CONNECTING -> ConnectionState.CONNECTING
                PlexConfig.ConnectionState.CONNECTION_FAILED -> ConnectionState.CONNECTION_FAILED
                PlexConfig.ConnectionState.NOT_CONNECTED -> ConnectionState.CONNECTION_FAILED
                null -> ConnectionState.CONNECTION_FAILED
            }
            _uiState.update { it.copy(connectionState = connectionState) }
        }
    }

    private fun observeCurrentlyPlaying() {
        viewModelScope.launch {
            currentlyPlaying.chapter.collect { chapter ->
                if (chapter.bookId == bookId.toLong()) {
                    updateCurrentChapter(chapter)
                }
            }
        }
    }

    private fun loadBookDetails(bookId: Int) {
        viewModelScope.launch {
            try {
                val trackResult = trackRepository.loadTracksForAudiobook(bookId)
                if (trackResult is com.github.michaelbull.result.Ok) {
                    val audiobook = bookRepository.getAudiobookAsync(bookId)
                    audiobook?.let {
                        trackRepository.syncTracksInBook(it.id)
                        bookRepository.syncAudiobook(it, trackResult.value)
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Throwable) {
                Timber.e("Failed to load book details for $bookId: $e")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun updateBookDetail(audiobook: Audiobook) {
        val coverUrl = audiobook.thumb.takeIf { it.isNotEmpty() }?.let {
            plexConfig.makeHighResThumbUri(it).toString()
        }

        val bookDetail = BookDetail(
            id = audiobook.id,
            title = audiobook.title,
            author = audiobook.author,
            coverUrl = coverUrl,
            summary = audiobook.summary,
            duration = audiobook.duration,
            progress = audiobook.progress,
            isWatched = audiobook.viewCount > 0,
        )

        // Also update cache status based on audiobook
        val cacheStatus = when {
            audiobook.isCached -> CacheStatus.CACHED
            bookId in (cachedFileManager.activeBookDownloads.value ?: emptySet()) -> CacheStatus.CACHING
            else -> CacheStatus.NOT_CACHED
        }

        _uiState.update {
            it.copy(
                book = bookDetail,
                cacheStatus = cacheStatus,
            )
        }
    }

    private fun updateProgressFromTracks(tracks: List<MediaItemTrack>) {
        if (tracks.isEmpty()) {
            _uiState.update {
                it.copy(
                    progressString = "0:00 / 0:00",
                    progressPercent = 0
                )
            }
            return
        }

        val progress = tracks.getProgress()
        val duration = tracks.getDuration()

        val progressStr = DateUtils.formatElapsedTime(StringBuilder(), progress / 1000L)
        val durationStr = DateUtils.formatElapsedTime(StringBuilder(), duration / 1000L)
        val percent = if (duration > 0) ((progress * 100) / duration).toInt() else 0

        _uiState.update {
            it.copy(
                progressString = "$progressStr / $durationStr",
                progressPercent = percent
            )
        }
    }

    private fun updateChaptersFromAudiobook(audiobook: Audiobook) {
        if (audiobook.chapters.isNotEmpty()) {
            currentChapters = audiobook.chapters
            updateChaptersFromList(audiobook.chapters)
        }
    }

    private fun updateChaptersFromList(chapters: List<Chapter>) {
        currentChapters = chapters
        val currentChapterId = currentlyPlaying.chapter.value.id

        val chapterItems = chapters.mapIndexed { index, chapter ->
            val durationMs = chapter.endTimeOffset - chapter.startTimeOffset
            ChapterItem(
                id = chapter.id,
                trackId = chapter.trackId,
                title = chapter.title.ifEmpty { "Chapter ${index + 1}" },
                startTimeOffset = chapter.startTimeOffset,
                durationMs = durationMs,
                durationString = chapter.durationStr,
                isCurrentChapter = chapter.id == currentChapterId,
            )
        }

        val currentIndex = chapterItems.indexOfFirst { it.isCurrentChapter }

        _uiState.update {
            it.copy(
                chapters = chapterItems,
                currentChapterIndex = currentIndex,
                isLoading = false,
            )
        }
    }

    private fun updateCurrentChapter(chapter: Chapter) {
        val updatedChapters = _uiState.value.chapters.map { item ->
            item.copy(isCurrentChapter = item.id == chapter.id)
        }
        val currentIndex = updatedChapters.indexOfFirst { it.isCurrentChapter }

        _uiState.update {
            it.copy(
                chapters = updatedChapters,
                currentChapterIndex = currentIndex
            )
        }
    }

    /**
     * Toggle play/pause for the current book.
     */
    fun playPause() {
        val audiobook = currentAudiobook ?: return

        if (plexConfig.isConnected.value != true && !audiobook.isCached) {
            _uiState.update { it.copy(userMessage = "Cannot play media: not connected to server") }
            return
        }

        val action: () -> Unit = action@{
            val transportControls = mediaServiceConnection.transportControls ?: return@action
            val isBookActive = _uiState.value.isBookActive
            val isPlaying = _uiState.value.isPlaying

            val extras = Bundle().apply {
                putLong(MediaPlayerService.KEY_START_TIME_TRACK_OFFSET, MediaPlayerService.USE_SAVED_TRACK_PROGRESS)
                putLong(MediaPlayerService.KEY_SEEK_TO_TRACK_WITH_ID, MediaPlayerService.ACTIVE_TRACK)
            }

            when {
                isPlaying -> transportControls.pause()
                isBookActive -> transportControls.play()
                else -> transportControls.playFromMediaId(audiobook.id.toString(), extras)
            }
        }

        if (mediaServiceConnection.isConnected.value != true) {
            mediaServiceConnection.connect(action)
        } else {
            action()
        }
    }

    /**
     * Jump to a specific chapter.
     */
    fun jumpToChapter(chapter: ChapterItem) {
        val audiobook = currentAudiobook ?: return

        val action: () -> Unit = action@{
            val transportControls = mediaServiceConnection.transportControls ?: return@action
            val extras = Bundle().apply {
                putLong(MediaPlayerService.KEY_START_TIME_TRACK_OFFSET, chapter.startTimeOffset)
                putLong(MediaPlayerService.KEY_SEEK_TO_TRACK_WITH_ID, chapter.trackId)
            }
            transportControls.playFromMediaId(audiobook.id.toString(), extras)
        }

        if (mediaServiceConnection.isConnected.value != true) {
            mediaServiceConnection.connect(action)
        } else {
            action()
        }
    }

    /**
     * Toggle the summary expanded state.
     */
    fun toggleSummary() {
        _uiState.update { it.copy(isSummaryExpanded = !it.isSummaryExpanded) }
    }

    /**
     * Handle cache button click - download, cancel, or prompt to delete.
     */
    fun onCacheButtonClick() {
        if (!prefsRepo.isPremium) {
            _uiState.update { it.copy(userMessage = "Premium required for offline playback") }
            return
        }

        when (_uiState.value.cacheStatus) {
            CacheStatus.NOT_CACHED -> {
                if (plexConfig.isConnected.value != true) {
                    _uiState.update { it.copy(userMessage = "Cannot download: not connected to server") }
                } else {
                    currentAudiobook?.let { book ->
                        cachedFileManager.downloadTracks(book.id, book.title)
                    }
                }
            }
            CacheStatus.CACHED -> {
                // For now, just delete. A proper implementation would show a confirmation dialog.
                viewModelScope.launch {
                    currentAudiobook?.let { book ->
                        cachedFileManager.deleteCachedBook(book.id)
                    }
                }
            }
            CacheStatus.CACHING -> {
                currentAudiobook?.let { book ->
                    cachedFileManager.cancelGroup(book.id)
                }
            }
        }
    }

    /**
     * Toggle the watched/unplayed state of the book.
     */
    fun toggleWatched() {
        viewModelScope.launch {
            val audiobook = currentAudiobook ?: return@launch
            val isWatched = audiobook.viewCount > 0

            if (isWatched) {
                bookRepository.setUnwatched(audiobook.id)
            } else {
                trackRepository.markTracksInBookAsWatched(audiobook.id)
                bookRepository.setWatched(audiobook.id)
            }
        }
    }

    /**
     * Force sync book data from the server.
     */
    fun forceSyncBook() {
        if (plexConfig.isConnected.value != true) {
            _uiState.update { it.copy(userMessage = "Cannot sync: not connected to server") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            try {
                val audiobook = currentAudiobook ?: return@launch
                val updatedTracks = trackRepository.syncTracksInBook(audiobook.id, forceUseNetwork = true)
                val success = bookRepository.syncAudiobook(audiobook, updatedTracks, forceNetwork = true)

                if (success) {
                    _uiState.update { it.copy(userMessage = "Sync successful") }
                } else {
                    _uiState.update { it.copy(userMessage = "Sync failed") }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync book")
                _uiState.update { it.copy(userMessage = "Sync failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    /**
     * Retry connecting to the server.
     */
    fun retryConnection() {
        viewModelScope.launch {
            plexConfig.connectToServerWithRetry(plexMediaService)
        }
    }

    /**
     * Clear any pending user message.
     */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
