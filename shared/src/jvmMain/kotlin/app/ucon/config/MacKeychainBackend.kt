package app.ucon.config

import java.io.IOException

private const val SERVICE = "app.ucon"
private const val ACCOUNT = "bearer_token"
private const val SECURITY_BIN = "/usr/bin/security"

/**
 * macOS Keychain via the `security` CLI — no extra deps, no entitlement
 * fights. The first call will prompt the user for permission to access
 * the entry; once allowed (and "Always Allow" picked), subsequent reads
 * are silent.
 */
internal class MacKeychainBackend : SecureStoreBackend {

    override fun read(): String? {
        val (rc, out, _) = run(SECURITY_BIN, "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w")
        if (rc != 0) return null
        return out.trim().ifBlank { null }
    }

    override fun write(token: String) {
        // `-U` updates if it already exists, adds otherwise.
        // `-A` lets any application read the item without an access prompt — pragmatic
        // for unsigned dev builds. Once the .app is code-signed under a stable Developer ID,
        // swap this for explicit `-T /Applications/uConnectivity.app/Contents/MacOS/uConnectivity`.
        val (rc, _, err) = run(
            SECURITY_BIN, "add-generic-password",
            "-s", SERVICE, "-a", ACCOUNT,
            "-w", token,
            "-U",
            "-A",
        )
        if (rc != 0) {
            throw IOException("security add-generic-password failed (rc=$rc): $err")
        }
    }

    override fun delete() {
        run(SECURITY_BIN, "delete-generic-password", "-s", SERVICE, "-a", ACCOUNT)
        // Ignore rc — "item not found" is the expected outcome of clearing twice.
    }

    private fun run(vararg cmd: String): Triple<Int, String, String> {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(false).start()
        val out = proc.inputStream.bufferedReader().readText()
        val err = proc.errorStream.bufferedReader().readText()
        val rc = proc.waitFor()
        return Triple(rc, out, err)
    }
}
