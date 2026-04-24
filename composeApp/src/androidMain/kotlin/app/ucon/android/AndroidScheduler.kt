package app.ucon.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

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
        // Kick off a run; wait for the running flag to clear so WorkManager knows we finished.
        vm.runNow()
        waitForRunToFinish(vm.scope)
        return Result.success()
    }

    private suspend fun waitForRunToFinish(@Suppress("UNUSED_PARAMETER") scope: kotlinx.coroutines.CoroutineScope) {
        // Poll the running state with a modest ceiling; we don't want to hold the worker forever.
        val deadline = System.currentTimeMillis() + 60.seconds.inWholeMilliseconds
        while (UconApplication.viewModel.state.value.running &&
            System.currentTimeMillis() < deadline
        ) {
            suspendCancellableCoroutine<Unit> { cont ->
                val t = kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(500)
                    cont.resume(Unit)
                }
                cont.invokeOnCancellation { t.cancel() }
            }
        }
    }
}
