package app.ucon.measure

import app.ucon.net.newHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hits the live Cloudflare endpoints. Requires network access — disabled by
 * default. Run manually with `./gradlew :shared:jvmTest --tests CloudflareSpeedProbeIT`
 * after removing the @Ignore.
 */
class CloudflareSpeedProbeIT {
    @Test @Ignore
    fun cloudflare_endpoints_return_real_numbers() = runBlocking {
        val client = newHttpClient()
        val probe = SpeedProbe(client)
        // Small payload so the test stays under a few seconds.
        val report = probe.measure(SpeedEndpoint.Cloudflare, testSizeBytes = 1L * 1024 * 1024)
        println("Cloudflare result: down=${report.downMbps} Mbps  up=${report.upMbps} Mbps  size=${report.testSizeBytes}B")
        assertNotNull(report.downMbps, "download Mbps should not be null")
        assertNotNull(report.upMbps, "upload Mbps should not be null")
        assertTrue(report.downMbps!! > 0.1, "download should be > 0.1 Mbps on a working network")
        assertTrue(report.upMbps!! > 0.1, "upload should be > 0.1 Mbps on a working network")
    }
}
