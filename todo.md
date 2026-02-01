# TODO - Opus Audiobook Player

## Pending Tasks

### Branding (High Priority)
- [ ] **Generate launcher icons from SVGs**
  - Convert `docs/Logos/ic_launcher_foreground.svg` to Android Vector Drawable
  - Convert `docs/Logos/ic_launcher_background.svg` to Android Vector Drawable
  - Generate mipmap PNGs at all densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
  - Update `app/src/main/res/mipmap-*` folders
  - Update `ic_launcher.xml` and `ic_launcher_round.xml` for adaptive icons
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
- [ ] Speed selector bottom sheet (0.5x - 3.0x) - *uses existing speed chooser*
- [ ] Sleep timer bottom sheet with presets
- [ ] Chapter list bottom sheet (Compose)
- [ ] Per-book speed memory (store in Room)

### Phase 2.2: Playback Controls
- [ ] Volume fade-out before sleep stop (15 seconds)
- [ ] Shake-to-extend sleep timer (accelerometer)
- [ ] Chapter markers on scrubber

### Phase 3: Car Mode
- [ ] Dedicated car mode screen (Compose)
- [ ] Auto-detect Bluetooth car audio
- [ ] Giant play/pause button (120dp+)
- [ ] Disable sleep timer while in car mode

### Phase 4: Library Screen
- [ ] Grid view with cover art
- [ ] Progress indicator overlay on covers
- [ ] Filters: Not Started / In Progress / Finished / Downloaded
- [ ] Sort: Title / Author / Date Added / Recently Played

### Technical Debt
- [ ] Migrate remaining LiveData to StateFlow
- [ ] Replace Fresco with Coil throughout
- [ ] Full Media3 migration (MediaSessionService)
- [ ] Update package name from `local.oss.chronicle` to new name (breaking change)

---

## Bug Fixes

### High Priority
- [x] MediaBrowser onConnected callback not firing (fixed: setSessionToken was missing)

### Medium Priority
- [ ] Notification doesn't update on chapter change
- [ ] Large libraries slow to load (needs incremental loading)
- [ ] Playback progress in library not real-time

### Low Priority
- [ ] Sleep timer "end of chapter" doesn't account for playback speed

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
