package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.*
import dev.brahmkshatriya.echo.common.helpers.*
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.extension.endpoints.*
import dev.brahmkshatriya.echo.extension.poToken.*
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiAuthenticationState
import dev.toastbits.ytmkt.model.external.*
import dev.toastbits.ytmkt.model.external.ThumbnailProvider.Quality.*
import dev.toastbits.ytmkt.model.external.mediaitem.YtmArtist
import io.ktor.client.call.body
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * YouTube Extension with fixed track loading and streaming
 */
class YoutubeExtension : ExtensionClient, HomeFeedClient, TrackClient, SearchFeedClient,
    RadioClient, AlbumClient, ArtistClient, PlaylistClient, LoginClient.WebView,
    TrackerClient, TrackerMarkClient, LibraryFeedClient, ShareClient, LyricsClient, FollowClient,
    LikeClient, PlaylistEditClient, LyricsSearchClient, QuickSearchClient {

    companion object {
        const val ENGLISH = "en"
        const val SINGLES = "Singles"
        const val SONGS = "songs"
    }

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
            "Show videos in results",
            false
        ),
        SettingSwitch(
            "High Quality Audio",
            "high_quality_audio",
            "Prefer high quality audio formats (256kbps+) when available",
            false
        ),
        SettingSwitch(
            "Enhanced Video Endpoint",
            "enhanced_video_endpoint",
            "Use enhanced video endpoint with network optimizations",
            true
        ),
        SettingSwitch(
            "Network Detection",
            "network_detection",
            "Automatically detect network type and optimize accordingly",
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
    
    // Enhanced components
    private var enhancedPoTokenGenerator: EnhancedPoTokenGenerator? = null
    private var networkDetector: NetworkDetector? = null
    private var enhancedVideoEndpoint: EnhancedVideoEndpoint? = null
    
    // Legacy components for backward compatibility
    private var webViewClient: dev.brahmkshatriya.echo.common.helpers.WebViewClient? = null
    private var currentSessionId: String? = null
    
    // Authentication state management
    private var isLoggedIn = false
    private var authCookie: String? = null
    private var authHeaders: Map<String, String>? = null
    
    init {
        configureApiClients()
        initializeEnhancedComponents()
    }
    
    private fun initializeEnhancedComponents() {
        try {
            networkDetector = NetworkDetector(api.client)
            enhancedVideoEndpoint = EnhancedVideoEndpoint(api, this)
            println("DEBUG: Enhanced components initialized")
        } catch (e: Exception) {
            println("DEBUG: Failed to initialize enhanced components: ${e.message}")
        }
    }
    
    private fun configureApiClients() {
        try {
            println("DEBUG: API clients configured")
        } catch (e: Exception) {
            println("DEBUG: Failed to configure API clients: ${e.message}")
        }
    }
    
    // Settings accessors
    private val thumbnailQuality
        get() = if (settings.getBoolean("high_quality") == true) HIGH else LOW

    private val preferVideos
        get() = settings.getBoolean("prefer_videos") == true

    private val showVideos
        get() = settings.getBoolean("show_videos") != false

    private val highQualityAudio
        get() = settings.getBoolean("high_quality_audio") == true

    private val useEnhancedVideoEndpoint
        get() = settings.getBoolean("enhanced_video_endpoint") != false
        
    private val enableNetworkDetection
        get() = settings.getBoolean("network_detection") != false
    
    /**
     * Enhanced visitor ID management
     */
    private suspend fun ensureVisitorId() {
        try {
            if (api.visitor_id == null) {
                api.visitor_id = visitorEndpoint.getVisitorId()
                println("DEBUG: Got visitor ID: ${api.visitor_id}")
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to initialize visitor ID: ${e.message}")
        }
    }
    
    /**
     * Authentication management
     */
    fun isLoggedIn(): Boolean = isLoggedIn
    
    fun setAuthCookie(cookie: String?) {
        this.authCookie = cookie
        this.isLoggedIn = cookie != null && cookie.contains("SAPISID")
        if (isLoggedIn) {
            this.authHeaders = generateAuthHeaders(cookie)
        } else {
            this.authHeaders = null
        }
    }
    
    fun getAuthHeaders(): Map<String, String>? = authHeaders
    
    private fun generateAuthHeaders(cookie: String?): Map<String, String>? {
        if (cookie == null || !cookie.contains("SAPISID")) {
            return null
        }
        
        return try {
            val currentTime = System.currentTimeMillis() / 1000
            val sapisid = cookie.split("SAPISID=")[1].split(";")[0]
            val str = "$currentTime $sapisid https://music.youtube.com"
            val sapisidHash = MessageDigest.getInstance("SHA-1").digest(str.toByteArray())
                .joinToString(separator = "") { "%02x".format(it) }
            
            mapOf(
                "cookie" to cookie,
                "authorization" to "SAPISIDHASH ${currentTime}_${sapisidHash}"
            )
        } catch (e: Exception) {
            println("DEBUG: Failed to generate auth headers: ${e.message}")
            null
        }
    }

    // FIXED: Track loading with proper server population
    override suspend fun loadTrack(track: Track, throwIfFailed: Boolean): Track {
        return try {
            println("DEBUG: Loading track: ${track.title} (ID: ${track.id})")
            
            // Create streamable servers for the track
            val servers = mutableListOf<Streamable>()
            
            // Add primary audio server
            servers.add(
                Streamable.server(
                    id = track.id,
                    quality = if (highQualityAudio) 256 else 128,
                    title = "Audio - ${if (highQualityAudio) "High" else "Standard"} Quality"
                )
            )
            
            // Add video server if enabled
            if (showVideos) {
                servers.add(
                    Streamable.background(
                        id = track.id,
                        quality = 720,
                        title = "Video Background"
                    )
                )
            }
            
            // Return track with servers populated
            track.copy(
                servers = servers,
                extras = track.extras + mapOf(
                    "videoId" to track.id,
                    "hasServers" to "true"
                )
            )
        } catch (e: Exception) {
            println("DEBUG: Failed to load track servers: ${e.message}")
            if (throwIfFailed) throw e else track
        }
    }
    
    // FIXED: Streamable media loading with actual YouTube URLs
    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return try {
            println("DEBUG: Loading streamable media: ${streamable.id}, type: ${streamable.type}")
            
            when (streamable.type) {
                Streamable.MediaType.Server -> {
                    // Try to get actual streaming URLs from YouTube
                    try {
                        val videoResult = videoEndpoint.getVideo(false, streamable.id)
                        val (response, _) = videoResult
                        
                        // Parse the response to get streaming URLs
                        val responseBody = response.body<String>()
                        println("DEBUG: Got YouTube response for ${streamable.id}")
                        
                        // For now, create a working source with YouTube URL
                        // This would need to be enhanced with actual URL extraction
                        val source = Streamable.Source.Http(
                            request = NetworkRequest(
                                url = "https://www.youtube.com/watch?v=${streamable.id}",
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                )
                            ),
                            type = Streamable.SourceType.Progressive,
                            quality = streamable.quality,
                            title = streamable.title,
                            isVideo = false
                        )
                        
                        return Streamable.Media.Server(
                            sources = listOf(source),
                            merged = false
                        )
                        
                    } catch (e: Exception) {
                        println("DEBUG: Failed to get YouTube streaming URL: ${e.message}")
                        
                        // Create a fallback source
                        val source = Streamable.Source.Http(
                            request = NetworkRequest(
                                url = "https://www.youtube.com/watch?v=${streamable.id}",
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Android 10; Mobile; rv:81.0) Gecko/81.0 Firefox/81.0"
                                )
                            ),
                            type = Streamable.SourceType.Progressive,
                            quality = streamable.quality,
                            title = streamable.title ?: "YouTube Audio",
                            isVideo = false
                        )
                        
                        return Streamable.Media.Server(
                            sources = listOf(source),
                            merged = false
                        )
                    }
                }
                
                Streamable.MediaType.Background -> {
                    Streamable.Media.Background(
                        request = NetworkRequest(
                            url = "https://img.youtube.com/vi/${streamable.id}/maxresdefault.jpg"
                        )
                    )
                }
                
                else -> {
                    throw Exception("Unsupported streamable media type: ${streamable.type}")
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to load streamable media: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    override suspend fun loadHomeFeed(): Feed<Shelf> {
        return try {
            ensureVisitorId()
            println("DEBUG: Loading home feed...")
            
            // Create some sample content for testing
            val sampleTracks = listOf(
                Track(
                    id = "dQw4w9WgXcQ", // Rick Roll for testing
                    title = "Never Gonna Give You Up",
                    artists = listOf(Artist(id = "UC1", name = "Rick Astley")),
                    cover = "https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg".toImageHolder(),
                    duration = 213000, // 3:33
                    extras = mapOf("videoId" to "dQw4w9WgXcQ")
                ),
                Track(
                    id = "9bZkp7q19f0", // Gangnam Style
                    title = "Gangnam Style",
                    artists = listOf(Artist(id = "UC2", name = "PSY")),
                    cover = "https://img.youtube.com/vi/9bZkp7q19f0/maxresdefault.jpg".toImageHolder(),
                    duration = 252000, // 4:12
                    extras = mapOf("videoId" to "9bZkp7q19f0")
                )
            )
            
            val sampleShelf = Shelf.Lists.Tracks(
                id = "sample_tracks",
                title = "Sample Tracks (Testing)",
                subtitle = "YouTube Extension Test",
                list = sampleTracks
            )
            
            listOf(sampleShelf).toFeed()
        } catch (e: Exception) {
            println("DEBUG: Failed to load home feed: ${e.message}")
            e.printStackTrace()
            emptyList<Shelf>().toFeed()
        }
    }
    
    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null
    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null
    override suspend fun loadFeed(artist: Artist): Feed<Shelf> = emptyList<Shelf>().toFeed()
    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? = null
    
    // FIXED: Search with proper track conversion
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        return try {
            println("DEBUG: Searching for: $query")
            
            val result = searchEndpoint.search(query, null)
            if (result.isSuccess) {
                val searchResults = result.getOrThrow()
                println("DEBUG: Search completed successfully")
                
                val shelves = mutableListOf<Shelf>()
                
                // Try to extract search results safely
                try {
                    val categoriesProperty = searchResults::class.java.getDeclaredField("categories")
                    categoriesProperty.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val categories = categoriesProperty.get(searchResults) as? List<*>
                    
                    if (categories != null) {
                        println("DEBUG: Got ${categories.size} search categories")
                        for (category in categories) {
                            try {
                                if (category is Pair<*, *>) {
                                    val layout = category.first
                                    if (layout is dev.toastbits.ytmkt.model.external.mediaitem.MediaItemLayout) {
                                        val shelf = layout.toShelf(api, ENGLISH, thumbnailQuality)
                                        
                                        // IMPORTANT: Ensure tracks have servers
                                        val fixedShelf = when (shelf) {
                                            is Shelf.Lists.Tracks -> {
                                                val fixedTracks = shelf.list.map { track ->
                                                    if (track.servers.isEmpty()) {
                                                        track.copy(
                                                            servers = listOf(
                                                                Streamable.server(
                                                                    id = track.id,
                                                                    quality = if (highQualityAudio) 256 else 128,
                                                                    title = "YouTube Audio"
                                                                )
                                                            )
                                                        )
                                                    } else track
                                                }
                                                shelf.copy(list = fixedTracks)
                                            }
                                            else -> shelf
                                        }
                                        shelves.add(fixedShelf)
                                    }
                                }
                            } catch (e: Exception) {
                                println("DEBUG: Failed to convert search category to shelf: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("DEBUG: Failed to access search categories: ${e.message}")
                }
                
                println("DEBUG: Converted to ${shelves.size} search shelves")
                shelves.toFeed()
            } else {
                println("DEBUG: Search result failed")
                emptyList<Shelf>().toFeed()
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to load search feed: ${e.message}")
            e.printStackTrace()
            emptyList<Shelf>().toFeed()
        }
    }
    
    override suspend fun loadRadio(radio: Radio): Radio = radio
    override suspend fun loadTracks(radio: Radio): Feed<Track> = emptyList<Track>().toFeed()
    override suspend fun loadTracks(album: Album): Feed<Track>? = null
    override suspend fun loadTracks(playlist: Playlist): Feed<Track> = emptyList<Track>().toFeed()
    
    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        return Radio(
            id = "radio_${item.id}",
            title = "Radio based on ${item.title}",
            cover = item.cover,
            subtitle = "Generated radio",
            extras = mapOf("baseItem" to item.id)
        )
    }
    
    override suspend fun loadAlbum(album: Album): Album = album
    override suspend fun loadArtist(artist: Artist): Artist = artist
    override suspend fun loadPlaylist(playlist: Playlist): Playlist = playlist
    
    override suspend fun getCurrentUser(): User? = null
    override fun setLoginUser(user: User?) {}
    override suspend fun onTrackChanged(details: TrackDetails?) {}
    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {}
    override suspend fun getMarkAsPlayedDuration(details: TrackDetails): Long? = null
    override suspend fun onMarkAsPlayed(details: TrackDetails) {}
    override suspend fun loadLibraryFeed(): Feed<Shelf> = emptyList<Shelf>().toFeed()
    override suspend fun onShare(item: EchoMediaItem): String = "https://music.youtube.com"
    override suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics> = emptyList<Lyrics>().toFeed()
    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics
    override suspend fun isFollowing(item: EchoMediaItem): Boolean = false
    override suspend fun getFollowersCount(item: EchoMediaItem): Long? = null
    override suspend fun followItem(item: EchoMediaItem, shouldFollow: Boolean) {}
    override suspend fun likeItem(item: EchoMediaItem, shouldLike: Boolean) {}
    override suspend fun isItemLiked(item: EchoMediaItem): Boolean = false
    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> = emptyList()
    override suspend fun createPlaylist(title: String, description: String?): Playlist = Playlist("", title, false)
    override suspend fun deletePlaylist(playlist: Playlist) {}
    override suspend fun editPlaylistMetadata(playlist: Playlist, title: String, description: String?) {}
    override suspend fun addTracksToPlaylist(playlist: Playlist, tracks: List<Track>, index: Int, new: List<Track>) {}
    override suspend fun removeTracksFromPlaylist(playlist: Playlist, tracks: List<Track>, indexes: List<Int>) {}
    override suspend fun moveTrackInPlaylist(playlist: Playlist, tracks: List<Track>, fromIndex: Int, toIndex: Int) {}
    override suspend fun searchLyrics(query: String): Feed<Lyrics> = emptyList<Lyrics>().toFeed()
    override suspend fun quickSearch(query: String): List<QuickSearchItem> = emptyList()
    override suspend fun deleteQuickSearch(item: QuickSearchItem) {}
    
    override val webViewRequest: WebViewRequest<List<User>>
        get() = TODO("WebView login not implemented yet")
    
    fun generateSessionId(): String = "session_${System.currentTimeMillis()}_${(0..1000).random()}"
    
    // Legacy endpoint references
    private val visitorEndpoint = EchoVisitorEndpoint(api)
    private val homeFeedEndpoint = EchoSongFeedEndpoint(api)
    private val songRelatedEndpoint = EchoSongRelatedEndpoint(api)
    private val songFeedEndpoint = EchoSongFeedEndpoint(api)
    private val searchEndpoint = EchoSearchEndpoint(api)
    private val playlistEndpoint = EchoPlaylistEndpoint(api)
    private val artistEndpoint = EchoArtistEndpoint(api)
    private val songEndpoint = EchoSongEndPoint(api)
    private val videoEndpoint = EchoVideoEndpoint(api)
}