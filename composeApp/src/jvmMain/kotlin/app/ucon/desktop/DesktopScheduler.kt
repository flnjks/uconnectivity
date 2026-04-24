package app.ucon.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simple in-process periodic trigger. On desktop we keep the app running
 * (tray app, auto-start at login), so a coroutine timer is sufficient.
 *
 * Real service-level background execution on macOS/Windows would use
 * launchd/Windows Service — documented as a follow-up in the plan.
 */
class DesktopScheduler(
    private val intervalProvider: () -> Int,
    private val action: () -> Unit,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val minutes = intervalProvider().coerceAtLeast(1)
                try {
                    action()
                } catch (_: Throwable) {
                    // Swallow — we want the loop to keep ticking even if a run fails.
                }
                delay(minutes * 60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
