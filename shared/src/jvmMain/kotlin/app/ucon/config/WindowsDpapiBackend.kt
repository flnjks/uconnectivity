package app.ucon.config

import com.sun.jna.Memory
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Windows DPAPI: encrypts the bearer token with a key derived from the user's
 * login credentials, stores the ciphertext on disk, and decrypts on read.
 * Survives reboots and won't decrypt for any other user account on the box.
 */
internal class WindowsDpapiBackend(private val file: File) : SecureStoreBackend {

    override fun read(): String? {
        if (!file.exists()) return null
        val cipher = file.readBytes()
        val plain = runCatching { Crypt32Util.cryptUnprotectData(cipher) }.getOrNull() ?: return null
        return String(plain, StandardCharsets.UTF_8).also {
            // Best effort: zero the plain bytes once we've copied them into a String.
            plain.fill(0)
        }
    }

    override fun write(token: String) {
        val plain = token.toByteArray(StandardCharsets.UTF_8)
        val cipher = Crypt32Util.cryptProtectData(plain)
        plain.fill(0)
        file.parentFile?.mkdirs()
        file.writeBytes(cipher)
        runCatching { file.setReadable(false, false); file.setReadable(true, true) }
    }

    override fun delete() {
        file.delete()
    }
}

@Suppress("unused")
private val keepImports: Array<Class<*>> = arrayOf(Memory::class.java) // satisfies linter for direct JNA usage in tests
