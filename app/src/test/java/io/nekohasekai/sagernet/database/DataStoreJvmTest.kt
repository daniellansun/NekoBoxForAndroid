package io.nekohasekai.sagernet.database

import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.fmt.ProductionConfigSource
import io.nekohasekai.sagernet.group.ProductionSubscriptionClient
import io.nekohasekai.sagernet.ktx.boolean
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.long
import io.nekohasekai.sagernet.ktx.string
import io.nekohasekai.sagernet.ktx.stringSet
import io.nekohasekai.sagernet.ktx.stringToInt
import io.nekohasekai.sagernet.ktx.stringToIntIfExists
import io.nekohasekai.sagernet.ktx.stringToLong
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.Type
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreJvmTest {
    private lateinit var configDao: MemoryKvDao
    private lateinit var cacheDao: MemoryKvDao
    private lateinit var groups: MemoryGroups

    @Before
    fun installMemoryStores() {
        configDao = MemoryKvDao()
        cacheDao = MemoryKvDao()
        groups = MemoryGroups()
        DataStore.configurationStoreOverride = RoomPreferenceDataStore(configDao)
        DataStore.profileCacheStoreOverride = RoomPreferenceDataStore(cacheDao)
        DataStore.userIndexOverride = 0
        DataStore.serviceState = BaseService.State.Idle
        DataStore.mixedInboundAuthed = false
        groupDirectory = groups
    }

    @After
    fun restoreProductionStores() {
        DataStore.configurationStoreOverride = null
        DataStore.profileCacheStoreOverride = null
        DataStore.userIndexOverride = null
        DataStore.serviceState = BaseService.State.Idle
        DataStore.mixedInboundAuthed = false
        groupDirectory = RoomGroupDirectory
    }

    @Test
    fun defaultsGroupsPortsAndRoundTrip() {
        assertEquals(Key.MODE_VPN, DataStore.serviceMode)
        assertTrue(DataStore.confirmProfileDelete)
        assertTrue(DataStore.networkChangeResetConnections)
        assertEquals("https://dns.google/dns-query", DataStore.remoteDns)
        assertEquals(9000, DataStore.mtu)
        assertFalse(DataStore.globalMode)

        groups.rows.clear()
        assertEquals(1L, DataStore.currentGroupId())
        val created = groups.rows.single()
        assertTrue(created.ungrouped)
        assertEquals(1L, DataStore.currentGroup().id)
        assertEquals(1L, DataStore.selectedGroupForImport())

        DataStore.configurationStore.remove(Key.PROFILE_GROUP)
        groups.rows.clear()
        groups.rows += ProxyGroup(id = 4L, name = "first")
        assertEquals(4L, DataStore.currentGroupId())
        assertEquals(4L, DataStore.selectedGroup)

        groups.rows.clear()
        groups.rows += ProxyGroup(id = 9L, type = GroupType.SUBSCRIPTION, name = "sub")
        groups.rows += ProxyGroup(id = 8L, type = GroupType.BASIC, name = "basic")
        DataStore.configurationStore.putLong(Key.PROFILE_GROUP, 99L)
        assertEquals(99L, DataStore.currentGroupId())
        assertEquals(9L, DataStore.currentGroup().id)
        assertEquals(8L, DataStore.selectedGroupForImport())
        DataStore.selectedGroup = 8L
        assertEquals(8L, DataStore.selectedGroupForImport())

        DataStore.configurationStore.remove(Key.PROFILE_GROUP)
        groups.rows.clear()
        val fresh = DataStore.currentGroup()
        assertTrue(fresh.ungrouped)
        assertTrue(fresh.id > 0L)

        DataStore.userIndexOverride = 4
        DataStore.configurationStore.remove(Key.MIXED_PORT)
        assertEquals(2084, DataStore.mixedPort)
        DataStore.initGlobal()
        DataStore.userIndexOverride = 9
        assertEquals(2084, DataStore.mixedPort)
        DataStore.initGlobal()
        DataStore.mixedPort = 4000
        assertEquals(4000, DataStore.mixedPort)
        val secret = DataStore.mixedSecret
        assertEquals(secret, DataStore.mixedSecret)
        assertTrue(secret.isNotEmpty())

        DataStore.serviceMode = Key.MODE_VPN
        DataStore.appendHttpProxy = false
        assertTrue(DataStore.mixedInboundNeedsAuth)
        DataStore.serviceMode = Key.MODE_PROXY
        assertFalse(DataStore.mixedInboundNeedsAuth)
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.appendHttpProxy = true
        assertTrue(DataStore.mixedInboundNeedsAuth)
        assertTrue(DataStore.mixedInboundHasAuth)
        assertEquals("", DataStore.mixedInboundUser)
        assertEquals("", DataStore.mixedInboundPass)
        DataStore.mixedInboundAuthed = true
        assertEquals(DataStore.mixedUsername, DataStore.mixedInboundUser)
        assertEquals(secret, DataStore.mixedInboundPass)

        assertNull(DataStore.webdavServer)
        DataStore.webdavServer = "https://dav.example"
        DataStore.webdavUsername = "user"
        DataStore.webdavPassword = "pass"
        assertEquals("https://dav.example", DataStore.webdavServer)
        assertEquals("user", DataStore.webdavUsername)
        assertEquals("pass", DataStore.webdavPassword)
        assertEquals("NekoBox", DataStore.webdavPath)
        DataStore.webdavPath = "box"
        assertEquals("box", DataStore.webdavPath)

        DataStore.configurationStore.putString(Key.MTU, "nope")
        assertEquals(9000, DataStore.mtu)
        DataStore.serverStreamReceiveWindow = 0
        assertEquals("", DataStore.profileCacheStore.getString(Key.SERVER_STREAM_RECEIVE_WINDOW))
        DataStore.serverConnectionReceiveWindow = 0
        assertEquals("", DataStore.profileCacheStore.getString(Key.SERVER_CONNECTION_RECEIVE_WINDOW))

        roundTrip()
        DataStore.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.SERVICE_MODE)
        DataStore.runningTest = true
        assertTrue(DataStore.runningTest)
        DataStore.vpnService = null
        DataStore.baseService = null
        assertNull(DataStore.vpnService)
        assertNull(DataStore.baseService)
    }

    @Test
    fun legacyPreferenceDelegatesRoundTrip() {
        val store = RoomPreferenceDataStore(MemoryKvDao())
        var text by store.string("s") { "d" }
        var flag by store.boolean("b") { true }
        var number by store.int("i") { 2 }
        var fromText by store.stringToInt("n") { 3 }
        var optional by store.stringToIntIfExists("e") { 1 }
        var wide by store.long("l") { 4L }
        var wideText by store.stringToLong("g") { 5L }
        var names by store.stringSet("set") { setOf("a") }
        assertEquals("d", text)
        assertTrue(flag)
        assertEquals(2, number)
        assertEquals(3, fromText)
        assertEquals(1, optional)
        assertEquals(4L, wide)
        assertEquals(5L, wideText)
        assertEquals(setOf("a"), names)
        text = "x"
        flag = false
        number = 8
        fromText = 9
        optional = 0
        wide = 10L
        wideText = 11L
        names = mutableSetOf("b", "c")
        assertEquals("x", text)
        assertFalse(flag)
        assertEquals(8, number)
        assertEquals(9, fromText)
        assertEquals(1, optional)
        assertEquals("", store.getString("e"))
        optional = 8
        assertEquals(8, optional)
        assertEquals(10L, wide)
        assertEquals(11L, wideText)
        assertEquals(setOf("b", "c"), names)
    }

    @Test
    fun productionReadersUseTheMemoryStore() {
        DataStore.serviceMode = Key.MODE_PROXY
        DataStore.remoteDns = "https://dns.example/dns-query"
        DataStore.directDns = "https://direct.example/dns-query"
        DataStore.dnsHosts = "one.example 1.1.1.1"
        DataStore.enableDnsRouting = false
        DataStore.enableFakeDns = false
        DataStore.trafficSniffing = 2
        DataStore.ipv6Mode = 1
        DataStore.enableClashAPI = true
        DataStore.logLevel = 3
        DataStore.tunImplementation = 1
        DataStore.mtu = 1400
        DataStore.resolveDestination = true
        DataStore.strictRoute = false
        DataStore.concurrentDial = true
        DataStore.enableTLSFragment = true
        DataStore.globalMode = true
        DataStore.bypassLan = true
        DataStore.bypassLanInCore = true
        DataStore.rulesUpdateInterval = "12"
        DataStore.fragmentLength = "50-60"
        DataStore.fragmentInterval = "1-2"
        DataStore.globalCustomConfig = "{}"
        DataStore.globalAllowInsecure = true
        DataStore.enableHevTun = true
        DataStore.allowAccess = true
        DataStore.allowInsecureOnRequest = true
        DataStore.appTLSVersion = "1.3"
        DataStore.mixedPort = 2080
        DataStore.mixedUsername = "neko"
        DataStore.mixedInboundAuthed = true
        DataStore.serviceState = BaseService.State.Connected

        DataStore.configurationStore.putString("domain_strategy_for_remote", "auto")
        DataStore.configurationStore.putString("domain_strategy_for_direct", "ipv4_only")
        DataStore.configurationStore.putString("domain_strategy_for_server", "auto")

        val source = ProductionConfigSource
        assertEquals(Key.MODE_PROXY, source.serviceMode)
        assertTrue(source.enableHevTun)
        assertTrue(source.allowAccess)
        assertEquals("https://dns.example/dns-query", source.remoteDns)
        assertEquals("https://direct.example/dns-query", source.directDns)
        assertEquals("one.example 1.1.1.1", source.dnsHosts)
        assertFalse(source.enableDnsRouting)
        assertFalse(source.enableFakeDns)
        assertEquals(2, source.trafficSniffing)
        assertEquals(1, source.ipv6Mode)
        assertTrue(source.enableClashAPI)
        assertEquals(3, source.logLevel)
        assertEquals(1, source.tunImplementation)
        assertEquals(1400, source.mtu)
        assertTrue(source.resolveDestination)
        assertFalse(source.strictRoute)
        assertEquals(2080, source.mixedPort)
        assertFalse(source.mixedInboundHasAuth)
        DataStore.serviceMode = Key.MODE_VPN
        assertTrue(source.mixedInboundHasAuth)
        DataStore.serviceMode = Key.MODE_PROXY
        assertEquals("neko", source.mixedUsername)
        assertTrue(source.mixedSecret.isNotEmpty())
        assertTrue(source.concurrentDial)
        assertTrue(source.enableTLSFragment)
        assertTrue(source.globalMode)
        assertTrue(source.bypassLan)
        assertEquals("12", source.rulesUpdateInterval)
        assertEquals("50-60", source.fragmentLength)
        assertEquals("1-2", source.fragmentInterval)
        assertTrue(source.bypassLanInCore)
        assertEquals("{}", source.globalCustomConfig)
        assertTrue(source.globalAllowInsecure)
        assertTrue(source.serviceStarted)
        assertEquals("", source.domainStrategy("dns-remote"))
        assertEquals("ipv4_only", source.domainStrategy("dns-direct"))
        assertEquals("prefer_ipv4", source.domainStrategy("server"))
        DataStore.serviceState = BaseService.State.Idle
        assertFalse(source.serviceStarted)

        val subscription = SubscriptionBean().apply {
            initializeDefaultValues()
            link = "https://example.test/sub"
            customUserAgent = ""
        }
        val fetched = ProductionSubscriptionClient.fetch(subscription)
        assertEquals("", fetched.body)
        assertEquals("", fetched.userinfo)
        assertEquals("", fetched.contentDisposition)
        subscription.customUserAgent = "Agent"
        DataStore.appTLSVersion = "1.2"
        DataStore.allowInsecureOnRequest = false
        DataStore.mixedInboundAuthed = false
        assertEquals("", ProductionSubscriptionClient.fetch(subscription).body)
    }

    @Test
    fun roomStoreTypesListenersAndBinding() {
        val store = DataStore.configurationStore
        val seen = ArrayList<String>()
        val listener = object : OnPreferenceDataStoreChangeListener {
            override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
                seen += key
            }
        }
        store.registerChangeListener(listener)
        store.registerChangeListener(DataStore)
        store.putFloat("f", 1.5f)
        assertEquals(1.5f, store.getFloat("f", 0f))
        assertEquals(1.5f, store.getFloat("f"))
        store.putFloat("f", null)
        assertNull(store.getFloat("f"))
        store.putStringSet("ss", mutableSetOf("a", "b"))
        assertEquals(setOf("a", "b"), store.getStringSet("ss", mutableSetOf()))
        store.putStringSet("ss", null)
        assertNull(store.getStringSet("ss"))
        store.putBoolean("b", true)
        assertTrue(store.getBoolean("b", false))
        store.putBoolean("b", null)
        assertNull(store.getBoolean("b"))
        store.putInt("i", 3)
        assertEquals(3, store.getInt("i", 0))
        store.putInt("i", null)
        assertNull(store.getInt("i"))
        store.putLong("l", 9L)
        assertEquals(9L, store.getLong("l", 0L))
        store.putLong("l", null)
        store.putString("s", null)
        store.remove("gone")
        assertTrue(seen.contains("f"))
        store.unregisterChangeListener(listener)
        val before = seen.size
        store.putString("after", "x")
        assertEquals(before, seen.size)
        store.unregisterChangeListener(DataStore)

        configDao.insert(listOf(KeyValuePair("inserted").put("text")))
        assertEquals("text", store.getString("inserted"))
        assertTrue(configDao.all().any { it.key == "inserted" })
        assertTrue(store.reset() > 0)
        assertNull(store.getString("inserted"))

        val cache = DataStore.profileCacheStore
        cache.putString("name", "alpha")
        cache.putString("port", "7")
        cache.putInt("count", 3)
        cache.putBoolean("flag", true)
        val bean = CacheBean()
        val text = PreferenceBinding(Type.Text, "name", bean)
        val number = PreferenceBinding(Type.TextToInt, "port", bean)
        val count = PreferenceBinding(Type.Int, "count", bean)
        val flag = PreferenceBinding(Type.Bool, "flag", bean)
        text.fromCache()
        number.fromCache()
        count.fromCache()
        flag.fromCache()
        assertEquals("alpha", bean.name)
        assertEquals(7, bean.port)
        assertEquals(3, bean.count)
        assertTrue(bean.flag)

        bean.name = "beta"
        bean.port = 8
        bean.count = 4
        bean.flag = false
        text.writeToCache()
        number.writeToCache()
        count.writeToCache()
        flag.writeToCache()
        assertEquals("beta", cache.getString("name"))
        assertEquals("8", cache.getString("port"))
        assertEquals(4, cache.getInt("count", 0))
        assertFalse(cache.getBoolean("flag", true))

        text.disable = true
        bean.name = "kept"
        text.fromCache()
        text.writeToCache()
        assertEquals("kept", bean.name)
        assertEquals("beta", cache.getString("name"))

        PreferenceBinding(Type.Text, "missing", bean).fromCache()
        PreferenceBinding(Type.Text, "missing", bean).writeToCache()
        PreferenceBinding(9, "name", bean).writeToCache()
        val skipped = PreferenceBinding(Type.Text, "count", bean)
        skipped.writeToCache()
        assertNotNull(skipped)
    }

    private fun roundTrip() {
        DataStore.currentProfile = 6L
        assertEquals(6L, DataStore.currentProfile)
        DataStore.selectedProxy = 6L
        assertEquals(6L, DataStore.selectedProxy)
        DataStore.selectedGroup = 6L
        assertEquals(6L, DataStore.selectedGroup)
        DataStore.appTLSVersion = "v"
        assertEquals("v", DataStore.appTLSVersion)
        DataStore.enableClashAPI = true
        assertTrue(DataStore.enableClashAPI)
        DataStore.showBottomBar = true
        assertTrue(DataStore.showBottomBar)
        DataStore.confirmProfileDelete = true
        assertTrue(DataStore.confirmProfileDelete)
        DataStore.groupLayoutMode = 6
        assertEquals(6, DataStore.groupLayoutMode)
        DataStore.profileCardStyle = 6
        assertEquals(6, DataStore.profileCardStyle)
        DataStore.allowInsecureOnRequest = true
        assertTrue(DataStore.allowInsecureOnRequest)
        DataStore.networkChangeResetConnections = true
        assertTrue(DataStore.networkChangeResetConnections)
        DataStore.wakeResetConnections = true
        assertTrue(DataStore.wakeResetConnections)
        DataStore.isExpert = true
        assertTrue(DataStore.isExpert)
        DataStore.appTheme = 6
        assertEquals(6, DataStore.appTheme)
        DataStore.nightTheme = 6
        assertEquals(6, DataStore.nightTheme)
        DataStore.appLanguage = "v"
        assertEquals("v", DataStore.appLanguage)
        DataStore.serviceMode = "v"
        assertEquals("v", DataStore.serviceMode)
        DataStore.trafficSniffing = 6
        assertEquals(6, DataStore.trafficSniffing)
        DataStore.resolveDestination = true
        assertTrue(DataStore.resolveDestination)
        DataStore.mtu = 6
        assertEquals(6, DataStore.mtu)
        DataStore.bypassLan = true
        assertTrue(DataStore.bypassLan)
        DataStore.bypassLanInCore = true
        assertTrue(DataStore.bypassLanInCore)
        DataStore.concurrentDial = true
        assertTrue(DataStore.concurrentDial)
        DataStore.allowAccess = true
        assertTrue(DataStore.allowAccess)
        DataStore.speedInterval = 6
        assertEquals(6, DataStore.speedInterval)
        DataStore.showGroupInNotification = true
        assertTrue(DataStore.showGroupInNotification)
        DataStore.globalCustomConfig = "v"
        assertEquals("v", DataStore.globalCustomConfig)
        DataStore.remoteDns = "v"
        assertEquals("v", DataStore.remoteDns)
        DataStore.directDns = "v"
        assertEquals("v", DataStore.directDns)
        DataStore.enableDnsRouting = true
        assertTrue(DataStore.enableDnsRouting)
        DataStore.enableFakeDns = true
        assertTrue(DataStore.enableFakeDns)
        DataStore.rulesProvider = 6
        assertEquals(6, DataStore.rulesProvider)
        DataStore.logLevel = 6
        assertEquals(6, DataStore.logLevel)
        DataStore.logBufSize = 6
        assertEquals(6, DataStore.logBufSize)
        DataStore.acquireWakeLock = true
        assertTrue(DataStore.acquireWakeLock)
        DataStore.hideFromRecentApps = true
        assertTrue(DataStore.hideFromRecentApps)
        DataStore.rulesGeositeUrl = "v"
        assertEquals("v", DataStore.rulesGeositeUrl)
        DataStore.rulesGeoipUrl = "v"
        assertEquals("v", DataStore.rulesGeoipUrl)
        DataStore.rulesUpdateInterval = "v"
        assertEquals("v", DataStore.rulesUpdateInterval)
        DataStore.mixedUsername = "v"
        assertEquals("v", DataStore.mixedUsername)
        DataStore.ipv6Mode = 6
        assertEquals(6, DataStore.ipv6Mode)
        DataStore.meteredNetwork = true
        assertTrue(DataStore.meteredNetwork)
        DataStore.proxyApps = true
        assertTrue(DataStore.proxyApps)
        DataStore.bypass = true
        assertTrue(DataStore.bypass)
        DataStore.individual = "v"
        assertEquals("v", DataStore.individual)
        DataStore.showDirectSpeed = true
        assertTrue(DataStore.showDirectSpeed)
        DataStore.persistAcrossReboot
        DataStore.appendHttpProxy = true
        assertTrue(DataStore.appendHttpProxy)
        DataStore.httpProxyBypass = "v"
        assertEquals("v", DataStore.httpProxyBypass)
        DataStore.dnsHosts = "v"
        assertEquals("v", DataStore.dnsHosts)
        DataStore.strictRoute = true
        assertTrue(DataStore.strictRoute)
        DataStore.connectionTestURL = "v"
        assertEquals("v", DataStore.connectionTestURL)
        DataStore.connectionTestConcurrent = 6
        assertEquals(6, DataStore.connectionTestConcurrent)
        DataStore.connectionTestTimeout = 6
        assertEquals(6, DataStore.connectionTestTimeout)
        DataStore.alwaysShowAddress = true
        assertTrue(DataStore.alwaysShowAddress)
        DataStore.tunImplementation = 6
        assertEquals(6, DataStore.tunImplementation)
        DataStore.enableHevTun = true
        assertTrue(DataStore.enableHevTun)
        DataStore.profileTrafficStatistics = true
        assertTrue(DataStore.profileTrafficStatistics)
        DataStore.yacdURL = "v"
        assertEquals("v", DataStore.yacdURL)
        DataStore.globalAllowInsecure = true
        assertTrue(DataStore.globalAllowInsecure)
        DataStore.enableTLSFragment = true
        assertTrue(DataStore.enableTLSFragment)
        DataStore.fragmentLength = "v"
        assertEquals("v", DataStore.fragmentLength)
        DataStore.fragmentInterval = "v"
        assertEquals("v", DataStore.fragmentInterval)
        DataStore.dirty = true
        assertTrue(DataStore.dirty)
        DataStore.editingId = 6L
        assertEquals(6L, DataStore.editingId)
        DataStore.editingGroup = 6L
        assertEquals(6L, DataStore.editingGroup)
        DataStore.profileName = "v"
        assertEquals("v", DataStore.profileName)
        DataStore.serverAddress = "v"
        assertEquals("v", DataStore.serverAddress)
        DataStore.serverPort = 6
        assertEquals(6, DataStore.serverPort)
        DataStore.serverPorts = "v"
        assertEquals("v", DataStore.serverPorts)
        DataStore.serverUsername = "v"
        assertEquals("v", DataStore.serverUsername)
        DataStore.serverPassword = "v"
        assertEquals("v", DataStore.serverPassword)
        DataStore.serverPassword1 = "v"
        assertEquals("v", DataStore.serverPassword1)
        DataStore.serverMethod = "v"
        assertEquals("v", DataStore.serverMethod)
        DataStore.sharedStorage = "v"
        assertEquals("v", DataStore.sharedStorage)
        DataStore.serverProtocol = "v"
        assertEquals("v", DataStore.serverProtocol)
        DataStore.serverObfs = "v"
        assertEquals("v", DataStore.serverObfs)
        DataStore.serverProtocolParam = "v"
        assertEquals("v", DataStore.serverProtocolParam)
        DataStore.serverObfsParam = "v"
        assertEquals("v", DataStore.serverObfsParam)
        DataStore.serverNetwork = "v"
        assertEquals("v", DataStore.serverNetwork)
        DataStore.serverHost = "v"
        assertEquals("v", DataStore.serverHost)
        DataStore.serverPath = "v"
        assertEquals("v", DataStore.serverPath)
        DataStore.serverSNI = "v"
        assertEquals("v", DataStore.serverSNI)
        DataStore.serverEncryption = "v"
        assertEquals("v", DataStore.serverEncryption)
        DataStore.serverALPN = "v"
        assertEquals("v", DataStore.serverALPN)
        DataStore.serverCertificates = "v"
        assertEquals("v", DataStore.serverCertificates)
        DataStore.serverMTU = 6
        assertEquals(6, DataStore.serverMTU)
        DataStore.serverHeaders = "v"
        assertEquals("v", DataStore.serverHeaders)
        DataStore.serverAllowInsecure = true
        assertTrue(DataStore.serverAllowInsecure)
        DataStore.serverAuthType = 6
        assertEquals(6, DataStore.serverAuthType)
        DataStore.serverUploadSpeed = 6
        assertEquals(6, DataStore.serverUploadSpeed)
        DataStore.serverDownloadSpeed = 6
        assertEquals(6, DataStore.serverDownloadSpeed)
        DataStore.serverStreamReceiveWindow = 6
        assertEquals(6, DataStore.serverStreamReceiveWindow)
        DataStore.serverConnectionReceiveWindow = 6
        assertEquals(6, DataStore.serverConnectionReceiveWindow)
        DataStore.serverDisableMtuDiscovery = true
        assertTrue(DataStore.serverDisableMtuDiscovery)
        DataStore.serverHopInterval = 6
        assertEquals(6, DataStore.serverHopInterval)
        DataStore.protocolVersion = 6
        assertEquals(6, DataStore.protocolVersion)
        DataStore.serverProtocolInt = 6
        assertEquals(6, DataStore.serverProtocolInt)
        DataStore.serverPrivateKey = "v"
        assertEquals("v", DataStore.serverPrivateKey)
        DataStore.serverInsecureConcurrency = 6
        assertEquals(6, DataStore.serverInsecureConcurrency)
        DataStore.serverUDPRelayMode = "v"
        assertEquals("v", DataStore.serverUDPRelayMode)
        DataStore.serverCongestionController = "v"
        assertEquals("v", DataStore.serverCongestionController)
        DataStore.serverDisableSNI = true
        assertTrue(DataStore.serverDisableSNI)
        DataStore.serverReduceRTT = true
        assertTrue(DataStore.serverReduceRTT)
        DataStore.serverUserId = "v"
        assertEquals("v", DataStore.serverUserId)
        DataStore.serverPinnedCertChainSha256 = "v"
        assertEquals("v", DataStore.serverPinnedCertChainSha256)
        DataStore.routeName = "v"
        assertEquals("v", DataStore.routeName)
        DataStore.routeDomain = "v"
        assertEquals("v", DataStore.routeDomain)
        DataStore.routeIP = "v"
        assertEquals("v", DataStore.routeIP)
        DataStore.routePort = "v"
        assertEquals("v", DataStore.routePort)
        DataStore.routeSourcePort = "v"
        assertEquals("v", DataStore.routeSourcePort)
        DataStore.routeNetwork = "v"
        assertEquals("v", DataStore.routeNetwork)
        DataStore.routeSource = "v"
        assertEquals("v", DataStore.routeSource)
        DataStore.routeProtocol = "v"
        assertEquals("v", DataStore.routeProtocol)
        DataStore.routeRuleset = "v"
        assertEquals("v", DataStore.routeRuleset)
        DataStore.routeOutbound = 6
        assertEquals(6, DataStore.routeOutbound)
        DataStore.routeOutboundRule = 6L
        assertEquals(6L, DataStore.routeOutboundRule)
        DataStore.routePackages = "v"
        assertEquals("v", DataStore.routePackages)
        DataStore.frontProxy = 6L
        assertEquals(6L, DataStore.frontProxy)
        DataStore.landingProxy = 6L
        assertEquals(6L, DataStore.landingProxy)
        DataStore.frontProxyTmp = 6
        assertEquals(6, DataStore.frontProxyTmp)
        DataStore.landingProxyTmp = 6
        assertEquals(6, DataStore.landingProxyTmp)
        DataStore.serverConfig = "v"
        assertEquals("v", DataStore.serverConfig)
        DataStore.serverCustom = "v"
        assertEquals("v", DataStore.serverCustom)
        DataStore.serverCustomOutbound = "v"
        assertEquals("v", DataStore.serverCustomOutbound)
        DataStore.groupName = "v"
        assertEquals("v", DataStore.groupName)
        DataStore.groupType = 6
        assertEquals(6, DataStore.groupType)
        DataStore.groupOrder = 6
        assertEquals(6, DataStore.groupOrder)
        DataStore.groupIsSelector = true
        assertTrue(DataStore.groupIsSelector)
        DataStore.subscriptionLink = "v"
        assertEquals("v", DataStore.subscriptionLink)
        DataStore.subscriptionForceResolve = true
        assertTrue(DataStore.subscriptionForceResolve)
        DataStore.subscriptionDeduplication = true
        assertTrue(DataStore.subscriptionDeduplication)
        DataStore.subscriptionUpdateWhenConnectedOnly = true
        assertTrue(DataStore.subscriptionUpdateWhenConnectedOnly)
        DataStore.subscriptionUserAgent = "v"
        assertEquals("v", DataStore.subscriptionUserAgent)
        DataStore.subscriptionAutoUpdate = true
        assertTrue(DataStore.subscriptionAutoUpdate)
        DataStore.subscriptionAutoUpdateDelay = 6
        assertEquals(6, DataStore.subscriptionAutoUpdateDelay)
        DataStore.subscriptionFilterMode = 6
        assertEquals(6, DataStore.subscriptionFilterMode)
        DataStore.subscriptionFilterRegex = "v"
        assertEquals("v", DataStore.subscriptionFilterRegex)
        DataStore.subscriptionServerDns = "v"
        assertEquals("v", DataStore.subscriptionServerDns)
        DataStore.rulesFirstCreate = true
        assertTrue(DataStore.rulesFirstCreate)
        DataStore.globalMode = true
        assertTrue(DataStore.globalMode)
    }
}

private class MemoryGroups : GroupDirectory {
    val rows = mutableListOf<ProxyGroup>()
    private var nextId = 1L
    override fun allGroups() = rows.toList()
    override fun getById(id: Long) = rows.find { it.id == id }
    override fun createGroup(group: ProxyGroup): Long {
        if (group.id == 0L) group.id = nextId++
        rows += group
        return group.id
    }
}

private class MemoryKvDao : KeyValuePair.Dao {
    private val rows = linkedMapOf<String, KeyValuePair>()
    override fun all() = rows.values.toList()
    override fun get(key: String) = rows[key]
    override fun put(value: KeyValuePair): Long {
        rows[value.key] = value
        return rows.size.toLong()
    }

    override fun delete(key: String) = if (rows.remove(key) != null) 1 else 0
    override fun reset(): Int {
        val size = rows.size
        rows.clear()
        return size
    }

    override fun insert(list: List<KeyValuePair>) {
        list.forEach { put(it) }
    }
}

class CacheBean {
    @JvmField var name: String = ""
    @JvmField var port: Int = 0
    @JvmField var count: Int = 0
    @JvmField var flag: Boolean = false
}
