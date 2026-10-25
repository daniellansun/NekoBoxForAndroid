package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import moe.matsuri.nb4a.SingBoxOptionsUtil

/**
 * Values read while building sing-box outbounds and a full config.
 *
 * Production getters still read [DataStore] and [SagerDatabase] at the moment of use.
 * Unit tests replace [configSource] with an in-memory implementation so those reads
 * never open Room.
 */
interface ConfigSource {
    val serviceMode: String
    val enableHevTun: Boolean
    val allowAccess: Boolean
    val remoteDns: String
    val directDns: String
    val dnsHosts: String
    val enableDnsRouting: Boolean
    val enableFakeDns: Boolean
    val trafficSniffing: Int
    val ipv6Mode: Int
    val enableClashAPI: Boolean
    val logLevel: Int
    val tunImplementation: Int
    val mtu: Int
    val resolveDestination: Boolean
    val strictRoute: Boolean
    val mixedPort: Int
    val mixedInboundHasAuth: Boolean
    val mixedUsername: String
    val mixedSecret: String
    val concurrentDial: Boolean
    val enableTLSFragment: Boolean
    val globalMode: Boolean
    val bypassLan: Boolean
    val rulesUpdateInterval: String
    val fragmentLength: String
    val fragmentInterval: String
    val bypassLanInCore: Boolean
    val globalCustomConfig: String
    val globalAllowInsecure: Boolean

    fun domainStrategy(tag: String): String
    fun groupById(id: Long): ProxyGroup?
    fun proxiesByIds(ids: List<Long>): List<ProxyEntity>
    fun proxyById(id: Long): ProxyEntity?
    fun enabledRules(): List<RuleEntity>
    fun proxiesByGroup(groupId: Long): List<ProxyEntity>
}

internal object ProductionConfigSource : ConfigSource {
    override val serviceMode get() = DataStore.serviceMode
    override val enableHevTun get() = DataStore.enableHevTun
    override val allowAccess get() = DataStore.allowAccess
    override val remoteDns get() = DataStore.remoteDns
    override val directDns get() = DataStore.directDns
    override val dnsHosts get() = DataStore.dnsHosts
    override val enableDnsRouting get() = DataStore.enableDnsRouting
    override val enableFakeDns get() = DataStore.enableFakeDns
    override val trafficSniffing get() = DataStore.trafficSniffing
    override val ipv6Mode get() = DataStore.ipv6Mode
    override val enableClashAPI get() = DataStore.enableClashAPI
    override val logLevel get() = DataStore.logLevel
    override val tunImplementation get() = DataStore.tunImplementation
    override val mtu get() = DataStore.mtu
    override val resolveDestination get() = DataStore.resolveDestination
    override val strictRoute get() = DataStore.strictRoute
    override val mixedPort get() = DataStore.mixedPort
    override val mixedInboundHasAuth get() = DataStore.mixedInboundHasAuth
    override val mixedUsername get() = DataStore.mixedUsername
    override val mixedSecret get() = DataStore.mixedSecret
    override val concurrentDial get() = DataStore.concurrentDial
    override val enableTLSFragment get() = DataStore.enableTLSFragment
    override val globalMode get() = DataStore.globalMode
    override val bypassLan get() = DataStore.bypassLan
    override val rulesUpdateInterval get() = DataStore.rulesUpdateInterval
    override val fragmentLength get() = DataStore.fragmentLength
    override val fragmentInterval get() = DataStore.fragmentInterval
    override val bypassLanInCore get() = DataStore.bypassLanInCore
    override val globalCustomConfig get() = DataStore.globalCustomConfig
    override val globalAllowInsecure get() = DataStore.globalAllowInsecure

    override fun domainStrategy(tag: String) = SingBoxOptionsUtil.domainStrategy(tag)
    override fun groupById(id: Long) = SagerDatabase.groupDao.getById(id)
    override fun proxiesByIds(ids: List<Long>) = SagerDatabase.proxyDao.getEntities(ids)
    override fun proxyById(id: Long) = SagerDatabase.proxyDao.getById(id)
    override fun enabledRules() = SagerDatabase.rulesDao.enabledRules()
    override fun proxiesByGroup(groupId: Long) = SagerDatabase.proxyDao.getByGroup(groupId)
}

@Volatile
var configSource: ConfigSource = ProductionConfigSource
