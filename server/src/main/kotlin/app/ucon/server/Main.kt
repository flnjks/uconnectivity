package app.ucon.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

class UconServer : CliktCommand(name = "ucon-server") {
    override fun run() = Unit
}

class Serve : CliktCommand(name = "serve") {
    private val port by option("--port").int().default(8080)
    private val host by option("--host").default("0.0.0.0")
    private val dbPath by option("--db").default("data/ucon.sqlite")

    override fun run() {
        Db.init(dbPath)
        embeddedServer(Netty, port = port, host = host) {
            module()
        }.start(wait = true)
    }
}

class AddSite : CliktCommand(name = "add-site") {
    private val label by argument()
    private val dbPath by option("--db").default("data/ucon.sqlite")

    override fun run() {
        Db.init(dbPath)
        val (siteId, token) = Admin.createSite(label)
        echo("site_id=$siteId")
        echo("token=$token")
        echo("")
        echo("Paste the token into the client's Settings screen. It will not be shown again.")
    }
}

class AdminCmd : CliktCommand(name = "admin") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    val cli = UconServer().subcommands(
        Serve(),
        AdminCmd().subcommands(AddSite()),
    )
    if (args.isEmpty()) {
        cli.main(arrayOf("serve"))
    } else {
        cli.main(args)
    }
}
