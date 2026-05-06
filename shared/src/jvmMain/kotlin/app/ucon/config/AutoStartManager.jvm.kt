package app.ucon.config

import java.io.File

/**
 * Desktop launch-at-login. Implementation is co-located with the desktop
 * Compose app — this actual class delegates to functions defined in
 * `composeApp/jvmMain/AutoStart.kt` via reflection so the shared module
 * doesn't need to depend on the desktop app.
 *
 * If reflection fails (e.g. unit-test context with no desktop on the
 * classpath), every method returns false / unsupported gracefully.
 */
actual class AutoStartManager {
    private val impl: Impl? = run {
        val cls = runCatching { Class.forName("app.ucon.desktop.AutoStart") }.getOrNull()
            ?: return@run null
        Impl(cls)
    }

    actual val isSupported: Boolean
        get() = impl?.isSupported() ?: false

    actual fun isEnabled(): Boolean = impl?.isEnabled() ?: false

    actual fun setEnabled(enabled: Boolean): Boolean = impl?.setEnabled(enabled) ?: false

    private class Impl(private val cls: Class<*>) {
        private val instance: Any = cls.getField("INSTANCE").get(null)
        fun isSupported(): Boolean =
            cls.getMethod("isSupported").invoke(instance) as Boolean
        fun isEnabled(): Boolean =
            cls.getMethod("isEnabled").invoke(instance) as Boolean
        fun setEnabled(enabled: Boolean): Boolean =
            cls.getMethod("setEnabled", Boolean::class.javaPrimitiveType).invoke(instance, enabled) as Boolean
    }
}
