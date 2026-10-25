package io.nekohasekai.sagernet.group

import androidx.core.net.toUri
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.isHttpOrHttpsUrl
import libcore.Libcore
import moe.matsuri.nb4a.utils.Util

/**
 * HTTP fetch and content-URI read used by [RawUpdater.doUpdate].
 *
 * Production still builds the libcore client from [DataStore] at call time.
 * Unit tests replace [subscriptionClient] so those reads never open Room.
 */
internal interface SubscriptionClient {
    fun fetch(subscription: SubscriptionBean): SubscriptionFetch
    fun readContent(link: String): String?
    fun noProxiesFound(): String
    fun noProxiesInSubscription(): String
    fun noProxiesFoundInFile(): String
}

internal class SubscriptionFetch(
    val body: String,
    val userinfo: String,
    val contentDisposition: String,
)

internal object ProductionSubscriptionClient : SubscriptionClient {
    override fun fetch(subscription: SubscriptionBean): SubscriptionFetch {
        val response = Libcore.newHttpClient().apply {
            trySocks5(
                DataStore.mixedPort,
                DataStore.mixedInboundUser,
                DataStore.mixedInboundPass,
            )
            tryH3Direct()
            when (DataStore.appTLSVersion) {
                "1.3" -> restrictedTLS()
            }
        }.newRequest().apply {
            if (DataStore.allowInsecureOnRequest) {
                allowInsecure()
            }
            val link = subscription.link?.trim().orEmpty()
            if (!isHttpOrHttpsUrl(link)) error("Unsupported subscription URL")
            setURL(link)
            setUserAgent(subscription.customUserAgent.takeIf { it.isNotBlank() } ?: USER_AGENT)
        }.execute()
        return SubscriptionFetch(
            Util.getStringBox(response.contentString),
            Util.getStringBox(response.getHeader("Subscription-Userinfo")),
            Util.getStringBox(response.getHeader("content-disposition")),
        )
    }

    override fun readContent(link: String): String? = readContentText(link)

    override fun noProxiesFound() = noProxiesFoundText()
    override fun noProxiesInSubscription() = noProxiesInSubscriptionText()
    override fun noProxiesFoundInFile() = noProxiesInFileText()
}

/** Production still reads the app resources. Tests replace the lambdas. */
@Volatile
internal var readContentText: (String) -> String? = { link ->
    app.contentResolver.openInputStream(link.toUri())
        ?.bufferedReader()
        ?.readText()
}

@Volatile
internal var noProxiesFoundText: () -> String = { app.getString(R.string.no_proxies_found) }

@Volatile
internal var noProxiesInSubscriptionText: () -> String =
    { app.getString(R.string.no_proxies_found_in_subscription) }

@Volatile
internal var noProxiesInFileText: () -> String = { app.getString(R.string.no_proxies_found_in_file) }

/**
 * Profile rows written while applying a subscription.
 * Production delegates to [SagerDatabase] at call time.
 */
internal interface ProfileStore {
    fun proxiesByGroup(groupId: Long): List<ProxyEntity>
    fun addProxy(proxy: ProxyEntity): Long
    fun updateProxies(proxies: List<ProxyEntity>): Int
    fun deleteProxies(proxies: List<ProxyEntity>): Int
    fun countByGroup(groupId: Long): Long
    fun updateGroup(group: ProxyGroup)
}

internal object ProductionProfileStore : ProfileStore {
    override fun proxiesByGroup(groupId: Long) = SagerDatabase.proxyDao.getByGroup(groupId)
    override fun addProxy(proxy: ProxyEntity) = SagerDatabase.proxyDao.addProxy(proxy)
    override fun updateProxies(proxies: List<ProxyEntity>) = SagerDatabase.proxyDao.updateProxy(proxies)
    override fun deleteProxies(proxies: List<ProxyEntity>) = SagerDatabase.proxyDao.deleteProxy(proxies)
    override fun countByGroup(groupId: Long) = SagerDatabase.proxyDao.countByGroup(groupId)
    override fun updateGroup(group: ProxyGroup) {
        SagerDatabase.groupDao.updateGroup(group)
    }
}

@Volatile
internal var subscriptionClient: SubscriptionClient = ProductionSubscriptionClient

@Volatile
internal var profileStore: ProfileStore = ProductionProfileStore
