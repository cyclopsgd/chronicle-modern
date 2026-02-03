# Opus - Audiobook Player (Development Guide)

> **App Name**: Opus - Audiobook Player
> **Package**: `local.oss.chronicle` (legacy package path, class names use Opus)
> **Branding**: See `docs/Logos/` for SVG assets

> See `DESIGN_SPEC.md` for full design specification and `todo.md` for current task tracking.

---

## 🚨 CURRENT WORK IN PROGRESS (2026-02-03)

**If user says "continue", launch the 3 parallel agents described below.**

### ✅ Completed Recently
- v0.60.25 release successful (APK on GitHub Releases)
- Version auto-bumped to 0.60.26-SNAPSHOT
- All 208 tests passing
- All CI workflows fixed (JDK path issue resolved)
- Compose UI migrations done: Settings, Collections, Book Details screens

### 🎯 Final Completion Plan

Full plan documented in `docs/COMPLETION_PLAN.md`. Summary:

**BATCH 1 - Launch 3 agents in parallel:**

| Agent | Task | Key Files |
|-------|------|-----------|
| **Agent 1: Chapter Bugs** | Fix chapter click starting at 0:00, fix skip chapter buttons | `BookDetailsScreen.kt`, `PlaybackStateController.kt`, `MediaPlayerService.kt` |
| **Agent 2: Performance** | Fix release build lag - add Baseline Profiles, audit Compose stability | All `*Screen.kt` files, `build.gradle.kts` |
| **Agent 3: Mini Player** | Convert mini player XML to Compose | `activity_main.xml`, `MainActivity.kt`, new `MiniPlayer.kt` |

**BATCH 2 - After Batch 1:**
- Agent 4: Complete Media3 migration (needs Agent 1 findings)

**BATCH 3 - Final:**
- Agent 5: Release prep (signing, tests, multi-device testing)

### 🐛 Known Bugs to Fix

1. **Chapter click starts at 0:00** - Clicking a chapter in a book you're not listening to starts at beginning instead of chapter position
2. **Skip chapter buttons don't work** - Forward/back chapter navigation broken
3. **Release build laggy** - Missing Baseline Profiles, possible Compose stability issues

### 📋 Agent Launch Commands

When user says "continue", launch these 3 agents in parallel:

**Agent 1 Prompt:**
```
Investigate and fix chapter playback bugs in Opus audiobook app.

Bug 1: Clicking a chapter in BookDetailsScreen for a book not currently playing starts playback at 0:00 instead of the chapter's position.

Bug 2: Skip chapter forward/back buttons don't navigate between chapters.

Files to investigate:
- features/bookdetails/compose/BookDetailsScreen.kt - chapter click handler
- features/bookdetails/compose/ComposeBookDetailsViewModel.kt - playChapter logic
- features/player/PlaybackStateController.kt - seek and chapter logic
- features/player/MediaPlayerService.kt - media item preparation
- features/player/AudiobookMediaSessionCallback.kt - playFromMediaId handling
- data/model/Chapter.kt - chapter data model

Tasks:
1. Trace code path from chapter click to playback start
2. Find where chapter.startTimeOffset gets lost
3. Fix the offset passing mechanism
4. Test with M4B (single file) and multi-file audiobooks
5. Document offset semantics (track-relative vs book-relative)

Do NOT start agents or create new files without reading existing code first.
```

**Agent 2 Prompt:**
```
Optimize Opus audiobook app performance for release builds.

Problem: Release APK feels laggy compared to debug.

Tasks:
1. Add Baseline Profiles:
   - Add profileinstaller dependency
   - Create baseline profile generation
   - Focus on: app startup, library scrolling, player controls

2. Audit Compose stability:
   - Enable Compose compiler reports
   - Check all *Screen.kt files for unstable parameters
   - Add @Immutable/@Stable annotations where needed

3. Check image loading:
   - Verify Coil disk cache config
   - Add placeholders to prevent layout jumps

4. Check database:
   - Ensure no main thread queries
   - Review DAO queries for missing indices

Key files:
- app/build.gradle.kts
- features/home/compose/HomeScreen.kt
- features/library/compose/LibraryScreen.kt
- features/nowplaying/NowPlayingScreen.kt

Do NOT modify playback logic - focus only on performance.
```

**Agent 3 Prompt:**
```
Convert the XML mini player to Compose in Opus audiobook app.

Current implementation:
- activity_main.xml contains mini player layout
- MainActivity.kt manages mini player state
- CurrentlyPlayingBindingAdapters.kt handles animations

Create new file:
- ui/components/MiniPlayer.kt

Design spec:
- Height: 72dp
- Background: Surface (#1E1E1E)
- Progress bar at top (2dp amber)
- Layout: [Cover 56x56] [Title + Chapter] [Play/Pause]
- Tap expands to full player
- AnimatedVisibility for show/hide

Tasks:
1. Create MiniPlayer composable
2. Replace mini player XML in activity_main.xml with ComposeView
3. Connect to existing playback state in MainActivity
4. Add slide up/down animations
5. Keep BottomNavigationView as XML (don't change nav bar)

Follow existing patterns from NowPlayingScreen.kt for styling.
```

