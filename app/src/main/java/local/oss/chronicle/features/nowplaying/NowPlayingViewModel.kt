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
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingSingleton
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.MediaPlayerService.Companion.KEY_SEEK_TO_TRACK_WITH_ID
import local.oss.chronicle.features.player.MediaPlayerService.Companion.KEY_START_TIME_TRACK_OFFSET
import local.oss.chronicle.features.player.SKIP_BACKWARDS_STRING
import local.oss.chronicle.features.player.SKIP_FORWARDS_STRING
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
    }

    private fun observePlaybackState() {
        // Use LiveData observer for MediaServiceConnection
        mediaServiceConnection.playbackState.observeForever { state ->
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            val absolutePosition = state?.position ?: 0L

            // Convert to chapter-relative position
            val chapter = currentlyPlaying.chapter.value
            val chapterRelativePosition = (absolutePosition - chapter.startTimeOffset)
                .coerceAtLeast(0L)

            _uiState.update { current ->
                current.copy(
                    isPlaying = isPlaying,
                    currentPositionMs = chapterRelativePosition,
                )
            }
        }

        mediaServiceConnection.isConnected.observeForever { connected ->
            Timber.d("NowPlayingViewModel: MediaService connected = $connected")
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
            }
        }

        viewModelScope.launch {
            currentlyPlaying.track.collect { track ->
                _uiState.update { current ->
                    current.copy(
                        durationMs = track.duration,
                    )
                }
            }
        }

        viewModelScope.launch {
            currentlyPlaying.chapter.collect { chapter ->
                _uiState.update { current ->
                    current.copy(
                        chapterTitle = chapter.title,
                        currentChapterIndex = chapter.index.toInt(),
                        // Chapter-scoped duration
                        durationMs = chapter.endTimeOffset - chapter.startTimeOffset,
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
        mediaServiceConnection.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        mediaServiceConnection.transportControls?.skipToPrevious()
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

    fun showSpeedSelector() {
        // Will trigger bottom sheet in fragment/activity
        // For now, cycle through common speeds
        val currentSpeed = _uiState.value.playbackSpeed
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + 1) % speeds.size
        val newSpeed = speeds[nextIndex]

        prefsRepo.playbackSpeed = newSpeed
        _uiState.update { it.copy(playbackSpeed = newSpeed) }
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

        // Jump to the chapter using custom action
        val extras = Bundle().apply {
            putInt(KEY_SEEK_TO_TRACK_WITH_ID, chapter.trackId.toInt())
            putLong(KEY_START_TIME_TRACK_OFFSET, chapter.startTimeOffset)
        }
        transportControls.sendCustomAction("JUMP_TO_CHAPTER", extras)
    }

    override fun onCleared() {
        super.onCleared()
        localBroadcastManager.unregisterReceiver(sleepTimerReceiver)
    }
}
