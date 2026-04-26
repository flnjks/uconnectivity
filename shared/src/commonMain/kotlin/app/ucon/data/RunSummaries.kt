package app.ucon.data

import app.ucon.api.LastRunSummary
import app.ucon.api.RunStatus
import kotlinx.datetime.Instant

fun Run.toLastRunSummary(siteLabel: String = ""): LastRunSummary = LastRunSummary(
    ts = Instant.fromEpochMilliseconds(started_at),
    status = LastRunSummary.statusOf(avg_latency_ms, loss_pct),
    downMbps = down_mbps,
    upMbps = up_mbps,
    avgLatencyMs = avg_latency_ms,
    lossPct = loss_pct,
    siteLabel = siteLabel,
)
