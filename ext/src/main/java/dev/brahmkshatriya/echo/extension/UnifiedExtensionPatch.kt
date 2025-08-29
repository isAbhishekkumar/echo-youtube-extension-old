package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User

/**
 * This is a patch class designed to fix the User to Artist casting issues in the UnifiedExtension.
 * 
 * The UnifiedExtension has methods like withExtensionId() that expect Artist objects but sometimes
 * receive User objects, leading to ClassCastExceptions. This class provides methods that act as
 * a "shim" to safely handle those scenarios.
 */
object UnifiedExtensionPatch {

    /**
     * Patches an EchoMediaItem to ensure all User objects are converted to Artist objects
     * when needed. This is particularly important for search results.
     * 
     * @param item The media item to patch
     * @param extensionId The extension ID to add to extras
     * @return The patched media item
     */
    @JvmStatic
    fun patchMediaItem(item: EchoMediaItem, extensionId: String): EchoMediaItem {
        // First run the media item through the SearchResultsFixer to ensure type correctness
        val fixedItem = SearchResultsFixer.fixSearchResultItem(item)
        
        return when (fixedItem) {
            is Track -> patchTrack(fixedItem, extensionId)
            is Album -> patchAlbum(fixedItem, extensionId)
            is Playlist -> patchPlaylist(fixedItem, extensionId)
            is User -> ModelTypeHelper.userToArtist(fixedItem).copy(
                extras = fixedItem.extras + mapOf("extension_id" to extensionId)
            )
            is Artist -> fixedItem.copy(
                extras = fixedItem.extras + mapOf("extension_id" to extensionId)
            )
            // No type conversion for other types, just add extension ID
            else -> item.apply {
                (this as? EchoMediaItem)?.let {
                    it.extras + mapOf("extension_id" to extensionId)
                }
            }
        }
    }
    
    /**
     * Patches a Track to ensure all its artists are Artist objects, not User objects
     */
    @JvmStatic
    fun patchTrack(track: Track, extensionId: String): Track {
        val patchedArtists = track.artists.map { artist ->
            when (artist) {
                is Artist -> artist
                // If not Artist, must be User since these are the only two types expected here
                else -> ModelTypeHelper.userToArtist(artist as User)
            }
        }
        
        return track.copy(
            artists = patchedArtists,
            extras = track.extras + mapOf("extension_id" to extensionId),
            album = track.album?.let { patchAlbum(it, extensionId) }
        )
    }
    
    /**
     * Patches an Album to ensure all its artists are Artist objects, not User objects
     */
    @JvmStatic
    fun patchAlbum(album: Album, extensionId: String): Album {
        val patchedArtists = album.artists.map { artist ->
            when (artist) {
                is Artist -> artist
                // If not Artist, must be User since these are the only two types expected here
                else -> ModelTypeHelper.userToArtist(artist as User)
            }
        }
        
        return album.copy(
            artists = patchedArtists,
            extras = album.extras + mapOf("extension_id" to extensionId)
        )
    }
    
    /**
     * Patches a Playlist to ensure all its authors are Artist objects, not User objects
     */
    @JvmStatic
    fun patchPlaylist(playlist: Playlist, extensionId: String): Playlist {
        val patchedAuthors = playlist.authors.map { author ->
            when (author) {
                is Artist -> author
                // If not Artist, must be User since these are the only two types expected here
                else -> ModelTypeHelper.userToArtist(author as User)
            }
        }
        
        return playlist.copy(
            authors = patchedAuthors,
            extras = playlist.extras + mapOf("extension_id" to extensionId)
        )
    }
    
    /**
     * Tries to convert any object to an Artist if possible
     */
    @JvmStatic
    fun safeToArtist(obj: Any?): Artist? {
        return when (obj) {
            is Artist -> obj
            is User -> ModelTypeHelper.userToArtist(obj)
            // We need to keep the else -> null here because obj could be anything
            else -> null
        }
    }
    
    /**
     * Special patch for search results which often have User objects in place of Artists
     */
    @JvmStatic
    fun patchSearchResults(items: List<EchoMediaItem>, extensionId: String): List<EchoMediaItem> {
        // Use SearchResultsFixer first to ensure proper types, then add extension ID
        return SearchResultsFixer.fixSearchResults(items).map { item -> 
            when (item) {
                is EchoMediaItem -> patchMediaItem(item, extensionId)
                else -> item
            }
        }
    }
    
    /**
     * Patch a shelf of search results to ensure all items have the proper types
     * and the extension ID is added
     */
    @JvmStatic
    fun patchSearchResultShelf(shelf: Shelf, extensionId: String): Shelf {
        return when (shelf) {
            is Shelf.Item -> {
                val fixedMedia = patchMediaItem(shelf.media, extensionId)
                Shelf.Item(fixedMedia)
            }
            
            is Shelf.Lists.Items -> Shelf.Lists.Items(
                id = shelf.id,
                title = shelf.title,
                subtitle = shelf.subtitle,
                list = shelf.list.map { patchMediaItem(it, extensionId) },
                more = shelf.more
            )
            
            is Shelf.Lists.Tracks -> Shelf.Lists.Tracks(
                id = shelf.id,
                title = shelf.title,
                subtitle = shelf.subtitle,
                list = shelf.list.map { patchTrack(it, extensionId) },
                more = shelf.more
            )
            
            is Shelf.Lists.Categories -> Shelf.Lists.Categories(
                id = shelf.id,
                title = shelf.title,
                list = shelf.list, // Categories don't need patching
                more = shelf.more
            )
            
            is Shelf.Category -> Shelf.Category(
                id = shelf.id,
                title = shelf.title,
                feed = shelf.feed  // Feed will be patched when loaded
            )
        }
    }
}