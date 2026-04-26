package app.ucon.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.ucon.android.MainActivity
import app.ucon.android.UconApplication
import app.ucon.api.LastRunSummary
import app.ucon.api.RunStatus
import app.ucon.data.toLastRunSummary
import kotlinx.coroutines.flow.first
import kotlin.math.roundToLong

class UconGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read the latest row from the same SQLDelight repo the rest of the app uses.
        val latest: LastRunSummary? = runCatching {
            val state = UconApplication.viewModel.state.first()
            state.latestSummary
        }.getOrNull()

        provideContent { Body(latest) }
    }

    @Composable
    private fun Body(latest: LastRunSummary?) {
        val (bg, fg) = if (latest == null) Color(0xFF424242) to Color.White
                       else statusColors(latest.status)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = if (latest == null) "uConnectivity" else formatPair(latest),
                    style = TextStyle(color = androidx.glance.color.ColorProvider(day = fg, night = fg), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = if (latest == null) "tap to set up" else "lat ${formatLat(latest)} · loss ${formatLoss(latest)}",
                    style = TextStyle(color = androidx.glance.color.ColorProvider(day = fg, night = fg), fontSize = 11.sp),
                )
            }
        }
    }
}

private fun statusColors(s: RunStatus): Pair<Color, Color> = when (s) {
    RunStatus.Good -> Color(0xFF1B5E20) to Color.White
    RunStatus.Warn -> Color(0xFFF57F17) to Color.White
    RunStatus.Bad -> Color(0xFFB71C1C) to Color.White
}

private fun formatPair(s: LastRunSummary): String {
    val d = s.downMbps?.let { "${(it * 10).roundToLong() / 10.0}" } ?: "—"
    val u = s.upMbps?.let { "${(it * 10).roundToLong() / 10.0}" } ?: "—"
    return "$d↓ / $u↑ Mbps"
}

private fun formatLat(s: LastRunSummary): String =
    s.avgLatencyMs?.let { "${it.roundToLong()} ms" } ?: "—"

private fun formatLoss(s: LastRunSummary): String =
    s.lossPct?.let { "${(it * 10).roundToLong() / 10.0}%" } ?: "—"
