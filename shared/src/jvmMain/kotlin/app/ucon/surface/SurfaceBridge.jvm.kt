package app.ucon.surface

import app.ucon.api.LastRunSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop bridge holds an in-process StateFlow that both the Compose Windows
 * tray and the macOS Swift-helper writer observe. There's no IPC boundary
 * inside the JVM process — platform-specific writers (Windows tray composable,
 * macOS helper-pipe driver) collect from these flows and react.
 */
actual class SurfaceBridge {
    private val _latest = MutableStateFlow<LastRunSummary?>(null)
    private val _recent = MutableStateFlow<List<LastRunSummary>>(emptyList())

    val latest: StateFlow<LastRunSummary?> = _latest.asStateFlow()
    val recent: StateFlow<List<LastRunSummary>> = _recent.asStateFlow()

    actual fun publishLatest(latest: LastRunSummary?, recent: List<LastRunSummary>) {
        _latest.value = latest
        _recent.value = recent
    }
}
