package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.fmt.http.parseHttp
import io.nekohasekai.sagernet.fmt.http.toUri as httpToUri
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.juicity.parseJuicity
import io.nekohasekai.sagernet.fmt.juicity.toUri as juicityToUri
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.mieru.buildMieruConfig
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.parseNaive
import io.nekohasekai.sagernet.fmt.naive.toUri as naiveToUri
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.fixPluginName
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocks.toUri as ssToUri
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.buildSingBoxOutboundShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.parseShadowsocksR
import io.nekohasekai.sagernet.fmt.shadowsocksr.toUri as ssrToUri
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.snell.buildSingBoxOutboundSnellBean
import io.nekohasekai.sagernet.fmt.snell.parseClashSnell
import io.nekohasekai.sagernet.fmt.snell.parseSnell
import io.nekohasekai.sagernet.fmt.snell.toUri as snellToUri
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.buildSingBoxOutboundSocksBean
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.socks.toUri as socksToUri
import io.nekohasekai.sagernet.fmt.socks.toV2rayN
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.ssh.buildSingBoxOutboundSSHBean
import io.nekohasekai.sagernet.fmt.trojan.parseTrojan
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.parseTrojanGo
import io.nekohasekai.sagernet.fmt.trojan_go.toUri as trojanGoToUri
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic.buildSingBoxOutboundTuicBean
import io.nekohasekai.sagernet.fmt.tuic.parseTuic
import io.nekohasekai.sagernet.fmt.tuic.toUri as tuicToUri
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxOutboundWireguardBean
import io.nekohasekai.sagernet.fmt.wireguard.genReserved
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean
import moe.matsuri.nb4a.proxy.anytls.buildSingBoxOutboundAnyTLSBean
import moe.matsuri.nb4a.proxy.anytls.parseAnytls
import moe.matsuri.nb4a.proxy.anytls.toUri as anytlsToUri
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSBean
import moe.matsuri.nb4a.proxy.shadowtls.buildSingBoxOutboundShadowTLSBean
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ShareLinkFmtTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    @Test
    fun shadowsocksSip002Base64AndJson() {
        val sip = parseShadowsocks(
            "ss://aes-256-gcm:secret@1.2.3.4:8388?plugin=simple-obfs%3Bobfs%3Dhttp#ss"
        )
        assertEquals("aes-256-gcm", sip.method)
        assertEquals("secret", sip.password)
        assertEquals("1.2.3.4", sip.serverAddress)
        assertEquals(8388, sip.serverPort)
        assertEquals("obfs-local;obfs=http", sip.plugin)
        assertEquals("ss", sip.name)
        assertEquals(sip.withDefaults(), sip.withDefaults().clone())

        val user = b64Url("aes-256-gcm:secret")
        val decodedUser = parseShadowsocks("ss://$user@10.0.0.8:8388#b64")
        assertEquals("aes-256-gcm", decodedUser.method)
        assertEquals("secret", decodedUser.password)
        assertEquals("b64", decodedUser.name)

        val whole = b64Std("aes-256-gcm:secret@10.1.1.1:9000")
        val v2rayN = parseShadowsocks("ss://$whole#my%20node")
        assertEquals("10.1.1.1", v2rayN.serverAddress)
        assertEquals(9000, v2rayN.serverPort)
        assertEquals("my node", v2rayN.name)

        assertThrows(IllegalStateException::class.java) {
            parseShadowsocks("ss://@1.2.3.4:8388#bad")
        }

        val fromJson = JSONObject(
            """{"server":"ss.example","server_port":1,"password":"p","method":"chacha20-ietf-poly1305","remarks":"json","plugin":"obfs-local","plugin_opts":"obfs=http"}"""
        ).parseShadowsocks()
        assertEquals("json", fromJson.name)
        assertEquals("obfs-local;obfs=http", fromJson.plugin)

        val again = parseShadowsocks(sip.withDefaults().ssToUri())
        assertEquals("secret", again.password)
        assertEquals("obfs-local;obfs=http", again.plugin)
        assertEquals("ss", again.name)

        val renamed = ShadowsocksBean().apply { plugin = "simple-obfs;obfs=tls" }
        renamed.fixPluginName()
        assertEquals("obfs-local;obfs=tls", renamed.plugin)

        val outbound = buildSingBoxOutboundShadowsocksBean(
            ShadowsocksBean().apply {
                serverAddress = "1.1.1.1"
                serverPort = 8388
                method = "aes-256-gcm"
                password = "p"
                plugin = "obfs-local;obfs=http"
            }.withDefaults()
        )
        assertEquals("shadowsocks", outbound.type)
        assertEquals("obfs-local", outbound.plugin)
        assertEquals("obfs=http", outbound.plugin_opts)

        val none = buildSingBoxOutboundShadowsocksBean(
            ShadowsocksBean().apply {
                serverAddress = "1.1.1.1"
                serverPort = 1
                plugin = "none;ignored"
            }.withDefaults()
        )
        assertNull(none.plugin)
        assertNull(none.plugin_opts)
    }

    @Test
    fun shadowsocksRLinkJsonAndOutbound() {
        val body = "1.2.3.4:8388:auth_aes128_md5:aes-256-gcm:tls1.2_ticket_auth:${b64Url("secret")}/" +
            "?obfsparam=${b64Url("obfs")}&protoparam=${b64Url("proto")}&remarks=${b64Url("ssr node")}"
        val bean = parseShadowsocksR("ssr://${b64Url(body)}")
        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(8388, bean.serverPort)
        assertEquals("auth_aes128_md5", bean.protocol)
        assertEquals("aes-256-gcm", bean.method)
        assertEquals("tls1.2_ticket_auth", bean.obfs)
        assertEquals("secret", bean.password)
        assertEquals("obfs", bean.obfsParam)
        assertEquals("proto", bean.protocolParam)
        assertEquals("ssr node", bean.name)
        assertEquals(bean.withDefaults(), bean.withDefaults().clone())

        val again = parseShadowsocksR(bean.withDefaults().ssrToUri())
        assertEquals("secret", again.password)
        assertEquals("ssr node", again.name)
        assertEquals("obfs", again.obfsParam)

        val json = JSONObject(
            """{"server":"ssr.example","server_port":1,"method":"none","password":"p","protocol":"origin","protocol_param":"pp","obfs":"plain","obfs_param":"op","remarks":"j"}"""
        ).parseShadowsocksR()
        assertEquals("ssr.example", json.serverAddress)
        assertEquals("pp", json.protocolParam)

        val outbound = buildSingBoxOutboundShadowsocksRBean(bean.withDefaults())
        assertEquals("shadowsocksr", outbound.type)
        assertEquals("secret", outbound.password)
        assertEquals("obfs", outbound.obfs_param)
    }

    @Test
    fun socksHttpAndTrojan() {
        val socks = parseSOCKS("socks5://user:pass@1.2.3.4:1080#socks")
        assertEquals(SOCKSBean.PROTOCOL_SOCKS5, socks.protocol)
        assertEquals("user", socks.username)
        assertEquals("pass", socks.password)
        assertEquals("socks", socks.name)
        val socksAgain = parseSOCKS(socks.withDefaults().socksToUri())
        assertEquals("user", socksAgain.username)
        assertEquals(1080, socksAgain.serverPort)

        val packed = b64Url("user:secret")
        val fromPacked = parseSOCKS("socks5://$packed@10.0.0.1:1080")
        assertEquals("user", fromPacked.username)
        assertEquals("secret", fromPacked.password)

        val v4 = parseSOCKS("socks4://10.0.0.2:1080#v4").withDefaults()
        assertEquals(SOCKSBean.PROTOCOL_SOCKS4, v4.protocol)
        assertTrue(v4.socksToUri().startsWith("socks4://"))
        val v4a = parseSOCKS("socks4a://u:p@10.0.0.3:1080").withDefaults()
        assertEquals(SOCKSBean.PROTOCOL_SOCKS4A, v4a.protocol)
        assertTrue(v4a.socksToUri().startsWith("socks4://"))
        val socksOut = buildSingBoxOutboundSocksBean(v4a)
        assertEquals("4a", socksOut.version)
        assertEquals("u", socksOut.username)

        val encoded = socks.withDefaults().toV2rayN()
        assertTrue(encoded.startsWith("socks://"))
        val payload = String(Base64.getDecoder().decode(encoded.substringAfter("socks://").substringBefore("#")))
        assertTrue(payload.contains("user:pass@1.2.3.4:1080"))
        assertEquals(socks.withDefaults(), socks.withDefaults().clone())

        val https = parseHttp("https://user:pass@proxy.example:8443/?sni=s.example#proxy")
        assertEquals("proxy.example", https.serverAddress)
        assertEquals(8443, https.serverPort)
        assertEquals("user", https.username)
        assertEquals("s.example", https.sni)
        assertTrue(https.isTLS())
        val httpsAgain = parseHttp(https.withDefaults().httpToUri())
        assertEquals("s.example", httpsAgain.sni)
        assertTrue(httpsAgain.isTLS())

        val http = parseHttp("http://user:pass@proxy.example:8080/#plain").withDefaults()
        assertFalse(http.isTLS())
        assertTrue(http.httpToUri().startsWith("http://"))
        assertThrows(IllegalStateException::class.java) {
            parseHttp("https://proxy.example/not-a-proxy")
        }
        assertThrows(IllegalStateException::class.java) {
            parseHttp("not a url")
        }

        val trojan = parseTrojan(
            "trojan://secret@tr.example:443?type=ws&host=cdn&path=%2Ft&security=tls&sni=a.example&peer=peer.example&allowInsecure=1#tr"
        )
        assertEquals("secret", trojan.password)
        assertEquals("ws", trojan.type)
        assertEquals("cdn", trojan.host)
        assertEquals("/t", trojan.path)
        assertEquals("peer.example", trojan.sni)
        assertTrue(trojan.allowInsecure)
        assertEquals("tr", trojan.name)
        assertEquals(trojan.withDefaults(), trojan.withDefaults().clone())
    }

    @Test
    fun trojanGoNaiveTuicAndJuicity() {
        val go = parseTrojanGo(
            "trojan-go://pass@tg.example:443?sni=s.example&type=ws&host=cdn&path=%2Fg&encryption=ss;aes-256-gcm:secret&plugin=obfs#tg"
        )
        assertEquals("pass", go.password)
        assertEquals("ws", go.type)
        assertEquals("cdn", go.host)
        assertEquals("/g", go.path)
        assertEquals("ss;aes-256-gcm:secret", go.encryption)
        assertEquals("obfs", go.plugin)
        assertEquals("tg", go.name)
        val goAgain = parseTrojanGo(go.withDefaults().trojanGoToUri())
        assertEquals("ws", goAgain.type)
        assertEquals("/g", goAgain.path)
        assertEquals("tg", goAgain.name)
        assertEquals(go.withDefaults(), go.withDefaults().clone())

        val goJson = JSONObject("""{"remote_addr":"1.2.3.4","remote_port":443,"password":"pw"}""").parseTrojanGo()
        assertEquals("1.2.3.4", goJson.serverAddress)
        assertEquals("pw", goJson.password)

        val naive = parseNaive(
            "naive+https://user:pass@n.example:443?sni=n.example&cert=CERT&extra-headers=Foo%3A%20bar&insecure-concurrency=2#naive"
        )
        assertEquals("https", naive.proto)
        assertEquals("user", naive.username)
        assertEquals("pass", naive.password)
        assertEquals("n.example", naive.sni)
        assertEquals("CERT", naive.certificates)
        assertEquals("Foo: bar", naive.extraHeaders)
        assertEquals(2, naive.insecureConcurrency)
        assertEquals("naive", naive.name)
        assertTrue(naive.naiveToUri().startsWith("naive+https://"))
        assertTrue(naive.naiveToUri(true).startsWith("https://"))
        assertEquals(naive.withDefaults(), naive.withDefaults().clone())

        val tuic = parseTuic(
            "tuic://$uuid:token@tuic.example:443?sni=tuic.example&congestion_control=bbr&udp_relay_mode=quic&alpn=h3&allow_insecure=1&disable_sni=1#tuic"
        )
        assertEquals(uuid, tuic.uuid)
        assertEquals("token", tuic.token)
        assertEquals("bbr", tuic.congestionController)
        assertEquals("quic", tuic.udpRelayMode)
        assertEquals("h3", tuic.alpn)
        assertTrue(tuic.allowInsecure)
        assertTrue(tuic.disableSNI)
        val tuicAgain = parseTuic(tuic.withDefaults().tuicToUri())
        assertEquals("token", tuicAgain.token)
        assertEquals("bbr", tuicAgain.congestionController)
        assertTrue(tuicAgain.disableSNI)
        assertEquals("tuic", tuicAgain.name)

        val packedUser = parseTuic("tuic://${uuid}%3Atoken@tuic.example:1#packed")
        assertEquals(uuid, packedUser.uuid)
        assertEquals("token", packedUser.token)
        assertEquals(5, packedUser.protocolVersion)
        assertThrows(Exception::class.java) {
            buildSingBoxOutboundTuicBean(TuicBean().apply { protocolVersion = 4 })
        }
        assertEquals(tuic.withDefaults(), tuic.withDefaults().clone())

        val hex = "ab".repeat(32)
        val juicity = parseJuicity(
            "juicity://$uuid:pw@j.example:443?sni=j.example&allow_insecure=true&pinned_certchain_sha256=$hex#ju"
        )
        assertEquals(uuid, juicity.uuid)
        assertEquals("pw", juicity.password)
        assertTrue(juicity.allowInsecure)
        assertEquals(64, hex.length)
        assertFalse(juicity.pinnedCertchainSha256.contains(":"))
        assertTrue(juicity.pinnedCertchainSha256.isNotBlank())
        val juicityAgain = parseJuicity(juicity.withDefaults().juicityToUri())
        assertEquals(juicity.pinnedCertchainSha256, juicityAgain.pinnedCertchainSha256)
        assertEquals("ju", juicityAgain.name)

        val shortHash = parseJuicity("juicity://$uuid:pw@j.example:443?pinned_certchain_sha256=abc%2Bdef%2Fghi")
        assertEquals("abc-def_ghi", shortHash.pinnedCertchainSha256)
        assertEquals(juicity.withDefaults(), juicity.withDefaults().clone())
    }

    @Test
    fun snellAnyTlsShadowTlsSshMieruAndWireGuard() {
        val snell = parseSnell(
            "snell://psk@sn.example:8443?version=6&userkey=uk&mode=unshaped&reuse=true&network=tcp&quic-proxy-mode=true#snell"
        )
        assertEquals("psk", snell.psk)
        assertEquals(6, snell.version)
        assertEquals("uk", snell.userKey)
        assertEquals("unshaped", snell.mode)
        assertTrue(snell.reuse)
        assertTrue(snell.quicProxyMode)
        assertEquals("tcp", snell.network)
        val snellAgain = parseSnell(snell.withDefaults().snellToUri())
        assertEquals("unshaped", snellAgain.mode)
        assertEquals("snell", snellAgain.name)
        assertEquals(1, parseSnell("snell://psk@h:1?version=0").version)
        assertEquals(6, parseSnell("snell://psk@h:1?version=9").version)
        assertThrows(IllegalStateException::class.java) { parseSnell("snell://") }

        val clash = parseClashSnell(
            mapOf(
                "name" to "clash",
                "server" to "c.example",
                "port" to 443,
                "psk" to "psk",
                "version" to 6,
                "udp" to false,
                "reuse" to true,
                "obfs-opts" to mapOf("mode" to "http", "host" to "obfs.example")
            )
        )
        assertEquals(5, clash.version)
        assertEquals("tcp", clash.network)
        assertEquals("http", clash.obfsMode)
        assertEquals("obfs.example", clash.obfsHost)
        val udpClash = parseClashSnell(mapOf("name" to "u", "server" to "h", "udp" to true))
        assertEquals("", udpClash.network)
        assertEquals(4, udpClash.version)

        val snellOut = buildSingBoxOutboundSnellBean(snell.withDefaults())
        assertEquals("snell", snellOut.type)
        assertEquals("uk", snellOut.userkey)
        assertEquals("unshaped", snellOut.mode)
        assertEquals(true, snellOut.quic_proxy_mode)
        assertEquals(true, snellOut.reuse)

        val obfsTls = buildSingBoxOutboundSnellBean(
            SnellBean().apply {
                serverAddress = "h"
                serverPort = 443
                psk = "psk"
                version = 4
                obfsMode = "tls"
                obfsHost = "obfs"
            }.withDefaults()
        )
        assertEquals("", obfsTls.obfs_mode)
        val obfsHttp = buildSingBoxOutboundSnellBean(
            SnellBean().apply {
                serverAddress = "h"
                serverPort = 443
                psk = "psk"
                version = 3
                obfsMode = "http"
                obfsHost = "obfs.example"
                reuse = true
                network = "tcp"
            }.withDefaults()
        )
        assertEquals("http", obfsHttp.obfs_mode)
        assertEquals("obfs.example", obfsHttp.obfs_host)
        assertEquals("tcp", obfsHttp.network)
        assertEquals(snell.withDefaults(), snell.withDefaults().clone())

        val any = parseAnytls(
            "anytls://pw@a.example:443?sni=a.example&insecure=1&fp=chrome&pbk=pub&sid=ab#any"
        )
        assertEquals("pw", any.password)
        assertEquals("a.example", any.sni)
        assertTrue(any.allowInsecure)
        assertEquals("chrome", any.utlsFingerprint)
        assertEquals("pub", any.realityPubKey)
        assertEquals("ab", any.realityShortId)
        val anyAgain = parseAnytls(any.withDefaults().anytlsToUri())
        assertEquals("pub", anyAgain.realityPubKey)
        assertEquals("any", anyAgain.name)

        val anyOut = buildSingBoxOutboundAnyTLSBean(
            AnyTLSBean().apply {
                serverAddress = "a.example"
                serverPort = 443
                password = "pw"
                sni = "a.example"
                alpn = "h2,http/1.1"
                certificates = "CERT"
                allowInsecure = true
                realityPubKey = "pub"
                realityShortId = "ab"
                echConfig = "abcdef"
            }.withDefaults()
        )
        assertEquals("anytls", anyOut.type)
        assertEquals("a.example", anyOut.tls.server_name)
        assertEquals(true, anyOut.tls.insecure)
        assertEquals(listOf("h2", "http/1.1"), anyOut.tls.alpn)
        assertEquals("CERT", anyOut.tls.certificate)
        assertEquals("pub", anyOut.tls.reality.public_key)
        assertNotNull(anyOut.tls.utls)
        assertEquals(true, anyOut.tls.utls.enabled)
        // `fingerprint = it` inside apply assigns the outer local, so the UTLS field stays unset.
        assertNull(anyOut.tls.utls.fingerprint)
        assertEquals(
            listOf("-----BEGIN ECH CONFIGS-----", "abcdef", "-----END ECH CONFIGS-----"),
            anyOut.tls.ech.config
        )
        val echReady = buildSingBoxOutboundAnyTLSBean(
            AnyTLSBean().apply {
                serverAddress = "a.example"
                serverPort = 443
                password = "pw"
                utlsFingerprint = "firefox"
                echConfig = "-----BEGIN ECH CONFIGS-----\nraw"
            }.withDefaults()
        )
        assertNotNull(echReady.tls.utls)
        assertEquals(true, echReady.tls.utls.enabled)
        assertNull(echReady.tls.utls.fingerprint)
        assertEquals(listOf("-----BEGIN ECH CONFIGS-----\nraw"), echReady.tls.ech.config)
        assertNull(echReady.tls.reality)
        assertEquals(any.withDefaults(), any.withDefaults().clone())

        val shadow = ShadowTLSBean().apply {
            serverAddress = "st.example"
            serverPort = 443
            version = 2
            password = "pw"
            security = "none"
        }
        val shadowOut = buildSingBoxOutboundShadowTLSBean(shadow)
        assertEquals("shadowtls", shadowOut.type)
        assertEquals(2, shadowOut.version)
        assertEquals("pw", shadowOut.password)
        assertNull(shadowOut.tls)
        shadow.withDefaults()
        assertEquals("tls", shadow.security)
        assertEquals(shadow, shadow.clone())

        val sshPass = buildSingBoxOutboundSSHBean(
            SSHBean().apply {
                serverAddress = "ssh.example"
                serverPort = 22
                username = "root"
                authType = SSHBean.AUTH_TYPE_PASSWORD
                password = "pw"
                publicKey = "line1\nline2"
            }.withDefaults()
        )
        assertEquals("ssh", sshPass.type)
        assertEquals("pw", sshPass.password)
        assertEquals(listOf("line1", "line2"), sshPass.host_key)
        val sshKey = buildSingBoxOutboundSSHBean(
            SSHBean().apply {
                serverAddress = "ssh.example"
                serverPort = 22
                username = "root"
                authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
                privateKey = "KEY"
                privateKeyPassphrase = "phrase"
            }.withDefaults()
        )
        assertEquals("KEY", sshKey.private_key)
        assertEquals("phrase", sshKey.private_key_passphrase)
        assertNull(sshKey.password)

        val mieru = MieruBean().apply {
            serverAddress = "m.example"
            serverPort = 8443
            username = "user"
            password = "pw"
            protocol = "TCP"
            mtu = 1400
            name = "mieru"
        }.withDefaults()
        val mieruJson = JSONObject(mieru.buildMieruConfig(1080))
        assertEquals(1080, mieruJson.getInt("socks5Port"))
        assertEquals("user", mieruJson.getJSONArray("profiles").getJSONObject(0).getJSONObject("user").getString("name"))
        assertEquals(mieru, mieru.clone())

        assertEquals("AQID", genReserved("1,2,3"))
        assertEquals("AQID", genReserved("[1, 2, 3]"))
        assertEquals("keep-me", genReserved("keep-me"))
        assertEquals("a,b,c", genReserved("a,b,c"))
        val wg = buildSingBoxOutboundWireguardBean(
            WireGuardBean().apply {
                serverAddress = "wg.example"
                serverPort = 51820
                localAddress = "10.0.0.2/32,fd00::2/128"
                privateKey = "priv"
                peerPublicKey = "pub"
                peerPreSharedKey = "psk"
                mtu = 1280
                reserved = "1,2,3"
            }.withDefaults()
        )
        assertEquals("wireguard", wg.type)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), wg.local_address)
        assertEquals("AQID", wg.reserved)
        assertEquals(1280, wg.mtu)
    }
}
