package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.juicity.buildSingBoxOutboundJuicityBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.buildTrojanGoConfig
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic.buildSingBoxOutboundTuicBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundTLS
import moe.matsuri.nb4a.SingBoxOptions.Outbound_Hysteria2Options
import moe.matsuri.nb4a.SingBoxOptions.Outbound_HysteriaOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsOutboundBuildTest {
    @Test
    fun v2rayTlsUsesInjectedAllowInsecure() {
        val source = MemoryConfigSource().apply { globalAllowInsecure = true }
        val bean = VMessBean().apply {
            initializeDefaultValues()
            security = "tls"
            sni = "sni.example"
            alpn = "h2\nhttp/1.1"
            certificates = "CERT"
            utlsFingerprint = "firefox"
            allowInsecure = false
            enableECH = true
            echConfig = "abcdef"
            type = "tcp"
        }
        val ws = VMessBean().apply {
            initializeDefaultValues()
            security = "tls"
            type = "ws"
            alpn = "h2\nhttp/1.1\nh3"
            realityPubKey = "pub"
            realityShortId = "ab"
        }
        withConfig(source) {
            val tls = buildSingBoxOutboundTLS(bean)!!
            assertEquals(true, tls.enabled)
            assertEquals(true, tls.insecure)
            assertEquals("sni.example", tls.server_name)
            assertEquals(listOf("h2", "http/1.1"), tls.alpn)
            assertEquals("CERT", tls.certificate)
            assertEquals("firefox", tls.utls.fingerprint)
            assertEquals(listOf("-----BEGIN ECH CONFIGS-----", "abcdef", "-----END ECH CONFIGS-----"), tls.ech.config)

            val wsTls = buildSingBoxOutboundTLS(ws)!!
            assertEquals(listOf("http/1.1"), wsTls.alpn)
            assertEquals("chrome", wsTls.utls.fingerprint)
            assertEquals("pub", wsTls.reality.public_key)
            assertEquals("ab", wsTls.reality.short_id)
        }
        assertNull(buildSingBoxOutboundTLS(VMessBean().apply {
            initializeDefaultValues()
            security = "none"
        }))
    }

    @Test
    fun hysteriaTuicAndJuicityReadInjectedInsecureFlag() {
        val source = MemoryConfigSource().apply { globalAllowInsecure = true }
        val hy1 = HysteriaBean().apply {
            initializeDefaultValues()
            protocolVersion = 1
            protocol = HysteriaBean.PROTOCOL_UDP
            serverAddress = "hy.example"
            serverPorts = "10000-10010,20000"
            authPayloadType = HysteriaBean.TYPE_BASE64
            authPayload = "YQ"
            sni = "sni.example"
            alpn = "h3"
            caText = "CA"
            allowInsecure = false
            uploadMbps = 10
            downloadMbps = 20
            obfuscation = "obfs"
            hopInterval = 15
            streamReceiveWindow = 1
            connectionReceiveWindow = 2
            disableMtuDiscovery = true
        }
        val hy2 = HysteriaBean().apply {
            initializeDefaultValues()
            protocolVersion = 2
            protocol = HysteriaBean.PROTOCOL_UDP
            serverAddress = "hy2.example"
            serverPorts = "443"
            authPayload = "token"
            obfuscation = "sal"
            allowInsecure = false
        }
        val tuic = TuicBean().apply {
            initializeDefaultValues()
            protocolVersion = 5
            serverAddress = "tuic.example"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
            token = "pw"
            sni = "tuic.example"
            alpn = "h3"
            caText = "CA"
            disableSNI = true
            congestionController = "bbr"
            udpRelayMode = "quic"
            reduceRTT = true
            allowInsecure = false
        }
        val juicity = JuicityBean().apply {
            initializeDefaultValues()
            serverAddress = "j.example"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
            password = "pw"
            sni = "j.example"
            pinnedCertchainSha256 = "ab"
            allowInsecure = false
        }
        withConfig(source) {
            val out1 = buildSingBoxOutboundHysteriaBean(hy1) as Outbound_HysteriaOptions
            assertEquals("hysteria", out1.type)
            assertEquals(listOf("10000:10010"), out1.server_ports)
            assertEquals("YQ", out1.auth)
            assertEquals(true, out1.tls.insecure)
            assertEquals("CA", out1.tls.certificate)
            assertEquals(true, out1.disable_mtu_discovery)

            val out2 = buildSingBoxOutboundHysteriaBean(hy2) as Outbound_Hysteria2Options
            assertEquals("hysteria2", out2.type)
            assertEquals(443, out2.server_port)
            assertEquals("token", out2.password)
            assertEquals("salamander", out2.obfs.type)
            assertEquals(listOf("h3"), out2.tls.alpn)
            assertEquals(true, out2.tls.insecure)

            val tuicOut = buildSingBoxOutboundTuicBean(tuic)
            assertEquals("tuic", tuicOut.type)
            assertEquals(true, tuicOut.tls.insecure)
            assertEquals(true, tuicOut.tls.disable_sni)
            assertEquals("quic", tuicOut.udp_relay_mode)
            assertEquals(true, tuicOut.zero_rtt_handshake)

            val juicityOut = buildSingBoxOutboundJuicityBean(juicity)
            assertEquals(true, juicityOut.tls.insecure)
            assertEquals("ab", juicityOut.pin_cert_sha256)

            assertThrows(Exception::class.java) {
                buildSingBoxOutboundTuicBean(TuicBean().apply {
                    initializeDefaultValues()
                    protocolVersion = 4
                })
            }
            assertThrows(IllegalStateException::class.java) {
                buildSingBoxOutboundHysteriaBean(HysteriaBean().apply {
                    initializeDefaultValues()
                    protocolVersion = 9
                })
            }
        }
    }

    @Test
    fun naiveAndTrojanGoConfigsUseInjectedLogAndIpv6() {
        val source = MemoryConfigSource().apply {
            logLevel = 1
            ipv6Mode = IPv6Mode.DISABLE
        }
        val naive = NaiveBean().apply {
            initializeDefaultValues()
            proto = "https"
            serverAddress = "naive.example"
            serverPort = 443
            username = "u"
            password = "p"
            sni = "sni.example"
            extraHeaders = "A: b\nC: d"
            insecureConcurrency = 3
            name = "naive"
            finalAddress = serverAddress
            finalPort = serverPort
        }
        val ipNaive = NaiveBean().apply {
            initializeDefaultValues()
            serverAddress = "1.2.3.4"
            serverPort = 443
        }
        val trojan = TrojanGoBean().apply {
            initializeDefaultValues()
            serverAddress = "tg.example"
            serverPort = 443
            password = "pw"
            type = "ws"
            host = "cdn.example"
            path = "/ws"
            sni = "sni.example"
            allowInsecure = true
            encryption = "ss;aes-128-gcm:secret"
        }
        val quiet = MemoryConfigSource().apply {
            logLevel = 0
            ipv6Mode = IPv6Mode.PREFER
        }
        withConfig(source) {
            val naiveJson = JSONObject(naive.buildNaiveConfig(1080))
            assertEquals("socks://127.0.0.1:1080", naiveJson.getString("listen"))
            assertTrue(naiveJson.getString("proxy").startsWith("https://"))
            assertEquals("MAP sni.example naive.example", naiveJson.getString("host-resolver-rules"))
            assertEquals("", naiveJson.getString("log"))
            assertEquals(3, naiveJson.getInt("insecure-concurrency"))
            assertTrue(naiveJson.getString("extra-headers").contains("\r\n"))
            assertFalse(JSONObject(ipNaive.buildNaiveConfig(1)).has("host-resolver-rules"))

            val tg = JSONObject(trojan.buildTrojanGoConfig(2080))
            assertEquals(0, tg.getInt("log_level"))
            assertEquals(true, tg.getJSONObject("tcp").getBoolean("prefer_ipv4"))
            assertEquals(true, tg.getJSONObject("websocket").getBoolean("enabled"))
            assertEquals("cdn.example", tg.getJSONObject("websocket").getString("host"))
            assertEquals(false, tg.getJSONObject("ssl").getBoolean("verify"))
            assertEquals("aes-128-gcm", tg.getJSONObject("shadowsocks").getString("method"))
        }
        withConfig(quiet) {
            val hostOnly = NaiveBean().apply {
                initializeDefaultValues()
                serverAddress = "naive.example"
                finalAddress = "1.2.3.4"
            }
            val mapped = JSONObject(hostOnly.buildNaiveConfig(1))
            assertEquals("MAP naive.example 1.2.3.4", mapped.getString("host-resolver-rules"))
            assertFalse(mapped.has("log"))

            val original = TrojanGoBean().apply {
                initializeDefaultValues()
                serverAddress = "tg.example"
                password = "pw"
                type = "original"
                encryption = "none"
            }
            val tg = JSONObject(original.buildTrojanGoConfig(1))
            assertEquals(2, tg.getInt("log_level"))
            assertEquals(false, tg.getJSONObject("tcp").getBoolean("prefer_ipv4"))
            assertFalse(tg.has("websocket"))
            assertFalse(tg.has("shadowsocks"))
        }
    }
}
