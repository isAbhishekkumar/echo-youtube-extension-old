package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User

/**
 * Special handler for search results to fix User to Artist conversion issues
 * This class is specifically designed to address ClassCastExceptions that occur
 * when scrolling through search results.
 */
object SearchResultsFixer {

    /**
     * Fix a list of search results, ensuring all User objects are properly converted to Artists
     * @param results List of search result items
     * @return Fixed list with proper type conversions
     */
    @JvmStatic
    fun fixSearchResults(results: List<EchoMediaItem>): List<EchoMediaItem> {
        return results.map { fixSearchResultItem(it) }
    }
    
    /**
     * Fix a shelf of search results
     * @param shelf Shelf containing search results
     * @return Fixed shelf with proper type conversions
     */
    @JvmStatic
    fun fixSearchResultShelf(shelf: Shelf): Shelf {
        try {
            return when (shelf) {
                is Shelf.Item -> try {
                    Shelf.Item(fixSearchResultItem(shelf.media))
                } catch (e: Exception) {
                    shelf
                }
                
                is Shelf.Lists.Items -> try {
                    Shelf.Lists.Items(
                        id = shelf.id,
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        list = shelf.list.mapNotNull { 
                            try {
                                fixSearchResultItem(it)
                            } catch (e: Exception) {
                                it // Return original item if fixing fails
                            }
                        },
                        more = shelf.more
                    )
                } catch (e: Exception) {
                    shelf
                }
                
                is Shelf.Lists.Tracks -> try {
                    Shelf.Lists.Tracks(
                        id = shelf.id,
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        list = shelf.list.mapNotNull { 
                            try {
                                fixTrack(it)
                            } catch (e: Exception) {
                                it // Return original track if fixing fails
                            }
                        },
                        more = shelf.more
                    )
                } catch (e: Exception) {
                    shelf
                }
                
                is Shelf.Lists.Categories -> try {
                    Shelf.Lists.Categories(
                        id = shelf.id,
                        title = shelf.title,
                        list = shelf.list.map { 
                            try {
                                fixSearchResultCategory(it)
                            } catch (e: Exception) {
                                it // Return original category if fixing fails
                            }
                        },
                        more = shelf.more
                    )
                } catch (e: Exception) {
                    shelf
                }
                
                is Shelf.Category -> shelf // Categories don't need fixing
            }
        } catch (e: Exception) {
            // If any error occurs, return original shelf
            return shelf
        }
    }
    
    /**
     * Fix a category shelf from search results
     */
    @JvmStatic
    private fun fixSearchResultCategory(category: Shelf.Category): Shelf.Category {
        return category // Categories don't need fixing
    }
    
    /**
     * Fix a single search result item
     */
    @JvmStatic
    fun fixSearchResultItem(item: EchoMediaItem): EchoMediaItem {
        try {
            return when (item) {
                is Track -> fixTrack(item)
                is Album -> fixAlbum(item)
                is Playlist -> fixPlaylist(item)
                is Artist -> item
                is User -> ModelTypeHelper.userToArtist(item)
                else -> item
            }
        } catch (e: Exception) {
            // If any conversion fails, return the original item
            // This prevents the entire search result from failing
            return item
        }
    }
    
