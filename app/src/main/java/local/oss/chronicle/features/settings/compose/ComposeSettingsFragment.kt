package local.oss.chronicle.features.settings.compose

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import dagger.hilt.android.AndroidEntryPoint
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Settings screen.
 *
 * Features:
 * - Settings categories (Appearance, Sync, Playback, Account, etc.)
 * - Toggle switches for boolean settings
 * - Clickable items with option selection bottom sheets
 * - Navigation to other screens (library chooser, server chooser, licenses)
 */
@AndroidEntryPoint
class ComposeSettingsFragment : Fragment() {

    @Inject
    lateinit var navigator: Navigator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val viewModel: ComposeSettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()

                // Set up navigation callbacks
                viewModel.setNavigationCallbacks(
                    onLibraryChooser = {
                        navigateToLibraryChooser()
                    },
                    onServerChooser = {
                        navigateToServerChooser()
                    },
                    onLicenses = {
                        showLicensesActivity()
                    },
                    onVersion = {
                        onVersionTapped()
                    },
                )

                OpusTheme(darkTheme = true) {
                    SettingsScreen(
                        state = state,
                        onDismissOptionsSheet = {
                            viewModel.dismissOptionsSheet()
                        },
                        onOptionSelected = { index ->
                            viewModel.onOptionSelected(index)
                        },
                    )
                }
            }
        }
    }

    private fun navigateToLibraryChooser() {
        Timber.i("Navigating to library chooser")
        // This would typically navigate to the library chooser screen
        // For now, log the action - actual navigation depends on app's navigation setup
        navigator.showLibraryChooser()
    }

    private fun navigateToServerChooser() {
        Timber.i("Navigating to server chooser")
        // This would typically navigate to the server chooser screen
        // For now, log the action - actual navigation depends on app's navigation setup
        navigator.showServerChooser()
    }

    private fun showLicensesActivity() {
        Timber.i("Showing licenses activity")
        try {
            startActivity(Intent(requireContext(), OssLicensesMenuActivity::class.java))
        } catch (e: Exception) {
            Timber.e(e, "Failed to show licenses activity")
        }
    }

    private fun onVersionTapped() {
        Timber.i("Version tapped")
        // Could implement easter egg logic here similar to original SettingsViewModel
    }

    companion object {
        const val TAG = "compose_settings"

        @JvmStatic
        fun newInstance() = ComposeSettingsFragment()
    }
}
