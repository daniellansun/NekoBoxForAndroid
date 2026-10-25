package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.fmt.ProductionConfigSource
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.ProductionProfileStore
import io.nekohasekai.sagernet.group.ProductionSubscriptionClient
import io.nekohasekai.sagernet.group.ProfileStore
import io.nekohasekai.sagernet.group.SubscriptionClient
import io.nekohasekai.sagernet.group.SubscriptionFetch
import io.nekohasekai.sagernet.group.noProxiesFoundText
import io.nekohasekai.sagernet.group.noProxiesInFileText
import io.nekohasekai.sagernet.group.noProxiesInSubscriptionText
import io.nekohasekai.sagernet.group.profileStore
import io.nekohasekai.sagernet.group.readContentText
import io.nekohasekai.sagernet.group.subscriptionClient
import java.io.IOException
import java.sql.SQLException
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProfileManagerJvmTest {
    private lateinit var groups: MemoryGroupDao
    private lateinit var proxies: MemoryProxyDao
    private lateinit var rules: MemoryRuleDao
    private lateinit var previousRuleText: (Int, Array<out Any>) -> String

    @Before
    fun install() {
        groups = MemoryGroupDao()
        proxies = MemoryProxyDao()
        rules = MemoryRuleDao()
        SagerDatabase.groupDaoOverride = groups
        SagerDatabase.proxyDaoOverride = proxies
        SagerDatabase.rulesDaoOverride = rules
        DataStore.configurationStoreOverride = RoomPreferenceDataStore(MemoryKvDao())
        DataStore.profileCacheStoreOverride = RoomPreferenceDataStore(MemoryKvDao())
        DataStore.userIndexOverride = 0
        DataStore.serviceState = BaseService.State.Idle
        groupDirectory = RoomGroupDirectory
        previousRuleText = ProfileManager.ruleTextProvider
        ProfileManager.ruleTextProvider = { resId, args ->
            if (args.isEmpty()) "s$resId" else "s$resId:${args.joinToString(",")}"
        }
        ProxyEntity.chainNameOverride = "Chain"
        SubscriptionUpdater.reconfigureOverride = {}
    }

    @After
    fun restore() {
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        DataStore.configurationStoreOverride = null
        DataStore.profileCacheStoreOverride = null
        DataStore.userIndexOverride = null
        DataStore.serviceState = BaseService.State.Idle
        groupDirectory = RoomGroupDirectory
        ProfileManager.ruleTextProvider = previousRuleText
        ProxyEntity.chainNameOverride = null
        SubscriptionUpdater.reconfigureOverride = null
        GroupManager.userInterface = null
    }

    @Test
    fun profileLifecycle() {
        runBlocking {
            val added = mutableListOf<Long>()
            val removed = mutableListOf<Long>()
            val updated = mutableListOf<Long>()
            val traffic = mutableListOf<Int>()
            val listener = object : ProfileManager.Listener {
                override suspend fun onAdd(profile: ProxyEntity) {
                    added += profile.id
                }

                override suspend fun onUpdated(data: List<TrafficData>) {
                    traffic += data.size
                }

                override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
                    updated += profile.id
                }

                override suspend fun onRemoved(groupId: Long, profileId: Long) {
                    removed += profileId
                }
            }
            ProfileManager.addListener(listener)
            try {
                assertNull(ProfileManager.getProfile(0L))
                assertTrue(ProfileManager.getProfiles(emptyList()).isEmpty())
                val first = ProfileManager.createProfile(3L, socks("a", "1.1.1.1"))
                val second = ProfileManager.createProfile(3L, socks("b", "2.2.2.2"))
                assertEquals(listOf(first.id, second.id), added)
                assertEquals(1L, first.userOrder)
                assertEquals(2L, second.userOrder)
                assertEquals(first.id, ProfileManager.getProfile(first.id)!!.id)
                assertEquals(2, ProfileManager.getProfiles(listOf(second.id, first.id)).size)
                first.status = 4
                ProfileManager.updateProfile(first)
                ProfileManager.updateProfile(listOf(second))
                assertEquals(listOf(first.id, second.id), updated)
                ProfileManager.updateTraffic(first.id, 9, 8)
                assertEquals(9L, proxies.getById(first.id)!!.rx)
                assertEquals(8L, proxies.getById(first.id)!!.tx)
                ProfileManager.resetTraffic(longArrayOf())
                ProfileManager.resetTraffic(longArrayOf(first.id))
                assertEquals(0L, proxies.getById(first.id)!!.rx)
                ProfileManager.postUpdate(emptyList())
                ProfileManager.postUpdate(listOf(TrafficData(id = first.id, rx = 1, tx = 2)))
                assertEquals(listOf(1), traffic)
                ProfileManager.postUpdate(first, true)
                ProfileManager.postUpdate(first.id, true)
                ProfileManager.postUpdate(404L)
                DataStore.selectedProxy = second.id
                ProfileManager.deleteProfile2(3L, second.id)
                assertEquals(0L, DataStore.selectedProxy)
                assertTrue(removed.isEmpty())
                assertNull(proxies.getById(second.id))
                DataStore.selectedProxy = first.id
                ProfileManager.deleteProfile(3L, first.id)
                assertEquals(listOf(first.id), removed)
                assertEquals(0L, DataStore.selectedProxy)
                ProfileManager.deleteProfile(3L, 99L)

                val p1 = ProfileManager.createProfile(4L, socks("c", "3.3.3.3"))
                val p2 = ProfileManager.createProfile(4L, socks("d", "4.4.4.4"))
                val p3 = ProfileManager.createProfile(4L, socks("e", "5.5.5.5"))
                ProfileManager.deleteProfile(4L, p1.id)
                val left = proxies.getByGroup(4L)
                assertEquals(listOf(1L, 2L), left.map { it.userOrder })
                assertEquals(listOf(p2.id, p3.id), left.map { it.id })
                assertEquals(0, TrafficData(id = 1, tx = 2, rx = 3).describeContents())
                assertEquals(0, SpeedDisplayData(txRateProxy = 1).describeContents())
            } finally {
                ProfileManager.removeListener(listener)
            }
        }
    }

    @Test
    fun profileReadErrors() {
        proxies.getByIdError = SQLiteCantOpenDatabaseException("closed")
        try {
            ProfileManager.getProfile(1L)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.cause is SQLiteCantOpenDatabaseException)
        }
        proxies.getByIdError = SQLException("sql")
        assertNull(ProfileManager.getProfile(1L))
        proxies.getByIdError = null

        proxies.getEntitiesError = SQLiteCantOpenDatabaseException("closed")
        try {
            ProfileManager.getProfiles(listOf(1L))
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.cause is SQLiteCantOpenDatabaseException)
        }
        proxies.getEntitiesError = SQLException("sql")
        assertTrue(ProfileManager.getProfiles(listOf(1L)).isEmpty())
    }

    @Test
    fun defaultRulesFollowLocale() {
        runBlocking {
            val previous = Locale.getDefault()
            val added = mutableListOf<String>()
            val listener = object : ProfileManager.RuleListener {
                override suspend fun onAdd(rule: RuleEntity) {
                    added += rule.name
                }

                override suspend fun onUpdated(rule: RuleEntity) {}
                override suspend fun onRemoved(ruleId: Long) {}
                override suspend fun onCleared() {}
            }
            ProfileManager.addListener(listener)
            try {
                Locale.setDefault(Locale.US)
                DataStore.rulesFirstCreate = true
                assertTrue(ProfileManager.getRules().isEmpty())
                DataStore.rulesFirstCreate = false
                val us = ProfileManager.getRules()
                assertEquals(9, us.size)
                assertEquals(2, added.size)
                assertTrue(DataStore.rulesFirstCreate)
                assertEquals(9, ProfileManager.getRules().size)

                rules.rows.clear()
                DataStore.rulesFirstCreate = false
                Locale.setDefault(Locale.CHINA)
                assertEquals(5, ProfileManager.getRules().size)
            } finally {
                ProfileManager.removeListener(listener)
                Locale.setDefault(previous)
            }
        }
    }

    @Test
    fun ruleEditsNotifyListeners() {
        runBlocking {
            val removed = mutableListOf<Long>()
            val updated = mutableListOf<Long>()
            val listener = object : ProfileManager.RuleListener {
                override suspend fun onAdd(rule: RuleEntity) {}
                override suspend fun onUpdated(rule: RuleEntity) {
                    updated += rule.id
                }

                override suspend fun onRemoved(ruleId: Long) {
                    removed += ruleId
                }

                override suspend fun onCleared() {}
            }
            ProfileManager.addListener(listener)
            try {
                DataStore.rulesFirstCreate = true
                val rule = ProfileManager.createRule(RuleEntity(name = "one", enabled = true))
                rule.name = "renamed"
                ProfileManager.updateRule(rule)
                assertEquals(listOf(rule.id), updated)
                val quiet = ProfileManager.createRule(RuleEntity(name = "two"), post = false)
                ProfileManager.deleteRule(rule.id)
                ProfileManager.deleteRules(listOf(quiet))
                assertEquals(listOf(rule.id, quiet.id), removed)
                val summary = RuleEntity(
                    name = "named",
                    config = "cfg",
                    domains = "example.com",
                    ip = "1.1.1.1",
                    port = "80",
                    sourcePort = "1",
                    network = "tcp",
                    source = "10.0.0.0/8",
                    protocol = "tls",
                    ruleset = "geo",
                )
                assertTrue(summary.mkSummary().endsWith("..."))
                assertEquals("Rule 8", RuleEntity(id = 8).displayName())
                assertEquals("named", summary.displayName())
            } finally {
                ProfileManager.removeListener(listener)
            }
        }
    }

    @Test
    fun groupLifecycleReconfiguresSubscriptions() {
        runBlocking {
            var reconfigured = 0
            SubscriptionUpdater.reconfigureOverride = { reconfigured++ }
            val events = mutableListOf<String>()
            val listener = object : GroupManager.Listener {
                override suspend fun groupAdd(group: ProxyGroup) {
                    events += "add:${group.id}"
                }

                override suspend fun groupUpdated(group: ProxyGroup) {
                    events += "upd:${group.id}"
                }

                override suspend fun groupRemoved(groupId: Long) {
                    events += "rm:$groupId"
                }

                override suspend fun groupUpdated(groupId: Long) {
                    events += "id:$groupId"
                }
            }
            GroupManager.addListener(listener)
            try {
                val basic = GroupManager.createGroup(ProxyGroup(name = "basic"))
                assertEquals(0, reconfigured)
                val sub = GroupManager.createGroup(
                    ProxyGroup(name = "sub", type = GroupType.SUBSCRIPTION),
                )
                assertEquals(1, reconfigured)
                sub.name = "sub2"
                GroupManager.updateGroup(sub)
                assertEquals(2, reconfigured)
                GroupManager.updateGroup(basic)
                assertEquals(2, reconfigured)
                GroupManager.postReload(sub.id)
                GroupManager.postUpdate(sub)
                GroupManager.postUpdate(sub.id)
                GroupManager.postUpdate(999L)
                assertTrue(events.contains("id:${sub.id}"))
                val profile = ProfileManager.createProfile(basic.id, socks("g", "9.9.9.9"))
                DataStore.selectedProxy = profile.id
                GroupManager.clearGroup(basic.id)
                assertEquals(0L, DataStore.selectedProxy)
                assertEquals(0L, proxies.countByGroup(basic.id))
                GroupManager.deleteGroup(basic.id)
                GroupManager.deleteGroup(listOf(sub))
                assertEquals(4, reconfigured)
                assertTrue(events.any { it == "rm:${basic.id}" })
                assertTrue(events.any { it == "rm:${sub.id}" })
            } finally {
                GroupManager.removeListener(listener)
            }
        }
    }

    @Test
    fun roomGroupDirectoryUsesDaoOverride() {
        groups.reset()
        DataStore.configurationStore.remove(Key.PROFILE_GROUP)
        val created = DataStore.currentGroupId()
        val row = groups.getById(created)
        assertNotNull(row)
        assertTrue(row!!.ungrouped)
        assertEquals(created, DataStore.currentGroup().id)

        groups.reset()
        val basic = ProxyGroup(name = "basic", type = GroupType.BASIC)
        basic.id = groups.createGroup(basic)
        val sub = ProxyGroup(name = "sub", type = GroupType.SUBSCRIPTION)
        sub.id = groups.createGroup(sub)
        DataStore.selectedGroup = sub.id
        assertEquals(sub.id, DataStore.currentGroup().id)
        assertEquals(basic.id, DataStore.selectedGroupForImport())
    }

    @Test
    fun productionReadersUseDaoOverride() {
        val group = ProxyGroup(name = "g")
        group.id = groups.createGroup(group)
        val proxy = ProxyEntity(groupId = group.id, type = ProxyEntity.TYPE_SOCKS).apply {
            socksBean = socks("p", "8.8.8.8")
        }
        proxy.id = proxies.addProxy(proxy)
        val enabled = RuleEntity(name = "on", enabled = true, userOrder = 1, packages = setOf("a"))
        enabled.id = rules.createRule(enabled)
        rules.createRule(RuleEntity(name = "off", enabled = false, userOrder = 2))
        assertEquals(group.id, ProductionConfigSource.groupById(group.id)!!.id)
        assertEquals(1, ProductionConfigSource.proxiesByIds(listOf(proxy.id)).size)
        assertEquals(proxy.id, ProductionConfigSource.proxyById(proxy.id)!!.id)
        assertEquals(listOf(enabled.id), ProductionConfigSource.enabledRules().map { it.id })
        assertEquals(1, ProductionConfigSource.proxiesByGroup(group.id).size)
        assertEquals(1, rules.checkVpnNeeded().size)

        assertEquals(1, ProductionProfileStore.proxiesByGroup(group.id).size)
        val added = ProductionProfileStore.addProxy(ProxyEntity(groupId = group.id))
        assertTrue(added > 0)
        assertEquals(1, ProductionProfileStore.updateProxies(listOf(proxy)))
        assertEquals(1, ProductionProfileStore.deleteProxies(listOf(proxies.getById(added)!!)))
        assertTrue(ProductionProfileStore.countByGroup(group.id) >= 1)
        ProductionProfileStore.updateGroup(group)
    }

    @Test
    fun proxyEntityActivitiesChainAndCreators() {
        val expected = listOf(
            ProxyEntity.TYPE_SOCKS to "SocksSettingsActivity",
            ProxyEntity.TYPE_HTTP to "HttpSettingsActivity",
            ProxyEntity.TYPE_SS to "ShadowsocksSettingsActivity",
            ProxyEntity.TYPE_SSR to "ShadowsocksRSettingsActivity",
            ProxyEntity.TYPE_VMESS to "VMessSettingsActivity",
            ProxyEntity.TYPE_TROJAN to "TrojanSettingsActivity",
            ProxyEntity.TYPE_TROJAN_GO to "TrojanGoSettingsActivity",
            ProxyEntity.TYPE_MIERU to "MieruSettingsActivity",
            ProxyEntity.TYPE_NAIVE to "NaiveSettingsActivity",
            ProxyEntity.TYPE_HYSTERIA to "HysteriaSettingsActivity",
            ProxyEntity.TYPE_SSH to "SSHSettingsActivity",
            ProxyEntity.TYPE_WG to "WireGuardSettingsActivity",
            ProxyEntity.TYPE_TUIC to "TuicSettingsActivity",
            ProxyEntity.TYPE_JUICITY to "JuicitySettingsActivity",
            ProxyEntity.TYPE_SHADOWTLS to "ShadowTLSSettingsActivity",
            ProxyEntity.TYPE_ANYTLS to "AnyTLSSettingsActivity",
            ProxyEntity.TYPE_CHAIN to "ChainSettingsActivity",
            ProxyEntity.TYPE_CONFIG to "ConfigSettingActivity",
            ProxyEntity.TYPE_SNELL to "SnellSettingsActivity",
        )
        for ((type, name) in expected) {
            assertEquals(name, ProxyEntity(type = type).settingsActivity().simpleName)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProxyEntity(type = ProxyEntity.TYPE_NEKO).settingsActivity()
        }
        assertEquals("Chain", ProxyEntity(type = ProxyEntity.TYPE_CHAIN).displayType())
        val undefined = ProxyEntity(type = 123)
        assertEquals("Undefined type 123", undefined.displayType())
        assertThrows(IllegalStateException::class.java) { undefined.requireBean() }
        assertEquals(0, ProxyEntity().describeContents())
        assertNotNull(ProxyEntity.CREATOR.newInstance())
        assertEquals(2, ProxyEntity.CREATOR.newArray(2).size)
        assertNotNull(ProxyGroup.CREATOR.newInstance())
        assertEquals(1, ProxyGroup.CREATOR.newArray(1).size)
        assertEquals("named", ProxyGroup(name = "named").displayName())
        val subscriptionCreator =
            SubscriptionBean.CREATOR as io.nekohasekai.sagernet.fmt.Serializable.CREATOR<SubscriptionBean>
        assertNotNull(subscriptionCreator.newInstance())
        assertEquals(1, subscriptionCreator.newArray(1).size)
    }

    @Test
    fun subscriptionTextAndStartUpdate() {
        val previousRead = readContentText
        val previousNone = noProxiesFoundText
        val previousSub = noProxiesInSubscriptionText
        val previousFile = noProxiesInFileText
        val previousClient = subscriptionClient
        val previousStore = profileStore
        val previousUi = GroupManager.userInterface
        try {
            readContentText = { "body:$it" }
            noProxiesFoundText = { "none" }
            noProxiesInSubscriptionText = { "sub" }
            noProxiesInFileText = { "file" }
            assertEquals("body:content://x", ProductionSubscriptionClient.readContent("content://x"))
            assertEquals("none", ProductionSubscriptionClient.noProxiesFound())
            assertEquals("sub", ProductionSubscriptionClient.noProxiesInSubscription())
            assertEquals("file", ProductionSubscriptionClient.noProxiesFoundInFile())

            val store = MemoryProfileStore()
            subscriptionClient = object : SubscriptionClient {
                override fun fetch(subscription: SubscriptionBean) =
                    SubscriptionFetch("socks5://127.0.0.1:1080#one", "", "")

                override fun readContent(link: String) = null
                override fun noProxiesFound() = "none"
                override fun noProxiesInSubscription() = "sub"
                override fun noProxiesFoundInFile() = "file"
            }
            profileStore = store
            val group = ProxyGroup(
                id = 77L,
                name = "sub",
                subscription = SubscriptionBean().apply {
                    initializeDefaultValues()
                    link = "https://example/sub"
                },
            )
            runBlocking {
                GroupUpdater.startUpdate(group, false).join()
            }
            assertEquals(1, store.rows.size)
            assertTrue(store.updatedGroups > 0)
        } finally {
            readContentText = previousRead
            noProxiesFoundText = previousNone
            noProxiesInSubscriptionText = previousSub
            noProxiesInFileText = previousFile
            subscriptionClient = previousClient
            profileStore = previousStore
            GroupManager.userInterface = previousUi
            GroupUpdater.updating.remove(77L)
        }
    }

    private fun socks(name: String, host: String) = SOCKSBean().apply {
        this.name = name
        serverAddress = host
        serverPort = 1080
    }
}

