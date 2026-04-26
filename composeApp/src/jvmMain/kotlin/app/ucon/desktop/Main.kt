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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

private val IS_MACOS: Boolean = System.getProperty("os.name").lowercase().contains("mac")

fun main() = application {
    val vm = AppContainer.viewModel
    val bridge = AppContainer.surfaceBridge

    var settingsOpen by remember { mutableStateOf(false) }

    val scheduler = remember {
        DesktopScheduler(
            intervalProvider = { AppContainer.settingsStore.load().intervalMinutes },
            action = { vm.runNow() },
        )
    }
    LaunchedEffect(Unit) { scheduler.start(vm.scope) }

    val helper: File? = remember { if (IS_MACOS) locateMacHelper() else null }
    val statusBar: MacStatusBar? = remember(helper) {
        helper?.let {
            MacStatusBar(helperPath = it) { menuItemId ->
                when (menuItemId) {
                    "run" -> vm.runNow()
                    "settings" -> settingsOpen = true
                    "quit" -> exitApplication()
                }
            }
        }
    }

    LaunchedEffect(statusBar) {
        statusBar?.start(vm.scope)
    }

    LaunchedEffect(statusBar) {
        if (statusBar != null) {
            combine(bridge.latest, bridge.recent, vm.state) { l, r, s -> Triple(l, r, s.running) }
                .collect { (latest, recent, running) -> statusBar.update(latest, recent, running) }
        }
    }

    // Always render the Compose tray as a fallback; on macOS the Swift helper takes
    // visual primacy (NSStatusItem text in the menu bar), but the AWT tray stays
    // around as a click target if the helper crashes.
    if (statusBar == null) {
        val latest by bridge.latest.collectAsState()
        val recent by bridge.recent.collectAsState()
        val state by vm.state.collectAsState()

        Tray(
            icon = rememberVectorPainter(Icons.Filled.NetworkCheck),
            state = rememberTrayState(),
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
    }

    if (settingsOpen) {
        SettingsWindow(vm = vm, onClose = { settingsOpen = false })
    }
}

private fun locateMacHelper(): File? {
    // 1. Bundled into the Compose .app under Contents/Resources/uconnectivity-statusbar.
    System.getProperty("compose.application.resources.dir")
        ?.let { File(it, "uconnectivity-statusbar") }
        ?.takeIf { it.canExecute() }
        ?.let { return it }

    // 2. Local Swift Package build (development).
    val cwd = File("desktop-helper-macos/.build/arm64-apple-macosx/release/uconnectivity-statusbar")
    if (cwd.canExecute()) return cwd

    // 3. Override via env for ad-hoc testing.
    System.getenv("UCON_STATUSBAR_HELPER")
        ?.let { File(it) }
        ?.takeIf { it.canExecute() }
        ?.let { return it }

    return null
}
