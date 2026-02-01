package local.oss.chronicle.features.currentlyplaying

import android.content.Context
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import local.oss.chronicle.application.MainActivity
import local.oss.chronicle.application.MainActivityViewModel.BottomSheetState.COLLAPSED
import local.oss.chronicle.features.nowplaying.NowPlayingScreen
import local.oss.chronicle.features.nowplaying.NowPlayingViewModel
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Now Playing screen.
 *
 * This fragment serves as a bridge between the existing navigation/bottom sheet
 * system and the new Compose UI. It delegates all UI rendering to NowPlayingScreen
 * and uses NowPlayingViewModel for state management.
 */
@AndroidEntryPoint
@ExperimentalCoroutinesApi
class CurrentlyPlayingFragment : Fragment() {
    private lateinit var currentlyPlayingInterface: MainActivity.CurrentlyPlayingInterface

    @Inject
    lateinit var navigator: Navigator

    companion object {
        fun newInstance() = CurrentlyPlayingFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentlyPlayingInterface = (context as MainActivity).getCurrentlyPlayingInterface()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose composition when fragment's view is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                // Use Hilt's hiltViewModel() to get the ViewModel scoped to the fragment
                val viewModel: NowPlayingViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                OpusTheme(darkTheme = true) {
                    NowPlayingScreen(
                        state = uiState,
                        onNavigateBack = {
                            currentlyPlayingInterface.setBottomSheetState(COLLAPSED)
                        },
                        onPlayPause = viewModel::playPause,
                        onSkipForward = viewModel::skipForward,
                        onSkipBackward = viewModel::skipBackward,
                        onSkipToNext = viewModel::skipToNext,
                        onSkipToPrevious = viewModel::skipToPrevious,
                        onSeekTo = viewModel::seekTo,
                        onSpeedClick = viewModel::showSpeedSelector,
                        onSpeedSelected = viewModel::setPlaybackSpeed,
                        onDismissSpeedSelector = viewModel::hideSpeedSelector,
                        onSleepTimerClick = viewModel::showSleepTimerOptions,
                        onBookmarkClick = viewModel::toggleBookmark,
                        onChapterClick = viewModel::showChapterList,
                        onChapterSelected = viewModel::jumpToChapter,
                        onDismissChapterList = viewModel::hideChapterList,
                        onSleepTimerSelected = viewModel::handleSleepTimerOption,
                        onDismissSleepTimer = viewModel::hideSleepTimer,
                        onCarModeClick = {
                            currentlyPlayingInterface.setBottomSheetState(COLLAPSED)
                            navigator.showCarMode()
                        },
                    )
                }
            }
        }
    }
}
