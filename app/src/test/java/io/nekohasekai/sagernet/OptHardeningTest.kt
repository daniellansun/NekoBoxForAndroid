package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.MemoryKvDao
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.isGitHubHttpsUrl
import io.nekohasekai.sagernet.ktx.isHttpOrHttpsUrl
import io.nekohasekai.sagernet.ktx.redactUrlUserInfo
import io.nekohasekai.sagernet.utils.formatThrowableReport
import io.nekohasekai.sagernet.utils.isSensitiveSettingKey
import io.nekohasekai.sagernet.utils.settingReportLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptHardeningTest {
    @Test
    fun throwableReportKeepsOrdinaryChainsAndStopsCycles() {
        val ordinary = IllegalStateException("outer", RuntimeException("inner"))
        val text = formatThrowableReport(ordinary)
        assertTrue(text.startsWith("java.lang.IllegalStateException: outer"))
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: inner"))
        assertFalse(text.contains("cyclic cause"))

        val first = RuntimeException("first")
        val second = RuntimeException("second")
        first.initCause(second)
        second.initCause(first)
        val cyclic = formatThrowableReport(first)
        assertTrue(cyclic.contains("Caused by: java.lang.RuntimeException: second"))
        assertTrue(cyclic.contains("cyclic cause"))
    }

    @Test
    fun crashReportRedactsSecretsAndUrlUserInfo() {
        assertTrue(isSensitiveSettingKey("webdavPassword"))
        assertTrue(isSensitiveSettingKey("mixedSecret"))
        assertTrue(isSensitiveSettingKey(Key.GLOBAL_CUSTOM_CONFIG))
        assertFalse(isSensitiveSettingKey("serviceMode"))
        assertEquals("webdavPassword: [redacted]", settingReportLine("webdavPassword", "s3cret"))
        assertEquals(
            "remoteDns: https://dns.example/dns-query",
            settingReportLine("remoteDns", "https://token:s3cret@dns.example/dns-query"),
        )
        assertEquals("serviceMode: vpn", settingReportLine("serviceMode", "vpn"))
        assertEquals(
            "https://dav.example/backup",
            redactUrlUserInfo("https://alice:s3cret@dav.example/backup"),
        )
    }

    @Test
    fun httpChecksAllowExistingUrlsAndRejectOtherSchemes() {
        assertTrue(isHttpOrHttpsUrl("https://github.com/SagerNet/sing-geosite/releases/latest/download/geosite.db"))
        assertTrue(isHttpOrHttpsUrl(" http://127.0.0.1:9090/ui "))
        assertTrue(isHttpOrHttpsUrl("http://example.com/sub"))
        assertFalse(isHttpOrHttpsUrl("file:///sdcard/sub.txt"))
        assertFalse(isHttpOrHttpsUrl("javascript:alert(1)"))
        assertFalse(isHttpOrHttpsUrl("content://config/body"))
        assertFalse(isHttpOrHttpsUrl("http://"))
        assertFalse(isHttpOrHttpsUrl(""))

        assertTrue(isGitHubHttpsUrl("https://github.com/starifly/NekoBoxForAndroid/releases/tag/1"))
        assertTrue(isGitHubHttpsUrl("https://www.github.com/starifly/NekoBoxForAndroid"))
        assertFalse(isGitHubHttpsUrl("http://github.com/starifly/NekoBoxForAndroid/releases/tag/1"))
        assertFalse(isGitHubHttpsUrl("https://evil.example/github.com"))
        assertFalse(isGitHubHttpsUrl("https://github.com.evil/repo"))
    }

    @Test
    fun unsetPreferenceFallbacksMatchTheSettingsScreen() {
        val config = RoomPreferenceDataStore(MemoryKvDao())
        val cache = RoomPreferenceDataStore(MemoryKvDao())
        val previousConfig = DataStore.configurationStoreOverride
        val previousCache = DataStore.profileCacheStoreOverride
        DataStore.configurationStoreOverride = config
        DataStore.profileCacheStoreOverride = cache
        try {
            assertEquals(1000, DataStore.speedInterval)
            assertEquals(
                "https://github.com/SagerNet/sing-geosite/releases/latest/download/geosite.db",
                DataStore.rulesGeositeUrl,
            )
            assertEquals(
                "https://github.com/SagerNet/sing-geoip/releases/latest/download/geoip.db",
                DataStore.rulesGeoipUrl,
            )
            DataStore.speedInterval = 0
            assertEquals(0, DataStore.speedInterval)
            DataStore.rulesGeositeUrl = "https://example.com/geosite.db"
            assertEquals("https://example.com/geosite.db", DataStore.rulesGeositeUrl)
        } finally {
            DataStore.configurationStoreOverride = previousConfig
            DataStore.profileCacheStoreOverride = previousCache
        }
    }
}
