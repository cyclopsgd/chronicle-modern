# Opus - Audiobook Player (Development Guide)

> **App Name**: Opus - Audiobook Player
> **Package**: `local.oss.chronicle` (legacy, may change)
> **Branding**: See `docs/Logos/` for SVG assets

> See `DESIGN_SPEC.md` for full design specification, UI/UX research (Audible/Prologue analysis), feature tiers, and architecture rationale.
>
> **IMPORTANT**: See `todo.md` for current task tracking and `MIGRATION_CHANGELOG.md` for migration history.

---

## Brand Identity

| Element | Value |
|---------|-------|
| **Name** | Opus - Audiobook Player |
| **Primary** | Amber `#FFAB40` |
| **Secondary** | Soft Green `#81C784` |
| **Background** | `#121212` |
| **Surface** | `#1E1E1E` |
| **Text** | `#FAFAFA` |

**Theme files**:
- Compose: `app/src/main/java/local/oss/chronicle/ui/theme/OpusTheme.kt`
- XML: `app/src/main/res/values/colors.xml`, `styles.xml`

---

## ✅ HILT MIGRATION COMPLETE ✅

**Phase 1.2: Hilt Migration** - COMPLETE

**Branch:** `feature/hilt-2.54-migration`

---

### Session 2026-02-01: Final Migration Fixes

**✅ All Issues Resolved:**

1. **Unqualified Context Injections** - Fixed with `@ApplicationContext`
   - `ChronicleBillingManager.kt` - added `@ApplicationContext`
   - `NotificationBuilder.kt` - added `@ApplicationContext`
   - `AudiobookMediaSessionCallback.kt` - added `@ApplicationContext`
   - `CachedFileManager.kt` - added `@ApplicationContext`

2. **FragmentManager & AppCompatActivity Injection**
   - **Problem**: Navigator needed FragmentManager and Activity at ActivityComponent scope
   - **Solution**: Added providers in `ActivityModule`:
     - `provideAppCompatActivity()` - casts ActivityContext to AppCompatActivity
     - `provideFragmentManager()` - gets supportFragmentManager from activity

3. **CoroutineScope for Activity Components**
   - **Problem**: `SimpleProgressUpdater` needed CoroutineScope in ActivityComponent
   - **Solution**: Added `provideActivityCoroutineScope()` using `activity.lifecycleScope`

4. **ProgressUpdater Binding**
   - Added `provideProgressUpdater()` in ActivityModule for fragment/activity use

**Build Status:**
- ✅ Kotlin compilation: SUCCESS
- ✅ KSP processing: SUCCESS
- ✅ Hilt annotation processing: SUCCESS
- ✅ Full APK build: SUCCESS

---

### Migration Best Practices Learned

#### 1. Service Interface Pattern (Hilt Services)
**Problem**: Hilt services can't be injected as dependencies (they're entry points, not graph participants)

**Solution**: Inject `android.app.Service` base class and cast:
```kotlin
class SomeCallback @Inject constructor(
    service: android.app.Service,  // Hilt provides this
    // ... other deps
) {
    // Cast to interfaces the service implements
    private val serviceController = service as ServiceController
    private val foregroundController = service as ForegroundServiceController
}
```

**Why this works**: Hilt provides `Service` in `ServiceComponent`, allowing consumers to get the service instance and access its interface implementations.

#### 2. Kotlin/Java Variance with @JvmSuppressWildcards
**Problem**: `List<File>` in Kotlin compiles to `List<? extends File>` in Java bytecode, causing type mismatches

**Solution**: Use `@JvmSuppressWildcards` on both provider and consumers:
```kotlin
// Provider
@Provides
@Named("qualifier")
fun provideList(): @JvmSuppressWildcards List<File> = ...

// Consumer
class SomeClass @Inject constructor(
    @Named("qualifier") val list: @JvmSuppressWildcards List<File>
)
```

#### 3. @Named Qualifiers for Generic Types
**Problem**: Multiple providers of same generic type (e.g., `List<File>`)

**Solution**: Always use `@Named` qualifiers for non-unique types:
```kotlin
@Provides
@Named("externalDeviceDirs")
fun provideExternalDirs(): List<File> = ...
```

**Don't forget**: EntryPoints also need @Named if they expose qualified dependencies

#### 4. Activity-Scoped Dependencies
**Problem**: Some classes need activity-scoped objects (FragmentManager, Activity, CoroutineScope)

**Solution**: Provide them in ActivityModule:
```kotlin
@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {
    @Provides
    @ActivityScoped
    fun provideAppCompatActivity(@ActivityContext context: Context): AppCompatActivity =
        context as AppCompatActivity

    @Provides
    @ActivityScoped
    fun provideFragmentManager(activity: AppCompatActivity): FragmentManager =
        activity.supportFragmentManager

    @Provides
    @ActivityScoped
    fun provideActivityCoroutineScope(activity: AppCompatActivity): CoroutineScope =
        activity.lifecycleScope
}
```

