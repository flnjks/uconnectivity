package app.ucon.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * Plain-HTML ops dashboard at /ops with a JSON sidecar at /ops/data.json.
 *
 * Auth: gated by [opsToken]. The dashboard accepts the token via either the
 * `Authorization: Bearer <token>` header or a `?t=<token>` query parameter so
 * a bookmarked URL works without configuring a browser to send the header.
 *
 * The HTML is intentionally a single-page no-framework affair — vanilla JS
 * polling /ops/data.json every 30 s, a simple table, status pill colors. If
 * we outgrow this it's the right kind of growth (means there's a fleet).
 */
@Serializable
data class FleetSnapshotEntry(
    val siteId: String,
    val label: String,
    val createdAt: Long,
    val lastRunAt: Long?,
    val avgLatencyMs: Double?,
    val lossPct: Double?,
    val downMbps: Double?,
    val upMbps: Double?,
    val status: String,
)

@Serializable
data class FleetSnapshotResponse(val sites: List<FleetSnapshotEntry>, val ts: Long)

internal fun Route.opsDashboard(opsToken: String?) {
    fun authorized(call: io.ktor.server.application.ApplicationCall): Boolean {
        if (opsToken == null) return false
        val header = call.request.headers["Authorization"]
            ?.removePrefix("Bearer ")?.trim()
            ?.takeIf { it.isNotBlank() }
        if (header != null && constantTimeEqualsServer(header, opsToken)) return true
        val q = call.request.queryParameters["t"]
        return q != null && constantTimeEqualsServer(q, opsToken)
    }

    get("/ops") {
        if (!authorized(call)) {
            call.respond(HttpStatusCode.Unauthorized, "ops token required (Authorization: Bearer …, or ?t=…)")
            return@get
        }
        call.respondText(OPS_DASHBOARD_HTML, ContentType.Text.Html)
    }

    get("/ops/data.json") {
        if (!authorized(call)) {
            call.respond(HttpStatusCode.Unauthorized); return@get
        }
        val sites = Admin.fleetSnapshot().map { s ->
            FleetSnapshotEntry(
                siteId = s.siteId,
                label = s.label,
                createdAt = s.createdAt,
                lastRunAt = s.lastRunAt,
                avgLatencyMs = s.avgLatencyMs,
                lossPct = s.lossPct,
                downMbps = s.downMbps,
                upMbps = s.upMbps,
                status = statusOf(s.avgLatencyMs, s.lossPct),
            )
        }
        call.respond(FleetSnapshotResponse(sites = sites, ts = System.currentTimeMillis()))
    }
}

private fun statusOf(avgLatencyMs: Double?, lossPct: Double?): String {
    val loss = lossPct ?: 0.0
    val lat = avgLatencyMs ?: 0.0
    return when {
        loss >= 10.0 || lat >= 500.0 -> "Bad"
        loss >= 2.0 || lat >= 150.0 -> "Warn"
        else -> "Good"
    }
}

private fun constantTimeEqualsServer(a: String, b: String): Boolean {
    val ab = a.toByteArray()
    val bb = b.toByteArray()
    if (ab.size != bb.size) return false
    var diff = 0
    for (i in ab.indices) diff = diff or (ab[i].toInt() xor bb[i].toInt())
    return diff == 0
}

@Suppress("MaxLineLength")
private val OPS_DASHBOARD_HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>uConnectivity — fleet</title>
<style>
  body { font: 14px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
         margin: 24px; color: #111; background: #fafafa; }
  h1 { margin: 0 0 4px; font-size: 20px; }
  .meta { color: #666; font-size: 12px; margin-bottom: 16px; }
  table { width: 100%; border-collapse: collapse; background: white; box-shadow: 0 1px 0 rgba(0,0,0,.05); }
  th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
  th { background: #f5f5f5; font-weight: 600; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: #555; }
  td.num { font-variant-numeric: tabular-nums; }
  .pill { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; color: white; }
  .pill.Good { background: #2e7d32; }
  .pill.Warn { background: #f57f17; }
  .pill.Bad { background: #c62828; }
  .pill.None { background: #888; }
  .stale { color: #999; }
</style>
</head>
<body>
<h1>uConnectivity fleet</h1>
<div class="meta" id="meta">loading…</div>
<table>
  <thead>
    <tr>
      <th>status</th><th>site</th><th>down</th><th>up</th>
      <th>latency</th><th>loss</th><th>last run</th>
    </tr>
  </thead>
  <tbody id="rows"></tbody>
</table>

<script>
const qs = new URLSearchParams(location.search);
const t = qs.get("t");
const url = "/ops/data.json" + (t ? "?t=" + encodeURIComponent(t) : "");
const fmtMbps = v => v == null ? "—" : v.toFixed(1) + " Mbps";
const fmtMs   = v => v == null ? "—" : Math.round(v) + " ms";
const fmtPct  = v => v == null ? "—" : v.toFixed(1) + "%";
const fmtTs   = v => v == null ? "(never)" : new Date(v).toISOString().replace("T", " ").replace(/\..+/, "");
const minutesAgo = v => v == null ? Infinity : Math.floor((Date.now() - v) / 60000);

async function tick() {
  try {
    const r = await fetch(url, { credentials: "omit" });
    if (!r.ok) { document.getElementById("meta").textContent = "auth required (?t=…) — " + r.status; return; }
    const data = await r.json();
    document.getElementById("meta").textContent =
      data.sites.length + " sites · refreshed " + new Date(data.ts).toLocaleTimeString();
    const rows = document.getElementById("rows");
    rows.innerHTML = "";
    for (const s of data.sites) {
      const tr = document.createElement("tr");
      const stale = minutesAgo(s.lastRunAt) > 90;
      const pill = s.lastRunAt == null ? "None" : s.status;
      tr.innerHTML =
        '<td><span class="pill ' + pill + '">' + (s.lastRunAt == null ? "—" : s.status) + '</span></td>' +
        '<td><strong>' + escape(s.label) + '</strong><br><span class="stale">' + escape(s.siteId) + '</span></td>' +
        '<td class="num">' + fmtMbps(s.downMbps) + '</td>' +
        '<td class="num">' + fmtMbps(s.upMbps) + '</td>' +
        '<td class="num">' + fmtMs(s.avgLatencyMs) + '</td>' +
        '<td class="num">' + fmtPct(s.lossPct) + '</td>' +
        '<td class="' + (stale ? 'stale' : '') + '">' + fmtTs(s.lastRunAt) + '</td>';
      rows.appendChild(tr);
    }
  } catch (e) {
    document.getElementById("meta").textContent = "fetch error: " + e;
  }
}
function escape(s) {
  return String(s).replace(/[&<>"']/g, c => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
  ));
}
tick();
setInterval(tick, 30000);
</script>
</body>
</html>
""".trimIndent()
