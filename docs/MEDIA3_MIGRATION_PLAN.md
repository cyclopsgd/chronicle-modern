# Media3 Migration Plan

## Overview

Migrate from legacy `MediaBrowserServiceCompat` + `MediaSessionCompat` to Media3's `MediaLibraryService` + `MediaSession`. This will fix the chapter skip bug and modernize the playback architecture.

**Migration Guide Reference:** https://developer.android.com/media/media3/exoplayer/migration-guide

---

## Why Migrate?

### Current Issues
1. **Chapter skip not working** - `seekTo()` is async but position tracking doesn't wait for completion
2. **Race conditions** - Progress updater reads stale position after seeks
3. **Complex callback chains** - `MediaSessionConnector` + `PlaybackPreparer` + `QueueNavigator` pattern

### Benefits of Media3
1. **Proper async handling** - `ListenableFuture` for all async operations
2. **Simpler API** - `MediaSession.Callback` replaces multiple connectors
3. **Built-in notifications** - No more `PlayerNotificationManager`
4. **Better Android Auto** - `MediaLibraryService` is the modern standard
5. **Position tracking** - `Player.Listener.onPositionDiscontinuity()` for seek completion

---

## Phase 1: Preparation (Before Migration)

### 1.1 Update Dependencies
```kotlin
// gradle/libs.versions.toml - Already have these:
media3 = "1.5.0"
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer" }
media3-session = { group = "androidx.media3", name = "media3-session" }
```

### 1.2 Files to Migrate

| Current File | Action |
|--------------|--------|
| `MediaPlayerService.kt` | Replace with `Media3PlayerService.kt` |
| `MediaServiceConnection.kt` | Replace with `Media3ServiceConnection.kt` |
| `AudiobookMediaSessionCallback.kt` | Merge into `Media3PlayerService` |
| `PlayerNotificationManager` usage | Remove (built into MediaLibraryService) |
| `OnMediaChangedCallback.kt` | Update to use `Player.Listener` |

### 1.3 Existing Partial Work

The `feat/media3-migration` branch has partial implementation:
- `Media3PlayerService.kt` - Basic structure exists
- `Media3ServiceConnection.kt` - Client connection exists
- Issue: Playback doesn't start (needs debugging)

---

## Phase 2: Core Service Migration

### 2.1 Create Media3PlayerService

```kotlin
@AndroidEntryPoint
class Media3PlayerService : MediaLibraryService() {

    @Inject lateinit var trackListStateManager: TrackListStateManager
    @Inject lateinit var playbackStateController: PlaybackStateController

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player?.addListener(playerListener)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player!!, sessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: ControllerInfo) = mediaLibrarySession

    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            session: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Handle play requests - load tracks from Plex
            return Futures.immediateFuture(resolveMediaItems(mediaItems))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Android Auto browsing root
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // Android Auto browsing children
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: PositionInfo,
            newPosition: PositionInfo,
            reason: Int
        ) {
            // THIS IS KEY - called when seek completes
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                playbackStateController.updatePosition(
                    newPosition.mediaItemIndex,
                    newPosition.positionMs
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playbackStateController.updatePlaybackState(playbackState)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Track changed - update chapter info
        }
    }
}
```

### 2.2 Update AndroidManifest

```xml
<service
    android:name=".features.player.media3.Media3PlayerService"
    android:enabled="true"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService"/>
        <action android:name="android.media.browse.MediaBrowserService"/>
    </intent-filter>
</service>

<!-- Disable legacy service -->
<service
    android:name=".features.player.MediaPlayerService"
    android:enabled="false"
    ... />
```

---

## Phase 3: Client Connection Migration

### 3.1 Create Media3ServiceConnection

```kotlin
@Singleton
class Media3ServiceConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaController: MediaController? = null
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _playbackState = MutableStateFlow<PlaybackState?>(null)
    val playbackState: StateFlow<PlaybackState?> = _playbackState

    suspend fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, Media3PlayerService::class.java)
        )

        mediaController = MediaController.Builder(context, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
            .await()

        _isConnected.value = true
    }

    fun play() = mediaController?.play()
    fun pause() = mediaController?.pause()
    fun seekTo(positionMs: Long) = mediaController?.seekTo(positionMs)
    fun seekTo(mediaItemIndex: Int, positionMs: Long) =
        mediaController?.seekTo(mediaItemIndex, positionMs)

    private val controllerListener = object : MediaController.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            // Update UI state
        }
    }
}
```

