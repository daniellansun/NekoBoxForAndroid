package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SubscriptionFilterMode
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.configSource
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.isIpAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RawUpdaterUpdateTest {
    @Test
    fun httpUpdateAppliesProfilesHeadersAndDedup() {
        val body = """
            #subscription-userinfo: upload=1; download=2
            #profile-title: base64:TmFtZQ==
            socks5://u:p@1.2.3.4:1080#alpha
            socks5://u:p@1.2.3.4:1080#alpha
            socks5://u:p@5.6.7.8:1080#alpha
            socks5://u:p@9.9.9.9:1080#beta
            socks5://u:p@8.8.8.8:1080#skipme
        """.trimIndent()
        val client = FakeSubscriptionClient(body)
        val store = MemoryProfileStore()
        val group = group("Subscription #3")
        group.subscription!!.apply {
            link = "https://example.test/sub"
            deduplication = true
            filterMode = SubscriptionFilterMode.INCLUDE
            filterRegex = "alpha|beta"
            customUserAgent = ""
        }
        val ui = RecordingUi()
        val savedUi = GroupManager.userInterface
        GroupManager.userInterface = ui
        try {
            withPorts(client, store) { RawUpdater.doUpdate(group, group.subscription!!, ui, true) }
        } finally {
            GroupManager.userInterface = savedUi
        }
        assertEquals("Name", group.name)
        assertEquals("upload=1; download=2", group.subscription!!.subscriptionUserinfo)
        assertTrue(group.subscription!!.lastUpdated > 0)
        val names = store.rows.map { it.displayName() }
        assertTrue(names.any { it == "alpha" || it.startsWith("alpha (") })
        assertTrue(names.contains("beta"))
        assertFalse(names.contains("skipme"))
        assertEquals(3, store.rows.size)
        assertEquals(3, ui.added.size)
        assertTrue(ui.duplicate.isNotEmpty())
        assertEquals(1, store.updatedGroups)

        client.userinfo = "from-header"
        client.contentDisposition = "attachment; filename*=UTF-8''Other%20Name"
        group.name = "Subscription #9"
        group.subscription!!.link = "https://example.test/sub"
        withPorts(client, store) { RawUpdater.doUpdate(group, group.subscription!!, ui, false) }
        assertEquals("from-header", group.subscription!!.subscriptionUserinfo)
        assertTrue(store.rows.all { it.userOrder > 0 })
    }

    @Test
    fun secondPassUpdatesReordersAndDeletes() {
        val body = """
            socks5://u:p@1.2.3.4:1080#alpha
            socks5://u:p@9.9.9.9:1080#beta
        """.trimIndent()
        val client = FakeSubscriptionClient(body, userinfo = "h")
        val store = MemoryProfileStore()
        val group = group("Kept")
        group.subscription!!.link = "https://example.test/sub"
        withPorts(client, store) { RawUpdater.doUpdate(group, group.subscription!!, null, true) }
        assertEquals(2, store.rows.size)

        val alpha = store.rows.first { it.displayName() == "alpha" }
        alpha.userOrder = 50
        val beta = store.rows.first { it.displayName() == "beta" }
        beta.socksBean!!.serverAddress = "0.0.0.0"
        beta.socksBean!!.customOutboundJson = """{"tag":"keep"}"""
        store.rows += socksEntity(70, group.id, "gone", "4.4.4.4")
        store.reportedCount = 0
        withPorts(client, store) { RawUpdater.doUpdate(group, group.subscription!!, null, true) }
        assertTrue(store.rows.none { it.displayName() == "gone" })
        val updatedBeta = store.rows.first { it.displayName() == "beta" }
        assertEquals("9.9.9.9", updatedBeta.socksBean!!.serverAddress)
        assertEquals("""{"tag":"keep"}""", updatedBeta.socksBean!!.customOutboundJson)
        assertNotEquals(50, store.rows.first { it.displayName() == "alpha" }.userOrder)
        assertEquals(0L, store.reportedCount)
    }

    @Test
    fun contentUriAndEmptySubscriptions() {
        val client = FakeSubscriptionClient("socks5://u:p@1.2.3.4:1080#office")
        val store = MemoryProfileStore()
        val group = group("file")
        group.subscription!!.link = "content://profiles/office"
        withPorts(client, store) { RawUpdater.doUpdate(group, group.subscription!!, null, true) }
        assertEquals("office", store.rows.single().displayName())

        client.content = null
        val missing = assertThrows(IllegalStateException::class.java) {
            withPorts(client, store) { RawUpdater.doUpdate(group, group.subscription!!, null, true) }
        }
        assertEquals("no proxies in subscription", missing.message)

        val httpGroup = group("http")
        httpGroup.subscription!!.link = "https://example.test/empty"
        client.body = "not a subscription"
        val empty = assertThrows(IllegalStateException::class.java) {
            withPorts(client, store) { RawUpdater.doUpdate(httpGroup, httpGroup.subscription!!, null, true) }
        }
        assertEquals("no proxies", empty.message)

        client.body = "socks5://u:p@1.2.3.4:1080#alpha"
        httpGroup.subscription!!.apply {
            filterMode = SubscriptionFilterMode.EXCLUDE
            filterRegex = "alpha"
        }
        val filtered = assertThrows(IllegalStateException::class.java) {
            withPorts(client, store) { RawUpdater.doUpdate(httpGroup, httpGroup.subscription!!, null, true) }
        }
        assertEquals("no proxies", filtered.message)
    }

    @Test
    fun forceResolveRewritesNamesAndSkipsNaiveAndIps() {
        val http = HttpBean().apply {
            initializeDefaultValues()
            name = "http"
            serverAddress = "localhost"
            security = "tls"
            sni = ""
        }
        val vmess = VMessBean().apply {
            initializeDefaultValues()
            name = "vmess"
            serverAddress = "localhost"
            security = "tls"
            sni = ""
        }
        val trojan = TrojanBean().apply {
            initializeDefaultValues()
            name = "trojan"
            serverAddress = "localhost"
            sni = ""
        }
        val trojanGo = TrojanGoBean().apply {
            initializeDefaultValues()
            name = "tgo"
            serverAddress = "localhost"
            sni = ""
        }
        val hysteria = HysteriaBean().apply {
            initializeDefaultValues()
            name = "hy"
            serverAddress = "localhost"
            sni = ""
        }
        val naive = NaiveBean().apply {
            initializeDefaultValues()
            name = "naive"
            serverAddress = "localhost"
        }
        val ip = SOCKSBean().apply {
            initializeDefaultValues()
            name = "ip"
            serverAddress = "1.2.3.4"
        }
        val missing = SOCKSBean().apply {
            initializeDefaultValues()
            name = "missing"
            serverAddress = "not a host"
        }
        val beans = listOf(http, vmess, trojan, trojanGo, hysteria, naive, ip, missing)
        val source = io.nekohasekai.sagernet.fmt.MemoryConfigSource().apply {
            ipv6Mode = IPv6Mode.PREFER
            enableFakeDns = true
            serviceStarted = true
            serviceMode = Key.MODE_VPN
        }
        val previous = configSource
        configSource = source
        try {
            runBlocking { RawUpdater.forceResolve(beans, 4L) }
        } finally {
            configSource = previous
        }
        assertEquals("localhost", http.sni)
        assertNotEquals("localhost", http.serverAddress)
        assertEquals("localhost", vmess.sni)
        assertEquals("localhost", trojan.sni)
        assertEquals("localhost", trojanGo.sni)
        assertEquals("localhost", hysteria.sni)
        assertEquals("localhost", naive.serverAddress)
        assertEquals("1.2.3.4", ip.serverAddress)
        assertEquals("not a host", missing.serverAddress)
        assertTrue(GroupUpdater.progress.containsKey(4L))
        GroupUpdater.progress.remove(4L)
    }

    @Test
    fun executeUpdateConfirmsFailureAndApplies() {
        val previousState = DataStore.serviceState
        val previousWarning = GroupUpdater.subscriptionUpdateWarning
        val previousUi = GroupManager.userInterface
        GroupUpdater.subscriptionUpdateWarning = { "warn-text" }
        try {
            GroupUpdater.updating.add(3L)
            val cancelled = runCatching {
                runBlocking { GroupUpdater.executeUpdate(group("busy"), true) }
            }
            assertTrue(cancelled.exceptionOrNull() is CancellationException || cancelled.getOrNull() == true)
            GroupUpdater.updating.remove(3L)

            DataStore.serviceState = BaseService.State.Idle
            val declined = ConfirmUi(accept = false)
            GroupManager.userInterface = declined
            val httpGroup = group("http")
            httpGroup.subscription!!.link = "http://example.test/sub"
            val declinedResult = runCatching {
                runBlocking { GroupUpdater.executeUpdate(httpGroup, true) }
            }
            assertEquals("warn-text", declined.message)
            assertFalse(GroupUpdater.updating.contains(3L))
            assertTrue(
                declinedResult.exceptionOrNull() is CancellationException || declinedResult.getOrNull() == true,
            )

            GroupManager.userInterface = null
            val quiet = runCatching {
                runBlocking { GroupUpdater.executeUpdate(httpGroup, true) }
            }
            assertTrue(quiet.exceptionOrNull() is CancellationException || quiet.getOrNull() == true)

            val client = FakeSubscriptionClient("socks5://u:p@1.2.3.4:1080#alpha")
            val store = MemoryProfileStore()
            DataStore.serviceState = BaseService.State.Connected
            val connected = group("kept")
            connected.subscription!!.apply {
                link = "https://example.test/sub"
                filterMode = 9
                filterRegex = "alpha"
            }
            withPorts(client, store) {
                assertTrue(GroupUpdater.executeUpdate(connected, true))
            }
            assertEquals(1, store.rows.size)
            assertFalse(GroupUpdater.updating.contains(3L))

            client.contentDisposition = "attachment; filename*=UTF-8''Other%20Name"
            val renamed = group("Subscription #9")
            renamed.subscription!!.link = "https://example.test/sub"
            withPorts(client, store) {
                assertTrue(GroupUpdater.executeUpdate(renamed, false))
            }
            assertEquals("Other Name", renamed.name)

            val accepted = ConfirmUi(accept = true)
            GroupManager.userInterface = accepted
            DataStore.serviceState = BaseService.State.Idle
            val onlyWhenUp = group("when-up")
            onlyWhenUp.subscription!!.apply {
                link = "https://example.test/sub"
                updateWhenConnectedOnly = true
            }
            store.rows.clear()
            withPorts(client, store) {
                assertTrue(GroupUpdater.executeUpdate(onlyWhenUp, true))
            }
            assertEquals("warn-text", accepted.message)
            assertEquals(1, store.rows.size)

            client.body = ""
            val failed = ConfirmUi(accept = true)
            GroupManager.userInterface = failed
            DataStore.serviceState = BaseService.State.Connected
            val broken = group("broken")
            broken.subscription!!.link = "https://example.test/empty"
            var ok = true
            withPorts(client, store) {
                ok = GroupUpdater.executeUpdate(broken, true)
            }
            assertFalse(ok)
            assertTrue(failed.failure.isNotEmpty())
        } finally {
            DataStore.serviceState = previousState
            GroupUpdater.subscriptionUpdateWarning = previousWarning
            GroupManager.userInterface = previousUi
            GroupUpdater.updating.remove(3L)
        }
    }

    private fun group(name: String): ProxyGroup {
        return ProxyGroup(id = 3L, name = name, subscription = SubscriptionBean().apply {
            initializeDefaultValues()
        })
    }

    private fun socksEntity(id: Long, groupId: Long, name: String, host: String): ProxyEntity {
        return ProxyEntity(id = id, groupId = groupId, type = ProxyEntity.TYPE_SOCKS).apply {
            socksBean = SOCKSBean().apply {
                initializeDefaultValues()
                this.name = name
                serverAddress = host
                serverPort = 1080
            }
        }
    }
}

