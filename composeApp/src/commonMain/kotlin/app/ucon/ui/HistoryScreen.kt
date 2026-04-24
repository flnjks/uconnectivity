package app.ucon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ucon.data.Run
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryScreen(state: UiState) {
    SectionTitle("Recent runs (${state.recent.size})")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.recent, key = { it.id }) { run ->
            HistoryRow(run)
        }
    }
}

@Composable
private fun HistoryRow(run: Run) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(ts(run.started_at), style = MaterialTheme.typography.titleSmall)
            Text(
                "lat ${formatMs(run.avg_latency_ms)} · loss ${formatPct(run.loss_pct)} · " +
                    "down ${formatMbps(run.down_mbps)} · up ${formatMbps(run.up_mbps)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("upload: ${run.status.lowercase()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun ts(ms: Long): String =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).toString()
