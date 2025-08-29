package dev.brahmkshatriya.echo.common.helpers

import kotlinx.serialization.Serializable

/**
 * Represents a paged data collection with pagination support
 *
 * @param T The type of items in the collection
 */
sealed class PagedData<T : Any> {
    /**
     * Loads the first page of data
     *
     * @return The first page of items
     */
    abstract suspend fun loadFirst(): List<T>

    /**
     * Loads the next page of data if available
     *
     * @return The next page of items, or null if no more pages
     */
    abstract suspend fun loadNext(): List<T>?

    /**
     * Checks if there are more pages available
     *
     * @return True if more pages are available, false otherwise
     */
    abstract val hasMore: Boolean

    /**
     * A single-page data collection
     *
     * @param T The type of items in the collection
     * @property loader Function to load all items at once
     */
    class Single<T : Any>(private val loader: suspend () -> List<T>) : PagedData<T>() {
        private var loaded = false
        private var data: List<T> = emptyList()

        override suspend fun loadFirst(): List<T> {
            if (!loaded) {
                data = loader()
                loaded = true
            }
            return data
        }

        override suspend fun loadNext(): List<T>? = null

        override val hasMore: Boolean = false
    }

    /**
     * A continuous paged data collection that loads data in pages
     *
     * @param T The type of items in the collection
     * @property loader Function to load pages with optional continuation token
     */
    class Continuous<T : Any>(
        private val loader: suspend (continuation: String?) -> Page<T>
    ) : PagedData<T>() {
        private var continuation: String? = null
        private var firstLoaded = false
        private var reachedEnd = false

        override suspend fun loadFirst(): List<T> {
            val page = loader(null)
            continuation = page.continuation
            firstLoaded = true
            reachedEnd = continuation == null
            return page.items
        }

        override suspend fun loadNext(): List<T>? {
            if (!firstLoaded) return loadFirst()
            if (reachedEnd) return null
            
            val nextToken = continuation
            if (nextToken != null) {
                val page = loader(nextToken)
                continuation = page.continuation
                reachedEnd = continuation == null
                return page.items
            }
            return null
        }

        override val hasMore: Boolean
            get() = !reachedEnd
    }

    companion object {
        /**
         * Creates a single-page data collection
         *
         * @param T The type of items in the collection
         * @param loader Function to load all items at once
         * @return A Single paged data collection
         */
        fun <T : Any> Single(loader: suspend () -> List<T>): PagedData<T> = Single(loader)

        /**
         * Creates a continuous paged data collection
         *
         * @param T The type of items in the collection
         * @param loader Function to load pages with optional continuation token
         * @return A Continuous paged data collection
         */
        fun <T : Any> Continuous(loader: suspend (continuation: String?) -> Page<T>): PagedData<T> = 
            Continuous(loader)
    }
}

/**
 * Represents a page of data with items and an optional continuation token
 *
 * @param T The type of items in the page
 * @property items The items in the page
 * @property continuation Optional token for loading the next page
 */
@Serializable
data class Page<T>(
    val items: List<T>,
    val continuation: String? = null
)