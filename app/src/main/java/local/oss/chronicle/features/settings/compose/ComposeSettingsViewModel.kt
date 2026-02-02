package local.oss.chronicle.features.settings.compose

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.oss.chronicle.BuildConfig
import local.oss.chronicle.R
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Compose-based Settings screen.
 *
 * Manages settings state and provides preference items for display.
 */
@HiltViewModel
class ComposeSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: PrefsRepo,
    private val plexPrefs: PlexPrefsRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Current option selection context
    private var currentOptionKey: String? = null
    private var currentOptionValues: List<Any> = emptyList()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Rebuild the preferences list when any preference changes
        rebuildPreferences()
    }

    init {
        prefsRepo.registerPrefsListener(prefsListener)
        rebuildPreferences()
    }

    override fun onCleared() {
        prefsRepo.unregisterPrefsListener(prefsListener)
    }

    private fun rebuildPreferences() {
        val preferences = buildPreferencesList()
        _uiState.update { it.copy(preferences = preferences) }
    }

    private fun buildPreferencesList(): List<SettingsItem> {
        val items = mutableListOf<SettingsItem>()

        // Appearance Section
        items.add(SettingsItem.Category(context.getString(R.string.settings_category_appearance)))
        items.add(
            SettingsItem.Clickable(
                id = "book_cover_style",
                title = context.getString(R.string.settings_book_cover_type_label),
                explanation = context.getString(R.string.settings_book_cover_type_explanation),
                currentValue = prefsRepo.bookCoverStyle,
                onClick = { showBookCoverStyleOptions() },
            )
        )

        // Sync Section
        items.add(SettingsItem.Category(context.getString(R.string.settings_category_sync)))
        items.add(
            SettingsItem.Clickable(
                id = "refresh_rate",
                title = context.getString(R.string.settings_refresh_rate_title),
                explanation = context.getString(R.string.settings_refresh_rate_explanation),
                currentValue = formatRefreshRate(prefsRepo.refreshRateMinutes),
                onClick = { showRefreshRateOptions() },
            )
        )
        items.add(
            SettingsItem.Switch(
                id = "offline_mode",
                title = context.getString(R.string.settings_offline_mode_title),
                isChecked = prefsRepo.offlineMode,
                onCheckedChange = { checked ->
                    prefsRepo.offlineMode = checked
                },
            )
        )

        // Playback Section
        items.add(SettingsItem.Category(context.getString(R.string.settings_category_playback)))
        items.add(
            SettingsItem.Switch(
                id = "skip_silence",
                title = context.getString(R.string.settings_skip_silent_audio),
                isChecked = prefsRepo.skipSilence,
                onCheckedChange = { checked ->
                    prefsRepo.skipSilence = checked
                },
            )
        )
        items.add(
            SettingsItem.Switch(
                id = "auto_rewind",
                title = context.getString(R.string.settings_auto_rewind),
                explanation = context.getString(R.string.settings_auto_rewind_explanation),
                isChecked = prefsRepo.autoRewind,
                onCheckedChange = { checked ->
                    prefsRepo.autoRewind = checked
                },
            )
        )
        items.add(
            SettingsItem.Switch(
                id = "shake_to_snooze",
                title = context.getString(R.string.settings_shake_to_snooze_title),
                explanation = context.getString(R.string.settings_shake_to_snooze_explanation),
                isChecked = prefsRepo.shakeToSnooze,
                onCheckedChange = { checked ->
                    prefsRepo.shakeToSnooze = checked
                },
            )
        )
        items.add(
            SettingsItem.Switch(
                id = "pause_on_focus_lost",
                title = context.getString(R.string.settings_pause_on_focus_lost_title),
                explanation = context.getString(R.string.settings_pause_on_focus_lost_explanation),
                isChecked = prefsRepo.pauseOnFocusLost,
                onCheckedChange = { checked ->
                    prefsRepo.pauseOnFocusLost = checked
                },
            )
        )
        items.add(
            SettingsItem.Switch(
                id = "auto_car_mode",
                title = context.getString(R.string.settings_auto_car_mode_title),
                explanation = context.getString(R.string.settings_auto_car_mode_explanation),
                isChecked = prefsRepo.autoEnterCarMode,
                onCheckedChange = { checked ->
                    prefsRepo.autoEnterCarMode = checked
                },
            )
        )
        items.add(
            SettingsItem.Clickable(
                id = "jump_forward",
                title = context.getString(R.string.settings_jump_forward_title),
                explanation = context.getString(R.string.settings_jump_forward_explanation),
                currentValue = "${prefsRepo.jumpForwardSeconds} ${context.getString(R.string.seconds)}",
                onClick = { showJumpForwardOptions() },
            )
        )
        items.add(
            SettingsItem.Clickable(
                id = "jump_backward",
                title = context.getString(R.string.settings_jump_backward_title),
                explanation = context.getString(R.string.settings_jump_backward_explanation),
                currentValue = "${prefsRepo.jumpBackwardSeconds} ${context.getString(R.string.seconds)}",
                onClick = { showJumpBackwardOptions() },
            )
        )

        // Account Section
        items.add(SettingsItem.Category(context.getString(R.string.settings_category_account)))
        items.add(
            SettingsItem.Clickable(
                id = "current_library",
                title = context.getString(R.string.settings_change_library),
                explanation = context.getString(
                    R.string.settings_current_library,
                    plexPrefs.library?.name ?: ""
                ),
                onClick = { onChangeLibraryClick() },
            )
        )
        items.add(
            SettingsItem.Clickable(
                id = "current_server",
                title = context.getString(R.string.settings_change_server),
                explanation = context.getString(
                    R.string.settings_current_server,
                    plexPrefs.server?.name ?: ""
                ),
                onClick = { onChangeServerClick() },
            )
        )

        // Misc Section
        items.add(SettingsItem.Category(context.getString(R.string.settings_category_etc)))
        items.add(
            SettingsItem.Clickable(
                id = "version",
                title = context.getString(R.string.settings_version_title),
                currentValue = BuildConfig.VERSION_NAME,
                onClick = { onVersionClick() },
            )
        )
        items.add(
            SettingsItem.Clickable(
                id = "licenses",
                title = context.getString(R.string.settings_licenses_title),
                explanation = context.getString(R.string.settings_licenses_explanation),
                onClick = { onLicensesClick() },
            )
        )

        // Debug options for debug builds
        if (BuildConfig.DEBUG) {
            items.add(SettingsItem.Category("Developer Options"))
            items.add(
                SettingsItem.Switch(
                    id = "debug_disable_progress",
                    title = "Disable local progress tracking",
                    explanation = "For debugging - prevents saving progress locally",
                    isChecked = prefsRepo.debugOnlyDisableLocalProgressTracking,
                    onCheckedChange = { checked ->
                        prefsRepo.debugOnlyDisableLocalProgressTracking = checked
                    },
                )
            )
        }

        return items
    }

    private fun formatRefreshRate(minutes: Long): String {
        return when {
            minutes == 0L -> context.getString(R.string.settings_refresh_rate_always)
            minutes < 60 -> "$minutes ${context.getString(R.string.minutes)}"
            minutes < 60 * 24 -> "${minutes / 60} ${context.getString(R.string.hours)}"
            minutes <= 60 * 24 * 7 -> "${minutes / (60 * 24)} ${context.getString(R.string.days)}"
            else -> context.getString(R.string.settings_refresh_rate_manual)
        }
    }

    // Option dialogs

    private fun showBookCoverStyleOptions() {
        currentOptionKey = "book_cover_style"
        val options = listOf(
            context.getString(R.string.settings_book_cover_type_square),
            context.getString(R.string.settings_book_cover_type_rect),
        )
        currentOptionValues = listOf("Square", "Rectangle")
        val selectedIndex = if (prefsRepo.bookCoverStyle == "Square") 0 else 1

        _uiState.update {
            it.copy(
                showOptionsSheet = true,
                optionsTitle = context.getString(R.string.settings_book_cover_type_label),
                options = options,
                selectedOptionIndex = selectedIndex,
            )
        }
    }

    private fun showRefreshRateOptions() {
        currentOptionKey = "refresh_rate"
        val options = listOf(
            context.getString(R.string.settings_refresh_rate_always),
            context.getString(R.string.settings_refresh_rate_15_minutes),
            context.getString(R.string.settings_refresh_rate_1_hour),
            context.getString(R.string.settings_refresh_rate_3_hours),
            context.getString(R.string.settings_refresh_rate_6_hours),
            context.getString(R.string.settings_refresh_rate_1_day),
            context.getString(R.string.settings_refresh_rate_3_days),
            context.getString(R.string.settings_refresh_rate_1_week),
            context.getString(R.string.settings_refresh_rate_manual),
        )
        currentOptionValues = listOf(0L, 15L, 60L, 180L, 360L, 60 * 24L, 60 * 24 * 3L, 60 * 24 * 7L, Long.MAX_VALUE)

        val currentValue = prefsRepo.refreshRateMinutes
        val selectedIndex = currentOptionValues.indexOfFirst { (it as Long) == currentValue }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                showOptionsSheet = true,
                optionsTitle = context.getString(R.string.settings_refresh_rate_title),
                options = options,
                selectedOptionIndex = selectedIndex,
            )
        }
    }

    private fun showJumpForwardOptions() {
        currentOptionKey = "jump_forward"
        val options = listOf(
            context.getString(R.string.settings_jump_10_seconds),
            context.getString(R.string.settings_jump_15_seconds),
            context.getString(R.string.settings_jump_20_seconds),
            context.getString(R.string.settings_jump_30_seconds),
            context.getString(R.string.settings_jump_60_seconds),
            context.getString(R.string.settings_jump_90_seconds),
        )
        currentOptionValues = listOf(10L, 15L, 20L, 30L, 60L, 90L)

        val currentValue = prefsRepo.jumpForwardSeconds
        val selectedIndex = currentOptionValues.indexOfFirst { (it as Long) == currentValue }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                showOptionsSheet = true,
                optionsTitle = context.getString(R.string.settings_jump_forward_title),
                options = options,
                selectedOptionIndex = selectedIndex,
            )
        }
    }

    private fun showJumpBackwardOptions() {
        currentOptionKey = "jump_backward"
        val options = listOf(
            context.getString(R.string.settings_jump_10_seconds),
            context.getString(R.string.settings_jump_15_seconds),
            context.getString(R.string.settings_jump_20_seconds),
            context.getString(R.string.settings_jump_30_seconds),
            context.getString(R.string.settings_jump_60_seconds),
            context.getString(R.string.settings_jump_90_seconds),
        )
        currentOptionValues = listOf(10L, 15L, 20L, 30L, 60L, 90L)

        val currentValue = prefsRepo.jumpBackwardSeconds
        val selectedIndex = currentOptionValues.indexOfFirst { (it as Long) == currentValue }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                showOptionsSheet = true,
                optionsTitle = context.getString(R.string.settings_jump_backward_title),
                options = options,
                selectedOptionIndex = selectedIndex,
            )
        }
    }

    fun dismissOptionsSheet() {
        _uiState.update {
            it.copy(
                showOptionsSheet = false,
                optionsTitle = "",
                options = emptyList(),
                selectedOptionIndex = -1,
            )
        }
        currentOptionKey = null
        currentOptionValues = emptyList()
    }

    fun onOptionSelected(index: Int) {
        viewModelScope.launch {
            when (currentOptionKey) {
                "book_cover_style" -> {
                    val value = currentOptionValues.getOrNull(index) as? String ?: return@launch
                    prefsRepo.bookCoverStyle = value
                }
                "refresh_rate" -> {
                    val value = currentOptionValues.getOrNull(index) as? Long ?: return@launch
                    prefsRepo.refreshRateMinutes = value
                }
                "jump_forward" -> {
                    val value = currentOptionValues.getOrNull(index) as? Long ?: return@launch
                    prefsRepo.jumpForwardSeconds = value
                }
                "jump_backward" -> {
                    val value = currentOptionValues.getOrNull(index) as? Long ?: return@launch
                    prefsRepo.jumpBackwardSeconds = value
                }
            }
            dismissOptionsSheet()
        }
    }

    // Navigation callbacks - these will be handled by the Fragment
    private var onNavigateToLibraryChooser: (() -> Unit)? = null
    private var onNavigateToServerChooser: (() -> Unit)? = null
    private var onNavigateToLicenses: (() -> Unit)? = null
    private var onVersionTapped: (() -> Unit)? = null

    fun setNavigationCallbacks(
        onLibraryChooser: () -> Unit,
        onServerChooser: () -> Unit,
        onLicenses: () -> Unit,
        onVersion: () -> Unit,
    ) {
        onNavigateToLibraryChooser = onLibraryChooser
        onNavigateToServerChooser = onServerChooser
        onNavigateToLicenses = onLicenses
        onVersionTapped = onVersion
    }

    private fun onChangeLibraryClick() {
        Timber.i("Change library clicked")
        onNavigateToLibraryChooser?.invoke()
    }

    private fun onChangeServerClick() {
        Timber.i("Change server clicked")
        onNavigateToServerChooser?.invoke()
    }

    private fun onLicensesClick() {
        Timber.i("Licenses clicked")
        onNavigateToLicenses?.invoke()
    }

    private fun onVersionClick() {
        Timber.i("Version clicked")
        onVersionTapped?.invoke()
    }
}
