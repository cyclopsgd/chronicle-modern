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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.player.MediaServiceConnection
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
            val position = state?.position ?: 0L

            _uiState.update { current ->
                current.copy(
                    isPlaying = isPlaying,
                    currentPositionMs = position,
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
                val coverUrl = book.thumb?.let { thumb ->
                    plexConfig.toServerString("photo/:/transcode?width=600&height=600&url=$thumb")
                }

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
                        // Chapter-scoped duration
                        durationMs = chapter.endTimeOffset - chapter.startTimeOffset,
                    )
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
        // Will trigger bottom sheet in fragment/activity
        // For now, toggle between 15 min and off
        val isActive = _uiState.value.isSleepTimerActive

        val intent = Intent(SleepTimer.ACTION_SLEEP_TIMER_CHANGE).apply {
            if (isActive) {
                putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.CANCEL)
            } else {
                putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.BEGIN)
                putExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, 15 * 60 * 1000L)
            }
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    fun toggleBookmark() {
        // TODO: Implement bookmark functionality
        _uiState.update { it.copy(isBookmarked = !it.isBookmarked) }
    }

    fun showChapterList() {
        // Will trigger bottom sheet in fragment/activity
        Timber.d("Show chapter list requested")
    }

    override fun onCleared() {
        super.onCleared()
        localBroadcastManager.unregisterReceiver(sleepTimerReceiver)
    }
}