1. **Merge feature branch** - PR to main
2. **Test app on device** - Verify all features work with new DI
3. **Continue to Phase 1.3** - Core Reliability Fixes
4. **Phase 2** - Now-Playing Screen Redesign (Compose)

---

### Files Modified in Final Session
- `gradle.properties` - Added `org.gradle.java.home` for JDK 17
- `app/src/main/java/local/oss/chronicle/application/ChronicleBillingManager.kt` - `@ApplicationContext`
- `app/src/main/java/local/oss/chronicle/features/player/NotificationBuilder.kt` - `@ApplicationContext`
- `app/src/main/java/local/oss/chronicle/features/player/AudiobookMediaSessionCallback.kt` - `@ApplicationContext`
- `app/src/main/java/local/oss/chronicle/data/sources/plex/CachedFileManager.kt` - `@ApplicationContext`
- `app/src/main/java/local/oss/chronicle/injection/modules/ActivityModule.kt` - Added activity-scoped providers
- `app/src/main/java/local/oss/chronicle/navigation/Navigator.kt` - Now receives dependencies from ActivityModule

**Environment:**
- Kotlin: 2.1.0, KSP: 2.1.0-1.0.29, Hilt: 2.54, Dagger: 2.54, AGP: 8.13.2, Java: JDK 17.0.17+10

---

## Project Context