internal class MemoryGroupDao : ProxyGroup.Dao {
    val rows = mutableListOf<ProxyGroup>()
    private var nextId = 1L

    override fun allGroups() = rows.sortedBy { it.userOrder }
    override suspend fun subscriptions() = rows.filter { it.type == GroupType.SUBSCRIPTION }
    override fun nextOrder(): Long? = rows.maxOfOrNull { it.userOrder }?.plus(1)
    override fun getById(groupId: Long) = rows.find { it.id == groupId }
    override fun deleteById(groupId: Long): Int {
        val before = rows.size
        rows.removeAll { it.id == groupId }
        return before - rows.size
    }

    override fun deleteGroup(group: ProxyGroup) {
        deleteById(group.id)
    }

    override fun deleteGroup(groupList: List<ProxyGroup>) {
        val ids = groupList.map { it.id }.toSet()
        rows.removeAll { it.id in ids }
    }

    override fun createGroup(group: ProxyGroup): Long {
        if (group.id == 0L) group.id = nextId++
        else if (group.id >= nextId) nextId = group.id + 1
        rows += group
        return group.id
    }

    override fun updateGroup(group: ProxyGroup) {
        val index = rows.indexOfFirst { it.id == group.id }
        if (index >= 0) rows[index] = group
    }

    override fun reset() {
        rows.clear()
    }

