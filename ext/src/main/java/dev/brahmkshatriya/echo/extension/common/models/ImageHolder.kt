package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable

/**
 * Represents an image that can be sourced from different locations.
 */
@Serializable
sealed class ImageHolder {
    /**
     * Represents an image from a URL.
     *
     * @property url The URL where the image is located
     */
    @Serializable
    data class Url(val url: String) : ImageHolder()
    
    /**
     * Represents an image from an Android resource.
     *
     * @property resourceName The name of the resource in the Android resources
     */
    @Serializable
    data class Resource(val resourceName: String) : ImageHolder()
    
    /**
     * Represents an image that's encoded as a Base64 string.
     *
     * @property base64Data The Base64-encoded string of the image data
     */
    @Serializable
    data class Base64(val base64Data: String) : ImageHolder()
}