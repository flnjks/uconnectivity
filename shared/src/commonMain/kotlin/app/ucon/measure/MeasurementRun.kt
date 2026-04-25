package app.ucon.measure

import app.ucon.api.RunReport
import app.ucon.api.SpeedReport
import app.ucon.api.TargetReport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlin.time.TimeSource

data class RunConfig(
    val siteId: String,
    val serverBaseUrl: String,
    val token: String,
    val clientVersion: String,
    val targets: List<Target> = Target.DEFAULTS,
    val samplesPerTarget: Int = 10,
    val speedTestEnabled: Boolean = true,
    val speedTestBytes: Long = 5L * 1024 * 1024,
)

class MeasurementRun(
    private val probes: Probes,
    private val speedProbe: SpeedProbe,
) {
    /**
     * Runs reachability + latency probes for every target in parallel, then (optionally) a
     * sequential up/down speed test. Returns a [RunReport] ready to persist and upload.
     */
    suspend fun runOnce(config: RunConfig): RunReport = coroutineScope {
        val startedAt = Clock.System.now()
        val mark = TimeSource.Monotonic.markNow()

        val perTarget = config.targets.map { target ->
            async {
                val m = probes.measureTarget(target, samples = config.samplesPerTarget)
                TargetReport(
                    label = target.label,
                    host = target.host,
                    port = target.port,
                    reachable = m.reachable,
                    latencyMs = m.latency,
                    lossPct = m.lossPct,
                )
            }
        }.awaitAll()

        val speed: SpeedReport? = if (config.speedTestEnabled) {
            // Use the configured ingest server when available; fall back to the public
            // Cloudflare endpoints so speeds still measure before any server is provisioned.
            val endpoint = if (config.serverBaseUrl.isNotBlank() && config.token.isNotBlank()) {
                SpeedEndpoint.IngestServer(config.serverBaseUrl, config.token)
            } else {
                SpeedEndpoint.Cloudflare
            }
            runCatching { speedProbe.measure(endpoint, config.speedTestBytes) }.getOrNull()
        } else null

        val durationMs = mark.elapsedNow().inWholeMilliseconds

        RunReport(
            siteId = config.siteId,
            startedAt = startedAt,
            durationMs = durationMs,
            targets = perTarget,
            speed = speed,
            clientVersion = config.clientVersion,
        )
    }
}
