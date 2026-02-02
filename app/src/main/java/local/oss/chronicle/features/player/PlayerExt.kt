package local.oss.chronicle.features.player

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import androidx.media3.common.Player
import local.oss.chronicle.R
import local.oss.chronicle.application.MILLIS_PER_SECOND
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingSingleton
import timber.log.Timber
import kotlin.math.abs

/**
 * Seek in the play queue by an offset of [durationMillis]. Positive [duration] seeks forwards,
 * negative [duration] seeks backwards
 */
fun Player.seekRelative(
    trackListStateManager: TrackListStateManager,
    durationMillis: Long,
) {
    // if seeking within the current track, no need to calculate seek
    if (durationMillis > 0 && (duration - currentPosition) > durationMillis) {
        Timber.i(
            "Seeking forwards within window: pos = $currentPosition, window duration = $duration, seek= $durationMillis",
        )
        seekTo(currentPosition + durationMillis)
    } else if (durationMillis < 0 && currentPosition > abs(durationMillis)) {
        Timber.i(
            "Seeking backwards within window: pos = $currentPosition, duration = $durationMillis",
        )
        seekTo(currentPosition + durationMillis)
    } else {
        Timber.i("Seeking via trackliststatemanager")
        trackListStateManager.updatePositionBlocking(currentMediaItemIndex, currentPosition)
        trackListStateManager.seekByRelativeBlocking(durationMillis)
        seekTo(trackListStateManager.currentTrackIndex, trackListStateManager.currentTrackProgress)
    }
}

/** Skip to next chapter */
fun Player.skipToNext(
    context: Context,
    trackListStateManager: TrackListStateManager,
    currentlyPlaying: CurrentlyPlaying,
    progressUpdater: ProgressUpdater,
) {
    Timber.i("Player.skipToNext called")

    // Get chapters from PlaybackStateController's state (not from book.chapters which may be empty)
    val playbackState = (currentlyPlaying as? CurrentlyPlayingSingleton)?.state?.value
    val chapters = playbackState?.chapters ?: currentlyPlaying.book.value.chapters
    val currentChapter = currentlyPlaying.chapter.value

    if (chapters.isEmpty()) {
        Timber.w("skipToNext: No chapters available")
        return
    }

    // Find current chapter's position in the list by matching trackId and startTimeOffset
    val currentChapterIndex = chapters.indexOfFirst {
        it.trackId == currentChapter.trackId && it.startTimeOffset == currentChapter.startTimeOffset
    }
    val nextChapterIndex = currentChapterIndex + 1

    Timber.d("skipToNext: currentChapterIndex=$currentChapterIndex, chaptersSize=${chapters.size}")

    if (currentChapterIndex >= 0 && nextChapterIndex < chapters.size) {
        val nextChapter = chapters[nextChapterIndex]
        Timber.d(
            "NEXT CHAPTER: index=$nextChapterIndex id=${nextChapter.id} trackId=${nextChapter.trackId} offset=${nextChapter.startTimeOffset} title=${nextChapter.title}",
        )

        // Try to find the track containing this chapter
        var containingTrackIndex = trackListStateManager.trackList.indexOfFirst {
            it.id.toLong() == nextChapter.trackId
        }

        // If track not found by trackId, try using current track (for single-file M4B books)
        if (containingTrackIndex < 0 && trackListStateManager.trackList.size == 1) {
            Timber.d("skipToNext: Single track book, using track index 0")
            containingTrackIndex = 0
        }

        // If still not found, use current media item index
        if (containingTrackIndex < 0) {
            Timber.w("skipToNext: Could not find track for chapter, using currentMediaItemIndex")
            containingTrackIndex = currentMediaItemIndex
        }

        Timber.d("skipToNext: seeking to trackIndex=$containingTrackIndex, offset=${nextChapter.startTimeOffset}")
        seekTo(containingTrackIndex, nextChapter.startTimeOffset + 300)
        // Don't call progressUpdater here - the seek is async and position won't be updated yet
        // The player's onPositionDiscontinuity callback will handle updating progress
    } else {
        val toast =
            Toast.makeText(
                context,
                R.string.skip_forwards_reached_last_chapter,
                Toast.LENGTH_LONG,
            )
        toast.setGravity(Gravity.BOTTOM, 0, 200)
        toast.show()
    }
}

