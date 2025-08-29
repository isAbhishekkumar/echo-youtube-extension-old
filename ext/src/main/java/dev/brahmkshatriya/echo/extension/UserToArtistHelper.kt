package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.User

/**
 * Helper class to ensure proper conversion between User and Artist models
 * This class helps maintain type safety in the conversion process and prevents ClassCastException
 * caused by proguard optimization and minification
 */
object UserToArtistHelper {
    /**
     * Safely converts a User to an Artist
     * @param user The User object to convert
     * @return The converted Artist object
     */
    @JvmStatic
    fun safeConvertUserToArtist(user: User): Artist {
        return Artist(
            id = user.id,
            name = user.name,
            cover = user.cover,
            extras = user.extras
        )
    }

    /**
     * Checks if an object is a User, and if so converts it to Artist
     * This helps with the UnifiedExtension casting issue
     * @param obj The object to check and potentially convert
     * @return An Artist if conversion was successful, null otherwise
     */
    @JvmStatic
    fun convertIfUser(obj: Any): Artist? {
        return if (obj is User) {
            safeConvertUserToArtist(obj)
        } else if (obj is Artist) {
            obj
        } else {
            null
        }
    }
}