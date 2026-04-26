package app.ucon.surface

import android.content.Context
import app.ucon.api.LastRunSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android bridge: triggers a Glance widget redraw via [glanceUpdater].
 * The composeApp module wires [glanceUpdater] to `UconGlanceWidget().updateAll(context)`
 * so this module doesn't depend on Glance directly.
 */
actual class SurfaceBridge(
    private val context: Context,
    private val glanceUpdater: suspend (Context) -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    actual fun publishLatest(latest: LastRunSummary?, recent: List<LastRunSummary>) {
        scope.launch {
            runCatching { glanceUpdater(context) }
        }
    }
}
