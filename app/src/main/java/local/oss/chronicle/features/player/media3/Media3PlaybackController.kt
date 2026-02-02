package local.oss.chronicle.features.player.media3

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.features.player.PlaybackController
import timber.log.Timber
import javax.inject.Inject

/**
 * Media3 implementation of [PlaybackController].
 *
 * This implementation uses the modern Media3 APIs (SessionToken, MediaController.Builder)
 * and exposes state via StateFlow for reactive UI updates.
 */
@OptIn(UnstableApi::class)
class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: PrefsRepo
) : PlaybackController {

    private val serviceComponent = ComponentName(context, Media3PlayerService::class.java)

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    override val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Current media info
    private val _currentMediaId = MutableStateFlow<String?>(null)
    override val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    override val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow<String?>(null)
    override val currentArtist: StateFlow<String?> = _currentArtist.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updateState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _currentTitle.value = mediaMetadata.title?.toString()
            _currentArtist.value = mediaMetadata.artist?.toString()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaId.value = mediaItem?.mediaId
            mediaItem?.mediaMetadata?.let {
                _currentTitle.value = it.title?.toString()
                _currentArtist.value = it.artist?.toString()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Media3 player error in PlaybackController")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateState()
        }
    }

    private fun updateState() {
        val controller = mediaController ?: return

        _isPlaying.value = controller.isPlaying
        _isPaused.value = controller.playbackState == Player.STATE_READY && !controller.playWhenReady
        _isBuffering.value = controller.playbackState == Player.STATE_BUFFERING
        _currentPosition.value = controller.currentPosition
        _duration.value = controller.duration.coerceAtLeast(0L)
        _playbackSpeed.value = controller.playbackParameters.speed
    }

    // ========== Connection Management ==========

    override fun connect() {
        if (_isConnected.value) {
            Timber.d("Media3 PlaybackController already connected")
            return
        }

        Timber.i("Media3 PlaybackController connecting...")
        val sessionToken = SessionToken(context, serviceComponent)

        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)

                _isConnected.value = true
                updateState()

                // Initialize media info
                mediaController?.currentMediaItem?.let {
                    _currentMediaId.value = it.mediaId
                    _currentTitle.value = it.mediaMetadata.title?.toString()
                    _currentArtist.value = it.mediaMetadata.artist?.toString()
                }

                Timber.i("Media3 PlaybackController connected")
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect Media3 PlaybackController")
                _isConnected.value = false
            }
        }, MoreExecutors.directExecutor())
    }

    override fun connect(onConnected: () -> Unit) {
        if (_isConnected.value) {
            Timber.d("Media3 PlaybackController already connected, invoking callback")
            onConnected()
            return
        }

        Timber.i("Media3 PlaybackController connecting with callback...")
        val sessionToken = SessionToken(context, serviceComponent)

        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)

                _isConnected.value = true
                updateState()

                Timber.i("Media3 PlaybackController connected")
                onConnected()
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect Media3 PlaybackController")
                _isConnected.value = false
            }
        }, MoreExecutors.directExecutor())
    }

    override fun disconnect() {
        Timber.i("Media3 PlaybackController disconnecting")
        _isConnected.value = false

        mediaController?.removeListener(playerListener)
        mediaController = null

        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    // ========== Transport Controls ==========

    override fun play() {
        mediaController?.play()
    }

    override fun pause() {
        mediaController?.pause()
    }

    override fun stop() {
        mediaController?.stop()
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    override fun seekToNext() {
        mediaController?.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
    }

    override fun playFromMediaId(mediaId: String) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .build()
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
    }

    override fun skipForward() {
        val skipMs = prefsRepo.jumpForwardSeconds * 1000L
        val newPosition = (mediaController?.currentPosition ?: 0L) + skipMs
        mediaController?.seekTo(newPosition)
    }

    override fun skipBackward() {
        val skipMs = prefsRepo.jumpBackwardSeconds * 1000L
        val newPosition = maxOf(0L, (mediaController?.currentPosition ?: 0L) - skipMs)
        mediaController?.seekTo(newPosition)
    }
}