---

## Current State (2026-02-02)

### ✅ Completed Phases
- **Phase 1.1**: Multi-module architecture
- **Phase 1.2**: Hilt 2.54 migration (complete)
- **Phase 1.3**: Core reliability fixes (smart rewind, error recovery, position persistence)
- **Phase 2**: Now-Playing Screen (complete)
- **Phase 3**: Library Screen (complete) - includes list view toggle
- **Phase 6**: Car Mode (complete)
- **UI Modernization**: Compose nav bar, mini player, settings, collections, book details

### 🐛 Known Bugs (Needs Investigation)
Two critical playback bugs were investigated but not yet fixed:

1. **Skip chapter navigation not working**
   - Skip forward/back buttons don't navigate between chapters
   - Root cause: Chapter offset semantics mismatch
   - `Chapter.startTimeOffset` is track-relative for M4B chapters from Plex
   - Files touched: `PlaybackState.kt`, `PlayerExt.kt`, `MediaItemTrack.kt`
   - May require Media3 migration for proper fix

2. **Play from library list doesn't load chapter metadata**
   - Books started from library grid show no chapters in the chapter list
   - `syncAudiobook` not getting called with proper track list
   - File touched: `AudiobookMediaSessionCallback.handlePlayBookWithNoTracks()`
   - Chapter data from Plex API not populating into PlaybackState

### 🚧 In Progress
- **Home Screen**: New Compose home with categories (merged from feat/home-screen)
- **Media3 Migration**: Modernizing playback service (`feat/media3-migration`)

### 📋 Backlog (see `todo.md`)
- Phase 4: Downloads & Offline
- Phase 5: Progress Sync
- Phase 7: Stats & Polish

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

## Key Files to Know

### Now Playing Screen (Compose)
- `NowPlayingScreen.kt` - Main Compose UI with chapter & sleep timer bottom sheets
- `NowPlayingViewModel.kt` - State management, playback controls
- `NowPlayingUiState` - Data class in NowPlayingScreen.kt
- `CurrentlyPlayingFragment.kt` - Hosts ComposeView, bridges to existing navigation

### Navigation & Layout
- `activity_main.xml` - Main layout with bottom nav and mini player
- `CurrentlyPlayingBindingAdapters.kt` - Bottom sheet state animations
- `MainActivityViewModel.kt` - Controls bottom sheet state (COLLAPSED/EXPANDED/HIDDEN)

### Styling
- `dimens.xml` - Heights: nav bar 56dp, mini player 72dp
- `styles.xml` - Widget.BottomNavigationView style
- `colors.xml` - All brand colors
- `bottom_nav_item_color.xml` - Nav item color selector

### DI (Hilt)
- `ActivityRetainedModule.kt` - MediaServiceConnection, LocalBroadcastManager
- `ActivityModule.kt` - Activity-scoped providers
- `ServiceModule.kt` - Service-scoped providers

### Playback
- `MediaPlayerService.kt` - Main playback service
- `MediaServiceConnection.kt` - Connect to service from UI
- `PlaybackStateController.kt` - Single source of truth for playback state
- `SleepTimer.kt` - Sleep timer logic

---

## Architecture Patterns

### Compose in Fragments
```kotlin
// Use ComposeView in Fragment
override fun onCreateView(...): View {
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val viewModel: MyViewModel = hiltViewModel()
            OpusTheme(darkTheme = true) {
                MyScreen(...)
            }
        }
    }
}
```

### ViewModel with StateFlow
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    fun doSomething() {
        _uiState.update { it.copy(loading = true) }
    }
}
```

### Bottom Sheets (Material3)
```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

if (state.showSheet) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OpusColors.Surface,
    ) {
        // Content
    }
}
```

---

## Build & Test

```bash
# Set JAVA_HOME for Gradle
export JAVA_HOME="/c/Users/cyclo/.jdks/jdk-17.0.17+10"

# Build debug APK
./gradlew assembleDebug

# APK location
app/build/outputs/apk/debug/app-debug.apk
```

**Environment:**
- Kotlin: 2.1.0, KSP: 2.1.0-1.0.29, Hilt: 2.54
- AGP: 8.13.2, Java: JDK 17.0.17+10
- Min SDK: 26, Target SDK: 35

---

## Git Authentication

`.netrc` file at `C:\Users\cyclo\_netrc`:
```
machine github.com
login USERNAME
password TOKEN
```

---

## What NOT to Change

- Package path remains `local.oss.chronicle` (class names use Opus branding)
- Don't remove existing features that work
- Don't break Plex authentication flow
- Keep GPL-3.0 license

---

## Quick Reference

| Task | File |
|------|------|
| Add new bottom sheet | `NowPlayingScreen.kt` |
| Change nav bar style | `styles.xml`, `activity_main.xml` |
| Update colors | `colors.xml`, `OpusTheme.kt` |
| Add playback feature | `NowPlayingViewModel.kt`, `MediaPlayerService.kt` |
| Change dimensions | `dimens.xml` |
| Track progress | `todo.md` |
| See what's done | `CHANGELOG.md` |
