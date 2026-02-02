package local.oss.chronicle.features.player.media3

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Media3 client connection for the MediaLibraryService.
 *
 * This replaces the legacy [MediaServiceConnection] which used
 * MediaBrowserCompat and MediaControllerCompat.
 *
 * Key differences:
 * - Uses SessionToken and MediaController.Builder
 * - Direct access to Player interface (no TransportControls)
 * - StateFlow-based state observation (modern Kotlin approach)
 * - Simplified connection handling
 */
@OptIn(UnstableApi::class)
class Media3ServiceConnection @Inject constructor(
    private val applicationContext: Context,
    private val serviceComponent: ComponentName
) {
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // LiveData for backward compatibility with existing ViewModels
    val isConnectedLiveData = MutableLiveData(false)

    // Playback state
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Current media item
    private val _nowPlaying = MutableStateFlow<MediaItem?>(null)
    val nowPlaying: StateFlow<MediaItem?> = _nowPlaying.asStateFlow()

    // Legacy LiveData for backward compatibility
    val nowPlayingLiveData = MutableLiveData<MediaMetadata?>(null)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updatePlaybackState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlaybackState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            Timber.d("Media3 metadata changed: ${mediaMetadata.title}")
            _nowPlaying.value = mediaController?.currentMediaItem
            nowPlayingLiveData.postValue(mediaMetadata)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Timber.d("Media3 media item transition: ${mediaItem?.mediaId}")
            _nowPlaying.value = mediaItem
            nowPlayingLiveData.postValue(mediaItem?.mediaMetadata)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Media3 player error")
            _playbackState.value = _playbackState.value.copy(
                error = error.message
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePlaybackState()
        }
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return

        _playbackState.value = PlaybackState(
            isPlaying = controller.isPlaying,
            isPaused = controller.playbackState == Player.STATE_READY && !controller.playWhenReady,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            position = controller.currentPosition,
            duration = controller.duration.coerceAtLeast(0L),
            playbackSpeed = controller.playbackParameters.speed,
            currentMediaItemIndex = controller.currentMediaItemIndex,
            playerState = controller.playbackState
        )
    }

    /**
     * Connect to the Media3 service.
     */
    fun connect() {
        if (_isConnected.value) {
            Timber.d("Media3 already connected")
            return
        }

        Timber.i("Connecting to Media3 service...")
        val sessionToken = SessionToken(applicationContext, serviceComponent)

        controllerFuture = MediaController.Builder(applicationContext, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)

                _isConnected.value = true
                isConnectedLiveData.postValue(true)

                // Initialize state
                updatePlaybackState()
                _nowPlaying.value = mediaController?.currentMediaItem
                nowPlayingLiveData.postValue(mediaController?.mediaMetadata)

                Timber.i("Media3 connected successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to Media3 service")
                _isConnected.value = false
                isConnectedLiveData.postValue(false)
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Connect with a callback when connected.
     */
    fun connect(onConnected: () -> Unit) {
        if (_isConnected.value) {
            Timber.d("Media3 already connected, invoking callback")
            onConnected()
            return
        }

        Timber.i("Connecting to Media3 service with callback...")
        val sessionToken = SessionToken(applicationContext, serviceComponent)

        controllerFuture = MediaController.Builder(applicationContext, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)

                _isConnected.value = true
                isConnectedLiveData.postValue(true)

                // Initialize state
                updatePlaybackState()
                _nowPlaying.value = mediaController?.currentMediaItem
                nowPlayingLiveData.postValue(mediaController?.mediaMetadata)

                Timber.i("Media3 connected successfully")
                onConnected()
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to Media3 service")
                _isConnected.value = false
                isConnectedLiveData.postValue(false)
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Disconnect from the Media3 service.
     */
    fun disconnect() {
        Timber.i("Disconnecting from Media3 service")
        _isConnected.value = false
        isConnectedLiveData.postValue(false)

        mediaController?.removeListener(playerListener)
        mediaController = null

        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    // ========== Transport Controls (Direct Player Interface) ==========

    /**
     * Get the underlying MediaController for direct player access.
     * Prefer using the helper methods below for common operations.
     */
    fun getController(): MediaController? = mediaController

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun stop() {
        mediaController?.stop()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        mediaController?.seekTo(mediaItemIndex, positionMs)
    }

    fun seekToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun seekToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
    }

    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        mediaController?.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    fun prepare() {
        mediaController?.prepare()
    }

    /**
     * Play from a media ID (for Android Auto / media browsing).
     */
    fun playFromMediaId(mediaId: String) {
        // Build a MediaItem from the ID and play it
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .build()
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
    }

    // ========== State Query Helpers ==========

    val currentPosition: Long
        get() = mediaController?.currentPosition ?: 0L

    val duration: Long
        get() = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    val isPlaying: Boolean
        get() = mediaController?.isPlaying ?: false

    val isPaused: Boolean
        get() = mediaController?.let {
            it.playbackState == Player.STATE_READY && !it.playWhenReady
        } ?: false

    val currentMediaItem: MediaItem?
        get() = mediaController?.currentMediaItem

    val currentMediaItemIndex: Int
        get() = mediaController?.currentMediaItemIndex ?: 0
}

/**
 * Simplified playback state for UI consumption.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val currentMediaItemIndex: Int = 0,
    val playerState: Int = Player.STATE_IDLE,
    val error: String? = null
) {
    val isPrepared: Boolean
        get() = playerState != Player.STATE_IDLE

    val isIdle: Boolean
        get() = playerState == Player.STATE_IDLE
}
