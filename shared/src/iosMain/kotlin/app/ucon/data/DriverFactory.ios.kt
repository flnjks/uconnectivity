package app.ucon.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.ucon.data.db.UconDb

actual class DriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(UconDb.Schema, "ucon.db")
}
