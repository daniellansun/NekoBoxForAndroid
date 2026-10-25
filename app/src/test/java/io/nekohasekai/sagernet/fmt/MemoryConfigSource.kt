package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity

internal class MemoryConfigSource : ConfigSource {
    override var serviceMode: String = "proxy"
    override var enableHevTun: Boolean = false
    override var allowAccess: Boolean = false
    override var remoteDns: String = "https://dns.google/dns-query"
    override var directDns: String = "https://223.5.5.5/dns-query"
    override var dnsHosts: String = ""
    override var enableDnsRouting: Boolean = true
    override var enableFakeDns: Boolean = false
    override var trafficSniffing: Int = 1
    override var ipv6Mode: Int = IPv6Mode.ENABLE
    override var enableClashAPI: Boolean = false
    override var logLevel: Int = 2
    override var tunImplementation: Int = TunImplementation.GVISOR
    override var mtu: Int = 9000
    override var resolveDestination: Boolean = false
    override var strictRoute: Boolean = true
    override var mixedPort: Int = 2080
    override var mixedInboundHasAuth: Boolean = false
    override var mixedUsername: String = ""
    override var mixedSecret: String = ""
    override var concurrentDial: Boolean = false
    override var enableTLSFragment: Boolean = false
    override var globalMode: Boolean = false
    override var bypassLan: Boolean = false
    override var rulesUpdateInterval: String = "0"
    override var fragmentLength: String = "100-200"
    override var fragmentInterval: String = "10-20"
    override var bypassLanInCore: Boolean = false
    override var globalCustomConfig: String = ""
    override var globalAllowInsecure: Boolean = false
    var domainStrategyResult: String = "prefer_ipv4"

    val groups = mutableMapOf<Long, ProxyGroup>()
    val proxies = mutableMapOf<Long, ProxyEntity>()
    val rules = mutableListOf<RuleEntity>()

    override fun domainStrategy(tag: String) = if (tag == "dns-remote") "" else domainStrategyResult
    override fun groupById(id: Long) = groups[id]
    override fun proxiesByIds(ids: List<Long>) = ids.mapNotNull { proxies[it] }.distinctBy { it.id }
    override fun proxyById(id: Long) = proxies[id]
    override fun enabledRules() = rules.toList()
    override fun proxiesByGroup(groupId: Long) = proxies.values.filter { it.groupId == groupId }

    fun add(entity: ProxyEntity) {
        proxies[entity.id] = entity
    }
}

internal fun <T> withConfig(source: ConfigSource, block: () -> T): T {
    val previous = configSource
    configSource = source
    return try {
        block()
    } finally {
        configSource = previous
    }
}

internal fun vpnMode(): String = Key.MODE_VPN
