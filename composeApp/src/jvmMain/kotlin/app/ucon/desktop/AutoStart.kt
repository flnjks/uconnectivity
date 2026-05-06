package app.ucon.desktop

import java.io.File

/**
 * Cross-platform "launch at login" toggle.
 *
 *  - macOS  → a per-user launchd `LaunchAgent` plist in `~/Library/LaunchAgents/`.
 *             The plist points at the bundled `.app`'s launcher executable
 *             (or, if running unbundled, at the JVM `java` binary plus the JAR).
 *             Loaded via `launchctl bootstrap` / unloaded via `launchctl bootout`.
 *  - Windows → drops a `.lnk` shortcut into the user's Startup folder using
 *             a small VBScript snippet (no extra deps).
 *  - Other  → a no-op; toggle simply doesn't appear in Settings.
 */
object AutoStart {
    private val OS = System.getProperty("os.name").lowercase()
    val isSupported: Boolean = OS.contains("mac") || OS.contains("win")

    fun isEnabled(): Boolean = when {
        OS.contains("mac") -> macPlist().exists()
        OS.contains("win") -> winShortcut().exists()
        else -> false
    }

    fun setEnabled(enabled: Boolean): Boolean = when {
        OS.contains("mac") -> if (enabled) installMac() else uninstallMac()
        OS.contains("win") -> if (enabled) installWin() else uninstallWin()
        else -> false
    }

    // -------------------------------- macOS --------------------------------

    private const val LABEL = "app.ucon.uconnectivity"

    private fun macPlist(): File =
        File(System.getProperty("user.home"), "Library/LaunchAgents/$LABEL.plist")

    private fun installMac(): Boolean {
        val launcher = locateLauncher() ?: return false
        val plist = macPlist()
        plist.parentFile?.mkdirs()
        plist.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
        ${launcher.programArgumentsXml()}
    </array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><false/>
    <key>StandardOutPath</key><string>${'$'}HOME/Library/Logs/uConnectivity.log</string>
    <key>StandardErrorPath</key><string>${'$'}HOME/Library/Logs/uConnectivity.log</string>
</dict>
</plist>
            """.trimIndent(),
        )
        // launchctl bootstrap is idempotent-ish; bootout first to be safe.
        runQuiet("/bin/launchctl", "bootout", "gui/${uid()}/$LABEL")
        runQuiet("/bin/launchctl", "bootstrap", "gui/${uid()}", plist.absolutePath)
        return true
    }

    private fun uninstallMac(): Boolean {
        runQuiet("/bin/launchctl", "bootout", "gui/${uid()}/$LABEL")
        macPlist().delete()
        return true
    }

    // -------------------------------- Windows --------------------------------

    private fun winShortcut(): File {
        val startup = File(System.getenv("APPDATA") ?: System.getProperty("user.home"))
            .resolve("Microsoft/Windows/Start Menu/Programs/Startup")
        return startup.resolve("uConnectivity.lnk")
    }

    private fun installWin(): Boolean {
        val launcher = locateLauncher() ?: return false
        val target = launcher.executablePath ?: return false
        val shortcut = winShortcut()
        shortcut.parentFile?.mkdirs()
        // Use VBScript via wscript.exe — bundled with Windows, no PowerShell exec policy issues.
        val vbs = File.createTempFile("ucon-startup-", ".vbs")
        vbs.writeText(
            """
            Set sh = WScript.CreateObject("WScript.Shell")
            Set lnk = sh.CreateShortcut("${shortcut.absolutePath.replace("\\", "\\\\")}")
            lnk.TargetPath = "${target.replace("\\", "\\\\")}"
            lnk.WorkingDirectory = "${File(target).parent?.replace("\\", "\\\\") ?: ""}"
            lnk.WindowStyle = 7
            lnk.Save
            """.trimIndent()
        )
        return try {
            val rc = ProcessBuilder("wscript", vbs.absolutePath).inheritIO().start().waitFor()
            rc == 0
        } finally {
            vbs.delete()
        }
    }

    private fun uninstallWin(): Boolean {
        winShortcut().delete()
        return true
    }

    // ----------------------------- Helpers -----------------------------

    private data class Launcher(val args: List<String>, val executablePath: String? = args.firstOrNull()) {
        fun programArgumentsXml(): String =
            args.joinToString("\n        ") { "<string>${it.escapeXml()}</string>" }
    }

    /** Resolve how to invoke this app at login: from a packaged .app, or via `java`. */
    private fun locateLauncher(): Launcher? {
        // 1. Packaged macOS .app — Compose Desktop sets compose.application.resources.dir
        //    inside Contents/app/resources/. Walk up to Contents/MacOS/<launcher>.
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (resourcesDir != null) {
            val contents = File(resourcesDir).parentFile?.parentFile  // .../Contents/app/resources -> .../Contents
            val macos = contents?.resolve("MacOS")
            val launcher = macos?.listFiles()?.firstOrNull { it.canExecute() && !it.name.startsWith(".") }
            if (launcher != null) return Launcher(args = listOf(launcher.absolutePath))
        }
        // 2. Packaged Windows app — bundled exe sits next to the .jar.
        val home = System.getProperty("compose.application.dir")
            ?: System.getProperty("user.dir")
        val winExe = File(home, "uConnectivity.exe")
        if (winExe.exists()) return Launcher(args = listOf(winExe.absolutePath))

        // 3. Development run via `gradle run`: invoke the JVM with the same classpath.
        val javaHome = System.getProperty("java.home") ?: return null
        val javaBin = File(javaHome, "bin/java").takeIf { it.canExecute() } ?: return null
        val mainClass = "app.ucon.desktop.MainKt"
        val classpath = System.getProperty("java.class.path") ?: return null
        return Launcher(args = listOf(javaBin.absolutePath, "-cp", classpath, mainClass))
    }

    private fun uid(): String =
        ProcessBuilder("/usr/bin/id", "-u").start()
            .inputStream.bufferedReader().readText().trim()

    private fun runQuiet(vararg cmd: String) {
        runCatching {
            ProcessBuilder(*cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor()
        }
    }

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
}
