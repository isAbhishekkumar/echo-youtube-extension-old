package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Track.Type
import dev.brahmkshatriya.echo.common.models.Track.Playable
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.YoutubeExtension.Companion.ENGLISH
import dev.brahmkshatriya.echo.extension.YoutubeExtension.Companion.SINGLES
import dev.brahmkshatriya.echo.extension.endpoints.GoogleAccountResponse
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import dev.toastbits.ytmkt.model.external.ThumbnailProvider
import dev.toastbits.ytmkt.model.external.mediaitem.MediaItemLayout
import dev.toastbits.ytmkt.model.external.mediaitem.YtmArtist
import dev.toastbits.ytmkt.model.external.mediaitem.YtmMediaItem
import dev.toastbits.ytmkt.model.external.mediaitem.YtmPlaylist
import dev.toastbits.ytmkt.model.external.mediaitem.YtmSong
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

// Extension function to create a Date object from a string year
fun String.toDate(): Date = Date(this.toInt())

// Extension function to create a Date from a string containing a timestamp
fun String.toDateFromTimestamp(): Date = Date(this.toLong())

// Extension function to check if a string contains a timestamp
fun String.containsTimestamp(): Boolean {
    return Regex("\\d{10,}").containsMatchIn(this)
}

suspend fun MediaItemLayout.toShelf(
    api: YoutubeiApi,
    language: String,
    quality: ThumbnailProvider.Quality
): Shelf {
    val single = title?.getString(ENGLISH) == SINGLES
    return Shelf.Lists.Items(
        id = title?.getString(language)?.hashCode()?.toString() ?: "Unknown",
        title = title?.getString(language) ?: "Unknown",
        subtitle = subtitle?.getString(language),
        list = items.mapNotNull { item ->
            item.toEchoMediaItem(single, quality)
        },
        more = view_more?.getBrowseParamsData()?.browse_id?.let { id ->
            val pagedData = PagedData.Single<EchoMediaItem> {
                val rows =
                    api.GenericFeedViewMorePage.getGenericFeedViewMorePage(id).getOrThrow()
                rows.mapNotNull { itemLayout ->
                    itemLayout.toEchoMediaItem(single, quality)
                }
            }
            Feed(emptyList()) { _ ->
                Feed.Data(PagedData.Single<Shelf> {
                    pagedData.loadAll().map { Shelf.Item(it) }
                })
            }
        }
    )
}

fun YtmMediaItem.toEchoMediaItem(
    single: Boolean,
    quality: ThumbnailProvider.Quality
): EchoMediaItem? {
    return when (this) {
        is YtmSong -> toTrack(quality)
        is YtmPlaylist -> when (type) {
            YtmPlaylist.Type.ALBUM -> toAlbum(single, quality)
            else -> {
                if (id != "VLSE") toPlaylist(quality)
                else null
            }
        }
        is YtmArtist -> toArtist(quality)
        else -> null
    }
}

fun YtmPlaylist.toPlaylist(
    quality: ThumbnailProvider.Quality, related: String? = null
): Playlist {
    val extras = mutableMapOf<String, String>()
    related?.let { extras["relatedId"] = it }
    val bool = owner_id?.split(",")?.map {
        it.toBoolean()
    } ?: listOf(false, false)
    return Playlist(
        id = id,
        title = name ?: "Unknown",
        isEditable = bool.getOrNull(1) ?: false,
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        authors = artists?.map { it.toUser(quality) }?.let { ModelTypeHelper.safeArtistListConversion(it) } ?: emptyList(),
        trackCount = item_count?.toLong(),
        duration = total_duration?.toLong(),
        creationDate = year?.let { yearStr -> 
            parseYearString(yearStr)
        },

        description = description,
        extras = extras,
    )
}

fun YtmPlaylist.toAlbum(
    single: Boolean = false,
    quality: ThumbnailProvider.Quality
): Album {
    val bool = owner_id?.split(",")?.map {
        it.toBoolean()
    } ?: listOf(false, false)
    return Album(
        id = id,
        title = name ?: "Unknown",
        isExplicit = bool.firstOrNull() ?: false,
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        artists = artists?.map { it.toArtist(quality) } ?: emptyList(),
        trackCount = item_count?.toLong() ?: if (single) 1L else null,
        releaseDate = year?.let { yearStr -> 
            parseYearString(yearStr)
        },

        label = null,
        duration = total_duration?.toLong(),
        description = description,
    )
}

