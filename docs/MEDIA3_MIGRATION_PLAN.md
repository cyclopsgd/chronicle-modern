# Media3 MediaSession Migration Plan

## Executive Summary

This document outlines the migration from legacy `android.support.v4.media` APIs to AndroidX Media3 MediaSession APIs. The app already uses Media3 ExoPlayer (1.5.0), but the MediaSession layer still uses the deprecated compat APIs.

## Migration Status: ✅ IMPLEMENTATION COMPLETE

**Branch:** `feat/media3-migration`
**Status:** Ready for testing and integration

### Completed Work

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1: Dependencies | ✅ Complete | media3-session 1.5.0 already configured |
| Phase 2: Core Service | ✅ Complete | Media3PlayerService created |
| Phase 3: Client Connection | ✅ Complete | Media3ServiceConnection created |
| Phase 4: ViewModel Prep | ✅ Complete | PlaybackController interface + implementation |
| Phase 5: Notifications | ✅ Complete | Handled automatically by Media3 |

### Files Created

```
app/src/main/java/local/oss/chronicle/features/player/media3/
├── Media3PlayerService.kt      # MediaLibraryService implementation
├── Media3ServiceConnection.kt  # Client-side MediaController wrapper
├── Media3PlaybackController.kt # PlaybackController interface implementation
└── Media3Module.kt             # Hilt DI module
```

### Files Modified

- `app/src/main/AndroidManifest.xml` - Added Media3 service registration
- `app/src/main/java/local/oss/chronicle/data/model/Audiobook.kt` - Added toMedia3MediaItem()
- `app/src/main/java/local/oss/chronicle/data/model/MediaItemTrack.kt` - Added toMedia3MediaItem()
- `app/src/main/java/local/oss/chronicle/features/player/PlaybackController.kt` - New interface

---

## Current Architecture

### Legacy Components (Still Active)
| Component | Package | Files |
|-----------|---------|-------|
| MediaBrowserServiceCompat | androidx.media | MediaPlayerService.kt |
| MediaSessionCompat | android.support.v4.media.session | MediaPlayerService.kt, ServiceModule.kt |
| MediaControllerCompat | android.support.v4.media.session | MediaServiceConnection.kt, ViewModels |
| MediaBrowserCompat | android.support.v4.media | MediaServiceConnection.kt |

### Media3 Components (New - Parallel)
| Component | Package | Files |
|-----------|---------|-------|
| MediaLibraryService | androidx.media3.session | Media3PlayerService.kt |
| MediaLibrarySession | androidx.media3.session | Media3PlayerService.kt |
| MediaController | androidx.media3.session | Media3ServiceConnection.kt |
| SessionToken | androidx.media3.session | Media3ServiceConnection.kt |

---

## Integration Guide

### Option 1: Gradual Migration (Recommended)

1. **Update ViewModels to use PlaybackController interface**
   ```kotlin
   // Before
   @Inject lateinit var mediaServiceConnection: MediaServiceConnection

   // After
   @Inject lateinit var playbackController: PlaybackController
   ```

2. **Add Hilt binding for Media3**
   ```kotlin
   @Module
   @InstallIn(ActivityRetainedComponent::class)
   abstract class PlaybackModule {
       @Binds
       abstract fun bindPlaybackController(
           impl: Media3PlaybackController
       ): PlaybackController
   }
   ```

3. **Update manifest to use Media3 service as primary**
   - Change which service handles `android.media.browse.MediaBrowserService`

### Option 2: Full Switch

1. Update `ActivityRetainedModule` to provide `Media3ServiceConnection` instead of `MediaServiceConnection`
2. Update all ViewModels to use the new connection class
3. Disable legacy service in manifest

---

## Testing Checklist

- [ ] App launches without crashes
- [ ] Playback starts from library
- [ ] Playback controls work (play/pause/seek)
- [ ] Chapter navigation works
- [ ] Sleep timer works
- [ ] Progress saves correctly
- [ ] Background playback works
- [ ] Notification controls work
- [ ] Android Auto browsing works
- [ ] Android Auto playback works
- [ ] Lock screen controls work
- [ ] Bluetooth media controls work

---

## Key Code Changes

### MediaPlayerService → MediaLibraryService

**Before (Legacy):**
```kotlin
class MediaPlayerService : MediaBrowserServiceCompat() {
    @Inject lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        mediaSession.setCallback(mediaSessionCallback)
        sessionToken = mediaSession.sessionToken
    }
}
```

**After (Media3):**
```kotlin
class Media3PlayerService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: ControllerInfo) = mediaSession
}
```

### MediaServiceConnection Migration

**Before (Legacy):**
```kotlin
val mediaBrowser = MediaBrowserCompat(context, serviceComponent, callbacks, null)
val mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken)
```

**After (Media3):**
```kotlin
val sessionToken = SessionToken(context, ComponentName(context, Media3PlayerService::class.java))
val mediaController = MediaController.Builder(context, sessionToken).buildAsync()
```

### Custom Commands for Audiobook Features

```kotlin
// Define custom commands
const val COMMAND_SET_SLEEP_TIMER = "SET_SLEEP_TIMER"
const val COMMAND_SEEK_TO_CHAPTER = "SEEK_TO_CHAPTER"

// In callback
override fun onCustomCommand(
    session: MediaSession,
    controller: ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle
): ListenableFuture<SessionResult> {
    when (customCommand.customAction) {
        COMMAND_SET_SLEEP_TIMER -> handleSleepTimer(args)
        COMMAND_SEEK_TO_CHAPTER -> handleSeekToChapter(args)
    }
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
}
```

---

## Benefits of Migration

1. **Simplified Code**: Media3 handles notifications, state sync automatically
2. **Better Android Auto**: Native support for media browsing via MediaLibrarySession
3. **Chapter Support**: Potential for better chapter handling via MediaItem
4. **Future Proof**: Media3 is the actively maintained API
5. **Bug Fixes**: May resolve chapter skip issues with modern APIs

---

## Rollback Plan

If issues are found:
1. Both services are registered in manifest and can coexist
2. Revert ViewModels to use legacy `MediaServiceConnection`
3. Disable Media3 service in manifest

---

## References

- [Media3 Migration Guide](https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide)
- [MediaSession Guide](https://developer.android.com/guide/topics/media/media3/session)
- [UAMP Sample App](https://github.com/android/uamp)
