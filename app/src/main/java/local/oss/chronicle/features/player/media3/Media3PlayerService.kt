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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import local.oss.chronicle.BuildConfig
import local.oss.chronicle.data.sources.plex.APP_NAME
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
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.data.model.EMPTY_AUDIOBOOK
import local.oss.chronicle.data.model.MediaItemTrack
import local.oss.chronicle.data.model.asChapterList
import local.oss.chronicle.data.model.getActiveTrack
import local.oss.chronicle.data.model.getProgress
import local.oss.chronicle.data.model.toMedia3MediaItem
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.data.sources.plex.model.getDuration
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
    private var positionObserverJob: kotlinx.coroutines.Job? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Coroutine exception in Media3PlayerService")
    }

    companion object {
        // Custom command identifiers (Media3)
        const val COMMAND_SET_SLEEP_TIMER = "SET_SLEEP_TIMER"
        const val COMMAND_CANCEL_SLEEP_TIMER = "CANCEL_SLEEP_TIMER"
        const val COMMAND_SKIP_FORWARD_CUSTOM = "SKIP_FORWARD_CUSTOM"
        const val COMMAND_SKIP_BACKWARD_CUSTOM = "SKIP_BACKWARD_CUSTOM"
        const val COMMAND_SEEK_TO_CHAPTER = "SEEK_TO_CHAPTER"
        const val COMMAND_SET_PLAYBACK_SPEED = "SET_PLAYBACK_SPEED"

        // Legacy custom action names (for backwards compatibility with MediaSessionCompat clients)
        const val LEGACY_SKIP_FORWARDS = "Skip forwards"
        const val LEGACY_SKIP_BACKWARDS = "Skip backwards"
        const val LEGACY_SKIP_TO_NEXT = "Skip to next"
        const val LEGACY_SKIP_TO_PREVIOUS = "Skip to previous"
        const val LEGACY_SET_PLAYBACK_SPEED = "SET_PLAYBACK_SPEED"

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

        // Create DataSource factory with Plex authentication headers
        val dataSourceFactory = createPlexDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
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

    /**
     * Creates a DataSource factory with Plex authentication headers.
     * This is required for ExoPlayer to access Plex media files.
     */
    private fun createPlexDataSourceFactory(): DefaultHttpDataSource.Factory {
        val authToken = plexPrefs.server?.accessToken
            ?: plexPrefs.user?.authToken
            ?: plexPrefs.accountAuthToken

        return DefaultHttpDataSource.Factory()
            .setUserAgent("$APP_NAME/${BuildConfig.VERSION_NAME}")
            .setDefaultRequestProperties(
                mapOf(
                    "X-Plex-Platform" to "Android",
                    "X-Plex-Provides" to "player",
                    "X-Plex-Client-Name" to APP_NAME,
                    "X-Plex-Client-Identifier" to plexPrefs.uuid,
                    "X-Plex-Version" to BuildConfig.VERSION_NAME,
                    "X-Plex-Product" to APP_NAME,
                    "X-Plex-Platform-Version" to android.os.Build.VERSION.RELEASE,
                    "X-Plex-Device" to android.os.Build.MODEL,
                    "X-Plex-Device-Name" to android.os.Build.MODEL,
                    "X-Plex-Token" to authToken,
                    "X-Plex-Session-Identifier" to plexPrefs.uuid,
                )
            )
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
            // Start/stop position observer
            if (isPlaying) {
                startPositionObserver()
            } else {
                stopPositionObserver()
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

    // ========== Position Observer ==========

    private fun startPositionObserver() {
        positionObserverJob?.cancel()
        positionObserverJob = serviceScope.launch {
            while (true) {
                val p = player ?: break
                if (!p.isPlaying) break

                val trackIndex = p.currentMediaItemIndex
                val position = p.currentPosition

                // Update PlaybackStateController with current position
                playbackStateController.updatePosition(trackIndex, position)

                // Also update trackListStateManager for internal state
                trackListStateManager.updatePosition(trackIndex, position)

                kotlinx.coroutines.delay(500L) // Update every 500ms
            }
        }
    }

    private fun stopPositionObserver() {
        positionObserverJob?.cancel()
        positionObserverJob = null

        // Final position update when stopping
        player?.let { p ->
            serviceScope.launch {
                playbackStateController.updatePosition(p.currentMediaItemIndex, p.currentPosition)
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

            // Build available custom commands (both Media3 and legacy)
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                // Media3 commands
                .add(SessionCommand(COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_FORWARD_CUSTOM, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_BACKWARD_CUSTOM, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_TO_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_PLAYBACK_SPEED, Bundle.EMPTY))
                // Legacy commands for backwards compatibility
                .add(SessionCommand(LEGACY_SKIP_FORWARDS, Bundle.EMPTY))
                .add(SessionCommand(LEGACY_SKIP_BACKWARDS, Bundle.EMPTY))
                .add(SessionCommand(LEGACY_SKIP_TO_NEXT, Bundle.EMPTY))
                .add(SessionCommand(LEGACY_SKIP_TO_PREVIOUS, Bundle.EMPTY))
                .add(SessionCommand(LEGACY_SET_PLAYBACK_SPEED, Bundle.EMPTY))
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
                // Media3 commands
                COMMAND_SET_SLEEP_TIMER -> {
                    val durationMs = args.getLong(EXTRA_SLEEP_TIMER_DURATION, 0L)
                    handleSetSleepTimer(durationMs)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_CANCEL_SLEEP_TIMER -> {
                    handleCancelSleepTimer()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SKIP_FORWARD_CUSTOM, LEGACY_SKIP_FORWARDS -> {
                    Timber.i("Handling skip forward command")
                    handleSkipForward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SKIP_BACKWARD_CUSTOM, LEGACY_SKIP_BACKWARDS -> {
                    Timber.i("Handling skip backward command")
                    handleSkipBackward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                LEGACY_SKIP_TO_NEXT -> {
                    Timber.i("Handling skip to next chapter command")
                    handleSkipToNextChapter()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                LEGACY_SKIP_TO_PREVIOUS -> {
                    Timber.i("Handling skip to previous chapter command")
                    handleSkipToPreviousChapter()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SEEK_TO_CHAPTER -> {
                    val chapterIndex = args.getInt(EXTRA_CHAPTER_INDEX, -1)
                    handleSeekToChapter(chapterIndex)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SET_PLAYBACK_SPEED, LEGACY_SET_PLAYBACK_SPEED -> {
                    val speed = args.getFloat(EXTRA_PLAYBACK_SPEED, 1.0f)
                    Timber.i("Handling set playback speed command: $speed")
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
                            val mediaItem = book?.toMedia3MediaItem(plexConfig)
                            if (mediaItem != null) {
                                LibraryResult.ofItem(mediaItem, null)
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
            Timber.i("onAddMediaItems: ${mediaItems.map { it.mediaId }}")

            return Futures.immediateFuture(
                runBlocking(Dispatchers.IO) {
                    val allResolvedItems = mutableListOf<MediaItem>()

                    for (item in mediaItems) {
                        try {
                            val bookId = item.mediaId.toIntOrNull()
                            if (bookId == null) {
                                Timber.w("Invalid mediaId (not an integer): ${item.mediaId}")
                                continue
                            }

                            val book = bookRepository.getAudiobookAsync(bookId)
                            if (book == null || book == EMPTY_AUDIOBOOK) {
                                Timber.w("Book not found for mediaId: $bookId")
                                continue
                            }

                            var tracks = trackRepository.getTracksForAudiobookAsync(bookId)

                            // If no tracks, try to load them
                            if (tracks.isEmpty()) {
                                Timber.i("No tracks found for book $bookId, attempting to load...")
                                val result = trackRepository.loadTracksForAudiobook(bookId)
                                if (result is com.github.michaelbull.result.Ok) {
                                    tracks = result.value
                                    bookRepository.updateTrackData(
                                        bookId,
                                        tracks.getProgress(),
                                        tracks.getDuration(),
                                        tracks.size
                                    )
                                }
                            }

                            if (tracks.isEmpty()) {
                                Timber.w("Still no tracks for book $bookId after loading attempt")
                                continue
                            }

                            // Sync audiobook to load chapters from Plex and wait for completion
                            val syncSuccess = bookRepository.syncAudiobook(book, tracks)
                            Timber.i("onAddMediaItems: syncAudiobook completed: success=$syncSuccess")

                            // Re-fetch book to get updated chapters
                            val updatedBook = bookRepository.getAudiobookAsync(bookId) ?: book

                            // Pre-resolve streaming URLs
                            try {
                                Timber.i("Pre-resolving streaming URLs for ${tracks.size} tracks...")
                                playbackUrlResolver.preResolveUrls(tracks)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to pre-resolve URLs, continuing anyway")
                            }

                            // Update internal state
                            trackListStateManager.trackList = tracks

                            val activeTrack = tracks.getActiveTrack()
                            val startingTrackIndex = tracks.sorted().indexOf(activeTrack).coerceAtLeast(0)
                            val startPositionMs = activeTrack.progress

                            trackListStateManager.updatePosition(startingTrackIndex, startPositionMs)

                            // Load chapters from updated book (which now has chapters from Plex sync)
                            val chapters = updatedBook.chapters.ifEmpty { tracks.asChapterList() }
                            Timber.i("onAddMediaItems: Loaded ${chapters.size} chapters for book '${updatedBook.title}' (from book: ${updatedBook.chapters.size}, from tracks: ${tracks.size})")

                            // Update playback state controller
                            playbackStateController.loadAudiobook(
                                audiobook = updatedBook,
                                tracks = tracks,
                                chapters = chapters,
                                startTrackIndex = startingTrackIndex,
                                startPositionMs = startPositionMs
                            )

                            // Return ALL track items for this book
                            val trackItems = tracks.map { it.toMedia3MediaItem(plexConfig) }
                            allResolvedItems.addAll(trackItems)

                            Timber.i("Resolved book '${book.title}' with ${tracks.size} tracks, starting at index $startingTrackIndex, position $startPositionMs")

                        } catch (e: Exception) {
                            Timber.e(e, "Error resolving media item: ${item.mediaId}")
                        }
                    }

                    allResolvedItems
                }
            )
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Timber.i("onSetMediaItems: ${mediaItems.size} items, startIndex=$startIndex, startPos=$startPositionMs")

            // Check if this is a book ID that needs to be resolved
            if (mediaItems.size == 1) {
                val bookId = mediaItems.first().mediaId.toIntOrNull()
                if (bookId != null) {
                    return Futures.immediateFuture(
                        runBlocking(Dispatchers.IO) {
                            try {
                                val book = bookRepository.getAudiobookAsync(bookId)
                                if (book == null || book == EMPTY_AUDIOBOOK) {
                                    return@runBlocking MediaSession.MediaItemsWithStartPosition(
                                        mediaItems, startIndex, startPositionMs
                                    )
                                }

                                var tracks = trackRepository.getTracksForAudiobookAsync(bookId)

                                if (tracks.isEmpty()) {
                                    val result = trackRepository.loadTracksForAudiobook(bookId)
                                    if (result is com.github.michaelbull.result.Ok) {
                                        tracks = result.value
                                        bookRepository.updateTrackData(
                                            bookId,
                                            tracks.getProgress(),
                                            tracks.getDuration(),
                                            tracks.size
                                        )
                                    }
                                }

                                if (tracks.isEmpty()) {
                                    return@runBlocking MediaSession.MediaItemsWithStartPosition(
                                        emptyList(), 0, 0L
                                    )
                                }

                                // Sync audiobook to load chapters from Plex and wait for completion
                                val syncSuccess = bookRepository.syncAudiobook(book, tracks)
                                Timber.i("syncAudiobook completed: success=$syncSuccess")

                                // Re-fetch book to get updated chapters
                                val updatedBook = bookRepository.getAudiobookAsync(bookId) ?: book

                                // Pre-resolve streaming URLs
                                try {
                                    playbackUrlResolver.preResolveUrls(tracks)
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to pre-resolve URLs")
                                }

                                trackListStateManager.trackList = tracks

                                val activeTrack = tracks.getActiveTrack()
                                val resolvedStartIndex = tracks.sorted().indexOf(activeTrack).coerceAtLeast(0)
                                val resolvedStartPos = activeTrack.progress

                                trackListStateManager.updatePosition(resolvedStartIndex, resolvedStartPos)

                                val chapters = updatedBook.chapters.ifEmpty { tracks.asChapterList() }
                                Timber.i("Loaded ${chapters.size} chapters for book '${updatedBook.title}' (from book: ${updatedBook.chapters.size}, from tracks: ${tracks.size})")

                                playbackStateController.loadAudiobook(
                                    audiobook = updatedBook,
                                    tracks = tracks,
                                    chapters = chapters,
                                    startTrackIndex = resolvedStartIndex,
                                    startPositionMs = resolvedStartPos
                                )

                                val trackItems = tracks.map { it.toMedia3MediaItem(plexConfig) }
                                Timber.i("onSetMediaItems resolved: ${trackItems.size} tracks, start=$resolvedStartIndex, pos=$resolvedStartPos")

                                // Schedule playback to start after items are set
                                serviceScope.launch {
                                    // Small delay to ensure items are set on player
                                    kotlinx.coroutines.delay(100)
                                    player?.let { p ->
                                        if (p.mediaItemCount > 0 && !p.isPlaying) {
                                            Timber.i("Starting playback after item resolution")
                                            p.prepare()
                                            p.play()
                                        }
                                    }
                                }

                                MediaSession.MediaItemsWithStartPosition(
                                    trackItems, resolvedStartIndex, resolvedStartPos
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Error in onSetMediaItems")
                                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                            }
                        }
                    )
                }
            }

            // Fallback to default behavior
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
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

        val chapters = playbackStateController.currentState.chapters
        if (chapterIndex >= chapters.size) return

        val chapter = chapters[chapterIndex]
        seekToChapter(chapter)
    }

    /**
     * Skip to the next chapter based on current position.
     * This works for M4B files with embedded chapters.
     */
    private fun handleSkipToNextChapter() {
        val state = playbackStateController.currentState
        val chapters = state.chapters
        if (chapters.isEmpty()) {
            // No chapters - fall back to track skip
            player?.seekToNextMediaItem()
            return
        }

        val currentChapterIndex = state.currentChapterIndex
        val nextChapterIndex = currentChapterIndex + 1

        if (nextChapterIndex < chapters.size) {
            val nextChapter = chapters[nextChapterIndex]
            Timber.i("Skipping to next chapter: ${nextChapter.title} (index $nextChapterIndex)")
            seekToChapter(nextChapter)
        } else {
            Timber.i("Already at last chapter")
            // Optionally skip to next track if at last chapter
            player?.seekToNextMediaItem()
        }
    }

    /**
     * Skip to the previous chapter based on current position.
     * If more than 3 seconds into current chapter, restart it.
     * Otherwise, go to previous chapter.
     */
    private fun handleSkipToPreviousChapter() {
        val state = playbackStateController.currentState
        val chapters = state.chapters
        if (chapters.isEmpty()) {
            // No chapters - fall back to track skip
            player?.seekToPreviousMediaItem()
            return
        }

        val currentChapterIndex = state.currentChapterIndex
        val positionInChapter = state.currentChapterPositionMs

        // If more than 3 seconds into chapter, restart current chapter
        if (positionInChapter > 3000L) {
            val currentChapter = chapters.getOrNull(currentChapterIndex)
            if (currentChapter != null) {
                Timber.i("Restarting current chapter: ${currentChapter.title}")
                seekToChapter(currentChapter)
                return
            }
        }

        // Otherwise, go to previous chapter
        val prevChapterIndex = currentChapterIndex - 1
        if (prevChapterIndex >= 0) {
            val prevChapter = chapters[prevChapterIndex]
            Timber.i("Skipping to previous chapter: ${prevChapter.title} (index $prevChapterIndex)")
            seekToChapter(prevChapter)
        } else {
            Timber.i("Already at first chapter, seeking to start")
            // Seek to start of first chapter/track
            player?.seekTo(0, 0L)
        }
    }

    /**
     * Seek to a specific chapter by finding its track and position.
     */
    private fun seekToChapter(chapter: Chapter) {
        val tracks = trackListStateManager.trackList
        val trackIndex = tracks.indexOfFirst { it.id.toLong() == chapter.trackId }

        if (trackIndex >= 0) {
            Timber.i("Seeking to chapter '${chapter.title}' at track $trackIndex, position ${chapter.startTimeOffset}ms")
            player?.seekTo(trackIndex, chapter.startTimeOffset)
        } else {
            // Fallback: single track (M4B) - seek within current track
            Timber.i("Seeking to chapter '${chapter.title}' at position ${chapter.startTimeOffset}ms (single track)")
            player?.seekTo(chapter.startTimeOffset)
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
