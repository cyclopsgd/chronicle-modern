# Hilt Migration Changelog

## Completed

### Phase 1.1: Multi-Module Architecture
- ✅ Created module structure (core: common, network, database, media, sync | feature: library, player, downloads, settings)
- ✅ Added build.gradle.kts files for each module
- ✅ Updated settings.gradle.kts with all modules
- ✅ Created AndroidManifest.xml files for each module

### Phase 1.2: Dependency Updates
- ✅ Created gradle/libs.versions.toml with version catalog
- ✅ Added Hilt 2.52
- ✅ Added Compose BOM 2025.01.00
- ✅ Added Compose Compiler plugin
- ✅ Updated SDK versions (compileSdk: 35, targetSdk: 35, minSdk: 26)
- ✅ Set up local.properties with correct SDK path
- ✅ Configured JAVA_HOME to /c/Users/cyclo/.jdks/jdk-17.0.17+10

### Phase 1.2: Hilt Migration - Core DI
- ✅ Added @HiltAndroidApp to ChronicleApplication
- ✅ Migrated AppModule to Hilt (object with @ApplicationContext)
- ✅ Migrated ServiceModule to Hilt (abstract class with companion object)
- ✅ Migrated ActivityModule to Hilt (abstract class with companion object)
- ✅ Deleted old Dagger components (AppComponent, ServiceComponent, ActivityComponent)
- ✅ Deleted custom scope annotations (@ServiceScope, @ActivityScope)
- ✅ Updated MediaPlayerService with @AndroidEntryPoint
- ✅ Removed Dagger component references from MediaPlayerService
- ✅ Fixed CollectionIdConverter to create local Moshi instance

### Phase 1.2: Hilt Migration - Components & Views
- ✅ Added @AndroidEntryPoint to DebugInfoDialogFragment
- ✅ Added @AndroidEntryPoint to SettingsFragment
- ✅ Added @AndroidEntryPoint to ModalBottomSheetSpeedChooser
- ✅ Created PlexConfigEntryPoint for BindingAdapters
- ✅ Created PrefsRepoEntryPoint for SettingsList
- ✅ Created AppContextEntryPoint for accessing Context, CoroutineExceptionHandler, List<File>
- ✅ Updated BindingAdapters to use EntryPointAccessors
- ✅ Updated SettingsList to use EntryPointAccessors

### Phase 1.2: Hilt Migration - Services & Player
- ✅ Injected CoroutineExceptionHandler into AudiobookMediaSessionCallback
- ✅ Injected CoroutineExceptionHandler into OnMediaChangedCallback
- ✅ Injected CoroutineExceptionHandler into MediaPlayerService
- ✅ Updated PlayerExt.kt skipToNext/skipToPrevious to accept Context parameter
- ✅ Updated calls to skipToNext/skipToPrevious in AudiobookMediaSessionCallback
- ✅ Removed Injector imports from player components

### Phase 1.2: Hilt Migration - ViewModels
- ✅ Injected Context, CoroutineExceptionHandler, and List<File> into SettingsViewModel
- ✅ Replaced all Injector.get() calls in SettingsViewModel with injected dependencies
- ✅ Fixed AudiobookDetailsViewModel (inject Context with @ApplicationContext)
- ✅ Updated AudiobookDetailsViewModel.Factory to inject Context

### Phase 1.2: Hilt Migration - Data Layer Complete
- ✅ Deleted obsolete Injector.kt
- ✅ Fixed MainActivityViewModel (inject CoroutineExceptionHandler)
- ✅ Fixed ChronicleApplication (use this instead of Injector)
- ✅ Fixed LibrarySyncRepository (inject Context)
- ✅ Fixed PlexConfig (inject Context)
- ✅ Fixed SharedPreferencesPrefsRepo (inject List<File>)
- ✅ Fixed TrackRepository (use injected plexPrefs)
- ✅ Fixed CachedFileManager (inject List<File>, remove Injector.get().fetch())
- ✅ Fixed MediaItemTrack (use EntryPoints for non-injectable data class)

