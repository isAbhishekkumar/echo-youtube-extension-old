package dev.brahmkshatriya.echo.extension.poToken

import dev.brahmkshatriya.echo.common.helpers.WebViewClient
import dev.brahmkshatriya.echo.extension.NetworkDetector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced PoToken Generator with network-aware optimizations
 * Specifically designed to handle WiFi network restrictions and improve success rates
 */
class EnhancedPoTokenGenerator(
    private val webViewClient: WebViewClient,
    private val networkDetector: NetworkDetector? = null
) {
    private val isWebViewSupported = AtomicBoolean(true)
    private var webViewBadImpl = false
    private var lastError: Throwable? = null

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    // Enhanced caching with network awareness
    private val poTokenCache = ConcurrentHashMap<String, CachedPoToken>()
    private val networkTypeCache = mutableMapOf<String, NetworkDetector.NetworkType>()
    
    // Performance tracking
    private var totalAttempts = 0
    private var successfulAttempts = 0
    private var lastNetworkType: NetworkDetector.NetworkType? = null

    data class CachedPoToken(
        val poToken: PoToken,
        val timestamp: Long,
        val networkType: NetworkDetector.NetworkType,
        val videoId: String
    ) {
        fun isValid(): Boolean {
            val age = System.currentTimeMillis() - timestamp
            // Cache tokens for 25 minutes (YouTube tokens typically expire in 30 minutes)
            return age < 25 * 60 * 1000
        }
    }

    /**
     * Helper function to validate PoToken
     */
    private fun isPoTokenValid(poToken: PoToken): Boolean {
        return try {
            poToken.playerRequestPoToken.isNotBlank() && 
            poToken.streamingDataPoToken.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enhanced PoToken generation with network awareness and retry logic
     */
    suspend fun getEnhancedWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRefresh: Boolean = false
    ): PoToken? {
        if (!isWebViewSupported.get() || webViewBadImpl) {
            println("DEBUG: WebView not supported or has bad implementation, skipping PoToken generation")
            return null
        }

        totalAttempts++
        
        return try {
            // Check cache first (unless forced refresh)
            if (!forceRefresh) {
                val cached = getCachedPoToken(videoId, sessionId)
                if (cached != null) {
                    println("DEBUG: Using cached PoToken for videoId: $videoId")
                    return cached
                }
            }

            // Detect network type for optimization
            val networkType = detectNetworkType()
            lastNetworkType = networkType
            
            println("DEBUG: Enhanced PoToken generation for videoId: $videoId, network: $networkType")
            
            // Use network-aware generation strategy
            val result = when (networkType) {
                NetworkDetector.NetworkType.WIFI_RESTRICTED -> {
                    generateWithRestrictedStrategy(videoId, sessionId)
                }
                NetworkDetector.NetworkType.WIFI_OPEN -> {
                    generateWithOpenStrategy(videoId, sessionId)
                }
                NetworkDetector.NetworkType.MOBILE_DATA -> {
                    generateWithMobileStrategy(videoId, sessionId)
                }
                NetworkDetector.NetworkType.UNKNOWN -> {
                    generateWithFallbackStrategy(videoId, sessionId)
                }
            }
            
            result?.let { poToken ->
                // Cache successful result
                cachePoToken(videoId, sessionId, poToken, networkType)
                successfulAttempts++
                println("DEBUG: PoToken generation successful (${getSuccessRate()}% success rate)")
            }
            
            result
            
        } catch (e: PoTokenException) {
            handlePoTokenException(e)
            null
        } catch (e: Exception) {
            println("DEBUG: Unexpected error during PoToken generation: ${e.message}")
            lastError = e
            null
        }
    }

    /**
     * Generate PoToken with strategy optimized for restricted WiFi networks
     */
    private suspend fun generateWithRestrictedStrategy(
        videoId: String,
        sessionId: String
    ): PoToken? {
        println("DEBUG: Using restricted network strategy for PoToken generation")
        
        return withContext(Dispatchers.IO) {
            var attempt = 0
            val maxAttempts = 3
            
            while (attempt < maxAttempts) {
                try {
                    attempt++
                    println("DEBUG: Restricted network attempt $attempt/$maxAttempts")
                    
                    // For restricted networks, always force recreation to avoid stale state
                    val poToken = getWebClientPoTokenInternal(videoId, sessionId, forceRecreate = true)
                    
                    if (poToken != null && isPoTokenValid(poToken)) {
                        println("DEBUG: Restricted network strategy succeeded on attempt $attempt")
                        return@withContext poToken
                    }
                    
                } catch (e: Exception) {
                    println("DEBUG: Restricted network attempt $attempt failed: ${e.message}")
                    
                    if (attempt < maxAttempts) {
                        // Progressive delay for restricted networks
                        val delayMs = 2000L * attempt
                        delay(delayMs)
                    }
                }
            }
            
            println("DEBUG: Restricted network strategy failed after $maxAttempts attempts")
            null
        }
    }

    /**
     * Generate PoToken with strategy optimized for open WiFi networks
     */
    private suspend fun generateWithOpenStrategy(
        videoId: String,
        sessionId: String
    ): PoToken? {
        println("DEBUG: Using open network strategy for PoToken generation")
        
        return try {
            // For open networks, try cached version first, then generate new if needed
            getWebClientPoTokenInternal(videoId, sessionId, forceRecreate = false)
        } catch (e: Exception) {
            println("DEBUG: Open network strategy failed, retrying with recreation: ${e.message}")
            try {
                getWebClientPoTokenInternal(videoId, sessionId, forceRecreate = true)
            } catch (e2: Exception) {
                println("DEBUG: Open network recreation also failed: ${e2.message}")
                null
            }
        }
    }

    /**
     * Generate PoToken with strategy optimized for mobile data
     */
    private suspend fun generateWithMobileStrategy(
        videoId: String,
        sessionId: String
    ): PoToken? {
        println("DEBUG: Using mobile data strategy for PoToken generation")
        
        return try {
            // For mobile data, prioritize speed and efficiency
            getWebClientPoTokenInternal(videoId, sessionId, forceRecreate = false)
        } catch (e: Exception) {
            println("DEBUG: Mobile data strategy failed: ${e.message}")
            null
        }
    }

    /**
     * Generate PoToken with fallback strategy for unknown networks
     */
    private suspend fun generateWithFallbackStrategy(
        videoId: String,
        sessionId: String
    ): PoToken? {
        println("DEBUG: Using fallback strategy for PoToken generation")
        
        return try {
            // Try standard approach first
            getWebClientPoTokenInternal(videoId, sessionId, forceRecreate = false)
        } catch (e: Exception) {
            println("DEBUG: Fallback strategy failed, trying alternative approach")
            
            // Alternative: Try with different session ID
            try {
                val alternativeSessionId = "${sessionId}_alt_${System.currentTimeMillis()}"
                getWebClientPoTokenInternal(videoId, alternativeSessionId, forceRecreate = true)
            } catch (e2: Exception) {
                println("DEBUG: Alternative fallback also failed: ${e2.message}")
                null
            }
        }
    }

    /**
     * Internal PoToken generation method (enhanced version of original)
     */
    private suspend fun getWebClientPoTokenInternal(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoToken? {
        return webPoTokenGenLock.withLock {
            val shouldRecreate = forceRecreate || 
                                webPoTokenGenerator == null || 
                                webPoTokenGenerator!!.isExpired || 
                                webPoTokenSessionId != sessionId

            if (shouldRecreate) {
                println("DEBUG: Creating new PoToken WebView instance")
                webPoTokenSessionId = sessionId

                // Close existing generator
                webPoTokenGenerator?.close()

                // Create a new webPoTokenGenerator with enhanced error handling
                val createResult = PoTokenWebView.create(webViewClient, videoId, sessionId)
                if (createResult.isFailure) {
                    val exception = createResult.exceptionOrNull() ?: PoTokenException.GenerationException("Unknown error creating PoToken WebView")
                    throw PoTokenException.GenerationException("Failed to create PoToken WebView", exception)
                }
                
                webPoTokenGenerator = createResult.getOrThrow()

                // Generate streaming PoToken with enhanced error handling
                println("DEBUG: Generating streaming PoToken")
                val streamingResult = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                if (streamingResult.isFailure) {
                    val exception = streamingResult.exceptionOrNull() ?: PoTokenException.GenerationException("Unknown error generating streaming PoToken")
                    throw PoTokenException.GenerationException("Failed to generate streaming PoToken", exception)
                }
                
                webPoTokenStreamingPot = streamingResult.getOrThrow()
                println("DEBUG: Streaming PoToken generated successfully")
            }

            val poTokenGenerator: PoTokenWebView = webPoTokenGenerator!!
            val streamingPot: String = webPoTokenStreamingPot!!

            // Generate player PoToken with enhanced retry logic
            println("DEBUG: Generating player PoToken for videoId: $videoId")
            val playerResult = poTokenGenerator.generatePoToken(videoId)
            if (playerResult.isFailure) {
                val exception = playerResult.exceptionOrNull() ?: PoTokenException.GenerationException("Unknown error generating player PoToken")
                throw PoTokenException.GenerationException("Failed to generate player PoToken", exception)
            }
            
            val playerPot = playerResult.getOrThrow()
            println("DEBUG: PoToken generation successful - playerPot length: ${playerPot.length}, streamingPot length: ${streamingPot.length}")

            PoToken(playerPot, streamingPot)
        }
    }

    /**
     * Detect current network type
     */
    private suspend fun detectNetworkType(): NetworkDetector.NetworkType {
        return networkDetector?.detectNetworkType() ?: NetworkDetector.NetworkType.UNKNOWN
    }

    /**
     * Get cached PoToken if available and valid
     */
    private fun getCachedPoToken(videoId: String, sessionId: String): PoToken? {
        val cacheKey = "${videoId}_${sessionId}"
        val cached = poTokenCache[cacheKey]
        
        return if (cached != null && cached.isValid()) {
            // Verify network type is compatible
            val currentNetworkType = lastNetworkType
            if (currentNetworkType == null || cached.networkType == currentNetworkType) {
                cached.poToken
            } else {
                // Network type changed, invalidate cache
                poTokenCache.remove(cacheKey)
                null
            }
        } else {
            // Remove expired cache entry
            poTokenCache.remove(cacheKey)
            null
        }
    }

    /**
     * Cache PoToken for future use
     */
    private fun cachePoToken(
        videoId: String,
        sessionId: String,
        poToken: PoToken,
        networkType: NetworkDetector.NetworkType
    ) {
        val cacheKey = "${videoId}_${sessionId}"
        val cached = CachedPoToken(
            poToken = poToken,
            timestamp = System.currentTimeMillis(),
            networkType = networkType,
            videoId = videoId
        )
        
        poTokenCache[cacheKey] = cached
        
        // Clean up old cache entries (keep only last 50)
        if (poTokenCache.size > 50) {
            val oldestKey = poTokenCache.keys.minByOrNull { key ->
                poTokenCache[key]?.timestamp ?: 0L
            }
            oldestKey?.let { poTokenCache.remove(it) }
        }
    }

    /**
     * Handle PoToken exceptions with enhanced error handling
     */
    private fun handlePoTokenException(e: PoTokenException): Nothing {
        when (e) {
            is PoTokenException.BadWebViewException -> {
                println("DEBUG: WebView implementation is broken, disabling PoToken generation: ${e.message}")
                webViewBadImpl = true
                lastError = e
                throw e
            }
            is PoTokenException.TimeoutException -> {
                println("DEBUG: PoToken generation timed out: ${e.message}")
                lastError = e
                throw e
            }
            is PoTokenException.ExpiredException -> {
                println("DEBUG: PoToken WebView expired, attempting recreation")
                lastError = e
                throw e
            }
            else -> {
                println("DEBUG: PoToken generation failed: ${e.message}")
                lastError = e
                throw e
            }
        }
    }

    /**
     * Enhanced reset method with network awareness
     */
    fun reset() {
        kotlinx.coroutines.runBlocking {
            webPoTokenGenLock.withLock {
                webPoTokenGenerator?.close()
                webPoTokenGenerator = null
                webPoTokenSessionId = null
                webPoTokenStreamingPot = null
                webViewBadImpl = false
                lastError = null
                
                // Clear cache
                poTokenCache.clear()
                networkTypeCache.clear()
                
                // Reset statistics
                totalAttempts = 0
                successfulAttempts = 0
                lastNetworkType = null
                
                println("DEBUG: Enhanced PoToken generator reset")
            }
        }
    }

    /**
     * Get enhanced status information
     */
    fun getEnhancedStatus(): EnhancedStatus {
        return EnhancedStatus(
            webViewStatus = when {
                !isWebViewSupported.get() -> "DISABLED"
                webViewBadImpl -> "BROKEN"
                webPoTokenGenerator == null -> "NOT_INITIALIZED"
                webPoTokenGenerator!!.isExpired -> "EXPIRED"
                else -> "READY"
            },
            successRate = getSuccessRate(),
            cacheSize = poTokenCache.size,
            lastNetworkType = lastNetworkType,
            totalAttempts = totalAttempts,
            successfulAttempts = successfulAttempts,
            lastError = lastError?.message
        )
    }

    /**
     * Calculate success rate
     */
    private fun getSuccessRate(): Int {
        return if (totalAttempts == 0) 0 else (successfulAttempts * 100 / totalAttempts)
    }

    /**
     * Enhanced status information
     */
    data class EnhancedStatus(
        val webViewStatus: String,
        val successRate: Int,
        val cacheSize: Int,
        val lastNetworkType: NetworkDetector.NetworkType?,
        val totalAttempts: Int,
        val successfulAttempts: Int,
        val lastError: String?
    )

    // Delegate original methods for backward compatibility
    fun getWebClientPoToken(videoId: String, sessionId: String): PoToken? {
        return kotlinx.coroutines.runBlocking { 
            getEnhancedWebClientPoToken(videoId, sessionId) 
        }
    }

    fun setWebViewSupported(supported: Boolean) {
        isWebViewSupported.set(supported)
        println("DEBUG: WebView support set to: $supported")
    }

    fun getLastError(): Throwable? = lastError

    fun isWebViewAvailable(): Boolean = isWebViewSupported.get() && !webViewBadImpl

    fun getStatus(): String {
        return when {
            !isWebViewSupported.get() -> "WebView support disabled"
            webViewBadImpl -> "WebView implementation broken"
            webPoTokenGenerator == null -> "Not initialized"
            webPoTokenGenerator!!.isExpired -> "Expired"
            else -> "Ready"
        }
    }
}