private class FakeSubscriptionClient(
    var body: String,
    var userinfo: String = "",
    var contentDisposition: String = "",
    var content: String? = body,
) : SubscriptionClient {
    override fun fetch(subscription: io.nekohasekai.sagernet.database.SubscriptionBean): SubscriptionFetch {
        return SubscriptionFetch(body, userinfo, contentDisposition)
    }

    override fun readContent(link: String): String? = content
    override fun noProxiesFound() = "no proxies"
    override fun noProxiesInSubscription() = "no proxies in subscription"
    override fun noProxiesFoundInFile() = "no proxies in file"
}

private class MemoryProfileStore : ProfileStore {
    val rows = mutableListOf<ProxyEntity>()
    var nextId = 1L
    var updatedGroups = 0
    var reportedCount: Long? = null

    override fun proxiesByGroup(groupId: Long) = rows.filter { it.groupId == groupId }
    override fun addProxy(proxy: ProxyEntity): Long {
        if (proxy.id == 0L) proxy.id = nextId++
        rows += proxy
        return proxy.id
    }

    override fun updateProxies(proxies: List<ProxyEntity>): Int = proxies.size
    override fun deleteProxies(proxies: List<ProxyEntity>): Int {
        rows.removeAll(proxies.toSet())
        return proxies.size
    }

