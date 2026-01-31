# Chronicle Modern 🎧

<div align="center">

**A modern, premium Plex audiobook player for Android**

*Complete architectural modernization of Chronicle with 2025's best practices*

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

[Features](#-features) • [Roadmap](#-roadmap) • [Tech Stack](#-tech-stack) • [Contributing](#-contributing)

</div>

---

## 📖 About

Chronicle Modern is a ground-up modernization of **[Chronicle](https://github.com/mattttvaughn/chronicle)** - the beloved Plex audiobook player originally created by Matt Vaughn.

Rather than just maintaining the original codebase, this project takes a **complete architectural overhaul approach**: rebuilding Chronicle from the inside out using 2025's modern Android development practices, while preserving the solid foundation that made the original great.

### The Vision

Transform Chronicle into a **premium audiobook experience** that rivals commercial apps like Audible, while maintaining the flexibility and privacy of self-hosted Plex:

- 🏗️ **Modern architecture** - Multi-module design, Hilt DI, Jetpack Compose
- 🎨 **Premium UX** - Cover-art-dominant design, car mode, smart features
- ⚡ **Rock-solid reliability** - Robust playback, offline handling, progress sync
- 🚀 **Active development** - Continuous improvement, not just bug fixes
- 🤖 **AI-assisted development** - Leveraging Claude Code for systematic, high-quality transformation

This is Chronicle, **reimagined for the modern era**.

---

## ✨ Features

### Currently Available
- ✅ Stream audiobooks from your Plex server
- ✅ Offline playback with downloads
- ✅ Playback progress sync with Plex
- ✅ Variable playback speed
- ✅ Sleep timer
- ✅ Chapter navigation
- ✅ Android Auto support
- ✅ Auto-rewind
- ✅ Skip silent audio

### 🚧 Modernization In Progress

**Phase 1: Foundation & Architecture** *(60% complete)*
- ⚡ **Hilt dependency injection** - Modern, type-safe DI
- 📦 **Multi-module architecture** - Clean separation of concerns
- 🎯 **Android SDK 35** - Latest platform features
- 🛠️ **Enhanced reliability** - Robust playback position persistence
- 📱 **Jetpack Compose foundation** - Progressive UI modernization

### 🔮 Coming Soon

**Phase 2: Now-Playing Screen** *(Next Up)*
- 🎨 **Cover-art-dominant design** with blurred backgrounds
- ⏱️ **Smart sleep timer** - Shake to extend, volume fade-out
- 🎚️ **Per-book speed memory** - Remembers your preference per audiobook
- 📑 **Enhanced chapter navigation** - Beautiful bottom sheet with progress
- 🎨 **Dynamic theming** - Colors extracted from cover art

**Phase 3: Car Mode** *(High Priority)*
- 🚗 **Dedicated driving interface** - Giant, easy-to-tap buttons
- 🔌 **Auto-activation** - Triggers on Bluetooth car connection
- 🎯 **Zero distraction** - Just play, rewind, and bookmark
- 🔊 **Voice announcements** - Chapter changes, timer warnings

**Phase 4-6: Complete Transformation**
- 📚 **Library redesign** - Grid view, advanced filtering, search
- ⬇️ **Download overhaul** - Better management, progress tracking
- 🎵 **Media3 migration** - Next-gen playback engine
- 📊 **Listening stats** - Track your audiobook journey
- 🌙 **Material You** - Dynamic theming, modern design language

---

## 🏗️ Tech Stack

### Modern Android Development
- **Language**: Kotlin 2.0
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15)
- **Build**: Gradle Kotlin DSL + Version Catalog

### Architecture & Dependencies
- **DI**: Hilt (fully migrated from Dagger)
- **Architecture**: Multi-module MVVM
- **UI**: Jetpack Compose + XML Views (progressive migration)
- **Async**: Coroutines + Flow
- **Navigation**: Jetpack Navigation Component
- **Database**: Room

### Media & Networking
- **Playback**: ExoPlayer (migrating to Media3)
- **HTTP**: Retrofit 2 + OkHttp 4
- **JSON**: Moshi with Kotlin codegen
- **Downloads**: Fetch2 + WorkManager
- **Images**: Glide

### Quality Assurance
- **Testing**: JUnit 5, Espresso, Mockito
- **Code Analysis**: Detekt (planned)
- **CI/CD**: GitHub Actions (planned)

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: 17 or higher
- **Plex Server**: With at least one audiobook library

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/yourusername/chronicle-modern.git
cd chronicle-modern

# Build debug APK
./gradlew assembleDebug

# Or install directly to connected device
./gradlew installDebug
```

### First Run Setup
1. Launch the app
2. Enter your Plex server address
3. Authenticate with your Plex account
4. Select your audiobook library
5. Start listening! 🎧

---

## 📈 Development Roadmap

### Phase 1: Foundation ✅ 60% Complete
- [x] Multi-module architecture setup
- [x] Gradle version catalog (libs.versions.toml)
- [x] Update to Android SDK 35, Hilt 2.52, Compose BOM 2025.01.00
- [x] Hilt migration - Core modules, data layer, ViewModels
- [ ] Complete Hilt migration - Remaining fragments and workers
- [ ] Core reliability improvements (robust position saving, error handling)
- [ ] Comprehensive test suite

### Phase 2: Now-Playing Screen Redesign
- [ ] Compose-based UI with cover-art-dominant layout
- [ ] Smart sleep timer with configurable presets
- [ ] Per-book playback speed memory
- [ ] Enhanced chapter list bottom sheet
- [ ] Extract and use M4B embedded chapters

### Phase 3: Car Mode
- [ ] Dedicated car UI with giant buttons
- [ ] Bluetooth auto-activation
- [ ] Safety-focused minimal design
- [ ] Quick bookmark functionality

### Phase 4: Library Screen Overhaul
- [ ] Grid view with cover art
- [ ] Advanced filters (progress, author, series)
- [ ] Fast search
- [ ] Sort options
- [ ] Collection/series grouping

### Phase 5: Downloads & Offline
- [ ] Redesigned download management UI
- [ ] Quality selection (standard/high)
- [ ] Storage location choice (SD card support)
- [ ] Space usage visualization
- [ ] Background download notifications

### Phase 6: Media3 & Polish
- [ ] Migrate to Media3 for future-proof playback
- [ ] Material You dynamic theming
- [ ] Listening statistics dashboard
- [ ] Enhanced Android Auto experience
- [ ] Performance optimizations

**Full details**: See [DESIGN_SPEC.md](DESIGN_SPEC.md) and [MIGRATION_CHANGELOG.md](MIGRATION_CHANGELOG.md)

---

## 🤝 Contributing

Contributions are **very welcome**! This is an active modernization project with lots of opportunity to get involved.

### How to Contribute

1. **Browse issues** - Look for `good first issue` or `help wanted` tags
2. **Check progress** - See [MIGRATION_CHANGELOG.md](MIGRATION_CHANGELOG.md) for current work
3. **Fork & branch** - Create a feature branch from `main`
4. **Follow patterns** - Use Hilt DI, MVVM, existing code style
5. **Test your changes** - Add tests for new functionality
6. **Submit PR** - Clear description of what and why

### Development Guidelines

```bash
# Setup
git checkout -b feature/awesome-feature

# Development
# See CLAUDE.md for architectural decisions and patterns
# Follow Kotlin coding conventions
# Use Hilt for all dependency injection

# Testing
./gradlew test
./gradlew connectedAndroidTest

# Verify build
./gradlew assembleDebug

# Commit
git commit -m "feat: add awesome feature"

# Submit
git push origin feature/awesome-feature
# Then open PR on GitHub
```

### Code Standards
- **Kotlin conventions** - Follow official style guide
- **Meaningful names** - Clear, descriptive variable/function names
- **Small, focused functions** - Single responsibility principle
- **Comments for complexity** - Document non-obvious logic
- **Hilt for DI** - Constructor injection preferred
- **Immutability** - Use `val` and immutable collections where possible

---

## 📄 License

Licensed under **GNU General Public License v3.0** - see [LICENSE](LICENSE)

**TL;DR:**
- ✅ Free to use, modify, and distribute
- ✅ Source code must be disclosed if distributed
- ✅ Modifications must use GPL-3.0
- ✅ No warranty provided

**Note on Branding**: If you fork this project for distribution, replace branding assets (logo, icons) with your own.

---

## 🙏 Credits & Acknowledgments

### Original Creator
**Matt Vaughn** ([@mattttvaughn](https://github.com/mattttvaughn)) - Created the original Chronicle app. This modernization builds upon his excellent foundation and vision for a self-hosted audiobook player.

### Inspiration
- **[Prologue](https://prologue.audio/)** - The iOS equivalent, showing what's possible
- **[Audible](https://www.audible.com/)** - UX inspiration for premium features
- **[Plex](https://www.plex.tv/)** - For making self-hosted media accessible

### Technology
Built with assistance from **[Claude Code](https://claude.ai/)** - demonstrating how AI can systematically modernize legacy codebases with architectural precision and maintainability.

---

## 💬 Community & Support

- **🐛 Bug Reports**: [Open an issue](https://github.com/yourusername/chronicle-modern/issues/new?template=bug_report.md)
- **💡 Feature Requests**: [Suggest a feature](https://github.com/yourusername/chronicle-modern/issues/new?template=feature_request.md)
- **💬 Discussions**: [Join the conversation](https://github.com/yourusername/chronicle-modern/discussions)
- **📖 Documentation**: See [CLAUDE.md](CLAUDE.md) for development guide

### Useful Resources
- [Plex Audiobook Guide](https://github.com/seanap/Plex-Audiobook-Guide) - Setup your Plex server for audiobooks
- [Original Chronicle subreddit](https://www.reddit.com/r/ChronicleApp/) - Community discussions

---

## 📊 What Makes This Different?

This isn't just maintenance of the original Chronicle - it's a **complete architectural transformation**:

| Aspect | Original Chronicle | Chronicle Modern |
|--------|-------------------|------------------|
| **Status** | Archived (2022) | Active development |
| **Approach** | Working codebase | Complete modernization |
| **Dependency Injection** | Dagger | **Hilt** |
| **UI Framework** | XML Views | **Jetpack Compose** (progressive) |
| **Media Playback** | ExoPlayer | **Media3** (planned) |
| **Architecture** | Single module | **Multi-module** |
| **Android SDK** | 31 | **35** (latest) |
| **Kotlin** | 1.6 | **2.0** |
| **New Features** | - | Car Mode, enhanced UX, stats |

**The goal**: Build the audiobook app that Plex deserves in 2025 and beyond.

---

## 🎯 Project Philosophy

1. **User Experience First** - Features should delight, not just function
2. **Reliability Above All** - Your listening position should never be lost
3. **Modern But Stable** - Use latest tools, but proven patterns
4. **Privacy Respected** - Self-hosted, no tracking, no analytics
5. **Community Driven** - Built for users, by users
6. **Open Source Spirit** - Share improvements, learn together

---

## 📸 Screenshots

*Coming soon with Phase 2 completion - stay tuned for the redesigned Now-Playing screen!*

---

<div align="center">

**Built with ❤️ for the Plex audiobook community**

[⭐ Star this repo](https://github.com/yourusername/chronicle-modern) • [🐛 Report Bug](https://github.com/yourusername/chronicle-modern/issues) • [💡 Request Feature](https://github.com/yourusername/chronicle-modern/issues)

---

*"The best way to predict the future is to build it."*

</div>
