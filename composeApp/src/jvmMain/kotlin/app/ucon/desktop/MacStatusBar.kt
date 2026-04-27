package app.ucon.desktop

import app.ucon.api.LastRunSummary
import app.ucon.ui.toFixed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Spawns the bundled `uconnectivity-statusbar` Swift helper as a child
 * process, pipes title + menu updates in over stdin (line-delimited JSON),
 * and reacts to user clicks read back over stdout.
 *
 * Runs only on macOS — Windows uses Compose Desktop's Tray instead.
 */
class MacStatusBar(
    private val helperPath: File,
    private val onMenuClick: (String) -> Unit,
) {
    private var process: Process? = null
    private var readerJob: Job? = null
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope): Boolean {
        if (process?.isAlive == true) return true
        if (!helperPath.exists()) {
            System.err.println("uConnectivity: status-bar helper not found at $helperPath")
            return false
        }
        val pb = ProcessBuilder(helperPath.absolutePath)
            .redirectErrorStream(false)
        val p = pb.start()
        process = p
        readerJob = scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    runCatching {
                        val ev = json.decodeFromString(EventMessage.serializer(), line)
                        if (ev.event == "click") onMenuClick(ev.id)
                    }
                }
            }
        }
        return true
    }

    fun update(latest: LastRunSummary?, recent: List<LastRunSummary>, running: Boolean) {
        val p = process ?: return
        val msg = UpdateMessage(
            title = headerLine(latest, running),
            menu = buildMenu(latest, recent, running),
        )
        val payload = json.encodeToString(UpdateMessage.serializer(), msg) + "\n"
        runCatching {
            p.outputStream.write(payload.toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
        }
    }

    fun stop() {
        readerJob?.cancel()
        process?.destroy()
        process = null
    }

    private fun buildMenu(latest: LastRunSummary?, recent: List<LastRunSummary>, running: Boolean): List<MenuItemMsg> {
        val items = mutableListOf<MenuItemMsg>()
        items += MenuItemMsg(id = "header", label = headerLine(latest, running), enabled = false)
        if (recent.isNotEmpty()) {
            items += MenuItemMsg(
                id = "recent",
                label = "Recent runs",
                enabled = true,
                submenu = recent.map { row ->
                    MenuItemMsg(id = "recent-${row.ts}", label = formatRecentRow(row), enabled = false)
                },
            )
        }
        items += MenuItemMsg(id = "run", label = "Run test now", enabled = !running, separatorBefore = true)
        items += MenuItemMsg(id = "settings", label = "Settings…", enabled = true)
        items += MenuItemMsg(id = "quit", label = "Quit", enabled = true, separatorBefore = true)
        return items
    }
}

@Serializable
private data class UpdateMessage(val title: String, val menu: List<MenuItemMsg>)

@Serializable
private data class MenuItemMsg(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val separatorBefore: Boolean = false,
    val submenu: List<MenuItemMsg>? = null,
)

@Serializable
private data class EventMessage(val event: String, val id: String)

internal fun headerLine(latest: LastRunSummary?, running: Boolean): String {
    if (running) return "running…"
    if (latest == null) return "— / — Mbps"
    val down = latest.downMbps?.toFixed(0) ?: "—"
    val up = latest.upMbps?.toFixed(0) ?: "—"
    return "$down⮃$up Mbps"
}

internal fun formatRecentRow(row: LastRunSummary): String {
    val down = row.downMbps?.toFixed(0) ?: "—"
    val up = row.upMbps?.toFixed(0) ?: "—"
    val lat = row.avgLatencyMs?.toFixed(0) ?: "—"
    val ts = row.ts.toString().substringBefore('.').replace('T', ' ')
    return "$ts  ↓$down ↑$up  ${lat}ms"
}
