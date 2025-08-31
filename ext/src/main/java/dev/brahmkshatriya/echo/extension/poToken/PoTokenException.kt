package dev.brahmkshatriya.echo.extension.poToken

open class PoTokenException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class BadWebViewException(message: String) : PoTokenException(message)
    class GenerationException(message: String, cause: Throwable? = null) : PoTokenException(message, cause)
    class TimeoutException(message: String) : PoTokenException(message)
    class ExpiredException(message: String) : PoTokenException(message)
}