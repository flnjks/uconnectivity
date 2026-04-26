package app.ucon.surface

import app.ucon.api.LastRunSummary
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL

/**
 * iOS bridge: serialises [LastRunSummary] as JSON into the App Group container
 * shared with the WidgetKit extension, and asks WidgetKit to reload its timeline.
 *
 * The widget reads `last_run.json` from the same path on its next refresh.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SurfaceBridge(
    private val appGroupId: String,
    private val reloadTimelines: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    actual fun publishLatest(latest: LastRunSummary?, recent: List<LastRunSummary>) {
        val payload = SurfacePayload(latest = latest, recent = recent)
        val containerUrl = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(appGroupId)
            ?: return
        val fileUrl = containerUrl.URLByAppendingPathComponent("last_run.json") ?: return

        val encoded = json.encodeToString(SurfacePayload.serializer(), payload)
        val nsString = NSString.create(string = encoded)
        val data: NSData = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        @Suppress("UNCHECKED_CAST")
        data.writeToURL(fileUrl as NSURL, atomically = true)

        reloadTimelines()
    }
}

@kotlinx.serialization.Serializable
private data class SurfacePayload(
    val latest: LastRunSummary?,
    val recent: List<LastRunSummary>,
)
