package local.oss.chronicle.features.bookdetails.compose

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
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Book Details screen.
 *
 * Features:
 * - Book header with cover, title, author, progress
 * - Play/Pause button
 * - Download button with cache status
 * - Expandable summary section
 * - Chapters list with current chapter highlighting
 * - Connection status banner
 * - Overflow menu with sync and mark as played options
 */
@AndroidEntryPoint
class ComposeBookDetailsFragment : Fragment() {

    companion object {
        const val TAG = "compose_book_details"
        const val ARG_AUDIOBOOK_ID = "audiobook_id"
        const val ARG_AUDIOBOOK_TITLE = "ARG_AUDIOBOOK_TITLE"
        const val ARG_IS_AUDIOBOOK_CACHED = "is_audiobook_cached"

        @JvmStatic
        fun newInstance(
            audiobookId: Int,
            audiobookTitle: String,
            isAudiobookCached: Boolean,
        ): ComposeBookDetailsFragment {
            return ComposeBookDetailsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_AUDIOBOOK_ID, audiobookId)
                    putString(ARG_AUDIOBOOK_TITLE, audiobookTitle)
                    putBoolean(ARG_IS_AUDIOBOOK_CACHED, isAudiobookCached)
                }
            }
        }
    }

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val bookId = requireArguments().getInt(ARG_AUDIOBOOK_ID)
        val bookTitle = requireArguments().getString(ARG_AUDIOBOOK_TITLE) ?: ""
        val isCached = requireArguments().getBoolean(ARG_IS_AUDIOBOOK_CACHED)

        Timber.i("ComposeBookDetailsFragment onCreateView() for book: $bookId - $bookTitle")

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val viewModel: ComposeBookDetailsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()

                // Initialize the ViewModel with book data
                LaunchedEffect(bookId) {
                    viewModel.loadBook(bookId, bookTitle, isCached)
                }

                OpusTheme(darkTheme = true) {
                    BookDetailsScreen(
                        state = state,
                        onBackClick = {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        },
                        onPlayPause = {
                            viewModel.playPause()
                        },
                        onCacheClick = {
                            viewModel.onCacheButtonClick()
                        },
                        onChapterClick = { chapter ->
                            Timber.i("Jumping to chapter: ${chapter.title}")
                            viewModel.jumpToChapter(chapter)
                        },
                        onToggleSummary = {
                            viewModel.toggleSummary()
                        },
                        onToggleWatched = {
                            viewModel.toggleWatched()
                        },
                        onSyncClick = {
                            viewModel.forceSyncBook()
                        },
                        onRetryConnection = {
                            viewModel.retryConnection()
                        },
                        onMessageShown = {
                            viewModel.clearUserMessage()
                        },
                    )
                }
            }
        }
    }
}
