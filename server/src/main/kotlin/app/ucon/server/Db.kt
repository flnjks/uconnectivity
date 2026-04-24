package app.ucon.server

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.Connection

object Sites : LongIdTable("sites") {
    val siteId = varchar("site_id", 64).uniqueIndex()
    val label = varchar("label", 255)
    val tokenHash = text("token_hash")
    val createdAt = long("created_at")
}

object Runs : LongIdTable("runs") {
    val siteId = varchar("site_id", 64).index()
    val ts = long("ts")
    val durationMs = long("duration_ms")
    val json = text("json")
    val clientVersion = varchar("client_version", 64)
    // Rollup columns for quick queries without JSON parsing.
    val avgLatencyMs = double("avg_latency_ms").nullable()
    val lossPct = double("loss_pct").nullable()
    val downMbps = double("down_mbps").nullable()
    val upMbps = double("up_mbps").nullable()
}

object Db {
    fun init(path: String) {
        File(path).parentFile?.mkdirs()
        val url = "jdbc:sqlite:$path"
        Database.connect(url, driver = "org.sqlite.JDBC")
        // SQLite requires serializable for writes in Exposed; use SERIALIZABLE.
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        transaction {
            SchemaUtils.create(Sites, Runs)
        }
    }
}
