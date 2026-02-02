package local.oss.chronicle.features.collections.compose

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
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Collection Details screen.
 *
 * Features:
 * - Display collection title
 * - Grid of books in the collection
 * - Book progress indicators
 * - Navigation to book details
 */
@AndroidEntryPoint
class ComposeCollectionDetailsFragment : Fragment() {

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
                val viewModel: ComposeCollectionDetailsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()

                OpusTheme(darkTheme = true) {
                    CollectionDetailsScreen(
                        state = state,
                        onBackClick = {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        },
                        onBookClick = { book ->
                            navigator.showDetails(
                                audiobookId = book.id,
                                audiobookTitle = book.title,
                                isAudiobookCached = book.isDownloaded,
                            )
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "compose_collection_details"

        @JvmStatic
        fun newInstance(collectionId: Int): ComposeCollectionDetailsFragment {
            return ComposeCollectionDetailsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ComposeCollectionDetailsViewModel.ARG_COLLECTION_ID, collectionId)
                }
            }
        }
    }
}
