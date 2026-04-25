package app.ucon.measure

import app.ucon.api.Api
import app.ucon.api.SpeedReport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Where speed-test traffic should be directed.
 *
 *  - [IngestServer] uses the user's own Ktor server's `/v1/speedtest/...` endpoints.
 *    Requires the bearer token; counted against site auth.
 *  - [Cloudflare] hits the public `speed.cloudflare.com` endpoints used by
 *    speed.cloudflare.com. No auth, free, works without standing up infra,
 *    but adds a network dependency outside the user's control.
 */
sealed interface SpeedEndpoint {
    /** Build the download URL for [bytes] of test data. */
    fun downloadUrl(bytes: Long): String
    /** Build the upload URL. */
    fun uploadUrl(): String
    /** Bearer token to send (or null). */
    fun bearer(): String?

    data class IngestServer(val baseUrl: String, val token: String) : SpeedEndpoint {
        override fun downloadUrl(bytes: Long): String =
            baseUrl.trimEnd('/') + Api.PATH_SPEED_DOWNLOAD + "?bytes=$bytes"
        override fun uploadUrl(): String = baseUrl.trimEnd('/') + Api.PATH_SPEED_UPLOAD
        override fun bearer(): String = token
    }

    data object Cloudflare : SpeedEndpoint {
        // https://speed.cloudflare.com/ uses these endpoints under the hood.
        override fun downloadUrl(bytes: Long): String =
            "https://speed.cloudflare.com/__down?bytes=$bytes"
        override fun uploadUrl(): String = "https://speed.cloudflare.com/__up"
        override fun bearer(): String? = null
    }
}

class SpeedProbe(private val client: HttpClient) {
    /**
     * Measures download + upload speed against [endpoint]. Returns null fields
     * if a leg fails; never throws.
     */
    suspend fun measure(
        endpoint: SpeedEndpoint,
        testSizeBytes: Long = 5L * 1024 * 1024,
        timeoutMs: Long = 15_000L,
    ): SpeedReport {
        val clamped = testSizeBytes.coerceIn(1, Api.MAX_SPEED_BYTES)
        return SpeedReport(
            downMbps = runCatching { measureDownload(endpoint, clamped, timeoutMs) }.getOrNull(),
            upMbps = runCatching { measureUpload(endpoint, clamped, timeoutMs) }.getOrNull(),
            testSizeBytes = clamped,
        )
    }

    private suspend fun measureDownload(endpoint: SpeedEndpoint, bytes: Long, timeoutMs: Long): Double {
        val mark = TimeSource.Monotonic.markNow()
        val body: ByteArray = client.get(endpoint.downloadUrl(bytes)) {
            timeout { requestTimeoutMillis = timeoutMs }
            endpoint.bearer()?.let { tok ->
                headers { append(HttpHeaders.Authorization, "Bearer $tok") }
            }
        }.body()
        val total = body.size.toLong()
        val seconds = mark.elapsedNow().inWholeMicroseconds / 1_000_000.0
        return (total * 8.0) / 1_000_000.0 / seconds.coerceAtLeast(0.001)
    }

    private suspend fun measureUpload(endpoint: SpeedEndpoint, bytes: Long, timeoutMs: Long): Double {
        val payload = ByteArray(bytes.toInt()).also { Random.Default.nextBytes(it) }
        val mark = TimeSource.Monotonic.markNow()
        // We measure client-side wall time (request start → response received) so the
        // measurement works whether the peer returns a structured ack or just 200.
        client.post(endpoint.uploadUrl()) {
            timeout { requestTimeoutMillis = timeoutMs }
            endpoint.bearer()?.let { tok ->
                headers { append(HttpHeaders.Authorization, "Bearer $tok") }
            }
            contentType(ContentType.Application.OctetStream)
            setBody(payload)
        }
        val seconds = mark.elapsedNow().inWholeMicroseconds / 1_000_000.0
        return (bytes * 8.0) / 1_000_000.0 / seconds.coerceAtLeast(0.001)
    }
}
