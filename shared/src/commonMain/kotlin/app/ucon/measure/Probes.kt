package app.ucon.measure

import app.ucon.api.LatencyStats
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class SingleProbeResult(val succeeded: Boolean, val elapsedMs: Double?)

data class TargetMeasurement(
    val target: Target,
    val reachable: Boolean,
    val latency: LatencyStats?,
    val lossPct: Double,
)

private const val DEFAULT_TIMEOUT_MS: Long = 3_000L

class Probes(private val client: HttpClient) {
    /**
     * Performs one probe — an HTTPS HEAD request — against [target], returning success + wall-clock ms.
     * We use HEAD over TLS rather than raw TCP connect so we work on every platform (iOS, Android, JVM)
     * and over HTTPS-only networks. True ICMP needs entitlements we don't assume.
     */
    suspend fun singleProbe(target: Target, timeoutMs: Long = DEFAULT_TIMEOUT_MS): SingleProbeResult {
        val mark = TimeSource.Monotonic.markNow()
        val url = URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            host = target.host
            port = target.port
        }.buildString()
        return try {
            val resp: HttpResponse? = withTimeoutOrNull(timeoutMs.milliseconds) {
                client.head(url) {
                    timeout { requestTimeoutMillis = timeoutMs }
                }
            }
            if (resp == null) {
                SingleProbeResult(succeeded = false, elapsedMs = null)
            } else {
                SingleProbeResult(succeeded = true, elapsedMs = mark.elapsedNow().inWholeMicroseconds / 1000.0)
            }
        } catch (_: TimeoutCancellationException) {
            SingleProbeResult(false, null)
        } catch (_: Exception) {
            SingleProbeResult(false, null)
        }
    }

    /**
     * Probes [target] [samples] times sequentially; computes latency stats from successes and
     * a loss percentage from the miss ratio.
     */
    suspend fun measureTarget(target: Target, samples: Int = 10, timeoutMs: Long = DEFAULT_TIMEOUT_MS): TargetMeasurement {
        require(samples > 0)
        val latencies = mutableListOf<Double>()
        var failures = 0
        repeat(samples) {
            val r = singleProbe(target, timeoutMs)
            if (r.succeeded && r.elapsedMs != null) latencies += r.elapsedMs else failures++
        }
        val lossPct = 100.0 * failures / samples
        val stats = if (latencies.isNotEmpty()) latencies.toLatencyStats() else null
        return TargetMeasurement(
            target = target,
            reachable = latencies.isNotEmpty(),
            latency = stats,
            lossPct = lossPct,
        )
    }
}

internal fun List<Double>.toLatencyStats(): LatencyStats {
    require(isNotEmpty())
    var mn = first()
    var mx = first()
    var sum = 0.0
    for (v in this) {
        mn = min(mn, v)
        mx = max(mx, v)
        sum += v
    }
    val avg = sum / size
    // Mean absolute deviation = jitter (pragmatic simplification).
    val jitter = if (size == 1) 0.0 else sumOf { abs(it - avg) } / size
    return LatencyStats(min = mn, avg = avg, max = mx, jitter = jitter, samples = size)
}
