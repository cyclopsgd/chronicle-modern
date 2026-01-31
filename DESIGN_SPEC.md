# Plex Audiobook Client for Android: Design Specification

The Android Plex audiobook ecosystem has a critical gap. While iOS users have Prologue—a polished, purpose-built client with **4.9 stars** and enthusiastic user loyalty—Android users are stuck with Chronicle, which hasn't received meaningful updates since 2022 and suffers from fundamental reliability issues. This specification defines a premium Android audiobook client that combines Audible's refined UI/UX, Prologue's Plex-native intelligence, and Smart Audiobook Player's power-user depth, built on a modern, maintainable architecture.

---

## The core problem Plex fails to solve

Plex's native apps treat audiobooks as music, creating a fundamentally broken experience. A **2012 forum thread** requesting resume functionality has accumulated 1,146+ replies and 48,000+ views—still unresolved after 14 years. The specific failures that drive users to third-party clients:

- **Position tracking unreliable**: Progress resets when switching to other media or when chapter completion exceeds 80-90%
- **No chapter support**: M4B files appear as single multi-hour tracks with no navigation
- **No variable playback speed**: Users commonly listen at 1.2x-2x
- **Skip controls broken**: Buttons jump to book start/end instead of 15-second increments
- **No sleep timer**: Essential bedtime listening feature completely absent

Prologue solves every one of these problems for iOS. This app will do the same for Android.

---

## Feature set: Core tier

The core tier represents the minimum viable product that solves the fundamental Plex audiobook problems and matches Prologue's value proposition.

### Plex integration and authentication

**Authentication** uses Plex's PIN-based OAuth flow: generate a time-limited PIN via the API, redirect the user to `app.plex.tv/auth`, then poll for token completion. Store the `X-Plex-Token` securely using Android's EncryptedSharedPreferences. Support Plex Home user switching with quick account selection.

**Server discovery** should automatically detect all available Plex servers and shared libraries after authentication. Display server health status and connection type (local/remote). Handle multiple servers gracefully—users may have personal and family servers.

**Library handling** must work with Plex's audiobook library configuration: music libraries with "Basic" agent type, "Store Track Progress" enabled, and the Audiobooks metadata scraper. Parse Plex collections for series grouping.

### Now-playing interface

Follow Audible's visual hierarchy: **cover art dominates 40-50% of vertical space**. This single design decision creates the premium, immersive feel users expect. Below the artwork:

- Book title, author, and narrator in clear typographic hierarchy
- Chapter name with elapsed/remaining time flanking it
- Overall book progress as percentage and hours remaining
- Scrubber with chapter markers visible on the timeline

**Playback controls** center around a prominent play/pause button with **30-second skip** buttons (not chapter skip) as the default. Speed indicator shows current rate (e.g., "1.2x") and opens the speed selector on tap. Sleep timer icon always visible with active state indication.

### Chapter navigation

This is where Plex fails completely. The app must:

- **Extract M4B chapter metadata** that Plex ignores—display actual chapter titles, not just file parts
- Provide a chapters tab showing all chapters with timestamps
- Enable tap-to-jump navigation to any chapter
- Support previous/next chapter controls in addition to skip buttons

### Playback speed control

Range of **0.5x to 3.0x** with 0.1x increments. Pitch correction enabled by default to maintain natural voice quality. **Per-book speed memory** is essential—fiction readers often prefer 1.0x while non-fiction listeners go faster.

### Sleep timer

Implement Audible's model with presets (5, 10, 15, 30, 45, 60 minutes) plus:

- **"End of chapter"** option—the most-requested sleep timer feature
- Custom duration up to 4 hours
- Visual countdown always visible when active
- Gentle volume fade-out over 15 seconds before stopping
- **Shake-to-extend**: Motion detection resets the timer (configurable sensitivity)

### Progress synchronization

**Dual-sync architecture** following Prologue's approach:

1. **Plex server sync**: Report playback status back to Plex so progress appears across all Plex clients
2. **Cloud sync** via Google account: Sync between the user's Android devices, independent of Plex

