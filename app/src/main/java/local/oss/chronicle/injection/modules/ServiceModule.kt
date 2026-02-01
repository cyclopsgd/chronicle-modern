package local.oss.chronicle.injection.modules

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.support.v4.media.RatingCompat.RATING_NONE
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.*
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import local.oss.chronicle.BuildConfig
import local.oss.chronicle.R
import local.oss.chronicle.application.MainActivity
import local.oss.chronicle.data.sources.plex.APP_NAME
import local.oss.chronicle.data.sources.plex.PlaybackUrlResolver
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexMediaService
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.features.player.*
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_BACK_BUFFER_DURATION_MILLIS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_MAX_BUFFER_DURATION_MILLIS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_MIN_BUFFER_DURATION_MILLIS
import local.oss.chronicle.util.PackageValidator
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Module
@InstallIn(ServiceComponent::class)
abstract class ServiceModule {

    @Binds
    @ServiceScoped
    abstract fun bindSleepTimer(impl: SimpleSleepTimer): SleepTimer

    @Binds
    @ServiceScoped
    abstract fun bindMediaSessionCallback(impl: AudiobookMediaSessionCallback): Callback

    companion object {
        // Attribution tag for audio operations (must match manifest declaration)
        private const val ATTRIBUTION_TAG_MEDIA_PLAYBACK = "chronicle_media_playback"

        @Provides
        @ServiceScoped
        fun provideServiceScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main)

        @Provides
        @ServiceScoped
        fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
            val attributedContext = context.createAttributionContext(ATTRIBUTION_TAG_MEDIA_PLAYBACK)
            return ExoPlayer.Builder(attributedContext).setLoadControl(
            // increase buffer size across the board as ExoPlayer defaults are set for video
            DefaultLoadControl.Builder().setBackBuffer(EXOPLAYER_BACK_BUFFER_DURATION_MILLIS, true)
                .setBufferDurationsMs(
                    EXOPLAYER_MIN_BUFFER_DURATION_MILLIS,
                    EXOPLAYER_MAX_BUFFER_DURATION_MILLIS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                    .build(),
            ).build()
        }

        @Provides
        @ServiceScoped
        fun providePendingIntent(@ApplicationContext context: Context): PendingIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName).let { sessionIntent ->
                sessionIntent?.putExtra(MainActivity.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
                PendingIntent.getActivity(
                    context,
                    MainActivity.REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING,
                    sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE,
                )
            }

        @Provides
        @ServiceScoped
        fun provideMediaSession(
            @ApplicationContext context: Context,
            launchActivityPendingIntent: PendingIntent
        ): MediaSessionCompat {
            val attributedContext = context.createAttributionContext(ATTRIBUTION_TAG_MEDIA_PLAYBACK)
            return MediaSessionCompat(attributedContext, APP_NAME).apply {
            // Enable queue management; media buttons handled automatically on recent APIs
                setFlags(FLAG_HANDLES_QUEUE_COMMANDS)
                setSessionActivity(launchActivityPendingIntent)
                setRatingType(RATING_NONE)
                isActive = true
            }
        }

        @Provides
        @ServiceScoped
        fun provideLocalBroadcastManager(@ApplicationContext context: Context): LocalBroadcastManager =
            LocalBroadcastManager.getInstance(context)

        @Provides
        @ServiceScoped
        fun provideProgressUpdater(
            updater: SimpleProgressUpdater,
            mediaControllerCompat: MediaControllerCompat,
        ): ProgressUpdater =
            updater.apply {
                mediaController = mediaControllerCompat
            }

        @Provides
        @ServiceScoped
        fun provideNotificationManager(@ApplicationContext context: Context): NotificationManagerCompat =
            NotificationManagerCompat.from(context)

        @Provides
        @ServiceScoped
        fun provideMediaController(
            @ApplicationContext context: Context,
            session: MediaSessionCompat
        ): MediaControllerCompat =
            MediaControllerCompat(context, session.sessionToken)

        @Provides
        @ServiceScoped
        fun provideBecomingNoisyReceiver(
            @ApplicationContext context: Context,
            session: MediaSessionCompat
        ): BecomingNoisyReceiver =
            BecomingNoisyReceiver(context, session.sessionToken)

        @Provides
        @ServiceScoped
        fun providePlexDataSourceFactory(
            @ApplicationContext context: Context,
            plexPrefs: PlexPrefsRepo
        ): DefaultHttpDataSource.Factory {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
            dataSourceFactory.setUserAgent(Util.getUserAgent(context, APP_NAME))

        dataSourceFactory.setDefaultRequestProperties(
            mapOf(
                "X-Plex-Platform" to "Android",
                "X-Plex-Provides" to "player",
                "X-Plex_Client-Name" to APP_NAME,
                "X-Plex-Client-Identifier" to plexPrefs.uuid,
                "X-Plex-Version" to BuildConfig.VERSION_NAME,
                "X-Plex-Product" to APP_NAME,
                "X-Plex-Platform-Version" to Build.VERSION.RELEASE,
                "X-Plex-Device" to Build.MODEL,
                "X-Plex-Device-Name" to Build.MODEL,
                "X-Plex-Token" to (
                    plexPrefs.server?.accessToken ?: plexPrefs.user?.authToken
                        ?: plexPrefs.accountAuthToken
                ),
                // Adding X-Plex-Session-Identifier to help server track playback sessions
                // This allows the Plex server to make transcoding decisions if needed
                "X-Plex-Session-Identifier" to plexPrefs.uuid,
                // Client profile declares what audio formats this app can directly play
                // Generic profile already has transcode targets, so only adding direct play profile
                "X-Plex-Client-Profile-Extra" to
                    "add-direct-play-profile(type=musicProfile&container=mp4,m4a,m4b,mp3,flac,ogg,opus&audioCodec=aac,mp3,flac,vorbis,opus&videoCodec=*&subtitleCodec=*)",
            ),
        )

            return dataSourceFactory
        }

        @Provides
        @ServiceScoped
        fun providePackageValidator(@ApplicationContext context: Context): PackageValidator =
            PackageValidator(context, R.xml.auto_allowed_callers)

        @Provides
        @ServiceScoped
        fun provideTrackListManager(): TrackListStateManager = TrackListStateManager()

        @Provides
        @ServiceScoped
        fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        @Provides
        @ServiceScoped
        fun provideToneManager(@ApplicationContext context: Context): ToneGenerator {
            val attributedContext = context.createAttributionContext(ATTRIBUTION_TAG_MEDIA_PLAYBACK)
            val audioManager = attributedContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        }

        // PlaybackUrlResolver is now provided via @Inject constructor with @Singleton scope
        // This allows it to be shared across service and singleton components
    }
}
