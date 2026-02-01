package local.oss.chronicle.features.library.compose

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
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.theme.OpusTheme
import javax.inject.Inject

/**
 * Fragment hosting the Compose-based Library screen.
 *
 * This fragment serves as a bridge between the existing navigation system
 * and the new Compose UI.
 */
@AndroidEntryPoint
@ExperimentalCoroutinesApi
class ComposeLibraryFragment : Fragment() {

    @Inject
    lateinit var navigator: Navigator

    companion object {
        fun newInstance() = ComposeLibraryFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val viewModel: ComposeLibraryViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                OpusTheme(darkTheme = true) {
                    LibraryScreen(
                        state = uiState,
                        onBookClick = { book ->
                            // Navigate to book details using the existing navigator
                            navigator.showDetails(
                                audiobookId = book.id,
                                audiobookTitle = book.title,
                                isAudiobookCached = book.isDownloaded,
                            )
                        },
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onSearchActiveChange = viewModel::setSearchActive,
                        onRefresh = viewModel::refresh,
                        onFilterClick = viewModel::showFilterSheet,
                        onDismissFilter = viewModel::hideFilterSheet,
                        onSortKeyChange = viewModel::setSortKey,
                        onSortDirectionToggle = viewModel::toggleSortDirection,
                        onProgressFilterChange = viewModel::setProgressFilter,
                    )
                }
            }
        }
    }
}
