package app.ucon.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val OPS = "ops_dashboard_test_token"

class OpsDashboardTest {

    @Test
    fun dashboard_html_renders_when_authorized_via_query() = testApplication {
        bootServer()
        val resp = client.get("/ops?t=$OPS")
        assertEquals(HttpStatusCode.OK, resp.status)
        val html = resp.bodyAsText()
        assertContains(html, "uConnectivity fleet")
        assertContains(html, "/ops/data.json")
    }

    @Test
    fun data_json_returns_provisioned_sites() = testApplication {
        bootServer()
        // Provision one site so the snapshot has content.
        Admin.createSite("office-9")

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.get("/ops/data.json") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val snapshot: FleetSnapshotResponse = resp.body()
        assertTrue(snapshot.sites.isNotEmpty())
        val office9 = snapshot.sites.firstOrNull { it.label == "office-9" }
        assertNotNull(office9)
        assertEquals("None", office9.status.let { if (office9.lastRunAt == null) "None" else it })
    }

    @Test
    fun rejects_missing_or_wrong_token() = testApplication {
        bootServer()
        val noAuth = client.get("/ops")
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)

        val wrong = client.get("/ops?t=nope")
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.bootServer() {
        val tmp = Files.createTempFile("ucon-ops-", ".sqlite").toAbsolutePath().toString()
        Db.init(tmp)
        environment {
            config = MapApplicationConfig("ucon.opsToken" to OPS)
        }
        application { module() }
    }
}
