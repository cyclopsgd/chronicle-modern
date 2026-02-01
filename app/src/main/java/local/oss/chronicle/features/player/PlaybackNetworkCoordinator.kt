package local.oss.chronicle.features.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import local.oss.chronicle.data.sources.plex.PlaybackUrlResolver
import local.oss.chronicle.util.NetworkMonitor
import local.oss.chronicle.util.NetworkState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates playback state with network availability.
 *
 * Bridges the NetworkMonitor and playback system to:
 * - Track when playback is waiting for network
 * - Auto-resume when network becomes available
 * - Refresh stale URLs on network change
 * - Provide playback-aware network state
 */
@Singleton
class PlaybackNetworkCoordinator
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        private val playbackUrlResolver: PlaybackUrlResolver,
    ) {
        /**
         * Playback-specific network states.
         */
        sealed class PlaybackNetworkState {
            /** Normal operation - network available or content cached */
            data object Normal : PlaybackNetworkState()

            /** Playback interrupted, waiting for network */
            data object WaitingForNetwork : PlaybackNetworkState()

            /** Network restored, restoring playback */
            data object RestoringPlayback : PlaybackNetworkState()

            /** Error state with message */
            data class Error(val message: String) : PlaybackNetworkState()
        }

        private val _playbackNetworkState =
            MutableStateFlow<PlaybackNetworkState>(PlaybackNetworkState.Normal)

        /** Current playback network state */
        val playbackNetworkState: StateFlow<PlaybackNetworkState> = _playbackNetworkState.asStateFlow()

        /** Whether playback should be paused due to network loss */
        val shouldPauseForNetwork: Boolean
            get() = _playbackNetworkState.value is PlaybackNetworkState.WaitingForNetwork

        private var observingJob: Job? = null
        private var wasPlayingBeforeNetworkLoss = false

        /** Callback for when network is restored and playback should resume */
        var onNetworkRestoredCallback: (() -> Unit)? = null

        /** Callback for notifying user of network state changes */
        var onNetworkMessageCallback: ((String) -> Unit)? = null

        /**
         * Starts observing network state changes.
         * Call this from service onCreate.
         *
         * @param scope CoroutineScope to use for observation
         */
        fun startObserving(scope: CoroutineScope) {
            if (observingJob != null) {
                Timber.d("PlaybackNetworkCoordinator already observing")
                return
            }

            Timber.i("PlaybackNetworkCoordinator starting observation")

            observingJob =
                scope.launch {
                    networkMonitor.networkState.collectLatest { networkState ->
                        handleNetworkStateChange(networkState)
                    }
                }
        }

        /**
         * Stops observing network state changes.
         * Call this from service onDestroy.
         */
        fun stopObserving() {
            Timber.i("PlaybackNetworkCoordinator stopping observation")
            observingJob?.cancel()
            observingJob = null
            _playbackNetworkState.value = PlaybackNetworkState.Normal
        }

        /**
         * Notifies coordinator that playback was interrupted due to network.
         * Call this when a network error occurs during playback.
         *
         * @param wasPlaying Whether playback was active when network was lost
         */
        fun notifyPlaybackInterruptedByNetwork(wasPlaying: Boolean) {
            wasPlayingBeforeNetworkLoss = wasPlaying
            _playbackNetworkState.value = PlaybackNetworkState.WaitingForNetwork
            Timber.i("Playback interrupted by network loss, wasPlaying=$wasPlaying")
        }

        /**
         * Handles network state changes.
         */
        private suspend fun handleNetworkStateChange(networkState: NetworkState) {
            Timber.d("Network state changed: $networkState")

            when (networkState) {
                is NetworkState.Connected -> {
                    if (_playbackNetworkState.value is PlaybackNetworkState.WaitingForNetwork) {
                        handleNetworkRestored()
                    }
                }
                is NetworkState.Disconnected -> {
                    if (_playbackNetworkState.value is PlaybackNetworkState.Normal) {
                        handleNetworkLost()
                    }
                }
                is NetworkState.Unknown -> {
                    // Initial state, no action needed
                }
            }
        }

        /**
         * Handles network restoration.
         */
        private suspend fun handleNetworkRestored() {
            Timber.i("Network restored, attempting to restore playback")
            _playbackNetworkState.value = PlaybackNetworkState.RestoringPlayback

            try {
                // Clear any stale URL cache to force fresh resolution
                playbackUrlResolver.clearCache()

                // If playback was active before network loss, resume it
                if (wasPlayingBeforeNetworkLoss) {
                    Timber.i("Resuming playback after network restoration")
                    onNetworkRestoredCallback?.invoke()
                }

                _playbackNetworkState.value = PlaybackNetworkState.Normal
            } catch (e: Exception) {
                Timber.e(e, "Error restoring playback after network restoration")
                _playbackNetworkState.value =
                    PlaybackNetworkState.Error("Failed to restore playback: ${e.message}")
            }
        }

        /**
         * Handles network loss.
         */
        private fun handleNetworkLost() {
            Timber.i("Network lost")
            // Don't automatically transition to WaitingForNetwork
            // Let the error handler determine if we need to wait
        }

        /**
         * Resets the coordinator state.
         * Call this when switching books or clearing playback.
         */
        fun reset() {
            wasPlayingBeforeNetworkLoss = false
            _playbackNetworkState.value = PlaybackNetworkState.Normal
            Timber.d("PlaybackNetworkCoordinator reset")
        }

        /**
         * Checks if content is available offline for a given book.
         * If content is offline, network state doesn't affect playback.
         *
         * @param isOffline Whether current content is available offline
         */
        fun setContentOfflineStatus(isOffline: Boolean) {
            if (isOffline && _playbackNetworkState.value is PlaybackNetworkState.WaitingForNetwork) {
                Timber.i("Content is offline, resuming normal operation")
                _playbackNetworkState.value = PlaybackNetworkState.Normal
            }
        }
    }
