package app.ucon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class Screen(val label: String) { Home("Home"), History("History"), Settings("Settings") }

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var current by remember { mutableStateOf(Screen.Home) }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = current == Screen.Home,
                        onClick = { current = Screen.Home },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(Screen.Home.label) },
                    )
                    NavigationBarItem(
                        selected = current == Screen.History,
                        onClick = { current = Screen.History },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text(Screen.History.label) },
                    )
                    NavigationBarItem(
                        selected = current == Screen.Settings,
                        onClick = { current = Screen.Settings },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(Screen.Settings.label) },
                    )
                }
            },
        ) { inner ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (current) {
                    Screen.Home -> HomeScreen(state, onRunNow = vm::runNow)
                    Screen.History -> HistoryScreen(state)
                    Screen.Settings -> SettingsScreen(
                        state = state,
                        onUpdateSettings = { vm.updateSettings { _ -> it } },
                        onSetToken = vm::setToken,
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
fun Gap(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}
