package app.ucon.android

import android.app.Application
import androidx.work.Configuration
import app.ucon.config.SecureStore
import app.ucon.config.SettingsStore
import app.ucon.data.DriverFactory
import app.ucon.data.RunRepository
import app.ucon.data.UploadQueue
import app.ucon.measure.MeasurementRun
import app.ucon.measure.Probes
import app.ucon.measure.SpeedProbe
import app.ucon.net.newHttpClient
import app.ucon.surface.SurfaceBridge
import app.ucon.ui.AppViewModel

class UconApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        instance = this

        val httpClient = newHttpClient()
        val settingsStore = SettingsStore(this)
        val secureStore = SecureStore(this)
        val repo = RunRepository(DriverFactory(this))
        val probes = Probes(httpClient)
        val speedProbe = SpeedProbe(httpClient)
        val measurement = MeasurementRun(probes, speedProbe)
        val uploadQueue = UploadQueue(
            repo = repo,
            httpClient = httpClient,
            serverBaseUrlProvider = { settingsStore.load().serverBaseUrl.ifBlank { null } },
            tokenProvider = { secureStore.getToken() },
        )

        // Glance is wired in the widget package; passing the updater here as a lambda
        // keeps :shared free of androidx.glance dependencies. Until the widget lands
        // in this commit pass, the updater is a no-op.
        val surfaceBridge = SurfaceBridge(this) { /* widget added in next commit */ }

        viewModel = AppViewModel(
            repo = repo,
            measurement = measurement,
            uploadQueue = uploadQueue,
            settingsStore = settingsStore,
            secureStore = secureStore,
            httpClient = httpClient,
            surface = surfaceBridge,
            clientVersion = "1.0.0-android",
        )

        AndroidScheduler.scheduleFromSettings(this, settingsStore.load().intervalMinutes)
    }

    companion object {
        lateinit var instance: UconApplication
            private set

        lateinit var viewModel: AppViewModel
            private set
    }
}