### Phase 1.2: Hilt Migration - Workers
- ✅ Fixed PlexSyncScrobbleWorker (use @HiltWorker with @AssistedInject)
- ✅ Injected all dependencies into PlexSyncScrobbleWorker (trackRepository, bookRepository, plexConfig, plexPrefs, plexMediaService, exceptionHandler, moshi)

### Phase 1.2: Hilt Migration - Fragments
- ✅ Added @AndroidEntryPoint to AudiobookDetailsFragment
- ✅ Removed old manual injection code from AudiobookDetailsFragment

### Phase 1.2: Hilt Migration - ViewModels Complete
- ✅ Migrated all 7 ViewModels to inject CoroutineExceptionHandler
  - ✅ CollectionsViewModel (inject @Inject constructor + CoroutineExceptionHandler)
  - ✅ CurrentlyPlayingViewModel (inject @Inject constructor + Context + CoroutineExceptionHandler)
  - ✅ HomeViewModel (inject @Inject constructor + CoroutineExceptionHandler)
  - ✅ LibraryViewModel (inject @Inject constructor + Context + CoroutineExceptionHandler)
  - ✅ ChooseServerViewModel (already had @Inject, added CoroutineExceptionHandler)
  - ✅ ChooseUserViewModel (inject @Inject constructor + CoroutineExceptionHandler)
  - ✅ LoginViewModel (inject @Inject constructor + CoroutineExceptionHandler)
- ✅ Replaced all Injector.get().unhandledExceptionHandler() with injected exceptionHandler
- ✅ Replaced all Injector.get().applicationContext() with @ApplicationContext Context in ViewModels

## In Progress

### Phase 1.2: Hilt Migration - Remaining Work
**Status:** ViewModels complete! Now moving to Fragments (estimated ~100-120 errors remaining)

### Phase 1.2: Hilt Migration - Remaining Work
Still need to migrate:
- [ ] Remaining Fragments using .inject() pattern (~18+ files)
- [ ] Remaining Workers needing @HiltWorker (~2-4 files)
  - DownloadNotificationWorker
  - MoveSyncLocationWorker
- [ ] Remaining files with Injector references (~10-15 files)

## Todo

### Phase 1.2: Hilt Migration - Remaining Files
Need to migrate these files from Injector.get() to Hilt:
- [x] ~~MainActivity~~ (COMPLETED)
- [x] ~~MainActivityViewModel~~ (COMPLETED)
- [ ] Remaining Fragment files still using .inject()
- [ ] Remaining ViewModel files using Injector.get()
- [ ] Remaining Worker files (DownloadNotificationWorker, MoveSyncLocationWorker)
- [x] ~~Data layer files~~ (COMPLETED: LibrarySyncRepository, SharedPreferencesPrefsRepo, TrackRepository, PlexConfig, CachedFileManager)
- [x] ~~Model files~~ (COMPLETED: MediaItemTrack)

### Phase 1.3: Core Reliability Fixes
- [ ] Implement robust playback position persistence (save every 5 seconds + on pause/stop)
- [ ] Fix offline/online transition handling
- [ ] Implement proper error handling with user-friendly messages
- [ ] Add smart rewind on resume (5-20 seconds based on pause duration)

### Phase 2: Now-Playing Screen Redesign
- Not started

### Phase 3: Car Mode
- Not started

### Phase 4: Library Screen
- Not started

### Phase 5: Downloads & Offline
- Not started

### Phase 6: Progress Sync
- Not started

## Notes
- JAVA_HOME: /c/Users/cyclo/.jdks/jdk-17.0.17+10
- SDK Path: C:/Users/cyclo/AppData/Local/Android/Sdk
- Hilt requires KSP for annotation processing
- Compose Compiler plugin required for Kotlin 2.0+
