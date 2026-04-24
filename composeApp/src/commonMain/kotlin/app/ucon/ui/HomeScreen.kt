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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ucon.data.Run
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HomeScreen(state: UiState, onRunNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteHeader(state)
        Gap()
        state.latest?.let { LatestRunCard(it) } ?: NoRunsYetCard()
        Gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRunNow, enabled = !state.running) {
                Text(if (state.running) "Running…" else "Run test now")
            }
            if (state.running) {
                Text(
                    "  measuring",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp),
                )
                CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(start = 6.dp))
            }
        }
        Gap()
        Text(
            "${state.pendingUploads} pending upload${if (state.pendingUploads == 1L) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SiteHeader(state: UiState) {
    Column {
        Text(
            state.settings.siteId.ifBlank { "(not provisioned)" },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            state.settings.serverBaseUrl.ifBlank { "no server configured" },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LatestRunCard(run: Run) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status(run))
                Text(
                    "  ${formatTs(run.started_at)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "latency: ${formatMs(run.avg_latency_ms)} · loss: ${formatPct(run.loss_pct)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "down: ${formatMbps(run.down_mbps)} · up: ${formatMbps(run.up_mbps)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "duration ${run.duration_ms} ms · upload ${run.status.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoRunsYetCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "No runs yet — tap \"Run test now\" to start.",
            modifier = Modifier.padding(16.dp),
        )
    }
}

private enum class RunStatus { Good, Warn, Bad }

private fun status(run: Run): RunStatus {
    val loss = run.loss_pct ?: 0.0
    val lat = run.avg_latency_ms ?: 0.0
    return when {
        loss >= 10.0 || lat >= 500.0 -> RunStatus.Bad
        loss >= 2.0 || lat >= 150.0 -> RunStatus.Warn
        else -> RunStatus.Good
    }
}

@Composable
private fun StatusDot(s: RunStatus) {
    val color = when (s) {
        RunStatus.Good -> Color(0xFF2E7D32)
        RunStatus.Warn -> Color(0xFFF9A825)
        RunStatus.Bad -> Color(0xFFC62828)
    }
    Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
}

private fun formatTs(ms: Long): String =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).toString()

internal fun formatMs(v: Double?): String = v?.let { "${it.toFixed(0)} ms" } ?: "—"
internal fun formatPct(v: Double?): String = v?.let { "${it.toFixed(1)}%" } ?: "—"
internal fun formatMbps(v: Double?): String = v?.let { "${it.toFixed(1)} Mbps" } ?: "—"
