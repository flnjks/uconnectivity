package app.ucon.config

import java.io.File
import java.util.Properties

/** JVM desktop uses a properties file in the user config dir. */
actual class SettingsStore(private val file: File) {
    actual fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        val p = Properties().apply { file.inputStream().use { load(it) } }
        return AppSettings(
            siteId = p.getProperty("siteId", ""),
            serverBaseUrl = p.getProperty("serverBaseUrl", ""),
            intervalMinutes = p.getProperty("intervalMinutes")?.toIntOrNull() ?: 60,
            speedTestEnabled = p.getProperty("speedTestEnabled", "true").toBooleanStrict(),
        )
    }

    actual fun save(settings: AppSettings) {
        file.parentFile?.mkdirs()
        val p = Properties().apply {
            setProperty("siteId", settings.siteId)
            setProperty("serverBaseUrl", settings.serverBaseUrl)
            setProperty("intervalMinutes", settings.intervalMinutes.toString())
            setProperty("speedTestEnabled", settings.speedTestEnabled.toString())
        }
        file.outputStream().use { p.store(it, "uconnectivity settings") }
    }
}

/**
 * Desktop placeholder for SecureStore. Real implementations:
 *   - macOS: Keychain via `security` CLI or JNA
 *   - Windows: DPAPI via JNA
 * For v1 this uses a file with 0600 perms. Upgrade before production use.
 */
actual class SecureStore(private val file: File) {
    actual fun getToken(): String? = if (file.exists()) file.readText().trim().ifEmpty { null } else null

    actual fun setToken(token: String?) {
        if (token.isNullOrBlank()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(token)
        runCatching { file.setReadable(false, false); file.setReadable(true, true) }
        runCatching { file.setWritable(false, false); file.setWritable(true, true) }
    }
}
