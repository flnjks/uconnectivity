package app.ucon.config

actual class AutoStartManager {
    actual val isSupported: Boolean = false
    actual fun isEnabled(): Boolean = false
    actual fun setEnabled(enabled: Boolean): Boolean = false
}
