# Position Persistence Bug Fix

## Problem Summary
Playback position was being lost when switching between books, caused by three root issues:

1. **Debounce cancellation**: The 3-second debounce window would cancel pending writes when switching books
2. **Network sync overwrite**: `syncAudiobook()` could overwrite recently saved local progress with stale network data
3. **Race conditions**: Multiple components writing to database simultaneously

## Solution

### 1. Fixed Debounce Cancellation (`PlaybackStateController.kt`)

**Location**: `scheduleDatabaseWrite()` method (line 283)

**Problem**: `dbWriteJob?.cancel()` was unconditionally canceling pending writes, losing position data when users switched books within the 3-second debounce window.

**Fix**:
- Detect when switching to a DIFFERENT book
- Force immediate persist of the PREVIOUS book's position before canceling
- Only debounce writes for the SAME book

```kotlin
// If there's a pending write for a DIFFERENT book, persist it immediately
// to avoid losing position when switching books
if (lastPersisted != null && lastPersisted.audiobook?.id != state.audiobook?.id) {
    Timber.d("$TAG: Book switch detected - forcing immediate persist of previous book")
    // Persist the previous book's position immediately (not the new one)
    persistStateToDatabase(lastPersisted, force = true)
}

// Cancel pending write for the SAME book (debounce)
dbWriteJob?.cancel()
```

### 2. Enhanced Pause/Stop Handling (`PlaybackStateController.kt`)

**Location**: `updatePlayingState()` method (line 162)

**Problem**: Pausing might not persist immediately if debounce was active.

**Fix**: Cancel any pending debounced writes and force immediate write on pause/stop:

```kotlin
if (!isPlaying) {
    // Cancel any pending debounced writes first
    dbWriteJob?.cancel()
    // Force immediate write to ensure we don't lose position
    persistStateToDatabase(_state.value, force = true)
}
```

### 3. Improved Clear Method (`PlaybackStateController.kt`)

**Location**: `clear()` method (line 199)

**Problem**: Canceling pending writes during service shutdown could lose data.

**Fix**: Wait for pending writes to complete, then force final write:

```kotlin
if (currentState.hasMedia) {
    // Wait for any pending writes to complete first
    dbWriteJob?.join()
    // Then force one final write
    persistStateToDatabase(currentState, force = true)
}
```

### 4. Protected Network Sync (`BookRepository.kt`)

**Location**: `syncAudiobook()` method (line 496)

**Problem**: Network sync would use `tracks.getProgress()` which could be stale, overwriting recent local progress.

**Fix**: Compare timestamps and use the more recent progress:

```kotlin
// Preserve local progress unless forceNetwork is true
// tracks.getProgress() returns the first track's progress, which may be stale
// The local audiobook.progress is more reliable if we've been playing recently
val progressToUse = if (forceNetwork) {
    tracks.getProgress()
} else {
    // Use whichever is more recent: local or network
    // If local was viewed more recently, trust local progress
    if (audiobook.lastViewedAt >= networkBook.lastViewedAt) {
        audiobook.progress
    } else {
        tracks.getProgress()
    }
}
```

## Testing Strategy

### Test Case 1: Quick Book Switching
1. Start playing Book A
2. Within 3 seconds, switch to Book B
3. Return to Book A
4. **Expected**: Book A resumes at the position from step 1

### Test Case 2: Pause and Switch
1. Play Book A for 30 seconds
2. Pause
3. Switch to Book B
4. Return to Book A
5. **Expected**: Book A resumes at 30 seconds

### Test Case 3: Service Shutdown
1. Play Book A
2. Force-stop the app while playing
3. Restart app
4. **Expected**: Book A position is preserved

### Test Case 4: Network Sync
1. Play Book A to 5 minutes on Device 1
2. Sync to server
3. On Device 2, play Book A to 1 minute
4. Pull to refresh library (network sync)
5. **Expected**: Device 2 keeps local 1-minute position (more recent)

## Files Modified

1. `app/src/main/java/local/oss/chronicle/features/player/PlaybackStateController.kt`
   - `scheduleDatabaseWrite()` - Added book switch detection
   - `updatePlayingState()` - Enhanced pause handling
   - `clear()` - Wait for pending writes

2. `app/src/main/java/local/oss/chronicle/data/local/BookRepository.kt`
   - `syncAudiobook()` - Added timestamp-based progress protection

## Performance Impact

- **Minimal**: Only adds one extra immediate write when switching books
- **Benefit**: Eliminates data loss scenarios
- **Debounce still active**: Regular playback still benefits from 3-second debounce

## Backward Compatibility

- All changes are internal to persistence logic
- No API changes
- No database schema changes
- Existing data is preserved
