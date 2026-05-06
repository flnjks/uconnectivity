package app.ucon.config

/**
 * Cross-platform "launch at login" facade. Mobile platforms always return
 * [isSupported] = false (Android/iOS already control app lifecycle); desktop
 * platforms wire in to launchd / the Windows Startup folder.
 */
expect class AutoStartManager() {
    val isSupported: Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean): Boolean
}
