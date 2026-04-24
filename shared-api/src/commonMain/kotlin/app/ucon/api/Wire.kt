package app.ucon.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RunReport(
    val siteId: String,
    val startedAt: Instant,
    val durationMs: Long,
    val targets: List<TargetReport>,
    val speed: SpeedReport? = null,
    val clientVersion: String,
)

@Serializable
data class TargetReport(
    val label: String,
    val host: String,
    val port: Int,
    val reachable: Boolean,
    val latencyMs: LatencyStats? = null,
    val lossPct: Double,
)

@Serializable
data class LatencyStats(
    val min: Double,
    val avg: Double,
    val max: Double,
    val jitter: Double,
    val samples: Int,
)

@Serializable
data class SpeedReport(
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val testSizeBytes: Long,
)

@Serializable
data class IngestAck(
    val accepted: Boolean,
    val runId: String? = null,
    val error: String? = null,
)

@Serializable
data class SpeedUploadAck(
    val bytesReceived: Long,
    val elapsedMs: Long,
)

@Serializable
data class SiteStatsPage(
    val siteId: String,
    val runs: List<RunReport>,
)

object Api {
    const val PATH_STATS: String = "/v1/stats"
    const val PATH_SPEED_DOWNLOAD: String = "/v1/speedtest/download"
    const val PATH_SPEED_UPLOAD: String = "/v1/speedtest/upload"
    const val PATH_HEALTHZ: String = "/healthz"
    const val MAX_SPEED_BYTES: Long = 25L * 1024 * 1024

    fun sitePath(siteId: String): String = "/v1/sites/$siteId/stats"
}
