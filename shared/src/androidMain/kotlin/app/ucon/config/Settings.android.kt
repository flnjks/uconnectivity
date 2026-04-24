package app.ucon.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PLAIN_PREFS = "ucon_prefs"
private const val SECURE_PREFS = "ucon_secure"
private const val KEY_TOKEN = "bearer_token"

actual class SettingsStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)

    actual fun load(): AppSettings = AppSettings(
        siteId = prefs.getString("siteId", "") ?: "",
        serverBaseUrl = prefs.getString("serverBaseUrl", "") ?: "",
        intervalMinutes = prefs.getInt("intervalMinutes", 60),
        speedTestEnabled = prefs.getBoolean("speedTestEnabled", true),
    )

    actual fun save(settings: AppSettings) {
        prefs.edit()
            .putString("siteId", settings.siteId)
            .putString("serverBaseUrl", settings.serverBaseUrl)
            .putInt("intervalMinutes", settings.intervalMinutes)
            .putBoolean("speedTestEnabled", settings.speedTestEnabled)
            .apply()
    }
}

actual class SecureStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val secure = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    actual fun getToken(): String? = secure.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    actual fun setToken(token: String?) {
        secure.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
        }.apply()
    }
}
