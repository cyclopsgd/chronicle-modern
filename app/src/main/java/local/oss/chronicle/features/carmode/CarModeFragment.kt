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

}