Use "most recent timestamp wins" for conflict resolution. Implement **smart rewind**: when resuming a book not played recently, automatically rewind 5-20 seconds based on pause duration.

### Offline downloads

Core requirement for commuter use. Implement:

- Download button on book detail with circular progress indicator
- Queue multiple downloads with background processing via WorkManager
- Two quality tiers: Standard (~28MB/hour) and High quality
- Storage management showing space used by downloads
- SD card support for users with limited internal storage
- Clear visual distinction between downloaded and streaming-only books

### Library organization

**Grid view as default** with cover art prominence. Filter options:

- Listening status: Not Started, In Progress, Downloaded, Finished  
- Collections: Pull from Plex collections for series grouping
- Authors: Group by artist metadata

Sorting by title, author, date added, and recently played. Search across the full library with predictive results.

---

## Feature set: Optional/advanced tier

These features differentiate the app for power users without cluttering the core experience. Expose through a clearly labeled "Advanced" settings section.

### Audio processing

From Smart Audiobook Player's most-praised features:

- **Volume boost**: Amplify quiet recordings beyond system maximum—invaluable for older audiobooks or noisy environments
- **Skip silence**: Remove pauses longer than 0.5 seconds (configurable threshold 0.1-0.9s)
- **5-band equalizer** with presets for voice clarity
- Noise reduction for poor-quality recordings

### Playback customization

- **Finer speed increments**: 0.05x steps for users who need precise control
- **Customizable skip intervals**: Allow 10s/30s/60s configuration
- **Auto-rewind based on pause duration**: Brief pause = 3 seconds rewind; extended pause = up to 30 seconds
- **Force stop**: Auto-pause after configurable continuous playback (1-4 hours) to prevent overnight battery drain

### Bookmarks and notes

