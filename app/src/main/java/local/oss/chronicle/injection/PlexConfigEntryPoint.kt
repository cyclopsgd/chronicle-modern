package local.oss.chronicle.injection

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import local.oss.chronicle.data.sources.plex.PlexConfig

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlexConfigEntryPoint {
    fun plexConfig(): PlexConfig
}
