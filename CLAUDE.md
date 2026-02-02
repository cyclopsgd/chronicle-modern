# Opus - Audiobook Player (Development Guide)

> **App Name**: Opus - Audiobook Player
> **Package**: `local.oss.chronicle` (legacy, may change)
> **Branding**: See `docs/Logos/` for SVG assets

> See `DESIGN_SPEC.md` for full design specification and `todo.md` for current task tracking.

---

## Current State (2026-02-01)

### ✅ Completed Phases
- **Phase 1.1**: Multi-module architecture
- **Phase 1.2**: Hilt 2.54 migration (complete)
- **Phase 1.3**: Core reliability fixes (smart rewind, error recovery, position persistence)
- **Phase 2.1**: Now-Playing Screen (mostly complete)

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

### 🚧 Next Steps
- Consider Media3 migration (may resolve both chapter issues above)
- Alternative: Deeper investigation into chapter offset handling architecture
- Key files for chapter handling:
  - `PlaybackState.kt` - currentChapter, currentChapterIndex computed properties
  - `PlayerExt.kt` - skipToNext(), skipToPrevious() functions
  - `TrackListStateManager.kt` - manages track/chapter state
  - `AudiobookMediaSessionCallback.kt` - handles play commands from media session

### 📋 Backlog (see `todo.md`)
- PNG launcher icons (vector done, PNG fallbacks needed)
- Phase 3: Car Mode
- Phase 4: Library Screen
- Phase 5: Downloads
- Phase 6: Advanced Features
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

- Keep the app ID as `local.oss.chronicle` for now
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
