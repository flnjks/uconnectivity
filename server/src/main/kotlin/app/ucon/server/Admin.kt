package app.ucon.server

import de.mkammerer.argon2.Argon2Factory
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.Base64

object Admin {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private val rng = SecureRandom()

    fun createSite(label: String): Pair<String, String> {
        val siteId = label.lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .trim('-')
            .ifEmpty { "site" }
            .let { base ->
                val suffix = randomSuffix(6)
                "$base-$suffix"
            }
        val token = randomToken()
        val hash = argon2.hash(2, 65536, 1, token.toCharArray())

        transaction {
            Sites.insert {
                it[Sites.siteId] = siteId
                it[Sites.label] = label
                it[Sites.tokenHash] = hash
                it[Sites.createdAt] = System.currentTimeMillis()
            }
        }
        return siteId to token
    }

    /** Validates a bearer token and returns the matching site id, or null. */
    fun authenticate(token: String): String? = transaction {
        val rows = Sites.selectAll().toList()
        for (row in rows) {
            val hash = row[Sites.tokenHash]
            if (argon2.verify(hash, token.toCharArray())) {
                return@transaction row[Sites.siteId]
            }
        }
        null
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        rng.nextBytes(bytes)
        return "ucon_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun randomSuffix(n: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString {
            repeat(n) { append(alphabet[rng.nextInt(alphabet.length)]) }
        }
    }
}
