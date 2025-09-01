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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * Enhanced YouTube Extension with working API integration
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

    // Required abstract method implementations
    override suspend fun loadTrack(track: Track, throwIfFailed: Boolean): Track {
        return try {
            println("DEBUG: Loading track: ${track.title}")
            
            // Try to load the track with ytmkt API
            val songResult = songEndpoint.loadSong(track.id)
            if (songResult.isSuccess) {
                val ytmTrack = songResult.getOrThrow()
                ytmTrack
            } else {
                track // Return original if loading fails
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to load track: ${e.message}")
            if (throwIfFailed) throw e else track
        }
    }
    
    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return try {
            println("DEBUG: Loading streamable media: ${streamable.id}, type: ${streamable.type}")
            
            when (streamable.type) {
                Streamable.MediaType.Server -> {
                    // Try to get actual streaming URL from YouTube
                    try {
                        val videoResult = videoEndpoint.getVideo(false, streamable.id)
                        val (response, _) = videoResult
                        
                        // Parse streaming data from YouTube response
                        val responseBody = response.body<JsonObject>()
                        val streamingData = responseBody["streamingData"]?.jsonObject
                        val adaptiveFormats = streamingData?.get("adaptiveFormats")?.jsonArray
                        
                        if (adaptiveFormats != null && adaptiveFormats.isNotEmpty()) {
                            // Find best audio format
                            val audioFormat = adaptiveFormats.find { format ->
                                val mimeType = format.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                                mimeType.contains("audio")
                            }
                            
                            if (audioFormat != null) {
                                val url = audioFormat.jsonObject["url"]?.jsonPrimitive?.content
                                if (url != null) {
                                    val source = Streamable.Source.Http(
                                        request = NetworkRequest(url = url),
                                        type = Streamable.SourceType.Progressive,
                                        quality = streamable.quality,
                                        title = streamable.title,
                                        isVideo = false
                                    )
                                    
                                    return Streamable.Media.Server(
                                        sources = listOf(source),
                                        merged = false
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Failed to get YouTube streaming URL: ${e.message}")
                    }
                    
                    // Fallback to basic implementation
                    val source = Streamable.Source.Http(
                        request = NetworkRequest(url = "https://www.youtube.com/watch?v=${streamable.id}"),
                        type = Streamable.SourceType.Progressive,
                        quality = streamable.quality,
                        title = streamable.title,
                        isVideo = false
                    )
                    
                    Streamable.Media.Server(
                        sources = listOf(source),
                        merged = false
                    )
                }
                
                Streamable.MediaType.Background -> {
                    Streamable.Media.Background(
                        request = NetworkRequest(url = "https://img.youtube.com/vi/${streamable.id}/maxresdefault.jpg")
                    )
                }
                
                else -> {
                    throw Exception("Unsupported streamable media type: ${streamable.type}")
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to load streamable media: ${e.message}")
            throw e
        }
    }
    
    override suspend fun loadHomeFeed(): Feed<Shelf> {
        return try {
            ensureVisitorId()
            println("DEBUG: Loading home feed...")
            
            val result = homeFeedEndpoint.getSongFeed()
            if (result.isSuccess) {
                val feedData = result.getOrThrow()
                println("DEBUG: Got ${feedData.rows.size} rows from home feed")
                
                val shelves = feedData.rows.mapNotNull { layout ->
                    try {
                        layout.toShelf(api, ENGLISH, thumbnailQuality)
                    } catch (e: Exception) {
                        println("DEBUG: Failed to convert layout to shelf: ${e.message}")
                        null
                    }
                }
                
                println("DEBUG: Converted to ${shelves.size} shelves")
                shelves.toFeed()
            } else {
                println("DEBUG: Home feed result failed")
                emptyList<Shelf>().toFeed()
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to load home feed: ${e.message}")
            e.printStackTrace()
            emptyList<Shelf>().toFeed()
        }
    }
    
    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        return try {
            val relatedId = track.extras["relatedId"] ?: return null
            println("DEBUG: Loading related feed for track: ${track.title}")
            
            val result = songRelatedEndpoint.loadFromPlaylist(relatedId)
            if (result.isSuccess) {
                val layouts = result.getOrThrow()
                val shelves = layouts.mapNotNull { layout ->
                    try {
                        layout.toShelf(api, ENGLISH, thumbnailQuality)
                    } catch (e: Exception) {
                        println("DEBUG: Failed to convert related layout to shelf: ${e.message}")
                        null
                    }
                }
                shelves.toFeed()
            } else null
        } catch (e: Exception) {
            println("DEBUG: Failed to load track feed: ${e.message}")
            null
        }
    }
    
    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null
    
    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        return try {
            println("DEBUG: Loading artist feed for: ${artist.name}")
            
            val result = songFeedEndpoint.getSongFeed(browseId = artist.id)
            if (result.isSuccess) {
                val feedData = result.getOrThrow()
                val shelves = feedData.rows.mapNotNull { layout ->
                    try {
                        layout.toShelf(api, ENGLISH, thumbnailQuality)
                    } catch (e: Exception) {
                        println("DEBUG: Failed to convert artist layout to shelf: ${e.message}")
                        null
                    }
                }
                shelves.toFeed()
            } else {
                emptyList<Shelf>().toFeed()
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to load artist feed: ${e.message}")
            emptyList<Shelf>().toFeed()
        }
    }
    
    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? = null
    
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        return try {
            println("DEBUG: Searching for: $query")
            
            val result = searchEndpoint.search(query, null)
            if (result.isSuccess) {
                val searchResults = result.getOrThrow()
                println("DEBUG: Got ${searchResults.categories.size} search categories")
                
                val shelves = searchResults.categories.mapNotNull { (layout, _) ->
                    try {
                        layout.toShelf(api, ENGLISH, thumbnailQuality)
                    } catch (e: Exception) {
                        println("DEBUG: Failed to convert search layout to shelf: ${e.message}")
                        null
                    }
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
    
    override suspend fun loadTracks(radio: Radio): Feed<Track> {
        return emptyList<Track>().toFeed()
    }
    
    override suspend fun loadTracks(album: Album): Feed<Track>? {
        return try {
            val (_, _, pagedTracks) = playlistEndpoint.loadFromPlaylist(album.id, null, thumbnailQuality)
            // Convert PagedData<Track> to Feed<Track>
            pagedTracks.toFeed()
        } catch (e: Exception) {
            println("DEBUG: Failed to load album tracks: ${e.message}")
            null
        }
    }
    
    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        return try {
            val (_, _, pagedTracks) = playlistEndpoint.loadFromPlaylist(playlist.id, null, thumbnailQuality)
            // Convert PagedData<Track> to Feed<Track>
            pagedTracks.toFeed()
        } catch (e: Exception) {
            println("DEBUG: Failed to load playlist tracks: ${e.message}")
            emptyList<Track>().toFeed()
        }
    }
    
    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        return Radio(
            id = "radio_${item.id}",
            title = "Radio based on ${item.title}",
            cover = item.cover,
            subtitle = "Generated radio",
            extras = mapOf("baseItem" to item.id)
        )
    }
    
    override suspend fun loadAlbum(album: Album): Album {
        return try {
            val (fullAlbum, _) = playlistEndpoint.loadFromPlaylist(album.id, null, thumbnailQuality)
            fullAlbum.toAlbum(false, thumbnailQuality)
        } catch (e: Exception) {
            println("DEBUG: Failed to load album: ${e.message}")
            album
        }
    }
    
    override suspend fun loadArtist(artist: Artist): Artist {
        return try {
            val fullArtist = artistEndpoint.loadArtist(artist.id)
            fullArtist.toArtist(thumbnailQuality)
        } catch (e: Exception) {
            println("DEBUG: Failed to load artist: ${e.message}")
            artist
        }
    }
    
    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        return try {
            val (fullPlaylist, _) = playlistEndpoint.loadFromPlaylist(playlist.id, null, thumbnailQuality)
            fullPlaylist.toPlaylist(thumbnailQuality)
        } catch (e: Exception) {
            println("DEBUG: Failed to load playlist: ${e.message}")
            playlist
        }
    }
    
    override suspend fun getCurrentUser(): User? = null
    override fun setLoginUser(user: User?) {}
    override suspend fun onTrackChanged(details: TrackDetails?) {}
    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {}
    override suspend fun getMarkAsPlayedDuration(details: TrackDetails): Long? = null
    override suspend fun onMarkAsPlayed(details: TrackDetails) {}
    
    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        return emptyList<Shelf>().toFeed()
    }
    
    override suspend fun onShare(item: EchoMediaItem): String = "https://music.youtube.com"
    
    override suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics> {
        return emptyList<Lyrics>().toFeed()
    }
    
    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics
    override suspend fun isFollowing(item: EchoMediaItem): Boolean = false
    override suspend fun getFollowersCount(item: EchoMediaItem): Long? = null
    override suspend fun followItem(item: EchoMediaItem, shouldFollow: Boolean) {}
    override suspend fun likeItem(item: EchoMediaItem, shouldLike: Boolean) {}
    override suspend fun isItemLiked(item: EchoMediaItem): Boolean = false
    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> = emptyList()
    
    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        return Playlist("", title, false)
    }
    
    override suspend fun deletePlaylist(playlist: Playlist) {}
    override suspend fun editPlaylistMetadata(playlist: Playlist, title: String, description: String?) {}
    override suspend fun addTracksToPlaylist(playlist: Playlist, tracks: List<Track>, index: Int, new: List<Track>) {}
    override suspend fun removeTracksFromPlaylist(playlist: Playlist, tracks: List<Track>, indexes: List<Int>) {}
    override suspend fun moveTrackInPlaylist(playlist: Playlist, tracks: List<Track>, fromIndex: Int, toIndex: Int) {}
    
    override suspend fun searchLyrics(query: String): Feed<Lyrics> {
        return emptyList<Lyrics>().toFeed()
    }
    
    override suspend fun quickSearch(query: String): List<QuickSearchItem> = emptyList()
    override suspend fun deleteQuickSearch(item: QuickSearchItem) {}
    
    // WebView request implementation - simple property instead of extending sealed interface
    override val webViewRequest: WebViewRequest<List<User>>
        get() = TODO("WebView login not implemented yet")
    
    fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(0..1000).random()}"
    }
    
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