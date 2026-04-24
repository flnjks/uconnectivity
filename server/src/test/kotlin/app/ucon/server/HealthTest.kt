package app.ucon.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun healthz_returns_ok() = testApplication {
        val tmp = Files.createTempFile("ucon-test-", ".sqlite").toAbsolutePath().toString()
        Db.init(tmp)
        application { module() }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }
}
