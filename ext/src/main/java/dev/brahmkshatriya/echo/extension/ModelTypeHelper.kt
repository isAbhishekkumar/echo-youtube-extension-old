package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User

/**
 * Helper class to ensure safe type conversions between Echo model classes
 * This class is designed to prevent ClassCastExceptions when models are being used across extensions
 */
object ModelTypeHelper {
    /**
     * Constant to add to extras to mark a converted Artist
     * This helps identify Artists that were converted from Users
     */
    const val CONVERTED_FROM_USER_KEY = "convertedFromUser"
    const val CONVERTED_FROM_USER_VALUE = "true"
    
    /**
     * Safe conversion from User to Artist
     * Adds a special flag in extras to mark this as a converted object
     */
    @JvmStatic
    fun userToArtist(user: User): Artist {
        try {
            val newExtras = user.extras.toMutableMap().apply {
                put(CONVERTED_FROM_USER_KEY, CONVERTED_FROM_USER_VALUE)
            }
            return Artist(
                id = user.id,
                name = user.name,
                cover = user.cover,
                subtitle = user.subtitle,
                extras = newExtras
            )
        } catch (e: Exception) {
            // If conversion fails for any reason, create a fallback Artist
            return Artist(
                id = user.id,
                name = user.name ?: "Unknown Artist",
                cover = null,
                subtitle = null,
                extras = mapOf(
                    CONVERTED_FROM_USER_KEY to CONVERTED_FROM_USER_VALUE,
                    "error" to "Conversion failed"
                )
            )
        }
    }
    
    /**
     * Safe conversion from Artist to User
     */
    @JvmStatic
    fun artistToUser(artist: Artist): User = User(
        id = artist.id,
        name = artist.name,
        cover = artist.cover,
        subtitle = artist.subtitle,
        extras = artist.extras
    )
    
    /**
     * Check if an Artist was converted from a User
     */
    @JvmStatic
    fun isConvertedArtist(artist: Artist): Boolean {
        return artist.extras[CONVERTED_FROM_USER_KEY] == CONVERTED_FROM_USER_VALUE
    }
    
    /**
     * Safely handle any object as an Artist
     * This method helps prevent ClassCastExceptions in the UnifiedExtension withExtensionId methods
     */
    @JvmStatic
    fun safeArtistConversion(obj: Any?): Artist? {
        try {
            return when (obj) {
                is Artist -> obj
                is User -> userToArtist(obj)
                is EchoMediaItem -> Artist(
                    id = obj.id,
                    name = obj.id, // obj can't be User here as it was checked above
                    cover = obj.cover,
                    subtitle = obj.subtitle,
                    extras = obj.extras + mapOf(CONVERTED_FROM_USER_KEY to CONVERTED_FROM_USER_VALUE)
                )
                else -> null
            }
        } catch (e: Exception) {
            // Create a fallback Artist if conversion fails
            return obj?.let {
                Artist(
                    id = "fallback-${it.hashCode()}",
                    name = "Unknown Artist",
                    cover = null,
                    subtitle = null,
                    extras = mapOf("error" to "Conversion failed")
                )
            }
        }
    }

    /**
     * Safe guard for conversion in lists
     */
    @JvmStatic
    fun safeArtistListConversion(list: List<Any>): List<Artist> {
        return list.mapNotNull { item -> 
            when (item) {
                is Artist -> item
                is User -> userToArtist(item)
                else -> null
            }
        }
    }
    
    /**
     * Ensure Artists in Album are proper Artist objects, not Users
     */
    @JvmStatic
    fun ensureProperArtistsInAlbum(album: Album): Album {
        val artists = album.artists.mapNotNull { artist ->
            when (artist) {
                is Artist -> artist
                is User -> userToArtist(artist)
                // No else branch needed as when is exhaustive
            }
        }
        
        return album.copy(artists = artists)
    }
    
    /**
     * Ensure Artists in Track are proper Artist objects, not Users
     */
    @JvmStatic
    fun ensureProperArtistsInTrack(track: Track): Track {
        val artists = track.artists.mapNotNull { artist ->
            when (artist) {
                is Artist -> artist
                is User -> userToArtist(artist)
                // No else branch needed as when is exhaustive
            }
        }
        
        // Also fix album artists if the track has an album
        val fixedAlbum = track.album?.let { ensureProperArtistsInAlbum(it) }
        
        return track.copy(
            artists = artists,
            album = fixedAlbum
        )
    }
    
    /**
     * Ensure Authors in Playlist are proper Artist objects, not Users
     */
    @JvmStatic
    fun ensureProperAuthorsInPlaylist(playlist: Playlist): Playlist {
        val authors = playlist.authors.mapNotNull { author ->
            when (author) {
                is Artist -> author
                is User -> userToArtist(author)
                // No else branch needed as when is exhaustive
            }
        }
        
        return playlist.copy(authors = authors)
    }
    
    /**
     * Fix EchoMediaItem that might have User objects where Artist objects are expected
     */
    @JvmStatic
    fun ensureProperTypesInMediaItem(item: EchoMediaItem): EchoMediaItem {
        try {
            return when (item) {
                is Track -> ensureProperArtistsInTrack(item)
                is Album -> ensureProperArtistsInAlbum(item)
                is Playlist -> ensureProperAuthorsInPlaylist(item)
                is User -> userToArtist(item) // Convert Users to Artists directly
                else -> item
            }
        } catch (e: Exception) {
            // If any conversion fails, return the original item
            return item
        }
    }
    
    /**
     * Fix a shelf containing search results
     */
    @JvmStatic
    fun fixSearchResultShelf(shelf: Shelf): Shelf {
        try {
            return when (shelf) {
                is Shelf.Item -> try {
                    Shelf.Item(ensureProperTypesInMediaItem(shelf.media))
                } catch (e: Exception) {
                    // If conversion fails for a specific item, return original
                    shelf
                }
                
                is Shelf.Lists.Items -> try {
                    Shelf.Lists.Items(
                        id = shelf.id,
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        list = shelf.list.mapNotNull { 
                            try {
                                ensureProperTypesInMediaItem(it)
                            } catch (e: Exception) {
                                // Skip items that fail conversion
                                null
                            }
                        },
                        more = shelf.more
                    )
                } catch (e: Exception) {
                    // If conversion fails for the entire list, return original
                    shelf
                }
                
                is Shelf.Lists.Tracks -> try {
                    Shelf.Lists.Tracks(
                        id = shelf.id,
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        list = shelf.list.mapNotNull { 
                            try {
                                ensureProperArtistsInTrack(it)
                            } catch (e: Exception) {
                                // Skip tracks that fail conversion
                                null
                            }
                        },
                        more = shelf.more
                    )
                } catch (e: Exception) {
                    // If conversion fails for the entire list, return original
                    shelf
                }
                
                else -> shelf // Other shelf types don't need fixing
            }
        } catch (e: Exception) {
            // Global fallback if any error occurs
            return shelf
        }
    }
}