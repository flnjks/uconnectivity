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
 * Hooks set from Swift (`iosApp/iosApp/iOSApp.swift`) at app launch.
 * Kotlin/Native lacks first-class WidgetKit and Security.framework interop,
 * so we delegate the platform-specific bits to Swift.
 *
 *   AppContainer_iosKt.widgetReloader  = { WidgetCenter.shared.reloadAllTimelines() }
 *   AppContainer_iosKt.keychainReader  = { Keychain.read() }
 *   AppContainer_iosKt.keychainWriter  = { Keychain.write($0) }
 *   AppContainer_iosKt.keychainDeleter = { Keychain.delete() }
 */
@Suppress("unused") var widgetReloader: () -> Unit = {}
@Suppress("unused") var keychainReader: () -> String? = { null }
@Suppress("unused") var keychainWriter: (String) -> Unit = { }
@Suppress("unused") var keychainDeleter: () -> Unit = {}

object AppContainer {
    val httpClient = newHttpClient()
    val settingsStore = SettingsStore()
    val secureStore = SecureStore(
        read = { keychainReader() },
        write = { keychainWriter(it) },
        delete = { keychainDeleter() },
    )
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
