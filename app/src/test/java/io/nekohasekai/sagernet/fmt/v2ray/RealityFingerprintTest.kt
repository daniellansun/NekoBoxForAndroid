package io.nekohasekai.sagernet.fmt.v2ray

import io.nekohasekai.sagernet.fmt.MemoryConfigSource
import io.nekohasekai.sagernet.fmt.withConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealityFingerprintTest {
    @Test
    fun hintOnlyWhenRealityUsesAndroidOr360() {
        assertFalse(realityFingerprintUnsupported(null, "android"))
        assertFalse(realityFingerprintUnsupported("", "android"))
        assertFalse(realityFingerprintUnsupported("   ", "360"))
        assertFalse(realityFingerprintUnsupported("pub", null))
        assertFalse(realityFingerprintUnsupported("pub", ""))
        assertFalse(realityFingerprintUnsupported("pub", "   "))

        assertTrue(realityFingerprintUnsupported("pub", "android"))
        assertTrue(realityFingerprintUnsupported("pub", "Android"))
        assertTrue(realityFingerprintUnsupported("pub", " android "))
        assertTrue(realityFingerprintUnsupported("pub", "360"))
        assertTrue(realityFingerprintUnsupported("pub", "360 "))
        assertTrue(realityFingerprintUnsupported(" pub ", "ANDROID"))

        listOf(
            "chrome",
            "firefox",
            "edge",
            "safari",
            "qq",
            "ios",
            "random",
            "randomized",
            "android11",
            "360browser",
        ).forEach { fingerprint ->
            assertFalse(fingerprint, realityFingerprintUnsupported("pub", fingerprint))
        }
    }

    @Test
    fun savedFingerprintIsNotRewritten() {
        val fingerprint = "android"
        assertTrue(realityFingerprintUnsupported("pub", fingerprint))
        assertEquals("android", fingerprint)

        val bean = VMessBean().apply {
            initializeDefaultValues()
            security = "tls"
            utlsFingerprint = "android"
            realityPubKey = "pub"
            realityShortId = "ab"
        }
        withConfig(MemoryConfigSource()) {
            val tls = buildSingBoxOutboundTLS(bean)!!
            assertEquals("android", tls.utls.fingerprint)
            assertEquals(true, tls.reality.enabled)
            assertEquals("pub", tls.reality.public_key)
        }
        assertEquals("android", bean.utlsFingerprint)
        assertEquals("pub", bean.realityPubKey)
    }
}
