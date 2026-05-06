package app.ucon.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val OPS = "ops_test_token_42"

class AdminEndpointsTest {

    @Test
    fun create_then_rotate_then_delete_round_trip() = testApplication {
        bootServer()
        val client = jsonClient()

        // Create
        val createResp = client.post("/v1/admin/sites") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
            contentType(ContentType.Application.Json)
            setBody(CreateSiteRequest(label = "office-7"))
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val created = createResp.body<CreateSiteResponse>()
        assertTrue(created.siteId.startsWith("office-7-"))
        assertTrue(created.token.startsWith("ucon_"))

        // List should include it
        val listResp = client.get("/v1/admin/sites") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
        }
        assertEquals(HttpStatusCode.OK, listResp.status)
        val list = listResp.body<SiteListResponse>()
        assertTrue(list.sites.any { it.siteId == created.siteId && it.label == "office-7" })

        // Old token authenticates an ingest; new token (after rotate) doesn't equal old.
        val rotateResp = client.post("/v1/admin/sites/${created.siteId}/rotate") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
        }
        assertEquals(HttpStatusCode.OK, rotateResp.status)
        val rotated = rotateResp.body<RotateTokenResponse>()
        assertEquals(created.siteId, rotated.siteId)
        assertNotEquals(created.token, rotated.token)

        // Delete returns 204; second delete returns 404.
        val firstDelete = client.delete("/v1/admin/sites/${created.siteId}") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
        }
        assertEquals(HttpStatusCode.NoContent, firstDelete.status)
        val secondDelete = client.delete("/v1/admin/sites/${created.siteId}") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
        }
        assertEquals(HttpStatusCode.NotFound, secondDelete.status)
    }

    @Test
    fun ops_routes_reject_unauthenticated_calls() = testApplication {
        bootServer()
        val client = jsonClient()

        val noAuth = client.post("/v1/admin/sites") {
            contentType(ContentType.Application.Json)
            setBody(CreateSiteRequest(label = "x"))
        }
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)

        val wrongAuth = client.post("/v1/admin/sites") {
            header(HttpHeaders.Authorization, "Bearer not-the-real-ops-token")
            contentType(ContentType.Application.Json)
            setBody(CreateSiteRequest(label = "x"))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongAuth.status)
    }

    @Test
    fun rotated_token_replaces_the_old_one_for_ingest_auth() = testApplication {
        bootServer()
        val client = jsonClient()

        // Create site
        val created = client.post("/v1/admin/sites") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
            contentType(ContentType.Application.Json)
            setBody(CreateSiteRequest(label = "office-8"))
        }.body<CreateSiteResponse>()

        // Old token authenticates ingest
        val oldOk = client.get("/v1/sites/${created.siteId}/stats") {
            header(HttpHeaders.Authorization, "Bearer ${created.token}")
        }
        assertEquals(HttpStatusCode.OK, oldOk.status)

        // Rotate
        val rotated = client.post("/v1/admin/sites/${created.siteId}/rotate") {
            header(HttpHeaders.Authorization, "Bearer $OPS")
        }.body<RotateTokenResponse>()

        // Old token no longer authenticates
        val oldDenied = client.get("/v1/sites/${created.siteId}/stats") {
            header(HttpHeaders.Authorization, "Bearer ${created.token}")
        }
        assertEquals(HttpStatusCode.Unauthorized, oldDenied.status)

        // New token does
        val newOk = client.get("/v1/sites/${created.siteId}/stats") {
            header(HttpHeaders.Authorization, "Bearer ${rotated.token}")
        }
        assertEquals(HttpStatusCode.OK, newOk.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.bootServer() {
        val tmp = Files.createTempFile("ucon-admin-", ".sqlite").toAbsolutePath().toString()
        Db.init(tmp)
        environment {
            config = MapApplicationConfig("ucon.opsToken" to OPS)
        }
        application { module() }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}
