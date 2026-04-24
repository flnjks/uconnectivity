package app.ucon.measure

import kotlinx.serialization.Serializable

@Serializable
data class Target(
    val label: String,
    val host: String,
    val port: Int = 443,
) {
    companion object {
        val DEFAULTS: List<Target> = listOf(
            Target(label = "Cloudflare", host = "1.1.1.1", port = 443),
            Target(label = "Google DNS", host = "8.8.8.8", port = 443),
            Target(label = "Google", host = "dns.google", port = 443),
        )
    }
}
