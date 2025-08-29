package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.InputStream

/**
 * A data class representing an unloaded streamable item that is used when playing a Track.
 * The streamable item can be of three types:
 * - Streamable.server - To represent a server that contains data to be played
 * - Streamable.background - To represent a background video
 * - Streamable.subtitle - To represent subtitle
 *
 * @property id The id of the streamable item
 * @property quality The quality of the streamable item, this is used to sort the streamable items
 * @property type The type of the streamable item
 * @property title The title of the streamable item
 * @property extras Any extra data you want to associate with the streamable item
 */
@Serializable
data class Streamable(
    val id: String,
    val quality: Int,
    val type: MediaType,
    val title: String? = null,
    val extras: Map<String, String> = mapOf()
) {

    /**
     * An enum class representing the type of media
     */
    enum class MediaType {
        /**
         * Represents an unloaded server streamable
         */
        Server,

        /**
         * Represents an unloaded background streamable
         */
        Background,

        /**
         * Represents an unloaded subtitle streamable
         */
        Subtitle
    }

    /**
     * An enum representing the type of Source.
     */
    enum class SourceType {
        /**
         * Source that contain Audio/Video in container format File.
         */
        Progressive,

        /**
         * Source that is a M3U8 File.
         */
        HLS,

        /**
         * Source that is a Dash Manifest File.
         */
        DASH
    }

    /**
     * An enum class representing the type of subtitle
     */
    enum class SubtitleType {
        /**
         * SubRip subtitle format (.srt)
         */
        SRT,

        /**
         * WebVTT subtitle format (.vtt)
         */
        VTT,

        /**
         * Advanced SubStation Alpha subtitle format (.ass)
         */
        ASS
    }

    /**
     * An interface that provides an InputStream from a given position.
     */
    fun interface InputProvider {
        /**
         * Provides an InputStream from the given position
         *
         * @param position The position to start reading from
         * @return The InputStream
         */
        fun getStream(position: Long): InputStream
    }

    /**
     * A class representing the actual source where streamable audio/video is present.
     *
     * There are three types of sources:
     * - Http - To represent a source that contains Audio/Video on a Http Url.
     * - Raw - To represent a source that contains Audio/Video in a Byte Stream.
     */
    @Serializable
    sealed class Source {
        /**
         * The quality of the source, this is used to sort the sources
         */
        abstract val quality: Int

        /**
         * The title of the source
         */
        abstract val title: String?

        /**
         * The id of the source
         */
        abstract val id: String

        /**
         * Whether the source is video or audio
         */
        abstract val isVideo: Boolean

        /**
         * Whether the source is live
         */
        abstract val isLive: Boolean

        /**
         * A data class representing a source that contains Audio/Video on a Http Url.
         *
         * @property request The request for the source
         * @property type The type of the source
         * @property decryption The decryption for the source
         * @property quality The quality of the source, this is used to sort the sources
         * @property title The title of the source
         * @property isVideo Whether the source is video or audio
         * @property isLive If true, will prevent caching of the source
         */
        @Serializable
        data class Http(
            val request: NetworkRequest,
            val type: SourceType = SourceType.Progressive,
            val decryption: Decryption? = null,
            override val quality: Int = 0,
            override val title: String? = null,
            override val isVideo: Boolean = false,
            override val isLive: Boolean = false
        ) : Source() {
            override val id: String
                get() = request.url
        }

        /**
         * A data class representing a source that contains Audio/Video in a Byte Stream.
         *
         * @property streamProvider A function that provides an InputStream from a given position
         * @property id The id of the source
         * @property quality The quality of the source, this is used to sort the sources
         * @property title The title of the source
         * @property isVideo Whether the source is video or audio
         * @property isLive Whether the source is live
         */
        @Serializable
        data class Raw(
            @Transient
            val streamProvider: InputProvider? = null,
            override val id: String,
            override val quality: Int = 0,
            override val title: String? = null,
            override val isVideo: Boolean = false,
            override val isLive: Boolean = false
        ) : Source()

        companion object {
            /**
             * Creates a Http source from the String Url.
             *
             * @param headers The headers for the request
             * @param type The type of the source
             * @param isVideo Whether the source is video or audio
             * @param isLive Whether the source is live
             * @return The Http source
             */
            fun String.toSource(
                headers: Map<String, String> = mapOf(),
                type: SourceType = SourceType.Progressive,
                isVideo: Boolean = false,
                isLive: Boolean = false
            ): Http = Http(
                request = NetworkRequest(this, headers),
                type = type,
                isVideo = isVideo,
                isLive = isLive
            )

            /**
             * Creates a Raw source from the InputProvider.
             *
             * @param id The id of the source
             * @param isVideo Whether the source is video or audio
             * @param isLive Whether the source is live
             * @return The Raw source
             */
            fun InputProvider.toSource(
                id: String,
                isVideo: Boolean = false,
                isLive: Boolean = false
            ): Raw = Raw(
                streamProvider = this,
                id = id,
                isVideo = isVideo,
                isLive = isLive
            )
        }
    }

    /**
     * A class representing Media Decryption for a Source.
     */
    @Serializable
    sealed class Decryption {
        // Implementation of specific decryption types would go here
    }

    /**
     * A class that represents a loaded streamable media.
     *
     * There are three types of media:
     * - Subtitle - To represent a loaded subtitle media
     * - Server - To represent a loaded server media
     * - Background - To represent a loaded background media
     */
    @Serializable
    sealed class Media {
        /**
         * A data class representing a loaded subtitle for a Track.
         *
         * @property url The url of the subtitle
         * @property type The type of the subtitle
         */
        @Serializable
        data class Subtitle(
            val url: String,
            val type: SubtitleType
        ) : Media()

        /**
         * A data class representing a loaded server media for a Track.
         *
         * The sources will all load at the same time if merged is true and combined into a single media source like a M3U8 with multiple qualities.
         * If merged is false, the sources will be loaded separately and the user can switch between them.
         *
         * @property sources The list of sources for the server media
         * @property merged Whether the server media is merged or not
         */
        @Serializable
        data class Server(
            val sources: List<Source>,
            val merged: Boolean
        ) : Media()

        /**
         * A data class representing a loaded background video for a Track.
         * The sound of the background video will be removed.
         *
         * @property request The request for the background media
         */
        @Serializable
        data class Background(val request: NetworkRequest) : Media()

        companion object {
            /**
             * Creates a Subtitle media from this String Url.
             *
             * @param type The type of the subtitle
             * @return The Subtitle media
             */
            fun String.toSubtitleMedia(type: SubtitleType): Subtitle =
                Subtitle(this, type)

            /**
             * Creates a single Source server media from this String Url.
             *
             * @param headers The headers for the request
             * @param type The type of the source
             * @param isVideo Whether the source is video or audio
             * @return The Server media
             */
            fun String.toServerMedia(
                headers: Map<String, String> = mapOf(),
                type: SourceType = SourceType.Progressive,
                isVideo: Boolean = false
            ): Server = Server(
                sources = listOf(
                    this.toSource(headers, type, isVideo)
                ),
                merged = false
            )

            /**
             * Creates a Background media from this String Url.
             *
             * @param headers The headers for the request
             * @return The Background media
             */
            fun String.toBackgroundMedia(headers: Map<String, String> = mapOf()): Background =
                Background(NetworkRequest(this, headers))

            /**
             * Creates a Server media from this Source.
             *
             * @return The Server media
             */
            fun Source.toMedia(): Server = Server(listOf(this), false)
        }
    }

    companion object {
        /**
         * Creates a Streamable with the given id, quality, title, and extras for a server media.
         *
         * @param id The id of the streamable
         * @param quality The quality of the streamable
         * @param title The title of the streamable
         * @param extras Any extra data associated with the streamable
         * @return The Streamable
         */
        fun server(
            id: String,
            quality: Int,
            title: String? = null,
            extras: Map<String, String> = mapOf()
        ): Streamable = Streamable(id, quality, MediaType.Server, title, extras)

        /**
         * Creates a Streamable with the given id, quality, title, and extras for a background media.
         *
         * @param id The id of the streamable
         * @param quality The quality of the streamable
         * @param title The title of the streamable
         * @param extras Any extra data associated with the streamable
         * @return The Streamable
         */
        fun background(
            id: String,
            quality: Int,
            title: String? = null,
            extras: Map<String, String> = mapOf()
        ): Streamable = Streamable(id, quality, MediaType.Background, title, extras)

        /**
         * Creates a Streamable with the given id, quality, title, and extras for a subtitle media.
         *
         * @param id The id of the streamable
         * @param title The title of the streamable
         * @param extras Any extra data associated with the streamable
         * @return The Streamable
         */
        fun subtitle(
            id: String,
            title: String? = null,
            extras: Map<String, String> = mapOf()
        ): Streamable = Streamable(id, 0, MediaType.Subtitle, title, extras)
    }
}

/**
 * A data class representing a network request.
 *
 * @property url The url of the request
 * @property headers The headers of the request
 */
@Serializable
data class NetworkRequest(
    val url: String,
    val headers: Map<String, String> = mapOf()
)