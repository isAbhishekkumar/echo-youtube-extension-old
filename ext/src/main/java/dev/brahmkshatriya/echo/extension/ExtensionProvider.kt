package dev.brahmkshatriya.echo.extension

/**
 * This file is the entry point for the Echo extension system.
 * It provides a function that returns the extension instance.
 */

/**
 * Returns the YouTube Music extension instance.
 * This function is called by Echo's extension system to load the extension.
 */
@Suppress("unused")
fun getExtension(): MusicExtension {
    return YoutubeExtensionWrapper()
}