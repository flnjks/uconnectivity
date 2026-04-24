package app.ucon.data

import app.ucon.api.Api
import app.ucon.api.IngestAck
import app.ucon.api.RunReport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class UploadQueue(
    private val repo: RunRepository,
    private val httpClient: HttpClient,
    private val serverBaseUrlProvider: () -> String?,
    private val tokenProvider: () -> String?,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = Clock.System.now().toEpochMilliseconds()
                val row = repo.nextPending(now)
                if (row == null) {
                    delay(5_000)
                    continue
                }
                val base = serverBaseUrlProvider()
                val token = tokenProvider()
                if (base.isNullOrBlank() || token.isNullOrBlank()) {
                    // Not yet provisioned; wait and retry.
                    delay(10_000)
                    continue
                }
                val report = repo.parseReport(row)
                val ok = tryUpload(base, token, report)
                if (ok) {
                    repo.markSent(row.id)
                } else {
                    val next = now + backoffMs((row.attempts + 1).toInt())
                    repo.markFailedAttempt(row.id, next)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun tryUpload(baseUrl: String, token: String, report: RunReport): Boolean {
        return try {
            val resp: HttpResponse = httpClient.post(baseUrl.trimEnd('/') + Api.PATH_STATS) {
                contentType(ContentType.Application.Json)
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                setBody(report)
            }
            if (!resp.status.isSuccess()) return false
            runCatching { resp.body<IngestAck>().accepted }.getOrDefault(true)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val STEPS_MS = longArrayOf(1_000, 5_000, 30_000, 300_000, 1_800_000)
        fun backoffMs(attempts: Int): Long =
            STEPS_MS[(attempts - 1).coerceIn(0, STEPS_MS.lastIndex)]
    }
}
