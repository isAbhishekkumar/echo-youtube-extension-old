package dev.brahmkshatriya.echo.common.clients

/**
 * Interface for classes that can be initialized and destroyed by the Echo application.
 * Extensions implementing this interface will have their lifecycle managed by the application.
 */
interface Injectable {
    /**
     * Called when the extension is initialized by the application.
     * Use this method for setup code that should run when the extension is first loaded.
     */
    fun onInitialize()
    
    /**
     * Called when the extension is being destroyed by the application.
     * Use this method for cleanup code that should run when the extension is unloaded.
     */
    fun onDestroy()
}