    /**
     * Fix a track by ensuring all artists are Artist objects, not User objects
     */
    @JvmStatic
    fun fixTrack(track: Track): Track {
        val fixedArtists = track.artists.mapNotNull { artist ->
            try {
                when (artist) {
                    is Artist -> artist
                    is User -> ModelTypeHelper.userToArtist(artist)
                    else -> {
                        // Create a generic Artist when type doesn't match expected types
                        Artist(
                            id = (artist as? EchoMediaItem)?.id ?: "unknown-id",
                            name = try {
                                when (artist) {
                                    is User -> artist.name ?: "Unknown Artist"
                                    is EchoMediaItem -> artist.title ?: artist.id
                                    else -> "Unknown Artist"
                                }
                            } catch (e: Exception) {
                                "Unknown Artist"
                            },
                            cover = (artist as? EchoMediaItem)?.cover,
                            subtitle = (artist as? EchoMediaItem)?.subtitle,
                            extras = (artist as? EchoMediaItem)?.extras ?: emptyMap()
                        )
                    }
                }
            } catch (e: Exception) {
                // If conversion fails, create a fallback Artist
                Artist(
                    id = "fallback-${track.id}-artist",
                    name = "Unknown Artist",
                    cover = null,
                    subtitle = null,
                    extras = mapOf("error" to "Conversion failed")
                )
            }
        }
        
        val fixedAlbum = try {
            track.album?.let { fixAlbum(it) }
        } catch (e: Exception) {
            track.album
        }
        
        return track.copy(
            artists = fixedArtists,
            album = fixedAlbum
        )
    }
    
    /**
     * Fix an album by ensuring all artists are Artist objects, not User objects
     */
    @JvmStatic
    fun fixAlbum(album: Album): Album {
        val fixedArtists = album.artists.mapNotNull { artist ->
            try {
                when (artist) {
                    is Artist -> artist
                    is User -> ModelTypeHelper.userToArtist(artist)
                    else -> {
                        // Create a generic Artist when type doesn't match expected types
                        Artist(
                            id = (artist as? EchoMediaItem)?.id ?: "unknown-id",
                            name = try {
                                when (artist) {
                                    is User -> artist.name ?: "Unknown Artist"
                                    is EchoMediaItem -> artist.title ?: artist.id
                                    else -> "Unknown Artist"
                                }
                            } catch (e: Exception) {
                                "Unknown Artist"
                            },
                            cover = (artist as? EchoMediaItem)?.cover,
                            subtitle = (artist as? EchoMediaItem)?.subtitle,
                            extras = (artist as? EchoMediaItem)?.extras ?: emptyMap()
                        )
                    }
                }
            } catch (e: Exception) {
                // If conversion fails, create a fallback Artist
                Artist(
                    id = "fallback-${album.id}-artist",
                    name = "Unknown Artist",
                    cover = null,
                    subtitle = null,
                    extras = mapOf("error" to "Conversion failed")
                )
            }
        }
        
        return album.copy(
            artists = fixedArtists
        )
    }
    
    /**
     * Fix a playlist by ensuring all authors are Artist objects, not User objects
     */
    @JvmStatic
    fun fixPlaylist(playlist: Playlist): Playlist {
        val fixedAuthors = playlist.authors.mapNotNull { author ->
            try {
                when (author) {
                    is Artist -> author
                    is User -> ModelTypeHelper.userToArtist(author)
                    // Type checking is redundant, but we need to handle other unexpected types
                    // that might appear in the list
                    else -> {
                        // Create a generic Artist when type doesn't match expected types
                        Artist(
                            id = (author as? EchoMediaItem)?.id ?: "unknown-id",
                            name = try {
                                // Since we're in the else branch, we know author is not User
                                // but we still need to check if it's an EchoMediaItem
                                if (author is EchoMediaItem) author.title ?: author.id
                                else "Unknown Artist"
                            } catch (e: Exception) {
                                "Unknown Artist"
                            },
                            cover = (author as? EchoMediaItem)?.cover,
                            subtitle = (author as? EchoMediaItem)?.subtitle,
                            extras = (author as? EchoMediaItem)?.extras ?: emptyMap()
                        )
                    }
                }
            } catch (e: Exception) {
                // If any conversion fails, create a fallback Artist
                Artist(
                    id = "fallback-${playlist.id}-author",
                    name = "Unknown Artist",
                    cover = null,
                    subtitle = null,
                    extras = mapOf("error" to "Conversion failed")
                )
            }
        }
        
        return playlist.copy(
            authors = fixedAuthors
        )
    }
}