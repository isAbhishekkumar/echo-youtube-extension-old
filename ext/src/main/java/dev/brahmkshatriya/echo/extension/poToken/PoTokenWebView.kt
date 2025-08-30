package dev.brahmkshatriya.echo.extension.poToken

import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class PoTokenWebView(
    private val webViewClient: dev.brahmkshatriya.echo.common.helpers.WebViewClient,
    private val videoId: String,
    private val sessionId: String,
) {
    private var expirationInstant: Instant = Instant.now()
    private var isInitialized = false
    private var lastError: Throwable? = null

    companion object {
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val PO_TOKEN_HTML_URL = "file:///android_asset/po_token.html"

        suspend fun create(
            webViewClient: dev.brahmkshatriya.echo.common.helpers.WebViewClient,
            videoId: String,
            sessionId: String,
        ): Result<PoTokenWebView> {
            return try {
                val webView = PoTokenWebView(webViewClient, videoId, sessionId)
                webView.initialize()
                Result.success(webView)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun initialize() {
        try {
            // Step 1: Load the HTML asset and check if it's ready
            val loadResult = loadHtmlAsset()
            if (loadResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to load HTML asset", loadResult.exceptionOrNull())
            }

            // Step 2: Create BotGuard challenge
            val challengeResult = createBotGuardChallenge()
            if (challengeResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to create BotGuard challenge", challengeResult.exceptionOrNull())
            }

            val challengeData = challengeResult.getOrThrow()
            
            // Step 3: Run BotGuard and get response
            val botguardResult = runBotGuard(challengeData)
            if (botguardResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to run BotGuard", botguardResult.exceptionOrNull())
            }

            val botguardResponse = botguardResult.getOrThrow()
            
            // Step 4: Generate integrity token
            val integrityResult = generateIntegrityToken(botguardResponse)
            if (integrityResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to generate integrity token", integrityResult.exceptionOrNull())
            }

            val (integrityToken, expirationTimeInSeconds) = integrityResult.getOrThrow()
            
            // Step 5: Store state in JavaScript
            val stateResult = storeJavaScriptState(botguardResponse, integrityToken)
            if (stateResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to store JavaScript state", stateResult.exceptionOrNull())
            }
            
            // Set expiration with 10 minutes margin
            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds).minus(10, ChronoUnit.MINUTES)
            
            isInitialized = true
            println("DEBUG: PoToken WebView initialized successfully, expires in $expirationTimeInSeconds seconds")
            
        } catch (e: Exception) {
            lastError = e
            println("DEBUG: Failed to initialize PoToken WebView: ${e.message}")
            throw e
        }
    }

    private suspend fun loadHtmlAsset(): Result<String> {
        return try {
            val request = object : WebViewRequest.Evaluate<String> {
                override val initialUrl = NetworkRequest(
                    url = PO_TOKEN_HTML_URL,
                    headers = emptyMap(),
                    method = NetworkRequest.Method.GET,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex("file:///android_asset.*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            if (typeof runBotGuard !== 'undefined' && typeof obtainPoToken !== 'undefined') {
                                return 'READY';
                            } else {
                                return 'ERROR: Functions not loaded';
                            }
                        } catch (e) {
                            return 'ERROR: ' + e.message;
                        }
                    })();
                """
                override val maxTimeout = 10000L // 10 seconds
                
                override suspend fun onStop(url: NetworkRequest, data: String?): String? {
                    return null
                }
            }

            val result = webViewClient.await(true, "Loading HTML asset", request)
            if (result.isFailure) {
                return Result.failure(PoTokenException.GenerationException("Failed to load HTML asset", result.exceptionOrNull()))
            }

            val response = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty HTML response"))
            
            if (response != "READY") {
                return Result.failure(PoTokenException.GenerationException("HTML asset not ready: $response"))
            }
            
            Result.success(response)
            
        } catch (e: Exception) {
            Result.failure(PoTokenException.GenerationException("Failed to load HTML asset", e))
        }
    }

    private suspend fun createBotGuardChallenge(): Result<String> {
        return try {
            val request = object : WebViewRequest.Evaluate<String> {
                override val initialUrl = NetworkRequest(
                    url = "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create",
                    headers = mapOf(
                        "accept" to "*/*",
                        "origin" to "https://www.youtube.com",
                        "content-type" to "application/json+protobuf",
                        "priority" to "u=1, i",
                        "referer" to "https://www.youtube.com/",
                        "sec-ch-ua" to "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
                        "sec-ch-ua-mobile" to "?0",
                        "sec-ch-ua-platform" to "\"macOS\"",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "cross-site",
                        "user-agent" to USER_AGENT,
                        "x-goog-api-key" to GOOGLE_API_KEY,
                        "x-user-agent" to "grpc-web-javascript/0.1"
                    ),
                    method = NetworkRequest.Method.POST,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex(".*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            const response = JSON.parse(document.body.innerText);
                            return JSON.stringify(response);
                        } catch (e) {
                            return 'ERROR: ' + e.message;
                        }
                    })();
                """
                override val maxTimeout = 30000L // 30 seconds
                
                override suspend fun onStop(url: NetworkRequest, data: String?): String? {
                    return null
                }
            }

            val result = webViewClient.await(true, "Creating BotGuard challenge", request)
            if (result.isFailure) {
                return Result.failure(PoTokenException.GenerationException("WebView request failed", result.exceptionOrNull()))
            }

            val responseBody = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty response body"))
            Result.success(responseBody)
            
        } catch (e: Exception) {
            Result.failure(PoTokenException.GenerationException("Failed to create BotGuard challenge", e))
        }
    }

    private suspend fun runBotGuard(challengeData: String): Result<String> {
        return try {
            val parsedChallengeData = parseChallengeData(challengeData)
            
            val request = object : WebViewRequest.Evaluate<String> {
                override val initialUrl = NetworkRequest(
                    url = PO_TOKEN_HTML_URL,
                    headers = emptyMap(),
                    method = NetworkRequest.Method.GET,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex("file:///android_asset.*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            const challengeData = $parsedChallengeData;
                            return runBotGuard(challengeData).then(function(result) {
                                return JSON.stringify(result);
                            }).catch(function(error) {
                                return 'ERROR: ' + error + '\n' + error.stack;
                            });
                        } catch (error) {
                            return 'ERROR: ' + error + '\n' + error.stack;
                        }
                    })();
                """
                override val maxTimeout = 60000L // 60 seconds
                
                override suspend fun onStop(url: NetworkRequest, data: String?): String? {
                    return null
                }
            }

            val result = webViewClient.await(true, "Running BotGuard", request)
            if (result.isFailure) {
                return Result.failure(PoTokenException.GenerationException("BotGuard execution failed", result.exceptionOrNull()))
            }

            val response = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty BotGuard response"))
            
            if (response.startsWith("ERROR:")) {
                return Result.failure(PoTokenException.BadWebViewException(response))
            }
            
            Result.success(response)
            
        } catch (e: Exception) {
            Result.failure(PoTokenException.GenerationException("Failed to run BotGuard", e))
        }
    }

    private suspend fun generateIntegrityToken(botguardResponse: String): Result<Pair<String, Long>> {
        return try {
            // Parse the botguard response to extract the response
            val responseJson = Json.parseToJsonElement(botguardResponse).jsonObject
            val botguardResponseValue = responseJson["botguardResponse"]?.jsonPrimitive?.content
                ?: return Result.failure(PoTokenException.GenerationException("Invalid BotGuard response format"))

            val request = object : WebViewRequest.Evaluate<String> {
                override val initialUrl = NetworkRequest(
                    url = "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT",
                    headers = mapOf(
                        "accept" to "*/*",
                        "origin" to "https://www.youtube.com",
                        "content-type" to "application/json+protobuf",
                        "priority" to "u=1, i",
                        "referer" to "https://www.youtube.com/",
                        "sec-ch-ua" to "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
                        "sec-ch-ua-mobile" to "?0",
                        "sec-ch-ua-platform" to "\"macOS\"",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "cross-site",
                        "user-agent" to USER_AGENT,
                        "x-goog-api-key" to GOOGLE_API_KEY,
                        "x-user-agent" to "grpc-web-javascript/0.1"
                    ),
                    method = NetworkRequest.Method.POST,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex(".*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            const response = JSON.parse(document.body.innerText);
                            return JSON.stringify(response);
                        } catch (e) {
                            return 'ERROR: ' + e.message;
                        }
                    })();
                """
                override val maxTimeout = 30000L // 30 seconds
                
                override suspend fun onStop(url: NetworkRequest, data: String?): String? {
                    return null
                }
            }

            val result = webViewClient.await(true, "Generating integrity token", request)
            if (result.isFailure) {
                return Result.failure(PoTokenException.GenerationException("Integrity token generation failed", result.exceptionOrNull()))
            }

            val responseBody = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty integrity token response"))
            
            val integrityTokenData = parseIntegrityTokenData(responseBody)
            Result.success(integrityTokenData)
            
        } catch (e: Exception) {
            Result.failure(PoTokenException.GenerationException("Failed to generate integrity token", e))
        }
    }

    private suspend fun storeJavaScriptState(botguardResponse: String, integrityToken: String): Result<String> {
        return try {
            // Parse the botguard response to extract webPoSignalOutput
            val responseJson = Json.parseToJsonElement(botguardResponse).jsonObject
            val webPoSignalOutputElement = responseJson["webPoSignalOutput"]
                ?: return Result.failure(PoTokenException.GenerationException("Invalid BotGuard response format - missing webPoSignalOutput"))
            
            val webPoSignalOutputArray = webPoSignalOutputElement.jsonArray

            // Convert webPoSignalOutput array to JavaScript array format
            val webPoSignalOutputJs = webPoSignalOutputArray.joinToString(",", prefix = "[", postfix = "]") { element ->
                element.jsonPrimitive.content
            }
            
            val request = object : WebViewRequest.Evaluate<String> {
                override val initialUrl = NetworkRequest(
                    url = PO_TOKEN_HTML_URL,
                    headers = emptyMap(),
                    method = NetworkRequest.Method.GET,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex("file:///android_asset.*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            const webPoSignalOutput = $webPoSignalOutputJs;
                            const integrityToken = ${Json.encodeToString(integrityToken)};
                            const result = storeState(webPoSignalOutput, integrityToken);
                            return JSON.stringify(result);
                        } catch (error) {
                            return 'ERROR: ' + error + '\n' + error.stack;
                        }
                    })();
                """
                override val maxTimeout = 10000L // 10 seconds
                
                override suspend fun onStop(url: NetworkRequest, data: String?): String? {
                    return null
                }
            }

            val result = webViewClient.await(true, "Storing JavaScript state", request)
            if (result.isFailure) {
                return Result.failure(PoTokenException.GenerationException("Failed to store JavaScript state", result.exceptionOrNull()))
            }

            val response = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty state storage response"))
            
            if (response.startsWith("ERROR:")) {
                return Result.failure(PoTokenException.GenerationException("State storage failed: $response"))
            }
            
            Result.success(response)
            
        } catch (e: Exception) {
            Result.failure(PoTokenException.GenerationException("Failed to store JavaScript state", e))
        }
    }

    suspend fun generatePoToken(identifier: String): Result<String> {
        return try {
            if (!isInitialized) {
                return Result.failure(PoTokenException.GenerationException("PoToken WebView not initialized"))
            }

            if (Instant.now().isAfter(expirationInstant)) {
                return Result.failure(PoTokenException.TimeoutException("PoToken WebView has expired"))
            }

            val request = object : WebViewRequest.Evaluate<String> {
                override val initialUrl = NetworkRequest(
                    url = PO_TOKEN_HTML_URL,
                    headers = emptyMap(),
                    method = NetworkRequest.Method.GET,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex("file:///android_asset.*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            const identifier = "$identifier";
                            const poToken = generatePoTokenWithState(identifier);
                            return poToken;
                        } catch (error) {
                            return 'ERROR: ' + error + '\n' + error.stack;
                        }
                    })();
                """
                override val maxTimeout = 30000L // 30 seconds
                
                override suspend fun onStop(url: NetworkRequest, data: String?): String? {
                    return null
                }
            }

            val result = webViewClient.await(true, "Generating PoToken", request)
            if (result.isFailure) {
                return Result.failure(PoTokenException.GenerationException("PoToken generation failed", result.exceptionOrNull()))
            }

            val poToken = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty PoToken response"))
            
            if (poToken.startsWith("ERROR:")) {
                return Result.failure(PoTokenException.GenerationException("PoToken generation error: $poToken"))
            }
            
            println("DEBUG: Generated PoToken for identifier $identifier: ${poToken.take(20)}...")
            Result.success(poToken)
            
        } catch (e: Exception) {
            Result.failure(PoTokenException.GenerationException("Failed to generate PoToken", e))
        }
    }

    val isExpired: Boolean
        get() = Instant.now().isAfter(expirationInstant)

    fun close() {
        isInitialized = false
        lastError = null
    }
}