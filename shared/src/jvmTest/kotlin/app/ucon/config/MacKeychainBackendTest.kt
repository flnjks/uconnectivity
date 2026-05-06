package app.ucon.config

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacKeychainBackendTest {
    private val isMac = System.getProperty("os.name").lowercase().contains("mac")
    private val backend = MacKeychainBackend()

    @AfterTest
    fun cleanup() {
        if (isMac) backend.delete()
    }

    @Test
    fun roundtrip_through_security_cli() {
        if (!isMac) return  // skip on non-Mac CI
        backend.delete()                              // start clean
        assertNull(backend.read(), "fresh keychain should have no entry")

        backend.write("ucon_test_value_42")
        assertEquals("ucon_test_value_42", backend.read())

        backend.write("ucon_test_value_43")           // upsert path
        assertEquals("ucon_test_value_43", backend.read())

        backend.delete()
        assertNull(backend.read(), "deleted entry should be gone")
    }
}
