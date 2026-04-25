package app.ucon.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "app.ucon.periodic"

object AndroidScheduler {
    fun scheduleFromSettings(context: Context, intervalMinutes: Int) {
        val interval = intervalMinutes.toLong().coerceAtLeast(15L) // WorkManager minimum
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<MeasurementWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class MeasurementWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val vm = UconApplication.viewModel
        vm.runNow()
        // Wait for the run to clear, with a 60s ceiling so WorkManager isn't held forever.
        val deadline = System.currentTimeMillis() + 60_000L
        while (vm.state.value.running && System.currentTimeMillis() < deadline) {
            delay(500)
        }
        return Result.success()
    }
}