    override fun insert(groupList: List<ProxyGroup>) {
        groupList.forEach { createGroup(it) }
    }
}

internal class MemoryProxyDao : ProxyEntity.Dao {
    val rows = mutableListOf<ProxyEntity>()
    private var nextId = 1L
    var getByIdError: Throwable? = null
    var getEntitiesError: Throwable? = null

    override fun getAll() = rows.toList()
    override fun getIdsByGroup(groupId: Long) =
        rows.filter { it.groupId == groupId }.sortedBy { it.userOrder }.map { it.id }

    override fun getByGroup(groupId: Long) =
        rows.filter { it.groupId == groupId }.sortedBy { it.userOrder }

    override fun getEntities(proxyIds: List<Long>): List<ProxyEntity> {
        getEntitiesError?.let { throw it }
        val wanted = proxyIds.toSet()
        return rows.filter { it.id in wanted }
    }

    override fun countByGroup(groupId: Long) = rows.count { it.groupId == groupId }.toLong()
    override fun nextOrder(groupId: Long): Long? =
        rows.filter { it.groupId == groupId }.maxOfOrNull { it.userOrder }?.plus(1)

    override fun getById(proxyId: Long): ProxyEntity? {
        getByIdError?.let { throw it }
        return rows.find { it.id == proxyId }
    }

