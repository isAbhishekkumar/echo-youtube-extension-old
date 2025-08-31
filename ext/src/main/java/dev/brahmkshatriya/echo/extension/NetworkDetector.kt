package dev.brahmkshatriya.echo.extension

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network type detection for optimizing YouTube API requests
 * Detects different network types and applies appropriate strategies
 */
class NetworkDetector(private val client: HttpClient) {
    
    enum class NetworkType {
        WIFI_OPEN,        // Open WiFi with full YouTube access
        WIFI_RESTRICTED,  // Restricted WiFi (corporate, school, public)
        MOBILE_DATA,     // Mobile data connection
        UNKNOWN          // Unknown network type
    }
    
    /**
     * Detect the current network type by testing YouTube accessibility
     */
    suspend fun detectNetworkType(): NetworkType = withContext(Dispatchers.IO) {
        try {
            // Test 1: Check YouTube Music API access
            val musicAccessible = testEndpointAccess("https://music.youtube.com/youtubei/v1/player")
            
            // Test 2: Check main YouTube API access  
            val youtubeAccessible = testEndpointAccess("https://www.youtube.com/youtubei/v1/player")
            
            // Test 3: Check BotGuard API access (critical for PoToken)
            val botguardAccessible = testEndpointAccess("https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create")
            
            when {
                musicAccessible && youtubeAccessible && botguardAccessible -> NetworkType.WIFI_OPEN
                !musicAccessible && youtubeAccessible -> NetworkType.WIFI_RESTRICTED
                !botguardAccessible -> NetworkType.WIFI_RESTRICTED
                musicAccessible -> NetworkType.WIFI_OPEN
                else -> NetworkType.MOBILE_DATA // Assume mobile if tests are inconclusive
            }
            
        } catch (e: Exception) {
            println("DEBUG: Network detection failed: ${e.message}")
            NetworkType.UNKNOWN
        }
    }
    
    /**
     * Test if a specific endpoint is accessible
     */
    private suspend fun testEndpointAccess(endpointUrl: String): Boolean {
        return try {
            val response = client.get(endpointUrl) {
                timeout {
                    requestTimeoutMillis = 3000
                    connectTimeoutMillis = 3000
                }
                headers {
                    append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    append("Origin", "https://music.youtube.com")
                }
            }
            response.status.value in 200..399
            
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get optimal client priority order based on network type
     */
    fun getClientPriority(networkType: NetworkType): List<String> {
        return when (networkType) {
            NetworkType.WIFI_OPEN -> listOf(
                "ANDROID_MUSIC",  // Best for open networks
                "IOS",           // Good fallback
                "WEB_REMIX",     // Reliable for open WiFi
                "TVHTML5",       // If others fail
                "ANDROID"        // Last resort
            )
            
            NetworkType.WIFI_RESTRICTED -> listOf(
                "TVHTML5",       // Best for restricted networks (embedded player)
                "IOS",           // Good success rate on restricted WiFi
                "ANDROID",       // Sometimes works on restricted networks
                "ANDROID_MUSIC", // May be blocked on restricted WiFi
                "WEB_REMIX"      // Often blocked on restricted networks
            )
            
            NetworkType.MOBILE_DATA -> listOf(
                "ANDROID_MUSIC",  // Optimized for mobile
                "IOS",           // Good mobile client
                "ANDROID",       // Android fallback
                "WEB_REMIX",     // If others fail
                "TVHTML5"        // Last resort
            )
            
            NetworkType.UNKNOWN -> listOf(
                "IOS",           // Most reliable across networks
                "TVHTML5",       // Good fallback
                "ANDROID_MUSIC", // Try standard mobile
                "WEB_REMIX",     // Try web
                "ANDROID"        // Final fallback
            )
        }
    }
    
    /**
     * Get enhanced headers based on network type
     */
    fun getEnhancedHeaders(networkType: NetworkType): Map<String, String> {
        val baseHeaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache"
        )
        
        val networkSpecificHeaders = when (networkType) {
            NetworkType.WIFI_RESTRICTED -> mapOf(
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors", 
                "Sec-Fetch-Site" to "cross-site",
                "DNT" to "1",
                "Upgrade-Insecure-Requests" to "1"
            )
            
            NetworkType.WIFI_OPEN -> mapOf(
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"
            )
            
            else -> emptyMap()
        }
        
        return baseHeaders + networkSpecificHeaders
    }
    
    /**
     * Get request timeout based on network type
     */
    fun getRequestTimeout(networkType: NetworkType): Long {
        return when (networkType) {
            NetworkType.WIFI_RESTRICTED -> 15000L // Longer timeout for restricted networks
            NetworkType.MOBILE_DATA -> 10000L     // Moderate timeout for mobile
            else -> 8000L                         // Standard timeout for open networks
        }
    }
    
    /**
     * Get retry strategy based on network type
     */
    fun getRetryStrategy(networkType: NetworkType): RetryStrategy {
        return when (networkType) {
            NetworkType.WIFI_RESTRICTED -> RetryStrategy(
                maxAttempts = 5,
                baseDelay = 1000L,
                maxDelay = 10000L,
                exponentialBackoff = true
            )
            
            NetworkType.MOBILE_DATA -> RetryStrategy(
                maxAttempts = 3,
                baseDelay = 500L,
                maxDelay = 5000L,
                exponentialBackoff = true
            )
            
            else -> RetryStrategy(
                maxAttempts = 3,
                baseDelay = 500L,
                maxDelay = 3000L,
                exponentialBackoff = false
            )
        }
    }
    
    data class RetryStrategy(
        val maxAttempts: Int,
        val baseDelay: Long,
        val maxDelay: Long,
        val exponentialBackoff: Boolean
    )
}