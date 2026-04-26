package app.ucon.ios

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

const val APP_GROUP_ID: String = "group.app.ucon.shared"

/**
 * Set from Swift at app launch — Kotlin/Native has no WidgetKit cinterop, so
 * the actual `WidgetCenter.shared.reloadAllTimelines()` lives on the Swift side.
 *
 * `iOSApp.swift` does:
 *   AppContainerKt.widgetReloader = { WidgetCenter.shared.reloadAllTimelines() }
 */
@Suppress("unused")
var widgetReloader: () -> Unit = {}

object AppContainer {
    val httpClient = newHttpClient()
    val settingsStore = SettingsStore()
    val secureStore = SecureStore()
    val repo = RunRepository(DriverFactory())

    private val probes = Probes(httpClient)
    private val speedProbe = SpeedProbe(httpClient)
    val measurement = MeasurementRun(probes, speedProbe)

    val uploadQueue = UploadQueue(
        repo = repo,
        httpClient = httpClient,
        serverBaseUrlProvider = { settingsStore.load().serverBaseUrl.ifBlank { null } },
        tokenProvider = { secureStore.getToken() },
    )

    private val surfaceBridge = SurfaceBridge(
        appGroupId = APP_GROUP_ID,
        reloadTimelines = { widgetReloader() },
    )

    val viewModel = AppViewModel(
        repo = repo,
        measurement = measurement,
        uploadQueue = uploadQueue,
        settingsStore = settingsStore,
        secureStore = secureStore,
        httpClient = httpClient,
        surface = surfaceBridge,
        clientVersion = "1.0.0-ios",
    )
}
