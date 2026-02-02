package local.oss.chronicle.features.player.media3

import android.content.ComponentName
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import local.oss.chronicle.util.ServiceUtils
import timber.log.Timber

/**
 * Hilt module providing Media3 dependencies.
 *
 * This module provides the Media3ServiceConnection which uses the modern
 * MediaController and MediaLibrarySession APIs instead of the legacy
 * MediaBrowserCompat and MediaControllerCompat.
 *
 * To switch from legacy to Media3:
 * 1. Update ViewModels to inject Media3ServiceConnection instead of MediaServiceConnection
 * 2. Or use the adapter pattern via PlaybackController interface
 *
 * Both services (legacy and Media3) are registered in the manifest and can run
 * in parallel during the transition period.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
object Media3Module {

    /**
     * Provides the Media3 service connection.
     *
     * This is the modern replacement for MediaServiceConnection, using:
     * - SessionToken instead of MediaBrowserCompat
     * - MediaController.Builder instead of manual connection callbacks
     * - Direct Player interface instead of TransportControls
     */
    @Provides
    @ActivityRetainedScoped
    fun provideMedia3ServiceConnection(
        @ApplicationContext context: Context
    ): Media3ServiceConnection {
        val serviceComponent = ComponentName(context, Media3PlayerService::class.java)
        val connection = Media3ServiceConnection(context, serviceComponent)

        val doesServiceExist = ServiceUtils.isServiceRunning(
            context,
            Media3PlayerService::class.java
        )
        Timber.i("Connecting to existing Media3 service? $doesServiceExist")

        if (doesServiceExist) {
            connection.connect()
        }

        return connection
    }
}
