package app.ucon.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.ucon.api.RunReport
import app.ucon.data.db.UconDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Thin wrapper around the SQLDelight-generated queries so the rest of the app doesn't
 * touch the DB types directly. Writes every run locally before the uploader tries to
 * ship it to the server — offline installs don't lose data.
 */
class RunRepository(driverFactory: DriverFactory) {
    private val db = UconDb(driverFactory.create())
    private val q = db.uconDbQueries
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun latest(): Flow<Run?> =
        q.latestRun().asFlow().mapToOneOrNull(Dispatchers.Default)

    fun recent(limit: Long = 50): Flow<List<Run>> =
        q.recentRuns(limit).asFlow().mapToList(Dispatchers.Default)

    fun pendingCount(): Flow<Long> =
        q.pendingCount().asFlow().mapToOne(Dispatchers.Default)

    fun insertRun(report: RunReport): Long {
        val avgLatency = report.targets.mapNotNull { it.latencyMs?.avg }.takeIf { it.isNotEmpty() }?.average()
        val lossPct = report.targets.map { it.lossPct }.takeIf { it.isNotEmpty() }?.average()
        q.insertRun(
            started_at = report.startedAt.toEpochMilliseconds(),
            duration_ms = report.durationMs,
            avg_latency_ms = avgLatency,
            loss_pct = lossPct,
            down_mbps = report.speed?.downMbps,
            up_mbps = report.speed?.upMbps,
            json = json.encodeToString(RunReport.serializer(), report),
        )
        return q.lastInsertedId().executeAsOne()
    }

    fun nextPending(nowMillis: Long): Run? = q.nextPending(nowMillis).executeAsOneOrNull()

    fun markSent(id: Long) = q.markSent(id)

    fun markFailedAttempt(id: Long, nextAttemptAt: Long) =
        q.markFailedAttempt(next = nextAttemptAt, id = id)

    fun parseReport(row: Run): RunReport = json.decodeFromString(RunReport.serializer(), row.json)

    fun prune(cutoffMillis: Long) = q.deleteOlderThan(cutoffMillis)
}
