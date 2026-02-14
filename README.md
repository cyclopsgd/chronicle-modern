# Opus - Audiobook Player 🎧

<div align="center">

**A modern, premium Plex audiobook player for Android**

*Bringing your self-hosted audiobook library to life*

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org/)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

[Features](#-features) • [Roadmap](#-roadmap) • [Tech Stack](#-tech-stack) • [Contributing](#-contributing)

</div>

---

## 📖 About

**Opus** is a complete modernization of the Chronicle Plex audiobook player, rebuilt from the ground up with 2025's best Android development practices.

### The Vision

A **premium audiobook experience** that rivals commercial apps like Audible, while maintaining the flexibility and privacy of self-hosted Plex:

- 🏗️ **Modern architecture** - Multi-module design, Hilt DI, Jetpack Compose
- 🎨 **Premium UX** - Cover-art-dominant design, car mode, smart features
- ⚡ **Rock-solid reliability** - Robust playback, offline handling, progress sync
- 🚀 **Active development** - Continuous improvement, not just bug fixes

---

## 🎨 Brand Identity

| Element | Value |
|---------|-------|
| **Name** | Opus - Audiobook Player |
| **Primary Color** | Amber `#FFAB40` |
| **Secondary Color** | Soft Green `#81C784` |
| **Background** | `#121212` |
| **Surface** | `#1E1E1E` |
| **Logo** | "O" with audio waveform |
| **Launcher Icon** | Adaptive icon with vector drawable |

Brand assets are in [`docs/Logos/`](docs/Logos/).

---

## ✨ Features

### Currently Available
- ✅ Stream audiobooks from your Plex server
- ✅ Offline playback with downloads
- ✅ Playback progress sync with Plex
- ✅ Variable playback speed (0.5x - 3.0x, per-book memory)
- ✅ Sleep timer with shake-to-extend
- ✅ Chapter navigation (including M4B chapters)
- ✅ Android Auto support
- ✅ Smart auto-rewind on resume
- ✅ Skip silent audio
- ✅ Network-aware playback recovery
- ✅ **Car Mode** - Giant buttons, Bluetooth auto-detection
- ✅ **Compose Library** - Grid view, filters, search
- ✅ **Modern Now Playing** - Cover-art-dominant design

### ✅ Recently Completed (February 2026)

**🚀 Performance Optimization Sprint**
- ⚡ **Baseline Profiles** - 30-50% faster app startup on release builds
- 🗃️ **Database Indices** - 5 new indices for lightning-fast library queries
- 🎨 **Compose Stability** - @Immutable annotations on 16 UI classes for smoother scrolling
- 🧹 **Code Cleanup** - Deleted 22 unused XML fragments/ViewModels (5,000+ lines removed)

**🐛 Critical Bug Fixes**
- 💾 **Position Persistence** - Fixed race condition causing position loss when switching books
- 📱 **Mini Player State** - Auto-restores after app restart (no more "lost audiobook link")
- 📜 **Library Scroll Jank** - Smooth scrolling during playback (smart progress updates)
- 📖 **Chapter Buffering** - Fixed chapter clicks starting at 0:00 instead of chapter position
- 🔧 **Bottom Nav Clipping** - Fixed navigation bar being cut off on some devices

**🎨 UI Modernization (Compose Migration)**
- ✅ Home Screen - Netflix-style categories with Continue Listening
- ✅ Library Screen - Grid/list view with filters and search
- ✅ Book Details - Cover-dominant design with chapter list
- ✅ Settings - Modern Compose preferences
- ✅ Collections - Beautiful grid view
- ✅ Now Playing - Full-screen player with bottom sheets
- ✅ Car Mode - Giant buttons for safe driving

**Previous Major Milestones**
- 🎨 **Phase 2** - Now-Playing Screen (Compose, sleep timer, chapters, speed selector)
- 📚 **Phase 3** - Library Screen (grid view, filters, search, progress indicators)
- ⬇️ **Phases 4-5** - Downloads & Sync (WorkManager, SD card, Plex progress sync)
- 🚗 **Phase 6** - Car Mode (giant 140dp button, Bluetooth auto-detection)

### 🔮 Coming Soon

**Phase 7: Stats & Polish**
- 📊 Listening statistics (time, streaks, books finished)
- ✨ Additional UI polish and animation refinements
- 🎵 Full Media3 migration (in progress)

---

## 🏗️ Tech Stack

### Modern Android Development
- **Language**: Kotlin 2.1
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Build**: Gradle Kotlin DSL + Version Catalog

### Architecture & Dependencies
- **DI**: Hilt 2.54
- **Architecture**: Multi-module MVVM
- **UI**: Jetpack Compose + XML Views (80% migrated to Compose)
- **Async**: Coroutines + Flow
- **Database**: Room with optimized indices
- **Performance**: Baseline Profiles for fast startup

### Media & Networking
- **Playback**: ExoPlayer / Media3
- **HTTP**: Retrofit 2 + OkHttp 4
- **JSON**: Moshi
- **Images**: Coil (Compose) + Fresco (Views)

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: 17 or higher
- **Plex Server**: With at least one audiobook library

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/cyclopsgd/chronicle-modern.git
cd chronicle-modern

# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

### First Run Setup
1. Launch the app
2. Login with your Plex account
3. Select your server and audiobook library
4. Start listening! 🎧

---

## 📈 Development Roadmap

### Phase 1: Foundation ✅ Complete
- [x] Multi-module architecture
- [x] Hilt dependency injection migration
- [x] Android SDK 35, Kotlin 2.1
- [x] Core reliability fixes (smart rewind, error recovery, position persistence)
- [x] Opus branding
- [x] Launcher icon (vector drawable)
- [x] Dark theme bottom navigation

### Phase 2: Now-Playing Screen ✅ Complete
- [x] Compose theme (OpusTheme)
- [x] NowPlayingScreen composable
- [x] NowPlayingViewModel
- [x] Wire into app navigation
- [x] Full-screen mode (hides bottom nav)
- [x] High-resolution cover art
- [x] Chapter-relative progress tracking
- [x] Chapter list bottom sheet (Compose)
- [x] Sleep timer bottom sheet (Compose)
- [x] Speed selector bottom sheet (Compose)
- [x] Per-book speed memory

### Phase 3: Library Screen ✅ Complete
- [x] Grid view with cover art
- [x] Progress indicator overlay on covers
- [x] Filters: Not Started / In Progress / Finished / Downloaded
- [x] Sort: Title / Author / Date Added / Recently Played / Duration

### Phase 4: Downloads & Offline ✅ Complete
- [x] Download management with WorkManager
- [x] SD card storage support
- [x] Download progress indicators
- [x] Storage management in settings

### Phase 5: Progress Sync ✅ Complete
- [x] Report playback progress to Plex server
- [x] Read progress from Plex on library refresh
- [x] Handle conflicts (most recent wins)
- [x] Offline-first architecture

### Phase 6: Car Mode ✅ Complete
- [x] Dedicated car mode screen (Compose)
- [x] Auto-detect Bluetooth car audio
- [x] Giant play/pause button (140dp)
- [x] Disable sleep timer while in car mode
- [x] Car mode entry from Now Playing screen
- [x] Auto-enter car mode setting

### Phase 7: Stats & Polish 🚧 In Progress
- [ ] Stats tab in bottom navigation
- [ ] Listening time tracking
- [ ] Books finished count
- [ ] UI polish and animation refinements
- [ ] Full Media3 migration

---

## 🤝 Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Quick Start
1. Fork & clone
2. Create feature branch from `main`
3. Follow existing patterns (Hilt DI, MVVM, Compose for new UI)
4. Submit PR with clear description

---

## 📄 License

Licensed under **GNU General Public License v3.0** - see [LICENSE](LICENSE)

---

## 🙏 Credits

### Original Project
**Chronicle** by Matt Vaughn ([@mattttvaughn](https://github.com/mattttvaughn)) - The foundation this project builds upon.

### Built With
- **[Claude Code](https://claude.ai/)** - AI-assisted development
- **[Plex](https://www.plex.tv/)** - Self-hosted media platform

---

<div align="center">

**Built with ❤️ for the Plex audiobook community**

[⭐ Star](https://github.com/cyclopsgd/chronicle-modern) • [🐛 Report Bug](https://github.com/cyclopsgd/chronicle-modern/issues) • [💡 Request Feature](https://github.com/cyclopsgd/chronicle-modern/issues)

</div>