---

## Phase 4: Fix Chapter Skip

### 4.1 Update PlayerExt.kt

```kotlin
fun Player.skipToNextChapter(
    chapters: List<Chapter>,
    currentChapter: Chapter,
    onSeekComplete: () -> Unit = {}
) {
    val currentIndex = chapters.indexOf(currentChapter)
    val nextIndex = currentIndex + 1

    if (nextIndex < chapters.size) {
        val nextChapter = chapters[nextIndex]

        // Media3: seekTo returns immediately, but onPositionDiscontinuity
        // in the Player.Listener will be called when seek completes
        seekTo(nextChapter.trackIndex, nextChapter.startTimeOffset)

        // Don't update progress here - let onPositionDiscontinuity handle it
    }
}
```

### 4.2 Key Insight

The chapter skip bug exists because:
1. `seekTo()` is async
2. We immediately read `currentPosition` which hasn't updated yet
3. Progress updater overwrites the seek target

**Fix:** Use `Player.Listener.onPositionDiscontinuity()` to know when seek completes:
```kotlin
override fun onPositionDiscontinuity(
    oldPosition: PositionInfo,
    newPosition: PositionInfo,
    reason: Int
) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
        // NOW the position is correct
        val actualPosition = newPosition.positionMs
        playbackStateController.updatePosition(actualPosition)
    }
}
```

---

## Phase 5: Remove Legacy Code

### 5.1 Files to Delete
- `MediaPlayerService.kt` (after migration verified)
- `MediaServiceConnection.kt` (after migration verified)
- `AudiobookMediaSessionCallback.kt` (merged into service)
- `PlayerNotificationManager` setup code

### 5.2 Dependencies to Remove
```kotlin
// Can remove after migration:
// - androidx.media:media (MediaBrowserServiceCompat)
// - com.google.android.exoplayer2:extension-mediasession
```

---

## Migration Checklist

### Preparation
- [ ] Create new branch `feat/media3-migration-v2`
- [ ] Review existing `feat/media3-migration` for reusable code
- [ ] Add any missing Media3 dependencies

### Service Migration
- [ ] Create/update `Media3PlayerService.kt`
- [ ] Implement `MediaLibrarySession.Callback`
- [ ] Add `Player.Listener` for position tracking
- [ ] Update `AndroidManifest.xml`

### Client Migration
- [ ] Create/update `Media3ServiceConnection.kt`
- [ ] Update all ViewModels to use new connection
- [ ] Update `NowPlayingViewModel` playback controls
- [ ] Update `CarModeViewModel` playback controls

### Chapter Skip Fix
- [ ] Update `PlayerExt.kt` skip functions
- [ ] Remove immediate progress updates after seeks
- [ ] Verify `onPositionDiscontinuity` fires correctly
- [ ] Test skip forward/backward with M4B files

### Android Auto
- [ ] Implement `onGetLibraryRoot()`
- [ ] Implement `onGetChildren()`
- [ ] Test browsing in Android Auto
- [ ] Verify playback from Android Auto

### Cleanup
- [ ] Remove legacy service code
- [ ] Remove unused dependencies
- [ ] Update CLAUDE.md

### Testing
- [ ] Basic playback (play/pause/seek)
- [ ] Chapter navigation (skip forward/back)
- [ ] Notification controls
- [ ] Android Auto browsing and playback
- [ ] Background playback
- [ ] Audio focus handling
- [ ] Bluetooth controls

---

## Rollback Plan

If issues arise:
1. In `AndroidManifest.xml`:
   - Set `Media3PlayerService` android:enabled="false"
   - Set `MediaPlayerService` android:enabled="true"
2. Revert DI module to provide legacy `MediaServiceConnection`

---

## References

- [Media3 Migration Guide](https://developer.android.com/media/media3/exoplayer/migration-guide)
- [MediaLibraryService docs](https://developer.android.com/reference/androidx/media3/session/MediaLibraryService)
- [Player.Listener](https://developer.android.com/reference/androidx/media3/common/Player.Listener)
- [ExoPlayer to Media3 Mappings](https://developer.android.com/reference/androidx/media3/packages)