    override fun countByGroup(groupId: Long) = reportedCount ?: rows.count { it.groupId == groupId }.toLong()
    override fun updateGroup(group: ProxyGroup) {
        updatedGroups++
    }
}

private class ConfirmUi(private val accept: Boolean) : GroupManager.Interface {
    var message = ""
    var failure = ""
    override suspend fun confirm(message: String): Boolean {
        this.message = message
        return accept
    }

    override suspend fun alert(message: String) {}
    override suspend fun onUpdateSuccess(
        group: ProxyGroup,
        changed: Int,
        added: List<String>,
        updated: Map<String, String>,
        deleted: List<String>,
        duplicate: List<String>,
        byUser: Boolean,
    ) {
    }

    override suspend fun onUpdateFailure(group: ProxyGroup, message: String) {
        failure = message
    }
}

private class RecordingUi : GroupManager.Interface {
    var added = emptyList<String>()
    var duplicate = emptyList<String>()
    override suspend fun confirm(message: String) = true
    override suspend fun alert(message: String) {}
    override suspend fun onUpdateSuccess(
        group: ProxyGroup,
        changed: Int,
        added: List<String>,
        updated: Map<String, String>,
        deleted: List<String>,
        duplicate: List<String>,
        byUser: Boolean,
    ) {
        this.added = added
        this.duplicate = duplicate
    }

    override suspend fun onUpdateFailure(group: ProxyGroup, message: String) {}
}

private fun withPorts(client: SubscriptionClient, store: ProfileStore, block: suspend () -> Unit) {
    val previousClient = subscriptionClient
    val previousStore = profileStore
    subscriptionClient = client
    profileStore = store
    try {
        runBlocking { block() }
    } finally {
        subscriptionClient = previousClient
        profileStore = previousStore
    }
}
