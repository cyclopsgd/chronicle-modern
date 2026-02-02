package local.oss.chronicle.features.player.media3

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import local.oss.chronicle.R
import local.oss.chronicle.application.MainActivity
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.EMPTY_AUDIOBOOK
import local.oss.chronicle.data.model.MediaItemTrack
import local.oss.chronicle.data.model.asChapterList
import local.oss.chronicle.data.model.getActiveTrack
import local.oss.chronicle.data.model.getProgress
import local.oss.chronicle.data.model.toMedia3MediaItem
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.data.sources.plex.model.getDuration
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.player.PlaybackErrorHandler
import local.oss.chronicle.features.player.PlaybackNetworkCoordinator
import local.oss.chronicle.features.player.PlaybackStateController
import local.oss.chronicle.features.player.ProgressUpdater
import local.oss.chronicle.features.player.SleepTimer
import local.oss.chronicle.features.player.SmartRewindCalculator
import local.oss.chronicle.features.player.TrackListStateManager
import local.oss.chronicle.util.ServiceUtils
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Media3 MediaLibraryService implementation for audiobook playback.
 *
 * This service replaces the legacy MediaBrowserServiceCompat + MediaSessionCompat architecture
 * with the modern AndroidX Media3 APIs. Key benefits:
 * - Unified ExoPlayer + MediaSession integration
 * - Automatic notification handling
 * - Native Android Auto support via MediaLibrarySession
 * - Simplified state management
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class Media3PlayerService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Inject lateinit var prefsRepo: PrefsRepo
    @Inject lateinit var plexPrefs: PlexPrefsRepo
    @Inject lateinit var plexConfig: PlexConfig
    @Inject lateinit var bookRepository: IBookRepository
    @Inject lateinit var trackRepository: ITrackRepository
    @Inject lateinit var currentlyPlaying: CurrentlyPlaying
    @Inject lateinit var playbackStateController: PlaybackStateController
    @Inject lateinit var smartRewindCalculator: SmartRewindCalculator
    @Inject lateinit var playbackErrorHandler: PlaybackErrorHandler
    @Inject lateinit var playbackNetworkCoordinator: PlaybackNetworkCoordinator
    @Inject lateinit var progressUpdater: ProgressUpdater
    @Inject lateinit var playbackUrlResolver: local.oss.chronicle.data.sources.plex.PlaybackUrlResolver

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private val trackListStateManager = TrackListStateManager()

    private var errorRetryCount = 0

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Coroutine exception in Media3PlayerService")
    }

    companion object {
        // Custom command identifiers
        const val COMMAND_SET_SLEEP_TIMER = "SET_SLEEP_TIMER"
        const val COMMAND_CANCEL_SLEEP_TIMER = "CANCEL_SLEEP_TIMER"
        const val COMMAND_SKIP_FORWARD_CUSTOM = "SKIP_FORWARD_CUSTOM"
        const val COMMAND_SKIP_BACKWARD_CUSTOM = "SKIP_BACKWARD_CUSTOM"
        const val COMMAND_SEEK_TO_CHAPTER = "SEEK_TO_CHAPTER"
        const val COMMAND_SET_PLAYBACK_SPEED = "SET_PLAYBACK_SPEED"

        // Extra keys
        const val EXTRA_SLEEP_TIMER_DURATION = "SLEEP_TIMER_DURATION"
        const val EXTRA_CHAPTER_INDEX = "CHAPTER_INDEX"
        const val EXTRA_PLAYBACK_SPEED = "PLAYBACK_SPEED"

        // Buffer configuration
        val BACK_BUFFER_DURATION_MS: Int = 120.seconds.inWholeMilliseconds.toInt()
        val MIN_BUFFER_DURATION_MS: Int = 10.seconds.inWholeMilliseconds.toInt()
        val MAX_BUFFER_DURATION_MS: Int = 360.seconds.inWholeMilliseconds.toInt()

        // Media browse tree constants
        const val MEDIA_ROOT_ID = "opus_media_root"
        const val MEDIA_EMPTY_ROOT = "opus_empty_root"
        const val RECENTLY_LISTENED_ID = "recently_listened"
        const val RECENTLY_ADDED_ID = "recently_added"
        const val LIBRARY_ID = "library"
        const val OFFLINE_ID = "offline"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("Media3PlayerService created")
        ServiceUtils.notifyServiceStarted(this)

        initializePlayer()
        initializeMediaSession()

        // Start observing network state for playback coordination
        playbackNetworkCoordinator.startObserving(serviceScope)
        playbackNetworkCoordinator.onNetworkRestoredCallback = {
            serviceScope.launch {
                if (player?.playWhenReady == false) {
                    Timber.i("Resuming playback after network restoration")
                    player?.play()
                }
            }
        }
    }

    private fun initializePlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(BACK_BUFFER_DURATION_MS, true)
            .setBufferDurationsMs(
                MIN_BUFFER_DURATION_MS,
                MAX_BUFFER_DURATION_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(buildAudioAttributes(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exoPlayer ->
                exoPlayer.addListener(playerListener)
                exoPlayer.setPlaybackParameters(
                    PlaybackParameters(prefsRepo.playbackSpeed, 1.0f)
                )
                exoPlayer.skipSilenceEnabled = prefsRepo.skipSilence
            }
    }

    private fun buildAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(
                if (prefsRepo.pauseOnFocusLost) C.AUDIO_CONTENT_TYPE_SPEECH
                else C.AUDIO_CONTENT_TYPE_MUSIC
            )
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    private fun initializeMediaSession() {
        val sessionActivityPendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.putExtra(MainActivity.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
            PendingIntent.getActivity(
                this,
                MainActivity.REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        mediaSession = MediaLibrarySession.Builder(this, player!!, librarySessionCallback)
            .setSessionActivity(sessionActivityPendingIntent!!)
            .setId("OpusAudiobookSession")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("onTaskRemoved: Initiating emergency progress save")
        progressUpdater.emergencySaveProgress()

        player?.let {
            if (!it.playWhenReady || it.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        Timber.i("Media3PlayerService destroyed")

        playbackNetworkCoordinator.stopObserving()
        serviceScope.cancel()

        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null

        ServiceUtils.notifyServiceStopped(this)
        super.onDestroy()
    }

    // ========== Player Listener ==========

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "ExoPlayer error")

            val recoveryAction = playbackErrorHandler.determineRecoveryAction(error, errorRetryCount)

            serviceScope.launch(exceptionHandler) {
                playbackErrorHandler.executeRecovery(
                    action = recoveryAction,
                    onRetry = {
                        errorRetryCount++
                        Timber.i("Retrying playback (attempt $errorRetryCount)")
                        player?.prepare()
                        player?.play()
                    },
                    onRefreshUrl = {
                        errorRetryCount++
                        Timber.i("Refreshing URL and retrying (attempt $errorRetryCount)")
                        player?.prepare()
                        player?.play()
                    },
                    onSkipTrack = {
                        Timber.i("Skipping to next track due to error")
                        errorRetryCount = 0
                        player?.seekToNextMediaItem()
                    },
                    onWaitForNetwork = {
                        Timber.i("Waiting for network to resume playback")
                        val wasPlaying = player?.playWhenReady == true
                        playbackNetworkCoordinator.notifyPlaybackInterruptedByNetwork(wasPlaying)
                    },
                    onNotifyUser = { message ->
                        Timber.w("Unrecoverable playback error: $message")
                        errorRetryCount = 0
                    }
                )
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                // Resuming playback - apply smart rewind
                val rewindDuration = smartRewindCalculator.calculateRewindDuration()
                if (rewindDuration > 0) {
                    val currentPosition = player?.currentPosition ?: 0L
                    val newPosition = maxOf(0L, currentPosition - rewindDuration)
                    Timber.i("SmartRewind: Rewinding from $currentPosition to $newPosition (${rewindDuration}ms)")
                    player?.seekTo(newPosition)
                }
                smartRewindCalculator.clearPauseTimestamp()
                errorRetryCount = 0
            } else {
                // Pausing - record timestamp for smart rewind
                smartRewindCalculator.recordPause()
            }

            serviceScope.launch {
                playbackStateController.updatePlayingState(playWhenReady)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            serviceScope.launch {
                playbackStateController.updatePlayingState(isPlaying)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                errorRetryCount = 0
            }

            if (playbackState == Player.STATE_ENDED) {
                Timber.i("Playback ended")
                serviceScope.launch {
                    val activeTrack = trackListStateManager.trackList.getActiveTrack()
                    if (activeTrack.id != MediaItemTrack.EMPTY_TRACK.id) {
                        withContext(Dispatchers.IO) {
                            progressUpdater.updateProgress(
                                activeTrack.id,
                                "stopped",
                                activeTrack.duration,
                                true
                            )
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Timber.d("Media item transition: ${mediaItem?.mediaId}, reason: $reason")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                Timber.i("Playing next track (auto transition)")
            }
        }
    }

    // ========== MediaLibrarySession.Callback ==========

    private val librarySessionCallback = object : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Timber.i("Media3 session connection from: ${controller.packageName}")

            // Build available custom commands
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_FORWARD_CUSTOM, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_BACKWARD_CUSTOM, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_TO_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_PLAYBACK_SPEED, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            Timber.i("Custom command received: ${customCommand.customAction}")

            return when (customCommand.customAction) {
                COMMAND_SET_SLEEP_TIMER -> {
                    val durationMs = args.getLong(EXTRA_SLEEP_TIMER_DURATION, 0L)
                    handleSetSleepTimer(durationMs)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_CANCEL_SLEEP_TIMER -> {
                    handleCancelSleepTimer()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SKIP_FORWARD_CUSTOM -> {
                    handleSkipForward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SKIP_BACKWARD_CUSTOM -> {
                    handleSkipBackward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SEEK_TO_CHAPTER -> {
                    val chapterIndex = args.getInt(EXTRA_CHAPTER_INDEX, -1)
                    handleSeekToChapter(chapterIndex)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SET_PLAYBACK_SPEED -> {
                    val speed = args.getFloat(EXTRA_PLAYBACK_SPEED, 1.0f)
                    handleSetPlaybackSpeed(speed)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> {
                    Timber.w("Unknown custom command: ${customCommand.customAction}")
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Timber.d("[AndroidAuto] onGetLibraryRoot from: ${browser.packageName}")

            if (!prefsRepo.allowAuto) {
                Timber.w("[AndroidAuto] Access denied - Android Auto is disabled")
                return Futures.immediateFuture(
                    LibraryResult.ofItem(createEmptyRootItem(), params)
                )
            }

            val rootItem = MediaItem.Builder()
                .setMediaId(MEDIA_ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Timber.d("[AndroidAuto] onGetChildren: parentId=$parentId, page=$page, pageSize=$pageSize")

            // Use runBlocking to call suspend functions from non-suspend context
            // This is acceptable here as Media3 expects synchronous-ish results
            return Futures.immediateFuture(
                runBlocking(Dispatchers.IO) {
                    try {
                        when (parentId) {
                            MEDIA_ROOT_ID -> {
                                val categories = listOf(
                                    createBrowsableItem(
                                        RECENTLY_LISTENED_ID,
                                        getString(R.string.auto_category_recently_listened)
                                    ),
                                    createBrowsableItem(
                                        OFFLINE_ID,
                                        getString(R.string.auto_category_offline)
                                    ),
                                    createBrowsableItem(
                                        RECENTLY_ADDED_ID,
                                        getString(R.string.auto_category_recently_added)
                                    ),
                                    createBrowsableItem(
                                        LIBRARY_ID,
                                        getString(R.string.auto_category_library)
                                    )
                                )
                                LibraryResult.ofItemList(categories, params)
                            }

                            RECENTLY_LISTENED_ID -> {
                                val books = bookRepository.getRecentlyListenedAsync()
                                val items = books.mapNotNull { it.toMedia3MediaItem(plexConfig) }
                                LibraryResult.ofItemList(items, params)
                            }

                            RECENTLY_ADDED_ID -> {
                                val books = bookRepository.getRecentlyAddedAsync()
                                val items = books.mapNotNull { it.toMedia3MediaItem(plexConfig) }
                                LibraryResult.ofItemList(items, params)
                            }

                            LIBRARY_ID -> {
                                val books = bookRepository.getAllBooksAsync()
                                val items = books.mapNotNull { it.toMedia3MediaItem(plexConfig) }
                                LibraryResult.ofItemList(items, params)
                            }

                            OFFLINE_ID -> {
                                val books = bookRepository.getCachedAudiobooksAsync()
                                val items = books.mapNotNull { it.toMedia3MediaItem(plexConfig) }
                                LibraryResult.ofItemList(items, params)
                            }

                            else -> {
                                Timber.w("[AndroidAuto] Unknown parentId: $parentId")
                                LibraryResult.ofItemList(emptyList(), params)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error in onGetChildren")
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
                    }
                }
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Timber.d("[AndroidAuto] onGetItem: mediaId=$mediaId")

            return Futures.immediateFuture(
                runBlocking(Dispatchers.IO) {
                    try {
                        val bookId = mediaId.toIntOrNull()
                        if (bookId != null) {
                            val book = bookRepository.getAudiobookAsync(bookId)
                            if (book != null && book != EMPTY_AUDIOBOOK) {
                                LibraryResult.ofItem(book.toMedia3MediaItem(plexConfig), null)
                            } else {
                                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                            }
                        } else {
                            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error getting item: $mediaId")
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
                    }
                }
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            Timber.i("[AndroidAuto] onSearch: query='$query'")

            serviceScope.launch(exceptionHandler) {
                try {
                    val books = withContext(Dispatchers.IO) {
                        bookRepository.searchAsync(query)
                    }
                    session.notifySearchResultChanged(browser, query, books.size, params)
                } catch (e: Exception) {
                    Timber.e(e, "Error during search")
                    session.notifySearchResultChanged(browser, query, 0, params)
                }
            }

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Timber.d("[AndroidAuto] onGetSearchResult: query='$query', page=$page")

            return Futures.immediateFuture(
                runBlocking(Dispatchers.IO) {
                    try {
                        val books = bookRepository.searchAsync(query)
                        val items = books.mapNotNull { it.toMedia3MediaItem(plexConfig) }
                        LibraryResult.ofItemList(items, params)
                    } catch (e: Exception) {
                        Timber.e(e, "Error getting search results")
                        LibraryResult.ofItemList(emptyList(), params)
                    }
                }
            )
        }

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Timber.i("Playback resumption requested")

            return Futures.immediateFuture(
                runBlocking(Dispatchers.IO) {
                    try {
                        val book = bookRepository.getMostRecentlyPlayed()
                        if (book == EMPTY_AUDIOBOOK) {
                            MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                        } else {
                            val tracks = trackRepository.getTracksForAudiobookAsync(book.id)
                            val items = tracks.map { it.toMedia3MediaItem(plexConfig) }
                            val activeTrack = tracks.getActiveTrack()
                            val startIndex = tracks.indexOf(activeTrack).coerceAtLeast(0)
                            MediaSession.MediaItemsWithStartPosition(items, startIndex, activeTrack.progress)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error during playback resumption")
                        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                    }
                }
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            Timber.d("onAddMediaItems: ${mediaItems.map { it.mediaId }}")

            return Futures.immediateFuture(
                runBlocking(Dispatchers.IO) {
                    mediaItems.mapNotNull { item ->
                        try {
                            val bookId = item.mediaId.toIntOrNull() ?: return@mapNotNull null
                            bookRepository.getAudiobookAsync(bookId) ?: return@mapNotNull null
                            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)

                            // Return resolved track items
                            if (tracks.isNotEmpty()) {
                                tracks.first().toMedia3MediaItem(plexConfig)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error resolving media item: ${item.mediaId}")
                            null
                        }
                    }
                }
            )
        }
    }

    // ========== Custom Command Handlers ==========

    private fun handleSetSleepTimer(durationMs: Long) {
        Timber.i("Setting sleep timer for ${durationMs}ms")
        // Sleep timer would need to be injected and configured for Media3 service
        // For now, just log the request
    }

    private fun handleCancelSleepTimer() {
        Timber.i("Cancelling sleep timer")
        // Sleep timer would need to be injected and configured for Media3 service
    }

    private fun handleSkipForward() {
        val skipMs = prefsRepo.jumpForwardSeconds * 1000L
        val newPosition = (player?.currentPosition ?: 0L) + skipMs
        player?.seekTo(newPosition)
    }

    private fun handleSkipBackward() {
        val skipMs = prefsRepo.jumpBackwardSeconds * 1000L
        val newPosition = maxOf(0L, (player?.currentPosition ?: 0L) - skipMs)
        player?.seekTo(newPosition)
    }

    private fun handleSeekToChapter(chapterIndex: Int) {
        if (chapterIndex < 0) return

        val chapters = currentlyPlaying.book.value.chapters
        if (chapterIndex >= chapters.size) return

        val chapter = chapters[chapterIndex]
        val trackIndex = trackListStateManager.trackList.indexOfFirst {
            it.id.toLong() == chapter.trackId
        }

        if (trackIndex >= 0) {
            player?.seekTo(trackIndex, chapter.startTimeOffset)
        }
    }

    private fun handleSetPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        player?.setPlaybackParameters(PlaybackParameters(clampedSpeed, 1.0f))
        prefsRepo.playbackSpeed = clampedSpeed
    }

    // ========== Helper Methods ==========

    private fun createEmptyRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MEDIA_EMPTY_ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun createBrowsableItem(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    /**
     * Play an audiobook by its ID.
     * This is the main entry point for starting playback of a book.
     */
    fun playBook(bookId: Int, trackId: Int? = null, startPosition: Long? = null) {
        serviceScope.launch(exceptionHandler) {
            val book = withContext(Dispatchers.IO) {
                bookRepository.getAudiobookAsync(bookId)
            }

            if (book == null || book == EMPTY_AUDIOBOOK) {
                Timber.w("Cannot play book: book not found (id=$bookId)")
                return@launch
            }

            val tracks = withContext(Dispatchers.IO) {
                trackRepository.getTracksForAudiobookAsync(bookId)
            }

            if (tracks.isEmpty()) {
                Timber.w("Cannot play book: no tracks found (id=$bookId)")
                handlePlayBookWithNoTracks(bookId)
                return@launch
            }

            // Pre-resolve streaming URLs
            try {
                Timber.i("Pre-resolving streaming URLs for ${tracks.size} tracks...")
                val resolvedCount = withContext(Dispatchers.IO) {
                    playbackUrlResolver.preResolveUrls(tracks)
                }
                Timber.i("Successfully pre-resolved $resolvedCount/${tracks.size} streaming URLs")
            } catch (e: Exception) {
                Timber.w(e, "Failed to pre-resolve streaming URLs, will fall back to direct file URLs")
            }

            trackListStateManager.trackList = tracks

            val startingTrack = if (trackId != null) {
                tracks.find { it.id == trackId } ?: tracks.getActiveTrack()
            } else {
                tracks.getActiveTrack()
            }

            val startingTrackIndex = tracks.sorted().indexOf(startingTrack)
            val startPositionMs = startPosition ?: startingTrack.progress

            trackListStateManager.updatePosition(startingTrackIndex, startPositionMs)

            // Load chapters
            val chapters = book.chapters.ifEmpty { tracks.asChapterList() }

            // Update playback state controller
            playbackStateController.loadAudiobook(
                audiobook = book,
                tracks = tracks,
                chapters = chapters,
                startTrackIndex = startingTrackIndex,
                startPositionMs = startPositionMs
            )

            // Update currently playing
            currentlyPlaying.update(
                book = book,
                tracks = tracks,
                track = startingTrack.copy(progress = startPositionMs)
            )

            // Build Media3 MediaItems
            val mediaItems = tracks.map { it.toMedia3MediaItem(plexConfig) }

            player?.apply {
                setMediaItems(mediaItems, startingTrackIndex, startPositionMs)
                prepare()
                play()
            }

            Timber.i("Started playback: book='${book.title}', track=$startingTrackIndex, position=$startPositionMs")
        }
    }

    private suspend fun handlePlayBookWithNoTracks(bookId: Int) {
        Timber.i("No known tracks for book: $bookId, attempting to fetch them")

        val result = withContext(Dispatchers.IO) {
            trackRepository.loadTracksForAudiobook(bookId)
        }

        if (result is com.github.michaelbull.result.Ok) {
            val tracks = result.value
            bookRepository.updateTrackData(
                bookId,
                tracks.getProgress(),
                tracks.getDuration(),
                tracks.size
            )

            val book = bookRepository.getAudiobookAsync(bookId)
            if (book != null) {
                withContext(Dispatchers.IO) {
                    bookRepository.syncAudiobook(book, tracks)
                }
            }

            // Retry playback now that tracks are loaded
            playBook(bookId)
        } else {
            Timber.e("Failed to load tracks for book: $bookId")
        }
    }
}
