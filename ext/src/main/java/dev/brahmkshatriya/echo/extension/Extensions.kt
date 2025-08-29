package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.Shelf

/**
 * Extension function to convert String to NetworkRequest
 */
fun String.toRequest(headers: Map<String, String> = emptyMap()): NetworkRequest {
    return NetworkRequest(this, headers)
}

/**
 * Extension function to load all items in a Feed
 */
suspend fun <T : Any> Feed<T>.load(): List<T> {
    return this.loadAll()
}

/**
 * Converts a PagedData of EchoMediaItems to a PagedData of Shelves
 */
private fun createShelfPagedData(original: PagedData<EchoMediaItem>): PagedData<Shelf> {
    return PagedData.Continuous { continuation ->
        val page = original.loadPage(continuation)
        val shelves = page.data.map { item -> Shelf.Item(item) }
        Page(shelves, page.continuation)
    }
}

/**
 * Extension function to create a Feed<Shelf> from a PagedData<EchoMediaItem>
 * This converts media items to shelf items for proper display
 */
fun PagedData<EchoMediaItem>.toShelfFeed(): Feed<Shelf> {
    return Feed(listOf()) { _ -> 
        Feed.Data(createShelfPagedData(this))
    }
}

// Extension functions for date conversion moved to Convertors.kt