- One-tap bookmark creation (like Audible's +Clip button) capturing timestamp
- Optional text notes attached to bookmarks  
- Adjustable clip length (5 seconds to 2 minutes)
- Organized bookmark library by book and chapter
- Export bookmarks as text file

### Headset and hardware controls

Smart Audiobook Player users specifically praise:

- Customizable Bluetooth headset button mapping
- Long-press actions (bookmark, speed change, chapter skip)
- Single/double/triple press differentiation
- Rotate-to-pause: Face phone down to pause, up to resume

### Integrations

- **Android Auto**: Simplified large-button interface with sleep timer auto-disable while connected
- **Chromecast**: Stream to home speakers
- **Tasker integration**: Play/pause/skip intents for automation users
- **Wear OS**: Companion app with basic playback controls

### Statistics and tracking

- Listening history with timestamps
- Total time listened (daily/weekly/monthly/all-time)
- Books completed counter
- Listening streaks for gamification

---

## UI/UX guidelines

### Visual design language

The premium feel comes from **restraint and content focus**, not feature density. Follow Audible's key principles:

**Color strategy**: Use a dark theme as default. Extract dominant color from cover art for subtle accent theming—not aggressive color extraction, but gentle tinting of progress bars and selected states. Primary brand accent for interactive elements.

**Typography**: Sans-serif font family with clear hierarchy. Title at 20-24sp, author at 16-18sp, metadata at 14sp. Generous line spacing for readability during casual glances.

**Whitespace**: Resist the urge to fill space. Padding around cover art (24-32dp minimum) creates breathing room. Section separators use space, not lines.

**Animation**: Smooth 300ms transitions between screens. Playback progress animates fluidly. Sleep timer countdown pulses subtly. Car mode transitions instantly without animation to minimize distraction.

### Screen-by-screen design

**Library screen**: Grid of covers with subtle drop shadows. Progress indicator as thin bar at bottom of each cover. Downloaded indicator (checkmark badge). Pull-to-refresh with branded loading animation. Floating action button for search.

**Book detail screen**: Full-bleed cover art with gradient fade to content area. Prominent Play/Resume button. Tabs for Chapters, Bookmarks, and Details. Download button with storage estimate.

**Now-playing screen**: Immersive cover art experience. Minimize chrome. Bottom sheet pattern for chapter list access. Swipe down to minimize to mini-player. Lock screen controls via MediaSession.

**Car mode**: Dramatically different interface—not just bigger buttons, but **fundamentally simplified**. Black background, high contrast. Only: giant play/pause, 30-second rewind, bookmark button. No text to read. No settings to adjust. Activates automatically on Bluetooth connection to known car audio systems.

### Interaction patterns

**Bottom Line Up Front (BLUF) in UI**: Most important information always visible first. Current chapter and progress should never require scrolling or tapping.

**One-tap for common actions**: Play, bookmark, and sleep timer accessible without navigation.

**Progressive disclosure**: Advanced settings hidden behind "Show advanced" toggles. Speed control shows presets by default, with "Fine control" expansion for 0.05x increments.

**Forgiveness**: Playback history enables undo for accidental skips. Confirmation dialogs only for destructive actions (delete download, remove from library).

---

## Technical architecture recommendations

### Migration from Chronicle Epilogue baseline

Chronicle Epilogue provides a working starting point with **99.9% Kotlin codebase** and GPL-3.0 licensing. However, significant modernization is required for long-term maintainability.

### Module structure

Implement a multi-module architecture separating concerns:

```
:app                    - Application shell, navigation, DI setup
:core:common            - Shared utilities, extensions, base classes
:core:network           - Plex API client, authentication, network handling
:core:database          - Room database, DAOs, entities
:core:media             - Media3 playback, audio processing
:core:sync              - Progress synchronization logic
:feature:library        - Library browsing, filtering, search
:feature:player         - Now-playing screen, controls, car mode
:feature:downloads      - Download management, storage
:feature:settings       - App settings, advanced options
```

Module boundaries enforce separation of concerns and enable parallel development. Each feature module depends only on core modules, not on other features.

### Dependency injection with Hilt

Chronicle uses Dagger, which should migrate to **Hilt** for simplified Android-aware DI:

- `@HiltAndroidApp` on Application class
- `@AndroidEntryPoint` on Activities and Fragments
- `@HiltViewModel` for ViewModels with `@Inject constructor`
- Module organization: `NetworkModule`, `DatabaseModule`, `MediaModule`, `RepositoryModule`

Hilt reduces boilerplate significantly while maintaining Dagger's compile-time safety.

### Media playback with Media3

ExoPlayer is deprecated. Migrate to **AndroidX Media3**:

- `media3-exoplayer` for core playback
- `media3-session` for MediaSession integration (lock screen, Android Auto)
- `media3-ui` for built-in UI components
- Custom audio processor for volume boost and skip silence

Media3's `MediaSessionService` handles all background playback, notification controls, and external control surfaces through a single unified API.

### Jetpack Compose migration path

Chronicle uses XML Views. A full Compose rewrite is unrealistic initially. Recommended approach:

**Phase 1**: New features in Compose. Car mode interface, settings screens, and dialogs first. Use `ComposeView` in existing XML layouts.

**Phase 2**: Migrate leaf screens. Book detail screen, download manager—screens with minimal shared state.

**Phase 3**: Core screens. Library and now-playing last, as they're most complex and have the most existing code.

Compose enables:
- Declarative UI matching design specifications exactly
- Built-in animation APIs for polished transitions
- Easier state management with ViewModel integration
- Material 3 theming with dynamic color

### State management

Implement **unidirectional data flow** with:

- ViewModels expose `StateFlow<UiState>` for observable state
- UI events passed to ViewModel as sealed class intents
- Repository pattern for data access (network + local cache)
- Domain use cases for complex business logic

Example state hierarchy for now-playing:
```kotlin
data class NowPlayingState(
    val book: Book?,
    val playbackState: PlaybackState,
    val position: Long,
    val duration: Long,
    val chapter: Chapter?,
    val sleepTimer: SleepTimerState,
    val speed: Float,
    val isOffline: Boolean
)
```

### Offline-first architecture

Design for unreliable connectivity from the start:

- Room database as single source of truth for library data
- Repository returns Flow that emits cached data immediately, then updates from network
- Download metadata stored locally, tracks survive app restarts
- Conflict resolution timestamps on all mutable data
- WorkManager for reliable download completion even if app killed

### Network layer

Retrofit with OkHttp for Plex API communication:

- Interceptor adds required Plex headers (`X-Plex-Token`, `X-Plex-Client-Identifier`, `X-Plex-Product`)
- Token refresh interceptor handles authentication expiry
- Connection state monitoring with automatic retry
- Timeout configuration: 30 seconds for library fetch (large collections timeout at 15s)

### Testing strategy

Chronicle lacks comprehensive tests. Implement:

**Unit tests** (JUnit 5 + MockK):
- ViewModel logic with TestCoroutineDispatcher
- Repository data transformations
- Use case business logic
- Plex API response parsing

**Integration tests**:
- Room database operations
- Network layer with MockWebServer
- Sync conflict resolution

**UI tests** (Compose Testing):
- Critical user flows: authentication, playback start, sleep timer
- Accessibility verification
- Screen state rendering

Target **70% code coverage** for core modules, **40%** for feature modules as minimum thresholds.

### Build configuration

```kotlin
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26  // Android 8.0 - covers 95%+ of devices
        targetSdk = 35
    }
    buildFeatures {
        compose = true
        viewBinding = true  // During migration
    }
}
```

Version catalog (`libs.versions.toml`) for dependency management. Enable R8 full mode for release builds.

### CI/CD pipeline

GitHub Actions workflow:
- Build and lint on every PR
- Run unit tests with coverage report
- UI tests on Firebase Test Lab (API 26, 31, 34)
- Automatic release build on tag push
- Play Store internal track deployment

---

## Implementation priorities

### Phase 1: Core reliability (Weeks 1-8)

Fix Chronicle's fundamental issues first:

1. Migrate to Media3 and fix playback reliability
2. Implement robust position persistence
3. Fix offline/online transition handling
4. Update to SDK 35, fix Android 14/15 compatibility
5. Implement proper error handling and user feedback

**Success metric**: Zero "lost my place" or "app stopped playing" reports.

### Phase 2: UI modernization (Weeks 9-16)

1. Redesign now-playing screen with Audible-style cover prominence
2. Implement car mode with automatic activation
3. Add sleep timer with all preset options and shake-to-extend
4. Create library grid view with filters
5. Begin Compose migration with new screens

**Success metric**: Visual parity with Audible/Prologue aesthetic.

### Phase 3: Feature completion (Weeks 17-24)

1. Progress sync with Plex server and Google cloud
2. Download management with SD card support
3. Bookmarks with notes
4. Android Auto optimization
5. Advanced audio processing (boost, silence skip)

**Success metric**: Feature parity with Prologue core features.

### Phase 4: Power user features (Ongoing)

1. Wear OS companion
2. Statistics and tracking
3. Tasker integration
4. Chromecast support
5. Widgets

---

## Competitive positioning

This app fills a specific gap: **the premium Plex audiobook experience for Android**. It is not competing with:

- **Audible**: Users committed to the Amazon ecosystem
- **Smart Audiobook Player**: Users with local file collections
- **Audiobookshelf**: Users willing to migrate away from Plex

The target user already has a Plex audiobook library and wants an experience matching iOS Prologue. The app should:

- Feel as polished as Audible
- Integrate as seamlessly as Prologue  
- Offer power-user depth like Smart Audiobook Player (optionally)

One-time purchase pricing ($5-6, matching Prologue) with premium features like offline download. Free tier for streaming-only to build user base.

---

## Conclusion

The Android Plex audiobook market is underserved by a wide margin. Chronicle Epilogue provides a functional baseline but requires significant architectural modernization and UX investment to compete with iOS alternatives. By combining Audible's premium visual design, Prologue's Plex-native understanding, and Smart Audiobook Player's power-user features—built on modern Android architecture with Hilt, Media3, and progressive Compose adoption—this app can become the definitive Android Plex audiobook client.

The key insight from user research: reliability beats features. Users will tolerate a smaller feature set that works flawlessly over a feature-rich app that loses their place or stops playing. Phase 1 focuses entirely on this foundation before any visual or feature expansion.