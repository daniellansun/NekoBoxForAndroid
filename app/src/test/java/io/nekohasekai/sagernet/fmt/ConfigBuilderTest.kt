package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_ANYTLS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_CHAIN
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_CONFIG
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_HTTP
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_HYSTERIA
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_JUICITY
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_MIERU
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_NAIVE
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_NEKO
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SHADOWTLS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SNELL
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SOCKS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SSR
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SSH
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_TROJAN
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_TROJAN_GO
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_TUIC
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_VMESS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_WG
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean
import moe.matsuri.nb4a.proxy.neko.NekoBean
import moe.matsuri.nb4a.proxy.config.ConfigBean
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSBean
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderTest {
    @Test
    fun rawConfigProfileReturnsItsText() {
        val bean = ConfigBean().apply {
            initializeDefaultValues()
            type = 0
            config = """{"log":{"level":"warn"}}"""
            name = "raw"
        }
        val proxy = ProxyEntity(id = 4, type = TYPE_CONFIG, configBean = bean)
        val result = buildConfig(proxy)
        assertEquals(bean.config, result.config)
        assertEquals(4L, result.mainEntId)
        assertEquals("raw", result.profileTagMap[4L])
        assertTrue(result.externalIndex.isEmpty())
        assertEquals(-1L, result.selectorGroupId)
    }

    @Test
    fun forTestBuildsOneOutboundAndDirectDns() {
        val source = MemoryConfigSource().apply {
            enableFakeDns = true
            enableDnsRouting = true
            remoteDns = ""
            directDns = "https://dns.example/dns-query"
        }
        val proxy = socks("1.2.3.4", 1)
        source.add(proxy)
        val result = withConfig(source) { buildConfig(proxy, forTest = true) }
        val json = JSONObject(result.config)
        assertEquals("dns-direct", json.getJSONObject("dns").getString("final"))
        assertEquals(0, json.getJSONArray("inbounds").length())
        assertEquals("info", json.getJSONObject("log").getString("level"))
        assertTrue(result.config.contains("1.2.3.4"))
        assertEquals(-1L, result.selectorGroupId)
    }

    @Test
    fun fullVpnConfigCoversOutboundsRulesAndDns() {
        val source = populatedVpnSource()
        val main = source.proxies.values.first { it.vmessBean?.name == "vmess-ws" }
        val result = withConfig(source) { buildConfig(main) }
        val json = JSONObject(result.config)
        assertEquals(source.groups.values.first().id, result.selectorGroupId)
        assertEquals("selector", json.getJSONArray("outbounds").getJSONObject(0).getString("type"))
        assertEquals("proxy", json.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
        assertEquals("trace", json.getJSONObject("log").getString("level"))
        assertTrue(json.has("experimental"))
        assertEquals("tun", json.getJSONArray("inbounds").getJSONObject(0).getString("type"))
        assertEquals("gvisor", json.getJSONArray("inbounds").getJSONObject(0).getString("stack"))
        assertTrue(result.config.contains("dns-fake"))
        assertTrue(result.config.contains("dns-hosts"))
        assertTrue(result.config.contains("reject"))
        assertTrue(result.config.contains("ruleset-"))
        assertTrue(result.config.contains("fragment"))
        assertTrue(result.config.contains("hysteria"))
        assertTrue(result.config.contains("tuic"))
        assertTrue(result.config.contains("juicity"))
        assertTrue(result.config.contains("wireguard"))
        assertTrue(result.config.contains("anytls"))
        assertTrue(result.config.contains("shadowtls"))
        assertTrue(result.config.contains("snell"))
        assertTrue(result.config.contains("dns-sub-"))
        assertEquals("from-custom", json.getJSONObject("log").optString("output"))
    }

    @Test
    fun globalModeSkipsUserRules() {
        val source = MemoryConfigSource().apply {
            serviceMode = vpnMode()
            globalMode = true
            bypassLan = true
            enableHevTun = true
            logLevel = 0
        }
        val proxy = socks("global.example", 3)
        source.add(proxy)
        source.groups[3L] = ProxyGroup(id = 3L)
        proxy.groupId = 3L
        val json = JSONObject(withConfig(source) { buildConfig(proxy).config })
        assertEquals("panic", json.getJSONObject("log").getString("level"))
        assertTrue(resultContainsInbound(json, "mixed-in"))
        assertTrue(json.getJSONObject("route").getJSONArray("rules").toString().contains("10.0.0.0/8"))
        assertFalse(json.getJSONArray("inbounds").toString().contains("\"tun\""))
    }

    @Test
    fun exportSkipsSelector() {
        val source = MemoryConfigSource()
        val proxy = socks("export.example", 5)
        source.add(proxy)
        source.groups[5L] = ProxyGroup(id = 5L, isSelector = true)
        proxy.groupId = 5L
        val result = withConfig(source) { buildConfig(proxy, forExport = true) }
        assertEquals(-1L, result.selectorGroupId)
        assertFalse(result.config.contains("\"selector\""))
    }

    @Test
    fun missingDnsThrows() {
        val source = MemoryConfigSource().apply { directDns = "# none\n" }
        val proxy = socks("dns.example", 1)
        source.add(proxy)
        assertThrows(Exception::class.java) {
            withConfig(source) { buildConfig(proxy, forTest = true) }
        }
        source.directDns = "https://223.5.5.5/dns-query"
        source.remoteDns = "\n# c\n"
        val thrown = assertThrows(Exception::class.java) {
            withConfig(source) { buildConfig(proxy) }
        }
        assertTrue(thrown.message!!.contains("No remote DNS"))
    }

    @Test
    fun tunStackIpv6AndLogBranches() {
        val source = MemoryConfigSource().apply {
            serviceMode = vpnMode()
            resolveDestination = true
            strictRoute = false
            allowAccess = true
            mixedInboundHasAuth = true
            mixedUsername = "u"
            mixedSecret = "p"
            concurrentDial = true
            trafficSniffing = 2
        }
        val proxy = socks("branch.example", 8)
        source.add(proxy)
        val stacks = listOf(TunImplementation.GVISOR, TunImplementation.SYSTEM, TunImplementation.MIXED, 99)
        val modes = listOf(IPv6Mode.DISABLE, IPv6Mode.ONLY, IPv6Mode.ENABLE, IPv6Mode.PREFER, 9)
        withConfig(source) {
            for (level in 0..5) {
                source.logLevel = level
                source.tunImplementation = stacks[level % stacks.size]
                source.ipv6Mode = modes[level % modes.size]
                val json = JSONObject(buildConfig(proxy).config)
                assertTrue(json.getJSONArray("inbounds").length() >= 1)
            }
        }
    }

    @Test
    fun externalMappingPluginsAndPackageRules() {
        val source = MemoryConfigSource().apply {
            serviceMode = "proxy"
            enableTLSFragment = true
            enableFakeDns = false
            enableDnsRouting = true
            packageUids["com.example.ok"] = 10123
            packageUids["com.example.system"] = 50
        }
        val hy = hysteriaExternal(40)
        source.add(hy)
        source.groups[1L] = ProxyGroup(id = 1L)
        hy.groupId = 1L
        val mapped = JSONObject(withConfig(source) { buildConfig(hy).config })
        val inboundTags = mapped.getJSONArray("inbounds").toString()
        assertTrue(inboundTags.contains("mapping-40"))
        assertTrue(mapped.toString().contains("\"type\":\"socks\""))

        source.matsuriPlugins = setOf("hysteria-plugin")
        val exe = JSONObject(withConfig(source) { buildConfig(hy).config })
        assertFalse(exe.getJSONArray("inbounds").toString().contains("mapping-40"))

        source.matsuriPlugins = emptySet()
        source.externalPlugins = setOf("hysteria-plugin")
        val thrown = assertThrows(Exception::class.java) {
            withConfig(source) { buildConfig(hy) }
        }
        assertTrue(thrown.message!!.contains("hysteria-plugin"))
        source.externalPlugins = emptySet()

        val socksHop = socks("after.example", 41, groupId = 1, name = "after")
        source.add(socksHop)
        val chain = profile(42, 1, TYPE_CHAIN, ChainBean().apply {
            initializeDefaultValues()
            name = "ext-chain"
            proxies = listOf(socksHop.id, hy.id)
        })
        source.add(chain)
        val chained = withConfig(source) { buildConfig(chain).config }
        assertTrue(chained.contains("mapping-40"))
        assertTrue(chained.contains("after"))

        source.add(profile(43, 1, TYPE_NEKO, NekoBean().apply {
            initializeDefaultValues()
            name = "neko"
            serverAddress = "neko.example"
        }))
        val neko = source.proxies[43L]!!
        assertTrue(withConfig(source) { buildConfig(neko).config }.contains("\"socks\""))

        source.rules += RuleEntity(
            id = 9,
            name = "apps",
            packages = setOf("com.example.ok", "com.example.system", "com.example.missing"),
            outbound = -1,
        )
        source.rules += RuleEntity(
            id = 10,
            name = "gone",
            domains = "missing.example",
            outbound = 999,
        )
        source.rules += RuleEntity(
            id = 11,
            domains = "direct.example",
            ruleset = "rssite:https://example.com/site.json",
            outbound = -1,
        )
        source.rules += RuleEntity(
            id = 12,
            domains = "remote.example",
            ruleset = "rssite:https://example.com/remote.json",
            outbound = 0,
        )
        source.rules += RuleEntity(
            id = 13,
            domains = "block.example",
            ruleset = "rssite:https://example.com/block.json",
            outbound = -2,
        )
        val ruled = withConfig(source) { buildConfig(hy).config }
        assertTrue(source.warnings.any { it.startsWith("vpn:") })
        assertTrue(source.warnings.any { it.startsWith("missing:") })
        assertTrue(ruled.contains("10123"))
        assertTrue(ruled.contains("dns-direct"))
        assertTrue(ruled.contains("dns-remote"))
        assertTrue(ruled.contains("dns-block"))
    }

    @Test
    fun configBeanHostsAndInvalidJson() {
        val source = MemoryConfigSource().apply {
            enableDnsRouting = true
        }
        val cfg = profile(50, 2, TYPE_CONFIG, ConfigBean().apply {
            initializeDefaultValues()
            name = "cfg"
            type = 1
            config = """{"type":"direct","server":"cfg.example"}"""
            serverAddress = "fallback.example"
        })
        source.add(cfg)
        source.groups[2L] = ProxyGroup(id = 2L)
        val json = withConfig(source) { buildConfig(cfg).config }
        assertTrue(json.contains("cfg.example"))

        val bad = profile(51, 2, TYPE_CONFIG, ConfigBean().apply {
            initializeDefaultValues()
            name = "bad"
            type = 1
            config = "not-json"
            serverAddress = "bad-json.example"
        })
        val tail = socks("tail.example", 52, groupId = 2, name = "tail")
        source.add(bad)
        source.add(tail)
        val chain = profile(53, 2, TYPE_CHAIN, ChainBean().apply {
            initializeDefaultValues()
            name = "bad-chain"
            proxies = listOf(tail.id, bad.id)
        })
        source.add(chain)
        assertThrows(Exception::class.java) {
            withConfig(source) { buildConfig(chain) }
        }
    }

    @Test
    fun fragmentDetourAndExtraProxy() {
        val source = MemoryConfigSource().apply {
            enableTLSFragment = true
            serviceMode = "proxy"
        }
        val trojan = profile(60, 1, TYPE_TROJAN, TrojanBean().apply {
            initializeDefaultValues()
            name = "tj"
            serverAddress = "tj.example"
            serverPort = 443
            password = "pw"
            security = "tls"
            sni = "tj.example"
        })
        val extra = socks("extra.example", 61, groupId = 1, name = "extra")
        source.add(trojan)
        source.add(extra)
        source.groups[1L] = ProxyGroup(id = 1L)
        source.rules += RuleEntity(id = 20, domains = "via-extra.example", outbound = extra.id)
        val json = withConfig(source) { buildConfig(trojan).config }
        assertTrue(json.contains("\"detour\": \"fragment\"") || json.contains("\"detour\":\"fragment\""))
        assertTrue(json.contains("extra"))
    }

    private fun hysteriaExternal(id: Int): ProxyEntity {
        return profile(id, 1, TYPE_HYSTERIA, HysteriaBean().apply {
            initializeDefaultValues()
            name = "hy-ext"
            protocolVersion = 1
            protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
            serverAddress = "hy-ext.example"
            serverPorts = "443"
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = "auth"
        })
    }

    private fun populatedVpnSource(): MemoryConfigSource {
        val source = MemoryConfigSource().apply {
            serviceMode = vpnMode()
            enableClashAPI = true
            logLevel = 4
            enableFakeDns = true
            enableDnsRouting = true
            enableTLSFragment = true
            bypassLanInCore = true
            trafficSniffing = 2
            allowAccess = true
            mixedInboundHasAuth = true
            mixedUsername = "mixed-user"
            mixedSecret = "mixed-pass"
            concurrentDial = true
            resolveDestination = true
            globalAllowInsecure = true
            rulesUpdateInterval = "24h"
            fragmentLength = "50-100"
            fragmentInterval = "10-20"
            dnsHosts = """
                # comment

                cdn.example 1.1.1.1 8.8.8.8
                skipped
                bad.example not-an-ip
            """.trimIndent()
            remoteDns = "https://dns.google/dns-query\n1.1.1.1\n# skip"
            directDns = "\n# c\nhttps://223.5.5.5/dns-query"
            globalCustomConfig = """{"log":{"output":"from-custom"}}"""
            domainStrategyResult = "prefer_ipv4"
        }
        val group = ProxyGroup(
            id = 7L,
            type = GroupType.SUBSCRIPTION,
            isSelector = true,
            subscription = SubscriptionBean().apply {
                serverDnsResolver = "\u0001https://dns.example/dns-query "
            },
            frontProxy = 100L,
            landingProxy = 101L,
        )
        source.groups[7L] = group
        source.add(socks("1.2.3.4", 100, groupId = 2, name = "front"))
        source.add(socks("landing.example", 101, groupId = 2, name = "landing"))

        source.add(vmess("vmess-ws", 1, groupId = 7).apply {
            vmessBean!!.apply {
                security = "tls"
                type = "ws"
                host = "cdn.example"
                path = "/ray?ed=2048"
                sni = "sni.example"
                alpn = "h2\nhttp/1.1"
                certificates = "CERT"
                utlsFingerprint = "firefox"
                allowInsecure = false
                enableECH = true
                echConfig = "-----BEGIN ECH CONFIGS-----\nraw"
                realityPubKey = ""
                packetEncoding = 1
                enableMux = true
                muxPadding = true
                muxType = 1
                muxMode = 1
                muxBrutal = true
                wsMaxEarlyData = 256
                earlyDataHeaderName = "Sec-WebSocket-Protocol"
                serverAddress = "vmess.example"
            }
        })
        source.add(vmess("vmess-tcp", 2, groupId = 7).apply {
            vmessBean!!.apply {
                packetEncoding = 0
                serverAddress = "vmess-tcp.example"
            }
        })
        source.add(vless(3, groupId = 7))
        source.add(profile(4, 7, TYPE_HTTP, HttpBean().apply {
            initializeDefaultValues()
            name = "http"
            serverAddress = "http.example"
            serverPort = 8080
            username = "u"
            password = "p"
            security = "tls"
            sni = "http.example"
        }))
        source.add(profile(5, 7, TYPE_TROJAN, TrojanBean().apply {
            initializeDefaultValues()
            name = "trojan"
            serverAddress = "trojan.example"
            password = "pw"
            security = "tls"
            sni = "trojan.example"
            type = "grpc"
            path = "svc"
            enableMux = true
            muxType = 2
            muxMode = 0
        }))
        source.add(profile(6, 7, TYPE_HYSTERIA, HysteriaBean().apply {
            initializeDefaultValues()
            name = "hy1"
            protocolVersion = 1
            protocol = HysteriaBean.PROTOCOL_UDP
            serverAddress = "hy.example"
            serverPorts = "10000-10010"
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = "auth"
            sni = "hy.example"
        }))
        source.add(profile(16, 7, TYPE_HYSTERIA, HysteriaBean().apply {
            initializeDefaultValues()
            name = "hy2"
            protocolVersion = 2
            protocol = HysteriaBean.PROTOCOL_UDP
            serverAddress = "hy2.example"
            serverPorts = "443"
            authPayload = "tok"
            obfuscation = "sal"
        }))
        source.add(profile(17, 7, TYPE_TUIC, TuicBean().apply {
            initializeDefaultValues()
            name = "tuic"
            serverAddress = "tuic.example"
            uuid = "11111111-1111-1111-1111-111111111111"
            token = "pw"
            sni = "tuic.example"
        }))
        source.add(profile(18, 7, TYPE_JUICITY, JuicityBean().apply {
            initializeDefaultValues()
            name = "juicity"
            serverAddress = "j.example"
            uuid = "11111111-1111-1111-1111-111111111111"
            password = "pw"
            pinnedCertchainSha256 = "ab"
        }))
        source.add(profile(19, 7, TYPE_SS, ShadowsocksBean().apply {
            initializeDefaultValues()
            name = "ss"
            serverAddress = "ss.example"
            password = "pw"
            method = "aes-256-gcm"
            plugin = "obfs-local;obfs=http"
            sUoT = true
        }))
        source.add(profile(20, 7, TYPE_SSR, ShadowsocksRBean().apply {
            initializeDefaultValues()
            name = "ssr"
            serverAddress = "ssr.example"
            password = "pw"
            method = "aes-256-cfb"
            protocol = "origin"
            obfs = "plain"
        }))
        source.add(profile(21, 7, TYPE_WG, WireGuardBean().apply {
            initializeDefaultValues()
            name = "wg"
            serverAddress = "wg.example"
            localAddress = "10.0.0.2/32"
            privateKey = "priv"
            peerPublicKey = "pub"
            reserved = "1,2,3"
        }))
        source.add(profile(22, 7, TYPE_SSH, SSHBean().apply {
            initializeDefaultValues()
            name = "ssh"
            serverAddress = "ssh.example"
            authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
            privateKey = "key"
            privateKeyPassphrase = "pass"
            publicKey = "hostkey"
        }))
        source.add(profile(23, 7, TYPE_ANYTLS, AnyTLSBean().apply {
            initializeDefaultValues()
            name = "any"
            serverAddress = "any.example"
            password = "pw"
            sni = "any.example"
        }))
        source.add(profile(24, 7, TYPE_SNELL, SnellBean().apply {
            initializeDefaultValues()
            name = "snell"
            serverAddress = "snell.example"
            psk = "psk"
        }))
        source.add(profile(25, 7, TYPE_SHADOWTLS, ShadowTLSBean().apply {
            initializeDefaultValues()
            name = "stls"
            serverAddress = "stls.example"
            password = "pw"
        }))
        source.add(profile(26, 7, TYPE_CONFIG, ConfigBean().apply {
            initializeDefaultValues()
            name = "cfg"
            type = 1
            config = """{"type":"direct","server":"cfg.example"}"""
            serverAddress = "fallback.example"
        }))
        source.add(profile(27, 7, TYPE_CONFIG, ConfigBean().apply {
            initializeDefaultValues()
            name = "cfg-empty"
            type = 1
            config = """{"server":""}"""
            serverAddress = "empty-fallback.example"
        }))
        val hop = socks("other.example", 30, groupId = 2, name = "hop")
        source.add(hop)
        val again = socks("dup.example", 32, groupId = 2, name = "dup")
        source.add(again)
        source.add(profile(31, 7, TYPE_CHAIN, ChainBean().apply {
            initializeDefaultValues()
            name = "chain"
            proxies = listOf(again.id, hop.id, again.id)
        }))

        source.rules += RuleEntity(
            id = 1,
            domains = "full:a.com\ndomain:b.com\nregexp:^c\nkeyword:d\ngeosite:google\nplain.com",
            ip = "geoip:private\ngeoip:cn\n1.1.1.1",
            port = "80\n1000:2000",
            sourcePort = "53\n1:10",
            network = "tcp",
            source = "10.0.0.0/8",
            protocol = "tls",
            ruleset = "rssite:https://example.com/rules.json\nrsip:https://example.com/ip.srs",
            outbound = 0,
            config = """{"extra":true}""",
        )
        source.rules += RuleEntity(id = 2, domains = "bypass.example", outbound = -1)
        source.rules += RuleEntity(id = 3, domains = "block.example", outbound = -2)
        source.rules += RuleEntity(id = 4, outbound = 0)
        source.rules += RuleEntity(id = 5, domains = "self.example", outbound = 1)
        return source
    }

    private fun resultContainsInbound(json: JSONObject, tag: String): Boolean {
        val rules = json.getJSONObject("route").getJSONArray("rules")
        for (i in 0 until rules.length()) {
            val inbound = rules.getJSONObject(i).optJSONArray("inbound") ?: continue
            for (j in 0 until inbound.length()) {
                if (inbound.getString(j) == tag) return true
            }
        }
        return false
    }

    private fun socks(host: String, id: Int, groupId: Int = 0, name: String = host): ProxyEntity {
        return profile(id, groupId, TYPE_SOCKS, SOCKSBean().apply {
            initializeDefaultValues()
            this.name = name
            serverAddress = host
            serverPort = 1080
            username = "u"
            password = "p"
        })
    }

    private fun vmess(name: String, id: Int, groupId: Int): ProxyEntity {
        return profile(id, groupId, TYPE_VMESS, VMessBean().apply {
            initializeDefaultValues()
            this.name = name
            serverAddress = "$name.example"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
        })
    }

    private fun vless(id: Int, groupId: Int): ProxyEntity {
        return profile(id, groupId, TYPE_VMESS, VMessBean().apply {
            initializeDefaultValues()
            name = "vless"
            alterId = -1
            serverAddress = "vless.example"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
            security = "tls"
            sni = "vless.example"
            encryption = "xtls-rprx-vision"
            vlessEncryption = "mlkem768x25519plus"
            packetEncoding = 2
            type = "http"
            host = "h1,h2"
            path = "/h"
        })
    }

    private fun profile(id: Int, groupId: Int, type: Int, bean: io.nekohasekai.sagernet.fmt.AbstractBean): ProxyEntity {
        bean.initializeDefaultValues()
        return ProxyEntity(id = id.toLong(), groupId = groupId.toLong(), type = type).apply {
            when (type) {
                TYPE_SOCKS -> socksBean = bean as SOCKSBean
                TYPE_HTTP -> httpBean = bean as HttpBean
                TYPE_SS -> ssBean = bean as ShadowsocksBean
                TYPE_SSR -> ssrBean = bean as ShadowsocksRBean
                TYPE_VMESS -> vmessBean = bean as VMessBean
                TYPE_TROJAN -> trojanBean = bean as TrojanBean
                TYPE_HYSTERIA -> hysteriaBean = bean as HysteriaBean
                TYPE_TUIC -> tuicBean = bean as TuicBean
                TYPE_JUICITY -> juicityBean = bean as JuicityBean
                TYPE_SSH -> sshBean = bean as SSHBean
                TYPE_WG -> wgBean = bean as WireGuardBean
                TYPE_SHADOWTLS -> shadowTLSBean = bean as ShadowTLSBean
                TYPE_ANYTLS -> anyTLSBean = bean as AnyTLSBean
                TYPE_SNELL -> snellBean = bean as SnellBean
                TYPE_CONFIG -> configBean = bean as ConfigBean
                TYPE_CHAIN -> chainBean = bean as ChainBean
                TYPE_TROJAN_GO -> trojanGoBean = bean as TrojanGoBean
                TYPE_MIERU -> mieruBean = bean as MieruBean
                TYPE_NAIVE -> naiveBean = bean as NaiveBean
                TYPE_NEKO -> nekoBean = bean as NekoBean
                else -> error("type $type")
            }
        }
    }
}
