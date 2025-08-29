package dev.brahmkshatriya.echo.common.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.io.File

/**
 * Helper class for extension package operations.
 */
class ExtensionPackageHelper(private val context: Context) {
    
    /**
     * Gets a drawable resource from the extension package.
     *
     * @param resourceName The name of the resource to retrieve
     * @return The drawable resource or null if not found
     */
    fun getDrawable(resourceName: String): Drawable? {
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            if (resourceId != 0) context.getDrawable(resourceId) else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the cache directory for the extension.
     *
     * @return The cache directory for the extension
     */
    fun getCacheDir(): File {
        return context.cacheDir
    }
    
    /**
     * Gets the version name of the extension package.
     *
     * @return The version name string
     */
    fun getVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }
    
    /**
     * Gets the version code of the extension package.
     *
     * @return The version code as a long
     */
    fun getVersionCode(): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }
}