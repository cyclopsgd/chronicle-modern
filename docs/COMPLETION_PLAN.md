# Opus App - Final Completion Plan

## Overview

This plan outlines the work needed to bring Opus to a polished, release-ready state. Work is organized into parallel batches where possible.

---

## Batch 1: Parallel Agents (Can Run Simultaneously)

### Agent 1: Chapter Playback Bugs

**Goal:** Fix chapter selection and navigation so users can tap any chapter and playback starts at that chapter's position.

**Known Issues:**
1. Clicking a chapter in BookDetailsScreen for a book not currently playing starts at 0:00 instead of chapter position
2. Skip chapter forward/back buttons don't navigate between chapters

**Files to Investigate:**
- `app/src/main/java/local/oss/chronicle/features/bookdetails/compose/BookDetailsScreen.kt` - chapter click handler
- `app/src/main/java/local/oss/chronicle/features/bookdetails/compose/ComposeBookDetailsViewModel.kt` - playBook/playChapter logic
- `app/src/main/java/local/oss/chronicle/features/player/PlaybackStateController.kt` - seek and chapter logic
- `app/src/main/java/local/oss/chronicle/features/player/MediaPlayerService.kt` - how media items are prepared
- `app/src/main/java/local/oss/chronicle/features/player/AudiobookMediaSessionCallback.kt` - handles playFromMediaId
- `app/src/main/java/local/oss/chronicle/data/model/Chapter.kt` - chapter data model
- `app/src/main/java/local/oss/chronicle/data/model/MediaItemTrack.kt` - track data model

**Key Questions to Answer:**
1. What is `Chapter.startTimeOffset` - is it book-relative or track-relative?
2. When `playChapter()` is called, how is the offset passed to the player?
3. Where does the seek get lost between UI click and actual playback position?

**Tasks:**
1. Trace the code path from chapter click → playback start
2. Add logging to understand current behavior
3. Fix the offset passing mechanism
4. Test with both M4B (single file) and multi-file audiobooks
5. Document the offset semantics for future reference

**Success Criteria:**
- Tapping chapter 5 of a book starts playback at chapter 5's position
- Skip forward/back buttons navigate to next/previous chapter
- Works for both M4B and multi-file books

---

### Agent 2: Performance Optimization

**Goal:** Improve release build performance - reduce lag and improve responsiveness.

**Areas to Address:**

#### 1. Baseline Profiles (Critical for Compose)
Compose apps need baseline profiles for good release performance. Without them, the first launch and initial interactions are slow because code isn't pre-compiled.

**Tasks:**
- Add `androidx.profileinstaller:profileinstaller` dependency
- Create baseline profile module or use Macrobenchmark
- Generate baseline profiles for critical user journeys:
  - App startup
  - Library scrolling
  - Book details loading
  - Player controls

#### 2. Compose Stability Audit
Unstable parameters cause unnecessary recompositions.

**Files to Check:**
- `app/src/main/java/local/oss/chronicle/features/home/compose/HomeScreen.kt`
- `app/src/main/java/local/oss/chronicle/features/library/compose/LibraryScreen.kt`
- `app/src/main/java/local/oss/chronicle/features/nowplaying/NowPlayingScreen.kt`
- `app/src/main/java/local/oss/chronicle/features/bookdetails/compose/BookDetailsScreen.kt`

**Tasks:**
- Enable Compose compiler reports: `kotlinOptions { freeCompilerArgs += "-P" + "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=..." }`
- Review reports for unstable classes
- Add `@Immutable` or `@Stable` annotations where needed
- Ensure data classes used in UI state are stable

#### 3. Image Loading
**Tasks:**
- Verify Coil disk cache configuration
- Add proper placeholders to prevent layout jumps
- Use `crossfade` for smoother image appearance
- Check if images are being reloaded unnecessarily

#### 4. Database Performance
**Tasks:**
- Check for main thread database access (should see StrictMode warnings)
- Review DAO queries for missing indices
- Ensure all repository calls use `withContext(Dispatchers.IO)`

**Success Criteria:**
- Release build scrolling is smooth (60fps)
- App startup is fast (< 2 seconds to interactive)
- No visible lag when navigating between screens

---

### Agent 3: Mini Player Compose Migration

**Goal:** Convert the XML-based mini player to Compose for consistency and better animations.

