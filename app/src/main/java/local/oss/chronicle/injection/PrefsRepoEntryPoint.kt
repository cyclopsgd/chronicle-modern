package local.oss.chronicle.injection

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import local.oss.chronicle.data.local.PrefsRepo

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrefsRepoEntryPoint {
    fun prefsRepo(): PrefsRepo
}
