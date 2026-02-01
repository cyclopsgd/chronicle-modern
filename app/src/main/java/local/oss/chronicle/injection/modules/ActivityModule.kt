package local.oss.chronicle.injection.modules

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineScope
import local.oss.chronicle.features.player.ProgressUpdater
import local.oss.chronicle.features.player.SimpleProgressUpdater

/**
 * Provides Activity-scoped dependencies.
 *
 * Note: MediaServiceConnection and LocalBroadcastManager are provided in
 * ActivityRetainedModule so they can be accessed by ViewModels as well.
 */
@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @Provides
    @ActivityScoped
    fun provideProgressUpdater(impl: SimpleProgressUpdater): ProgressUpdater = impl

    @Provides
    @ActivityScoped
    fun provideAppCompatActivity(@ActivityContext context: Context): AppCompatActivity =
        context as AppCompatActivity

    @Provides
    @ActivityScoped
    fun provideFragmentManager(activity: AppCompatActivity): FragmentManager =
        activity.supportFragmentManager

    @Provides
    @ActivityScoped
    fun provideActivityCoroutineScope(activity: AppCompatActivity): CoroutineScope =
        activity.lifecycleScope
}
