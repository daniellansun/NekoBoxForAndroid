package io.nekohasekai.sagernet.database

import android.os.Binder
import android.os.Build
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.CONNECTION_TEST_URL
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.booleanPref
import io.nekohasekai.sagernet.ktx.intPref
import io.nekohasekai.sagernet.ktx.longPref
import io.nekohasekai.sagernet.ktx.parsePort
import io.nekohasekai.sagernet.ktx.stringPref
import io.nekohasekai.sagernet.ktx.stringToIntIfExistsPref
import io.nekohasekai.sagernet.ktx.stringToIntPref
import moe.matsuri.nb4a.TempDatabase

internal interface GroupDirectory {
    fun allGroups(): List<ProxyGroup>
    fun getById(id: Long): ProxyGroup?
    fun createGroup(group: ProxyGroup): Long
}

internal object RoomGroupDirectory : GroupDirectory {
    override fun allGroups() = SagerDatabase.groupDao.allGroups()
    override fun getById(id: Long) = SagerDatabase.groupDao.getById(id)
    override fun createGroup(group: ProxyGroup) = SagerDatabase.groupDao.createGroup(group)
}

@Volatile
internal var groupDirectory: GroupDirectory = RoomGroupDirectory

object DataStore : OnPreferenceDataStoreChangeListener {

    // share service state in main & bg process
    @Volatile
    var serviceState = BaseService.State.Idle

    @Volatile
    var mixedInboundAuthed: Boolean = false

    // Room opens on the first real preference read. Tests assign the overrides first.
    internal var configurationStoreOverride: RoomPreferenceDataStore? = null
    internal var profileCacheStoreOverride: RoomPreferenceDataStore? = null

    private val roomConfigurationStore by lazy { RoomPreferenceDataStore(PublicDatabase.kvPairDao) }
    private val roomProfileCacheStore by lazy { RoomPreferenceDataStore(TempDatabase.profileCacheDao) }

    val configurationStore: RoomPreferenceDataStore
        get() = configurationStoreOverride ?: roomConfigurationStore

    val profileCacheStore: RoomPreferenceDataStore
        get() = profileCacheStoreOverride ?: roomProfileCacheStore

    private fun configPrefs(): PreferenceDataStore = configurationStore
    private fun cachePrefs(): PreferenceDataStore = profileCacheStore

    // last used, but may not be running
    var currentProfile by longPref(::configPrefs, Key.PROFILE_CURRENT)

    var selectedProxy by longPref(::configPrefs, Key.PROFILE_ID)
    var selectedGroup by longPref(::configPrefs, Key.PROFILE_GROUP) { currentGroupId() } // "ungrouped" group id = 1

    // only in bg process
    var vpnService: VpnService? = null
    var baseService: BaseService.Interface? = null

    // main

    var runningTest = false

