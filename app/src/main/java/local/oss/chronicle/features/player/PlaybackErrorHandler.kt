package local.oss.chronicle.features.player

import android.content.Context
import androidx.media3.common.PlaybackException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import local.oss.chronicle.R
import local.oss.chronicle.util.NetworkMonitor
import local.oss.chronicle.util.NetworkState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Handles playback errors with intelligent recovery strategies.
 *
 * Instead of failing silently or crashing, this handler determines the appropriate
 * recovery action based on the error type and current network state.
 */
@Singleton
class PlaybackErrorHandler
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val MAX_RETRY_ATTEMPTS = 3
            private const val BASE_RETRY_DELAY_MS = 1000L
            private const val MAX_RETRY_DELAY_MS = 10000L
        }

        /**
         * Possible recovery actions for playback errors.
         */
        sealed class ErrorRecoveryAction {
            /** Retry the same operation after a delay */
            data class Retry(val delayMs: Long) : ErrorRecoveryAction()

            /** Refresh the streaming URL and retry */
            data object RefreshUrlAndRetry : ErrorRecoveryAction()

            /** Skip to the next track */
            data object SkipTrack : ErrorRecoveryAction()

            /** Wait for network to become available */
            data object WaitForNetwork : ErrorRecoveryAction()

            /** Notify user of the error (unrecoverable) */
            data class NotifyUser(val message: String) : ErrorRecoveryAction()
        }

        /**
         * Determines the appropriate recovery action for a playback error.
         *
         * @param error The playback exception that occurred
         * @param retryCount Current retry attempt count
         * @return The recommended recovery action
         */
        fun determineRecoveryAction(
            error: PlaybackException,
            retryCount: Int = 0,
        ): ErrorRecoveryAction {
            Timber.d("Determining recovery for error: ${error.errorCode}, retry count: $retryCount")

            // Check network state first
            if (networkMonitor.currentState is NetworkState.Disconnected) {
                Timber.i("Network disconnected, waiting for reconnection")
                return ErrorRecoveryAction.WaitForNetwork
            }

            // Exceeded retry limit
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                Timber.w("Max retry attempts ($MAX_RETRY_ATTEMPTS) exceeded")
                return ErrorRecoveryAction.NotifyUser(
                    getErrorMessage(error),
                )
            }

            return when (error.errorCode) {
                // Network-related errors - retry with backoff
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                -> {
                    val delay = calculateBackoffDelay(retryCount)
                    Timber.i("Network error, will retry after ${delay}ms")
                    ErrorRecoveryAction.Retry(delay)
                }

                // Server errors - may need URL refresh
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    // HTTP errors like 401, 403, 404, 503
                    when (extractHttpStatusCode(error)) {
                        401, 403 -> {
                            Timber.i("Auth error, refreshing URL")
                            ErrorRecoveryAction.RefreshUrlAndRetry
                        }
                        404 -> {
                            Timber.w("Track not found (404)")
                            ErrorRecoveryAction.NotifyUser(
                                context.getString(R.string.playback_error_404),
                            )
                        }
                        503 -> {
                            val delay = calculateBackoffDelay(retryCount)
                            Timber.i("Server unavailable (503), will retry after ${delay}ms")
                            ErrorRecoveryAction.Retry(delay)
                        }
                        else -> {
                            ErrorRecoveryAction.RefreshUrlAndRetry
                        }
                    }
                }

                // Unrecoverable source errors
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> {
                    Timber.w("Unrecoverable source error: ${error.errorCode}")
                    ErrorRecoveryAction.NotifyUser(getErrorMessage(error))
                }

                // Parsing/format errors - skip track
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                -> {
                    Timber.w("Format error, skipping track")
                    ErrorRecoveryAction.SkipTrack
                }

                // Other IO errors - try refreshing URL first
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                -> {
                    if (retryCount == 0) {
                        Timber.i("IO error, refreshing URL")
                        ErrorRecoveryAction.RefreshUrlAndRetry
                    } else {
                        val delay = calculateBackoffDelay(retryCount)
                        Timber.i("IO error on retry, will retry after ${delay}ms")
                        ErrorRecoveryAction.Retry(delay)
                    }
                }

                // Default - retry with backoff
                else -> {
                    val delay = calculateBackoffDelay(retryCount)
                    Timber.i("Unknown error ${error.errorCode}, will retry after ${delay}ms")
                    ErrorRecoveryAction.Retry(delay)
                }
            }
        }

        /**
         * Executes a recovery action.
         *
         * @param action The recovery action to execute
         * @param onRetry Callback to retry playback
         * @param onRefreshUrl Callback to refresh URL and retry
         * @param onSkipTrack Callback to skip to next track
         * @param onWaitForNetwork Callback when waiting for network
         * @param onNotifyUser Callback to display error to user
         */
        suspend fun executeRecovery(
            action: ErrorRecoveryAction,
            onRetry: suspend () -> Unit,
            onRefreshUrl: suspend () -> Unit,
            onSkipTrack: suspend () -> Unit,
            onWaitForNetwork: suspend () -> Unit,
            onNotifyUser: suspend (String) -> Unit,
        ) {
            when (action) {
                is ErrorRecoveryAction.Retry -> {
                    Timber.d("Executing retry after ${action.delayMs}ms")
                    delay(action.delayMs)
                    onRetry()
                }
                is ErrorRecoveryAction.RefreshUrlAndRetry -> {
                    Timber.d("Executing URL refresh and retry")
                    onRefreshUrl()
                }
                is ErrorRecoveryAction.SkipTrack -> {
                    Timber.d("Executing skip track")
                    onSkipTrack()
                }
                is ErrorRecoveryAction.WaitForNetwork -> {
                    Timber.d("Waiting for network")
                    onWaitForNetwork()
                }
                is ErrorRecoveryAction.NotifyUser -> {
                    Timber.d("Notifying user: ${action.message}")
                    onNotifyUser(action.message)
                }
            }
        }

        /**
         * Calculates exponential backoff delay.
         */
        private fun calculateBackoffDelay(retryCount: Int): Long {
            val delay = BASE_RETRY_DELAY_MS * 2.0.pow(retryCount.toDouble()).toLong()
            return min(delay, MAX_RETRY_DELAY_MS)
        }

        /**
         * Extracts HTTP status code from a playback exception if available.
         */
        private fun extractHttpStatusCode(error: PlaybackException): Int? {
            // Check if the cause contains HTTP status information
            val cause = error.cause
            val message = cause?.message ?: error.message ?: ""

            // Common patterns for HTTP status in error messages
            val statusPattern = Regex("(\\d{3})")
            val match = statusPattern.find(message)
            return match?.groupValues?.get(1)?.toIntOrNull()
        }

        /**
         * Gets a user-friendly error message for display.
         */
        private fun getErrorMessage(error: PlaybackException): String {
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                -> context.getString(R.string.not_connected)

                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    context.getString(R.string.playback_error_404)

                else -> {
                    val httpStatus = extractHttpStatusCode(error)
                    when (httpStatus) {
                        401 -> context.getString(R.string.playback_error_401)
                        503 -> context.getString(R.string.playback_error_503)
                        404 -> context.getString(R.string.playback_error_404)
                        else -> context.getString(R.string.playback_error, error.message ?: "Unknown error")
                    }
                }
            }
        }
    }
