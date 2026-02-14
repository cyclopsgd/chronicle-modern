package local.oss.chronicle.features.library

import local.oss.chronicle.data.model.Audiobook

/**
 * Interface for handling audiobook click events.
 * Extracted from the old LibraryFragment for use with data binding adapters.
 */
interface AudiobookClick {
    fun onClick(audiobook: Audiobook)
}
