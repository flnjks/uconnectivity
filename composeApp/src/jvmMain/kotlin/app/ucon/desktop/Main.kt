package app.ucon.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import app.ucon.ui.App
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.MenuBarScope
import kotlinx.coroutines.CoroutineScope

fun main() = application {
    val vm = AppContainer.viewModel

    // Start the desktop scheduler once, tied to the Compose application scope.
    val trayState = rememberTrayState()
    val scheduler = remember {
        DesktopScheduler(
            intervalProvider = { AppContainer.settingsStore.load().intervalMinutes },
            action = { vm.runNow() },
        )
    }
    LaunchedEffect(Unit) {
        scheduler.start(vm.scope)
    }

    Tray(
        icon = rememberVectorPainter(Icons.Filled.NetworkCheck),
        state = trayState,
        tooltip = "uConnectivity",
        menu = {
            Item("Run test now", onClick = { vm.runNow() })
            Item("Quit", onClick = ::exitApplication)
        },
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "uConnectivity",
    ) {
        App(vm)
    }
}
