package app.ucon.server

import de.mkammerer.argon2.Argon2Factory
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.Base64

data class SiteRow(val siteId: String, val label: String, val createdAt: Long)

object Admin {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private val rng = SecureRandom()

    /** Provision a new site. Returns (siteId, plaintext-token). */
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

    /** Lists every provisioned site (no tokens — those are hashed). */
    fun listSites(): List<SiteRow> = transaction {
        Sites.selectAll().orderBy(Sites.createdAt, SortOrder.ASC).map {
            SiteRow(
                siteId = it[Sites.siteId],
                label = it[Sites.label],
                createdAt = it[Sites.createdAt],
            )
        }
    }

    /** Returns the new plaintext token, or null if [siteId] doesn't exist. */
    fun rotateToken(siteId: String): String? = transaction {
        val exists = Sites.selectAll().where { Sites.siteId eq siteId }.count() > 0
        if (!exists) return@transaction null
        val token = randomToken()
        val hash = argon2.hash(2, 65536, 1, token.toCharArray())
        Sites.update({ Sites.siteId eq siteId }) {
            it[Sites.tokenHash] = hash
        }
        token
    }

    /**
     * Removes a site and all its runs. Returns true if the site existed.
     * After this, the site's bearer token will no longer authenticate
     * (no token rows remain) and all historical run rows for the site
     * are dropped.
     */
    fun deleteSite(siteId: String): Boolean = transaction {
        val deleted = Sites.deleteWhere { Sites.siteId eq siteId }
        if (deleted > 0) {
            Runs.deleteWhere { Runs.siteId eq siteId }
        }
        deleted > 0
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