    override fun deleteById(proxyId: Long): Int {
        val before = rows.size
        rows.removeAll { it.id == proxyId }
        return before - rows.size
    }

    override fun deleteByGroup(groupId: Long) {
        rows.removeAll { it.groupId == groupId }
    }

    override fun deleteByGroup(groupId: LongArray) {
        val ids = groupId.toSet()
        rows.removeAll { it.groupId in ids }
    }

    override fun deleteProxy(proxy: ProxyEntity) = deleteById(proxy.id)
    override fun deleteProxy(proxies: List<ProxyEntity>): Int {
        val ids = proxies.map { it.id }.toSet()
        val before = rows.size
        rows.removeAll { it.id in ids }
        return before - rows.size
    }

    override fun updateProxy(proxy: ProxyEntity): Int {
        val index = rows.indexOfFirst { it.id == proxy.id }
        if (index < 0) return 0
        rows[index] = proxy
        return 1
    }

    override fun updateProxy(proxies: List<ProxyEntity>): Int = proxies.sumOf { updateProxy(it) }
    override fun updateTraffic(proxyId: Long, rx: Long, tx: Long): Int {
        val row = rows.find { it.id == proxyId } ?: return 0
        row.rx = rx
        row.tx = tx
        return 1
    }

    override fun resetTraffic(profileIds: LongArray): Int {
        val ids = profileIds.toSet()
        var count = 0
        for (row in rows) {
            if (row.id in ids) {
                row.rx = 0
                row.tx = 0
                count++
            }
        }
        return count
    }

