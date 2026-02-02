package local.oss.chronicle.features.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Unified playback controller interface that abstracts over both legacy
 * MediaServiceConnection and modern Media3ServiceConnection.
 *
 * This interface enables gradual migration to Media3 by allowing ViewModels
 * to work with either implementation without changes.
 *
 * Usage:
 * 1. ViewModels inject PlaybackController instead of specific connection types
 * 2. Hilt binds either LegacyPlaybackController or Media3PlaybackController
 * 3. Switch implementations via Hilt module configuration
 *
 * Example injection:
 * ```kotlin
 * @HiltViewModel
 * class NowPlayingViewModel @Inject constructor(
 *     private val playbackController: PlaybackController
 * ) : ViewModel() {
 *     fun onPlayPauseClicked() {
 *         if (playbackController.isPlaying.value) {
 *             playbackController.pause()
 *         } else {
 *             playbackController.play()
 *         }
 *     }
 * }
 * ```
 */
interface PlaybackController {
    // Connection state
    val isConnected: StateFlow<Boolean>

    // Playback state
    val isPlaying: StateFlow<Boolean>
    val isPaused: StateFlow<Boolean>
    val isBuffering: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val playbackSpeed: StateFlow<Float>

    // Current media info
    val currentMediaId: StateFlow<String?>
    val currentTitle: StateFlow<String?>
    val currentArtist: StateFlow<String?>

    // Connection management
    fun connect()
    fun connect(onConnected: () -> Unit)
    fun disconnect()

    // Transport controls
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekToNext()
    fun seekToPrevious()
    fun setPlaybackSpeed(speed: Float)

    // Playback from media ID (for Android Auto / browsing)
    fun playFromMediaId(mediaId: String)

    // Skip forward/backward by configured duration
    fun skipForward()
    fun skipBackward()
}
