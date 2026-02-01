package local.oss.chronicle.features.currentlyplaying

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
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
import local.oss.chronicle.features.nowplaying.NowPlayingViewModel.NowPlayingEvent
import local.oss.chronicle.ui.theme.OpusTheme
import local.oss.chronicle.views.ModalBottomSheetSpeedChooser

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

                // Handle one-time events from ViewModel
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is NowPlayingEvent.ShowChapterList -> {
                                // TODO: Implement chapter list bottom sheet
                                Toast.makeText(context, "Chapter list coming soon", Toast.LENGTH_SHORT).show()
                            }
                            is NowPlayingEvent.ShowSpeedSelector -> {
                                ModalBottomSheetSpeedChooser().show(
                                    childFragmentManager,
                                    ModalBottomSheetSpeedChooser.TAG,
                                )
                            }
                            is NowPlayingEvent.ShowSleepTimerOptions -> {
                                // TODO: Implement sleep timer options bottom sheet
                                Toast.makeText(context, "Sleep timer options coming soon", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

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
                        onSpeedClick = {
                            // Show the existing speed chooser bottom sheet
                            ModalBottomSheetSpeedChooser().show(
                                childFragmentManager,
                                ModalBottomSheetSpeedChooser.TAG,
                            )
                        },
                        onSleepTimerClick = viewModel::showSleepTimerOptions,
                        onBookmarkClick = viewModel::toggleBookmark,
                        onChapterClick = viewModel::showChapterList,
                    )
                }
            }
        }
    }
}
