package local.oss.chronicle.injection.modules

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import local.oss.chronicle.features.player.MediaPlayerService
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.ProgressUpdater
import local.oss.chronicle.features.player.SimpleProgressUpdater
import local.oss.chronicle.util.ServiceUtils
import timber.log.Timber

@Module
@InstallIn(ActivityComponent::class)
abstract class ActivityModule {

    @Binds
    @ActivityScoped
    abstract fun bindProgressUpdater(impl: SimpleProgressUpdater): ProgressUpdater

    companion object {
        @Provides
        @ActivityScoped
        fun provideBroadcastManager(@ActivityContext context: Context): LocalBroadcastManager =
            LocalBroadcastManager.getInstance(context)

        @Provides
        @ActivityScoped
        fun provideMediaServiceConnection(@ActivityContext context: Context): MediaServiceConnection {
            val conn = MediaServiceConnection(
                context.applicationContext,
                ComponentName(context.applicationContext, MediaPlayerService::class.java),
            )
            val doesServiceExist = ServiceUtils.isServiceRunning(
                context.applicationContext,
                MediaPlayerService::class.java,
            )
            Timber.i("Connecting to existing service? $doesServiceExist")
            if (doesServiceExist) {
                conn.connect()
            }
            return conn
        }
    }
}
