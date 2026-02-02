package local.oss.chronicle.features.nowplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingSingleton
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.SKIP_BACKWARDS_STRING
import local.oss.chronicle.features.player.SKIP_FORWARDS_STRING
import local.oss.chronicle.features.player.SKIP_TO_NEXT_STRING
import local.oss.chronicle.features.player.SKIP_TO_PREVIOUS_STRING
import local.oss.chronicle.features.player.SleepTimer
import local.oss.chronicle.features.player.SleepTimer.SleepTimerAction
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Compose-based Now Playing screen.
 *
 * Bridges the existing playback infrastructure (MediaServiceConnection, CurrentlyPlaying)
 * to the new Compose UI via StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaServiceConnection: MediaServiceConnection,
    private val currentlyPlaying: CurrentlyPlaying,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig,
    private val localBroadcastManager: LocalBroadcastManager,
    private val bookRepository: IBookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    // Events for UI actions that require Fragment/Activity handling
    private val _events = MutableSharedFlow<NowPlayingEvent>()
    val events: SharedFlow<NowPlayingEvent> = _events.asSharedFlow()

    sealed class NowPlayingEvent {
        object ShowChapterList : NowPlayingEvent()
        object ShowSpeedSelector : NowPlayingEvent()
        object ShowSleepTimerOptions : NowPlayingEvent()
    }

    private var sleepTimerRemainingMs: Long = 0L
    private var currentBookId: Int = -1

    private val sleepTimerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val action = IntentCompat.getSerializableExtra(
                intent,
                SleepTimer.ARG_SLEEP_TIMER_ACTION,
                SleepTimerAction::class.java
            )
            val durationMillis = intent.getLongExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, 0L)

            when (action) {
                SleepTimerAction.BEGIN, SleepTimerAction.EXTEND -> {
                    sleepTimerRemainingMs = durationMillis
                    updateSleepTimerState(true, durationMillis)
                }
                SleepTimerAction.UPDATE -> {
                    sleepTimerRemainingMs = durationMillis
                    updateSleepTimerState(durationMillis > 0, durationMillis)
                }
                SleepTimerAction.CANCEL -> {
                    sleepTimerRemainingMs = 0L
                    updateSleepTimerState(false, 0L)
                }
                null -> {}
            }
        }
    }

    init {
        // Register sleep timer receiver
        localBroadcastManager.registerReceiver(
            sleepTimerReceiver,
            IntentFilter(SleepTimer.ACTION_SLEEP_TIMER_CHANGE)
        )

        // Observe playback state from MediaServiceConnection (LiveData)
        observePlaybackState()

        // Observe metadata from CurrentlyPlaying (StateFlow)
        observeCurrentlyPlaying()

        // Set initial skip seconds from prefs
        updateSkipSeconds()

        // Set initial playback speed from prefs
        _uiState.update { it.copy(playbackSpeed = prefsRepo.playbackSpeed) }
    }

    private fun observePlaybackState() {
        // Use LiveData observer for MediaServiceConnection - only for isPlaying state
        mediaServiceConnection.playbackState.observeForever { state ->
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            _uiState.update { current ->
                current.copy(isPlaying = isPlaying)
            }
        }

        mediaServiceConnection.isConnected.observeForever { connected ->
            Timber.d("NowPlayingViewModel: MediaService connected = $connected")
        }

        // Observe position from PlaybackStateController via CurrentlyPlayingSingleton
        // This ensures chapter and position are always in sync
        if (currentlyPlaying is CurrentlyPlayingSingleton) {
            viewModelScope.launch {
                currentlyPlaying.state.collect { playbackState ->
                    // Use the controller's computed chapter-relative position
                    val chapterPositionMs = playbackState.currentChapterPositionMs
                    val chapterDurationMs = playbackState.currentChapterDurationMs

                    _uiState.update { current ->
                        current.copy(
                            currentPositionMs = chapterPositionMs,
                            durationMs = chapterDurationMs,
                        )
                    }
                }
            }
        }
    }

    private fun observeCurrentlyPlaying() {
        // Observe StateFlows from CurrentlyPlaying
        viewModelScope.launch {
            currentlyPlaying.book.collect { book ->
                // Build high-resolution cover URL with auth token
                // Using 1000x1000 for crisp display on high-density screens
                val coverUrl = book.thumb?.let { thumb ->
                    plexConfig.makeHighResThumbUri(thumb).toString()
                }
                Timber.d("Cover URL for ${book.title}: $coverUrl")

                _uiState.update { current ->
                    current.copy(
                        bookTitle = book.title,
                        author = book.author,
                        coverArtUrl = coverUrl,
                    )
                }

                // Load per-book speed when book changes
                if (book.id != currentBookId && book.id > 0) {
                    currentBookId = book.id
                    loadBookPlaybackSpeed(book.id)
                }
            }
        }

        viewModelScope.launch {
            currentlyPlaying.chapter.collect { chapter ->
                // Update chapter title and index (duration is handled by state observer)
                _uiState.update { current ->
                    current.copy(
                        chapterTitle = chapter.title,
                        currentChapterIndex = chapter.index.toInt(),
                    )
                }
            }
        }

        // Observe chapters list from PlaybackStateController via CurrentlyPlayingSingleton
        if (currentlyPlaying is CurrentlyPlayingSingleton) {
            viewModelScope.launch {
                currentlyPlaying.state.collect { state ->
                    _uiState.update { current ->
                        current.copy(
                            chapters = state.chapters,
                        )
                    }
                }
            }
        }
    }

    private fun updateSkipSeconds() {
        _uiState.update { current ->
            current.copy(
                skipBackwardSeconds = prefsRepo.jumpBackwardSeconds.toInt(),
                skipForwardSeconds = prefsRepo.jumpForwardSeconds.toInt(),
            )
        }
    }

    private fun updateSleepTimerState(active: Boolean, remainingMs: Long) {
        _uiState.update { current ->
            current.copy(
                isSleepTimerActive = active,
                sleepTimerRemainingMs = remainingMs,
            )
        }
    }

    // Playback controls

    fun playPause() {
        val transportControls = mediaServiceConnection.transportControls ?: return
        val isPlaying = _uiState.value.isPlaying

        if (isPlaying) {
            transportControls.pause()
        } else {
            transportControls.play()
        }
    }

    fun skipForward() {
        val transportControls = mediaServiceConnection.transportControls ?: return
        transportControls.sendCustomAction(SKIP_FORWARDS_STRING, Bundle.EMPTY)
    }

    fun skipBackward() {
        val transportControls = mediaServiceConnection.transportControls ?: return
        transportControls.sendCustomAction(SKIP_BACKWARDS_STRING, Bundle.EMPTY)
    }

    fun skipToNext() {
        // Use custom action for chapter skip instead of track skip
        val transportControls = mediaServiceConnection.transportControls ?: return
        transportControls.sendCustomAction(SKIP_TO_NEXT_STRING, Bundle.EMPTY)
    }

    fun skipToPrevious() {
        // Use custom action for chapter skip instead of track skip
        val transportControls = mediaServiceConnection.transportControls ?: return
        transportControls.sendCustomAction(SKIP_TO_PREVIOUS_STRING, Bundle.EMPTY)
    }

    fun seekTo(progress: Float) {
        val transportControls = mediaServiceConnection.transportControls ?: return
        val chapter = currentlyPlaying.chapter.value

        // Calculate chapter-relative seek position
        val chapterDuration = chapter.endTimeOffset - chapter.startTimeOffset
        val chapterPosition = (progress * chapterDuration).toLong()
        val absolutePosition = chapter.startTimeOffset + chapterPosition

        transportControls.seekTo(absolutePosition)
    }

    companion object {
        // Preset speeds for cycling through
        val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    }

    fun showSpeedSelector() {
        _uiState.update { it.copy(showSpeedSelector = true) }
    }

    fun hideSpeedSelector() {
        _uiState.update { it.copy(showSpeedSelector = false) }
    }

    /**
     * Cycle to the next speed in the preset list (tap behavior).
     */
    fun cycleSpeedForward() {
        val currentSpeed = _uiState.value.playbackSpeed
        val currentIndex = SPEED_PRESETS.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= SPEED_PRESETS.lastIndex) {
            0 // Wrap to beginning
        } else {
            currentIndex + 1
        }
        setPlaybackSpeed(SPEED_PRESETS[nextIndex])
    }

    /**
     * Reset playback speed to 1.0x (long-press behavior).
     */
    fun resetSpeedTo1x() {
        setPlaybackSpeed(1.0f)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)

        // Update UI state first (before hiding selector to avoid flash of old value)
        _uiState.update { it.copy(playbackSpeed = clampedSpeed, showSpeedSelector = false) }

        // Save per-book speed
        if (currentBookId > 0) {
            viewModelScope.launch {
                bookRepository.updatePlaybackSpeed(currentBookId, clampedSpeed)
            }
        }

        // Also update global prefs
        prefsRepo.playbackSpeed = clampedSpeed

        // Send speed change to player service via custom action
        val transportControls = mediaServiceConnection.transportControls ?: return
        val bundle = Bundle().apply {
            putFloat("PLAYBACK_SPEED", clampedSpeed)
        }
        transportControls.sendCustomAction("SET_PLAYBACK_SPEED", bundle)
    }

    private fun loadBookPlaybackSpeed(bookId: Int) {
        viewModelScope.launch {
            val bookSpeed = bookRepository.getPlaybackSpeed(bookId)
            val effectiveSpeed = bookSpeed ?: prefsRepo.playbackSpeed
            prefsRepo.playbackSpeed = effectiveSpeed
            _uiState.update { it.copy(playbackSpeed = effectiveSpeed) }
            Timber.d("Loaded playback speed for book $bookId: $effectiveSpeed (per-book: $bookSpeed)")
        }
    }

    fun showSleepTimerOptions() {
        _uiState.update { it.copy(showSleepTimer = true) }
    }

    fun hideSleepTimer() {
        _uiState.update { it.copy(showSleepTimer = false) }
    }

    fun handleSleepTimerOption(option: SleepTimerOption) {
        hideSleepTimer()

        val intent = Intent(SleepTimer.ACTION_SLEEP_TIMER_CHANGE).apply {
            when (option) {
                is SleepTimerOption.Cancel -> {
                    putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.CANCEL)
                }
                is SleepTimerOption.Extend5 -> {
                    putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.EXTEND)
                    putExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, option.durationMs)
                }
                is SleepTimerOption.EndOfChapter -> {
                    // Calculate time to end of current chapter
                    val chapter = currentlyPlaying.chapter.value
                    val currentPosition = _uiState.value.currentPositionMs
                    val chapterDuration = chapter.endTimeOffset - chapter.startTimeOffset
                    val remainingInChapter = (chapterDuration - currentPosition).coerceAtLeast(0L)

                    putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.BEGIN)
                    putExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, remainingInChapter)
                }
                else -> {
                    // Standard duration preset
                    putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.BEGIN)
                    putExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, option.durationMs)
                }
            }
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    fun toggleBookmark() {
        // TODO: Implement bookmark functionality
        _uiState.update { it.copy(isBookmarked = !it.isBookmarked) }
    }

    fun showChapterList() {
        _uiState.update { it.copy(showChapterList = true) }
    }

    fun hideChapterList() {
        _uiState.update { it.copy(showChapterList = false) }
    }

    fun jumpToChapter(chapter: Chapter) {
        val transportControls = mediaServiceConnection.transportControls ?: return

        // Hide the chapter list
        hideChapterList()

        // Find chapter index and send custom action to seek to it
        val chapters = _uiState.value.chapters
        val chapterIndex = chapters.indexOfFirst {
            it.trackId == chapter.trackId && it.startTimeOffset == chapter.startTimeOffset
        }

        if (chapterIndex >= 0) {
            Timber.d("Jumping to chapter $chapterIndex: ${chapter.title}")
            val extras = Bundle().apply {
                putInt("CHAPTER_INDEX", chapterIndex)
            }
            transportControls.sendCustomAction("SEEK_TO_CHAPTER", extras)
        } else {
            Timber.w("Could not find chapter index for: ${chapter.title}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        localBroadcastManager.unregisterReceiver(sleepTimerReceiver)
    }
}
