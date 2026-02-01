package local.oss.chronicle.injection

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import javax.inject.Named

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppContextEntryPoint {
    @ApplicationContext
    fun applicationContext(): Context
    fun exceptionHandler(): CoroutineExceptionHandler
    @Named("externalDeviceDirs")
    fun externalDeviceDirs(): List<File>
}
