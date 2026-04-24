package app.ucon.config

/**
 * Non-sensitive app configuration. Persisted via [SettingsStore] expect/actual.
 * Token is held separately in [SecureStore] (keychain / EncryptedSharedPreferences / DPAPI).
 */
data class AppSettings(
    val siteId: String = "",
    val serverBaseUrl: String = "",
    val intervalMinutes: Int = 60,
    val speedTestEnabled: Boolean = true,
)

expect class SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

expect class SecureStore {
    fun getToken(): String?
    fun setToken(token: String?)
}
