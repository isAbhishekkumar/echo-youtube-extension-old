package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable

/**
 * Represents a feed of multiple T items
 * Used in various contexts like home, library, etc.
 *
 * @param T The type of items in the feed
 * @property tabs The list of tabs in the feed
 * @property getPagedData Function to retrieve the shelves with Buttons for a given tab
 */
class Feed<T : Any>(
    val tabs: List<Tab>,
    val getPagedData: suspend (Tab?) -> Data<T>
) {
    /**
     * A list of Tab items that are not sort tabs.
     * These tabs are used to load data in the feed and are not considered for sorting.
     */
    val notSortTabs: List<Tab> get() = tabs

    /**
     * Represents the loaded data of the Feed
     *
     * @param T The type of items in the feed
     * @property pagedData The PagedData containing the items for the feed
     * @property buttons The Buttons to be shown in the feed. If null, the buttons will be decided automatically
     * @property background The ImageHolder to be used as the background of the feed. If null, the background will be decided automatically
     */
    data class Data<T : Any>(
        val pagedData: PagedData<T>,
        val buttons: Buttons? = null,
        val background: ImageHolder? = null
    )

    /**
     * A data class representing the buttons that can be shown in the feed
     *
     * @property showSearch Whether to show the search button
     * @property showSort Whether to show the sort button
     * @property showPlayAndShuffle Whether to show the play and shuffle buttons
     * @property customTrackList To play a custom list of tracks when play and shuffle buttons are clicked
     */
    @Serializable
    data class Buttons(
        val showSearch: Boolean = true,
        val showSort: Boolean = true,
        val showPlayAndShuffle: Boolean = false,
        val customTrackList: List<Track>? = null
    ) {
        companion object {
            val EMPTY = Buttons(showSearch = false, showSort = false, showPlayAndShuffle = false)
        }
    }

    companion object {
        /**
         * Convenience function to convert a PagedData to a Feed.Data
         */
        fun <T : Any> PagedData<T>.toFeedData(
            buttons: Buttons? = null,
            background: ImageHolder? = null
        ): Data<T> = Data(this, buttons, background)

        /**
         * Convenience function to convert a list of T items to a Feed.Data
         */
        fun <T : Any> List<T>.toFeedData(
            buttons: Buttons? = null,
            background: ImageHolder? = null
        ): Data<T> = PagedData.Single { this }.toFeedData(buttons, background)

        /**
         * Convenience function to create a Feed from a PagedData of T items
         */
        fun <T : Any> PagedData<T>.toFeed(
            buttons: Buttons? = null,
            background: ImageHolder? = null
        ): Feed<T> = Feed(emptyList()) { toFeedData(buttons, background) }

        /**
         * Convenience function to create a Feed from a list of T items
         */
        fun <T : Any> List<T>.toFeed(
            buttons: Buttons? = null, 
            background: ImageHolder? = null
        ): Feed<T> = PagedData.Single { this }.toFeed(buttons, background)
        
        /**
         * Convenience function to load all items in the Feed. Please use sparingly.
         */
        suspend fun <T : Any> Feed<T>.loadAll(): List<T> {
            val data = getPagedData(null)
            return data.pagedData.loadFirst()
        }
        
        /**
         * Convenience function to load all items in the Feed for the firstOrNull Tab
         */
        suspend fun <T : Any> Feed<T>.pagedDataOfFirst(): PagedData<T> {
            return getPagedData(tabs.firstOrNull()).pagedData
        }
    }
}

/**
 * Represents a tab in a feed
 *
 * @property id The identifier for the tab
 * @property title The display title for the tab
 */
@Serializable
data class Tab(
    val id: String,
    val title: String
)