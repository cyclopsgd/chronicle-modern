package local.oss.chronicle.features.player

import local.oss.chronicle.data.model.Audiobook
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.data.model.MediaItemTrack

/**
 * Immutable representation of the current playback state.
 * This is the single source of truth for playback state throughout the app.
 *
 * All position values are in milliseconds.
 *
 * Design Principles:
 * 1. ExoPlayer's position is authoritative
 * 2. All state is immutable - use copy() for updates
 * 3. Derived properties are computed, not stored
 *
 * @property audiobook The currently playing audiobook, or null if none
 * @property tracks List of all tracks in the audiobook
 * @property chapters List of all chapters in the audiobook
 * @property currentTrackIndex Index of the currently playing track (0-based)
 * @property currentTrackPositionMs Position within the current track in milliseconds
 * @property isPlaying Whether playback is currently active
 * @property playbackSpeed Current playback speed multiplier (1.0 = normal)
 * @property lastUpdatedAtMs Timestamp when this state was last updated
 */
data class PlaybackState(
    val audiobook: Audiobook? = null,
    val tracks: List<MediaItemTrack> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val currentTrackIndex: Int = 0,
    val currentTrackPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val lastUpdatedAtMs: Long = System.currentTimeMillis(),
) {
    companion object {
        /**
         * Empty playback state representing no media loaded.
         */
        val EMPTY = PlaybackState()

        /**
         * Creates a PlaybackState from audiobook data.
         * Used when starting playback of a new audiobook.
         */
        fun fromAudiobook(
            audiobook: Audiobook,
            tracks: List<MediaItemTrack>,
            chapters: List<Chapter>,
            startTrackIndex: Int = 0,
            startPositionMs: Long = 0L,
        ): PlaybackState =
            PlaybackState(
                audiobook = audiobook,
                tracks = tracks,
                chapters = chapters,
                currentTrackIndex = startTrackIndex.coerceIn(0, tracks.lastIndex.coerceAtLeast(0)),
                currentTrackPositionMs = startPositionMs.coerceAtLeast(0L),
                isPlaying = false,
                playbackSpeed = 1.0f,
            )
    }

    // =====================
    // Computed Properties
    // =====================

    /**
     * Whether any media is loaded for playback.
     */
    val hasMedia: Boolean
        get() = audiobook != null && tracks.isNotEmpty()

    /**
     * The currently playing track, or null if no media loaded or index is invalid.
     */
    val currentTrack: MediaItemTrack?
        get() = tracks.getOrNull(currentTrackIndex)

    /**
     * The current chapter based on track position, or null if no chapters or position is invalid.
     *
     * Chapter startTimeOffset is track-relative (offset from start of containing track),
     * so we need to find chapters for the current track and compare against track position.
     */
    val currentChapter: Chapter?
        get() {
            if (chapters.isEmpty()) return null
            val track = currentTrack ?: return chapters.firstOrNull()
            val trackId = track.id.toLong()
            val trackPos = currentTrackPositionMs

            // Find chapters belonging to current track
            val trackChapters = chapters.filter { it.trackId == trackId }
            if (trackChapters.isEmpty()) {
                // Fallback: maybe chapters don't have proper trackId set, use book-relative logic
                val bookPos = bookPositionMs
                return chapters.lastOrNull { it.startTimeOffset <= bookPos }
                    ?: chapters.firstOrNull()
            }

            // Find the chapter containing current track position
            return trackChapters.lastOrNull { trackPos >= it.startTimeOffset }
                ?: trackChapters.firstOrNull()
        }

    /**
     * Index of the current chapter (0-based), or -1 if no chapter found.
     * This returns the index in the full chapters list, not just the track's chapters.
     */
    val currentChapterIndex: Int
        get() {
            if (chapters.isEmpty()) return -1
            val chapter = currentChapter ?: return 0
            // Find the index of this chapter in the full list
            return chapters.indexOfFirst {
                it.trackId == chapter.trackId && it.startTimeOffset == chapter.startTimeOffset
            }.takeIf { it >= 0 } ?: 0
        }

    /**
     * Position within the entire audiobook in milliseconds.
     * Calculated as sum of previous track durations + current track position.
     */
    val bookPositionMs: Long
        get() {
            if (tracks.isEmpty()) return 0L
            var position = 0L
            for (i in 0 until currentTrackIndex) {
                position += tracks.getOrNull(i)?.duration ?: 0L
            }
            return position + currentTrackPositionMs
        }

    /**
     * Total duration of the audiobook in milliseconds.
     */
    val bookDurationMs: Long
        get() = tracks.sumOf { it.duration }

    /**
     * Duration of the current track in milliseconds.
     */
    val currentTrackDurationMs: Long
        get() = currentTrack?.duration ?: 0L

    /**
     * Duration of the current chapter in milliseconds.
     */
    val currentChapterDurationMs: Long
        get() {
            val chapter = currentChapter ?: return 0L
            // Chapter endTimeOffset - startTimeOffset gives the duration
            return (chapter.endTimeOffset - chapter.startTimeOffset).coerceAtLeast(0L)
        }

    /**
     * Position within the current chapter in milliseconds.
     * Since chapter offsets are track-relative, we compare against track position.
     */
    val currentChapterPositionMs: Long
        get() {
            val chapter = currentChapter ?: return 0L
            return (currentTrackPositionMs - chapter.startTimeOffset).coerceAtLeast(0L)
        }

    /**
     * Progress through the book as a fraction (0.0 to 1.0).
     */
    val bookProgress: Float
        get() {
            val duration = bookDurationMs
            return if (duration > 0) {
                (bookPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    /**
     * Progress through the current track as a fraction (0.0 to 1.0).
     */
    val trackProgress: Float
        get() {
            val duration = currentTrackDurationMs
            return if (duration > 0) {
                (currentTrackPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    /**
     * Progress through the current chapter as a fraction (0.0 to 1.0).
     */
    val chapterProgress: Float
        get() {
            val duration = currentChapterDurationMs
            return if (duration > 0) {
                (currentChapterPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    // =====================
    // State Update Methods
    // =====================

    /**
     * Creates a copy with updated position.
     */
    fun withPosition(
        trackIndex: Int,
        positionMs: Long,
    ): PlaybackState =
        copy(
            currentTrackIndex = trackIndex.coerceIn(0, tracks.lastIndex.coerceAtLeast(0)),
            currentTrackPositionMs = positionMs.coerceAtLeast(0L),
            lastUpdatedAtMs = System.currentTimeMillis(),
        )

    /**
     * Creates a copy with updated playback state.
     */
    fun withPlayingState(isPlaying: Boolean): PlaybackState =
        copy(
            isPlaying = isPlaying,
            lastUpdatedAtMs = System.currentTimeMillis(),
        )

    /**
     * Creates a copy with updated playback speed.
     */
    fun withPlaybackSpeed(speed: Float): PlaybackState =
        copy(
            playbackSpeed = speed.coerceIn(0.5f, 3.0f),
            lastUpdatedAtMs = System.currentTimeMillis(),
        )

    // =====================
    // Utility Methods
    // =====================

    /**
     * Returns whether the position has changed significantly from another state.
     * Used to determine if state should be persisted to database.
     *
     * @param other The previous state to compare against
     * @param thresholdMs Minimum position change to be considered significant
     */
    fun hasSignificantPositionChange(
        other: PlaybackState,
        thresholdMs: Long = 1000L,
    ): Boolean {
        if (audiobook?.key != other.audiobook?.key) return true
        if (currentTrackIndex != other.currentTrackIndex) return true
        return kotlin.math.abs(currentTrackPositionMs - other.currentTrackPositionMs) >= thresholdMs
    }

    override fun toString(): String {
        val bookTitle = audiobook?.title?.take(20) ?: "None"
        return "PlaybackState(book=$bookTitle, track=$currentTrackIndex, " +
            "pos=${currentTrackPositionMs}ms, playing=$isPlaying)"
    }
}

/**
 * Extension property to get the unique key for an audiobook.
 * For Plex: uses the rating key (id)
 */
private val Audiobook.key: String
    get() = id.toString()
