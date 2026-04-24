package app.ucon.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.ucon.data.db.UconDb
import java.io.File

actual class DriverFactory(private val dbFile: File) {
    actual fun create(): SqlDriver {
        dbFile.parentFile?.mkdirs()
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        val driver = JdbcSqliteDriver(url)
        UconDb.Schema.create(driver)
        return driver
    }
}