    override fun addProxy(proxy: ProxyEntity): Long {
        if (proxy.id == 0L) proxy.id = nextId++
        else if (proxy.id >= nextId) nextId = proxy.id + 1
        rows += proxy
        return proxy.id
    }

    override fun insert(proxies: List<ProxyEntity>) {
        proxies.forEach { addProxy(it) }
    }

    override fun deleteAll(groupId: Long): Int {
        val before = rows.size
        deleteByGroup(groupId)
        return before - rows.size
    }

    override fun reset() {
        rows.clear()
    }
}

internal class MemoryRuleDao : RuleEntity.Dao {
    val rows = mutableListOf<RuleEntity>()
    private var nextId = 1L

    override fun checkVpnNeeded() = rows.filter { it.enabled && it.packages.isNotEmpty() }
    override fun allRules() = rows.sortedBy { it.userOrder }
    override fun enabledRules(enabled: Boolean) =
        rows.filter { it.enabled == enabled }.sortedBy { it.userOrder }

    override fun nextOrder(): Long? = rows.maxOfOrNull { it.userOrder }?.plus(1)
    override fun getById(ruleId: Long) = rows.find { it.id == ruleId }
    override fun deleteById(ruleId: Long): Int {
        val before = rows.size
        rows.removeAll { it.id == ruleId }
        return before - rows.size
    }

