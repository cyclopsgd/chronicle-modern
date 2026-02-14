package local.oss.chronicle.features.collections

import local.oss.chronicle.data.model.Collection

/**
 * Interface for handling collection click events.
 * Extracted from the old CollectionsFragment for use with data binding adapters.
 */
interface CollectionClick {
    fun onClick(collection: Collection)
}
