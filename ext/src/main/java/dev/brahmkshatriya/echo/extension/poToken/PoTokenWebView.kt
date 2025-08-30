package dev.brahmkshatriya.echo.extension.poToken

import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit

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
        private const val PO_TOKEN_HTML = """
            <!DOCTYPE html>
            <html lang="en"><head><title></title><script>
                /**
                 * Factory method to create and load a BotGuardClient instance.
                 * @param options - Configuration options for the BotGuardClient.
                 * @returns A promise that resolves to a loaded BotGuardClient instance.
                 */
                function loadBotGuard(challengeData) {
                  this.vm = this[challengeData.globalName];
                  this.program = challengeData.program;
                  this.vmFunctions = {};
                  this.syncSnapshotFunction = null;

                  if (!this.vm)
                    throw new Error('[BotGuardClient]: VM not found in the global object');

                  if (!this.vm.a)
                    throw new Error('[BotGuardClient]: Could not load program');

                  const vmFunctionsCallback = function (
                    asyncSnapshotFunction,
                    shutdownFunction,
                    passEventFunction,
                    checkCameraFunction
                  ) {
                    this.vmFunctions = {
                      asyncSnapshotFunction: asyncSnapshotFunction,
                      shutdownFunction: shutdownFunction,
                      passEventFunction: passEventFunction,
                      checkCameraFunction: checkCameraFunction
                    };
                  };

                  this.syncSnapshotFunction = this.vm.a(this.program, vmFunctionsCallback, true, this.userInteractionElement, function () {/** no-op */ }, [ [], [] ])[0]

                  // an asynchronous function runs in the background and it will eventually call
                  // `vmFunctionsCallback`, however we need to manually tell JavaScript to pass
                  // control to the things running in the background by interrupting this async
                  // function in any way, e.g. with a delay of 1ms. The loop is most probably not
                  // needed but is there just because.
                  return new Promise(function (resolve, reject) {
                    i = 0
                    refreshIntervalId = setInterval(function () {
                      if (!!this.vmFunctions.asyncSnapshotFunction) {
                        resolve(this)
                        clearInterval(refreshIntervalId);
                      }
                      if (i >= 10000) {
                        reject("asyncSnapshotFunction is null even after 10 seconds")
                        clearInterval(refreshIntervalId);
                      }
                      i += 1;
                    }, 1);
                  })
                }

                /**
                 * Takes a snapshot asynchronously.
                 * @returns The snapshot result.
                 */
                function snapshot(args) {
                  return new Promise(function (resolve, reject) {
                    if (!this.vmFunctions.asyncSnapshotFunction)
                      return reject(new Error('[BotGuardClient]: Async snapshot function not found'));

                    this.vmFunctions.asyncSnapshotFunction(function (response) { resolve(response) }, [
                      args.contentBinding,
                      args.signedTimestamp,
                      args.webPoSignalOutput,
                      args.skipPrivacyBuffer
                    ]);
                  });
                }

                function runBotGuard(challengeData) {
                  const interpreterJavascript = challengeData.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;

                  if (interpreterJavascript) {
                    new Function(interpreterJavascript)();
                  } else throw new Error('Could not load VM');

                  const webPoSignalOutput = [];
                  return loadBotGuard({
                    globalName: challengeData.globalName,
                    globalObj: this,
                    program: challengeData.program
                  }).then(function (botguard) {
                    return botguard.snapshot({ webPoSignalOutput: webPoSignalOutput })
                  }).then(function (botguardResponse) {
                    return { webPoSignalOutput: webPoSignalOutput, botguardResponse: botguardResponse }
                  })
                }

                function obtainPoToken(webPoSignalOutput, integrityToken, identifier) {
                  const getMinter = webPoSignalOutput[0];

                  if (!getMinter)
                    throw new Error('PMD:Undefined');

                  const mintCallback = getMinter(integrityToken);

                  if (!(mintCallback instanceof Function))
                    throw new Error('APF:Failed');

                  const result = mintCallback(identifier);

                  if (!result)
                    throw new Error('YNJ:Undefined');

                  if (!(result instanceof Uint8Array))
                    throw new Error('ODM:Invalid');

                  return result;
                }
            </script></head><body></body></html>
        """

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
            // Step 1: Load HTML and get BotGuard challenge
            val challengeResult = createBotGuardChallenge()
            if (challengeResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to create BotGuard challenge", challengeResult.exceptionOrNull())
            }

            val challengeData = challengeResult.getOrThrow()
            
            // Step 2: Run BotGuard and get response
            val botguardResult = runBotGuard(challengeData)
            if (botguardResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to run BotGuard", botguardResult.exceptionOrNull())
            }

            val botguardResponse = botguardResult.getOrThrow()
            
            // Step 3: Generate integrity token
            val integrityResult = generateIntegrityToken(botguardResponse)
            if (integrityResult.isFailure) {
                throw PoTokenException.GenerationException("Failed to generate integrity token", integrityResult.exceptionOrNull())
            }

            val (integrityToken, expirationTimeInSeconds) = integrityResult.getOrThrow()
            
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
                    url = "data:text/html;charset=utf-8,${PO_TOKEN_HTML.encodeToByteArray().toString()}",
                    headers = emptyMap(),
                    method = NetworkRequest.Method.GET,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex("data:.*")
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
                    url = "data:text/html;charset=utf-8,${PO_TOKEN_HTML.encodeToByteArray().toString()}",
                    headers = emptyMap(),
                    method = NetworkRequest.Method.GET,
                    bodyBase64 = null
                )
                override val stopUrlRegex = Regex("data:.*")
                override val javascriptToEvaluate = """
                    (function() {
                        try {
                            identifier = "$identifier";
                            u8Identifier = ${stringToU8(identifier)};
                            poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier);
                            poTokenU8String = poTokenU8.join(",");
                            return poTokenU8String;
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

            val poTokenU8 = result.getOrThrow() ?: return Result.failure(PoTokenException.GenerationException("Empty PoToken response"))
            
            if (poTokenU8.startsWith("ERROR:")) {
                return Result.failure(PoTokenException.GenerationException("PoToken generation error: $poTokenU8"))
            }
            
            val poToken = u8ToBase64(poTokenU8)
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