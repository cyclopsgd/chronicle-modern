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
- ✅ Variable playback speed
- ✅ Sleep timer with shake-to-extend
- ✅ Chapter navigation (including M4B chapters)
- ✅ Android Auto support
- ✅ Smart auto-rewind on resume
- ✅ Skip silent audio
- ✅ Network-aware playback recovery

### 🚧 In Progress

**Phase 2: Now-Playing Screen** *(Active)*
- 🎨 **Compose-based UI** with cover-art-dominant design
- ⏱️ **Enhanced sleep timer** - Volume fade-out, shake to extend
- 📑 **Chapter list bottom sheet** - Beautiful chapter navigation
- 🎚️ **Speed selector** - 0.5x to 3.0x in 0.1x increments

### 🔮 Coming Soon

**Phase 3: Car Mode**
- 🚗 Dedicated driving interface with giant buttons
- 🔌 Auto-activation on Bluetooth connection
- 🎯 Zero-distraction design

**Phase 4-6: Complete Transformation**
- 📚 Library redesign with grid view and filters
- ⬇️ Download management overhaul
- 🎵 Media3 migration
- 📊 Listening statistics

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
- **UI**: Jetpack Compose + XML Views (progressive migration)
- **Async**: Coroutines + Flow
- **Database**: Room

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

### Phase 2: Now-Playing Screen 🚧 In Progress
- [x] Compose theme (OpusTheme)
- [x] NowPlayingScreen composable
- [x] NowPlayingViewModel
- [x] Wire into app navigation
- [x] Full-screen mode (hides bottom nav)
- [x] High-resolution cover art
- [x] Chapter-relative progress tracking
- [x] Chapter list bottom sheet (Compose)
- [x] Sleep timer bottom sheet (Compose)
- [ ] Speed selector bottom sheet (Compose)
- [ ] Per-book speed memory

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

### Phase 5: Downloads & Offline
- [ ] Download management overhaul
- [ ] SD card storage support
- [ ] Background downloads via WorkManager

### Phase 6: Advanced Features
- [ ] Full Media3 migration
- [ ] Progress sync with Plex server
- [ ] Bookmarks with notes
- [ ] Listening statistics
- [ ] Android Auto optimization

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
