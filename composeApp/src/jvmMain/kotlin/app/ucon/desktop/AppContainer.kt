package app.ucon.desktop

import app.ucon.config.SecureStore
import app.ucon.config.SettingsStore
import app.ucon.data.DriverFactory
import app.ucon.data.RunRepository
import app.ucon.data.UploadQueue
import app.ucon.measure.MeasurementRun
import app.ucon.measure.Probes
import app.ucon.measure.SpeedProbe
import app.ucon.net.newHttpClient
import app.ucon.ui.AppViewModel
import io.ktor.client.HttpClient
import java.io.File

private const val CLIENT_VERSION = "1.0.0-desktop"

object AppContainer {
    private val dataDir: File = platformDataDir()

    val httpClient: HttpClient = newHttpClient()

    val settingsStore = SettingsStore(File(dataDir, "settings.properties"))
    val secureStore = SecureStore(File(dataDir, "token"))
    val repo = RunRepository(DriverFactory(File(dataDir, "ucon.db")))

    private val probes = Probes(httpClient)
    private val speedProbe = SpeedProbe(httpClient)
    val measurement = MeasurementRun(probes, speedProbe)

    val uploadQueue = UploadQueue(
        repo = repo,
        httpClient = httpClient,
        serverBaseUrlProvider = { settingsStore.load().serverBaseUrl.ifBlank { null } },
        tokenProvider = { secureStore.getToken() },
    )

    val viewModel = AppViewModel(
        repo = repo,
        measurement = measurement,
        uploadQueue = uploadQueue,
        settingsStore = settingsStore,
        secureStore = secureStore,
        httpClient = httpClient,
        clientVersion = CLIENT_VERSION,
    )
}

/**
 * macOS: ~/Library/Application Support/uConnectivity
 * Windows: %APPDATA%/uConnectivity
 * Linux: ~/.config/uConnectivity
 */
private fun platformDataDir(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        os.contains("mac") -> File(home, "Library/Application Support/uConnectivity")
        os.contains("win") -> File(System.getenv("APPDATA") ?: home, "uConnectivity")
        else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "uConnectivity")
    }
    dir.mkdirs()
    return dir
}
