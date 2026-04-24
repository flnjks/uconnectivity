package app.ucon.server

import app.ucon.api.Api
import app.ucon.api.IngestAck
import app.ucon.api.LatencyStats
import app.ucon.api.RunReport
import app.ucon.api.SiteStatsPage
import app.ucon.api.SpeedReport
import app.ucon.api.TargetReport
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IngestRoundTripTest {
    @Test
    fun post_stats_then_read_them_back() = testApplication {
        val tmp = Files.createTempFile("ucon-rt-", ".sqlite").toAbsolutePath().toString()
        Db.init(tmp)
        application { module() }

        // Provision a site via the admin path (direct call; admin CLI isn't part of the HTTP surface).
        val (siteId, token) = Admin.createSite("round-trip-test")

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val report = RunReport(
            siteId = siteId,
            startedAt = Clock.System.now(),
            durationMs = 1234,
            targets = listOf(
                TargetReport(
                    label = "Cloudflare",
                    host = "1.1.1.1",
                    port = 443,
                    reachable = true,
                    latencyMs = LatencyStats(min = 8.0, avg = 12.0, max = 20.0, jitter = 2.0, samples = 10),
                    lossPct = 0.0,
                ),
            ),
            speed = SpeedReport(downMbps = 123.4, upMbps = 45.6, testSizeBytes = 1_000_000),
            clientVersion = "test",
        )

        // POST /v1/stats
        val postResp = client.post(Api.PATH_STATS) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(report)
        }
        assertEquals(HttpStatusCode.Accepted, postResp.status)
        val ack: IngestAck = postResp.body()
        assertTrue(ack.accepted)
        assertNotNull(ack.runId)

        // GET /v1/sites/{siteId}/stats
        val getResp = client.get(Api.sitePath(siteId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getResp.status)
        val page: SiteStatsPage = getResp.body()
        assertEquals(siteId, page.siteId)
        assertEquals(1, page.runs.size)
        val echoed = page.runs.first()
        assertEquals(report.durationMs, echoed.durationMs)
        assertEquals(report.speed?.downMbps, echoed.speed?.downMbps)
        assertEquals(report.targets.first().latencyMs?.avg, echoed.targets.first().latencyMs?.avg)
    }

    @Test
    fun rejects_without_token() = testApplication {
        val tmp = Files.createTempFile("ucon-rt2-", ".sqlite").toAbsolutePath().toString()
        Db.init(tmp)
        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post(Api.PATH_STATS) {
            contentType(ContentType.Application.Json)
            setBody("""{"siteId":"nope","startedAt":"2025-01-01T00:00:00Z","durationMs":0,"targets":[],"clientVersion":"t"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
