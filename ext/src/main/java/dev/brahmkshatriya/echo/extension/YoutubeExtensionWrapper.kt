package dev.brahmkshatriya.echo.extension

import android.content.Context
import android.content.SharedPreferences
import dev.brahmkshatriya.echo.common.clients.ExtensionType
import dev.brahmkshatriya.echo.common.clients.Injectable
import dev.brahmkshatriya.echo.common.helpers.ExtensionPackageHelper
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Metadata
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import io.ktor.client.plugins.cookies.CookieJar
import kotlinx.serialization.Serializable

@Serializable
class YoutubeExtensionWrapper : MusicExtension {
    
    override val metadata: Metadata = Metadata(
        name = YoutubeExtension.EXTENSION_NAME,
        description = "A YouTube Music extension for Echo",
        packageName = YoutubeExtension.PACKAGE_NAME,
        version = "1.0.0",
        author = "brahmkshatriya",
        icon = ImageHolder.Resource("ic_launcher_foreground")
    )
    
    override val extensionType: ExtensionType = ExtensionType.MUSIC
    
    override fun createInstance(
        context: Context,
        packageHelper: ExtensionPackageHelper,
        preferences: SharedPreferences
    ): Injectable {
        val api = YoutubeiApi(data_language = YoutubeExtension.ENGLISH)
        return YoutubeExtension(
            context,
            packageHelper,
            preferences,
            api,
            LOW_QUALITY, // Default quality
            YoutubeExtension.ENGLISH,
            null
        )
    }
    
    companion object {
        private const val LOW_QUALITY = 0
    }
}