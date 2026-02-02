package local.oss.chronicle.application

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.oss.chronicle.R
import local.oss.chronicle.application.MainActivityViewModel.BottomSheetState.COLLAPSED
import local.oss.chronicle.application.MainActivityViewModel.BottomSheetState.EXPANDED
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.model.EMPTY_AUDIOBOOK
import local.oss.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.LOGGED_IN_FULLY
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.databinding.ActivityMainBinding
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import local.oss.chronicle.features.player.MediaPlayerService.Companion.ACTION_PLAYBACK_ERROR
import local.oss.chronicle.features.player.MediaPlayerService.Companion.PLAYBACK_ERROR_MESSAGE
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.navigation.Navigator
import local.oss.chronicle.ui.components.MainNavigationBar
import local.oss.chronicle.ui.components.MainTab
import local.oss.chronicle.ui.components.MiniPlayerContent
import local.oss.chronicle.ui.theme.OpusTheme
import local.oss.chronicle.util.observeEvent
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var localBroadcastManager: LocalBroadcastManager

    @Inject
    lateinit var mainActivityViewModelFactory: MainActivityViewModel.Factory

    private val viewModel: MainActivityViewModel by lazy {
        ViewModelProvider(this, mainActivityViewModelFactory).get(MainActivityViewModel::class.java)
    }

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var plexPrefsRepo: PlexPrefsRepo

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("MainActivity onCreate()")
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for MD3
        WindowCompat.setDecorFitsSystemWindows(window, false)

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        val binding =
            DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.plexConfig = plexConfig

        // Set up Compose-based bottom navigation bar
        binding.bottomNav.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentTab by viewModel.currentTab.collectAsState()
                val hasCollections by viewModel.hasCollections.observeAsState(initial = true)

                OpusTheme(darkTheme = true) {
                    MainNavigationBar(
                        currentTab = currentTab,
                        showCollections = hasCollections,
                        onTabSelect = { tab ->
                            viewModel.setCurrentTab(tab)
                            viewModel.minimizeCurrentlyPlaying()
                            when (tab) {
                                MainTab.HOME -> navigator.showHome()
                                MainTab.LIBRARY -> navigator.showLibrary()
                                MainTab.COLLECTIONS -> navigator.showCollections()
                                MainTab.SETTINGS -> navigator.showSettings()
                            }
                        },
                    )
                }
            }
        }

        // Set up Compose-based mini player
        binding.currentlyPlayingHandle.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val miniPlayerState by viewModel.miniPlayerState.collectAsState()

                OpusTheme(darkTheme = true) {
                    MiniPlayerContent(
                        state = miniPlayerState,
                        onPlayPause = { viewModel.pausePlayButtonClicked() },
                        onClick = { viewModel.onCurrentlyPlayingClicked() },
                    )
                }
            }
        }

        viewModel.errorMessage.observeEvent(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }

        if (savedInstanceState == null) {
            setupCurrentlyPlaying()
            // Re-post a fresh login state event for this new activity instance
            plexLoginRepo.determineLoginState()
            plexLoginRepo.loginEvent.value?.let {
                if (it.peekContent() == LOGGED_IN_FULLY) {
                    navigator.showHome()
                }
            }
        }

        // Handle back button press with modern OnBackPressedCallback
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // If currently playing view is over fragments, close it via back button
                    if (viewModel.currentlyPlayingLayoutState.value == EXPANDED) {
                        viewModel.setBottomSheetState(COLLAPSED)
                        return
                    }
                    // default to activity back stack if navigator did not handle anything
                    if (!navigator.onBackPressed()) {
                        Timber.i("MainActivity handleOnBackPressed()")
                        if (supportFragmentManager.backStackEntryCount == 0) {
                            // At base fragment (home screen) - navigate to home instead of exiting
                            // This prevents accidentally closing the app
                            navigator.showHome()
                        } else {
                            // Let the system handle the back press
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            },
        )

        // If the app is being launched by voice assistant with a query
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (!query.isNullOrEmpty()) {
            mediaServiceConnection.connect {
                mediaServiceConnection.transportControls?.playFromSearch(query, Bundle())
            }
        }

        handleNotificationIntent(intent)
    }

    private fun setupCurrentlyPlaying() {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(
            R.id.currently_playing_fragment_container,
            CurrentlyPlayingFragment.newInstance(),
        )
        transaction.commit()
        // Note: Mini player tap handling is done via Compose onClick
        // Swipe-up gesture could be added to MiniPlayer composable if needed
    }

    interface CurrentlyPlayingInterface {
        fun setBottomSheetState(state: MainActivityViewModel.BottomSheetState)
        fun setCarModeActive(active: Boolean)
    }

    fun getCurrentlyPlayingInterface(): CurrentlyPlayingInterface {
        return viewModel
    }

    override fun onStart() {
        super.onStart()
        Timber.i("MainActivity onStart()")
        localBroadcastManager.registerReceiver(onPlaybackError, IntentFilter(ACTION_PLAYBACK_ERROR))
    }

    override fun onStop() {
        Timber.i("MainActivity onStop()")
        localBroadcastManager.unregisterReceiver(onPlaybackError)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val openCurrentlyPlaying =
            intent?.extras?.getBoolean(
                FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, false,
            ) == true
        if (openCurrentlyPlaying) {
            viewModel.maximizeCurrentlyPlaying()
        }

        val openAudiobookWithId =
            intent?.extras?.getInt(
                FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID, NO_AUDIOBOOK_FOUND_ID,
            ) ?: NO_AUDIOBOOK_FOUND_ID
        if (openAudiobookWithId != NO_AUDIOBOOK_FOUND_ID) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val audiobook = bookRepository.getAudiobookAsync(openAudiobookWithId)
                    if (audiobook != null && audiobook != EMPTY_AUDIOBOOK) {
                        navigator.showDetails(audiobook.id, audiobook.title, audiobook.isCached)
                    }
                }
            }
        }
    }

    private val onPlaybackError =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PLAYBACK_ERROR -> {
                        val errorMessage =
                            intent.getStringExtra(PLAYBACK_ERROR_MESSAGE)
                                ?: getString(R.string.playback_error_unknown)
                        val userMessage =
                            when {
                                errorMessage.contains(
                                    "404",
                                ) -> getString(R.string.playback_error_404)
                                errorMessage.contains(
                                    "503",
                                ) -> getString(R.string.playback_error_503)
                                errorMessage.contains(
                                    "401",
                                ) -> getString(R.string.playback_error_401)
                                else -> errorMessage
                            }
                        viewModel.showUserMessage(userMessage)
                    }
                    else -> throw NoWhenBranchMatchedException(
                        getString(R.string.playback_error_unknown),
                    )
                }
            }
        }

    companion object {
        const val FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING = "OPEN_ACTIVITY_TO_AUDIOBOOK"
        const val REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING = -12
        const val FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID = "OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID"

        // add audiobook id to this number to avoid repeats
        const val REQUEST_CODE_PREFIX_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID = -1001110
    }
}