/** Skip to previous chapter */
fun Player.skipToPrevious(
    context: Context,
    trackListStateManager: TrackListStateManager,
    currentlyPlaying: CurrentlyPlaying,
    progressUpdater: ProgressUpdater,
) {
    Timber.i("Player.skipToPrevious called")

    // Get chapters from PlaybackStateController's state (not from book.chapters which may be empty)
    val playbackState = (currentlyPlaying as? CurrentlyPlayingSingleton)?.state?.value
    val chapters = playbackState?.chapters ?: currentlyPlaying.book.value.chapters
    val currentChapter = currentlyPlaying.chapter.value

    if (chapters.isEmpty()) {
        Timber.w("skipToPrevious: No chapters available")
        return
    }

    // Find current chapter's position in the list by matching trackId and startTimeOffset
    val currentChapterIndex = chapters.indexOfFirst {
        it.trackId == currentChapter.trackId && it.startTimeOffset == currentChapter.startTimeOffset
    }

    Timber.d("skipToPrevious: currentChapterIndex=$currentChapterIndex, chaptersSize=${chapters.size}, trackListSize=${trackListStateManager.trackList.size}")
    Timber.d("skipToPrevious trackList IDs: ${trackListStateManager.trackList.map { it.id }}")
    Timber.d("skipToPrevious chapter trackIds: ${chapters.map { it.trackId }}")

    if (currentChapterIndex < 0) {
        Timber.w("Could not find current chapter in list, trying by index...")
        // Fallback: find chapter by time position
        val chapterByTime = chapters.indexOfFirst {
            currentPosition >= it.startTimeOffset && currentPosition < it.endTimeOffset
        }
        if (chapterByTime >= 0) {
            Timber.d("skipToPrevious: Found chapter by time position: $chapterByTime")
            // Seek to start of this chapter
            val chapter = chapters[chapterByTime]
            seekTo(currentMediaItemIndex, chapter.startTimeOffset)
            progressUpdater.updateProgressWithoutParameters()
            return
        }
        Timber.w("Could not find current chapter by any method, aborting skip")
        return
    }

    var previousChapterIndex: Int =
        if ((currentPosition - currentChapter.startTimeOffset) < (SKIP_TO_PREVIOUS_CHAPTER_THRESHOLD_SECONDS * MILLIS_PER_SECOND)) {
            Timber.d("skipToPrevious → skip to previous chapter")
            currentChapterIndex - 1
        } else {
            Timber.d("skipToPrevious → back to start of current chapter")
            currentChapterIndex
        }
    if (previousChapterIndex < 0) previousChapterIndex = 0

    val previousChapter = chapters[previousChapterIndex]
    Timber.d(
        "PREVIOUS CHAPTER: index=$previousChapterIndex id=${previousChapter.id} trackId=${previousChapter.trackId} offset=${previousChapter.startTimeOffset} title=${previousChapter.title}",
    )

    // Try to find the track containing this chapter
    var containingTrackIndex = trackListStateManager.trackList.indexOfFirst {
        it.id.toLong() == previousChapter.trackId
    }

    // If track not found by trackId, try using current track (for single-file M4B books)
    if (containingTrackIndex < 0 && trackListStateManager.trackList.size == 1) {
        Timber.d("skipToPrevious: Single track book, using track index 0")
        containingTrackIndex = 0
    }

    // If still not found, use current media item index
    if (containingTrackIndex < 0) {
        Timber.w("skipToPrevious: Could not find track for chapter, using currentMediaItemIndex")
        containingTrackIndex = currentMediaItemIndex
    }

    Timber.i(
        "skipToPrevious SEEK: trackIndex=$containingTrackIndex, targetPosition=${previousChapter.startTimeOffset}, " +
            "currentPosition=$currentPosition",
    )
    seekTo(containingTrackIndex, previousChapter.startTimeOffset)
    Timber.i("skipToPrevious AFTER SEEK: newPosition=$currentPosition")
    // Don't call progressUpdater here - the seek is async and position won't be updated yet
    // The player's onPositionDiscontinuity callback will handle updating progress
}