You are modernising Chronicle Epilogue (https://github.com/fabiogermann/chronicle), an open-source Plex audiobook player for Android. The goal is to create a premium audiobook experience matching Audible's polish and Prologue's Plex integration.

The codebase is 97% Kotlin, GPL-3.0 licensed, currently uses Dagger and ExoPlayer. We're modernising to Hilt, Media3, and progressively adopting Jetpack Compose.

## Your Mission

Transform this functional but dated app into a polished, maintainable audiobook client. Work autonomously through the phases below, committing logical chunks as you go.

---

## Phase 1: Foundation & Architecture (Do First)

### 1.1 Project Structure
Create a multi-module architecture:
```
:app                    - Application shell, navigation, DI setup
:core:common            - Shared utilities, extensions, base classes
:core:network           - Plex API client, authentication
:core:database          - Room database, DAOs, entities
:core:media             - Media3 playback, audio processing
:core:sync              - Progress synchronisation logic
:feature:library        - Library browsing, filtering, search
:feature:player         - Now-playing screen, controls, car mode
:feature:downloads      - Download management
:feature:settings       - App settings
```

### 1.2 Dependency Updates
- Migrate ExoPlayer to AndroidX Media3 (media3-exoplayer, media3-session, media3-ui)
- Migrate Dagger to Hilt
- Update to compileSdk 35, targetSdk 35, minSdk 26
- Add Jetpack Compose dependencies alongside existing Views
- Create libs.versions.toml for version catalog

### 1.3 Core Reliability Fixes
- Implement robust playback position persistence (save every 5 seconds + on pause/stop)
- Fix offline/online transition (don't crash when connectivity changes)
- Implement proper error handling with user-friendly messages
- Add smart rewind on resume (5-20 seconds based on pause duration)

---

## Phase 2: Now-Playing Screen Redesign

### 2.1 Layout (Implement in Compose)
Create a new Compose-based now-playing screen with:
- Cover art taking 40-50% of vertical space
- Blurred/dimmed cover art as background with gradient fade
- Book title (20-24sp), author (16-18sp), narrator (14sp) hierarchy
- Chapter name with tap to open chapter list
- Dual time display: elapsed on left, remaining on right
- Scrubber with chapter markers visible
- Central play/pause button (56dp touch target minimum)
- 30-second skip back/forward buttons flanking play
- Bottom row: Speed (shows current like "1.2x"), Sleep Timer, Bookmark

### 2.2 Playback Controls
- Speed selector: 0.5x to 3.0x in 0.1x increments, pitch correction enabled
- Per-book speed memory (store in Room)
- Sleep timer presets: 5, 10, 15, 30, 45, 60 min + "End of chapter" + custom
- Sleep timer shows countdown when active
- Volume fade-out over 15 seconds before sleep stop
- Shake-to-extend sleep timer (use accelerometer, configurable sensitivity)

### 2.3 Chapter Navigation
- Extract M4B chapter metadata (Plex often ignores this)
- Chapter list as bottom sheet
- Tap chapter to jump
- Previous/next chapter buttons (as secondary controls, not primary)

---

## Phase 3: Car Mode

### 3.1 Dedicated Car Mode Screen (Compose)
Completely different interface - not just bigger buttons:
- Pure black background, maximum contrast
- Giant play/pause button (120dp+)
- Large 30-second rewind button
- Large bookmark button
- NO text to read while driving
- NO settings access
- Minimal: just cover art thumbnail, play, rewind, bookmark

### 3.2 Auto-Activation
- Detect Bluetooth connection to known car audio devices
- Auto-enter car mode on connection
- Auto-exit on disconnect
- Manual toggle always available
- Disable sleep timer while in car mode

---

## Phase 4: Library Screen

### 4.1 Grid View (Compose)
- Cover art grid as default view
- Progress indicator as thin bar at bottom of each cover
- Downloaded badge (checkmark) overlay
- Pull-to-refresh
- Floating search button

### 4.2 Filtering & Sorting
Filters:
- Not Started / In Progress / Finished / Downloaded
- By Collection (from Plex collections for series)
- By Author

Sorting:
- Title / Author / Date Added / Recently Played

### 4.3 Book Detail Screen
- Full-bleed cover art with gradient fade
- Prominent Play/Resume button
- Tabs: Chapters | Bookmarks | Details
- Download button with size estimate
- Show series info if part of collection

---

## Phase 5: Downloads & Offline

### 5.1 Download Management
- Download button on book detail
- Circular progress during download
- Queue multiple downloads
- Background processing via WorkManager
- Two quality options: Standard / High
- SD card storage support

### 5.2 Storage Management
- Settings screen showing space used
- Delete individual downloads
- Clear all downloads option
- Visual distinction between downloaded and streaming books

---

## Phase 6: Progress Sync

### 6.1 Plex Server Sync
- Report playback progress back to Plex server
- Read progress from Plex on library refresh
- Handle conflicts with "most recent wins"

### 6.2 Local Persistence
- Room database for all library and progress data
- Offline-first: show cached data immediately, update from network
- Progress survives app uninstall/reinstall via backup

---

## Technical Requirements

### State Management Pattern
```kotlin
// ViewModel exposes StateFlow
data class NowPlayingState(
    val book: Book?,
    val playbackState: PlaybackState,
    val position: Long,
    val duration: Long,
    val chapter: Chapter?,
    val sleepTimer: SleepTimerState,
    val speed: Float,
    val isDownloaded: Boolean
)

// UI events as sealed class
sealed class NowPlayingEvent {
    object PlayPause : NowPlayingEvent()
    object SkipForward : NowPlayingEvent()
    object SkipBack : NowPlayingEvent()
    data class SeekTo(val position: Long) : NowPlayingEvent()
    data class SetSpeed(val speed: Float) : NowPlayingEvent()
    data class SetSleepTimer(val minutes: Int) : NowPlayingEvent()
    object AddBookmark : NowPlayingEvent()
}
```

### Media3 Setup
```kotlin
// MediaSessionService for background playback
@AndroidEntryPoint
class AudiobookPlaybackService : MediaSessionService() {
    @Inject lateinit var player: ExoPlayer
    
    private var mediaSession: MediaSession? = null
    
    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(AudiobookSessionCallback())
            .build()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
}
```

### Hilt Modules
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
```

### Compose Theme
```kotlin
// Dark theme as default, accent from cover art
@Composable
fun AudiobookTheme(
    dominantColor: Color = Color(0xFF1A1A2E),
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = dominantColor,
        surface = Color(0xFF0F0F1A),
        background = Color(0xFF0F0F1A),
        onSurface = Color.White,
        onBackground = Color.White
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

---

## UI/UX Principles

1. **Cover art dominates** - This creates the premium feel
2. **Dark theme default** - Easier on eyes during long listening sessions
3. **Minimal chrome** - Content over interface
4. **One-tap common actions** - Play, bookmark, sleep timer always accessible
5. **Progressive disclosure** - Advanced settings hidden until needed
6. **Reliability over features** - Better to do less that works perfectly

---

## File Naming Conventions

- Compose screens: `*Screen.kt` (e.g., `NowPlayingScreen.kt`)
- ViewModels: `*ViewModel.kt`
- Use cases: `*UseCase.kt`
- Repositories: `*Repository.kt` (interface) + `*RepositoryImpl.kt`
- Room entities: `*Entity.kt`
- API models: `*Dto.kt`
- UI state: `*State.kt`
- UI events: `*Event.kt`

---

## Commit Strategy

Make logical commits as you complete each subsection:
- "feat: migrate to multi-module architecture"
- "feat: implement Media3 playback service"
- "feat: add Compose now-playing screen"
- "fix: robust position persistence"
- etc.

---

## What NOT to Change

- Keep the app ID as `local.oss.chronicle` for now
- Don't remove existing features that work
- Don't break Plex authentication flow (it works, just modernise it)
- Keep GPL-3.0 license

---

## Start Here

Begin with Phase 1.1 (multi-module setup) and 1.2 (dependency updates). Get the project building with the new structure before touching features. Then proceed through phases in order.

Ask clarifying questions if needed, otherwise proceed autonomously.