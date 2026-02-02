package local.oss.chronicle.features.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for controlling media playback.
 *
 * This abstraction allows ViewModels to control playback without directly depending
 * on either the legacy MediaServiceConnection or the new Media3ServiceConnection.
 *
 * Implementations:
 * - Media3PlaybackController: Uses Media3 MediaController (modern)
 * - Future: Could add a legacy implementation wrapper if needed
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
    fun playFromMediaId(mediaId: String)
    fun skipForward()
    fun skipBackward()
}
