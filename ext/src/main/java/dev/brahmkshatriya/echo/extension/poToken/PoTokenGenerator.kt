package dev.brahmkshatriya.echo.extension.poToken

import dev.brahmkshatriya.echo.common.helpers.WebViewClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class PoTokenGenerator(
    private val webViewClient: WebViewClient,
) {
    private val isWebViewSupported = AtomicBoolean(true)
    private var webViewBadImpl = false
    private var lastError: Throwable? = null

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
    ): PoToken? {
        if (!isWebViewSupported.get() || webViewBadImpl) {
            println("DEBUG: WebView not supported or has bad implementation, skipping PoToken generation")
            return null
        }

        return try {
            kotlinx.coroutines.runBlocking { 
                getWebClientPoToken(videoId, sessionId, forceRecreate = false) 
            }
        } catch (e: PoTokenException) {
            when (e) {
                is PoTokenException.BadWebViewException -> {
                    println("DEBUG: WebView implementation is broken, disabling PoToken generation: ${e.message}")
                    webViewBadImpl = true
                    lastError = e
                    null
                }
                is PoTokenException.TimeoutException -> {
                    println("DEBUG: PoToken generation timed out: ${e.message}")
                    lastError = e
                    null
                }
                else -> {
                    println("DEBUG: PoToken generation failed: ${e.message}")
                    lastError = e
                    null
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Unexpected error during PoToken generation: ${e.message}")
            lastError = e
            null
        }
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoToken {
        println("DEBUG: Web PoToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator: PoTokenWebView, streamingPot: String, hasBeenRecreated: Boolean) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired || webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    println("DEBUG: Creating new PoToken WebView instance")
                    webPoTokenSessionId = sessionId

                    // Close existing generator
                    webPoTokenGenerator?.close()

                    // Create a new webPoTokenGenerator
                    val createResult = PoTokenWebView.create(webViewClient, videoId, sessionId)
                    if (createResult.isFailure) {
                        val exception = createResult.exceptionOrNull() ?: PoTokenException.GenerationException("Unknown error creating PoToken WebView")
                        throw PoTokenException.GenerationException("Failed to create PoToken WebView", exception)
                    }
                    
                    webPoTokenGenerator = createResult.getOrThrow()

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    println("DEBUG: Generating streaming PoToken")
                    val streamingResult = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                    if (streamingResult.isFailure) {
                        val exception = streamingResult.exceptionOrNull() ?: PoTokenException.GenerationException("Unknown error generating streaming PoToken")
                        throw PoTokenException.GenerationException("Failed to generate streaming PoToken", exception)
                    }
                    
                    webPoTokenStreamingPot = streamingResult.getOrThrow()
                    println("DEBUG: Streaming PoToken generated successfully")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreated)
            }

        val playerPot =
            try {
                // Not using synchronized here, since poTokenGenerator would be able to generate
                // multiple poTokens in parallel if needed. The only important thing is for exactly one
                // streaming poToken (based on [sessionId]) to be generated before anything else.
                println("DEBUG: Generating player PoToken for videoId: $videoId")
                val playerResult = poTokenGenerator.generatePoToken(videoId)
                if (playerResult.isFailure) {
                    val exception = playerResult.exceptionOrNull() ?: PoTokenException.GenerationException("Unknown error generating player PoToken")
                    throw PoTokenException.GenerationException("Failed to generate player PoToken", exception)
                }
                
                playerResult.getOrThrow()
            } catch (throwable: Throwable) {
                if (hasBeenRecreated) {
                    // the poTokenGenerator has just been recreated (and possibly this is already the
                    // second time we try), so there is likely nothing we can do
                    throw throwable
                } else {
                    // retry, this time recreating the [webPoTokenGenerator] from scratch;
                    // this might happen for example if the app goes in the background and the WebView
                    // content is lost
                    println("DEBUG: Failed to obtain PoToken, retrying with recreation: ${throwable.message}")
                    return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
                }
            }

        println("DEBUG: PoToken generation successful - playerPot length: ${playerPot.length}, streamingPot length: ${streamingPot.length}")

        return PoToken(playerPot, streamingPot)
    }

    fun reset() {
        kotlinx.coroutines.runBlocking {
            webPoTokenGenLock.withLock {
                webPoTokenGenerator?.close()
                webPoTokenGenerator = null
                webPoTokenSessionId = null
                webPoTokenStreamingPot = null
                webViewBadImpl = false
                lastError = null
                println("DEBUG: PoToken generator reset")
            }
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