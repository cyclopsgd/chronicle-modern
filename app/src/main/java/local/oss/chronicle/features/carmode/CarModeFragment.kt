package local.oss.chronicle.features.carmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import local.oss.chronicle.application.MainActivity
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Car Mode screen.
 *
 * This fragment provides a simplified, high-contrast playback interface
 * optimized for use while driving.
 */
@AndroidEntryPoint
class CarModeFragment : Fragment() {

    @Inject
    lateinit var navigator: Navigator

    companion object {
        fun newInstance() = CarModeFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Enable full-screen car mode (hide bottom nav and mini player)
        (activity as? MainActivity)?.getCurrentlyPlayingInterface()?.setCarModeActive(true)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val viewModel: CarModeViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                // Enter car mode when the screen is displayed (manual entry)
                LaunchedEffect(Unit) {
                    viewModel.enterCarMode(autoDetected = false)
                }

                OpusTheme(darkTheme = true) {
                    CarModeScreen(
                        state = uiState,
                        onExitCarMode = {
                            viewModel.exitCarMode()
                            // Disable full-screen car mode before navigating back
                            (activity as? MainActivity)?.getCurrentlyPlayingInterface()?.setCarModeActive(false)
                            navigator.goBack()
                        },
                        onPlayPause = viewModel::playPause,
                        onSkipForward = viewModel::skipForward,
                        onSkipBackward = viewModel::skipBackward,
                        onSkipToNext = viewModel::skipToNext,
                        onSkipToPrevious = viewModel::skipToPrevious,
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        // Ensure car mode is disabled when the fragment is destroyed
        // (e.g., when user presses back button or navigates away)
        (activity as? MainActivity)?.getCurrentlyPlayingInterface()?.setCarModeActive(false)
        super.onDestroyView()
    }
}
