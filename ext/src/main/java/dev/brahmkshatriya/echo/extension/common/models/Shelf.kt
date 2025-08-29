package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable

/**
 * Represents a collection of media items organized in a shelf
 * Used for displaying content in feeds
 */
sealed class Shelf {
    /**
     * The title of the shelf
     */
    abstract val title: String?
    
    /**
     * The subtitle of the shelf
     */
    abstract val subtitle: String?
    
    /**
     * The image/thumbnail for the shelf
     */
    abstract val image: ImageHolder?
    
    /**
     * A shelf containing a single media item
     *
     * @property media The media item in the shelf
     * @property title The title of the shelf
     * @property subtitle The subtitle of the shelf
     * @property image The image/thumbnail for the shelf
     */
    data class Item(
        val media: EchoMediaItem,
        override val title: String? = media.title,
        override val subtitle: String? = media.subtitle,
        override val image: ImageHolder? = media.imageHolder
    ) : Shelf()
    
    /**
     * Classes for representing lists of items in shelves
     */
    sealed class Lists : Shelf() {
        /**
         * A shelf containing a list of media items
         *
         * @property list The list of media items
         * @property title The title of the shelf
         * @property subtitle The subtitle of the shelf
         * @property image The image/thumbnail for the shelf
         */
        data class Items(
            val list: List<EchoMediaItem>,
            override val title: String? = null,
            override val subtitle: String? = null,
            override val image: ImageHolder? = null
        ) : Lists()
        
        /**
         * A shelf containing a list of tracks
         *
         * @property list The list of tracks
         * @property title The title of the shelf
         * @property subtitle The subtitle of the shelf
         * @property image The image/thumbnail for the shelf
         */
        data class Tracks(
            val list: List<Track>,
            override val title: String? = null,
            override val subtitle: String? = null,
            override val image: ImageHolder? = null
        ) : Lists()
    }
    
    companion object {
        /**
         * Constants for different types of shelf contents
         */
        const val SINGLES = "SINGLES"
    }
}

/**
 * Extension function to convert an EchoMediaItem to a Shelf.Item
 *
 * @return A Shelf.Item containing this media item
 */
fun EchoMediaItem.toShelf(): Shelf.Item = Shelf.Item(this)