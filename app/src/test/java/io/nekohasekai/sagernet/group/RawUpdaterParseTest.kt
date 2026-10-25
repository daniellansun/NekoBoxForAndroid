package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean
import moe.matsuri.nb4a.proxy.config.ConfigBean
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RawUpdaterParseTest {
    @Test
    fun parsesClashYaml() {
        runBlocking {
        val proxies = RawUpdater.parseRaw(CLASH)!!
        assertTrue(proxies.size >= 12)
        val socks = proxies.filterIsInstance<SOCKSBean>().single()
        assertEquals("socks.example", socks.serverAddress)
        assertEquals(1080, socks.serverPort)
        assertEquals("u", socks.username)

        val http = proxies.filterIsInstance<HttpBean>().single()
        assertEquals("tls", http.security)
        assertEquals("sni.example", http.sni)
        assertTrue(http.allowInsecure)

        val ss = proxies.filterIsInstance<ShadowsocksBean>()
        assertEquals("none", ss[0].method)
        assertTrue(ss[0].plugin.contains("obfs-local"))
        assertTrue(ss[1].plugin.contains("tls"))
        assertTrue(ss[1].plugin.contains("mux=8"))

        val ssr = proxies.filterIsInstance<ShadowsocksRBean>().single()
        assertEquals("origin", ssr.protocol)
        assertEquals("obfs-param", ssr.obfsParam)

        val vmess = proxies.filterIsInstance<VMessBean>().filter { it.alterId != -1 }
        assertEquals(2, vmess.single { it.name == "vmess" }.alterId)
        assertEquals("tls", vmess.single { it.name == "vmess" }.security)
        assertEquals("cdn.example", vmess.single { it.name == "vmess" }.host)
        assertEquals("/ws", vmess.single { it.name == "vmess" }.path)
        assertEquals("sni-from-host.example", vmess.single { it.name == "vmess-host" }.sni)

        val vless = proxies.filterIsInstance<VMessBean>().filter { it.alterId == -1 }
        val xhttp = vless.single { it.name == "vless-xhttp" }
        assertEquals("xhttp", xhttp.type)
        assertEquals("stream-one", xhttp.xhttpMode)
        assertEquals("firefox", xhttp.utlsFingerprint)
        assertTrue(xhttp.xhttpExtra.contains("x_padding_bytes"))
        assertEquals("firefox", vless.single { it.name == "vless-reality" }.utlsFingerprint)
        assertEquals("pub", vless.single { it.name == "vless-reality" }.realityPubKey)
        assertEquals(2, vless.single { it.name == "vless-xudp" }.packetEncoding)
        assertTrue(vless.single { it.name == "vless-flow" }.encryption.contains("xtls-rprx-vision"))

        val any = proxies.filterIsInstance<AnyTLSBean>().single()
        assertEquals("pw", any.password)
        assertEquals("pub", any.realityPubKey)
        assertTrue(any.allowInsecure)

        val wg = proxies.filterIsInstance<WireGuardBean>()
        assertEquals("10.0.0.2/32", wg[0].localAddress.lines().first())
        assertTrue(wg[0].localAddress.contains("/128"))
        assertEquals("1\n2\n3", wg[0].reserved)
        assertEquals("wg2.example", wg[1].serverAddress)
        assertEquals("only", wg[1].reserved)

        val hy = proxies.filterIsInstance<HysteriaBean>()
        assertEquals(1, hy[0].protocolVersion)
        assertEquals("20000", hy[0].serverPorts)
        assertEquals(HysteriaBean.TYPE_STRING, hy[0].authPayloadType)
        assertEquals("h3", hy[0].alpn)
        assertEquals(2, hy[1].protocolVersion)
        assertEquals("tok", hy[1].authPayload)
        assertEquals("443,444", hy[1].serverPorts)

        val tuic = proxies.filterIsInstance<TuicBean>()
        assertEquals(4, tuic[0].protocolVersion)
        assertEquals("token", tuic[0].token)
        assertEquals("not-an-ip.example", tuic[0].sni)
        assertEquals("1.1.1.1", tuic[1].serverAddress)
        assertEquals("explicit.example", tuic[1].sni)
        assertEquals("bbr", tuic[1].congestionController)

        assertEquals("none", RawUpdater.clashCipher("dummy"))
        assertEquals("aes-128-gcm", RawUpdater.clashCipher("aes-128-gcm"))
        }
    }

    @Test
    fun blankGlobalFingerprintDefaultsToChrome() {
        runBlocking {
        val parsed = RawUpdater.parseRaw(
            """
            proxies:
              - name: vless
                type: vless
                server: v.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                tls: true
                reality-opts:
                  public-key: pub
                  short-id: ab
            """.trimIndent()
        )!!
        val bean = parsed.single() as VMessBean
        assertEquals("chrome", bean.utlsFingerprint)
        }
    }

    @Test
    fun parsesWireGuardIniAndJsonAndLinks() {
        runBlocking {
        val wg = RawUpdater.parseWireGuard(
            """
            [Interface]
            Address = 10.0.0.2/32, fd00::2/128
            PrivateKey = priv
            MTU = 1280

            [Peer]
            PublicKey = pub
            Endpoint = wg.example:51820
            PresharedKey = psk
            """.trimIndent()
        )
        assertEquals(1, wg.size)
        assertEquals("wg.example", wg[0].serverAddress)
        assertEquals(51820, wg[0].serverPort)
        assertEquals(1280, wg[0].mtu)
        assertTrue(wg[0].localAddress.contains("fd00::2/128"))

        val named = RawUpdater.parseRaw(wgConf(), "office.conf")!!
        assertEquals("office", named.single().name)

        val hysteria = RawUpdater.parseRaw("""{"server":"hy.example","up_mbps":10,"auth":"a"}""")!!
        assertTrue(hysteria.single() is HysteriaBean)

        val ssr = RawUpdater.parseRaw(
            """{"server":"s.example","server_port":1,"method":"aes-256-cfb","obfs":"plain","protocol":"origin","password":"pw"}"""
        )!!
        assertTrue(ssr.single() is ShadowsocksRBean)

        val ss = RawUpdater.parseRaw(
            """{"server":"s.example","server_port":1,"method":"aes-128-gcm","password":"pw","plugin":"obfs-local","plugin_opts":"obfs=http"}"""
        )!!
        assertTrue((ss.single() as ShadowsocksBean).plugin.contains("obfs-local"))

        val trojanGo = RawUpdater.parseRaw(
            """{"remote_addr":"t.example","remote_port":443,"password":"pw"}"""
        )!!
        assertEquals("t.example", trojanGo.single().serverAddress)

        val outbound = RawUpdater.parseRaw(
            """
            {"outbounds":[
              {"type":"dns"},
              {"type":""},
              {"type":"block"},
              {"type":"direct"},
              {"type":"selector"},
              {"type":"urltest"},
              {"type":"vmess","tag":"v","server":"v.example","server_port":443}
            ]}
            """.trimIndent()
        )!!
        val config = outbound.single() as ConfigBean
        assertEquals(1, config.type)
        assertEquals("v", config.name)
        assertTrue(config.config.contains("vmess"))

        val bare = RawUpdater.parseRaw("""{"server":"c.example","server_port":443}""")!!
        assertEquals(1, (bare.single() as ConfigBean).type)

        val array = RawUpdater.parseRaw(
            """[{"server":"a.example","server_port":1},{"not":"a profile"}]"""
        )!!
        assertEquals(1, array.size)

        val link = "socks5://u:p@1.2.3.4:1080#name"
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(link.toByteArray())
        val fromB64 = RawUpdater.parseRaw(encoded)!!
        assertTrue(fromB64.single() is SOCKSBean)
        val plain = RawUpdater.parseRaw(link)!!
        assertEquals("1.2.3.4", (plain.single() as SOCKSBean).serverAddress)

        assertNull(RawUpdater.parseRaw("not a subscription"))
        assertNull(RawUpdater.parseRaw("proxies: ["))
        // A broken WireGuard file still starts with `[`, which org.json accepts as an array,
        // so parseRaw returns an empty list after the ini parser throws.
        assertTrue(RawUpdater.parseRaw("[Interface]\nPrivateKey = x\n").isNullOrEmpty())
        assertTrue(
            RawUpdater.parseRaw(
                """
                [Interface]
                Address = 10.0.0.2/32
                PrivateKey = priv
                MTU = 1280

                [Peer]
                PublicKey = pub
                Endpoint = bad
                """.trimIndent()
            ).isNullOrEmpty()
        )

        assertThrows(SubscriptionFoundException::class.java) {
            runBlocking { RawUpdater.parseRaw("clash://install-config?url=http://example.com") }
        }
        assertThrows(SubscriptionFoundException::class.java) {
            runBlocking { RawUpdater.parseRaw("https://user:pass@h.example:443/sub") }
        }
        }
    }

    private fun wgConf(): String = """
        [Interface]
        Address = 10.0.0.2/32
        PrivateKey = priv
        MTU = 1280

        [Peer]
        PublicKey = pub
        Endpoint = wg.example:51820
    """.trimIndent()

    private companion object {
        val CLASH = """
            global-client-fingerprint: firefox
            proxies:
              - name: mystery
                type: mystery
                server: x
                port: 1
              - name: socks
                type: socks5
                server: socks.example
                port: 1080
                username: u
                password: p
              - name: http
                type: http
                server: http.example
                port: 8080
                username: u
                password: p
                tls: true
                sni: sni.example
                skip-cert-verify: true
              - name: ss-obfs
                type: ss
                server: ss.example
                port: 8388
                cipher: dummy
                password: pw
                plugin: obfs
                plugin-opts:
                  mode: http
                  host: obfs.example
              - name: ss-v2ray
                type: ss
                server: ss2.example
                port: 8388
                cipher: aes-256-gcm
                password: pw
                plugin: v2ray-plugin
                plugin-opts:
                  mode: "true"
                  host: h.example
                  path: /ray
                  mux: true
              - name: ssr
                type: ssr
                server: ssr.example
                port: 8388
                cipher: aes-256-cfb
                password: pw
                obfs: plain
                protocol: origin
                obfs-param: obfs-param
                protocol-param: proto-param
              - name: vmess
                type: vmess
                server: vmess.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                alterId: 2
                cipher: auto
                tls: true
                sni: sni.example
                alpn: [h2, http/1.1]
                skip-cert-verify: true
                client-fingerprint: chrome
                network: ws
                ws-opts:
                  path: /ws
                  max-early-data: 256
                  early-data-header-name: Sec-WebSocket-Protocol
                  v2ray-http-upgrade: false
                  headers:
                    Host: cdn.example
                smux:
                  enabled: true
                  max-streams: 8
                  padding: true
                ech-opts:
                  enable: true
                  config: abcdef
              - name: vmess-host
                type: vmess
                server: vmess-host.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                tls: true
                network: ws
                ws-opts:
                  path: /ws
                  headers:
                    Host: sni-from-host.example
              - name: vmess-http
                type: vmess
                server: http-net.example
                port: 80
                uuid: 11111111-1111-1111-1111-111111111111
                network: http
                http-opts:
                  path: [/a, /b]
                  headers:
                    Host: [h1, h2]
              - name: vmess-h2
                type: vmess
                server: h2.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                network: h2
                h2-opts:
                  host: [h2.example]
                  path: /h2
              - name: vmess-grpc
                type: vmess
                server: grpc.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                network: grpc
                grpc-opts:
                  grpc-service-name: svc
              - name: vmess-upgrade
                type: vmess
                server: up.example
                port: 80
                uuid: 11111111-1111-1111-1111-111111111111
                network: ws
                ws-opts:
                  v2ray-http-upgrade: true
                  path: /up
              - name: vless-xhttp
                type: vless
                server: xhttp.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                tls: true
                network: xhttp
                client-fingerprint: firefox
                xhttp-opts:
                  host: cdn.example
                  path: /x
                  mode: stream-one
                  no-grpc-header: true
                  x-padding-bytes: 100-200
                  sc-max-each-post-bytes: 1000
                  sc-min-posts-interval-ms: 30
                  x-padding-obfs-mode: true
                  x-padding-key: k
                  x-padding-header: h
                  x-padding-placement: query
                  x-padding-method: repeat
                  uplink-http-method: POST
                  session-placement: header
                  session-key: sk
                  seq-placement: header
                  seq-key: qk
                  uplink-data-placement: body
                  uplink-data-key: uk
                  uplink-chunk-size: 100
                  reuse-settings:
                    max-connections: 4
                    max-concurrency: 8
                    c-max-reuse-times: 1
                    h-max-request-times: 2
                    h-max-reusable-secs: 3
                    h-keep-alive-period: 4
              - name: vless-reality
                type: vless
                server: reality.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                reality-opts:
                  public-key: pub
                  short-id: ab
              - name: vless-flow
                type: vless
                server: flow.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                flow: xtls-rprx-vision
                encryption: mlkem
                packet-encoding: packetaddr
              - name: vless-xudp
                type: vless
                server: xudp.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                packet-encoding: xudp
              - name: trojan
                type: trojan
                server: trojan.example
                port: 443
                password: pw
                sni: trojan.example
                network: grpc
                grpc-opts:
                  grpc-service-name: trojan-svc
              - name: any
                type: anytls
                server: any.example
                port: 443
                password: pw
                client-fingerprint: chrome
                sni: any.example
                skip-cert-verify: "true"
                alpn: [h2, http/1.1]
                reality_pub_key: pub
                reality_short_id: ab
              - name: wg
                type: wireguard
                server: wg.example
                port: 51820
                ip: 10.0.0.2
                ipv6: fd00::2
                private-key: priv
                public-key: pub
                pre-shared-key: psk
                mtu: 1280
                reserved: [1, 2, 3]
              - name: wg2
                type: wireguard
                ip: 10.0.0.3/32
                peers:
                  - server: wg2.example
                    port: 2408
                    public-key: pub2
                    reserved: "only"
              - name: hy1
                type: hysteria
                server: hy.example
                port: 443
                ports: "20000"
                obfs: obfs
                auth-str: auth
                sni: hy.example
                skip-cert-verify: "1"
                up: "100 Mbps"
                down: "200 Mbps"
                recv-window-conn: 1
                recv-window: 2
                disable-mtu-discovery: "1"
                alpn: h3
              - name: hy2
                type: hysteria2
                server: hy2.example
                port: 443
                ports: "443,444"
                obfs-password: sal
                password: tok
                sni: hy2.example
                skip-cert-verify: "true"
                up: 10
                down: 20
              - name: tuic-token
                type: tuic
                server: tuic.example
                port: 443
                token: token
                ip: not-an-ip.example
                skip-cert-verify: "true"
                disable-sni: "true"
                reduce-rtt: "true"
                udp-relay-mode: quic
              - name: tuic-v5
                type: tuic
                server: tuic5.example
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                password: pw
                ip: 1.1.1.1
                sni: explicit.example
                alpn: [h3]
                congestion-controller: bbr
                udp-relay-mode: native
              - name: snell
                type: snell
                server: snell.example
                port: 443
                psk: psk
                version: 4
                reuse: true
                udp: false
                obfs-opts:
                  mode: http
                  host: obfs.example
        """.trimIndent()
    }
}
