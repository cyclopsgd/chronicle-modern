package local.oss.chronicle.features.home.compose

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
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Home screen.
 *
 * Features:
 * - Featured book with play button
 * - Continue listening section
 * - Recently added section
 * - Downloaded books section
 * - Collections section
 * - Pull-to-refresh
 */
@AndroidEntryPoint
class ComposeHomeFragment : Fragment() {

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val viewModel: ComposeHomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()

                OpusTheme(darkTheme = true) {
                    HomeScreen(
                        state = state,
                        onBookClick = { book ->
                            navigator.showDetails(
                                audiobookId = book.id,
                                audiobookTitle = book.title,
                                isAudiobookCached = book.isDownloaded
                            )
                        },
                        onCollectionClick = { collection ->
                            navigator.showCollectionDetails(collection.id)
                        },
                        onSeeAllContinueListening = {
                            navigator.showLibraryWithFilter("in_progress")
                        },
                        onSeeAllRecentlyAdded = {
                            navigator.showLibraryWithFilter("recently_added")
                        },
                        onSeeAllDownloaded = {
                            navigator.showLibraryWithFilter("downloaded")
                        },
                        onSeeAllCollections = {
                            navigator.showCollections()
                        },
                        onRefresh = {
                            viewModel.refresh()
                        },
                        onPlayFeatured = {
                            state.featuredBook?.let { book ->
                                playBook(book.id)
                            }
                        },
                    )
                }
            }
        }
    }

    private fun playBook(bookId: Int) {
        // Connect to media service and play the book
        if (mediaServiceConnection.isConnected.value == true) {
            mediaServiceConnection.transportControls?.playFromMediaId(bookId.toString(), null)
        } else {
            mediaServiceConnection.connect {
                mediaServiceConnection.transportControls?.playFromMediaId(bookId.toString(), null)
            }
        }
    }

    companion object {
        const val TAG = "compose_home"

        @JvmStatic
        fun newInstance() = ComposeHomeFragment()
    }
}