    fun currentGroupId(): Long {
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) return currentSelected
        val groups = groupDirectory.allGroups()
        if (groups.isNotEmpty()) {
            val groupId = groups[0].id
            selectedGroup = groupId
            return groupId
        }
        val groupId = groupDirectory.createGroup(ProxyGroup(ungrouped = true))
        selectedGroup = groupId
        return groupId
    }

    fun currentGroup(): ProxyGroup {
        var group: ProxyGroup? = null
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) {
            group = groupDirectory.getById(currentSelected)
        }
        if (group != null) return group
        val groups = groupDirectory.allGroups()
        if (groups.isEmpty()) {
            group = ProxyGroup(ungrouped = true).apply {
                id = groupDirectory.createGroup(this)
            }
        } else {
            group = groups[0]
        }
        selectedGroup = group.id
        return group
    }

    fun selectedGroupForImport(): Long {
        val current = currentGroup()
        if (current.type == GroupType.BASIC) return current.id
        val groups = groupDirectory.allGroups()
        return groups.find { it.type == GroupType.BASIC }!!.id
    }

    var appTLSVersion by stringPref(::configPrefs, Key.APP_TLS_VERSION)
    var enableClashAPI by booleanPref(::configPrefs, Key.ENABLE_CLASH_API)
    var showBottomBar by booleanPref(::configPrefs, Key.SHOW_BOTTOM_BAR)
    var confirmProfileDelete by booleanPref(::configPrefs, Key.CONFIRM_PROFILE_DELETE) { true }
    var groupLayoutMode by stringToIntPref(::configPrefs, Key.GROUP_LAYOUT_MODE) { 0 }
    var profileCardStyle by stringToIntPref(::configPrefs, Key.PROFILE_CARD_STYLE) { 0 }

    var allowInsecureOnRequest by booleanPref(::configPrefs, Key.ALLOW_INSECURE_ON_REQUEST)
    var networkChangeResetConnections by booleanPref(::configPrefs, Key.NETWORK_CHANGE_RESET_CONNECTIONS) { true }
    var wakeResetConnections by booleanPref(::configPrefs, Key.WAKE_RESET_CONNECTIONS)

    //

    var isExpert by booleanPref(::configPrefs, Key.APP_EXPERT)
    var appTheme by intPref(::configPrefs, Key.APP_THEME)
    var nightTheme by stringToIntPref(::configPrefs, Key.NIGHT_THEME)
    var appLanguage by stringPref(::configPrefs, Key.APP_LANGUAGE) { "" }
    var serviceMode by stringPref(::configPrefs, Key.SERVICE_MODE) { Key.MODE_VPN }

    var trafficSniffing by stringToIntPref(::configPrefs, Key.TRAFFIC_SNIFFING) { 1 }
    var resolveDestination by booleanPref(::configPrefs, Key.RESOLVE_DESTINATION)

    var mtu by stringToIntPref(::configPrefs, Key.MTU) { 9000 }

    var bypassLan by booleanPref(::configPrefs, Key.BYPASS_LAN)
    var bypassLanInCore by booleanPref(::configPrefs, Key.BYPASS_LAN_IN_CORE)
    var concurrentDial by booleanPref(::configPrefs, Key.CONCURRENT_DIAL)

    var allowAccess by booleanPref(::configPrefs, Key.ALLOW_ACCESS)
    // Same fallback the settings screen already shows (global_preferences.xml).
    // An unset key used to read as 0, which disables the traffic loop.
    var speedInterval by stringToIntPref(::configPrefs, Key.SPEED_INTERVAL) { 1000 }
    var showGroupInNotification by booleanPref(::configPrefs, "showGroupInNotification")

    var globalCustomConfig by stringPref(::configPrefs, Key.GLOBAL_CUSTOM_CONFIG) { "" }

    var remoteDns by stringPref(::configPrefs, Key.REMOTE_DNS) { "https://dns.google/dns-query" }
    var directDns by stringPref(::configPrefs, Key.DIRECT_DNS) { "https://223.5.5.5/dns-query" }
    var enableDnsRouting by booleanPref(::configPrefs, Key.ENABLE_DNS_ROUTING) { true }
    var enableFakeDns by booleanPref(::configPrefs, Key.ENABLE_FAKEDNS) { true }

    var rulesProvider by stringToIntPref(::configPrefs, Key.RULES_PROVIDER)
    var logLevel by stringToIntPref(::configPrefs, Key.LOG_LEVEL)
    var logBufSize by intPref(::configPrefs, Key.LOG_BUF_SIZE) { 0 }
    var acquireWakeLock by booleanPref(::configPrefs, Key.ACQUIRE_WAKE_LOCK)
    var hideFromRecentApps by booleanPref(::configPrefs, Key.HIDE_FROM_RECENT_APPS)

    // Match global_preferences.xml. The previous fallbacks swapped the two files.
    var rulesGeositeUrl by stringPref(::configPrefs, Key.RULES_GEOSITE_URL) {
        "https://github.com/SagerNet/sing-geosite/releases/latest/download/geosite.db"
    }
    var rulesGeoipUrl by stringPref(::configPrefs, Key.RULES_GEOIP_URL) {
        "https://github.com/SagerNet/sing-geoip/releases/latest/download/geoip.db"
    }
    var rulesUpdateInterval by stringPref(::configPrefs, Key.RULES_UPDATE_INTERVAL) { "0" } // 默认为0，不自动更新

    // hopefully hashCode = mHandle doesn't change, currently this is true from KitKat to Nougat
    internal var userIndexOverride: Int? = null
    private val cachedUserIndex by lazy { Binder.getCallingUserHandle().hashCode() }
    private fun userIndex(): Int = userIndexOverride ?: cachedUserIndex
    val mixedSecret: String
        @Synchronized get() {
            var s = configurationStore.getString(Key.MIXED_SECRET)
            if (s == null) {
                s = java.util.UUID.randomUUID().toString().replace("-", "")
                configurationStore.putString(Key.MIXED_SECRET, s)
            }
            return s
        }

    var mixedUsername by stringPref(::configPrefs, Key.MIXED_USERNAME_PREF) { Key.MIXED_USERNAME }

    var mixedPort: Int
        get() = getLocalPort(Key.MIXED_PORT, 2080)
        set(value) = saveLocalPort(Key.MIXED_PORT, value)

    val mixedInboundNeedsAuth: Boolean
        get() = serviceMode == Key.MODE_VPN &&
            !(appendHttpProxy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

    val mixedInboundHasAuth: Boolean
        get() = mixedInboundNeedsAuth &&
            (mixedUsername.isNotEmpty() || mixedSecret.isNotEmpty())

    val mixedInboundUser: String get() = if (mixedInboundAuthed) mixedUsername else ""
    val mixedInboundPass: String get() = if (mixedInboundAuthed) mixedSecret else ""

    fun initGlobal() {
        if (configurationStore.getString(Key.MIXED_PORT) == null) {
            mixedPort = mixedPort
        }
    }


    private fun getLocalPort(key: String, default: Int): Int {
        return parsePort(configurationStore.getString(key), default + userIndex())
    }

    private fun saveLocalPort(key: String, value: Int) {
        configurationStore.putString(key, "$value")
    }

    var ipv6Mode by stringToIntPref(::configPrefs, Key.IPV6_MODE) { IPv6Mode.DISABLE }

    var meteredNetwork by booleanPref(::configPrefs, Key.METERED_NETWORK)
    var proxyApps by booleanPref(::configPrefs, Key.PROXY_APPS)
    var bypass by booleanPref(::configPrefs, Key.BYPASS_MODE) { true }
    var individual by stringPref(::configPrefs, Key.INDIVIDUAL)
    var showDirectSpeed by booleanPref(::configPrefs, Key.SHOW_DIRECT_SPEED) { true }

    val persistAcrossReboot by booleanPref(::configPrefs, Key.PERSIST_ACROSS_REBOOT) { false }

    var appendHttpProxy by booleanPref(::configPrefs, Key.APPEND_HTTP_PROXY)
    var httpProxyBypass by stringPref(::configPrefs, Key.HTTP_PROXY_BYPASS) { "" }
    var dnsHosts by stringPref(::configPrefs, Key.DNS_HOSTS) { "" }
    var strictRoute by booleanPref(::configPrefs, Key.STRICT_ROUTE) { true }
    var connectionTestURL by stringPref(::configPrefs, Key.CONNECTION_TEST_URL) { CONNECTION_TEST_URL }
    var connectionTestConcurrent by intPref(::configPrefs, "connectionTestConcurrent") { 5 }
    var connectionTestTimeout by intPref(::configPrefs, Key.CONNECTION_TEST_TIMEOUT) { 3000 }
    var alwaysShowAddress by booleanPref(::configPrefs, Key.ALWAYS_SHOW_ADDRESS)

    var tunImplementation by stringToIntPref(::configPrefs, Key.TUN_IMPLEMENTATION) { TunImplementation.GVISOR }
    var enableHevTun by booleanPref(::configPrefs, Key.ENABLE_HEV_TUN)
    var profileTrafficStatistics by booleanPref(::configPrefs, Key.PROFILE_TRAFFIC_STATISTICS) { true }

    var yacdURL by stringPref(::configPrefs, "yacdURL") { "http://127.0.0.1:9090/ui" }

    // protocol

    var globalAllowInsecure by booleanPref(::configPrefs, Key.GLOBAL_ALLOW_INSECURE) { false }

    var enableTLSFragment by booleanPref(::configPrefs, Key.ENABLE_TLS_FRAGMENT) { false }
    var fragmentLength by stringPref(::configPrefs, Key.FRAGMENT_LENGTH) { "100-200" }
    var fragmentInterval by stringPref(::configPrefs, Key.FRAGMENT_INTERVAL) { "10-20" }

    // old cache, DO NOT ADD

    var dirty by booleanPref(::cachePrefs, Key.PROFILE_DIRTY)
    var editingId by longPref(::cachePrefs, Key.PROFILE_ID)
    var editingGroup by longPref(::cachePrefs, Key.PROFILE_GROUP)
    var profileName by stringPref(::cachePrefs, Key.PROFILE_NAME)
    var serverAddress by stringPref(::cachePrefs, Key.SERVER_ADDRESS)
    var serverPort by stringToIntPref(::cachePrefs, Key.SERVER_PORT)
    var serverPorts by stringPref(::cachePrefs, "serverPorts")
    var serverUsername by stringPref(::cachePrefs, Key.SERVER_USERNAME)
    var serverPassword by stringPref(::cachePrefs, Key.SERVER_PASSWORD)
    var serverPassword1 by stringPref(::cachePrefs, Key.SERVER_PASSWORD1)
    var serverMethod by stringPref(::cachePrefs, Key.SERVER_METHOD)

    var sharedStorage by stringPref(::cachePrefs, "sharedStorage")

    var serverProtocol by stringPref(::cachePrefs, Key.SERVER_PROTOCOL)
    var serverObfs by stringPref(::cachePrefs, Key.SERVER_OBFS)
    var serverProtocolParam by stringPref(::cachePrefs, Key.SERVER_PROTOCOL_PARAM)
    var serverObfsParam by stringPref(::cachePrefs, Key.SERVER_OBFS_PARAM)

    var serverNetwork by stringPref(::cachePrefs, Key.SERVER_NETWORK)
    var serverHost by stringPref(::cachePrefs, Key.SERVER_HOST)
    var serverPath by stringPref(::cachePrefs, Key.SERVER_PATH)
    var serverSNI by stringPref(::cachePrefs, Key.SERVER_SNI)
    var serverEncryption by stringPref(::cachePrefs, Key.SERVER_ENCRYPTION)
    var serverALPN by stringPref(::cachePrefs, Key.SERVER_ALPN)
    var serverCertificates by stringPref(::cachePrefs, Key.SERVER_CERTIFICATES)
    var serverMTU by stringToIntPref(::cachePrefs, Key.SERVER_MTU)
    var serverHeaders by stringPref(::cachePrefs, Key.SERVER_HEADERS)
    var serverAllowInsecure by booleanPref(::cachePrefs, Key.SERVER_ALLOW_INSECURE)

    var serverAuthType by stringToIntPref(::cachePrefs, Key.SERVER_AUTH_TYPE)
    var serverUploadSpeed by stringToIntPref(::cachePrefs, Key.SERVER_UPLOAD_SPEED)
    var serverDownloadSpeed by stringToIntPref(::cachePrefs, Key.SERVER_DOWNLOAD_SPEED)
    var serverStreamReceiveWindow by stringToIntIfExistsPref(::cachePrefs, Key.SERVER_STREAM_RECEIVE_WINDOW)
    var serverConnectionReceiveWindow by stringToIntIfExistsPref(::cachePrefs, Key.SERVER_CONNECTION_RECEIVE_WINDOW)
    var serverDisableMtuDiscovery by booleanPref(::cachePrefs, Key.SERVER_DISABLE_MTU_DISCOVERY)
    var serverHopInterval by stringToIntPref(::cachePrefs, Key.SERVER_HOP_INTERVAL) { 10 }

    var protocolVersion by stringToIntPref(::cachePrefs, Key.PROTOCOL_VERSION) { 2 } // default is SOCKS5

    var serverProtocolInt by stringToIntPref(::cachePrefs, Key.SERVER_PROTOCOL)
    var serverPrivateKey by stringPref(::cachePrefs, Key.SERVER_PRIVATE_KEY)
    var serverInsecureConcurrency by stringToIntPref(::cachePrefs, Key.SERVER_INSECURE_CONCURRENCY)

    var serverUDPRelayMode by stringPref(::cachePrefs, Key.SERVER_UDP_RELAY_MODE)
    var serverCongestionController by stringPref(::cachePrefs, Key.SERVER_CONGESTION_CONTROLLER)
    var serverDisableSNI by booleanPref(::cachePrefs, Key.SERVER_DISABLE_SNI)
    var serverReduceRTT by booleanPref(::cachePrefs, Key.SERVER_REDUCE_RTT)

    var serverUserId by stringPref(::cachePrefs, Key.SERVER_USER_ID)
    var serverPinnedCertChainSha256 by stringPref(::cachePrefs, Key.SERVER_PINNED_CERT_CHAIN_SHA256)

    var routeName by stringPref(::cachePrefs, Key.ROUTE_NAME)
    var routeDomain by stringPref(::cachePrefs, Key.ROUTE_DOMAIN)
    var routeIP by stringPref(::cachePrefs, Key.ROUTE_IP)
    var routePort by stringPref(::cachePrefs, Key.ROUTE_PORT)
    var routeSourcePort by stringPref(::cachePrefs, Key.ROUTE_SOURCE_PORT)
    var routeNetwork by stringPref(::cachePrefs, Key.ROUTE_NETWORK)
    var routeSource by stringPref(::cachePrefs, Key.ROUTE_SOURCE)
    var routeProtocol by stringPref(::cachePrefs, Key.ROUTE_PROTOCOL)
    var routeRuleset by stringPref(::cachePrefs, Key.ROUTE_RULESET)
    var routeOutbound by stringToIntPref(::cachePrefs, Key.ROUTE_OUTBOUND)
    var routeOutboundRule by longPref(::cachePrefs, Key.ROUTE_OUTBOUND + "Long")
    var routePackages by stringPref(::cachePrefs, Key.ROUTE_PACKAGES)

    var frontProxy by longPref(::cachePrefs, Key.GROUP_FRONT_PROXY + "Long")
    var landingProxy by longPref(::cachePrefs, Key.GROUP_LANDING_PROXY + "Long")
    var frontProxyTmp by stringToIntPref(::cachePrefs, Key.GROUP_FRONT_PROXY)
    var landingProxyTmp by stringToIntPref(::cachePrefs, Key.GROUP_LANDING_PROXY)

    var serverConfig by stringPref(::cachePrefs, Key.SERVER_CONFIG)
    var serverCustom by stringPref(::cachePrefs, Key.SERVER_CUSTOM)
    var serverCustomOutbound by stringPref(::cachePrefs, Key.SERVER_CUSTOM_OUTBOUND)

    var groupName by stringPref(::cachePrefs, Key.GROUP_NAME)
    var groupType by stringToIntPref(::cachePrefs, Key.GROUP_TYPE)
    var groupOrder by stringToIntPref(::cachePrefs, Key.GROUP_ORDER)
    var groupIsSelector by booleanPref(::cachePrefs, Key.GROUP_IS_SELECTOR)

    var subscriptionLink by stringPref(::cachePrefs, Key.SUBSCRIPTION_LINK)
    var subscriptionForceResolve by booleanPref(::cachePrefs, Key.SUBSCRIPTION_FORCE_RESOLVE)
    var subscriptionDeduplication by booleanPref(::cachePrefs, Key.SUBSCRIPTION_DEDUPLICATION)
    var subscriptionUpdateWhenConnectedOnly by booleanPref(::cachePrefs, Key.SUBSCRIPTION_UPDATE_WHEN_CONNECTED_ONLY)
    var subscriptionUserAgent by stringPref(::cachePrefs, Key.SUBSCRIPTION_USER_AGENT)
    var subscriptionAutoUpdate by booleanPref(::cachePrefs, Key.SUBSCRIPTION_AUTO_UPDATE)
    var subscriptionAutoUpdateDelay by stringToIntPref(::cachePrefs, Key.SUBSCRIPTION_AUTO_UPDATE_DELAY) { 360 }
    var subscriptionFilterMode by stringToIntPref(::cachePrefs, Key.SUBSCRIPTION_FILTER_MODE) { 0 }
    var subscriptionFilterRegex by stringPref(::cachePrefs, Key.SUBSCRIPTION_FILTER_REGEX)
    var subscriptionServerDns by stringPref(::cachePrefs, Key.SUBSCRIPTION_SERVER_DNS)

    var rulesFirstCreate by booleanPref(::cachePrefs, "rulesFirstCreate")

    // var enableTLSFragment by booleanPref(::configPrefs, Key.ENABLE_TLS_FRAGMENT)

    var webdavServer: String?
        get() = configurationStore.getString("webdavServer")
        set(value) = configurationStore.putString("webdavServer", value)

    var webdavUsername: String?
        get() = configurationStore.getString("webdavUsername")
        set(value) = configurationStore.putString("webdavUsername", value)

    var webdavPassword: String?
        get() = configurationStore.getString("webdavPassword")
        set(value) = configurationStore.putString("webdavPassword", value)

    var webdavPath: String?
        get() = configurationStore.getString("webdavPath") ?: "NekoBox"  // 设置默认值
        set(value) = configurationStore.putString("webdavPath", value)

    var globalMode by booleanPref(::configPrefs, Key.GLOBAL_MODE)

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
    }
}
