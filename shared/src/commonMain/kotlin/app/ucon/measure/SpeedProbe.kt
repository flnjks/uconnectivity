package app.ucon.measure

import app.ucon.api.Api
import app.ucon.api.SpeedReport
import app.ucon.api.SpeedUploadAck
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

class SpeedProbe(private val client: HttpClient) {
    /**
     * Measures download + upload speed against [baseUrl] using size-capped transfers.
     *
     * Returns null fields if the respective test fails; never throws.
     */
    suspend fun measure(
        baseUrl: String,
        token: String,
        testSizeBytes: Long = 5L * 1024 * 1024,
        timeoutMs: Long = 15_000L,
    ): SpeedReport {
        val clamped = testSizeBytes.coerceIn(1, Api.MAX_SPEED_BYTES)
        return SpeedReport(
            downMbps = runCatching { measureDownload(baseUrl, token, clamped, timeoutMs) }.getOrNull(),
            upMbps = runCatching { measureUpload(baseUrl, token, clamped, timeoutMs) }.getOrNull(),
            testSizeBytes = clamped,
        )
    }

    private suspend fun measureDownload(baseUrl: String, token: String, bytes: Long, timeoutMs: Long): Double {
        val url = baseUrl.trimEnd('/') + Api.PATH_SPEED_DOWNLOAD + "?bytes=$bytes"
        val mark = TimeSource.Monotonic.markNow()
        val body: ByteArray = client.get(url) {
            timeout { requestTimeoutMillis = timeoutMs }
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
        }.body()
        val total = body.size.toLong()
        val seconds = mark.elapsedNow().inWholeMicroseconds / 1_000_000.0
        return (total * 8.0) / 1_000_000.0 / seconds.coerceAtLeast(0.001)
    }

    private suspend fun measureUpload(baseUrl: String, token: String, bytes: Long, timeoutMs: Long): Double {
        val url = baseUrl.trimEnd('/') + Api.PATH_SPEED_UPLOAD
        val payload = ByteArray(bytes.toInt()).also { Random.Default.nextBytes(it) }
        val mark = TimeSource.Monotonic.markNow()
        val ack: SpeedUploadAck = client.post(url) {
            timeout { requestTimeoutMillis = timeoutMs }
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
            contentType(ContentType.Application.OctetStream)
            setBody(payload)
        }.body()
        val seconds = mark.elapsedNow().inWholeMicroseconds / 1_000_000.0
        return (ack.bytesReceived * 8.0) / 1_000_000.0 / seconds.coerceAtLeast(0.001)
    }
}
