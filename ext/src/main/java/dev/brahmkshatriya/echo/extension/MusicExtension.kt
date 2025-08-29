package dev.brahmkshatriya.echo.extension

import android.content.Context
import android.content.SharedPreferences
import dev.brahmkshatriya.echo.common.clients.ExtensionType
import dev.brahmkshatriya.echo.common.clients.Injectable
import dev.brahmkshatriya.echo.common.helpers.ExtensionPackageHelper
import dev.brahmkshatriya.echo.common.models.Metadata

/**
 * Interface representing a music extension for Echo.
 * This wrapper provides metadata and creates the extension instance.
 */
interface MusicExtension {
    /**
     * Metadata about the extension including name, description, etc.
     */
    val metadata: Metadata
    
    /**
     * The type of extension (MUSIC)
     */
    val extensionType: ExtensionType
    
    /**
     * Creates a new instance of the extension implementation
     *
     * @param context Android application context
     * @param packageHelper Helper for extension package operations
     * @param preferences Shared preferences for the extension
     * @return The injectable extension instance
     */
    fun createInstance(
        context: Context,
        packageHelper: ExtensionPackageHelper,
        preferences: SharedPreferences
    ): Injectable
}