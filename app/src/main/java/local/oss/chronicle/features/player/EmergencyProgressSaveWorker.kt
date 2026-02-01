package local.oss.chronicle.features.player

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import local.oss.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import local.oss.chronicle.data.model.getTrackStartTime
import local.oss.chronicle.data.sources.plex.model.getDuration
import timber.log.Timber

/**
 * Worker that saves playback progress when the app is being killed.
 *
 * This worker is expedited to ensure it runs immediately, even if the app
 * is being terminated. It persists the current playback position to the
 * local database so progress isn't lost.
 */
@HiltWorker
class EmergencyProgressSaveWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val trackRepository: ITrackRepository,
        private val bookRepository: IBookRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val trackId = inputData.getInt(SimpleProgressUpdater.EMERGENCY_SAVE_TRACK_ID, TRACK_NOT_FOUND)
            val position = inputData.getLong(SimpleProgressUpdater.EMERGENCY_SAVE_POSITION, 0L)
            val timestamp = inputData.getLong(SimpleProgressUpdater.EMERGENCY_SAVE_TIMESTAMP, System.currentTimeMillis())

            if (trackId == TRACK_NOT_FOUND) {
                Timber.w("EmergencyProgressSaveWorker: Invalid track ID")
                return Result.failure()
            }

            Timber.i("EmergencyProgressSaveWorker: Saving progress for track $trackId at position $position")

            return try {
                val bookId = trackRepository.getBookIdForTrack(trackId)
                val track = trackRepository.getTrackAsync(trackId)

                if (bookId == NO_AUDIOBOOK_FOUND_ID || track == null) {
                    Timber.w("EmergencyProgressSaveWorker: Book or track not found")
                    return Result.failure()
                }

                val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                val bookProgress = tracks.getTrackStartTime(track) + position

                // Update track progress
                trackRepository.updateTrackProgress(position, trackId, timestamp)

                // Update book progress
                bookRepository.updateProgress(bookId, timestamp, bookProgress)
                bookRepository.updateTrackData(
                    bookId,
                    bookProgress,
                    tracks.getDuration(),
                    tracks.size,
                )

                Timber.i(
                    "EmergencyProgressSaveWorker: Successfully saved - " +
                        "trackId=$trackId, position=$position, bookProgress=$bookProgress",
                )

                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "EmergencyProgressSaveWorker: Failed to save progress")
                Result.failure()
            }
        }
    }
