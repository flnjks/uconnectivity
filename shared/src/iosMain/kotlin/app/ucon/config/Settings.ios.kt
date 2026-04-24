package app.ucon.config

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSData
import platform.darwin.OSStatus

actual class SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun load(): AppSettings = AppSettings(
        siteId = defaults.stringForKey("siteId").orEmpty(),
        serverBaseUrl = defaults.stringForKey("serverBaseUrl").orEmpty(),
        intervalMinutes = (defaults.integerForKey("intervalMinutes").toInt()).takeIf { it > 0 } ?: 60,
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
 * Bearer token stored in NSUserDefaults for v1 simplicity.
 *
 * Production: move to Keychain via Security.framework. Keychain bindings from
 * Kotlin/Native require a handful of CFRelease dance; left as a follow-up.
 */
actual class SecureStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "ucon_bearer_token"

    actual fun getToken(): String? = defaults.stringForKey(key)?.takeIf { it.isNotBlank() }

    actual fun setToken(token: String?) {
        if (token.isNullOrBlank()) defaults.removeObjectForKey(key)
        else defaults.setObject(token, key)
        defaults.synchronize()
    }
}
