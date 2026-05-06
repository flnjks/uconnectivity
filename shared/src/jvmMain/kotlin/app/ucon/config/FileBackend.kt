package app.ucon.config

import java.io.File

/**
 * Plain-file fallback. The token sits in [file] with 0600 perms. Used on
 * Linux until a libsecret backend lands.
 */
internal class FileBackend(private val file: File) : SecureStoreBackend {

    override fun read(): String? =
        if (file.exists()) file.readText().trim().ifEmpty { null } else null

    override fun write(token: String) {
        file.parentFile?.mkdirs()
        file.writeText(token)
        runCatching { file.setReadable(false, false); file.setReadable(true, true) }
        runCatching { file.setWritable(false, false); file.setWritable(true, true) }
    }

    override fun delete() {
        file.delete()
    }
}
