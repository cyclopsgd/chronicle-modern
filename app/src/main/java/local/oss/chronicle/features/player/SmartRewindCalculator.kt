package local.oss.chronicle.features.player

import local.oss.chronicle.data.local.PrefsRepo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Calculates smart rewind duration based on how long playback has been paused.
 *
 * When resuming playback after a pause, users often need a brief recap to remember
 * where they were in the story. The longer the pause, the more context they need.
 *
 * Rewind rules:
 * - < 15 seconds pause: No rewind (still in context)
 * - 15 seconds - 1 minute pause: 5 second rewind
 * - 1 minute - 1 hour pause: 10 second rewind
 * - 1 hour - 24 hours pause: 15 second rewind
 * - > 24 hours pause: 20 second rewind
 *
 * This feature respects the user's autoRewind preference setting.
 */
@Singleton
class SmartRewindCalculator
    @Inject
    constructor(
        private val prefsRepo: PrefsRepo,
    ) {
        companion object {
            // Pause duration thresholds
            private val THRESHOLD_MINIMAL = 15.seconds.inWholeMilliseconds
            private val THRESHOLD_SHORT = 1.minutes.inWholeMilliseconds
            private val THRESHOLD_MEDIUM = 1.hours.inWholeMilliseconds
            private val THRESHOLD_LONG = 24.hours.inWholeMilliseconds

            // Rewind durations
            private val REWIND_NONE = 0L
            private val REWIND_SHORT = 5.seconds.inWholeMilliseconds
            private val REWIND_MEDIUM = 10.seconds.inWholeMilliseconds
            private val REWIND_LONG = 15.seconds.inWholeMilliseconds
            private val REWIND_EXTENDED = 20.seconds.inWholeMilliseconds
        }

        /**
         * Records the current timestamp as the pause time.
         * Call this when playback is paused.
         */
        fun recordPause() {
            val timestamp = System.currentTimeMillis()
            prefsRepo.lastPauseTimestamp = timestamp
            Timber.d("SmartRewind: Recorded pause at $timestamp")
        }

        /**
         * Calculates how much to rewind based on pause duration.
         * Call this when playback is about to resume.
         *
         * @return Rewind duration in milliseconds, or 0 if no rewind needed
         */
        fun calculateRewindDuration(): Long {
            if (!prefsRepo.autoRewind) {
                Timber.d("SmartRewind: Auto-rewind disabled, returning 0")
                return REWIND_NONE
            }

            val pauseTimestamp = prefsRepo.lastPauseTimestamp
            if (pauseTimestamp == 0L) {
                Timber.d("SmartRewind: No pause timestamp recorded, returning 0")
                return REWIND_NONE
            }

            val pauseDuration = System.currentTimeMillis() - pauseTimestamp
            val rewindDuration = calculateRewindForPauseDuration(pauseDuration)

            Timber.i(
                "SmartRewind: Pause duration=${pauseDuration}ms (${pauseDuration / 1000}s), " +
                    "rewind=${rewindDuration}ms (${rewindDuration / 1000}s)",
            )

            return rewindDuration
        }

        /**
         * Determines rewind duration based on pause duration.
         */
        private fun calculateRewindForPauseDuration(pauseDurationMs: Long): Long {
            return when {
                pauseDurationMs < THRESHOLD_MINIMAL -> REWIND_NONE
                pauseDurationMs < THRESHOLD_SHORT -> REWIND_SHORT
                pauseDurationMs < THRESHOLD_MEDIUM -> REWIND_MEDIUM
                pauseDurationMs < THRESHOLD_LONG -> REWIND_LONG
                else -> REWIND_EXTENDED
            }
        }

        /**
         * Clears the stored pause timestamp.
         * Call this after applying rewind or when starting fresh playback.
         */
        fun clearPauseTimestamp() {
            prefsRepo.lastPauseTimestamp = 0L
            Timber.d("SmartRewind: Cleared pause timestamp")
        }
    }
