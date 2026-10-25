package io.nekohasekai.sagernet

import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.os.Bundle
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.bg.proto.TrafficUpdater
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.fmt.gson.GsonConverters
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.group.SubscriptionClient
import io.nekohasekai.sagernet.group.SubscriptionFetch
import io.nekohasekai.sagernet.group.subscriptionClient
import io.nekohasekai.sagernet.ktx.isOss
import io.nekohasekai.sagernet.ktx.isPlay
import io.nekohasekai.sagernet.ktx.isPreview
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import libcore.BoxInstance
import libcore.ExchangeContext
import moe.matsuri.nb4a.hevtun.HevTunRuntime
import moe.matsuri.nb4a.net.LocalResolverImpl
import moe.matsuri.nb4a.plugin.Plugins
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Batch5PureTest {
    @Before
    fun installStores() {
        DataStore.configurationStoreOverride = RoomPreferenceDataStore(MemoryKv())
        DataStore.profileCacheStoreOverride = RoomPreferenceDataStore(MemoryKv())
        DataStore.userIndexOverride = 0
    }

    @After
    fun clearStores() {
        DataStore.configurationStoreOverride = null
        DataStore.profileCacheStoreOverride = null
        DataStore.userIndexOverride = null
    }

    @Test
    fun gsonConvertersRoundTripEmptyAndValues() {
        assertEquals("", GsonConverters.toJson(emptyList<String>()))
        assertTrue(GsonConverters.toJson(listOf("a")).contains("a"))
        assertTrue(GsonConverters.toJson("x").contains("x"))
        assertTrue(GsonConverters.toList("").isEmpty())
        assertTrue(GsonConverters.toList(null).isEmpty())
        assertEquals("a", GsonConverters.toList("[\"a\"]")[0])
        assertTrue(GsonConverters.toSet(" ").isEmpty())
        assertTrue(GsonConverters.toSet("[\"a\"]").contains("a"))
    }

    @Test
    fun executableScansProcAndPluginsStayPure() {
        Executable.killAll()
        Executable.killAll(true)
        assertTrue(isOss)
        assertFalse(isPlay)
        assertFalse(isPreview)

        val info = PackageInfo()
        assertFalse(Plugins.isExe(info))
        info.providers = emptyArray()
        assertFalse(Plugins.isExe(info))
        val provider = ProviderInfo()
        info.providers = arrayOf(provider)
        assertFalse(Plugins.isExe(info))
        provider.authority = "io.nekohasekai.sagernet.plugin.obfs"
        assertTrue(Plugins.isExe(info))
        provider.authority = "moe.matsuri.exe.obfs"
        assertTrue(Plugins.isExe(info))
        provider.authority = "other"
        assertFalse(Plugins.isExe(info))
        assertEquals(Plugins.AUTHORITIES_PREFIX_NEKO_EXE, Plugins.preferExePrefix())
        assertEquals("SagerNet", Plugins.displayExeProvider("io.nekohasekai.sagernet.plugin.x"))
        assertEquals("Matsuri", Plugins.displayExeProvider("moe.matsuri.exe.x"))
        assertEquals("Unknown", Plugins.displayExeProvider("x"))
        assertNull(Plugins.getPlugin(""))
        assertNull(Plugins.getPluginExternal(""))
        assertFalse(Plugins.isUsingMatsuriExe(""))
        assertNull(PluginManager.init(""))
        provider.metaData = Bundle()
        assertNull(provider.loadString("missing"))
    }

    @Test
    fun hevConfigTrafficAndResolver() {
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.mixedUsername = "o'h"
        DataStore.ipv6Mode = IPv6Mode.PREFER
        DataStore.enableFakeDns = true
        DataStore.mtu = 1400
        val yaml = HevTunRuntime.buildConfig()
        assertTrue(yaml.contains("mtu: 1400"))
        assertTrue(yaml.contains("172.19.0.1"))
        assertTrue(yaml.contains("ipv6:"))
        assertTrue(yaml.contains("o''h"))
        assertTrue(yaml.contains("mapdns:"))
        assertTrue(yaml.contains("100.64.0.0"))
        DataStore.serviceMode = Key.MODE_PROXY
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        DataStore.enableFakeDns = false
        val plain = HevTunRuntime.buildConfig()
        assertFalse(plain.contains("ipv6:"))
        assertFalse(plain.contains("username:"))
        assertFalse(plain.contains("mapdns:"))
        assertFalse(HevTunRuntime.isRunning())
        HevTunRuntime.stop()

        val box = BoxInstance()
        val ignored = TrafficUpdater.TrafficLooperData(tag = "skip", ignore = true)
        val fresh = TrafficUpdater.TrafficLooperData(
            tag = "fresh",
            lastUpdate = System.currentTimeMillis() + 60_000,
        )
        val first = TrafficUpdater.TrafficLooperData(
            tag = "proxy",
            lastUpdate = System.currentTimeMillis() - 1_000,
        )
        val second = TrafficUpdater.TrafficLooperData(
            tag = "proxy",
            lastUpdate = System.currentTimeMillis() - 1_000,
        )
        TrafficUpdater(box, listOf(ignored, fresh, first, second)).updateAll()
        assertFalse(ignored.hasTrafficDelta)
        assertEquals(0L, fresh.rxRate)
        assertEquals(0L, first.rx)
        assertFalse(second.hasTrafficDelta)

        assertFalse(LocalResolverImpl.raw())
        assertEquals(0L, LocalResolverImpl.networkHandle())
        runBlocking {
            LocalResolverImpl.lookup(ExchangeContext(), "ip", "localhost")
            LocalResolverImpl.lookup(ExchangeContext(), "ip", "no-such-host.invalid")
            delay(1500)
        }
        assertNotNull(TrafficData())
    }

    @Test
    fun parseRawRejectsANonListProxyField() {
        val previous = subscriptionClient
        subscriptionClient = object : SubscriptionClient {
            override fun fetch(subscription: SubscriptionBean) = SubscriptionFetch("", "", "")
            override fun readContent(link: String): String? = null
            override fun noProxiesFound() = "none"
            override fun noProxiesInSubscription() = "sub"
            override fun noProxiesFoundInFile() = "not a list"
        }
        try {
            runBlocking {
                try {
                    RawUpdater.parseRaw("proxies: 1\n")
                    org.junit.Assert.fail("expected illegal state")
                } catch (e: IllegalStateException) {
                    assertTrue(e.message!!.contains("not a list"))
                }
            }
        } finally {
            subscriptionClient = previous
        }
        assertNotNull(ProxyGroup())
    }
}

private class MemoryKv : io.nekohasekai.sagernet.database.preference.KeyValuePair.Dao {
    private val rows = linkedMapOf<String, io.nekohasekai.sagernet.database.preference.KeyValuePair>()
    override fun all() = rows.values.toList()
    override fun get(key: String) = rows[key]
    override fun put(value: io.nekohasekai.sagernet.database.preference.KeyValuePair): Long {
        rows[value.key] = value
        return rows.size.toLong()
    }

    override fun delete(key: String) = if (rows.remove(key) != null) 1 else 0
    override fun reset(): Int {
        val size = rows.size
        rows.clear()
        return size
    }

    override fun insert(list: List<io.nekohasekai.sagernet.database.preference.KeyValuePair>) {
        list.forEach { put(it) }
    }
}
