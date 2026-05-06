package app.ucon.config

import platform.Foundation.NSUserDefaults

actual class SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun load(): AppSettings = AppSettings(
        siteId = defaults.stringForKey("siteId").orEmpty(),
        serverBaseUrl = defaults.stringForKey("serverBaseUrl").orEmpty(),
        intervalMinutes = defaults.integerForKey("intervalMinutes").toInt().takeIf { it > 0 } ?: 60,
        speedTestEnabled = if (defaults.objectForKey("speedTestEnabled") == null) true
                           else defaults.boolForKey("speedTestEnabled"),
    )

    actual fun save(settings: AppSettings) {
        defaults.setObject(settings.siteId, "siteId")
        defaults.setObject(settings.serverBaseUrl, "serverBaseUrl")
        defaults.setInteger(settings.intervalMinutes.toLong(), "intervalMinutes")
        defaults.setBool(settings.speedTestEnabled, "speedTestEnabled")
        defaults.synchronize()
    }
}

/**
 * Bearer token in the iOS Keychain. The actual `Security.framework` calls live
 * on the Swift side (`iosApp/iosApp/Keychain.swift`) — this class delegates
 * through three function references that the consumer (composeApp/iosMain
 * `AppContainer`) wires up at construction time.
 *
 * When the function references are no-ops (e.g. unit-test context with no
 * Swift wiring), reads/writes transparently fall back to NSUserDefaults so
 * the app keeps working.
 *
 * Includes a one-time migration from the v1 NSUserDefaults bearer-token key
 * into the Keychain on first read.
 */
actual class SecureStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val delete: () -> Unit,
) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getToken(): String? {
        read()?.takeIf { it.isNotBlank() }?.let { return it }
        // One-time migration from v1 NSUserDefaults storage.
        val legacy = defaults.stringForKey(LEGACY_KEY)?.takeIf { it.isNotBlank() } ?: return null
        write(legacy)
        defaults.removeObjectForKey(LEGACY_KEY)
        defaults.synchronize()
        return legacy
    }

    actual fun setToken(token: String?) {
        if (token.isNullOrBlank()) delete() else write(token)
    }

    private companion object {
        const val LEGACY_KEY = "ucon_bearer_token"
    }
}
