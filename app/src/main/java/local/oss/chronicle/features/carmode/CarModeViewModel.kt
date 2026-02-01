package local.oss.chronicle.features.carmode

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.Audiobook
import local.oss.chronicle.data.model.Chapter
import local.oss.chronicle.data.model.EMPTY_AUDIOBOOK
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.SKIP_BACKWARDS_STRING
import local.oss.chronicle.features.player.SKIP_FORWARDS_STRING
import local.oss.chronicle.features.player.SleepTimer
import local.oss.chronicle.features.player.SleepTimer.SleepTimerAction
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Car Mode screen.
 *
 * Manages:
 * - Playback state for simplified car mode UI
 * - Bluetooth car audio detection for auto-triggering car mode
 * - Sleep timer disable while in car mode
 */
@HiltViewModel
class CarModeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaServiceConnection: MediaServiceConnection,
    private val currentlyPlaying: CurrentlyPlaying,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig,
    private val localBroadcastManager: LocalBroadcastManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarModeUiState())
    val uiState: StateFlow<CarModeUiState> = _uiState.asStateFlow()

    private val _isCarModeActive = MutableStateFlow(false)
    val isCarModeActive: StateFlow<Boolean> = _isCarModeActive.asStateFlow()

    // Track sleep timer state
    private var sleepTimerWasActive = false
    private var previousSleepTimerDuration = 0L
    private var currentSleepTimerRemainingMs = 0L

    private val sleepTimerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val action = IntentCompat.getSerializableExtra(
                intent,
                SleepTimer.ARG_SLEEP_TIMER_ACTION,
                SleepTimerAction::class.java
            )
            val durationMillis = intent.getLongExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, 0L)

            when (action) {
                SleepTimerAction.BEGIN, SleepTimerAction.EXTEND, SleepTimerAction.UPDATE -> {
                    currentSleepTimerRemainingMs = durationMillis
                }
                SleepTimerAction.CANCEL -> {
                    currentSleepTimerRemainingMs = 0L
                }
                null -> {}
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    handleBluetoothDeviceConnected(device)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    handleBluetoothDeviceDisconnected(device)
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Audio is becoming noisy (headphones unplugged, Bluetooth disconnected)
                    // This is a good signal that car mode should end
                    if (_isCarModeActive.value && _uiState.value.isCarModeAutoDetected) {
                        Timber.i("Car mode: Audio becoming noisy, considering exit")
                    }
                }
            }
        }
    }

    init {
        observePlaybackState()
        registerBluetoothReceiver()
        registerSleepTimerReceiver()
    }

    private fun registerSleepTimerReceiver() {
        localBroadcastManager.registerReceiver(
            sleepTimerReceiver,
            IntentFilter(SleepTimer.ACTION_SLEEP_TIMER_CHANGE)
        )
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            currentlyPlaying.book.collect { book: Audiobook ->
                updateBookInfo(book)
            }
        }

        viewModelScope.launch {
            currentlyPlaying.chapter.collect { chapter: Chapter ->
                _uiState.update { it.copy(chapterTitle = chapter.title) }
            }
        }

        // Use MediaServiceConnection's playbackState for both isPlaying and position
        mediaServiceConnection.playbackState.observeForever { state ->
            val isPlaying = state?.state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
            val absolutePosition = state?.position ?: 0L

            // Convert to chapter-relative position
            val chapter = currentlyPlaying.chapter.value
            val chapterRelativePosition = (absolutePosition - chapter.startTimeOffset)
                .coerceAtLeast(0L)

            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    currentPositionMs = chapterRelativePosition,
                )
            }
        }
    }

    private fun updateBookInfo(book: Audiobook) {
        if (book == EMPTY_AUDIOBOOK) return

        val coverUrl = book.thumb?.takeIf { it.isNotEmpty() }?.let {
            plexConfig.makeThumbUri(it).toString()
        }

        _uiState.update { state ->
            state.copy(
                bookTitle = book.title,
                author = book.author,
                coverArtUrl = coverUrl,
                durationMs = book.duration,
            )
        }
    }

    private fun registerBluetoothReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register Bluetooth receiver")
        }
    }

    private fun handleBluetoothDeviceConnected(device: BluetoothDevice?) {
        if (device == null) return

        try {
            if (isCarAudioDevice(device)) {
                Timber.i("Car mode: Car audio device connected - ${device.name}")
                if (prefsRepo.autoEnterCarMode) {
                    enterCarMode(autoDetected = true)
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException checking Bluetooth device")
        }
    }

    private fun handleBluetoothDeviceDisconnected(device: BluetoothDevice?) {
        if (device == null) return

        try {
            if (isCarAudioDevice(device)) {
                Timber.i("Car mode: Car audio device disconnected - ${device.name}")
                if (_isCarModeActive.value && _uiState.value.isCarModeAutoDetected) {
                    exitCarMode()
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException checking Bluetooth device")
        }
    }

    /**
     * Check if a Bluetooth device is likely a car audio system.
     * This uses device class to identify car audio devices.
     */
    private fun isCarAudioDevice(device: BluetoothDevice): Boolean {
        return try {
            val deviceClass = device.bluetoothClass
            val majorClass = deviceClass?.majorDeviceClass
            val deviceType = deviceClass?.deviceClass

            // Check for car audio major class
            if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                // Check for specific car audio types
                when (deviceType) {
                    BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                    BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> true
                    else -> {
                        // Fallback: check if device name suggests car audio
                        val name = device.name?.lowercase() ?: ""
                        name.contains("car") ||
                            name.contains("auto") ||
                            name.contains("vehicle") ||
                            name.contains("sync") ||
                            name.contains("carplay") ||
                            name.contains("android auto")
                    }
                }
            } else {
                false
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException accessing Bluetooth device info")
            false
        }
    }

    /**
     * Enter car mode.
     * Disables sleep timer while in car mode.
     */
    fun enterCarMode(autoDetected: Boolean = false) {
        if (_isCarModeActive.value) return

        Timber.i("Car mode: Entering (autoDetected=$autoDetected)")
        _isCarModeActive.value = true
        _uiState.update { it.copy(isCarModeAutoDetected = autoDetected) }

        // Save and disable sleep timer
        if (currentSleepTimerRemainingMs > 0) {
            sleepTimerWasActive = true
            previousSleepTimerDuration = currentSleepTimerRemainingMs
            cancelSleepTimer()
            Timber.i("Car mode: Disabled sleep timer (was $previousSleepTimerDuration ms remaining)")
        }
    }

    /**
     * Exit car mode.
     * Restores sleep timer if it was active before entering car mode.
     */
    fun exitCarMode() {
        if (!_isCarModeActive.value) return

        Timber.i("Car mode: Exiting")
        _isCarModeActive.value = false
        _uiState.update { it.copy(isCarModeAutoDetected = false) }

        // Restore sleep timer if it was active
        if (sleepTimerWasActive && previousSleepTimerDuration > 0) {
            startSleepTimer(previousSleepTimerDuration)
            Timber.i("Car mode: Restored sleep timer with $previousSleepTimerDuration ms")
        }
        sleepTimerWasActive = false
        previousSleepTimerDuration = 0L
    }

    private fun cancelSleepTimer() {
        val intent = Intent(SleepTimer.ACTION_SLEEP_TIMER_CHANGE).apply {
            putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.CANCEL)
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun startSleepTimer(durationMs: Long) {
        val intent = Intent(SleepTimer.ACTION_SLEEP_TIMER_CHANGE).apply {
            putExtra(SleepTimer.ARG_SLEEP_TIMER_ACTION, SleepTimerAction.BEGIN)
            putExtra(SleepTimer.ARG_SLEEP_TIMER_DURATION_MILLIS, durationMs)
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    // Playback controls

    fun playPause() {
        val transportControls = mediaServiceConnection.transportControls ?: return
        val isPlaying = _uiState.value.isPlaying

        if (isPlaying) {
            transportControls.pause()
        } else {
            transportControls.play()
        }
    }

    fun skipForward() {
        val transportControls = mediaServiceConnection.transportControls ?: return
        transportControls.sendCustomAction(SKIP_FORWARDS_STRING, Bundle.EMPTY)
    }

    fun skipBackward() {
        val transportControls = mediaServiceConnection.transportControls ?: return
        transportControls.sendCustomAction(SKIP_BACKWARDS_STRING, Bundle.EMPTY)
    }

    fun skipToNext() {
        mediaServiceConnection.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        mediaServiceConnection.transportControls?.skipToPrevious()
    }

    override fun onCleared() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister Bluetooth receiver")
        }
        try {
            localBroadcastManager.unregisterReceiver(sleepTimerReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister sleep timer receiver")
        }
        super.onCleared()
    }
}
