package local.oss.chronicle.injection.modules

import android.content.ComponentName
import android.content.Context
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.media3.Media3PlayerService
import local.oss.chronicle.util.ServiceUtils
import timber.log.Timber

/**
 * Provides dependencies at ActivityRetainedComponent scope.
 *
 * This scope survives configuration changes (like rotation) and is accessible
 * by both ViewModels (ViewModelComponent) and Activities (ActivityComponent).
 *
 * Use this for dependencies that:
 * 1. Need to be shared between ViewModels and Activities
 * 2. Should survive configuration changes
 * 3. Are tied to the activity lifecycle (but not configuration changes)
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
object ActivityRetainedModule {

    @Provides
    @ActivityRetainedScoped
    fun provideLocalBroadcastManager(@ApplicationContext context: Context): LocalBroadcastManager =
        LocalBroadcastManager.getInstance(context)

    /**
     * Provides MediaServiceConnection that connects to Media3PlayerService.
     *
     * Media3's MediaLibraryService is backwards compatible with legacy MediaBrowserCompat
     * clients, so existing ViewModels continue to work without changes.
     */
    @Provides
    @ActivityRetainedScoped
    fun provideMediaServiceConnection(@ApplicationContext context: Context): MediaServiceConnection {
        val conn = MediaServiceConnection(
            context,
            // Connect to Media3PlayerService instead of legacy MediaPlayerService
            ComponentName(context, Media3PlayerService::class.java),
        )
        val doesServiceExist = ServiceUtils.isServiceRunning(
            context,
            Media3PlayerService::class.java,
        )
        Timber.i("Connecting to existing Media3 service? $doesServiceExist")
        if (doesServiceExist) {
            conn.connect()
        }
        return conn
    }
}
