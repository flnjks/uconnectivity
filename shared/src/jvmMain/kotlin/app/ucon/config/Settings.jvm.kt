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
 * Bearer-token storage. Detects the host OS at construction time and picks
 * the strongest available backend:
 *
 *  - macOS  → Keychain via `/usr/bin/security` CLI (works for any signed or
 *             unsigned build, no extra entitlements).
 *  - Windows → DPAPI via JNA (`Crypt32.CryptProtectData`/`CryptUnprotectData`).
 *             Stores the protected ciphertext on disk under
 *             `%APPDATA%/uConnectivity/token.dpapi`.
 *  - Other  → 0600-mode flat file at [fallbackFile]. Better than nothing on
 *             Linux until libsecret support lands.
 *
 * Has a one-time migration: if the legacy plaintext file at [fallbackFile]
 * exists at startup, its contents are pushed into the OS-native store and
 * the file is deleted.
 */
actual class SecureStore(private val fallbackFile: File) {
    private val backend: SecureStoreBackend = run {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> MacKeychainBackend()
            os.contains("win") -> WindowsDpapiBackend(fallbackFile.parentFile.resolve("token.dpapi"))
            else -> FileBackend(fallbackFile)
        }
    }

    init {
        // One-time migration from the v1 plaintext file.
        if (backend !is FileBackend && fallbackFile.exists()) {
            val legacy = runCatching { fallbackFile.readText().trim() }.getOrNull()
            if (!legacy.isNullOrBlank()) {
                runCatching { backend.write(legacy) }
                fallbackFile.delete()
            } else {
                fallbackFile.delete()
            }
        }
    }

    actual fun getToken(): String? = runCatching { backend.read() }.getOrNull()?.takeIf { it.isNotBlank() }

    actual fun setToken(token: String?) {
        if (token.isNullOrBlank()) runCatching { backend.delete() }
        else runCatching { backend.write(token) }
    }
}

internal interface SecureStoreBackend {
    fun read(): String?
    fun write(token: String)
    fun delete()
}
