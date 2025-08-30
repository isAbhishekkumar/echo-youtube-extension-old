package dev.brahmkshatriya.echo.extension.endpoints

import dev.brahmkshatriya.echo.extension.YoutubeExtension
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import dev.toastbits.ytmkt.model.ApiEndpoint
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Enhanced Video Endpoint with multi-client fallback strategy inspired by InnerTune
 * Provides robust video loading with multiple fallback mechanisms to avoid 403 errors
 */
class EnhancedVideoEndpoint(
    override val api: YoutubeiApi,
    private val extension: YoutubeExtension
) : ApiEndpoint() {

    // Multiple client configurations for fallback strategy
    private val clients = listOf(
        ClientConfig.ANDROID_MUSIC,
        ClientConfig.IOS,
        ClientConfig.TVHTML5,
        ClientConfig.WEB_REMIX,
        ClientConfig.ANDROID
    )

    data class ClientConfig(
        val name: String,
        val version: String,
        val apiKey: String,
        val userAgent: String,
        val contextBuilder: (String?) -> JsonObject
    ) {
        companion object {
            val ANDROID_MUSIC = ClientConfig(
                name = "ANDROID_MUSIC",
                version = "5.01",
                apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
                userAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Mobile Safari/537.36",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "ANDROID_MUSIC")
                                put("clientVersion", "5.01")
                                put("androidSdkVersion", 30)
                                put("osName", "Android")
                                put("osVersion", "12")
                                put("platform", "MOBILE")
                                visitorData?.let { put("visitorData", it) }
                            })
                            put("thirdParty", buildJsonObject {
                                put("embedUrl", "https://www.youtube.com/")
                            })
                        })
                    }
                }
            )

            val IOS = ClientConfig(
                name = "IOS",
                version = "19.34.2",
                apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
                userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "IOS")
                                put("clientVersion", "19.34.2")
                                put("deviceMake", "Apple")
                                put("deviceModel", "iPhone16,2")
                                put("osName", "iOS")
                                put("osVersion", "17.5.1.21F90")
                                visitorData?.let { put("visitorData", it) }
                            })
                        })
                    }
                }
            )

            val TVHTML5 = ClientConfig(
                name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                version = "2.0",
                apiKey = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
                userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                                put("clientVersion", "2.0")
                                put("platform", "TV")
                                visitorData?.let { put("visitorData", it) }
                            })
                            put("thirdParty", buildJsonObject {
                                put("embedUrl", "https://www.youtube.com/")
                            })
                        })
                    }
                }
            )

            val WEB_REMIX = ClientConfig(
                name = "WEB_REMIX",
                version = "1.20220606.03.00",
                apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", "1.20220606.03.00")
                                put("platform", "DESKTOP")
                                visitorData?.let { put("visitorData", it) }
                            })
                        })
                    }
                }
            )

            val ANDROID = ClientConfig(
                name = "ANDROID",
                version = "17.13.3",
                apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
                userAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Mobile Safari/537.36",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "ANDROID")
                                put("clientVersion", "17.13.3")
                                put("androidSdkVersion", 30)
                                put("osName", "Android")
                                put("osVersion", "12")
                                put("platform", "MOBILE")
                                visitorData?.let { put("visitorData", it) }
                            })
                        })
                    }
                }
            )
        }
    }

    /**
     * Enhanced video loading with multi-client fallback strategy
     * Attempts multiple clients in order of preference until one succeeds
     */
    suspend fun getVideoWithFallback(
        resolve: Boolean,
        videoId: String,
        playlistId: String? = null,
        enablePoToken: Boolean = true
    ): Pair<HttpResponse, String?> = coroutineScope {
        println("DEBUG: Enhanced video loading for videoId: $videoId")

        // Generate PoToken if enabled
        val poToken = if (enablePoToken && extension.isPoTokenEnabled()) {
            try {
                extension.generatePoTokenForVideoPublic(videoId)
            } catch (e: Exception) {
                println("DEBUG: PoToken generation failed: ${e.message}")
                null
            }
        } else {
            null
        }

        // Try clients in order of preference
        val preferredClients = if (extension.isLoggedIn()) {
            listOf(ClientConfig.ANDROID_MUSIC, ClientConfig.IOS, ClientConfig.TVHTML5, ClientConfig.WEB_REMIX)
        } else {
            listOf(ClientConfig.IOS, ClientConfig.TVHTML5, ClientConfig.WEB_REMIX, ClientConfig.ANDROID)
        }

        var lastException: Exception? = null
        var successfulResponse: HttpResponse? = null
        var successfulClient: ClientConfig? = null

        for (client in preferredClients) {
            try {
                println("DEBUG: Trying client: ${client.name}")
                
                val response = requestWithClient(client, videoId, playlistId, poToken)
                
                // Check if the response is valid by parsing it
                val responseBody = response.body<JsonObject>()
                val videoDetails = responseBody["videoDetails"]?.jsonObject
                val streamingData = responseBody["streamingData"]?.jsonObject
                
                if (videoDetails != null && streamingData != null) {
                    println("DEBUG: Successfully loaded video with client: ${client.name}")
                    successfulResponse = response
                    successfulClient = client
                    break
                } else {
                    println("DEBUG: Invalid response from client: ${client.name}")
                }
                
            } catch (e: Exception) {
                println("DEBUG: Client ${client.name} failed: ${e.message}")
                lastException = e
                // Small delay between retries to avoid rate limiting
                delay(200)
            }
        }

        // If all clients failed, try with basic web context as last resort
        if (successfulResponse == null) {
            try {
                println("DEBUG: Trying basic web context as last resort")
                val basicResponse = requestBasicWeb(videoId, playlistId)
                val responseBody = basicResponse.body<JsonObject>()
                val videoDetails = responseBody["videoDetails"]?.jsonObject
                
                if (videoDetails != null) {
                    successfulResponse = basicResponse
                    successfulClient = null
                }
            } catch (e: Exception) {
                println("DEBUG: Basic web context also failed: ${e.message}")
                lastException = e
            }
        }

        return@coroutineScope if (successfulResponse != null) {
            successfulResponse to (successfulClient?.name)
        } else {
            throw lastException ?: Exception("All clients failed to load video")
        }
    }

    /**
     * Make request with specific client configuration
     */
    private suspend fun requestWithClient(
        client: ClientConfig,
        videoId: String,
        playlistId: String?,
        poToken: String?
    ): HttpResponse {
        val context = client.contextBuilder(api.visitor_id)
        
        return api.client.post("https://music.youtube.com/youtubei/v1/player") {
            parameter("key", client.apiKey)
            contentType(ContentType.Application.Json)
            
            // Add enhanced headers
            headers {
                append("X-Goog-Api-Format-Version", "1")
                append("X-YouTube-Client-Name", client.name)
                append("X-YouTube-Client-Version", client.version)
                append("X-Origin", "https://music.youtube.com")
                append("Referer", "https://music.youtube.com/")
                append("User-Agent", client.userAgent)
                
                // Add authentication if logged in
                if (extension.isLoggedIn()) {
                    extension.getAuthHeaders()?.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                
                // Add PoToken if available
                poToken?.let {
                    append("X-Goog-PoToken", it)
                }
            }
            
            // Set request body
            setBody(context.apply {
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
            })
        }
    }

    /**
     * Basic web request as fallback
     */
    private suspend fun requestBasicWeb(videoId: String, playlistId: String?): HttpResponse {
        val context = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "WEB")
                    put("clientVersion", "2.2021111")
                    api.visitor_id?.let { put("visitorData", it) }
                })
            })
        }

        return api.client.post("https://music.youtube.com/youtubei/v1/player") {
            parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
            contentType(ContentType.Application.Json)
            headers {
                append("X-Goog-Api-Format-Version", "1")
                append("X-YouTube-Client-Name", "WEB")
                append("X-YouTube-Client-Version", "2.2021111")
                append("X-Origin", "https://music.youtube.com")
                append("Referer", "https://music.youtube.com/")
                append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36")
            }
            setBody(context.apply {
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
            })
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    suspend fun getVideo(resolve: Boolean, id: String, playlist: String? = null) = coroutineScope {
        val enhancedResult = getVideoWithFallback(resolve, id, playlist)
        val web = if (resolve) {
            try {
                // Use the basic web request for legacy compatibility
                val response = requestBasicWeb(id, playlist).body<JsonObject>()
                val videoDetails = response["videoDetails"]?.jsonObject
                videoDetails?.get("musicVideoType")?.jsonPrimitive?.content
            } catch (e: Exception) {
                println("DEBUG: Web remix resolve failed: ${e.message}")
                null
            }
        } else {
            null
        }
        enhancedResult.first to web
    }

    private fun iosContext() = buildJsonObject {
        put("context", buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", "IOS")
                put("clientVersion", "19.34.2")
                put("visitorData", api.visitor_id)
            })
        })
    }

    private val webRemix = buildJsonObject {
        put("context", buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20220606.03.00")
            })
        })
    }
}