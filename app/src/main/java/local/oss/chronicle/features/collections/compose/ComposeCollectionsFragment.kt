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
 * Fragment hosting the Compose-based Collections screen.
 *
 * Features:
 * - Grid display of all collections
 * - Search functionality
 * - Pull-to-refresh
 * - Navigation to collection details
 */
@AndroidEntryPoint
class ComposeCollectionsFragment : Fragment() {

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
                val viewModel: ComposeCollectionsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()

                OpusTheme(darkTheme = true) {
                    CollectionsScreen(
                        state = state,
                        onCollectionClick = { collection ->
                            navigator.showCollectionDetails(collection.id)
                        },
                        onSearchQueryChange = { query ->
                            viewModel.setSearchQuery(query)
                        },
                        onSearchActiveChange = { active ->
                            viewModel.setSearchActive(active)
                        },
                        onRefresh = {
                            viewModel.refresh()
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "compose_collections"

        @JvmStatic
        fun newInstance() = ComposeCollectionsFragment()
    }
}
