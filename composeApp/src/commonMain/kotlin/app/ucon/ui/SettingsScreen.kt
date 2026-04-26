package app.ucon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ucon.api.LastRunSummary
import app.ucon.api.RunStatus
import app.ucon.config.AppSettings

@Composable
fun SettingsScreen(
    state: UiState,
    onUpdateSettings: (AppSettings) -> Unit,
    onSetToken: (String?) -> Unit,
    onRunNow: () -> Unit,
) {
    var siteId by remember(state.settings.siteId) { mutableStateOf(state.settings.siteId) }
    var serverBaseUrl by remember(state.settings.serverBaseUrl) { mutableStateOf(state.settings.serverBaseUrl) }
    var interval by remember(state.settings.intervalMinutes) { mutableStateOf(state.settings.intervalMinutes.toString()) }
    var speedTest by remember(state.settings.speedTestEnabled) { mutableStateOf(state.settings.speedTestEnabled) }
    var tokenInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LastRunCard(state.latestSummary, state.running, onRunNow)

        SectionTitle("Settings")

        OutlinedTextField(
            value = siteId,
            onValueChange = { siteId = it },
            label = { Text("Site ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = serverBaseUrl,
            onValueChange = { serverBaseUrl = it },
            label = { Text("Server base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = interval,
            onValueChange = { if (it.all(Char::isDigit)) interval = it },
            label = { Text("Interval (minutes)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = speedTest, onCheckedChange = { speedTest = it })
            Text("  Run speed test (uses ~10 MB per hour)", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            buildString {
                append("Speed test endpoint: ")
                append(
                    if (serverBaseUrl.isNotBlank() && state.tokenPresent) "your server"
                    else "speed.cloudflare.com (fallback)"
                )
            },
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text(if (state.tokenPresent) "Paste new bearer token" else "Bearer token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onUpdateSettings(
                    AppSettings(
                        siteId = siteId.trim(),
                        serverBaseUrl = serverBaseUrl.trim(),
                        intervalMinutes = interval.toIntOrNull()?.coerceIn(5, 1440) ?: 60,
                        speedTestEnabled = speedTest,
                    )
                )
                if (tokenInput.isNotBlank()) {
                    onSetToken(tokenInput.trim())
                    tokenInput = ""
                }
            }) { Text("Save") }

            if (state.tokenPresent) {
                Button(onClick = { onSetToken(null) }) { Text("Clear token") }
            }
        }

        Text(
            buildString {
                append("Token: ")
                append(if (state.tokenPresent) "stored" else "missing")
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Note: on iOS, actual run cadence is at the OS's discretion. Desktop + Android " +
                "respect the interval within ~1 minute.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LastRunCard(latest: LastRunSummary?, running: Boolean, onRunNow: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (latest == null) {
                Text("No runs yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap \"Run test now\" — speed test runs against speed.cloudflare.com " +
                        "until a server URL + token are saved.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(latest.status)
                    Text(
                        "  ${formatMbps(latest.downMbps)} ↓  ${formatMbps(latest.upMbps)} ↑",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    "${formatMs(latest.avgLatencyMs)} latency · ${formatPct(latest.lossPct)} loss",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRunNow, enabled = !running) {
                    Text(if (running) "Running…" else "Run test now")
                }
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: RunStatus) {
    val color = when (status) {
        RunStatus.Good -> Color(0xFF2E7D32)
        RunStatus.Warn -> Color(0xFFF9A825)
        RunStatus.Bad -> Color(0xFFC62828)
    }
    Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
}

internal fun formatMs(v: Double?): String = v?.let { "${it.toFixed(0)} ms" } ?: "—"
internal fun formatPct(v: Double?): String = v?.let { "${it.toFixed(1)}%" } ?: "—"
internal fun formatMbps(v: Double?): String = v?.let { "${it.toFixed(1)} Mbps" } ?: "—"