**Current Implementation:**
- `app/src/main/res/layout/activity_main.xml` - contains mini player layout
- `app/src/main/java/local/oss/chronicle/application/MainActivity.kt` - manages mini player state
- `app/src/main/java/local/oss/chronicle/features/currentlyplaying/CurrentlyPlayingBindingAdapters.kt` - animations

**New Files to Create:**
- `app/src/main/java/local/oss/chronicle/ui/components/MiniPlayer.kt`

**Design Spec:**
- Height: 72dp
- Background: Surface color (#1E1E1E)
- Progress bar at top (2dp, amber color)
- Layout: [Cover 56x56] [Title + Chapter] [Play/Pause button]
- Tap anywhere (except button) expands to full player
- Slide up animation when appearing
- Slide down animation when hiding

**Tasks:**
1. Create `MiniPlayer` composable with:
   ```kotlin
   @Composable
   fun MiniPlayer(
       bookTitle: String,
       chapterTitle: String,
       coverUrl: String?,
       progress: Float, // 0f to 1f
       isPlaying: Boolean,
       onPlayPauseClick: () -> Unit,
       onExpandClick: () -> Unit,
       modifier: Modifier = Modifier,
   )
   ```

2. Create `MiniPlayerState` data class for ViewModel

3. Modify `activity_main.xml`:
   - Replace mini player XML with ComposeView
   - Keep BottomNavigationView as XML (lower risk)

4. Update `MainActivity.kt`:
   - Set up ComposeView for mini player
   - Connect to existing playback state

5. Add animations:
   - `AnimatedVisibility` for show/hide
   - `slideInVertically` / `slideOutVertically`

**Success Criteria:**
- Mini player shows current book info
- Play/pause button works
- Tapping expands to full player
- Smooth show/hide animations
- No visual regression from current implementation

---

## Batch 2: Sequential (After Batch 1)

### Agent 4: Media3 Migration Completion

**Depends on:** Agent 1 findings (chapter offset semantics)

**Goal:** Complete migration from legacy MediaPlayerService to Media3 MediaLibraryService.

**Current State:**
- `Media3PlayerService.kt` exists but playback doesn't start
- `Media3ServiceConnection.kt` handles client connection
- Legacy service still enabled in manifest

**Branch:** `feat/media3-migration`

**Tasks:**
1. Apply chapter offset fixes from Agent 1
2. Debug why ExoPlayer isn't playing:
   - Check `onSetMediaItems` implementation
   - Verify media items are correctly formatted
   - Check for errors in logcat
3. Test Android Auto integration
4. Remove legacy service once Media3 works

**Success Criteria:**
- Books play correctly via Media3
- Chapter navigation works
- Android Auto browsing works
- Legacy service removed

---

## Batch 3: Final Polish

### Agent 5: Release Preparation

**Depends on:** All previous agents

**Tasks:**

#### Testing
- Re-enable unit tests (fix constructor signature issues)
- Add integration tests for critical paths
- Test on multiple API levels (26, 29, 33, 34)
- Test on different screen sizes

#### Release Configuration
- Set up proper release signing (not debug)
- Configure ProGuard rules if needed
- Set up Play Store credentials
- Create store listing assets

#### Final Polish
- Review all empty states
- Review all error states
- Check offline mode behavior
- Verify all animations are smooth

**Success Criteria:**
- All tests pass
- Signed release APK works correctly
- Ready for Play Store submission

---

## File Reference

### Core Playback
- `MediaPlayerService.kt` - Legacy playback service
- `Media3PlayerService.kt` - New Media3 service
- `PlaybackStateController.kt` - Playback state management
- `AudiobookMediaSessionCallback.kt` - Media session handling

### UI Screens (Compose)
- `HomeScreen.kt` - Home with categories
- `LibraryScreen.kt` - Book grid/list
- `NowPlayingScreen.kt` - Full player
- `BookDetailsScreen.kt` - Book info + chapters
- `SettingsScreen.kt` - Settings
- `CollectionsScreen.kt` - Collections grid

### Navigation
- `Navigator.kt` - Fragment navigation
- `MainActivity.kt` - Main activity + bottom nav

### Data
- `Chapter.kt` - Chapter model
- `MediaItemTrack.kt` - Track model
- `Audiobook.kt` - Book model

---

## Progress Tracking

| Agent | Status | Notes |
|-------|--------|-------|
| 1: Chapter Bugs | Not Started | |
| 2: Performance | Not Started | |
| 3: Mini Player | Not Started | |
| 4: Media3 | Blocked on #1 | |
| 5: Release | Blocked on all | |
