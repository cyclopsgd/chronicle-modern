# TODO - Opus Audiobook Player

## Pending Tasks

### Branding (High Priority)
- [ ] **Generate PNG launcher icons at all densities**
  - Vector drawable done, but PNG fallbacks needed for some launchers
  - Use Android Studio Image Asset tool or convert SVGs
  - Needed: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi
- [ ] Update notification icon to use new branding
- [ ] Update splash screen (if exists) with Opus branding

### Phase 2: Now-Playing Screen (In Progress)
- [x] Create OpusTheme with brand colors
- [x] Create NowPlayingScreen composable
- [x] Create NowPlayingViewModel
- [x] Wire NowPlayingScreen into app navigation
- [x] Full-screen player hides bottom navigation
- [x] Chapter-relative progress display
- [x] Cover art with authentication token
- [x] High-resolution cover art (1000x1000)
- [x] Speed selector bottom sheet (0.5x - 3.0x) - *Compose implementation*
- [x] Sleep timer bottom sheet with presets
- [x] Chapter list bottom sheet (Compose)
- [x] Per-book speed memory (store in Room)

### Phase 2.2: Playback Controls
- [x] Always show mini player when logged in (like Audible)

### Phase 3: Library Screen ✅
- [x] Grid view with cover art (Compose)
- [x] Progress indicator overlay on covers
- [x] Filters: Not Started / In Progress / Finished / Downloaded
- [x] Sort: Title / Author / Date Added / Recently Played / Duration / Year
- [x] Search with real-time filtering
- [x] Pull-to-refresh

### Phase 4: Downloads & Offline ✅
- [x] Download button on book detail
- [x] Download progress indicator
- [x] Queue multiple downloads (WorkManager)
- [x] SD card storage support
- [x] Storage management in settings

### Phase 5: Progress Sync ✅
- [x] Report playback progress to Plex server
- [x] Read progress from Plex on library refresh
- [x] Handle conflicts (most recent wins)
- [x] Offline-first: show cached data, update from network

### Phase 6: Car Mode ✅
- [x] Dedicated car mode screen (Compose)
- [x] Auto-detect Bluetooth car audio
- [x] Giant play/pause button (120dp+)
- [x] Disable sleep timer while in car mode
- [x] Car mode entry from Now Playing screen
- [x] Auto-enter car mode setting in Settings

### Technical Debt
- [ ] Migrate remaining LiveData to StateFlow
- [ ] Replace Fresco with Coil throughout
- [ ] **Full Media3 migration (MediaSessionService)** - May resolve chapter navigation and playback issues
  - Current MediaSessionCompat has complex chapter offset handling
  - Media3's MediaSession has cleaner API for chapter/timeline support
- [ ] Update package name from `local.oss.chronicle` to new name (breaking change)
- [ ] Clean up PlayerExt.kt - remove CurrentlyPlayingSingleton cast, simplify chapter lookup

### Phase 7: Home Screen Redesign & Polish
- [ ] **Home page categorization** (Netflix/Audible style)
  - Continue Listening (in-progress books)
  - Recently Played (based on lastViewedAt)
  - Recently Added (based on addedAt)
  - Genre-based rows (e.g., "Mystery", "Sci-Fi", "Biography")
  - Horizontal scrolling rows
- [ ] UI polish and animation refinements
- [ ] Volume fade-out before sleep stop (15 seconds)
- [ ] Shake-to-extend sleep timer (accelerometer)
- [ ] Chapter markers on scrubber
- [ ] Book detail screen Compose migration
- [ ] Hide chapters behind "Chapters" button on book detail page

### Future: Stats Feature
- [ ] Add Stats tab to bottom navigation (Home, Library, Stats, Settings)
- [ ] Listening time (daily, weekly, monthly, all-time)
- [ ] Books finished count
- [ ] Listening streaks

---

## Bug Fixes

### High Priority
- [x] MediaBrowser onConnected callback not firing (fixed: setSessionToken was missing)
- [ ] **Skip chapter navigation not working** - Skip forward/back buttons don't navigate chapters correctly
  - Chapter offset semantics mismatch: `startTimeOffset` is track-relative for M4B files
  - PlayerExt.kt skipToNext/skipToPrevious need architectural review
  - May require Media3 migration for proper fix
- [ ] **Play from library list doesn't load chapter metadata** - Books started from library show no chapters
  - syncAudiobook may not be getting called with proper track list
  - Chapter data from Plex API not being populated into PlaybackState
  - AudiobookMediaSessionCallback.handlePlayBookWithNoTracks() investigated but issue persists

### Medium Priority
- [ ] Mini player loses audiobook link after app restart (shows "no audiobook found" when clicked)
- [ ] Samsung Now Bar notification image is low resolution
- [ ] Notification doesn't update on chapter change
- [ ] Large libraries slow to load (needs incremental loading)
- [ ] Playback progress in library not real-time
- [x] User profile images not loading on choose user screen (fixed: empty URI handling)
- [x] Profile picture shows black/orange square on selection screen (fixed with above)

### Low Priority
- [ ] Sleep timer "end of chapter" doesn't account for playback speed
- [ ] Recently added section - option for vertical layout instead of horizontal
- [ ] Update home nav icon (currently bland oblong shape)
- [ ] Review/improve transition animations

---

## Completed

### 2026-02-01: Phase 1.3 + Branding + Now Playing Integration
- [x] Fix MediaBrowserCompat connection (setSessionToken)
- [x] Smart Rewind on Resume
- [x] Network-Aware Playback Recovery
- [x] Player Error Recovery
- [x] Emergency Position Save
- [x] Opus branding (theme, colors, logo)
- [x] NowPlayingScreen composable (Phase 2.1)
- [x] Wire NowPlayingScreen into CurrentlyPlayingFragment
- [x] Full-screen player hides bottom navigation
- [x] Fix cover art URL authentication
- [x] High-resolution cover art (1000x1000)
- [x] Chapter-relative progress tracking
- [x] Dark background throughout app (all fragments updated)
- [x] Hilt DI reorganization (ActivityRetainedModule for ViewModel access)
- [x] Mini player progress bar (book progress indicator replacing static orange line)
- [x] Subtle divider between navbar and mini player
- [x] Rename "Chronicle" to "Opus" in all user-facing strings
- [x] Download button repositioned and resized
- [x] "Don't show again" option for chapter jump warning
- [x] Fix chapter selection from bottom sheet
- [x] Reduce spacing between nav bar icons and text
- [x] Speed toggle (tap to cycle forward, long-press to go back)
- [x] Library grid/list view toggle
- [x] Fix user profile images not loading

### Previous: Hilt Migration
- [x] Complete Hilt 2.54 migration
- [x] Fix all DI bindings
- [x] Multi-module architecture

---

## Notes

### SVG to Android Drawable Conversion
The logo SVGs in `docs/Logos/` need to be converted:
1. Use Android Studio's Vector Asset tool (File > New > Vector Asset)
2. Or use online converter like svg2vector
3. Ensure foreground fits in 72dp safe zone (108dp total for adaptive icons)

### Package Rename Consideration
Changing from `local.oss.chronicle` to something like `app.opus.audiobook` would:
- Break existing installs (users would need to reinstall)
- Require new Play Store listing
- Recommend doing this before any public release