    override fun deleteRule(rule: RuleEntity) {
        deleteById(rule.id)
    }

    override fun deleteRules(rules: List<RuleEntity>) {
        val ids = rules.map { it.id }.toSet()
        rows.removeAll { it.id in ids }
    }

    override fun createRule(rule: RuleEntity): Long {
        if (rule.id == 0L) rule.id = nextId++
        else if (rule.id >= nextId) nextId = rule.id + 1
        rows += rule
        return rule.id
    }

    override fun updateRule(rule: RuleEntity) {
        val index = rows.indexOfFirst { it.id == rule.id }
        if (index >= 0) rows[index] = rule
    }

    override fun updateRules(rules: List<RuleEntity>) {
        rules.forEach { updateRule(it) }
    }

    override fun reset() {
        rows.clear()
    }

    override fun insert(rules: List<RuleEntity>) {
        rules.forEach { createRule(it) }
    }
}

private class MemoryProfileStore : ProfileStore {
    val rows = mutableListOf<ProxyEntity>()
    var nextId = 1L
    var updatedGroups = 0

    override fun proxiesByGroup(groupId: Long) = rows.filter { it.groupId == groupId }
    override fun addProxy(proxy: ProxyEntity): Long {
        if (proxy.id == 0L) proxy.id = nextId++
        rows += proxy
        return proxy.id
    }

    override fun updateProxies(proxies: List<ProxyEntity>) = proxies.size
    override fun deleteProxies(proxies: List<ProxyEntity>): Int {
        rows.removeAll(proxies.toSet())
        return proxies.size
    }

    override fun countByGroup(groupId: Long) = rows.count { it.groupId == groupId }.toLong()
    override fun updateGroup(group: ProxyGroup) {
        updatedGroups++
    }
}