fun YtmSong.toTrack(
    quality: ThumbnailProvider.Quality,
    setId: String? = null
): Track {
    val album = album?.toAlbum(false, quality)
    val extras = mutableMapOf<String, String>()
    setId?.let { extras["setId"] = it }
    
    // Determine track type based on video status
    val trackType = when {
        is_video -> Track.Type.VideoSong
        else -> Track.Type.Song
    }
    
    // Determine playable status (default to Yes for now, could be made conditional)
    val playableStatus = Track.Playable.Yes
    
    return Track(
        id = id,
        title = name ?: "Unknown",
        type = trackType,
        artists = artists?.map { it.toArtist(quality) } ?: emptyList(),
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(crop = true)
            ?: getCover(id, quality),
        album = album,
        duration = duration?.toLong(),
        plays = view_count?.toLongOrNull(),
        releaseDate = album?.releaseDate,
        isExplicit = is_explicit,
        isPlayable = playableStatus,
        isRadioSupported = true,
        isFollowable = false,
        isSaveable = true,
        isLikeable = true,
        isHideable = true,
        isShareable = true,
        extras = extras.apply {
            put("videoId", id)
            put("isVideo", is_video.toString())
            put("availability", "public")
            put("trackType", trackType.name)
        }
    )
}

private fun getCover(
    id: String,
    quality: ThumbnailProvider.Quality
): ImageHolder.NetworkRequestImageHolder {
    return when (quality) {
        ThumbnailProvider.Quality.LOW -> "https://img.youtube.com/vi/$id/mqdefault.jpg"
        ThumbnailProvider.Quality.HIGH -> "https://img.youtube.com/vi/$id/maxresdefault.jpg"
    }.toImageHolder(crop = true)
}

fun YtmArtist.toArtist(
    quality: ThumbnailProvider.Quality,
): Artist {
    // Create an Artist with a special flag to identify it as a genuine Artist object
    return Artist(
        id = id,
        name = name ?: "Unknown",
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        bio = description,
        extras = mutableMapOf<String, String>().apply {
            subscribe_channel_id?.let { put("subId", it) }
            subscriber_count?.let { put("subscriberCount", it.toString()) }
            subscribed?.let { put("isSubscribed", it.toString()) }
            // Add special flag to identify this as a genuine Artist object
            put("genuineArtist", "true")
        }
    )
}

fun YtmArtist.toUser(
    quality: ThumbnailProvider.Quality,
): User {
    // Create a User object from a YtmArtist with enhanced metadata
    val subscriberCountText = subscriber_count?.let { count ->
        when {
            count >= 1000000 -> "${(count / 1000000).toInt()}M subscribers"
            count >= 1000 -> "${(count / 1000).toInt()}K subscribers"
            else -> "$count subscribers"
        }
    } ?: "Artist"
    
    return User(
        id = id,
        name = name ?: "Unknown",
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        subtitle = subscriberCountText,
        extras = mutableMapOf<String, String>().apply {
            subscribe_channel_id?.let { put("subId", it) }
            subscriber_count?.let { put("subscriberCount", it.toString()) }
            subscribed?.let { put("isSubscribed", it.toString()) }
            // Add a flag to identify this as originally an Artist
            put("isArtist", "true")
            put("userType", "artist")
            put("profileUrl", "https://music.youtube.com/channel/$id")
            // Add channel information
            put("channelId", id)
            put("displayName", name ?: "Unknown")
        }
    )
}

fun User.toArtist(): Artist {
    // Use the more comprehensive ModelTypeHelper to ensure proper conversion
    // with additional safety checks to prevent ClassCastException
    return ModelTypeHelper.userToArtist(this)
}

private fun parseYearString(yearValue: Any): Date? {
    val yearStr = yearValue.toString()
    return try {
        // First try to parse as timestamp (long value)
        if (yearStr.length >= 10 && yearStr.all { it.isDigit() }) {
            Date(yearStr.toLong())
        } else {
            // Otherwise parse as year
            Date(yearStr.toInt())
        }
    } catch (e: Exception) {
        // If that fails, try just the year
        try {
            Date(yearStr.toInt())
        } catch (e: Exception) {
            null
        }
    }
}

val json = Json { ignoreUnknownKeys = true }
suspend fun HttpResponse.getUsers(
    cookie: String,
    auth: String
) = bodyAsText().let {
    val trimmed = it.substringAfter(")]}'")
    json.decodeFromString<GoogleAccountResponse>(trimmed)
}.getUsers(cookie, auth)