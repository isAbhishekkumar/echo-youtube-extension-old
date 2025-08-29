package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.FollowClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.LyricsSearchClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.clients.TrackerMarkClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Feed.Companion.pagedDataOfFirst
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Track.Type
import dev.brahmkshatriya.echo.common.models.Track.Playable
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.endpoints.EchoArtistEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoArtistMoreEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoEditPlaylistEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoLibraryEndPoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoLyricsEndPoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoPlaylistEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoSearchEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoSearchSuggestionsEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoSongEndPoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoSongFeedEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoSongRelatedEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoVideoEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.EchoVisitorEndpoint
import dev.brahmkshatriya.echo.extension.endpoints.GoogleAccountResponse
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiAuthenticationState
import dev.toastbits.ytmkt.model.external.PlaylistEditor
import dev.toastbits.ytmkt.model.external.SongLikedStatus
import dev.toastbits.ytmkt.model.external.ThumbnailProvider.Quality.HIGH
import dev.toastbits.ytmkt.model.external.ThumbnailProvider.Quality.LOW
import dev.toastbits.ytmkt.model.external.mediaitem.YtmArtist
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.http.headers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

/**
 * Creates a PagedData<Shelf> from PagedData<EchoMediaItem>
 */
private fun createShelfPagedDataFromMediaItems(mediaItems: PagedData<EchoMediaItem>): PagedData<Shelf> {
    return PagedData.Continuous { continuation ->
        val page = mediaItems.loadPage(continuation)
        val shelves = page.data.map { item -> Shelf.Item(item) }
        Page(shelves, page.continuation)
    }
}
class YoutubeExtension : ExtensionClient, HomeFeedClient, TrackClient, SearchFeedClient,
    RadioClient, AlbumClient, ArtistClient, PlaylistClient, LoginClient.WebView,
    TrackerClient, TrackerMarkClient, LibraryFeedClient, ShareClient, LyricsClient, FollowClient,
    LikeClient, PlaylistEditClient, LyricsSearchClient, QuickSearchClient {

    override suspend fun getSettingItems(): List<Setting> = listOf(
        SettingSwitch(
            "High Thumbnail Quality",
            "high_quality",
            "Use high quality thumbnails, will cause more data usage.",
            false
        ),
        SettingSwitch(
            "Prefer Videos",
            "prefer_videos",
            "Prefer videos over audio when available",
            true
        ),
        SettingSwitch(
            "Show Videos",
            "show_videos",
            "Allows videos to be available when playing stuff. Instead of disabling videos, change the streaming quality as Medium in the app settings to select audio only by default.",
            false
        ),
        SettingSwitch(
            "High Quality Audio",
            "high_quality_audio",
            "Prefer high quality audio formats (256kbps+) when available",
            false
        ),
        SettingSwitch(
            "Opus Audio Preferred",
            "prefer_opus",
            "Prefer Opus audio format over AAC for better efficiency",
            true
        ),
        SettingSwitch(
            "Adaptive Audio Quality",
            "adaptive_audio",
            "Automatically adjust audio quality based on network conditions",
            true
        )
    )

    private lateinit var settings: Settings
    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    val api = YoutubeiApi(
        data_language = ENGLISH
    )
    val mobileApi = YoutubeiApi(
        data_language = "en"
    )
    init {
        configureApiClients()
    }
    private fun configureApiClients() {
        try {
            println("DEBUG: API clients will be configured with safe User-Agents at request level")
            
            println("DEBUG: API clients configured with safe User-Agents")
        } catch (e: Exception) {
            println("DEBUG: Failed to configure API clients: ${e.message}")
        }
    }
    private suspend fun ensureVisitorId() {
        try {
            println("DEBUG: Checking visitor ID, current: ${api.visitor_id}")
            if (api.visitor_id == null) {
                println("DEBUG: Getting new visitor ID")
                var visitorError: Exception? = null
                for (attempt in 1..3) {
                    try {
                        api.visitor_id = visitorEndpoint.getVisitorId()
                        println("DEBUG: Got visitor ID on attempt $attempt: ${api.visitor_id}")
                        return
                    } catch (e: Exception) {
                        visitorError = e
                        println("DEBUG: Visitor ID attempt $attempt failed: ${e.message}")
                        if (attempt < 3) {
                            kotlinx.coroutines.delay(500L * attempt) 
                        }
                    }
                }
                throw visitorError ?: Exception("Failed to get visitor ID after 3 attempts")
            } else {
                println("DEBUG: Visitor ID already exists: ${api.visitor_id}")
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to initialize visitor ID: ${e.message}")
        }
    }
    private val thumbnailQuality
        get() = if (settings.getBoolean("high_quality") == true) HIGH else LOW

    private val preferVideos
        get() = settings.getBoolean("prefer_videos") == true

    private val showVideos
        get() = settings.getBoolean("show_videos") != false

    private val highQualityAudio
        get() = settings.getBoolean("high_quality_audio") == true

    private val preferOpus
        get() = settings.getBoolean("prefer_opus") != false

    private val adaptiveAudio
        get() = settings.getBoolean("adaptive_audio") != false
    private fun getTargetVideoQuality(streamable: Streamable? = null): Int? {
        if (!showVideos) {
            println("DEBUG: Videos disabled, using any available quality")
            return null
        }
        val extras = streamable?.extras ?: emptyMap()
        println("DEBUG: Available streamable extras: ${extras.keys}")
        
        val qualitySetting = when {
            extras.containsKey("quality") -> extras["quality"] as? String
            extras.containsKey("streamQuality") -> extras["streamQuality"] as? String
            extras.containsKey("videoQuality") -> extras["videoQuality"] as? String
            else -> null
        }   
        println("DEBUG: Detected quality setting: $qualitySetting")        
        val targetQuality = when (qualitySetting?.lowercase()) {
            "lowest", "low", "144p" -> {
                println("DEBUG: App quality setting: lowest (144p)")
                144
            }
            "medium", "480p" -> {
                println("DEBUG: App quality setting: medium (480p)")
                480
            }
            "highest", "high", "720p", "1080p" -> {
                println("DEBUG: App quality setting: highest (720p)")
                720
            }
            "auto", "automatic" -> {
                println("DEBUG: App quality setting: auto, using medium (480p)")
                480
            }
            else -> {
                println("DEBUG: No quality setting found, defaulting to medium (480p)")
                480
            }
        }
        
        return targetQuality
    }
    private fun getBestVideoSourceByQuality(videoSources: List<Streamable.Source.Http>, targetQuality: Int?): Streamable.Source.Http? {
        if (videoSources.isEmpty()) {
            return null
        }        
        if (targetQuality == null) {
            println("DEBUG: No quality restriction, selecting highest quality available")
            val best = videoSources.maxByOrNull { it.quality }
            println("DEBUG: Selected source with bitrate: ${best?.quality}")
            return best
        }
        
        println("DEBUG: Filtering ${videoSources.size} video sources for target quality: ${targetQuality}p")
        videoSources.forEach { source ->
            println("DEBUG: Available video source - bitrate: ${source.quality}")
        }
        val matchingSources = videoSources.filter { source ->
            val bitrate = source.quality
            val isMatch = when (targetQuality) {
                144 -> {
                    bitrate in 50000..300000
                }
                480 -> {
                    bitrate in 300000..2000000
                }
                720 -> {
                    bitrate in 1500000..5000000
                }
                else -> {
                    true
                }
            }
            
            if (isMatch) {
                println("DEBUG: ✓ Source matches quality criteria - bitrate: $bitrate for target ${targetQuality}p")
            } else {
                println("DEBUG: ✗ Source does not match quality criteria - bitrate: $bitrate for target ${targetQuality}p")
            }
            isMatch
        }
        
        val selectedSource = if (matchingSources.isNotEmpty()) {
            val best = matchingSources.maxByOrNull { it.quality }
            println("DEBUG: Selected best matching source with bitrate: ${best?.quality}")
            best
        } else {
            println("DEBUG: No exact matches found, falling back to best available")
            val fallback = videoSources.maxByOrNull { it.quality }
            println("DEBUG: Fallback source with bitrate: ${fallback?.quality}")
            fallback
        }
        
        return selectedSource
    }
    private fun getTargetAudioQuality(networkType: String): AudioQualityLevel {
        return when {
            adaptiveAudio -> when (networkType) {
                "restricted_wifi", "mobile_data" -> AudioQualityLevel.MEDIUM
                "wifi", "ethernet" -> if (highQualityAudio) AudioQualityLevel.VERY_HIGH else AudioQualityLevel.HIGH
                else -> AudioQualityLevel.MEDIUM
            }
            highQualityAudio -> AudioQualityLevel.HIGH
            else -> AudioQualityLevel.MEDIUM
        }
    }
    private fun parseAudioFormatItag(format: Any?): Int? {
        return try {
            when (format) {
                is Map<*, *> -> {
                    (format["itag"] as? Int?) ?: (format["format_id"] as? Int?) ?: (format["id"] as? Int?)
                }
                else -> {
                    format?.javaClass?.getDeclaredField("itag")?.let { field ->
                        field.isAccessible = true
                        field.get(format) as? Int?
                    }
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to parse audio format itag: ${e.message}")
            null
        }
    }
    private fun getBestAudioSource(audioSources: List<Streamable.Source.Http>, networkType: String): Streamable.Source.Http? {
        if (audioSources.isEmpty()) {
            return null
        }
        println("DEBUG: Selecting best audio source from ${audioSources.size} sources")        
        val opusSources = audioSources.filter { source ->
            source.request.url.toString().contains("opus") || 
            source.request.url.toString().contains("webm")
        }
        val aacSources = audioSources.filter { source ->
            source.request.url.toString().contains("aac") || 
            source.request.url.toString().contains("mp4")
        }
        println("DEBUG: Found ${opusSources.size} Opus sources and ${aacSources.size} AAC sources")
        val targetQuality = getTargetAudioQuality(networkType)
        println("DEBUG: Target audio quality: $targetQuality")
        val preferredSources = when {
            preferOpus && opusSources.isNotEmpty() -> opusSources
            aacSources.isNotEmpty() -> aacSources
            opusSources.isNotEmpty() -> opusSources
            else -> audioSources
        }
        println("DEBUG: Using ${if (preferOpus) "Opus" else "AAC"} preferred sources (${preferredSources.size} available)")
        val qualityFilteredSources = preferredSources.filter { source ->
            val bitrate = source.quality
            when (targetQuality) {
                AudioQualityLevel.LOW -> bitrate <= targetQuality.maxBitrate
                AudioQualityLevel.MEDIUM -> bitrate in targetQuality.minBitrate..targetQuality.maxBitrate
                AudioQualityLevel.HIGH -> bitrate >= targetQuality.minBitrate
                AudioQualityLevel.VERY_HIGH -> bitrate >= targetQuality.minBitrate
            }
        }
        println("DEBUG: Quality-filtered sources: ${qualityFilteredSources.size}")
        val bestSource = when {
            qualityFilteredSources.isNotEmpty() -> {
                qualityFilteredSources.maxByOrNull { it.quality }
            }
            preferredSources.isNotEmpty() -> {
                preferredSources.maxByOrNull { it.quality }
            }
            else -> {
                audioSources.maxByOrNull { it.quality }
            }
        }
        println("DEBUG: Selected audio source with bitrate: ${bestSource?.quality}")
        return bestSource
    }
    private fun processAudioFormat(format: Any, networkType: String): Streamable.Source.Http? {
        try {
            val itag = parseAudioFormatItag(format)
            println("DEBUG: Processing audio format with itag: $itag")
            if (itag == null) {
                println("DEBUG: Could not parse itag, falling back to bitrate-based processing")
                return null
            }
            val audioBitrate = AUDIO_FORMAT_BITRATES[itag]
            if (audioBitrate == null) {
                println("DEBUG: Unknown audio itag: $itag, skipping")
                return null
            }
            val targetQuality = getTargetAudioQuality(networkType)
            val meetsQuality = when (targetQuality) {
                AudioQualityLevel.LOW -> audioBitrate <= targetQuality.maxBitrate
                AudioQualityLevel.MEDIUM -> audioBitrate in targetQuality.minBitrate..targetQuality.maxBitrate
                AudioQualityLevel.HIGH -> audioBitrate >= targetQuality.minBitrate
                AudioQualityLevel.VERY_HIGH -> audioBitrate >= targetQuality.minBitrate
            }
            if (!meetsQuality) {
                println("DEBUG: Audio format $itag ($audioBitrate bps) does not meet quality requirements: $targetQuality")
                return null
            }
            val isOpus = itag in listOf(
                AUDIO_OPUS_50KBPS, AUDIO_OPUS_70KBPS, AUDIO_OPUS_128KBPS,
                AUDIO_OPUS_256KBPS, AUDIO_OPUS_480KBPS_AMBISONIC, AUDIO_OPUS_35KBPS
            )
            val isAac = itag in listOf(
                AUDIO_AAC_HE_48KBPS, AUDIO_AAC_LC_128KBPS, AUDIO_AAC_LC_256KBPS,
                AUDIO_AAC_HE_30KBPS, AUDIO_AAC_HE_192KBPS_5_1, AUDIO_AAC_LC_384KBPS_5_1,
                AUDIO_AAC_LC_256KBPS_5_1
            )
            if (preferOpus && isAac && isOpus.not()) {
                println("DEBUG: Skipping AAC format $itag (Opus preferred)")
                return null
            }
            println("DEBUG: Audio format $itag ($audioBitrate bps) meets quality requirements: $targetQuality")
            return try {
                val url = when (format) {
                    is Map<*, *> -> format["url"] as? String
                    else -> format.javaClass.getDeclaredField("url")?.let { field ->
                        field.isAccessible = true
                        field.get(format) as? String
                    }
                }
                if (url != null) {
                    Streamable.Source.Http(
                        request = url.toGetRequest(),
                        quality = audioBitrate
                    ).also {
                        println("DEBUG: Created audio source for itag $itag with bitrate $audioBitrate")
                    }
                } else {
                    println("DEBUG: Could not extract URL for format $itag")
                    null
                }
            } catch (e: Exception) {
                println("DEBUG: Failed to create audio source for itag $itag: ${e.message}")
                null
            }

        } catch (e: Exception) {
            println("DEBUG: Error processing audio format: ${e.message}")
            return null
        }
    }
    private val language = ENGLISH
    private val visitorEndpoint = EchoVisitorEndpoint(api)
    private val songFeedEndPoint = EchoSongFeedEndpoint(api)
    private val artistEndPoint = EchoArtistEndpoint(api)
    private val artistMoreEndpoint = EchoArtistMoreEndpoint(api)
    private val libraryEndPoint = EchoLibraryEndPoint(api)
    private val songEndPoint = EchoSongEndPoint(api)
    private val songRelatedEndpoint = EchoSongRelatedEndpoint(api)
    private val videoEndpoint = EchoVideoEndpoint(api)
    private val mobileVideoEndpoint = EchoVideoEndpoint(mobileApi)
    private val playlistEndPoint = EchoPlaylistEndpoint(api)
    private val lyricsEndPoint = EchoLyricsEndPoint(api)
    private val searchSuggestionsEndpoint = EchoSearchSuggestionsEndpoint(api)
    private val searchEndpoint = EchoSearchEndpoint(api)
    private val editorEndpoint = EchoEditPlaylistEndpoint(api)
    companion object {
        const val ENGLISH = "en-GB"
        const val SINGLES = "Singles"
        const val SONGS = "songs"
        const val AUDIO_AAC_HE_48KBPS = 139    
        const val AUDIO_AAC_LC_128KBPS = 140   
        const val AUDIO_AAC_LC_256KBPS = 141   
        const val AUDIO_AAC_HE_192KBPS_5_1 = 256
        const val AUDIO_AAC_LC_384KBPS_5_1 = 258 
        const val AUDIO_AAC_LC_256KBPS_5_1 = 327 
        const val AUDIO_OPUS_50KBPS = 249    
        const val AUDIO_OPUS_70KBPS = 250     
        const val AUDIO_OPUS_128KBPS = 251    
        const val AUDIO_OPUS_480KBPS_AMBISONIC = 338 
        const val AUDIO_OPUS_256KBPS = 774            
        const val AUDIO_AAC_HE_30KBPS = 599   
        const val AUDIO_OPUS_35KBPS = 600    
        const val AUDIO_IAMF_900KBPS = 773    
        val AUDIO_FORMAT_PRIORITY = listOf(
            AUDIO_IAMF_900KBPS,       
            AUDIO_OPUS_256KBPS,        
            AUDIO_AAC_LC_256KBPS,      
            AUDIO_OPUS_128KBPS,        
            AUDIO_AAC_LC_128KBPS,     
            AUDIO_OPUS_70KBPS,         
            AUDIO_OPUS_50KBPS,         
            AUDIO_AAC_HE_48KBPS,       
            AUDIO_OPUS_35KBPS,         
            AUDIO_AAC_HE_30KBPS        
        )
        val AUDIO_FORMAT_BITRATES = mapOf(
            AUDIO_IAMF_900KBPS to 900000,
            AUDIO_OPUS_256KBPS to 256000,
            AUDIO_AAC_LC_256KBPS to 256000,
            AUDIO_OPUS_128KBPS to 128000,
            AUDIO_AAC_LC_128KBPS to 128000,
            AUDIO_OPUS_70KBPS to 70000,
            AUDIO_OPUS_50KBPS to 50000,
            AUDIO_AAC_HE_48KBPS to 48000,
            AUDIO_OPUS_35KBPS to 35000,
            AUDIO_AAC_HE_30KBPS to 30000,
            AUDIO_AAC_HE_192KBPS_5_1 to 192000,
            AUDIO_AAC_LC_384KBPS_5_1 to 384000,
            AUDIO_AAC_LC_256KBPS_5_1 to 256000,
            AUDIO_OPUS_480KBPS_AMBISONIC to 480000
        )
        enum class AudioQualityLevel(val minBitrate: Int, val maxBitrate: Int) {
            LOW(0, 64000),           
            MEDIUM(64001, 128000),   
            HIGH(128001, 256000),    
            VERY_HIGH(256001, Int.MAX_VALUE) 
        }
        val MOBILE_USER_AGENTS = listOf(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
        )
        val DESKTOP_USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.7258.128 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
        )
        val YOUTUBE_MUSIC_HEADERS = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "keep-alive",
            "Host" to "music.youtube.com",
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "active",
            "User-Agent" to MOBILE_USER_AGENTS[0] 
        )
        val DESKTOP_HEADERS = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "keep-alive",
            "Host" to "music.youtube.com",
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "active",
            "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
            "sec-ch-ua-arch" to "\"x86\"",
            "sec-ch-ua-bitness" to "\"64\"",
            "sec-ch-ua-form-factors" to "\"Desktop\"",
            "sec-ch-ua-full-version" to "139.0.7258.128",
            "sec-ch-ua-full-version-list" to "\"Not;A=Brand\";v=\"99.0.0.0\", \"Google Chrome\";v=\"139.0.7258.128\", \"Chromium\";v=\"139.0.7258.128\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-model" to "\"\"",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-ch-ua-platform-version" to "19.0.0",
            "sec-ch-ua-wow64" to "?0",
            "User-Agent" to DESKTOP_USER_AGENTS[0]
        )
        val VIDEO_STREAMING_HEADERS = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "keep-alive",
            "Host" to "rr1---sn-cvh7knzl.googlevideo.com", 
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "active",
            "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
            "sec-ch-ua-arch" to "\"x86\"",
            "sec-ch-ua-bitness" to "\"64\"",
            "sec-ch-ua-form-factors" to "\"Desktop\"",
            "sec-ch-ua-full-version" to "139.0.7258.128",
            "sec-ch-ua-full-version-list" to "\"Not;A=Brand\";v=\"99.0.0.0\", \"Google Chrome\";v=\"139.0.7258.128\", \"Chromium\";v=\"139.0.7258.128\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-model" to "\"\"",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-ch-ua-platform-version" to "19.0.0",
            "sec-ch-ua-wow64" to "?0",
            "User-Agent" to DESKTOP_USER_AGENTS[0],
            "X-Browser-Channel" to "stable",
            "X-Browser-Copyright" to "Copyright 2025 Google LLC. All rights reserved.",
            "X-Browser-Validation" to "XPdmRdCCj2OkELQ2uovjJFk6aKA=",
            "X-Browser-Year" to "2025",
            "X-Client-Data" to "CLDtygE="
        )
        val GOOGLEVIDEO_HOST_PATTERNS = listOf(
            "rr1---sn-",
            "rr2---sn-",
            "rr3---sn-",
            "rr4---sn-",
            "rr5---sn-",
            "r1---sn-",
            "r2---sn-",
            "r3---sn-",
            "r4---sn-",
            "r5---sn-",
            ".googlevideo.com"
        )
    }
    override suspend fun loadHomeFeed(): Feed<Shelf> {
        val tabs = listOf<Tab>() // YouTube home doesn't have tabs currently
        return Feed(tabs) { tab ->
            val pagedData = PagedData.Continuous { continuation ->
                val result = songFeedEndPoint.getSongFeed(
                    params = tab?.id, continuation = continuation
                ).getOrThrow()
                val data = result.layouts.map { itemLayout ->
                    itemLayout.toShelf(api, SINGLES, thumbnailQuality)
                }
                Page(data, result.ctoken)
            }
            Feed.Data(pagedData)
        }
    }
    private fun detectNetworkType(): String {
        return try {
            val testConnection = java.net.URL("https://www.google.com").openConnection() as java.net.HttpURLConnection
            testConnection.connectTimeout = 2000
            testConnection.readTimeout = 2000
            testConnection.requestMethod = "HEAD"
            val responseCode = testConnection.responseCode
            val youtubeTest = java.net.URL("https://music.youtube.com").openConnection() as java.net.HttpURLConnection
            youtubeTest.connectTimeout = 3000
            youtubeTest.readTimeout = 3000
            youtubeTest.requestMethod = "HEAD"
            val youtubeResponse = youtubeTest.responseCode          
            when {
                responseCode == 200 && youtubeResponse == 200 -> "mobile_data"
                responseCode == 200 && youtubeResponse != 200 -> "restricted_wifi"
                else -> "restricted_wifi"
            }
        } catch (e: Exception) {
            println("DEBUG: Network detection failed, assuming restricted WiFi: ${e.message}")
            "restricted_wifi"
        }
    }
    private fun getRandomUserAgent(isMobile: Boolean = true): String {
        val agents = if (isMobile) MOBILE_USER_AGENTS else DESKTOP_USER_AGENTS
        return agents.random()
    }
    private fun getSafeUserAgent(isMobile: Boolean = true): String {
        return getRandomUserAgent(isMobile)
    }
    private fun getVideoStreamingHeaders(url: String, strategy: String): Map<String, String> {
        val headers = VIDEO_STREAMING_HEADERS.toMutableMap()
        val host = try {
            val uri = java.net.URI(url)
            uri.host
        } catch (e: Exception) {
            "rr1---sn-cvh7knzl.googlevideo.com" 
        }        
        headers["Host"] = host
        when (strategy) {
            "mobile_emulation", "aggressive_mobile" -> {
                headers["User-Agent"] = getSafeUserAgent(true)
                headers["sec-ch-ua-mobile"] = "?1"
                headers["sec-ch-ua-platform"] = "\"Android\""
                headers["sec-ch-ua-form-factors"] = "\"Mobile\""
                headers["sec-ch-ua-model"] = "Pixel 8"
                headers["sec-ch-ua-platform-version"] = "14.0.0"
                headers["sec-ch-ua-arch"] = "\"\""
                headers["sec-ch-ua-bitness"] = "\"\""
            }
            "desktop_fallback" -> {
                headers["User-Agent"] = getSafeUserAgent(false)
                headers["sec-ch-ua-mobile"] = "?0"
                headers["sec-ch-ua-platform"] = "\"Windows\""
                headers["sec-ch-ua-form-factors"] = "\"Desktop\""
                headers["sec-ch-ua-model"] = "\"\""
                headers["sec-ch-ua-platform-version"] = "19.0.0"
                headers["sec-ch-ua-arch"] = "\"x86\""
                headers["sec-ch-ua-bitness"] = "\"64\""
            }
        }       
        return headers
    }
    private fun getEnhancedHeaders(strategy: String, attempt: Int): Map<String, String> {
        val baseHeaders = YOUTUBE_MUSIC_HEADERS.toMutableMap()
        baseHeaders["User-Agent"] = getSafeUserAgent(
            when (strategy) {
                "mobile_emulation", "aggressive_mobile" -> true
                "desktop_fallback" -> false
                else -> true 
            }
        )        
        return when (strategy) {
            "mobile_emulation" -> {
                baseHeaders.apply {
                    put("User-Agent", getSafeUserAgent(true))
                    put("Sec-Ch-Ua-Mobile", "?1")
                    put("Sec-Ch-Ua-Platform", "\"Android\"")
                    putAll(mapOf(
                        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
                        "sec-ch-ua-arch" to "\"\"",
                        "sec-ch-ua-bitness" to "\"\"",
                        "sec-ch-ua-form-factors" to "\"Mobile\"",
                        "sec-ch-ua-full-version" to "139.0.0.0",
                        "sec-ch-ua-full-version-list" to "\"Not;A=Brand\";v=\"8.0.0.0\", \"Chromium\";v=\"139.0.0.0\", \"Google Chrome\";v=\"139.0.0.0\"",
                        "sec-ch-ua-model" to "Pixel 8",
                        "sec-ch-ua-platform-version" to "14.0.0",
                        "sec-ch-ua-wow64" to "?0"
                    ))
                    if (attempt > 2) {
                        put("Accept-Language", "en-US,en;q=0.8")
                        put("Cache-Control", "max-age=0")
                    }
                }
            }
            "desktop_fallback" -> {
                baseHeaders.apply {
                    put("User-Agent", getSafeUserAgent(false))
                    put("Sec-Ch-Ua-Mobile", "?0")
                    put("Sec-Ch-Ua-Platform", "\"Windows\"")
                    putAll(mapOf(
                        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
                        "sec-ch-ua-arch" to "\"x86\"",
                        "sec-ch-ua-bitness" to "\"64\"",
                        "sec-ch-ua-form-factors" to "\"Desktop\"",
                        "sec-ch-ua-full-version" to "139.0.7258.128",
                        "sec-ch-ua-full-version-list" to "\"Not;A=Brand\";v=\"99.0.0.0\", \"Google Chrome\";v=\"139.0.7258.128\", \"Chromium\";v=\"139.0.7258.128\"",
                        "sec-ch-ua-model" to "\"\"",
                        "sec-ch-ua-platform-version" to "19.0.0",
                        "sec-ch-ua-wow64" to "?0"
                    ))
                }
            }
            "aggressive_mobile" -> {
                baseHeaders.apply {
                    put("User-Agent", getSafeUserAgent(true))
                    put("Accept", "*/*")
                    put("Accept-Language", "en-US,en;q=0.5")
                    put("DNT", "1")
                    put("Connection", "keep-alive")
                    putAll(mapOf(
                        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
                        "sec-ch-ua-mobile" to "?1",
                        "sec-ch-ua-platform" to "\"Android\"",
                        "sec-ch-ua-arch" to "\"\"",
                        "sec-ch-ua-bitness" to "\"\"",
                        "sec-ch-ua-form-factors" to "\"Mobile\"",
                        "sec-ch-ua-full-version" to "139.0.0.0",
                        "sec-ch-ua-model" to "SM-S918B",
                        "sec-ch-ua-platform-version" to "14.0.0",
                        "sec-ch-ua-wow64" to "?0"
                    ))
                }
            }
            else -> {
                baseHeaders.apply {
                    put("User-Agent", getSafeUserAgent(true))
                    putAll(mapOf(
                        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
                        "sec-ch-ua-mobile" to "?1",
                        "sec-ch-ua-platform" to "\"Android\"",
                        "sec-ch-ua-arch" to "\"\"",
                        "sec-ch-ua-bitness" to "\"\"",
                        "sec-ch-ua-form-factors" to "\"Mobile\"",
                        "sec-ch-ua-full-version" to "139.0.0.0",
                        "sec-ch-ua-model" to "Pixel 7",
                        "sec-ch-ua-platform-version" to "13.0.0",
                        "sec-ch-ua-wow64" to "?0"
                    ))
                }
            }
        }
    }
    private fun getStrategyForNetwork(attempt: Int, networkType: String): String {
        return when (networkType) {
            "restricted_wifi" -> {
                when (attempt) {
                    1 -> "mobile_emulation"      
                    2 -> "aggressive_mobile"      
                    3 -> "reset_visitor"          
                    4 -> "desktop_fallback"       
                    5 -> "alternate_params"       
                    else -> "mobile_emulation"
                }
            }
            "mobile_data" -> {
                when (attempt) {
                    1 -> "mobile_emulation"       
                    2 -> "standard"               
                    3 -> "alternate_params"       
                    4 -> "reset_visitor"          
                    5 -> "desktop_fallback"       
                    else -> "mobile_emulation"
                }
            }
            else -> {
                when (attempt) {
                    1 -> "standard"
                    2 -> "mobile_emulation"
                    3 -> "alternate_params"
                    4 -> "reset_visitor"
                    5 -> "aggressive_mobile"
                    else -> "standard"
                }
            }
        }
    }
    private fun createPostRequest(url: String, headers: Map<String, String>, body: String? = null): Streamable.Source.Http {
        val enhancedUrl = if (body != null) {
            if (url.contains("?")) {
                "$url&post_data=${body.hashCode()}"
            } else {
                "$url?post_data=${body.hashCode()}"
            }
        } else {
            url
        }
        val finalHeaders = if (GOOGLEVIDEO_HOST_PATTERNS.any { url.contains(it) }) {
            getVideoStreamingHeaders(url, "desktop_fallback") 
        } else {
            headers.toMutableMap().apply {
                putAll(YOUTUBE_MUSIC_HEADERS)
            }
        }
        val headerString = finalHeaders.map { (key, value) ->
            "${key.hashCode()}=${value.hashCode()}"
        }.joinToString("&")
        
        val finalUrl = if (enhancedUrl.contains("?")) {
            "$enhancedUrl&headers=$headerString"
        } else {
            "$enhancedUrl?headers=$headerString"
        }
        
        return Streamable.Source.Http(
            finalUrl.toGetRequest(),
            quality = 0 
        )
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable, isDownload: Boolean
    ): Streamable.Media {
        return when (streamable.type) {
            Streamable.MediaType.Server -> when (streamable.id) {
                "AUDIO_MP3", "AUDIO_MP4", "AUDIO_WEBM" -> {
                    println("DEBUG: Loading audio stream for videoId: ${streamable.extras["videoId"]}")
                    ensureVisitorId()                 
                    val videoId = streamable.extras["videoId"]!!
                    var audioSources = mutableListOf<Streamable.Source.Http>()
                    var lastError: Exception? = null
                    val networkType = detectNetworkType()
                    println("DEBUG: Detected network type: $networkType")
                    for (attempt in 1..5) {
                        try {
                            println("DEBUG: Audio attempt $attempt of 5 on $networkType")
                            if (attempt > 1) {
                                val delay = (500L * attempt) + (Math.random() * 1000L).toLong()
                                println("DEBUG: Adding random delay: ${delay}ms")
                                kotlinx.coroutines.delay(delay)
                            }
                            val strategy = getStrategyForNetwork(attempt, networkType)
                            println("DEBUG: Using strategy: $strategy for $networkType")
                            when (strategy) {
                                "reset_visitor" -> {
                                    println("DEBUG: Resetting visitor ID")
                                    api.visitor_id = null
                                    ensureVisitorId()
                                }
                                "mobile_emulation", "aggressive_mobile", "desktop_fallback" -> {
                                    println("DEBUG: Applying $strategy strategy with enhanced headers")
                                }
                            }
                            val useDifferentParams = strategy != "standard"
                            val currentVideoEndpoint = when (strategy) {
                                "mobile_emulation", "aggressive_mobile" -> mobileVideoEndpoint
                                "desktop_fallback" -> videoEndpoint  
                                else -> videoEndpoint
                            }
                            val (video, _) = currentVideoEndpoint.getVideo(useDifferentParams, videoId)
                            val audioSources = mutableListOf<Streamable.Source.Http>()
                            val videoSources = mutableListOf<Streamable.Source.Http>()
                            
                            video.streamingData.adaptiveFormats.forEach { format ->
                                val mimeType = format.mimeType.lowercase()
                                val originalUrl = format.url ?: return@forEach
                                val isAudioFormat = when {
                                    mimeType.contains("audio/mp4") -> true
                                    mimeType.contains("audio/webm") -> true
                                    mimeType.contains("audio/mp3") || mimeType.contains("audio/mpeg") -> true
                                    else -> false
                                }
                                
                                val isVideoFormat = when {
                                    mimeType.contains("video/mp4") -> true
                                    mimeType.contains("video/webm") -> true
                                    else -> false
                                }     
                                when {
                                    isAudioFormat -> {
                                        println("DEBUG: Processing audio format: $mimeType")
                                        val enhancedAudioSource = processAudioFormat(format, networkType)
                                        if (enhancedAudioSource != null) {
                                            audioSources.add(enhancedAudioSource)
                                            println("DEBUG: Added enhanced audio source (quality: ${enhancedAudioSource.quality}, mimeType: $mimeType)")
                                        } else {
                                            println("DEBUG: Enhanced processing failed, using fallback for: $mimeType")
                                            
                                            val qualityValue = when {
                                                format.bitrate > 0 -> {
                                                    val baseBitrate = format.bitrate.toInt()
                                                    when (networkType) {
                                                        "restricted_wifi" -> minOf(baseBitrate, 128000)
                                                        "mobile_data" -> minOf(baseBitrate, 192000)
                                                        else -> baseBitrate
                                                    }
                                                }
                                                format.audioSampleRate != null -> {
                                                    val sampleRate = format.audioSampleRate!!.toInt()
                                                    when (networkType) {
                                                        "restricted_wifi" -> minOf(sampleRate, 128000)
                                                        "mobile_data" -> minOf(sampleRate, 192000)
                                                        else -> sampleRate
                                                    }
                                                }
                                                else -> {
                                                    when (networkType) {
                                                        "restricted_wifi" -> 96000
                                                        "mobile_data" -> 128000
                                                        else -> 192000
                                                    }
                                                }
                                            } 
                                            val freshUrl = generateEnhancedUrl(originalUrl, attempt, strategy, networkType)
                                            val headers = generateMobileHeaders(strategy, networkType)
                                            
                                            val audioSource = when (strategy) {
                                                "mobile_emulation", "aggressive_mobile" -> {
                                                    createPostRequest(freshUrl, headers, "rn=1")
                                                }
                                                "desktop_fallback" -> {
                                                    createPostRequest(freshUrl, headers, null)
                                                }
                                                else -> {
                                                    Streamable.Source.Http(
                                                        freshUrl.toGetRequest(),
                                                        quality = qualityValue
                                                    )
                                                }
                                            }     
                                            audioSources.add(audioSource)
                                            println("DEBUG: Added fallback audio source (quality: $qualityValue, mimeType: $mimeType)")
                                        }
                                    }                                    
                                    isVideoFormat && showVideos -> {
                                        val qualityValue = format.bitrate?.toInt() ?: 0
                                        val freshUrl = generateEnhancedUrl(originalUrl, attempt, strategy, networkType)
                                        val headers = generateMobileHeaders(strategy, networkType)
                                        
                                        val videoSource = when (strategy) {
                                            "mobile_emulation", "aggressive_mobile" -> {
                                                createPostRequest(freshUrl, headers, "rn=1")
                                            }
                                            "desktop_fallback" -> {
                                                createPostRequest(freshUrl, headers, null)
                                            }
                                            else -> {
                                                Streamable.Source.Http(
                                                    NetworkRequest(freshUrl, emptyMap()),
                                                    quality = qualityValue
                                                )
                                            }
                                        }                                        
                                        videoSources.add(videoSource)
                                        println("DEBUG: Added video source (quality: $qualityValue, mimeType: $mimeType)")
                                    }
                                }
                            }
                            val mpdUrl = try {
                                video.streamingData.javaClass.getDeclaredField("dashManifestUrl").let { field ->
                                    field.isAccessible = true
                                    field.get(video.streamingData) as? String
                                }
                            } catch (e: Exception) {
                                null
                            }
                            
                            if (mpdUrl != null && showVideos) {
                                println("DEBUG: Found MPD stream URL: $mpdUrl")
                                val mpdMedia = handleMPDStream(mpdUrl, strategy, networkType)
                                lastError = null
                                return mpdMedia
                            }
                            val targetQuality = getTargetVideoQuality(streamable)
                            println("DEBUG: Target video quality: ${targetQuality ?: "any"}")
                            
                            val resultMedia = when {
                                preferVideos && videoSources.isNotEmpty() && audioSources.isNotEmpty() -> {
                                    println("DEBUG: Creating merged audio+video stream")
                                    val bestAudioSource = getBestAudioSource(audioSources, networkType)
                                    val bestVideoSource = getBestVideoSourceByQuality(videoSources, targetQuality)
                                    
                                    if (bestAudioSource != null && bestVideoSource != null) {
                                        Streamable.Media.Server(
                                            sources = listOf(bestAudioSource, bestVideoSource),
                                            merged = true
                                        )
                                    } else {
                                        val fallbackAudioSource = getBestAudioSource(audioSources, networkType)
                                        if (fallbackAudioSource != null) {
                                            Streamable.Media.Server(listOf(fallbackAudioSource), false)
                                        } else {
                                            throw Exception("No valid audio sources found")
                                        }
                                    }
                                }
                                
                                showVideos && videoSources.isNotEmpty() && !preferVideos -> {
                                    println("DEBUG: Creating audio stream (video sources available but not preferred)")
                                    val bestAudioSource = getBestAudioSource(audioSources, networkType)
                                    if (bestAudioSource != null) {
                                        Streamable.Media.Server(listOf(bestAudioSource), false)
                                    } else {
                                        throw Exception("No valid audio sources found")
                                    }
                                }
                                
                                audioSources.isNotEmpty() -> {
                                    println("DEBUG: Creating audio-only stream")
                                    val bestAudioSource = getBestAudioSource(audioSources, networkType)
                                    if (bestAudioSource != null) {
                                        Streamable.Media.Server(listOf(bestAudioSource), false)
                                    } else {
                                        throw Exception("No valid audio sources found")
                                    }
                                }
                                
                                else -> {
                                    throw Exception("No valid media sources found")
                                }
                            }                            
                            lastError = null
                            return resultMedia
                            
                        } catch (e: Exception) {
                            lastError = e
                            println("DEBUG: Audio attempt $attempt failed with strategy ${getStrategyForNetwork(attempt, networkType)}: ${e.message}")                            
                            if (attempt < 5) {
                                val delayTime = when (attempt) {
                                    1 -> 500L  
                                    2 -> 1000L 
                                    3 -> 2000L 
                                    4 -> 3000L 
                                    else -> 500L
                                }
                                println("DEBUG: Waiting ${delayTime}ms before next attempt")
                                kotlinx.coroutines.delay(delayTime)
                            }
                        }
                    }
                    val errorMsg = "All audio attempts failed on $networkType. This might be due to network restrictions. Last error: ${lastError?.message}"
                    println("DEBUG: $errorMsg")
                    throw Exception(errorMsg)
                }
                
                "VIDEO_MP4", "VIDEO_WEBM" -> {
                    println("DEBUG: Loading video stream for videoId: ${streamable.extras["videoId"]}")
                    
                    if (!showVideos) {
                        throw Exception("Video streaming is disabled in settings")
                    }
                    ensureVisitorId()
                    
                    val videoId = streamable.extras["videoId"]!!
                    var lastError: Exception? = null
                    val networkType = detectNetworkType()
                    println("DEBUG: Detected network type: $networkType")
                    for (attempt in 1..5) {
                        try {
                            println("DEBUG: Video attempt $attempt of 5 on $networkType")
                            if (attempt > 1) {
                                val delay = (500L * attempt) + (Math.random() * 1000L).toLong()
                                println("DEBUG: Adding random delay: ${delay}ms")
                                kotlinx.coroutines.delay(delay)
                            }
                            val strategy = getStrategyForNetwork(attempt, networkType)
                            println("DEBUG: Using strategy: $strategy for $networkType")
                            when (strategy) {
                                "reset_visitor" -> {
                                    println("DEBUG: Resetting visitor ID")
                                    api.visitor_id = null
                                    ensureVisitorId()
                                }
                                "mobile_emulation", "aggressive_mobile", "desktop_fallback" -> {
                                    println("DEBUG: Applying $strategy strategy with enhanced headers")
                                }
                            }
                            val useDifferentParams = strategy != "standard"
                            val currentVideoEndpoint = when (strategy) {
                                "mobile_emulation", "aggressive_mobile" -> mobileVideoEndpoint
                                "desktop_fallback" -> videoEndpoint
                                else -> videoEndpoint
                            }
                            val (video, _) = currentVideoEndpoint.getVideo(useDifferentParams, videoId)
                            val mpdUrl = try {
                                video.streamingData.javaClass.getDeclaredField("dashManifestUrl").let { field ->
                                    field.isAccessible = true
                                    field.get(video.streamingData) as? String
                                }
                            } catch (e: Exception) {
                                null
                            }
                            
                            if (mpdUrl != null) {
                                println("DEBUG: Found MPD stream URL for video: $mpdUrl")
                                val mpdMedia = handleMPDStream(mpdUrl, strategy, networkType)
                                lastError = null
                                return mpdMedia
                            }
                            val audioSources = mutableListOf<Streamable.Source.Http>()
                            val videoSources = mutableListOf<Streamable.Source.Http>()
                            
                            video.streamingData.adaptiveFormats.forEach { format ->
                                val mimeType = format.mimeType.lowercase()
                                val originalUrl = format.url ?: return@forEach  
                                val isAudioFormat = when {
                                    mimeType.contains("audio/mp4") -> true
                                    mimeType.contains("audio/webm") -> true
                                    mimeType.contains("audio/mp3") || mimeType.contains("audio/mpeg") -> true
                                    else -> false
                                }
                                val isVideoFormat = when {
                                    mimeType.contains("video/mp4") -> true
                                    mimeType.contains("video/webm") -> true
                                    else -> false
                                }
                                when {
                                    isAudioFormat -> {
                                        val qualityValue = format.bitrate?.toInt() ?: 192000
                                        val freshUrl = generateEnhancedUrl(originalUrl, attempt, strategy, networkType)
                                        val headers = generateMobileHeaders(strategy, networkType)
                                        
                                        val audioSource = when (strategy) {
                                            "mobile_emulation", "aggressive_mobile" -> {
                                                createPostRequest(freshUrl, headers, "rn=1")
                                            }
                                            "desktop_fallback" -> {
                                                createPostRequest(freshUrl, headers, null)
                                            }
                                            else -> {
                                                Streamable.Source.Http(
                                                    NetworkRequest(freshUrl, emptyMap()),
                                                    quality = qualityValue
                                                )
                                            }
                                        }
                                        
                                        audioSources.add(audioSource)
                                    } 
                                    isVideoFormat -> {
                                        val qualityValue = format.bitrate?.toInt() ?: 0
                                        val freshUrl = generateEnhancedUrl(originalUrl, attempt, strategy, networkType)
                                        val headers = generateMobileHeaders(strategy, networkType)
                                        
                                        val videoSource = when (strategy) {
                                            "mobile_emulation", "aggressive_mobile" -> {
                                                createPostRequest(freshUrl, headers, "rn=1")
                                            }
                                            "desktop_fallback" -> {
                                                createPostRequest(freshUrl, headers, null)
                                            }
                                            else -> {
                                                Streamable.Source.Http(
                                                    NetworkRequest(freshUrl, emptyMap()),
                                                    quality = qualityValue
                                                )
                                            }
                                        }
                                        
                                        videoSources.add(videoSource)
                                    }
                                }
                            }
                            val targetQuality = getTargetVideoQuality(streamable)
                            println("DEBUG: Video mode - Target video quality: ${targetQuality ?: "any"}") 
                            val resultMedia = when {
                                videoSources.isNotEmpty() && audioSources.isNotEmpty() -> {
                                    println("DEBUG: Creating merged audio+video stream")
                                    val bestAudioSource = getBestAudioSource(audioSources, networkType)
                                    val bestVideoSource = getBestVideoSourceByQuality(videoSources, targetQuality)
                                    
                                    if (bestAudioSource != null && bestVideoSource != null) {
                                        Streamable.Media.Server(
                                            sources = listOf(bestAudioSource, bestVideoSource),
                                            merged = true
                                        )
                                    } else {
                                        throw Exception("Could not create merged video stream")
                                    }
                                }  
                                videoSources.isNotEmpty() -> {
                                    println("DEBUG: Creating video-only stream")
                                    val bestVideoSource = getBestVideoSourceByQuality(videoSources, targetQuality)
                                    if (bestVideoSource != null) {
                                        Streamable.Media.Server(listOf(bestVideoSource), false)
                                    } else {
                                        throw Exception("No valid video sources found")
                                    }
                                }
                                
                                else -> {
                                    throw Exception("No valid video sources found")
                                }
                            }
                            lastError = null
                            return resultMedia
                            
                        } catch (e: Exception) {
                            lastError = e
                            println("DEBUG: Video attempt $attempt failed with strategy ${getStrategyForNetwork(attempt, networkType)}: ${e.message}")
                            
                            if (attempt < 5) {
                                val delayTime = when (attempt) {
                                    1 -> 500L
                                    2 -> 1000L
                                    3 -> 2000L
                                    4 -> 3000L
                                    else -> 1000L
                                }
                                kotlinx.coroutines.delay(delayTime)
                            }
                        }
                    }
                    val errorMsg = "All video attempts failed on $networkType. Last error: ${lastError?.message}"
                    println("DEBUG: $errorMsg")
                    throw Exception(errorMsg)
                }
                
                else -> throw IllegalArgumentException("Unknown server streamable ID: ${streamable.id}")
            }
            Streamable.MediaType.Background -> throw IllegalArgumentException("Background media type not supported")
            Streamable.MediaType.Subtitle -> throw IllegalArgumentException("Subtitle media type not supported")
        }
    }
    private fun generateEnhancedUrl(originalUrl: String, attempt: Int, strategy: String, networkType: String): String {
        val timestamp = System.currentTimeMillis()
        val random = java.util.Random().nextInt(1000000)
        val baseUrl = if (originalUrl.contains("?")) {
            originalUrl.substringBefore("?")
        } else {
            originalUrl
        }
        val existingParams = if (originalUrl.contains("?")) {
            originalUrl.substringAfter("?").split("&").associate {
                val (key, value) = it.split("=", limit = 2)
                key to (value ?: "")
            }
        } else {
            emptyMap()
        }.toMutableMap()
        when (strategy) {
            "standard" -> {
                existingParams["t"] = timestamp.toString()
                existingParams["r"] = random.toString()
                existingParams["att"] = attempt.toString()
                existingParams["nw"] = networkType
            }
            "alternate_params" -> {
                existingParams["time"] = (timestamp + 1000).toString()
                existingParams["rand"] = (random + 1000).toString()
                existingParams["attempt"] = attempt.toString()
                existingParams["nw"] = networkType
                existingParams["alr"] = "yes" 
            }
            "reset_visitor" -> {
                existingParams["ts"] = (timestamp + 2000).toString()
                existingParams["rn"] = (random + 2000).toString()
                existingParams["at"] = attempt.toString()
                existingParams["reset"] = "1"
                existingParams["nw"] = networkType
                existingParams["svpuc"] = "1" 
            }
            "mobile_emulation" -> {
                existingParams["_t"] = timestamp.toString()
                existingParams["_r"] = random.toString()
                existingParams["_a"] = attempt.toString()
                existingParams["mobile"] = "1"
                existingParams["android"] = "1"
                existingParams["nw"] = networkType
                existingParams["gir"] = "yes" 
                existingParams["alr"] = "yes" 
            }
            "aggressive_reset" -> {
                existingParams["cache_bust"] = (timestamp + 5000).toString()
                existingParams["random_id"] = (random + 5000).toString()
                existingParams["try_num"] = attempt.toString()
                existingParams["fresh"] = "1"
                existingParams["aggressive"] = "1"
                existingParams["nw"] = networkType
                existingParams["svpuc"] = "1"
                existingParams["gir"] = "yes"
                existingParams["alr"] = "yes"
            }
        }
        val paramString = existingParams.map { (key, value) ->
            "$key=$value"
        }.joinToString("&")
        
        return "$baseUrl?$paramString"
    }
    private fun generateMobileHeaders(strategy: String, networkType: String): Map<String, String> {
        val baseHeaders = mutableMapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Accept-Language" to "en-GB,en;q=0.9,en-US;q=0.8,hi;q=0.7",
            "Connection" to "keep-alive",
            "Host" to "music.youtube.com",
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "active"
        )
        when (strategy) {
            "mobile_emulation" -> {
                baseHeaders.putAll(mapOf(
                    "User-Agent" to getSafeUserAgent(true),
                    "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                    "sec-ch-ua-arch" to "\"\"",
                    "sec-ch-ua-bitness" to "\"\"",
                    "sec-ch-ua-form-factors" to "\"Mobile\"",
                    "sec-ch-ua-full-version" to "120.0.6099.230",
                    "sec-ch-ua-full-version-list" to "\"Not_A Brand\";v=\"8.0.0.0\", \"Chromium\";v=\"120.0.6099.230\", \"Google Chrome\";v=\"120.0.6099.230\"",
                    "sec-ch-ua-mobile" to "?1",
                    "sec-ch-ua-model" to "vivo 1916",
                    "sec-ch-ua-platform" to "Android",
                    "sec-ch-ua-platform-version" to "13.0.0",
                    "sec-ch-ua-wow64" to "?0",
                    "Cache-Control" to "no-cache",
                    "Pragma" to "no-cache"
                ))
            }
            "aggressive_mobile" -> {
                baseHeaders.putAll(mapOf(
                    "User-Agent" to getSafeUserAgent(true),
                    "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                    "sec-ch-ua-mobile" to "?1",
                    "sec-ch-ua-platform" to "\"Android\"",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "DNT" to "1",
                    "Upgrade-Insecure-Requests" to "1"
                ))
            }
            "desktop_fallback" -> {
                DESKTOP_HEADERS.toMutableMap().apply {
                    put("Accept-Language", "en-US,en;q=0.8,en-GB;q=0.6")
                    put("Cache-Control", "no-cache")
                    put("Pragma", "no-cache")
                }
            }
            else -> {
                baseHeaders.putAll(mapOf(
                    "User-Agent" to getSafeUserAgent(true),
                    "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                    "sec-ch-ua-mobile" to "?1",
                    "sec-ch-ua-platform" to "\"Android\""
                ))
            }
        }
        
        return baseHeaders
    }
    private suspend fun handleMPDStream(mpdUrl: String, strategy: String, networkType: String): Streamable.Media {
        println("DEBUG: Processing MPD stream from: $mpdUrl")
        
        return try {
            val audioUrl = "$mpdUrl/audio"
            val videoUrl = "$mpdUrl/video"
            val enhancedAudioUrl = generateEnhancedUrl(audioUrl, 1, strategy, networkType)
            val enhancedVideoUrl = generateEnhancedUrl(videoUrl, 1, strategy, networkType)
            val audioHeaders = generateMobileHeaders(strategy, networkType)
            val videoHeaders = generateMobileHeaders(strategy, networkType)
            val audioSource = when (strategy) {
                "mobile_emulation", "aggressive_mobile" -> {
                    createPostRequest(enhancedAudioUrl, audioHeaders, "rn=1")
                }
                "desktop_fallback" -> {
                    createPostRequest(enhancedAudioUrl, audioHeaders, null)
                }
                else -> {
                    Streamable.Source.Http(
                        enhancedAudioUrl.toGetRequest(),
                        quality = 192000 
                    )
                }
            }
            val videoSource = when (strategy) {
                "mobile_emulation", "aggressive_mobile" -> {
                    createPostRequest(enhancedVideoUrl, videoHeaders, "rn=1")
                }
                "desktop_fallback" -> {
                    createPostRequest(enhancedVideoUrl, videoHeaders, null)
                }
                else -> {
                    Streamable.Source.Http(
                        enhancedVideoUrl.toGetRequest(),
                        quality = 1000000 
                    )
                }
            }
            Streamable.Media.Server(
                sources = listOf(audioSource, videoSource),
                merged = true
            )
            
        } catch (e: Exception) {
            println("DEBUG: Failed to process MPD stream: ${e.message}")
            throw Exception("MPD stream processing failed: ${e.message}")
        }
    }

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = coroutineScope {
        ensureVisitorId()
        
        println("DEBUG: Loading track: ${track.title} (${track.id})")
        
        val deferred = async { songEndPoint.loadSong(track.id).getOrThrow() }
        val (video, type) = videoEndpoint.getVideo(true, track.id)

        println("DEBUG: Video type: $type")

        val resolvedTrack = null 

        val audioFiles = video.streamingData.adaptiveFormats.mapNotNull {
            if (!it.mimeType.contains("audio")) return@mapNotNull null
            it.audioSampleRate.toString() to it.url!!
        }.toMap()
        println("DEBUG: Audio formats found: ${audioFiles.keys}")
        
        val newTrack = resolvedTrack ?: deferred.await()
        val resultTrack = newTrack.copy(
            description = video.videoDetails.shortDescription,
            artists = newTrack.artists.ifEmpty {
                video.videoDetails.run { listOf(Artist(channelId, author)) }
            },
            streamables = listOfNotNull(
                Streamable.server(
                    "AUDIO_MP3",
                    0,
                    "Audio Stream (MP3/MP4)",
                    mutableMapOf<String, String>().apply { put("videoId", track.id) }
                ).takeIf { audioFiles.isNotEmpty() }
            ),
            plays = video.videoDetails.viewCount?.toLongOrNull(),
            // Update extras with video-specific information
            extras = newTrack.extras.toMutableMap().apply {
                put("videoId", track.id)
                put("channelId", video.videoDetails.channelId ?: "")
                put("author", video.videoDetails.author ?: "")
                put("viewCount", (video.videoDetails.viewCount ?: "0").toString())
                put("lengthSeconds", (video.videoDetails.lengthSeconds ?: "0").toString())
            }
        )
        
        println("DEBUG: Streamables created: ${resultTrack.streamables.size}")
        resultTrack.streamables.forEach { streamable ->
            println("DEBUG: Streamable: ${streamable.id} with extras: ${streamable.extras.keys}")
        }
        
        resultTrack
    }

    private suspend fun loadRelated(track: Track) = track.run {
        val relatedId = extras["relatedId"] ?: throw Exception("No related id found.")
        songFeedEndPoint.getSongFeed(browseId = relatedId).getOrThrow().layouts.map {
            it.toShelf(api, SINGLES, thumbnailQuality)
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        val shelves = loadRelated(track)
        return Feed(emptyList()) { _ -> PagedData.Single { shelves }.toFeedData() }
    }

    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        searchSuggestionsEndpoint.delete(item as QuickSearchItem.Query)
    }

    override suspend fun quickSearch(query: String) = query.takeIf { it.isNotBlank() }?.run {
        try {
            api.SearchSuggestions.getSearchSuggestions(this).getOrThrow()
                .map { QuickSearchItem.Query(it.text, it.is_from_history) }
        } catch (e: NullPointerException) {
            null
        } catch (e: ConnectTimeoutException) {
            null
        }
    } ?: listOf()


    private var oldSearch: Pair<String, List<Shelf>>? = null
    
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val tabs = if (query.isNotBlank()) {
            // Get search tabs for the query
            val search = api.Search.search(query, null).getOrThrow()
            oldSearch = query to search.categories.map { (itemLayout, _) ->
                // Fix shelf items for search results
                try {
                    SearchResultsFixer.fixSearchResultShelf(itemLayout.toShelf(api, SINGLES, thumbnailQuality))
                } catch (e: Exception) {
                    // If fixing fails, return the original shelf to prevent crashes
                    itemLayout.toShelf(api, SINGLES, thumbnailQuality)
                }
            }
            val searchTabs = search.categories.mapNotNull { (item, filter) ->
                filter?.let {
                    Tab(
                        id = it.params,
                        title = item.title?.getString(language) ?: "???",
                        isSort = false,
                        extras = mapOf(
                            "browseId" to it.params,
                            "category" to (item.title?.getString(language) ?: "unknown"),
                            "filterType" to it.type.name,
                            "isSearchTab" to "true"
                        )
                    )
                }
            }
            listOf(
                Tab(
                    id = "All", 
                    title = "All",
                    isSort = false,
                    extras = mapOf(
                        "browseId" to "All",
                        "category" to "All",
                        "isSearchTab" to "true",
                        "isDefaultTab" to "true"
                    )
                )
            ) + searchTabs
        } else {
            val result = songFeedEndPoint.getSongFeed().getOrThrow()
            result.filter_chips?.map {
                Tab(
                    id = it.params,
                    title = it.text.getString(language),
                    isSort = false,
                    extras = mapOf(
                        "browseId" to it.params,
                        "category" to it.text.getString(language),
                        "isFilterChip" to "true",
                        "isHomeFeedTab" to "true"
                    )
                )
            } ?: emptyList()
        }

        return Feed(tabs) { tab ->
            if (query.isNotBlank() && (tab == null || tab.id == "All")) {
                // For search with query and "All" tab - return EchoMediaItem feed
                val pagedData = PagedData.Single {
                    try {
                        val old = oldSearch?.takeIf { it.first == query }?.second
                        if (old != null) return@Single old
                        
                        val search = try {
                            api.Search.search(query, tab?.id).getOrThrow()
                        } catch (e: Exception) {
                            // If search fails, return an empty list
                            return@Single emptyList()
                        }
                        
                        try {
                            search.categories.map { (itemLayout, _) ->
                                try {
                                    itemLayout.items.mapNotNull { item ->
                                        try {
                                            val shelf = item.toEchoMediaItem(false, thumbnailQuality)?.toShelf()
                                            if (shelf != null) {
                                                // Fix shelf items for search results
                                                try {
                                                    SearchResultsFixer.fixSearchResultShelf(shelf)
                                                } catch (e: Exception) {
                                                    // If fixing fails, return the original shelf to prevent crashes
                                                    shelf
                                                }
                                            } else null
                                        } catch (e: Exception) {
                                            // Skip items that cause exceptions
                                            null
                                        }
                                    }
                                } catch (e: Exception) {
                                    // If processing a category fails, return an empty list for this category
                                    emptyList()
                                }
                            }.flatten()
                        } catch (e: Exception) {
                            // If the entire process fails, return an empty list
                            emptyList()
                        }
                    } catch (e: Exception) {
                        // Global error handler
                        emptyList()
                    }
                }
                
                // Convert EchoMediaItem to Shelf for the feed - create a proper shelf feed
                Feed.Data(PagedData.Single { 
                    pagedData.loadAll().map { item -> Shelf.Item(item) } 
                })
            } else {
                // For tab-based search - return Shelf feed directly
                val pagedData = PagedData.Continuous {
                    try {
                        val params = tab?.id ?: return@Continuous Page(emptyList(), null)
                        val continuation = it
                        val result = try {
                            songFeedEndPoint.getSongFeed(
                                params = params, continuation = continuation
                            ).getOrThrow()
                        } catch (e: Exception) {
                            // If the feed fetch fails, return an empty page
                            return@Continuous Page(emptyList(), null)
                        }
                        
                        val data = try {
                            result.layouts.mapNotNull { itemLayout ->
                                try {
                                    // Fix shelf items for search results in tabs
                                    val shelf = try {
                                        itemLayout.toShelf(api, SINGLES, thumbnailQuality)
                                    } catch (e: Exception) {
                                        // If conversion fails, skip this item
                                        return@mapNotNull null
                                    }
                                    
                                    try {
                                        SearchResultsFixer.fixSearchResultShelf(shelf)
                                    } catch (e: Exception) {
                                        // If fixing fails, return the original shelf
                                        shelf
                                    }
                                } catch (e: Exception) {
                                    // If any error occurs for a specific layout, skip it
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            // If the entire mapping fails, return an empty list
                            emptyList()
                        }
                        
                        Page(data, result.ctoken)
                    } catch (e: Exception) {
                        // Global error handler for the entire data loading process
                        Page(emptyList(), null)
                    }
                }
                
                Feed.Data(pagedData)
            }
        }
    }

    override suspend fun loadTracks(radio: Radio): Feed<Track> =
        PagedData.Single { json.decodeFromString<List<Track>>(radio.extras["tracks"]!!) }.toFeed()

    suspend fun radio(album: Album): Radio {
        val track = api.LoadPlaylist.loadPlaylist(album.id).getOrThrow().items
            ?.lastOrNull()?.toTrack(HIGH)
            ?: throw Exception("No tracks found")
        return radio(track, null)
    }

    suspend fun radio(artist: Artist): Radio {
        val id = "radio_${artist.id}"
        val result = api.ArtistRadio.getArtistRadio(artist.id, null).getOrThrow()
        val tracks = result.items.map { song -> song.toTrack(thumbnailQuality) }
        return Radio(
            id = id,
            title = "${artist.name} Radio",
            extras = mutableMapOf<String, String>().apply {
                put("tracks", json.encodeToString(tracks))
            }
        )
    }


    suspend fun radio(track: Track, context: EchoMediaItem? = null): Radio {
        val id = "radio_${track.id}"
        val cont = context?.extras?.get("cont")
        val result = api.SongRadio.getSongRadio(track.id, cont).getOrThrow()
        val tracks = result.items.map { song -> song.toTrack(thumbnailQuality) }
        return Radio(
            id = id,
            title = "${track.title} Radio",
            extras = mutableMapOf<String, String>().apply {
                put("tracks", json.encodeToString(tracks))
                result.continuation?.let { put("cont", it) }
            }
        )
    }

    suspend fun radio(user: User): Radio {
        // Convert the User to Artist using ModelTypeHelper which guarantees an Artist
        val artist = ModelTypeHelper.userToArtist(user)
        return radio(artist)
    }

    suspend fun radio(playlist: Playlist): Radio {
        val track = loadTracks(playlist)?.loadAll()?.lastOrNull()
            ?: throw Exception("No tracks found")
        return radio(track, null)
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? {
        val tracks = loadTracks(album)?.loadAll() ?: emptyList()
        val lastTrack = tracks.lastOrNull() ?: return null
        val loadedTrack = loadTrack(lastTrack, false)
        val shelves = loadRelated(loadedTrack)
        return Feed(emptyList()) { _ -> PagedData.Single { shelves }.toFeedData() }
    }


    private val trackMap = mutableMapOf<String, PagedData<Track>>()
    override suspend fun loadAlbum(album: Album): Album {
        val (ytmPlaylist, _, data) = playlistEndPoint.loadFromPlaylist(
            album.id, null, thumbnailQuality
        )
        trackMap[ytmPlaylist.id] = data
        return ytmPlaylist.toAlbum(false, HIGH)
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? = trackMap[album.id]?.toFeed()

    private suspend fun getArtistMediaItems(artist: Artist): List<Shelf> {
        val result =
            loadedArtist.takeIf { artist.id == it?.id } ?: api.LoadArtist.loadArtist(artist.id)
                .getOrThrow()

        return result.layouts?.map {
            val title = it.title?.getString(ENGLISH)
            val single = title == SINGLES
            Shelf.Lists.Items(
                id = it.title?.getString(language)?.hashCode()?.toString() ?: "Unknown",
                title = it.title?.getString(language) ?: "Unknown",
                subtitle = it.subtitle?.getString(language),
                list = it.items?.mapNotNull { item ->
                    item.toEchoMediaItem(single, thumbnailQuality)
                } ?: emptyList(),
                more = it.view_more?.getBrowseParamsData()?.let { param ->
                    PagedData.Single {
                        val data = artistMoreEndpoint.load(param)
                        data.map { row ->
                            row.items.mapNotNull { item ->
                                item.toEchoMediaItem(single, thumbnailQuality)
                            }
                        }.flatten()
                    }.let { mediaItems ->
                        Feed(listOf()) { _ -> 
                            Feed.Data(createShelfPagedDataFromMediaItems(mediaItems))
                        }
                    }
                })
        } ?: emptyList()
    }

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        val shelves = getArtistMediaItems(artist)
        return Feed(emptyList()) { _ -> PagedData.Single { shelves }.toFeedData() }
    }

  

    // This is now handled by followItem method
    // The code below was part of the followArtist implementation which is now handled by followItem
    /* 
    suspend fun followArtist(artist: Artist, follow: Boolean) {
        val subId = artist.extras["subId"] ?: throw Exception("No subId found")
        withUserAuth { it.SetSubscribedToArtist.setSubscribedToArtist(artist.id, follow, subId) }
    }
    */

    private var loadedArtist: YtmArtist? = null
    override suspend fun loadArtist(artist: Artist): Artist {
        val result = artistEndPoint.loadArtist(artist.id)
        loadedArtist = result
        return result.toArtist(HIGH)
    }

    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? {
        val cont = playlist.extras["relatedId"] ?: throw Exception("No related id found.")
        val shelves = if (cont.startsWith("id://")) {
            val id = cont.substring(5)
            val track = Track(id, "")
            val loadedTrack = loadTrack(track, false)
            val feed = loadFeed(loadedTrack)
            coroutineScope { 
                if (feed != null) {
                    val items = feed.loadAll()
                    items.filterIsInstance<Shelf.Category>()
                } else emptyList()
            }
        } else {
            val continuation = songRelatedEndpoint.loadFromPlaylist(cont).getOrThrow()
            continuation.map { it.toShelf(api, language, thumbnailQuality) }
        }
        return Feed(emptyList()) { _ -> Feed.Data(PagedData.Single { shelves }) }
    }


    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val (ytmPlaylist, related, data) = playlistEndPoint.loadFromPlaylist(
            playlist.id,
            null,
            thumbnailQuality
        )
        trackMap[ytmPlaylist.id] = data
        return ytmPlaylist.toPlaylist(HIGH, related)
    }

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> = trackMap[playlist.id]?.toFeed() ?: listOf<Track>().toFeed()


    override val webViewRequest = object : WebViewRequest.Cookie<List<User>> {
        override val initialUrl =
            "https://accounts.google.com/v3/signin/identifier?dsh=S1527412391%3A1678373417598386&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den-GB%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F%253Fcbrd%253D1%26feature%3D__FEATURE__&hl=en-GB&ifkv=AWnogHfK4OXI8X1zVlVjzzjybvICXS4ojnbvzpE4Gn_Pfddw7fs3ERdfk-q3tRimJuoXjfofz6wuzg&ltmpl=music&passive=true&service=youtube&uilel=3&flowName=GlifWebSignIn&flowEntry=ServiceLogin".toGetRequest()
        override val stopUrlRegex = "https://music\\.youtube\\.com/.*".toRegex()
        override suspend fun onStop(url: NetworkRequest, cookie: String): List<User> {
            if (!cookie.contains("SAPISID")) throw Exception("Login Failed, could not load SAPISID")
            val auth = run {
                val currentTime = System.currentTimeMillis() / 1000
                val id = cookie.split("SAPISID=")[1].split(";")[0]
                val str = "$currentTime $id https://music.youtube.com"
                val idHash = MessageDigest.getInstance("SHA-1").digest(str.toByteArray())
                    .joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
                "SAPISIDHASH ${currentTime}_${idHash}"
            }
            val headersMap = mutableMapOf("cookie" to cookie, "authorization" to auth)
            val headers = headers { headersMap.forEach { (t, u) -> append(t, u) } }
            return api.client.request("https://music.youtube.com/getAccountSwitcherEndpoint") {
                headers {
                    append("referer", "https://music.youtube.com/")
                    appendAll(headers)
                }
            }.getUsers(cookie, auth)
        }
    }

    // Implement LoginClient methods
    // Implements LoginClient
    override fun setLoginUser(user: User?) {
        if (user == null) {
            api.user_auth_state = null
        } else {
            val cookie = user.extras["cookie"] ?: throw Exception("No cookie")
            val auth = user.extras["auth"] ?: throw Exception("No auth")

            val headers = headers {
                append("cookie", cookie)
                append("authorization", auth)
            }
            val authenticationState =
                YoutubeiAuthenticationState(api, headers, user.id.ifEmpty { null })
            api.user_auth_state = authenticationState
        }
        api.visitor_id = runCatching { kotlinx.coroutines.runBlocking { visitorEndpoint.getVisitorId() } }.getOrNull()
    }

    override suspend fun getCurrentUser(): User? {
        val headers = api.user_auth_state?.headers ?: return null
        val userResponse = api.client.request("https://music.youtube.com/getAccountSwitcherEndpoint") {
            headers {
                append("referer", "https://music.youtube.com/")
                appendAll(headers)
            }
        }.getUsers("", "").firstOrNull() ?: return null
        
        // Enhance the user with additional metadata
        return userResponse.copy(
            subtitle = userResponse.extras["email"] ?: "YouTube Music User",
            extras = userResponse.extras.toMutableMap().apply {
                put("isLoggedIn", "true")
                put("userService", "youtube_music")
                put("accountType", "google")
                put("lastUpdated", System.currentTimeMillis().toString())
                // Preserve existing user data
                putAll(userResponse.extras)
            }
        )
    }


    // Implement TrackerMarkClient methods
    override suspend fun getMarkAsPlayedDuration(details: TrackDetails): Long? = 30000L

    override suspend fun onMarkAsPlayed(details: TrackDetails) {
        api.user_auth_state?.MarkSongAsWatched?.markSongAsWatched(details.track.id)?.getOrThrow()
    }

        private suspend fun <T> withUserAuth(
        block: suspend (auth: YoutubeiAuthenticationState) -> T
    ): T {
        val state = api.user_auth_state
            ?: throw ClientException.LoginRequired()
        return runCatching { block(state) }.getOrElse {
            if (it is ClientRequestException) {
                if (it.response.status.value == 401) {
                    val user = state.own_channel_id
                        ?: throw ClientException.LoginRequired()
                    throw ClientException.Unauthorized(user)
                }
            }
            throw it
        }
    }

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        val tabs = listOf(
            Tab(
                id = "FEmusic_library_landing", 
                title = "All",
                isSort = false,
                extras = mapOf(
                    "browseId" to "FEmusic_library_landing",
                    "category" to "All",
                    "isLibraryTab" to "true",
                    "isDefaultLibraryTab" to "true"
                )
            ),
            Tab(
                id = "FEmusic_history", 
                title = "History",
                isSort = false,
                extras = mapOf(
                    "browseId" to "FEmusic_history",
                    "category" to "History",
                    "isLibraryTab" to "true",
                    "contentType" to "history"
                )
            ),
            Tab(
                id = "FEmusic_liked_playlists", 
                title = "Playlists",
                isSort = false,
                extras = mapOf(
                    "browseId" to "FEmusic_liked_playlists",
                    "category" to "Playlists",
                    "isLibraryTab" to "true",
                    "contentType" to "playlists"
                )
            ),
//            Tab("FEmusic_listening_review", "Review"),
            Tab(
                id = "FEmusic_liked_videos", 
                title = "Songs",
                isSort = false,
                extras = mapOf(
                    "browseId" to "FEmusic_liked_videos",
                    "category" to "Songs",
                    "isLibraryTab" to "true",
                    "contentType" to "liked_songs"
                )
            ),
            Tab(
                id = "FEmusic_library_corpus_track_artists", 
                title = "Artists",
                isSort = false,
                extras = mapOf(
                    "browseId" to "FEmusic_library_corpus_track_artists",
                    "category" to "Artists",
                    "isLibraryTab" to "true",
                    "contentType" to "artists"
                )
            )
        )
        
        return Feed(tabs) { tab ->
            val pagedData = PagedData.Continuous<Shelf> { cont ->
                val browseId = tab?.id ?: "FEmusic_library_landing"
                val (result, ctoken) = withUserAuth { libraryEndPoint.loadLibraryFeed(browseId, cont) }
                val data = result.mapNotNull { playlist ->
                    playlist.toEchoMediaItem(false, thumbnailQuality)?.toShelf()
                }
                Page(data, ctoken)
            }
            Feed.Data(pagedData)
        }
    }

    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        val playlistId = withUserAuth {
            it.CreateAccountPlaylist
                .createAccountPlaylist(title, description ?: "")
                .getOrThrow()
        }
        return loadPlaylist(Playlist(playlistId, "", true))
    }

    override suspend fun deletePlaylist(playlist: Playlist) = withUserAuth {
        it.DeleteAccountPlaylist.deleteAccountPlaylist(playlist.id).getOrThrow()
    }

    // Implement LikeClient methods
    // Implements LikeClient
    override suspend fun likeItem(item: EchoMediaItem, shouldLike: Boolean) {
        val track = item as? Track ?: throw Exception("Only tracks can be liked")
        likeTrack(track, shouldLike)
    }

    private suspend fun likeTrack(track: Track, isLiked: Boolean) {
        val likeStatus = if (isLiked) SongLikedStatus.LIKED else SongLikedStatus.NEUTRAL
        withUserAuth { it.SetSongLiked.setSongLiked(track.id, likeStatus).getOrThrow() }
    }

    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> =
        withUserAuth { auth ->
            auth.AccountPlaylists.getAccountPlaylists().getOrThrow().mapNotNull {
                if (it.id != "VLSE") it.toPlaylist(thumbnailQuality) to false
                else null
            }

        }

    override suspend fun editPlaylistMetadata(
        playlist: Playlist, title: String, description: String?
    ) {
        withUserAuth { auth ->
            val editor = auth.AccountPlaylistEditor.getEditor(playlist.id, listOf(), listOf())
            editor.performAndCommitActions(
                listOfNotNull(
                    PlaylistEditor.Action.SetTitle(title),
                    description?.let { PlaylistEditor.Action.SetDescription(it) }
                )
            )
        }
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist, tracks: List<Track>, indexes: List<Int>
    ) {
        val actions = indexes.map {
            val track = tracks[it]
            EchoEditPlaylistEndpoint.Action.Remove(track.id, track.extras["setId"]!!)
        }
        editorEndpoint.editPlaylist(playlist.id, actions)
    }

    override suspend fun addTracksToPlaylist(
        playlist: Playlist, tracks: List<Track>, index: Int, new: List<Track>
    ) {
        val actions = new.map { EchoEditPlaylistEndpoint.Action.Add(it.id) }
        val setIds = editorEndpoint.editPlaylist(playlist.id, actions).playlistEditResults!!.map {
            it.playlistEditVideoAddedResultData.setVideoId
        }
        val addBeforeTrack = tracks.getOrNull(index)?.extras?.get("setId") ?: return
        val moveActions = setIds.map { setId ->
            EchoEditPlaylistEndpoint.Action.Move(setId, addBeforeTrack)
        }
        editorEndpoint.editPlaylist(playlist.id, moveActions)
    }

    override suspend fun moveTrackInPlaylist(
        playlist: Playlist, tracks: List<Track>, fromIndex: Int, toIndex: Int
    ) {
        val setId = tracks[fromIndex].extras["setId"]!!
        val before = if (fromIndex - toIndex > 0) 0 else 1
        val addBeforeTrack = tracks.getOrNull(toIndex + before)?.extras?.get("setId")
            ?: return
        editorEndpoint.editPlaylist(
            playlist.id, listOf(
                EchoEditPlaylistEndpoint.Action.Move(setId, addBeforeTrack)
            )
        )
    }

    override suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics> {
        val pagedData = PagedData.Single {
            val lyricsId = track.extras["lyricsId"] ?: return@Single listOf()
            val data = lyricsEndPoint.getLyrics(lyricsId) ?: return@Single listOf()
            val lyrics = data.first.map {
                it.cueRange.run {
                    Lyrics.Item(
                        it.lyricLine,
                        startTimeMilliseconds.toLong(),
                        endTimeMilliseconds.toLong()
                    )
                }
            }
            listOf(Lyrics(lyricsId, track.title, data.second, Lyrics.Timed(lyrics)))
        }
        return pagedData.toFeed()
    }

    override suspend fun loadLyrics(lyrics: Lyrics) = lyrics

    override suspend fun onShare(item: EchoMediaItem) = when (item) {
        is Album -> "https://music.youtube.com/browse/${item.id}"
        is Playlist -> "https://music.youtube.com/playlist?list=${item.id}"
        is Radio -> "https://music.youtube.com/playlist?list=${item.id}"
        is Artist -> "https://music.youtube.com/channel/${item.id}"
        is Track -> "https://music.youtube.com/watch?v=${item.id}"
        else -> throw ClientException.NotSupported("Unsupported media item type for sharing")
    }
    
    // Required method implementations to satisfy the interface
    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        // Fix any User objects that might be passed as EchoMediaItem
        val fixedItem = when(item) {
            is User -> ModelTypeHelper.userToArtist(item)
            else -> item
        }
        
        return when(fixedItem) {
            is Track -> radio(fixedItem)
            is Artist -> radio(fixedItem)
            is Playlist -> radio(fixedItem)
            else -> throw ClientException.NotSupported("Radio not supported for this media item type")
        }
    }
    
    override suspend fun loadRadio(radio: Radio): Radio = radio
    
    // Helper method to convert URL to NetworkRequest
    private fun String.toGetRequest(): NetworkRequest {
        return NetworkRequest(url = this)
    }
    
    override suspend fun onTrackChanged(details: TrackDetails?) {}
    
    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {}
    
    // LikeClient implementation
    override suspend fun isItemLiked(item: EchoMediaItem): Boolean {
        return item.extras["isLiked"]?.toBoolean() ?: false
    }
    
    // FollowClient implementation
    override suspend fun isFollowing(item: EchoMediaItem): Boolean {
        return item.extras["isFollowed"]?.toBoolean() ?: false
    }
    
    override suspend fun getFollowersCount(item: EchoMediaItem): Long? {
        return item.extras["followerCount"]?.toLong()
    }
    
    override suspend fun followItem(item: EchoMediaItem, shouldFollow: Boolean) {
        when(item) {
            is Artist -> {
                val subId = item.extras["subId"] ?: throw Exception("No subId found")
                withUserAuth { it.SetSubscribedToArtist.setSubscribedToArtist(item.id, shouldFollow, subId) }
            }
            else -> throw ClientException.NotSupported("Follow not supported for this media item type")
        }
    }
    
    // LyricsSearchClient implementation
    override suspend fun searchLyrics(query: String): Feed<Lyrics> {
        return listOf<Lyrics>().toFeed()
    }
}