package app.ucon.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import app.ucon.ui.AppViewModel
import app.ucon.ui.SettingsScreen

/**
 * Lazily-mounted settings dialog. Opened from the tray menu; everything else
 * happens through the always-visible tray surface.
 */
@Composable
fun SettingsWindow(vm: AppViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    val windowState: WindowState = rememberWindowState(width = 520.dp, height = 720.dp)
    Window(
        onCloseRequest = onClose,
        title = "uConnectivity — Settings",
        state = windowState,
    ) {
        MaterialTheme {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            ) {
                SettingsScreen(
                    state = state,
                    onUpdateSettings = { vm.updateSettings { _ -> it } },
                    onSetToken = vm::setToken,
                    onRunNow = vm::runNow,
                )
            }
        }
    }
}
