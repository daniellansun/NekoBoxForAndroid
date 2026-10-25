package io.nekohasekai.sagernet

import android.os.Build
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.StringCollectionConverter
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.MemoryConfigSource
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.withConfig
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.getValue
import io.nekohasekai.sagernet.ktx.setValue
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.filterIsInstance
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.getBool
import io.nekohasekai.sagernet.ktx.getIntNya
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.isJsonObjectValid
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.ktx.urlSafe
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean
import moe.matsuri.nb4a.proxy.config.ConfigBean
import moe.matsuri.nb4a.proxy.neko.NekoBean
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSBean
import moe.matsuri.nb4a.utils.JavaUtil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch3HelperTest {
    @Test
    fun kryoConvertersAndProxyEntityRoundTrip() {
        val samples = listOf(
            ProxyEntity.TYPE_SOCKS to SOCKSBean(),
            ProxyEntity.TYPE_HTTP to HttpBean(),
            ProxyEntity.TYPE_SS to ShadowsocksBean(),
            ProxyEntity.TYPE_SSR to ShadowsocksRBean(),
            ProxyEntity.TYPE_VMESS to VMessBean(),
            ProxyEntity.TYPE_TROJAN to TrojanBean(),
            ProxyEntity.TYPE_TROJAN_GO to TrojanGoBean(),
            ProxyEntity.TYPE_MIERU to MieruBean(),
            ProxyEntity.TYPE_NAIVE to NaiveBean(),
            ProxyEntity.TYPE_HYSTERIA to HysteriaBean(),
            ProxyEntity.TYPE_SSH to SSHBean(),
            ProxyEntity.TYPE_WG to WireGuardBean(),
            ProxyEntity.TYPE_TUIC to TuicBean(),
            ProxyEntity.TYPE_JUICITY to JuicityBean(),
            ProxyEntity.TYPE_SHADOWTLS to ShadowTLSBean(),
            ProxyEntity.TYPE_ANYTLS to AnyTLSBean(),
            ProxyEntity.TYPE_CHAIN to ChainBean(),
            ProxyEntity.TYPE_NEKO to NekoBean(),
            ProxyEntity.TYPE_CONFIG to ConfigBean(),
            ProxyEntity.TYPE_SNELL to SnellBean(),
        )
        for ((type, bean) in samples) {
            bean.initializeDefaultValues()
            bean.name = "n-$type"
            bean.serverAddress = "10.0.0.$type"
            val entity = ProxyEntity(id = type.toLong(), groupId = 2, type = type, userOrder = 3).apply {
                putBean(bean)
            }
            assertTrue(entity.haveLink() || type == ProxyEntity.TYPE_CHAIN)
            if (type != ProxyEntity.TYPE_CHAIN && type != ProxyEntity.TYPE_CONFIG) {
                assertEquals(bean.serverAddress, entity.displayAddress().substringBefore(":"))
            }
            val restored = KryoConverters.deserialize(ProxyEntity(), KryoConverters.serialize(entity))
            if (type != ProxyEntity.TYPE_CHAIN && type != ProxyEntity.TYPE_CONFIG) {
                assertEquals(bean.serverAddress, restored.requireBean().serverAddress)
            }
            assertEquals("n-$type", restored.displayName())
            if (type != ProxyEntity.TYPE_CHAIN) {
                assertFalse(restored.displayType().isEmpty())
            }
        }
        assertNull(KryoConverters.httpDeserialize(null))
        assertNull(KryoConverters.httpDeserialize(byteArrayOf()))
        assertNull(KryoConverters.shadowsocksDeserialize(byteArrayOf()))
        assertNull(KryoConverters.shadowsocksrDeserialize(byteArrayOf()))
        assertNull(KryoConverters.configDeserialize(byteArrayOf()))
        assertNull(KryoConverters.vmessDeserialize(byteArrayOf()))
        assertNull(KryoConverters.trojanDeserialize(byteArrayOf()))
        assertNull(KryoConverters.trojanGoDeserialize(byteArrayOf()))
        assertNull(KryoConverters.mieruDeserialize(byteArrayOf()))
        assertNull(KryoConverters.naiveDeserialize(byteArrayOf()))
        assertNull(KryoConverters.hysteriaDeserialize(byteArrayOf()))
        assertNull(KryoConverters.sshDeserialize(byteArrayOf()))
        assertNull(KryoConverters.wireguardDeserialize(byteArrayOf()))
        assertNull(KryoConverters.tuicDeserialize(byteArrayOf()))
        assertNull(KryoConverters.juicityDeserialize(byteArrayOf()))
        assertNull(KryoConverters.shadowTLSDeserialize(byteArrayOf()))
        assertNull(KryoConverters.anyTLSDeserialize(byteArrayOf()))
        assertNull(KryoConverters.snellDeserialize(byteArrayOf()))
        assertNull(KryoConverters.chainDeserialize(byteArrayOf()))
        assertNull(KryoConverters.nekoDeserialize(byteArrayOf()))
        assertNull(KryoConverters.subscriptionDeserialize(byteArrayOf()))

        val ssh = entity(ProxyEntity.TYPE_SSH, SSHBean())
        assertFalse(ssh.haveStandardLink())
        assertTrue(ssh.toStdLink().startsWith("sn://"))
        val socks = entity(ProxyEntity.TYPE_SOCKS, SOCKSBean())
        assertTrue(socks.haveStandardLink())
        assertTrue(socks.toStdLink().startsWith("socks"))
        val vmess = entity(ProxyEntity.TYPE_VMESS, VMessBean())
        assertTrue(vmess.toStdLink().startsWith("vmess://"))

        val group = ProxyGroup(id = 8, name = "sub", type = GroupType.SUBSCRIPTION).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "https://example.test/sub"
            }
        }
        val groupBack = KryoConverters.deserialize(ProxyGroup(), KryoConverters.serialize(group))
        assertEquals("https://example.test/sub", groupBack.subscription!!.link)
        group.export = true
        val shared = KryoConverters.deserialize(ProxyGroup().apply { export = true }, KryoConverters.serialize(group))
        assertEquals("sub", shared.name)
        assertEquals("https://example.test/sub", shared.subscription!!.link)

        val longRule = RuleEntity(
            domains = "a.com",
            ip = "1.1.1.1",
            port = "80",
            network = "tcp",
            protocol = "tls",
        )
        assertTrue(longRule.mkSummary().endsWith("..."))
        assertEquals("b.com", RuleEntity(domains = "b.com").mkSummary())
        assertEquals("Rule 4", RuleEntity(id = 4).displayName())

        assertEquals("", StringCollectionConverter.fromSet(emptySet()))
        assertEquals(emptySet<String>(), StringCollectionConverter.toSet(" "))
        assertEquals(setOf("a", "b"), StringCollectionConverter.toSet(StringCollectionConverter.fromSet(setOf("a", "b"))))

        val pair = KeyValuePair("k")
        assertNull(pair.boolean)
        assertNull(pair.float)
        assertNull(pair.int)
        assertNull(pair.long)
        assertNull(pair.string)
        assertNull(pair.stringSet)
        assertEquals("null", pair.toString())
        assertEquals(true, pair.put(true).boolean)
        assertEquals(1.5f, pair.put(1.5f).float)
        assertEquals(7, pair.put(7).int)
        assertEquals(7L, pair.long)
        assertEquals(9L, pair.put(9L).long)
        assertEquals("s", pair.put("s").string)
        assertEquals(setOf("a", "bb"), pair.put(setOf("a", "bb")).stringSet)
        assertTrue(pair.toString().contains("a"))
        assertEquals(0, pair.describeContents())
    }

    @Test
    fun exportConfigAppendsExternalPluginText() {
        val source = MemoryConfigSource()
        val hysteria = external(ProxyEntity.TYPE_HYSTERIA, HysteriaBean().apply {
            protocolVersion = 1
            protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = "auth"
        })
        val trojanGo = external(ProxyEntity.TYPE_TROJAN_GO, TrojanGoBean())
        val mieru = external(ProxyEntity.TYPE_MIERU, MieruBean())
        val naive = external(ProxyEntity.TYPE_NAIVE, NaiveBean())
        withConfig(source) {
            for (entity in listOf(hysteria, trojanGo, mieru, naive)) {
                val (text, name) = entity.exportConfig()
                assertEquals("profiles.txt", name)
                assertTrue(text.contains("127.0.0.1"))
            }
            val socks = entity(ProxyEntity.TYPE_SOCKS, SOCKSBean())
            val (json, file) = socks.exportConfig()
            assertTrue(file.endsWith(".json"))
            assertTrue(json.contains("socks"))
        }
    }

    @Test
    fun singBoxOptionConstructorsRoundTrip() {
        var constructed = 0
        for (cls in SingBoxOptions::class.java.declaredClasses) {
            if (java.lang.reflect.Modifier.isAbstract(cls.modifiers)) continue
            val ctor = cls.declaredConstructors.firstOrNull { it.parameterCount == 0 } ?: continue
            ctor.isAccessible = true
            val obj = try {
                ctor.newInstance()
            } catch (e: Throwable) {
                throw AssertionError(cls.simpleName, e)
            }
            if (obj is SingBoxOptions.SingBoxOption) {
                try {
                    assertNotNull(obj.asMap())
                } catch (e: Throwable) {
                    throw AssertionError(cls.simpleName, e)
                }
            }
            constructed++
        }
        assertTrue(constructed > 40)
        val custom = SingBoxOptions.CustomSingBoxOption("""{"type":"direct"}""")
        assertEquals("direct", custom.asMap()["type"])
    }

    @Test
    fun formatsUtilsAndJavaUtilEdges() {
        val array = JSONArray().put("a").put(1)
        assertEquals(listOf("a"), array.filterIsInstance<String>())
        val seen = mutableListOf<Any>()
        array.forEach { _, value -> seen += value }
        JSONObject().put("k", 2).forEach { key, value ->
            assertEquals("k", key)
            assertEquals(2, value)
        }
        assertFalse(isJsonObjectValid("nope"))
        val obj = JSONObject().put("s", " ").put("b", true).put("n", 3)
        assertNull(obj.getStr("s"))
        assertNull(obj.getStr("missing"))
        assertEquals(true, obj.getBool("b"))
        assertNull(obj.getBool("nope"))
        assertEquals(3, obj.getIntNya("n"))
        assertNull(obj.getIntNya("nope"))
        assertTrue(obj.toStringPretty().contains("true"))
        assertEquals("A", "QQ".decodeBase64UrlSafe())
        assertEquals("\b\u000c\r\\'", JavaUtil.unescapeString("\\b\\f\\r\\\\\\'"))

        runBlocking {
            val parsed = parseProxies(
                """
                sn://%%%
                socks5://%%%
                socks4://%%%
                vmess://%%%
                vless://%%%
                trojan://%%%
                trojan-go://%%%
                ss://%%%
                ssr://%%%
                naive+https://%%%
                hysteria://%%%
                hysteria2://%%%
                hy2://%%%
                tuic://%%%
                juicity://%%%
                snell://%%%
                anytls://%%%
                """.trimIndent()
            )
            assertTrue(parsed.isEmpty())
        }
        val subscription = assertThrows(SubscriptionFoundException::class.java) {
            runBlocking { parseProxies("https://user:pass@h.example:443/sub") }
        }
        assertTrue(subscription.link.startsWith("clash://"))

        assertNull("  ".blankAsNull())
        assertEquals("a", "a".blankAsNull())
        assertEquals("boom", IllegalStateException("boom").readableMessage)
        assertEquals("a%20b", "a b".urlSafe())
        val holder = Holder()
        holder.flag = true
        holder.n = 4
        holder.wide = 8
        holder.ref = "z"
        holder.slot = "v"
        assertTrue(holder.flag)
        assertEquals(4, holder.n)
        assertEquals(8L, holder.wide)
        assertEquals("z", holder.ref)
        assertEquals("v", holder.slot)
        holder.slot = null
        assertNull(holder.slot)

        if (Build.VERSION.SDK_INT < 28) {
            JavaUtil.handleWebviewDir(null)
        }
    }

    private fun entity(type: Int, bean: AbstractBean): ProxyEntity {
        bean.initializeDefaultValues()
        if (bean.name.isNullOrBlank()) bean.name = "e$type"
        if (bean.serverAddress.isNullOrBlank()) bean.serverAddress = "example$type.com"
        return ProxyEntity(id = type.toLong(), groupId = 1, type = type).apply { putBean(bean) }
    }

    private fun external(type: Int, bean: AbstractBean): ProxyEntity {
        bean.initializeDefaultValues()
        bean.name = "ext$type"
        bean.serverAddress = "ext$type.example"
        bean.serverPort = 443
        return entity(type, bean)
    }

    private class Holder {
        var flag: Boolean by AtomicBoolean()
        var n: Int by AtomicInteger()
        var wide: Long by AtomicLong()
        var ref: String by AtomicReference("x")
        var slot: String? by mutableMapOf<Holder, String>()
    }
}
