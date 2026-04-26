package app.ucon.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

enum class RunStatus { Good, Warn, Bad }

/**
 * Compact rollup of a [RunReport], shaped for the always-visible surfaces
 * (menu bar text on macOS, tray tooltip on Windows, home-screen widgets on
 * Android/iOS). Persisted as `last_run.json` inside the iOS App Group container
 * and re-derived from SQLDelight rows on Android/Desktop.
 */
@Serializable
data class LastRunSummary(
    val ts: Instant,
    val status: RunStatus,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val avgLatencyMs: Double? = null,
    val lossPct: Double? = null,
    val siteLabel: String = "",
) {
    companion object {
        /** Threshold-based status pill — kept here so widget + tray + UI agree. */
        fun statusOf(avgLatencyMs: Double?, lossPct: Double?): RunStatus {
            val loss = lossPct ?: 0.0
            val lat = avgLatencyMs ?: 0.0
            return when {
                loss >= 10.0 || lat >= 500.0 -> RunStatus.Bad
                loss >= 2.0 || lat >= 150.0 -> RunStatus.Warn
                else -> RunStatus.Good
            }
        }
    }
}
