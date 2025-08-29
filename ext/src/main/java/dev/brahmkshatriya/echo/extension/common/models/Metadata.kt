package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable

/**
 * Represents metadata information about an extension.
 *
 * @property name The display name of the extension
 * @property description A brief description of what the extension does
 * @property packageName The unique package name identifier for the extension
 * @property version The version string of the extension
 * @property author The name of the extension author
 * @property icon An image holder for the extension's icon
 */
@Serializable
data class Metadata(
    val name: String,
    val description: String,
    val packageName: String,
    val version: String,
    val author: String,
    val icon: ImageHolder
)