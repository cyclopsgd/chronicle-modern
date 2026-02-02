# Media3 MediaSession Migration Plan

## Executive Summary

This document outlines the migration from legacy `android.support.v4.media` APIs to AndroidX Media3 MediaSession APIs. The app already uses Media3 ExoPlayer (1.5.0), but the MediaSession layer still uses the deprecated compat APIs.

## Current Architecture

### Legacy Components in Use
| Component | Package | Files |
|-----------|---------|-------|
| MediaBrowserServiceCompat | androidx.media | MediaPlayerService.kt |
| MediaSessionCompat | android.support.v4.media.session | MediaPlayerService.kt, ServiceModule.kt |
| MediaControllerCompat | android.support.v4.media.session | MediaServiceConnection.kt, ViewModels |
| MediaBrowserCompat | android.support.v4.media | MediaServiceConnection.kt |
| MediaMetadataCompat | android.support.v4.media | Multiple files |
| PlaybackStateCompat | android.support.v4.media.session | Multiple files |

### Files Requiring Migration (24 total)
1. **Core Service Layer** (Critical)
   - `MediaPlayerService.kt` - Main service
   - `AudiobookMediaSessionCallback.kt` - Session callback
   - `MediaServiceConnection.kt` - Client connection
   - `ServiceModule.kt` - DI providers

2. **Notification & Actions**
   - `NotificationBuilder.kt` - Media notifications
   - `OnMediaChangedCallback.kt` - State change handling
   - `CustomActions.kt` - Custom media actions

3. **Playback Components**
   - `PlaybackStateExt.kt` - State extensions
   - `MediaMetadataCompatExt.kt` - Metadata extensions
   - `MediaItemExt.kt` - Media item handling
   - `PlayerExt.kt` - Player extensions
   - `ProgressUpdater.kt` - Progress tracking
   - `SleepTimer.kt` - Sleep timer

4. **ViewModels** (6 files)
   - `MainActivityViewModel.kt`
   - `NowPlayingViewModel.kt`
   - `CurrentlyPlayingViewModel.kt`
   - `AudiobookDetailsViewModel.kt`
   - `CarModeViewModel.kt`
   - + others

5. **Data Models**
   - `MediaItemTrack.kt`
   - `Audiobook.kt`

## Target Architecture

### Media3 Components
| Legacy | Media3 Replacement |
|--------|-------------------|
| MediaBrowserServiceCompat | MediaLibraryService |
| MediaSessionCompat | MediaSession |
| MediaSessionCompat.Callback | MediaSession.Callback |
| MediaControllerCompat | MediaController |
| MediaBrowserCompat | MediaBrowser |
| MediaMetadataCompat | MediaMetadata |
| PlaybackStateCompat | Player state (direct) |

## Migration Strategy

### Phase 1: Dependencies & Setup
1. Ensure `media3-session` dependency is included
2. Add `@OptIn(UnstableApi::class)` annotations where needed
3. Create new Media3-based service class alongside existing

### Phase 2: Core Service Migration
1. Create `Media3PlayerService` extending `MediaLibraryService`
2. Implement `MediaLibraryService.MediaLibrarySession.Callback`
3. Setup ExoPlayer with MediaSession
4. Handle custom commands for audiobook features

### Phase 3: Client Migration
1. Create `Media3ServiceConnection` using `MediaController.Builder`
2. Migrate callback handling to `MediaController.Listener`
3. Replace `TransportControls` with direct `MediaController` calls

### Phase 4: ViewModel Updates
1. Update ViewModels to use new connection class
2. Replace `PlaybackStateCompat` observation with Player state
3. Update metadata handling

### Phase 5: Notification & Android Auto
1. Let Media3 handle notifications automatically
2. Verify Android Auto integration works
3. Test background playback

## Key Code Changes

### MediaPlayerService → MediaLibraryService

**Before:**
```kotlin
class MediaPlayerService : MediaBrowserServiceCompat() {
    @Inject lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        mediaSession.setCallback(mediaSessionCallback)
        sessionToken = mediaSession.sessionToken
    }
}
```

**After:**
```kotlin
class MediaPlayerService : MediaLibraryService() {
    private lateinit var mediaSession: MediaLibrarySession

    override fun onCreate() {
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
}
```

### MediaServiceConnection Migration

**Before:**
```kotlin
val mediaBrowser = MediaBrowserCompat(context, serviceComponent, callbacks, null)
val mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken)
```

**After:**
```kotlin
val sessionToken = SessionToken(context, ComponentName(context, MediaPlayerService::class.java))
val mediaController = MediaController.Builder(context, sessionToken).buildAsync().get()
```

### Custom Commands for Audiobook Features

```kotlin
// Define custom commands
val COMMAND_SET_SLEEP_TIMER = SessionCommand("SET_SLEEP_TIMER", Bundle.EMPTY)
val COMMAND_SKIP_TO_CHAPTER = SessionCommand("SKIP_TO_CHAPTER", Bundle.EMPTY)

// In callback
override fun onCustomCommand(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle
): ListenableFuture<SessionResult> {
    when (customCommand.customAction) {
        "SET_SLEEP_TIMER" -> handleSleepTimer(args)
        "SKIP_TO_CHAPTER" -> handleSkipToChapter(args)
    }
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
}
```

## Risk Mitigation

1. **Parallel Implementation**: Keep old service working while building new one
2. **Feature Flags**: Use build variants to test new implementation
3. **Incremental Testing**: Test each phase independently
4. **Rollback Plan**: Keep old code in separate package until verified

## Benefits of Migration

1. **Simplified Code**: Media3 handles notifications, state sync automatically
2. **Better Android Auto**: Native support for media browsing
3. **Chapter Support**: Potential for better chapter handling via MediaItem
4. **Future Proof**: Media3 is the actively maintained API
5. **Bug Fixes**: May resolve chapter skip issues

## Timeline Estimate

| Phase | Effort |
|-------|--------|
| Phase 1: Dependencies | 1 hour |
| Phase 2: Core Service | 4-6 hours |
| Phase 3: Client | 2-3 hours |
| Phase 4: ViewModels | 2-3 hours |
| Phase 5: Testing | 2-4 hours |

Total: ~15-20 hours of focused work

## References

- [Media3 Migration Guide](https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide)
- [MediaSession Guide](https://developer.android.com/guide/topics/media/media3/session)
- [UAMP Sample App](https://github.com/android/uamp)
