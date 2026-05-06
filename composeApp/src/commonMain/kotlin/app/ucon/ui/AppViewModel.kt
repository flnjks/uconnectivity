package app.ucon.ui

import app.ucon.api.LastRunSummary
import app.ucon.api.RunReport
import app.ucon.config.AppSettings
import app.ucon.config.AutoStartManager
import app.ucon.config.SecureStore
import app.ucon.config.SettingsStore
import app.ucon.data.Run
import app.ucon.data.RunRepository
import app.ucon.data.UploadQueue
import app.ucon.data.toLastRunSummary
import app.ucon.measure.MeasurementRun
import app.ucon.measure.RunConfig
import app.ucon.measure.Target
import app.ucon.surface.SurfaceBridge
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val settings: AppSettings = AppSettings(),
    val tokenPresent: Boolean = false,
    val latest: Run? = null,
    val latestSummary: LastRunSummary? = null,
    val recent: List<Run> = emptyList(),
    val pendingUploads: Long = 0,
    val running: Boolean = false,
    val autoStartSupported: Boolean = false,
    val autoStartEnabled: Boolean = false,
)

/**
 * App-wide view model. Pure Kotlin — no Compose types here so platform
 * entry points can reuse it (e.g. Android Service, iOS SceneDelegate).
 *
 * On every DB change (insert from a run completion, or anything else)
 * pushes a [LastRunSummary] + recent-list out via [SurfaceBridge] so the
 * macOS menu bar / Windows tray / Android+iOS widgets refresh in lockstep.
 */
class AppViewModel(
    private val repo: RunRepository,
    private val measurement: MeasurementRun,
    private val uploadQueue: UploadQueue,
    private val settingsStore: SettingsStore,
    private val secureStore: SecureStore,
    private val httpClient: HttpClient,
    private val surface: SurfaceBridge,
    val clientVersion: String,
    private val autoStart: AutoStartManager = AutoStartManager(),
    parentScope: CoroutineScope? = null,
) {
    val scope: CoroutineScope = parentScope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _settings = MutableStateFlow(settingsStore.load())
    private val _tokenPresent = MutableStateFlow(secureStore.getToken() != null)
    private val _running = MutableStateFlow(false)
    private val _autoStartEnabled = MutableStateFlow(autoStart.isEnabled())

    val state: StateFlow<UiState> = combine(
        _settings,
        _tokenPresent,
        repo.latest(),
        repo.recent(10),
        repo.pendingCount(),
        _running,
        _autoStartEnabled,
    ) { arr ->
        val latestRun = arr[2] as Run?
        val recentRuns = @Suppress("UNCHECKED_CAST") (arr[3] as List<Run>)
        val siteLabel = (arr[0] as AppSettings).siteId
        UiState(
            settings = arr[0] as AppSettings,
            tokenPresent = arr[1] as Boolean,
            latest = latestRun,
            latestSummary = latestRun?.toLastRunSummary(siteLabel),
            recent = recentRuns,
            pendingUploads = arr[4] as Long,
            running = arr[5] as Boolean,
            autoStartSupported = autoStart.isSupported,
            autoStartEnabled = arr[6] as Boolean,
        )
    }
        .onEach { ui ->
            // Republish to platform surfaces every time the DB or settings change.
            val recentSummaries = ui.recent.map { it.toLastRunSummary(ui.settings.siteId) }
            surface.publishLatest(ui.latestSummary, recentSummaries)
        }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, UiState())

    init {
        uploadQueue.start(scope)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        settingsStore.save(next)
        _settings.value = next
    }

    fun setToken(token: String?) {
        secureStore.setToken(token)
        _tokenPresent.value = token != null
    }

    fun setAutoStart(enabled: Boolean) {
        if (!autoStart.isSupported) return
        val ok = autoStart.setEnabled(enabled)
        _autoStartEnabled.value = if (ok) enabled else autoStart.isEnabled()
    }

    /**
     * Runs a measurement synchronously on a background coroutine and persists the result.
     * Uploader will pick it up next.
     */
    fun runNow() {
        if (_running.value) return
        _running.value = true
        scope.launch {
            try {
                val s = _settings.value
                val token = secureStore.getToken().orEmpty()
                val cfg = RunConfig(
                    siteId = s.siteId.ifBlank { "unprovisioned" },
                    serverBaseUrl = s.serverBaseUrl,
                    token = token,
                    clientVersion = clientVersion,
                    targets = Target.DEFAULTS,
                    speedTestEnabled = s.speedTestEnabled,
                )
                val report: RunReport = measurement.runOnce(cfg)
                repo.insertRun(report)
            } finally {
                _running.value = false
            }
        }
    }

    fun prune(keepDays: Int = 30) {
        val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() -
            keepDays.toLong() * 24 * 3600 * 1000
        repo.prune(cutoff)
    }
}
