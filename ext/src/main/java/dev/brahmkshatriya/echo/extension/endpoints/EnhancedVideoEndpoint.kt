package dev.brahmkshatriya.echo.extension.endpoints

import dev.brahmkshatriya.echo.extension.NetworkDetector
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
 * Enhanced Video Endpoint with network-aware multi-client fallback strategy
 * Specifically designed to handle 403 errors on WiFi networks with intelligent routing
 */
class EnhancedVideoEndpoint(
    override val api: YoutubeiApi,
    private val extension: YoutubeExtension
) : ApiEndpoint() {

    // Network detector for intelligent client selection
    private val networkDetector = NetworkDetector(api.client)
    
    // Enhanced client configurations with updated versions and better headers
    data class EnhancedClientConfig(
        val name: String,
        val version: String,
        val apiKey: String,
        val userAgent: String,
        val clientNumber: String,
        val contextBuilder: (String?) -> JsonObject,
        val priority: Map<NetworkDetector.NetworkType, Int> = emptyMap()
    ) {
        companion object {
            val ANDROID_MUSIC = EnhancedClientConfig(
                name = "ANDROID_MUSIC",
                version = "5.28.1",
                apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
                userAgent = "com.google.android.apps.youtube.music/5.28.1 (Linux; U; Android 11) gzip",
                clientNumber = "21",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "ANDROID_MUSIC")
                                put("clientVersion", "5.28.1")
                                put("androidSdkVersion", 33)
                                put("osName", "Android")
                                put("osVersion", "13")
                                put("platform", "MOBILE")
                                put("clientFormFactor", "SMALL_FORM_FACTOR")
                                visitorData?.let { put("visitorData", it) }
                            })
                            put("thirdParty", buildJsonObject {
                                put("embedUrl", "https://music.youtube.com/")
                            })
                            put("user", buildJsonObject {
                                put("lockedSafetyMode", false)
                            })
                            put("request", buildJsonObject {
                                put("useSsl", true)
                                put("internalExperimentFlags", emptyList<String>())
                                put("consistencyTokenJars", emptyList<String>())
                            })
                        })
                    }
                },
                priority = mapOf(
                    NetworkDetector.NetworkType.WIFI_OPEN to 1,
                    NetworkDetector.NetworkType.MOBILE_DATA to 1,
                    NetworkDetector.NetworkType.WIFI_RESTRICTED to 4,
                    NetworkDetector.NetworkType.UNKNOWN to 2
                )
            )

            val IOS = EnhancedClientConfig(
                name = "IOS",
                version = "19.45.4",
                apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_7 like Mac OS X;)",
                clientNumber = "5",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "IOS")
                                put("clientVersion", "19.45.4")
                                put("deviceMake", "Apple")
                                put("deviceModel", "iPhone16,2")
                                put("osName", "iOS")
                                put("osVersion", "17.7.1")
                                put("platform", "MOBILE")
                                visitorData?.let { put("visitorData", it) }
                            })
                            put("user", buildJsonObject {
                                put("lockedSafetyMode", false)
                            })
                        })
                    }
                },
                priority = mapOf(
                    NetworkDetector.NetworkType.WIFI_OPEN to 2,
                    NetworkDetector.NetworkType.MOBILE_DATA to 2,
                    NetworkDetector.NetworkType.WIFI_RESTRICTED to 2,
                    NetworkDetector.NetworkType.UNKNOWN to 1
                )
            )

            val TVHTML5 = EnhancedClientConfig(
                name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                version = "2.0",
                apiKey = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
                userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)",
                clientNumber = "85",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                                put("clientVersion", "2.0")
                                put("platform", "TV")
                                put("clientScreen", "EMBED")
                                visitorData?.let { put("visitorData", it) }
                            })
                            put("thirdParty", buildJsonObject {
                                put("embedUrl", "https://music.youtube.com/")
                            })
                        })
                    }
                },
                priority = mapOf(
                    NetworkDetector.NetworkType.WIFI_OPEN to 4,
                    NetworkDetector.NetworkType.MOBILE_DATA to 5,
                    NetworkDetector.NetworkType.WIFI_RESTRICTED to 1, // Best for restricted networks
                    NetworkDetector.NetworkType.UNKNOWN to 2
                )
            )

            val WEB_REMIX = EnhancedClientConfig(
                name = "WEB_REMIX",
                version = "1.20241204.01.00",
                apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                clientNumber = "67",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", "1.20241204.01.00")
                                put("platform", "DESKTOP")
                                put("browserName", "Chrome")
                                put("browserVersion", "131.0.0.0")
                                visitorData?.let { put("visitorData", it) }
                            })
                        })
                    }
                },
                priority = mapOf(
                    NetworkDetector.NetworkType.WIFI_OPEN to 3,
                    NetworkDetector.NetworkType.MOBILE_DATA to 4,
                    NetworkDetector.NetworkType.WIFI_RESTRICTED to 5, // Often blocked on restricted networks
                    NetworkDetector.NetworkType.UNKNOWN to 3
                )
            )

            val ANDROID = EnhancedClientConfig(
                name = "ANDROID",
                version = "18.48.37",
                apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
                userAgent = "com.google.android.youtube/18.48.37 (Linux; U; Android 13) gzip",
                clientNumber = "3",
                contextBuilder = { visitorData ->
                    buildJsonObject {
                        put("context", buildJsonObject {
                            put("client", buildJsonObject {
                                put("clientName", "ANDROID")
                                put("clientVersion", "18.48.37")
                                put("androidSdkVersion", 33)
                                put("osName", "Android")
                                put("osVersion", "13")
                                put("platform", "MOBILE")
                                visitorData?.let { put("visitorData", it) }
                            })
                            put("user", buildJsonObject {
                                put("lockedSafetyMode", false)
                            })
                        })
                    }
                },
                priority = mapOf(
                    NetworkDetector.NetworkType.WIFI_OPEN to 5,
                    NetworkDetector.NetworkType.MOBILE_DATA to 3,
                    NetworkDetector.NetworkType.WIFI_RESTRICTED to 3,
                    NetworkDetector.NetworkType.UNKNOWN to 4
                )
            )

            fun getAllConfigs() = listOf(ANDROID_MUSIC, IOS, TVHTML5, WEB_REMIX, ANDROID)
        }
    }

    /**
     * Enhanced video loading with network-aware multi-client fallback strategy
     * Detects network type and optimizes client selection accordingly
     */
    suspend fun getVideoWithEnhancedFallback(
        resolve: Boolean,
        videoId: String,
        playlistId: String? = null,
        enablePoToken: Boolean = true
    ): Pair<HttpResponse, String?> = coroutineScope {
        println("DEBUG: Enhanced video loading for videoId: $videoId")

        // Step 1: Detect network type
        val networkType = networkDetector.detectNetworkType()
        println("DEBUG: Detected network type: $networkType")

        // Step 2: Get retry strategy for this network type
        val retryStrategy = networkDetector.getRetryStrategy(networkType)

        // Step 3: Generate PoToken if enabled (more aggressive on restricted networks)
        val poToken = if (enablePoToken && extension.isPoTokenEnabled()) {
            try {
                // On restricted networks, always try PoToken generation
                if (networkType == NetworkDetector.NetworkType.WIFI_RESTRICTED) {
                    println("DEBUG: Restricted network detected, forcing PoToken generation")
                    extension.generatePoTokenForVideoPublic(videoId)
                } else {
                    // On other networks, try PoToken but don't fail if it doesn't work
                    runCatching { extension.generatePoTokenForVideoPublic(videoId) }.getOrNull()
                }
            } catch (e: Exception) {
                println("DEBUG: PoToken generation failed: ${e.message}")
                null
            }
        } else {
            null
        }

        // Step 4: Get network-optimized client priority
        val clientPriority = getClientPriorityForNetwork(networkType)
        println("DEBUG: Client priority for $networkType: ${clientPriority.map { it.name }}")

        // Step 5: Try clients with enhanced retry logic
        var lastException: Exception? = null
        var successfulResponse: HttpResponse? = null
        var successfulClient: EnhancedClientConfig? = null

        for ((attempt, client) in clientPriority.withIndex()) {
            if (attempt >= retryStrategy.maxAttempts) break

            try {
                println("DEBUG: Attempt ${attempt + 1}/${retryStrategy.maxAttempts} - Trying client: ${client.name}")

                val response = requestWithEnhancedClient(client, videoId, playlistId, poToken, networkType)

                // Validate response
                if (validateResponse(response)) {
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

                // Exponential backoff for restricted networks
                if (attempt < clientPriority.size - 1) {
                    val delayMs = if (retryStrategy.exponentialBackoff) {
                        minOf(retryStrategy.baseDelay * (1L shl attempt), retryStrategy.maxDelay)
                    } else {
                        retryStrategy.baseDelay
                    }
                    println("DEBUG: Waiting ${delayMs}ms before next attempt")
                    delay(delayMs)
                }
            }
        }

        // Step 6: If all enhanced clients failed, try legacy fallback
        if (successfulResponse == null) {
            try {
                println("DEBUG: All enhanced clients failed, trying legacy fallback")
                val legacyResponse = requestLegacyFallback(videoId, playlistId, poToken, networkType)
                
                if (validateResponse(legacyResponse)) {
                    successfulResponse = legacyResponse
                    successfulClient = null
                }
            } catch (e: Exception) {
                println("DEBUG: Legacy fallback also failed: ${e.message}")
                lastException = e
            }
        }

        return@coroutineScope if (successfulResponse != null) {
            successfulResponse to (successfulClient?.name)
        } else {
            throw lastException ?: Exception("All clients failed to load video on $networkType")
        }
    }

    /**
     * Get client priority order based on network type
     */
    private fun getClientPriorityForNetwork(networkType: NetworkDetector.NetworkType): List<EnhancedClientConfig> {
        return EnhancedClientConfig.getAllConfigs()
            .sortedBy { config ->
                config.priority[networkType] ?: Int.MAX_VALUE
            }
    }

    /**
     * Enhanced request with network-specific optimizations
     */
    private suspend fun requestWithEnhancedClient(
        client: EnhancedClientConfig,
        videoId: String,
        playlistId: String?,
        poToken: String?,
        networkType: NetworkDetector.NetworkType
    ): HttpResponse {
        val context = client.contextBuilder(api.visitor_id)
        val enhancedHeaders = networkDetector.getEnhancedHeaders(networkType)
        val timeout = networkDetector.getRequestTimeout(networkType)

        return api.client.post("https://music.youtube.com/youtubei/v1/player") {
            timeout {
                requestTimeoutMillis = timeout
                connectTimeoutMillis = timeout / 2
            }
            
            parameter("key", client.apiKey)
            parameter("prettyPrint", "false")
            contentType(ContentType.Application.Json)
            
            // Add enhanced headers
            headers {
                // Base headers
                append("X-Goog-Api-Format-Version", "1")
                append("X-YouTube-Client-Name", client.clientNumber)
                append("X-YouTube-Client-Version", client.version)
                append("Origin", "https://music.youtube.com")
                append("Referer", "https://music.youtube.com/")
                append("User-Agent", client.userAgent)
                
                // Network-specific enhanced headers
                enhancedHeaders.forEach { (key, value) ->
                    append(key, value)
                }
                
                // Authentication headers if logged in
                if (extension.isLoggedIn()) {
                    extension.getAuthHeaders()?.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                
                // PoToken header (critical for restricted networks)
                poToken?.let {
                    append("X-Goog-PoToken", it)
                }
                
                // Additional security headers for restricted networks
                if (networkType == NetworkDetector.NetworkType.WIFI_RESTRICTED) {
                    append("Sec-Ch-Ua", "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    append("Sec-Ch-Ua-Mobile", "?0")
                    append("Sec-Ch-Ua-Platform", "\"Windows\"")
                }
            }
            
            // Enhanced request body
            val requestBody = buildJsonObject {
                put("context", context)
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
                
                // Additional parameters for better success rate
                put("racyCheckOk", false)
                put("contentCheckOk", false)
                
                // Network-specific parameters
                if (networkType == NetworkDetector.NetworkType.WIFI_RESTRICTED) {
                    put("params", "CgIIAQ%3D%3D") // Additional params for restricted networks
                }
            }
            setBody(requestBody)
        }
    }

    /**
     * Legacy fallback request for maximum compatibility
     */
    private suspend fun requestLegacyFallback(
        videoId: String,
        playlistId: String?,
        poToken: String?,
        networkType: NetworkDetector.NetworkType
    ): HttpResponse {
        val context = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20241204.01.00")
                    api.visitor_id?.let { put("visitorData", it) }
                })
            })
        }

        return api.client.post("https://www.youtube.com/youtubei/v1/player") {
            parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30")
            parameter("prettyPrint", "false")
            contentType(ContentType.Application.Json)
            
            headers {
                append("X-Goog-Api-Format-Version", "1")
                append("X-YouTube-Client-Name", "1")
                append("X-YouTube-Client-Version", "2.20241204.01.00")
                append("Origin", "https://www.youtube.com")
                append("Referer", "https://www.youtube.com/")
                append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                
                // Add PoToken if available
                poToken?.let {
                    append("X-Goog-PoToken", it)
                }
            }
            
            val requestBody = buildJsonObject {
                put("context", context)
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
            }
            setBody(requestBody)
        }
    }

    /**
     * Validate HTTP response to ensure it contains valid video data
     */
    private suspend fun validateResponse(response: HttpResponse): Boolean {
        return try {
            val responseBody = response.body<JsonObject>()
            val videoDetails = responseBody["videoDetails"]?.jsonObject
            val streamingData = responseBody["streamingData"]?.jsonObject
            
            // Check for basic structure
            val hasVideoDetails = videoDetails != null && 
                                  videoDetails["videoId"]?.jsonPrimitive?.content?.isNotEmpty() == true
            val hasStreamingData = streamingData != null && 
                                   (streamingData["adaptiveFormats"]?.jsonArray?.isNotEmpty() == true ||
                                    streamingData["formats"]?.jsonArray?.isNotEmpty() == true)
            
            hasVideoDetails && hasStreamingData
            
        } catch (e: Exception) {
            println("DEBUG: Response validation failed: ${e.message}")
            false
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    suspend fun getVideo(resolve: Boolean, id: String, playlist: String? = null) = coroutineScope {
        val enhancedResult = getVideoWithEnhancedFallback(resolve, id, playlist)
        val web = if (resolve) {
            try {
                val response = requestLegacyFallback(id, playlist, null, NetworkDetector.NetworkType.UNKNOWN).body<JsonObject>()
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

    /**
     * Get network diagnostics for debugging
     */
    suspend fun getNetworkDiagnostics(): String {
        val networkType = networkDetector.detectNetworkType()
        val clientPriority = getClientPriorityForNetwork(networkType)
        val retryStrategy = networkDetector.getRetryStrategy(networkType)
        
        return """
            Network Type: $networkType
            Client Priority: ${clientPriority.map { it.name }}
            Max Attempts: ${retryStrategy.maxAttempts}
            Base Delay: ${retryStrategy.baseDelay}ms
            Exponential Backoff: ${retryStrategy.exponentialBackoff}
        """.trimIndent()
    }
}