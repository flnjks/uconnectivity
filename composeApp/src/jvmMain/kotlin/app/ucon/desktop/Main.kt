package app.ucon.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import app.ucon.api.LastRunSummary
import app.ucon.api.RunStatus
import app.ucon.ui.toFixed

fun main() = application {
    val vm = AppContainer.viewModel
    val bridge = AppContainer.surfaceBridge

    val latest by bridge.latest.collectAsState()
    val recent by bridge.recent.collectAsState()
    val state by vm.state.collectAsState()

    val scheduler = remember {
        DesktopScheduler(
            intervalProvider = { AppContainer.settingsStore.load().intervalMinutes },
            action = { vm.runNow() },
        )
    }
    LaunchedEffect(Unit) { scheduler.start(vm.scope) }

    var settingsOpen by remember { mutableStateOf(false) }
    val trayState = rememberTrayState()

    Tray(
        icon = rememberVectorPainter(Icons.Filled.NetworkCheck),
        state = trayState,
        tooltip = headerLine(latest, state.running),
        menu = {
            Item(headerLine(latest, state.running), enabled = false, onClick = {})

            if (recent.isNotEmpty()) {
                Menu("Recent runs") {
                    recent.forEach { row ->
                        Item(text = formatRecentRow(row), enabled = false, onClick = {})
                    }
                }
            }

            Item("Run test now", enabled = !state.running, onClick = vm::runNow)
            Item("Settings…", onClick = { settingsOpen = true })
            Item("Quit", onClick = ::exitApplication)
        },
    )

    if (settingsOpen) {
        SettingsWindow(vm = vm, onClose = { settingsOpen = false })
    }
}

private fun headerLine(latest: LastRunSummary?, running: Boolean): String {
    if (running) return "uConnectivity · running…"
    if (latest == null) return "uConnectivity · — / — Mbps"
    val pill = when (latest.status) {
        RunStatus.Good -> "✓"
        RunStatus.Warn -> "!"
        RunStatus.Bad -> "✕"
    }
    val down = latest.downMbps?.toFixed(0) ?: "—"
    val up = latest.upMbps?.toFixed(0) ?: "—"
    return "$pill $down/$up Mbps"
}

private fun formatRecentRow(row: LastRunSummary): String {
    val down = row.downMbps?.toFixed(0) ?: "—"
    val up = row.upMbps?.toFixed(0) ?: "—"
    val lat = row.avgLatencyMs?.toFixed(0) ?: "—"
    val ts = row.ts.toString().substringBefore('.').replace('T', ' ')
    return "$ts  ↓$down ↑$up  ${lat}ms"
}
