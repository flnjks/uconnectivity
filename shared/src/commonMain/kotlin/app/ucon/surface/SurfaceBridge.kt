package app.ucon.surface

import app.ucon.api.LastRunSummary

/**
 * Pushes the latest run summary out to platform-specific always-visible surfaces:
 * the macOS menu bar item, the Windows tray, and the Android/iOS home-screen widgets.
 *
 * Implementations must be safe to call from any thread and should never throw.
 * The data flow is: AppViewModel.runNow() persists a row → calls publishLatest →
 * the platform surface re-renders.
 */
expect class SurfaceBridge {
    fun publishLatest(latest: LastRunSummary?, recent: List<LastRunSummary>)
}
