package app.ucon.server

import app.ucon.api.Api
import app.ucon.api.IngestAck
import app.ucon.api.RunReport
import app.ucon.api.SiteStatsPage
import app.ucon.api.SpeedUploadAck
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.random.Random

private fun constantTimeEquals(a: String, b: String): Boolean {
    val ab = a.toByteArray()
    val bb = b.toByteArray()
    if (ab.size != bb.size) return false
    var diff = 0
    for (i in ab.indices) diff = diff or (ab[i].toInt() xor bb[i].toInt())
    return diff == 0
}

data class SitePrincipal(val siteId: String)
data class OpsPrincipal(val name: String)

@kotlinx.serialization.Serializable
data class CreateSiteRequest(val label: String)

@kotlinx.serialization.Serializable
data class CreateSiteResponse(val siteId: String, val token: String)

@kotlinx.serialization.Serializable
data class RotateTokenResponse(val siteId: String, val token: String)

@kotlinx.serialization.Serializable
data class SiteListEntry(val siteId: String, val label: String, val createdAt: Long)

@kotlinx.serialization.Serializable
data class SiteListResponse(val sites: List<SiteListEntry>)

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("unhandled", cause)
            call.respond(HttpStatusCode.InternalServerError, IngestAck(accepted = false, error = cause.message))
        }
    }
    val opsToken: String? = environment.config.propertyOrNull("ucon.opsToken")?.getString()
        ?: System.getenv("OPS_TOKEN")?.takeIf { it.isNotBlank() }

    install(Authentication) {
        bearer("site") {
            authenticate { creds ->
                val siteId = Admin.authenticate(creds.token) ?: return@authenticate null
                SitePrincipal(siteId)
            }
        }
        bearer("ops") {
            authenticate { creds ->
                if (opsToken == null) return@authenticate null
                if (constantTimeEquals(creds.token, opsToken)) OpsPrincipal("ops") else null
            }
        }
    }

    routing {
        get(Api.PATH_HEALTHZ) { call.respondText("ok") }

        // Browser-friendly ops dashboard at /ops; uses ?t=$OPS_TOKEN so the page
        // can be bookmarked. JSON sidecar at /ops/data.json.
        opsDashboard(opsToken)

        authenticate("ops") {
            // Provision a new site. Body: { "label": "office-1" }. Response includes
            // the one-time plaintext token; subsequent calls cannot recover it.
            post("/v1/admin/sites") {
                val body = call.receive<CreateSiteRequest>()
                if (body.label.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest); return@post
                }
                val (siteId, token) = Admin.createSite(body.label)
                call.respond(HttpStatusCode.Created, CreateSiteResponse(siteId, token))
            }

            // List provisioned sites (no tokens — they're hashed).
            get("/v1/admin/sites") {
                val sites = Admin.listSites().map {
                    SiteListEntry(it.siteId, it.label, it.createdAt)
                }
                call.respond(SiteListResponse(sites))
            }

            // Issue a fresh token for an existing site, replacing the previous one.
            post("/v1/admin/sites/{siteId}/rotate") {
                val siteId = call.parameters["siteId"]!!
                val newToken = Admin.rotateToken(siteId)
                if (newToken == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(RotateTokenResponse(siteId, newToken))
            }

            // Permanent revoke + run deletion.
            delete("/v1/admin/sites/{siteId}") {
                val siteId = call.parameters["siteId"]!!
                if (Admin.deleteSite(siteId)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound)
            }
        }

        authenticate("site") {
            post(Api.PATH_STATS) {
                val principal = call.principal<SitePrincipal>()!!
                val report = call.receive<RunReport>()
                if (report.siteId != principal.siteId) {
                    call.respond(HttpStatusCode.Forbidden, IngestAck(false, error = "site_id does not match token"))
                    return@post
                }
                val runId = UUID.randomUUID().toString()
                val json = Json.encodeToString(RunReport.serializer(), report)
                val avgLatency = report.targets.mapNotNull { it.latencyMs?.avg }.takeIf { it.isNotEmpty() }?.average()
                val avgLoss = report.targets.map { it.lossPct }.takeIf { it.isNotEmpty() }?.average()
                transaction {
                    Runs.insert {
                        it[Runs.siteId] = report.siteId
                        it[Runs.ts] = report.startedAt.toEpochMilliseconds()
                        it[Runs.durationMs] = report.durationMs
                        it[Runs.json] = json
                        it[Runs.clientVersion] = report.clientVersion
                        it[Runs.avgLatencyMs] = avgLatency
                        it[Runs.lossPct] = avgLoss
                        it[Runs.downMbps] = report.speed?.downMbps
                        it[Runs.upMbps] = report.speed?.upMbps
                    }
                }
                call.respond(HttpStatusCode.Accepted, IngestAck(accepted = true, runId = runId))
            }

            route("/v1/sites/{siteId}/stats") {
                get {
                    val principal = call.principal<SitePrincipal>()!!
                    val siteId = call.parameters["siteId"]!!
                    if (siteId != principal.siteId) {
                        call.respond(HttpStatusCode.Forbidden); return@get
                    }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
                    val runs = transaction {
                        Runs.selectAll()
                            .where { Runs.siteId eq siteId }
                            .orderBy(Runs.ts, SortOrder.DESC)
                            .limit(limit)
                            .map { Json.decodeFromString(RunReport.serializer(), it[Runs.json]) }
                    }
                    call.respond(SiteStatsPage(siteId, runs))
                }
            }

            get(Api.PATH_SPEED_DOWNLOAD) {
                val bytes = (call.request.queryParameters["bytes"]?.toLongOrNull() ?: (1L * 1024 * 1024))
                    .coerceIn(1, Api.MAX_SPEED_BYTES)
                call.respondOutputStream(contentLength = bytes) {
                    val chunk = ByteArray(64 * 1024)
                    Random.Default.nextBytes(chunk)
                    var remaining = bytes
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                        write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            }

            post(Api.PATH_SPEED_UPLOAD) {
                val started = System.nanoTime()
                val input = call.receiveStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > Api.MAX_SPEED_BYTES) {
                        call.respond(HttpStatusCode.PayloadTooLarge); return@post
                    }
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                call.respond(SpeedUploadAck(bytesReceived = total, elapsedMs = elapsedMs))
            }
        }
    }
}
